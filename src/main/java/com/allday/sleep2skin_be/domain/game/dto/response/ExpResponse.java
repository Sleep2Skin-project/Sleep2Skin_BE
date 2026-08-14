package com.allday.sleep2skin_be.domain.game.dto.response;

import com.allday.sleep2skin_be.domain.game.LevelPolicy;
import com.allday.sleep2skin_be.domain.game.entity.ExpReason;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * exp 적립 결과 (api.md §1). <b>적립이 일어나는 네 API가 전부 이 객체를 쓴다.</b>
 *
 * <pre>
 * POST /users/me/attendance · POST /sleep/sessions · POST /skin/selfie · PATCH /todo/{id}
 * </pre>
 *
 * <p>넷이 같은 모양을 쓰는 이유는 <b>앱이 파싱 코드와 레벨 업 연출을 한 번만 만들면 되기</b>
 * 때문이다. <b>도메인마다 DTO를 새로 만들지 말 것.</b>
 *
 * <p><b>{@code gained: 0}은 정상이다.</b> 오늘 이미 받은 보상을 다시 요청한 경우이며
 * {@code reasons}가 빈 배열이다 — 앱은 시작할 때마다 호출하므로 일상적으로 발생한다.
 * 에러가 아니다.
 *
 * @param gained       <b>요청한 양이 아니라 실제 증감이다.</b> {@code reasons}의 합과 같지만,
 *                     회수가 0에서 멈추는 경우(erd.md §3.1)만 그보다 작아진다.
 *                     ⚠️ <b>앱은 부호를 그대로 반영해야 한다</b> — 양수로 가정하고 더하면
 *                     서버가 막은 무한 적립이 화면에서 되살아난다
 * @param reasons      무엇으로 받았는지. 없으면 빈 배열이며 <b>한 요청에 둘이 함께 실릴 수 있다</b>
 *                     (수면 업로드의 증가 보상 + 90점 보상, TODO의 개별 완료 + 전체 완료)
 * @param totalExp     조정 이후 {@code users.exp}
 * @param levelUp      이번 요청으로 레벨이 올라갔는가. 앱이 캐릭터 변경 연출을 띄우는 신호이며
 *                     <b>서버는 어떤 캐릭터인지 모른다</b>
 * @param nextLevelExp 다음 레벨 <b>컷오프 절대값</b>. 만렙(5)이면 {@code null}이고, "남은 exp"는
 *                     앱이 {@code nextLevelExp − totalExp}로 계산한다
 */
@Schema(description = "exp 적립 결과. 적립이 일어나는 네 API가 같은 모양을 쓴다")
public record ExpResponse(

        @Schema(description = "이번 요청으로 **실제 증감한** 양 (부호 포함, `0`일 수 있다)",
                example = "25")
        int gained,

        @Schema(description = "무엇으로 받았는지. 없으면 빈 배열")
        List<ExpReasonResponse> reasons,

        @Schema(description = "조정 이후 누적 exp", example = "320")
        int totalExp,

        @Schema(description = "현재 레벨 (1~5). `totalExp`에서 계산되며 저장된 컬럼이 아니다",
                example = "3")
        int level,

        @Schema(description = "이번 요청으로 레벨이 올라갔는가", example = "true")
        boolean levelUp,

        @Schema(description = "다음 레벨 **컷오프 절대값**. 만렙(5)이면 `null`", nullable = true,
                example = "450")
        Integer nextLevelExp
) {

    /**
     * @param before 조정 <b>전</b> 누적 exp. 레벨 업 판정에만 쓴다
     * @param after  조정 <b>후</b> 누적 exp. {@code users.exp}의 현재 값이다
     */
    public static ExpResponse of(int before, int after, List<ExpReasonResponse> reasons) {
        return new ExpResponse(
                after - before,
                List.copyOf(reasons),
                after,
                LevelPolicy.levelOf(after),
                LevelPolicy.levelOf(after) > LevelPolicy.levelOf(before),
                LevelPolicy.nextLevelExp(after));
    }

    /**
     * 적립 없이 현재 상태만. 재수신·재호출처럼 <b>아무것도 지급하지 않은 요청</b>이 쓴다.
     *
     * <p>{@code exp} 객체 자체를 빼지 않는 이유는 앱이 응답마다 존재 여부를 분기하지 않게 하기
     * 위해서다 — 값이 {@code 0}일 뿐 모양은 같다.
     */
    public static ExpResponse unchanged(int totalExp) {
        return of(totalExp, totalExp, List.of());
    }

    /** 적립 한 건. */
    @Schema(description = "적립 한 건")
    public record ExpReasonResponse(

            @Schema(description = "적립 사유", example = "VERIFICATION_STREAK")
            ExpReason reason,

            @Schema(description = "이 사유로 지급된 양", example = "25")
            int amount
    ) {
    }

}
