package com.allday.sleep2skin_be.domain.sleep.repository;

import com.allday.sleep2skin_be.domain.sleep.entity.SleepSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 수면 세션 조회.
 */
public interface SleepSessionRepository extends JpaRepository<SleepSession, Long> {

    /**
     * 유니크 키 {@code (user_id, sleep_date)} 조회.
     *
     * <p>중복 수신 판정의 출발점이다 — 기존 행의 {@code payloadHash}와 비교해 같으면
     * <b>저장·스코어링을 시작하기 전에</b> 중단한다.
     */
    Optional<SleepSession> findByUserIdAndSleepDate(Long userId, LocalDate sleepDate);

    /**
     * 기간 안의 잠든 시각들. 취침 규칙성({@code BEDTIME_REGULARITY} → {@code COMPLEXION})의 입력이다.
     *
     * <p>엔티티가 아니라 시각만 뽑는다 — 규칙성 계산에 다른 컬럼이 필요 없고, 세션 하나가
     * 구간 수백 개를 달고 있어 엔티티로 읽으면 쓰지도 않을 것을 끌고 온다.
     *
     * <p><b>호출부는 오늘을 빼고 조회한 뒤 이번 밤의 값을 직접 더한다.</b> 갱신 경로에서는 DB에
     * 아직 옛 값이 들어 있어, 저장 순서에 따라 규칙성이 달라지는 것을 막기 위해서다.
     */
    @Query("select s.sleepOnsetTime from SleepSession s"
            + " where s.userId = :userId and s.sleepDate between :from and :to")
    List<OffsetDateTime> findSleepOnsetTimes(@Param("userId") Long userId,
                                             @Param("from") LocalDate from,
                                             @Param("to") LocalDate to);

    /**
     * 마지막으로 수면 데이터가 <b>서버에 도착한</b> 시각 (MY-02).
     *
     * <p><b>{@code created_at}이지 {@code sleepDate}가 아니다.</b> 화면이 말하려는 것은 "언제
     * 잤는가"가 아니라 <b>"앱이 마지막으로 동기화한 게 언제인가"</b>이므로, 앞엣것을 쓰면 며칠 전
     * 데이터를 방금 올린 경우에 "동기화가 며칠째 안 됐다"고 잘못 말하게 된다(erd.md §2).
     *
     * <p>수신 이력이 없으면 빈 값이다 — 신규 사용자에게 일상적이라 에러가 아니다.
     *
     * <p>⚠️ 같은 수면이 재수신되어 <b>갱신</b>되면 {@code created_at}은 그대로다. 그때는
     * {@code updated_at}이 움직이지만, "언제 처음 받았는가"가 연결 상태를 더 정확히 말한다 —
     * 갱신은 앱이 같은 밤을 다시 올린 것이라 새 동기화가 아니다.
     */
    @Query("select max(s.createdAt) from SleepSession s where s.userId = :userId")
    Optional<OffsetDateTime> findLastReceivedAt(@Param("userId") Long userId);

    /**
     * 사용자의 수면 세션 전량 삭제 (MY-04 전체 삭제).
     *
     * <p><b>DB에 `users` 외래키가 없어 CASCADE가 걸리지 않는다</b> — SleepSession.userId 는 연관관계가
     * 아니라 단순 컬럼이라 Hibernate 가 제약을 만들지 않았다. 그래서 삭제를 손으로 지운다.
     *
     * <p>파생 {@code deleteBy}가 아니라 벌크 삭제인 이유는 전부 엔티티로 읽어 하나씩 지우지
     * 않기 위해서다.
     */
    @Modifying
    @Query("delete from SleepSession e where e.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);

}
