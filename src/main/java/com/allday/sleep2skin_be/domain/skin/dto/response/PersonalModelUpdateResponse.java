package com.allday.sleep2skin_be.domain.skin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 개인 가중치 학습 결과 (HOME-08).
 *
 * <p><b>지금은 {@code updated: false} 로 고정이다.</b> 학습 로직이 아직 없다 — 이 PR 은 분석과
 * 검증(HOME-06/07)까지이고 보정은 다음 PR 이다.
 *
 * <p><b>필드를 미리 낸 것은 앱이 파싱 코드를 두 번 쓰지 않게 하기 위해서다.</b> 나중에
 * {@code changes} 와 {@code message} 가 <b>추가</b>되며, 기존 필드는 바뀌지 않는다.
 */
@Schema(description = "개인 가중치 학습 결과 (HOME-08)")
public record PersonalModelUpdateResponse(

        @Schema(description = "이번 검증으로 개인 가중치가 갱신됐는가", example = "false")
        boolean updated
) {

    /** 학습 미구현 상태의 응답. HOME-08 이 붙으면 이 자리가 실제 결과로 바뀐다. */
    public static PersonalModelUpdateResponse notYetImplemented() {
        return new PersonalModelUpdateResponse(false);
    }

}
