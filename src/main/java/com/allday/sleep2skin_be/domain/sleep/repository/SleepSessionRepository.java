package com.allday.sleep2skin_be.domain.sleep.repository;

import com.allday.sleep2skin_be.domain.sleep.entity.SleepSession;
import org.springframework.data.jpa.repository.JpaRepository;
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

}
