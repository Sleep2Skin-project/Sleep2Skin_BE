package com.allday.sleep2skin_be.domain.skin;

import com.allday.sleep2skin_be.domain.skin.dto.response.MetricVerificationResponse;
import com.allday.sleep2skin_be.domain.skin.dto.response.PersonalModelUpdateResponse;
import com.allday.sleep2skin_be.domain.skin.dto.response.SelfieVerificationResponse;
import com.allday.sleep2skin_be.domain.skin.dto.response.SkippedMetricResponse;
import com.allday.sleep2skin_be.domain.skin.entity.SkinForecast;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMeasurement;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMetric;
import com.allday.sleep2skin_be.domain.skin.repository.SkinForecastRepository;
import com.allday.sleep2skin_be.domain.skin.repository.SkinMeasurementRepository;
import com.allday.sleep2skin_be.domain.sleep.entity.SleepSession;
import com.allday.sleep2skin_be.domain.sleep.repository.SleepSessionRepository;
import com.allday.sleep2skin_be.global.exception.BusinessException;
import com.allday.sleep2skin_be.global.exception.ErrorCode;
import com.allday.sleep2skin_be.global.infra.openai.SkinVisionScores;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 실측 저장 + 예보 대조 (HOME-07).
 *
 * <p><b>여기서부터가 트랜잭션이다.</b> LLM 호출은 {@link SelfieAnalysisService}가 이 앞에서
 * 트랜잭션 밖에 두고 끝낸다 — 최대 30초짜리 외부 호출이 DB 커넥션을 잡고 있으면 셀피가 몰릴 때
 * 커넥션 풀이 고갈되어 <b>수면 업로드까지 함께 막힌다.</b>
 *
 * <p>{@code SelfieAnalysisService}와 다른 빈으로 나눈 것도 그 때문이다. 같은 빈의 메서드를
 * 호출하면 프록시를 타지 않아 {@code @Transactional}이 걸리지 않는다.
 *
 * <h2>대조하지 못하는 지표가 있다</h2>
 *
 * <p><b>실측은 항상 3종이 나온다.</b> LLM은 예보와 무관하게 셋을 산출하고 {@code skin_measurement}도
 * 셋 다 {@code NOT NULL}이다. 갈리는 것은 <b>예보 쪽</b>이다 — 워치를 안 찬 밤은 혈색이, 단계가
 * 안 잡힌 밤은 장벽이 비어 있다(§10.6). 그 지표는 판정에서 빠지고 <b>적중률 분모에서도 빠진다.</b>
 *
 * <p>0점으로 채워 대조하면 존재하지 않는 오차가 적중률에 섞이고, 같은 값이 HOME-08의 학습 입력이
 * 되어 <b>없던 값이 개인 가중치를 움직인다.</b>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkinVerificationService {

    private final SkinForecastRepository skinForecastRepository;
    private final SkinMeasurementRepository skinMeasurementRepository;
    private final SleepSessionRepository sleepSessionRepository;

    /**
     * 실측을 저장하고 그날 예보와 대조한다.
     *
     * @param scores LLM이 산출한 지표 3종. <b>여기까지 이미지가 따라오지 않는다</b> — 숫자만 넘어온다
     */
    @Transactional
    public SelfieVerificationResponse record(Long userId, LocalDate baseDate, SkinVisionScores scores) {
        SkinForecast forecast = skinForecastRepository.findByUserIdAndBaseDate(userId, baseDate)
                .orElseThrow(() -> new BusinessException(ErrorCode.SKIN_FORECAST_NOT_FOUND,
                        "대조할 예보가 없다 userId=" + userId + " baseDate=" + baseDate));

        SkinMeasurement measurement = save(userId, baseDate, scores);

        boolean watchDataMissing = watchDataMissing(userId, baseDate);
        List<MetricVerificationResponse> verifications = new ArrayList<>();
        List<SkippedMetricResponse> skipped = new ArrayList<>();

        // 다크서클은 예보가 빈 상태가 될 수 없다 — 컬럼이 NOT NULL 이라 타입이 그걸 말한다
        verifications.add(MetricVerificationResponse.of(
                SkinMetric.DARK_CIRCLE, forecast.getDarkCircle(), measurement.getDarkCircle()));

        collect(SkinMetric.COMPLEXION, forecast.getComplexion(), measurement.getComplexion(),
                watchDataMissing, verifications, skipped);
        collect(SkinMetric.BARRIER, forecast.getBarrier(), measurement.getBarrier(),
                watchDataMissing, verifications, skipped);

        log.info("셀피 검증 완료 userId={} baseDate={} 적중률={}% 대조={} 제외={}",
                userId, baseDate, hitRate(verifications), verifications.size(), skipped.size());

        return new SelfieVerificationResponse(baseDate, measurement.getAnalyzedAt(),
                List.copyOf(verifications), List.copyOf(skipped), hitRate(verifications),
                PersonalModelUpdateResponse.notYetImplemented());
    }

    /**
     * <b>중복 검사는 이미 {@link SelfieAnalysisService}가 LLM 호출 전에 했다.</b> 그래도 여기서
     * 유니크 제약 위반을 잡는 것은, 같은 사용자의 두 요청이 검사와 저장 사이에 겹칠 수 있기
     * 때문이다 — 드물지만 그때 500이 나가면 앱은 재시도할 수 없다고 판단한다.
     */
    private SkinMeasurement save(Long userId, LocalDate baseDate, SkinVisionScores scores) {
        try {
            return skinMeasurementRepository.save(SkinMeasurement.builder()
                    .userId(userId)
                    .baseDate(baseDate)
                    .darkCircle(scores.darkCircle())
                    .complexion(scores.complexion())
                    .barrier(scores.barrier())
                    .analyzedAt(OffsetDateTime.now())
                    .build());

        } catch (DataIntegrityViolationException e) {
            log.warn("같은 날 검증이 동시에 들어왔다 userId={} baseDate={}", userId, baseDate);
            throw new BusinessException(ErrorCode.VERIFICATION_ALREADY_DONE,
                    "하루 1회 제약 위반 userId=" + userId + " baseDate=" + baseDate);
        }
    }

    /** 예보가 있으면 판정하고, 없으면 실측만 담아 {@code skipped}로 보낸다. */
    private void collect(SkinMetric metric, Integer forecast, int measured, boolean watchDataMissing,
                         List<MetricVerificationResponse> verifications,
                         List<SkippedMetricResponse> skipped) {

        if (forecast == null) {
            skipped.add(SkippedMetricResponse.of(metric, measured, watchDataMissing));
            return;
        }
        verifications.add(MetricVerificationResponse.of(metric, forecast, measured));
    }

    /**
     * <b>분모는 대조한 지표 수다 — 3이 아니다.</b> 비지 않는 것은 다크서클이 항상 판정되기 때문이다.
     */
    private int hitRate(List<MetricVerificationResponse> verifications) {
        long hits = verifications.stream().filter(MetricVerificationResponse::isHit).count();
        return (int) Math.round(hits * 100.0 / verifications.size());
    }

    /**
     * 빈 예보의 사유를 가르는 유일한 입력. 예보에 사유 컬럼이 없어(파생값이라 두지 않았다)
     * 그날 세션에서 되짚는다 — {@code SkinForecastService}가 조회 경로에서 하는 것과 같다.
     */
    private boolean watchDataMissing(Long userId, LocalDate baseDate) {
        Optional<SleepSession> session = sleepSessionRepository.findByUserIdAndSleepDate(userId, baseDate);
        if (session.isEmpty()) {
            log.warn("예보는 있는데 수면 세션이 없다 userId={} baseDate={}", userId, baseDate);
            return true;
        }
        return session.get().isWatchDataMissing();
    }

}
