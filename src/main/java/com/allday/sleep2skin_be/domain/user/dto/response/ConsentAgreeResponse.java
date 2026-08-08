package com.allday.sleep2skin_be.domain.user.dto.response;

import com.allday.sleep2skin_be.domain.user.entity.ConsentHistory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "개인정보 수집·이용 동의 저장 결과")
public record ConsentAgreeResponse(

        @Schema(description = "동의 이력 식별자", example = "1")
        Long consentId,

        @Schema(description = "동의한 약관 버전", example = "1.0")
        String termsVersion,

        @Schema(description = "동의 시각 (ISO 8601, 오프셋 포함)", example = "2026-08-08T00:12:33Z")
        OffsetDateTime agreedAt,

        @Schema(description = """
                이번 요청으로 새 이력이 생겼는지 여부.
                false면 같은 버전에 이미 동의한 상태여서 기존 이력을 그대로 돌려준 것이다.""",
                example = "true")
        boolean newlyAgreed
) {

    public static ConsentAgreeResponse of(ConsentHistory consentHistory, boolean newlyAgreed) {
        return new ConsentAgreeResponse(
                consentHistory.getId(),
                consentHistory.getTermsVersion(),
                // agreed_at 컬럼이 따로 없다. 행이 생기는 순간이 곧 동의한 순간이다 (erd.md §3.2).
                consentHistory.getCreatedAt(),
                newlyAgreed);
    }

}
