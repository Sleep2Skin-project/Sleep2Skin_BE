package com.allday.sleep2skin_be.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 전체 삭제 결과 (MY-04).
 *
 * <p><b>복구 불가 영구 삭제다.</b> soft delete가 아니라 행을 지우며, FK의
 * {@code ON DELETE CASCADE}로 수면·예보·실측·가중치·TODO가 함께 사라진다(erd.md §5).
 *
 * <p><b>본문이 없는 204가 아니라 200 + 래퍼다.</b> 모든 응답이 같은 래퍼를 쓴다는 규약
 * (conventions.md §1)을 이 API만 비켜가면 앱이 여기서만 다르게 파싱해야 한다.
 *
 * <p><b>삭제 후 앱이 어느 화면으로 가는지는 서버가 정하지 않는다</b> — 온보딩으로 돌아갈지는
 * 미결정이고(prd.md §7 P2), 2단계 확인 다이얼로그도 클라이언트 몫이다. 서버는 지우기만 한다.
 */
@Schema(description = "전체 삭제 결과")
public record UserDeleteResponse(

        @Schema(description = "삭제된 사용자 ID", example = "1")
        Long userId,

        @Schema(description = "삭제 완료 여부. 이 응답이 나갔다면 항상 `true`다", example = "true")
        boolean deleted
) {

    public static UserDeleteResponse of(Long userId) {
        return new UserDeleteResponse(userId, true);
    }

}
