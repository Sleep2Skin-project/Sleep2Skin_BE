package com.allday.sleep2skin_be.domain.skin.repository;

import com.allday.sleep2skin_be.domain.skin.dto.VerifiedDay;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMeasurement;
import org.springframework.data.jpa.repository.JpaRepository;
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

}
