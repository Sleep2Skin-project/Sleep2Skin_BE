package com.allday.sleep2skin_be.domain.skin;

import com.allday.sleep2skin_be.domain.game.ExpService;
import com.allday.sleep2skin_be.domain.game.LevelPolicy;
import com.allday.sleep2skin_be.domain.game.dto.ExpGrantCommand;
import com.allday.sleep2skin_be.domain.game.dto.response.ExpResponse;
import com.allday.sleep2skin_be.domain.game.entity.ExpReason;
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
 * 실측 저장 + 예보 대조 + 학습 트리거 (HOME-07→08).
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
 *
 * <h2>연속 검증 보상이 여기서 지급된다 (HOME-04 — prd.md §10.9)</h2>
 *
 * <table border="1">
 *   <caption>{@code VERIFICATION_STREAK} 지급액</caption>
 *   <tr><th>{@code streakCount}</th><th>{@code exp.gained}</th></tr>
 *   <tr><td>{@code 1} (첫 검증 또는 연속이 끊긴 뒤 첫 검증)</td>
 *       <td>{@code 0} — <b>보상 구간이 2일부터다</b></td></tr>
 *   <tr><td>{@code 2} / {@code 3} / {@code 4}</td><td>{@code +5} / {@code +10} / {@code +15}</td></tr>
 *   <tr><td>{@code 5} 이상</td><td>{@code +25} (상한 구간, 매일)</td></tr>
 * </table>
 *
 * <p><b>적립은 저장·검증이 끝난 뒤다.</b> 분석이 실패하면 행도 exp도 생기지 않는다.
 * 재시도가 이중 지급으로 이어지지도 않는다 — 검증은 하루 1회({@code VERIFICATION_ALREADY_DONE})라
 * 두 번째 요청은 여기까지 오지 않고, 그래도 {@code exp_grant}의 유니크가 마지막 방어선이다.
 *
 * <p><b>출석 체크인이 이 보상을 대신 주지 않는다.</b> 출석은 앱을 켠 사실에, 연속 보상은 검증한
 * 사실에 붙는다 — 저쪽에서 함께 지급하면 셀피를 찍지 않아도 보상이 나간다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkinVerificationService {

    private final SkinForecastRepository skinForecastRepository;
    private final SkinMeasurementRepository skinMeasurementRepository;
    private final SleepSessionRepository sleepSessionRepository;
    private final SkinModelService skinModelService;
    private final VerificationStreakCalculator streakCalculator;
    private final ExpService expService;

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

        // 세션을 한 번만 읽는다 — 빈 지표의 사유와 학습의 부분점수가 같은 행에서 나온다
        Optional<SleepSession> session = sleepSessionRepository.findByUserIdAndSleepDate(userId, baseDate);
        boolean watchDataMissing = watchDataMissing(session, userId, baseDate);

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

        int streakCount = streakCount(userId, baseDate);

        return new SelfieVerificationResponse(baseDate, measurement.getAnalyzedAt(),
                List.copyOf(verifications), List.copyOf(skipped), hitRate(verifications),
                learn(userId, session, verifications),
                streakCount, grantStreakExp(userId, baseDate, streakCount));
    }

    /**
     * 연속 검증 횟수. <b>방금 저장한 실측을 포함한 값이다</b> — 조회 직전에 영속성 컨텍스트가
     * flush되므로 오늘 행이 결과에 들어온다. 검증 전 값을 쓰면 응답의 팝업 문구와 지급액이
     * 하루씩 어긋난다.
     *
     * <p><b>계산은 {@link VerificationStreakCalculator} 한 곳에서만 한다.</b> HOME-09 배너·
     * MY-01 프로필·출석 체크인이 같은 숫자를 써야 하고(prd.md §4.2), 각자 계산하면 화면들이
     * 어긋난다 — 어긋나도 값 범위는 정상이라 알아채기 어렵다. <b>여기에 계산을 다시 적지 말 것.</b>
     */
    private int streakCount(Long userId, LocalDate baseDate) {
        return streakCalculator.calculate(baseDate,
                skinMeasurementRepository.findVerifiedBaseDates(userId, baseDate));
    }

    /**
     * 연속 검증 보상 (§10.9). <b>1일차는 {@code 0}이라 이력 행도 생기지 않는다</b> —
     * {@code ExpService}가 0 이하를 건너뛴다.
     */
    private ExpResponse grantStreakExp(Long userId, LocalDate baseDate, int streakCount) {
        return expService.grantDaily(userId, baseDate, List.of(
                new ExpGrantCommand(ExpReason.VERIFICATION_STREAK,
                        LevelPolicy.verificationStreakExp(streakCount))));   // 확정값 (PRD §10.9)
    }

    /**
     * 학습 (HOME-08). <b>검증과 같은 트랜잭션이다</b> — 실측만 남고 가중치가 안 바뀌면 그날의
     * 오차를 영영 학습하지 못한다. 하루 1회 제약 때문에 다시 시도할 방법도 없다.
     *
     * <p>세션이 없으면 부분점수를 계산할 수 없어 건너뛴다. 예보가 있는데 세션이 없는 것은 데이터가
     * 어긋난 상태이지만, <b>검증 자체는 이미 성립했으므로</b> 응답까지 실패시키지 않는다.
     */
    private PersonalModelUpdateResponse learn(Long userId, Optional<SleepSession> session,
                                              List<MetricVerificationResponse> verifications) {
        return session
                .map(found -> skinModelService.learn(userId, found, verifications))
                .orElseGet(PersonalModelUpdateResponse::notUpdated);
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
                    .pigmentationDetected(scores.pigmentationDetected())
                    .acneScarDetected(scores.acneScarDetected())
                    .agingDetected(scores.agingDetected())
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
    private boolean watchDataMissing(Optional<SleepSession> session, Long userId, LocalDate baseDate) {
        if (session.isEmpty()) {
            log.warn("예보는 있는데 수면 세션이 없다 userId={} baseDate={}", userId, baseDate);
            return true;
        }
        return session.get().isWatchDataMissing();
    }

}
