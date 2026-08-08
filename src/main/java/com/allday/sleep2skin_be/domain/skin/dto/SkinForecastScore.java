package com.allday.sleep2skin_be.domain.skin.dto;

import com.allday.sleep2skin_be.domain.skin.entity.SkinMetric;
import com.allday.sleep2skin_be.domain.skin.entity.SleepFeature;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 스코어링 결과. 저장도 응답 변환도 하지 않는 계산 결과다.
 *
 * <p><b>지표 하나가 비어도 나머지는 정상 발급된다.</b> 워치를 안 찬 밤은 혈색만, 단계가 하나도
 * 안 잡힌 밤은 장벽만 빈 상태다(§10.6).
 *
 * @param metricScores   <b>산출된 지표만 들어 있다.</b> 빈 상태인 지표는 키 자체가 없다 —
 *                       {@code 0}과 결측을 구분하기 위해서다
 * @param unavailable    산출하지 못한 지표와 사유. {@code null}만 주면 앱이 문구를 고를 수 없다
 * @param featureScores  그날 실제로 스코어링에 참여한 피처의 부분점수 {@code s(f)}.
 *                       결측 피처는 키가 없다
 */
public record SkinForecastScore(

        Map<SkinMetric, Integer> metricScores,

        List<UnavailableMetric> unavailable,

        Map<SleepFeature, Double> featureScores
) {

    /** 빈 상태면 {@code null}. */
    public Integer scoreOf(SkinMetric metric) {
        return metricScores.get(metric);
    }

    /**
     * 그날 스코어링에 참여한 피처. <b>HOME-08은 이 피처의 가중치만 갱신한다</b> — 워치를 안 찬
     * 밤의 오차를 {@code HRV} 탓으로 돌리면 안 된다(§10.6).
     *
     * <p>빈 상태인 지표의 피처는 여기 들어올 수 없다. 지표가 비는 조건이 "그 지표의 피처가 전부
     * 결측"이라, 참여한 피처가 하나라도 있으면 그 지표는 산출된다.
     */
    public Set<SleepFeature> scoredFeatures() {
        return featureScores.keySet();
    }

    /** 산출하지 못한 지표 하나. */
    public record UnavailableMetric(SkinMetric metric, UnavailableReason reason) {
    }

}
