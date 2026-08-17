package com.allday.sleep2skin_be.domain.skin.repository;

import com.allday.sleep2skin_be.domain.skin.dto.VerifiedDay;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMeasurement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 셀피 실측 조회.
 */
public interface SkinMeasurementRepository extends JpaRepository<SkinMeasurement, Long> {

    /**
     * 유니크 키 {@code (user_id, base_date)} 조회. 하루 1회 검증을 판정한다.
     *
     * <p>수면 재수신 시 <b>"그날 검증을 마쳤는가"의 판정에도 이 메서드를 쓴다</b> — 행이 있으면
     * 예보를 갱신하지 않는다. 검증을 마친 날의 예보가 바뀌면 이미 끝난 대조의 기준이 사후에
     * 달라져 적중률이 훼손되고 개인 가중치가 중복 학습된다.
     */
    Optional<SkinMeasurement> findByUserIdAndBaseDate(Long userId, LocalDate baseDate);

    /**
     * {@code baseDate} 이하 범위에서 <b>가장 최근</b> 실측 1건 (REP-10 클리닉 트리아지의
     * {@code clinicNeeded}).
     *
     * <p><b>전체 최신이 아니라 {@code baseDate} 이하 최신이다</b> — 서버는 "오늘"을 모르므로,
     * 앱이 알려준 기준일보다 미래의 행이 섞이면 안 된다(conventions.md §8과 같은 이유). 예보·
     * 정체 판정과 달리 <b>기간 하한이 없다</b> — 색소침착·여드름 흉터·구조적 노화는 추세가 아니라
     * "지금 상태"를 보여주는 값이라 3주 창 밖의 실측이라도 가장 최근 것이면 유효하다.
     */
    Optional<SkinMeasurement> findFirstByUserIdAndBaseDateLessThanEqualOrderByBaseDateDesc(
            Long userId, LocalDate baseDate);

    /**
     * 검증한 날들을 <b>최신순</b>으로. 예보와 실측을 조인해 하루씩 묶는다.
     *
     * <p><b>연관관계가 아니라 값으로 조인한다.</b> {@code skin_forecast}와
     * {@code skin_measurement}는 FK로 연결돼 있지 않고 {@code (user_id, base_date)}가 같으면 같은
     * 날이다(erd.md §2). 엔티티에 관계를 만들지 않은 것은 <b>둘의 생명주기가 다르기</b> 때문이다 —
     * 예보는 수면 업로드 때, 실측은 셀피 검증 때 생긴다.
     *
     * <p><b>내부 조인이라 예보만 있고 검증하지 않은 날은 빠진다.</b> 그게 정확히 "검증한 날"의
     * 정의다.
     *
     * <p><b>{@code baseDate} 이하만 본다.</b> 서버는 "오늘"을 모르므로 앱이 알려준 기준일보다
     * 미래의 행(시연용으로 미리 넣은 데이터 등)이 적중률·연속 횟수에 섞이면 안 된다.
     *
     * <p>정렬이 최신순인 이유는 <b>두 계산이 모두 앞에서부터 읽기</b> 때문이다 — 최근 1건은 첫
     * 원소이고, 연속 횟수는 기준일부터 거꾸로 훑는다.
     */
    @Query("""
            select new com.allday.sleep2skin_be.domain.skin.dto.VerifiedDay(
                       m.baseDate,
                       f.darkCircle, f.complexion, f.barrier,
                       m.darkCircle, m.complexion, m.barrier)
              from SkinMeasurement m, SkinForecast f
             where m.userId = f.userId
               and m.baseDate = f.baseDate
               and m.userId = :userId
               and m.baseDate <= :baseDate
             order by m.baseDate desc
            """)
    List<VerifiedDay> findVerifiedDays(@Param("userId") Long userId,
                                       @Param("baseDate") LocalDate baseDate);

    /**
     * 누적 검증 횟수. <b>{@code COUNT}로 나오므로 컬럼을 두지 않았다</b>(erd.md §3.7 원칙 ①).
     *
     * <p>MY-01·REP-12·HOME-08이 전부 이 숫자를 쓰므로 <b>어긋나면 세 화면이 동시에 틀린다.</b>
     */
    long countByUserId(Long userId);

    /**
     * 검증한 날짜만 <b>최신순</b>으로. 연속 검증 횟수 계산의 입력이다.
     *
     * <p><b>{@link #findVerifiedDays}의 가벼운 형제다.</b> 저쪽은 예보를 조인해 점수 6개를 함께
     * 끌고 오는데, MY-01 프로필은 <b>날짜만 있으면 된다</b> — 적중률을 계산하지 않기 때문이다.
     *
     * <p>{@code baseDate} 이하만 보는 것과 정렬이 최신순인 것은 저쪽과 같은 이유다. 두 메서드가
     * 같은 날짜 집합을 주어야 <b>HOME-09 배너와 MY-01 프로필의 연속 횟수가 어긋나지 않는다</b>
     * (prd.md §4.2). <b>조건을 한쪽만 고치지 말 것.</b>
     */
    @Query("select m.baseDate from SkinMeasurement m"
            + " where m.userId = :userId and m.baseDate <= :baseDate"
            + " order by m.baseDate desc")
    List<LocalDate> findVerifiedBaseDates(@Param("userId") Long userId,
                                          @Param("baseDate") LocalDate baseDate);

    /**
     * 사용자의 실측 전량 삭제 (MY-04 전체 삭제).
     *
     * <p><b>DB에 `users` 외래키가 없어 CASCADE가 걸리지 않는다</b> — SkinMeasurement.userId 는 연관관계가
     * 아니라 단순 컬럼이라 Hibernate 가 제약을 만들지 않았다. 그래서 삭제를 손으로 지운다.
     *
     * <p>파생 {@code deleteBy}가 아니라 벌크 삭제인 이유는 전부 엔티티로 읽어 하나씩 지우지
     * 않기 위해서다.
     */
    @Modifying
    @Query("delete from SkinMeasurement e where e.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);

}
