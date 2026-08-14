package com.allday.sleep2skin_be.domain.user.dto.response;

import com.allday.sleep2skin_be.domain.game.LevelPolicy;
import com.allday.sleep2skin_be.domain.user.ConsentPolicy;
import com.allday.sleep2skin_be.domain.user.entity.ConsentHistory;
import com.allday.sleep2skin_be.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * 온보딩·동의 상태 + 프로필 (ONB-01 진입 분기 + MY-01).
 *
 * <p><b>앱이 시작될 때 가장 먼저 호출한다.</b> 온보딩 화면을 띄울지, 동의 화면을 띄울지,
 * 바로 홈으로 갈지를 이 한 번의 응답으로 결정한다.
 *
 * <p><b>{@code {status, message}}를 쓰지 않는다.</b> 다른 조회 API와 달리 이 응답은 사용자가
 * 존재하면 언제나 완전하다 — 신규 사용자도 {@code onboardingCompleted: false}라는 <b>정상적인
 * 값</b>을 받는다. 빈 상태가 없으므로 상태 필드가 늘 {@code AVAILABLE}이 되어 뜻이 없다.
 * 사용자가 없으면 그건 진짜 오류이므로 {@code 404 USER_NOT_FOUND}다.
 *
 * @param consentAgreed      <b>"동의한 적이 있는가"가 아니라 "현재 약관 버전에 동의했는가"다.</b>
 *                           약관이 개정돼 {@link ConsentPolicy#CURRENT_TERMS_VERSION}이 올라가면
 *                           기존 사용자도 {@code false}가 되어 자연스럽게 재동의 화면으로 간다.
 *                           <b>로컬 플래그로는 이걸 알 방법이 없다</b> — 앱은 "동의 완료"만
 *                           기억하고 있어서 버전이 올라간 것을 영원히 모른다
 * @param agreedTermsVersion 가장 최근에 동의한 버전. 이력이 없으면 {@code null}
 * @param verificationCount  누적 검증 횟수. <b>등급이 아니라 숫자다</b> — 신뢰도 해석은
 *                           클라이언트가 한다(prd.md §4.5 L8). 등급만 내려주면 원본 숫자가 가려져
 *                           REP-12와 어긋나도 알아채기 어렵다
 * @param streakCount        연속 검증 횟수. <b>HOME-09 배너와 같은 계산에서 나온다</b>(§4.2)
 * @param level              현재 레벨 (1~5). <b>{@code users.exp}에서 계산되며 저장된 컬럼이
 *                           아니다</b>(erd.md §3.1) — 두 컬럼을 두면 이중 상태가 되고 컷오프를
 *                           바꿀 때 전 행을 다시 계산해야 한다
 * @param nextLevelExp       다음 레벨 <b>컷오프 절대값</b>. 만렙(5)이면 {@code null}
 */
@Schema(description = "온보딩·동의 상태 + 프로필")
public record UserProfileResponse(

        @Schema(description = "사용자 ID", example = "1")
        Long userId,

        @Schema(description = "닉네임", example = "테스트유저1")
        String nickname,

        @Schema(description = "온보딩 완료 여부", example = "true")
        boolean onboardingCompleted,

        @Schema(description = "**현재 약관 버전**에 동의했는지. 약관이 개정되면 기존 사용자도 `false`가 된다",
                example = "true")
        boolean consentAgreed,

        @Schema(description = "서버가 요구하는 현재 약관 버전", example = "1.0")
        String currentTermsVersion,

        @Schema(description = "가장 최근에 동의한 버전. 동의 이력이 없으면 `null`", nullable = true,
                example = "1.0")
        String agreedTermsVersion,

        @Schema(description = "가장 최근 동의 시각. 이력이 없으면 `null`", nullable = true,
                example = "2026-08-08T00:12:33Z")
        OffsetDateTime agreedAt,

        @Schema(description = "누적 검증 횟수 (MY-01). 등급이 아니라 숫자다", example = "5")
        long verificationCount,

        @Schema(description = "연속 검증 횟수 (MY-01). HOME-09 배너와 같은 값", example = "3")
        int streakCount,

        @Schema(description = "현재 레벨 (1~5, HOME-04). `totalExp`에서 계산되며 저장된 컬럼이 아니다",
                example = "3")
        int level,

        @Schema(description = "누적 경험치 (HOME-04)", example = "320")
        int totalExp,

        @Schema(description = "다음 레벨 **컷오프 절대값**. 만렙(5)이면 `null` — "
                + "\"남은 exp\"는 앱이 `nextLevelExp − totalExp`로 계산한다",
                nullable = true, example = "450")
        Integer nextLevelExp
) {

    /**
     * @param latestConsent 가장 최근 동의 이력. 없으면 {@code null}
     */
    public static UserProfileResponse of(User user, ConsentHistory latestConsent,
                                         long verificationCount, int streakCount) {
        boolean agreed = latestConsent != null
                && ConsentPolicy.CURRENT_TERMS_VERSION.equals(latestConsent.getTermsVersion());

        return new UserProfileResponse(
                user.getId(),
                user.getNickname(),
                user.isOnboardingCompleted(),
                agreed,
                ConsentPolicy.CURRENT_TERMS_VERSION,
                latestConsent == null ? null : latestConsent.getTermsVersion(),
                latestConsent == null ? null : latestConsent.getCreatedAt(),
                verificationCount,
                streakCount,
                LevelPolicy.levelOf(user.getExp()),
                user.getExp(),
                LevelPolicy.nextLevelExp(user.getExp()));
    }

}
