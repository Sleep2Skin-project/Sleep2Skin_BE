package com.allday.sleep2skin_be.domain.sleep;

import com.allday.sleep2skin_be.domain.game.ExpService;
import com.allday.sleep2skin_be.domain.game.LevelPolicy;
import com.allday.sleep2skin_be.domain.game.dto.ExpGrantCommand;
import com.allday.sleep2skin_be.domain.game.dto.response.ExpResponse;
import com.allday.sleep2skin_be.domain.game.entity.ExpReason;
import com.allday.sleep2skin_be.domain.skin.ScoringPolicy;
import com.allday.sleep2skin_be.domain.skin.SkinScoringEngine;
import com.allday.sleep2skin_be.domain.skin.dto.SkinForecastScore;
import com.allday.sleep2skin_be.domain.skin.dto.response.SkinForecastResponse;
import com.allday.sleep2skin_be.domain.skin.entity.PersonalWeight;
import com.allday.sleep2skin_be.domain.skin.entity.SkinForecast;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMetric;
import com.allday.sleep2skin_be.domain.skin.entity.SleepFeature;
import com.allday.sleep2skin_be.domain.skin.repository.PersonalWeightRepository;
import com.allday.sleep2skin_be.domain.skin.repository.SkinForecastRepository;
import com.allday.sleep2skin_be.domain.skin.repository.SkinMeasurementRepository;
import com.allday.sleep2skin_be.domain.sleep.dto.SleepNormalizationCommand;
import com.allday.sleep2skin_be.domain.sleep.dto.SleepNormalizationResult;
import com.allday.sleep2skin_be.domain.sleep.dto.SleepSegmentCommand;
import com.allday.sleep2skin_be.domain.sleep.dto.response.SleepSessionUploadResponse;
import com.allday.sleep2skin_be.domain.sleep.dto.response.SleepSessionUploadResult;
import com.allday.sleep2skin_be.domain.sleep.entity.SleepSession;
import com.allday.sleep2skin_be.domain.sleep.entity.SleepStageSegment;
import com.allday.sleep2skin_be.domain.sleep.repository.SleepSessionRepository;
import com.allday.sleep2skin_be.domain.sleep.repository.SleepStageSegmentRepository;
import com.allday.sleep2skin_be.domain.user.repository.UserRepository;
import com.allday.sleep2skin_be.global.exception.BusinessException;
import com.allday.sleep2skin_be.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 수면 세션 수신 → 예보 산출 (ONB-03 + HOME-03).
 *
 * <p><b>업로드와 스코어링이 한 트랜잭션이다.</b> 앱은 켜질 때마다 이 API 하나를 호출하고 홈
 * 화면을 그린다.
 *
 * <h2>같은 수면 데이터는 재처리하지 않는다</h2>
 *
 * <p>앱은 시작될 때마다 업로드하므로 새 수면이 생기기 전까지 같은 세션이 계속 온다.
 * 정규화 결과의 해시를 비교해 <b>저장·스코어링을 시작하기 전에</b> 중단한다.
 *
 * <table border="1">
 *   <caption>재수신 판정</caption>
 *   <tr><th>상황</th><th>created</th><th>processed</th><th>동작</th></tr>
 *   <tr><td>그날 첫 수신</td><td>true</td><td>true</td><td>저장 + 스코어링</td></tr>
 *   <tr><td>해시 동일</td><td>false</td><td>false</td><td>기존 예보 반환</td></tr>
 *   <tr><td>해시 다름 + 검증 완료</td><td>false</td><td>false</td><td>기존 예보 반환</td></tr>
 *   <tr><td>해시 다름 + 검증 전</td><td>false</td><td>true</td><td>갱신 + 재산출</td></tr>
 * </table>
 *
 * <p><b>검증을 마친 날의 예보는 절대 바뀌지 않는다.</b> 바뀌면 이미 끝난 셀피 검증의 대조 기준이
 * 사후에 달라져 적중률이 훼손되고 개인 가중치가 중복 학습된다. 성능이 아니라 정확성 문제이며,
 * 이 규칙 덕분에 예보 이력 테이블이 필요 없다.
 *
 * <h2>수면 점수 보상 두 종이 여기서 지급된다 (HOME-04 — prd.md §10.9)</h2>
 *
 * <table border="1">
 *   <caption>적립 조건</caption>
 *   <tr><th>사유</th><th>조건</th><th>양</th></tr>
 *   <tr><td>{@code SLEEP_SCORE_IMPROVED}</td><td>전날 수면 점수보다 올랐음</td>
 *       <td>{@code (오늘 − 어제) × 2}</td></tr>
 *   <tr><td>{@code SLEEP_SCORE_HIGH}</td><td>오늘 수면 점수 90 이상</td><td>{@code +10}</td></tr>
 * </table>
 *
 * <p><b>둘은 겹칠 수 있다</b> — 90점을 넘기며 오른 날은 {@code reasons}에 둘 다 실린다.
 * 90점 보상은 증가 여부와 무관한데, 95점을 유지하는 사용자가 보상을 못 받는 일이 없어야 하기
 * 때문이다.
 *
 * <p>⚠️ <b>{@code processed: false}면 적립하지 않는다.</b> 재처리를 하지 않은 요청이라 새로
 * 산출된 점수가 없다 — 앱이 시작할 때마다 호출하므로 <b>여기서 매번 적립하면 앱을 다섯 번 켤 때
 * 다섯 번 붙는다.</b>
 *
 * <p>⚠️ <b>{@code processed: true}인데 이미 지급된 경우가 있다.</b> 해시가 다르고 검증 전이면
 * 같은 날 두 번째 재산출이 일어나고, 그때 수면 점수가 바뀌면 조건이 다시 성립한다.
 * {@code exp_grant}의 유니크가 두 번째 지급을 막으며 그 경우 {@code gained}는 {@code 0}이다.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SleepSessionService {

    private final UserRepository userRepository;
    private final SleepSessionRepository sleepSessionRepository;
    private final SleepStageSegmentRepository sleepStageSegmentRepository;
    private final SkinForecastRepository skinForecastRepository;
    private final SkinMeasurementRepository skinMeasurementRepository;
    private final PersonalWeightRepository personalWeightRepository;
    private final SleepSessionNormalizer normalizer;
    private final SkinScoringEngine scoringEngine;
    private final BedtimeRegularityCalculator bedtimeRegularityCalculator;
    private final SleepScoreCalculator sleepScoreCalculator;
    private final ExpService expService;

    @Transactional
    public SleepSessionUploadResult upload(Long userId, SleepNormalizationCommand command) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND,
                    "수면 데이터를 받을 사용자가 없다 userId=" + userId);
        }

        // 해시 비교가 저장보다 먼저다 — 판정에 필요한 값이 정규화 결과에서만 나온다
        SleepNormalizationResult normalized = normalizer.normalize(command);
        Optional<SleepSession> existing =
                sleepSessionRepository.findByUserIdAndSleepDate(userId, normalized.sleepDate());

        if (existing.isEmpty()) {
            return create(userId, normalized);
        }
        SleepSession session = existing.get();

        if (session.getPayloadHash().equals(normalized.payloadHash())) {
            return unchanged(userId, session, normalized, "같은 페이로드 재수신");
        }
        if (skinMeasurementRepository.findByUserIdAndBaseDate(userId, normalized.sleepDate()).isPresent()) {
            return unchanged(userId, session, normalized, "검증을 마친 날이라 예보를 고정한다");
        }
        return update(userId, session, normalized);
    }

    /** 그날 첫 수신 — 세션·구간·예보를 새로 만든다. */
    private SleepSessionUploadResult create(Long userId, SleepNormalizationResult normalized) {
        SleepSession session = sleepSessionRepository.save(normalized.toEntity(userId));
        saveSegments(session, normalized.segments());

        SkinForecastScore score = calculateScore(userId, normalized);
        SkinForecast forecast = skinForecastRepository.save(SkinForecast.builder()
                .userId(userId)
                .baseDate(normalized.sleepDate())
                .darkCircle(requireDarkCircle(score))
                .complexion(score.scoreOf(SkinMetric.COMPLEXION))
                .barrier(score.scoreOf(SkinMetric.BARRIER))
                .build());

        Integer sleepScore = sleepScoreCalculator.calculate(score.featureScores());

        return new SleepSessionUploadResult(true,
                SleepSessionUploadResponse.of(true, session, sleepScore,
                        SkinForecastResponse.of(forecast, normalized.isWatchDataMissing()),
                        grantSleepScoreExp(userId, normalized.sleepDate(), sleepScore)));
    }

    /** 내용이 다른 데이터가 검증 전에 다시 왔다 — 갱신하고 재산출한다. */
    private SleepSessionUploadResult update(Long userId, SleepSession session,
                                            SleepNormalizationResult normalized) {
        session.applyNormalization(normalized);
        replaceSegments(session, normalized.segments());

        SkinForecastScore score = calculateScore(userId, normalized);
        SkinForecast forecast = skinForecastRepository
                .findByUserIdAndBaseDate(userId, normalized.sleepDate())
                .orElseThrow(() -> new IllegalStateException(
                        "세션은 있는데 예보가 없다 — 같은 트랜잭션에서 함께 만들어져야 한다 userId="
                                + userId + " baseDate=" + normalized.sleepDate()));
        forecast.updateScores(requireDarkCircle(score),
                score.scoreOf(SkinMetric.COMPLEXION), score.scoreOf(SkinMetric.BARRIER));

        Integer sleepScore = sleepScoreCalculator.calculate(score.featureScores());

        return new SleepSessionUploadResult(false,
                SleepSessionUploadResponse.of(true, session, sleepScore,
                        SkinForecastResponse.of(forecast, normalized.isWatchDataMissing()),
                        grantSleepScoreExp(userId, normalized.sleepDate(), sleepScore)));
    }

    /**
     * 아무것도 바꾸지 않고 기존 예보를 그대로 돌려준다.
     *
     * <p><b>스코어링을 다시 돌리지 않는다.</b> 저장된 점수가 그날의 정답이며, 그 사이 개인 가중치가
     * 학습으로 바뀌었더라도 예보는 산출 시점의 값으로 남아야 한다.
     *
     * <p>빈 지표의 사유는 저장된 {@code null}에서 되짚는다 — 예보에는 사유 컬럼이 없고, 있어도
     * 파생값이라 두지 않는다.
     *
     * <p><b>exp도 적립하지 않는다.</b> 새로 산출된 점수가 없으므로 줄 근거가 없고, 앱이 시작할
     * 때마다 호출하므로 여기서 적립하면 <b>앱을 다섯 번 켤 때 다섯 번 붙는다.</b> 그래도
     * {@code exp} 객체는 현재 상태를 담아 나간다 — 앱이 응답마다 존재 여부를 분기하지 않게.
     *
     * <p>수면 점수는 저장된 세션에서 다시 계산해 실어 보낸다. 저장하지 않는 파생값이라 조회
     * 경로에서도 매번 계산하는 것이 원칙이다(prd.md §10.8).
     */
    private SleepSessionUploadResult unchanged(Long userId, SleepSession session,
                                               SleepNormalizationResult normalized, String reason) {
        log.info("수면 재수신 처리 안 함 userId={} sleepDate={} 사유={}",
                userId, session.getSleepDate(), reason);

        SkinForecast forecast = skinForecastRepository
                .findByUserIdAndBaseDate(userId, session.getSleepDate())
                .orElseThrow(() -> new IllegalStateException(
                        "세션은 있는데 예보가 없다 userId=" + userId + " baseDate=" + session.getSleepDate()));

        return new SleepSessionUploadResult(false,
                SleepSessionUploadResponse.of(false, session,
                        sleepScoreCalculator.calculateFor(userId, session.getSleepDate()),
                        SkinForecastResponse.of(forecast, normalized.isWatchDataMissing()),
                        expService.current(userId)));
    }

    /**
     * 수면 점수 보상 두 종을 적립한다 (§10.9).
     *
     * <p><b>재처리한 요청에서만 호출된다</b> — {@code unchanged} 경로는 여기로 오지 않는다.
     *
     * <p><b>전날 점수가 없으면 증가 보상은 지급되지 않는다.</b> 비교 대상이 없는 것이지 0점에서
     * 오른 것이 아니다 — 0에서 올랐다고 치면 신규 사용자의 첫날이 {@code +180}을 받는다.
     * 판정은 {@link LevelPolicy#sleepScoreImprovedExp}가 하고 여기서는 값만 모은다.
     *
     * <p>오늘 점수가 {@code null}인 날(참여 피처 0개)은 두 보상 모두 조건이 성립하지 않는다.
     */
    private ExpResponse grantSleepScoreExp(Long userId, LocalDate sleepDate, Integer sleepScore) {
        if (sleepScore == null) {
            return expService.current(userId);
        }

        Integer yesterday = sleepScoreCalculator.calculateFor(userId, sleepDate.minusDays(1));

        List<ExpGrantCommand> commands = new ArrayList<>();
        // 0 이하는 ExpService가 건너뛴다 — 조건이 성립하지 않은 날에 이력 행이 생기면 안 된다
        commands.add(new ExpGrantCommand(ExpReason.SLEEP_SCORE_IMPROVED,
                LevelPolicy.sleepScoreImprovedExp(sleepScore, yesterday)));
        if (sleepScore >= LevelPolicy.SLEEP_SCORE_HIGH_THRESHOLD) {   // 확정값 (PRD §10.9)
            commands.add(new ExpGrantCommand(ExpReason.SLEEP_SCORE_HIGH,
                    LevelPolicy.SLEEP_SCORE_HIGH_EXP));
        }

        return expService.grantDaily(userId, sleepDate, commands);
    }

    private SkinForecastScore calculateScore(Long userId, SleepNormalizationResult normalized) {
        // 이번 밤의 잠든 시각을 DB가 아니라 정규화 결과에서 넘긴다 — 갱신 경로에서는 DB에
        // 아직 옛 값이 들어 있어, 저장 순서에 따라 규칙성이 달라진다
        Double bedtimeRegularitySd = bedtimeRegularityCalculator.calculate(
                userId, normalized.sleepDate(), normalized.sleepOnsetTime());

        return scoringEngine.score(
                normalized.toScoringCommand(bedtimeRegularitySd, personalWeights(userId)));
    }

    /** 행이 없으면 빈 맵이고, 스코어링은 그때 일반 가중치만 쓴다 — 행의 존재가 곧 개인화 시작 여부다. */
    private Map<SleepFeature, BigDecimal> personalWeights(Long userId) {
        return personalWeightRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(PersonalWeight::getSleepFeature, PersonalWeight::getWeight,
                        (first, second) -> first, () -> new EnumMap<>(SleepFeature.class)));
    }

    /**
     * 다크서클은 빈 상태가 될 수 없다 — 피처 둘이 세션이 존재하는 이상 결측되지 않는다.
     * 매핑이 바뀌어 이 전제가 깨지면 조용히 틀린 값을 저장하는 대신 여기서 실패한다.
     */
    private int requireDarkCircle(SkinForecastScore score) {
        Integer darkCircle = score.scoreOf(SkinMetric.DARK_CIRCLE);
        if (darkCircle == null) {
            throw new IllegalStateException("다크서클이 산출되지 않았다 — 매핑 또는 결측 규칙이 바뀌었다");
        }
        return darkCircle;
    }

    /**
     * 구간 전량 교체 (erd.md §3.4). 다시 정규화하면 경계가 달라질 수 있어 옛 구간과 새 구간을
     * 짝지을 방법이 없다 — 남겨두면 타임라인에 사라진 구간이 계속 그려진다.
     */
    private void replaceSegments(SleepSession session, List<SleepSegmentCommand> segments) {
        sleepStageSegmentRepository.deleteBySleepSessionId(session.getId());
        saveSegments(session, segments);
    }

    private void saveSegments(SleepSession session, List<SleepSegmentCommand> segments) {
        sleepStageSegmentRepository.saveAll(segments.stream()
                .map(segment -> SleepStageSegment.builder()
                        .sleepSession(session)
                        .stage(segment.stage())
                        .startTime(segment.startTime())
                        .endTime(segment.endTime())
                        .build())
                .toList());
    }

}
