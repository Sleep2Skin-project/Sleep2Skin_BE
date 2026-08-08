package com.allday.sleep2skin_be.domain.sleep.repository;

import com.allday.sleep2skin_be.domain.sleep.entity.SleepSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
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

}
