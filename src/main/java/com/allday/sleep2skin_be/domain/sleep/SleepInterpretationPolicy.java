package com.allday.sleep2skin_be.domain.sleep;

import com.allday.sleep2skin_be.domain.skin.ScoringPolicy;
import com.allday.sleep2skin_be.domain.skin.entity.SleepFeature;
import com.allday.sleep2skin_be.domain.sleep.entity.SleepSession;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 수면 통역 카드(HOME-02)의 문구와 판정 기준.
 *
 * <p><b>{@code ScoringPolicy}와 나눈 이유는 성격이 다르기 때문이다.</b> 저쪽은 알고리즘
 * 파라미터(숫자)이고 이쪽은 콘텐츠(문장)다. 문구는 기획이 다듬고 숫자는 개발이 조정한다.
 *
 * <p><b>기준치를 따로 두지 않는다.</b> "부족했다"의 판단은 {@code ScoringPolicy}의 정규화
 * 곡선(§10.5)이 그대로 한다 — 부분점수가 낮은 피처가 곧 기준치에서 가장 멀어진 피처다.
 * 기준을 따로 두면 카드는 "충분히 주무셨어요"라고 하는데 예보는 총 수면을 감점한 상태가 될 수
 * 있고, <b>같은 화면에서 두 문장이 서로 반박한다.</b>
 */
public final class SleepInterpretationPolicy {

    private SleepInterpretationPolicy() {
    }

    /**
     * 지적할 것이 없다고 볼 부분점수 — <b>§10.1의 안정 등급 시작점을 그대로 쓴다.</b>
     *
     * <p>모든 피처가 이 값 이상이면 칭찬 문구를 낸다. <b>분기가 없으면 전부 100점인 밤에도
     * 무언가를 "부족하다"고 지목하게 된다</b> — 잘 잔 사용자에게 없는 문제를 알려주는 셈이고,
     * 예보가 0점과 결측을 구분하는 것과 같은 종류의 실수다.
     *
     * <p>새 임시값을 만들지 않으려고 확정된 컷오프를 재사용했다. 덕분에 카드 문구와 예보 등급이
     * 같은 기준을 쓴다.
     */
    public static boolean isSatisfactory(double featureScore) {
        return featureScore > ScoringPolicy.GRADE_NORMAL_MAX;   // 확정값 (PRD §10.1)
    }

    /** 모든 피처가 안정 구간일 때. */
    public static String praiseHeadline() {
        return "어젯밤은 회복에 충분한 잠이었어요.";
    }

    /**
     * 가장 부족했던 피처를 지목하는 문장.
     *
     * <p><b>{@code DARK_CIRCLE}은 "회복된 정도"다</b> — 각성이 많을수록 점수가 내려간다.
     * 문구에서 이 방향이 뒤집히면 값 범위는 정상이라 아무 제약에도 안 걸린다.
     *
     * <p><b>{@code REM_SLEEP} → {@code BARRIER}는 직접 근거가 약하다는 것이 문서에 명시돼
     * 있다</b>(§10.3). 그래서 이 문구만 피부 효과를 단정하지 않는다 — 문서와 리포트가 같은
     * 태도를 유지해야 한다.
     */
    public static String improveHeadline(SleepFeature feature, SleepSession session,
                                         Double bedtimeRegularitySd) {
        return switch (feature) {
            case AWAKE_COUNT -> "밤중에 %d번 깼어요. 다크서클 회복이 더뎌질 수 있어요."
                    .formatted(session.getAwakeCount());
            case TOTAL_SLEEP -> "%s 주무셨어요. 회복에 필요한 시간에는 조금 모자랐어요."
                    .formatted(formatDuration(session.getTotalSleepMinutes()));
            case DEEP_SLEEP -> "깊은 수면이 전체의 %d%%였어요. 피부 장벽은 이 구간에서 회복돼요."
                    .formatted(stagePercentage(session.getDeepSleepMinutes(), session));
            case REM_SLEEP -> "REM 수면이 전체의 %d%%로 권장 범위보다 낮았어요."
                    .formatted(stagePercentage(session.getRemSleepMinutes(), session));
            case BEDTIME_REGULARITY -> "최근 취침 시각이 ±%d분으로 흔들렸어요. 혈색은 리듬에 민감해요."
                    .formatted(Math.round(bedtimeRegularitySd));
            case HRV -> "밤새 심박변이도가 %sms로 낮았어요. 몸이 충분히 이완되지 못했어요."
                    .formatted(formatHrv(session.getHrv()));
            case RESTING_HEART_RATE -> "야간 안정시 심박이 %dbpm으로 높았어요. 혈색이 흐려질 수 있어요."
                    .formatted(session.getRestingHeartRate());
        };
    }

    /** 앱이 한국어 이름을 따로 하드코딩하면 우리 문구와 어긋나므로 여기서 함께 내려준다. */
    public static String label(SleepFeature feature) {
        return switch (feature) {
            case AWAKE_COUNT -> "야간 각성";
            case TOTAL_SLEEP -> "총 수면 시간";
            case DEEP_SLEEP -> "깊은 수면";
            case REM_SLEEP -> "REM 수면";
            case BEDTIME_REGULARITY -> "취침 규칙성";
            case HRV -> "심박변이도";
            case RESTING_HEART_RATE -> "안정시 심박";
        };
    }

    private static String formatDuration(int minutes) {
        int hours = minutes / 60;
        int remainder = minutes % 60;
        return remainder == 0 ? "%d시간".formatted(hours) : "%d시간 %d분".formatted(hours, remainder);
    }

    /** 분모는 총 수면이 아니라 단계 합이다 — 카드가 예보와 다른 비율을 말하면 안 된다. */
    private static int stagePercentage(int stageMinutes, SleepSession session) {
        return (int) Math.round(stageMinutes * 100.0 / session.stagedSleepMinutes());
    }

    private static String formatHrv(BigDecimal hrv) {
        return hrv.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

}
