package com.allday.sleep2skin_be.domain.skin;

import com.allday.sleep2skin_be.domain.skin.dto.response.MetricVerificationResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 적중률 — <b>판정된 지표들의 예보 정확도 평균</b>(2026-08-19 교체).
 *
 * <h2>개수 비율이 아니다</h2>
 *
 * <p>예전에는 {@code HIT 개수 ÷ 판정 수}였다. 분모가 최대 3이라 나올 수 있는 값이
 * {@code 0·33·67·100}(지표가 2개면 {@code 0·50·100})뿐이었고, <b>오차 6점과 오차 60점이 똑같이
 * "적중 아님"으로 뭉개졌다.</b> "지난번 대비 상승폭"도 {@code ±33}·{@code ±67}의 계단으로만
 * 움직였다.
 *
 * <p>지금은 지표마다 {@link ScoringPolicy#accuracy}로 연속값을 내고 그 평균을 낸다.
 * <b>판정({@code verdict})은 그대로다</b> — 라벨과 숫자가 다른 축이므로 한쪽만 바꾸면 조용히
 * 어긋난다.
 *
 * <h2>별도 컴포넌트로 뺀 이유</h2>
 *
 * <p><b>셀피 검증(HOME-07)과 배너(HOME-09)가 같은 숫자를 써야 한다.</b> 실제로 두 서비스에 같은
 * 식이 복제돼 있었고, 곡선으로 바뀌면 갈릴 여지가 더 커진다.
 * {@code VerificationStreakCalculator}와 같은 이유다 — <b>계산을 다시 적지 말 것.</b>
 */
@Component
public class HitRateCalculator {

    /**
     * <b>분모는 판정한 지표 수다</b> — {@code 3}도, {@code 검증 일수 × 3}도 아니다. 그날 예보가
     * 없던 지표는 판정 자체가 없었으므로 세지 않는다.
     *
     * <p><b>반올림은 여기서 한 번만 한다.</b> 지표마다 반올림한 뒤 평균내면 오차가 누적된다.
     *
     * <p>빈 리스트는 들어오지 않는다 — {@code DARK_CIRCLE}은 예보가 빈 상태가 될 수 없다
     * (erd.md §3.5). 방어 코드를 두면 <b>있을 수 없는 상태에 숫자를 하나 정해 주는 셈</b>이라
     * 두지 않는다.
     *
     * @param verifications 그날(또는 누적) 판정된 지표들. 비어 있지 않다
     * @return 0~100 정수 적중률(%)
     */
    public int calculate(List<MetricVerificationResponse> verifications) {
        double average = verifications.stream()
                .mapToDouble(HitRateCalculator::accuracyOf)
                .average()
                .orElseThrow(() -> new IllegalArgumentException("판정된 지표가 없다"));

        return (int) Math.round(average);
    }

    /**
     * <b>{@code difference}가 아니라 두 점수를 그대로 넘긴다.</b> {@code ScoringPolicy.accuracy}가
     * {@code verdict}와 같은 시그니처를 갖게 해, 두 축이 같은 입력에서 나온다는 것을 호출부에서
     * 보이게 한다.
     */
    private static double accuracyOf(MetricVerificationResponse verification) {
        return ScoringPolicy.accuracy(
                verification.forecast().score(), verification.measured().score());
    }

}
