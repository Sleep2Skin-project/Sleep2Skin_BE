package com.allday.sleep2skin_be.domain.sleep.dto;

import com.allday.sleep2skin_be.domain.sleep.entity.SleepStage;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * 앱이 보낸 수면 단계 구간 하나. 정규화 계층의 입력이자, 잘라낸 뒤 남은 세션 구간의 표현이다.
 *
 * <p>요청 DTO와 분리한 이유는 정규화 로직이 HTTP를 모르게 두기 위해서다 — 이 계층은 DB도 웹도
 * 없이 단위 테스트로 검증된다.
 *
 * <p>시각이 {@link OffsetDateTime}인 것이 핵심이다. {@code LocalDateTime}으로 받으면 요청의
 * 오프셋이 이미 버려져 {@code sleepDate}가 하루 밀리고, 그 날짜로 조인되는 예보·검증이 전부
 * 어긋난다(erd.md §3.1).
 */
public record SleepSegmentCommand(

        SleepStage stage,

        OffsetDateTime startTime,

        OffsetDateTime endTime
) {

    public Duration duration() {
        return Duration.between(startTime, endTime);
    }

    public boolean isAsleep() {
        return stage.isAsleep();
    }

}
