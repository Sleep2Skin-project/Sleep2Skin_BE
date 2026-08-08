package com.allday.sleep2skin_be.domain.user.dto.response;

import com.allday.sleep2skin_be.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "온보딩 완료 처리 결과")
public record OnboardingCompleteResponse(

        @Schema(description = "사용자 식별자", example = "1")
        Long userId,

        @Schema(description = "온보딩 완료 여부. 이 API를 거치면 항상 true다.", example = "true")
        boolean onboardingCompleted,

        @Schema(description = """
                이번 요청으로 상태가 바뀌었는지 여부.
                false면 이미 완료된 사용자였다는 뜻이며 에러가 아니다.""",
                example = "true")
        boolean newlyCompleted
) {

    public static OnboardingCompleteResponse of(User user, boolean newlyCompleted) {
        return new OnboardingCompleteResponse(user.getId(), user.isOnboardingCompleted(), newlyCompleted);
    }

}
