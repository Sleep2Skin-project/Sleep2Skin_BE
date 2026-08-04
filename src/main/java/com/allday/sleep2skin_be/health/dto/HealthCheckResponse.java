package com.allday.sleep2skin_be.health.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "헬스체크 응답")
public record HealthCheckResponse(

        @Schema(description = "서비스 상태", example = "UP")
        String status,

        @Schema(description = "애플리케이션 이름", example = "sleep2skin_be")
        String applicationName,

        @Schema(description = "응답 시각", example = "2026-08-04T14:58:00+09:00")
        OffsetDateTime serverTime
) {

    public static HealthCheckResponse up(String applicationName) {
        return new HealthCheckResponse("UP", applicationName, OffsetDateTime.now());
    }

}
