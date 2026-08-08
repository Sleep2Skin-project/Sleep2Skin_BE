package com.allday.sleep2skin_be.domain.skin.dto;

import com.allday.sleep2skin_be.domain.skin.entity.SleepFeature;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 스코어링 입력. 하룻밤의 수면 집계 + 그 사용자의 개인 가중치다.
 *
 * <p><b>DB를 보지 않는다.</b> 개인 가중치를 조회 결과로 받는 이유는 스코어링 단위 테스트가
 * DB 없이 돌아야 하기 때문이다 — 숫자가 틀리면 제품 전체가 틀리는 로직인데 테스트가
 * 무거워지면 아무도 돌리지 않는다(erd.md §4).
 *
 * <p><b>{@code null}은 "0"이 아니라 "없음"이다.</b> 결측 피처는 항을 빼고 재정규화하며 기본값을
 * 대입하지 않는다 — 채워 넣은 값으로 낸 예보가 실측과 대조되면 존재하지 않았던 값이 개인
 * 가중치에 반영된다. 예보 하나를 살리려고 학습 전체를 망가뜨리는 거래다(§10.6).
 *
 * @param deepSleepMinutes    분 그대로 받는다. 비율 환산은 스코어링이 한다
 * @param stagedSleepMinutes  {@code deep + rem + core}. <b>총 수면이 아니다</b> — 비율의 분모이며
 *                            {@code 0}이면 {@code BARRIER}가 빈 상태가 된다(§10.5·§10.6)
 * @param bedtimeRegularitySd 최근 7일 {@code sleep_onset_time} 표준편차(분).
 *                            <b>이력 3일 미만이면 {@code null}</b> — 2점으로 낸 표준편차는
 *                            두 밤의 차이일 뿐 규칙성이 아니다
 * @param hrv                 야간 RMSSD(ms). 워치 미착용 시 {@code null}
 * @param restingHeartRate    야간 안정시 심박(bpm). 워치 미착용 시 {@code null}
 * @param personalWeights     {@code personal_weight.weight} 배수. 없는 피처는 {@code 1.0}으로 본다.
 *                            첫 검증 전 사용자는 빈 맵이고 그때 {@code w'}는 일반 가중치와 같다.
 *                            <b>피처만으로 키를 잡은 것은 §10.3의 매핑이 1:1이기 때문</b>이다 —
 *                            한 피처가 두 지표에 붙게 되면 이 키를 (피처, 지표)로 넓혀야 한다
 */
public record ScoringCommand(

        int awakeCount,

        int totalSleepMinutes,

        int deepSleepMinutes,

        int remSleepMinutes,

        int stagedSleepMinutes,

        Double bedtimeRegularitySd,

        BigDecimal hrv,

        Integer restingHeartRate,

        Map<SleepFeature, BigDecimal> personalWeights
) {

    /** 워치를 차지 않고 잔 밤인가. {@code HRV}와 안정시 심박은 같은 착용 여부에 의존해 함께 결측된다. */
    public boolean isWatchDataMissing() {
        return hrv == null && restingHeartRate == null;
    }

}
