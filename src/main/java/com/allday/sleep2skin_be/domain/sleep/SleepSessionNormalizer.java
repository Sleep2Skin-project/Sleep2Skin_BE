package com.allday.sleep2skin_be.domain.sleep;

import com.allday.sleep2skin_be.domain.sleep.dto.SleepNormalizationCommand;
import com.allday.sleep2skin_be.domain.sleep.dto.SleepNormalizationResult;
import com.allday.sleep2skin_be.domain.sleep.dto.SleepSegmentCommand;
import com.allday.sleep2skin_be.domain.sleep.entity.SleepStage;
import com.allday.sleep2skin_be.global.exception.BusinessException;
import com.allday.sleep2skin_be.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Predicate;

/**
 * 앱이 보낸 단계 구간 배열을 <b>세션 한 행으로 접는다.</b> 저장도 스코어링도 하지 않는 순수 계산이다.
 *
 * <p>처리 순서는 api.md §3의 서버 처리 1~5단계와 같다.
 *
 * <pre>
 * 1. 시간순 정렬 · 구간 겹침 검사
 * 2. 세션 경계 자르기   연속 AWAKE 60분 이상 → 첫 기상. 이후 구간 전부 버림
 * 3. 집계              총 수면 = asleep 구간 합 (UNSPECIFIED 포함)
 * 4. sleepDate 결정     wakeTime의 날짜 (오프셋 기준)
 * 5. 해시 계산
 * </pre>
 *
 * <p><b>세션은 {@code sleepOnsetTime}부터 {@code wakeTime}까지다.</b> 잠들기 전의 {@code AWAKE}와
 * 기상 이후의 구간은 세션 밖이라 남기지 않는다 — 그래야 저장되는 구간과
 * {@code sleep_onset_time}·{@code wake_time}이 같은 창을 가리킨다.
 *
 * <p><b>앱이 보고한 집계값과 세션 분할을 쓰지 않는다.</b> 서버가 첫 기상에서 자르므로 앱의 총합에는
 * 그 뒤의 낮잠이 섞여 있을 수 있고, 기기·OS별로 구간을 쪼개는 기준도 다르다(erd.md §3.3).
 */
@Component
public class SleepSessionNormalizer {

    public SleepNormalizationResult normalize(SleepNormalizationCommand command) {
        List<SleepSegmentCommand> sorted = sortAndValidate(command.segments());

        int onsetIndex = firstAsleepIndex(sorted);
        WakeUp wakeUp = findWakeUp(sorted, onsetIndex);
        List<SleepSegmentCommand> session = List.copyOf(sorted.subList(onsetIndex, wakeUp.endIndex()));

        BigDecimal hrv = normalizeHrv(command.hrv());
        List<Duration> awakeEpisodes = awakeEpisodes(session);

        return new SleepNormalizationResult(
                wakeUp.wakeTime().toLocalDate(),
                session.getFirst().startTime(),
                wakeUp.wakeTime(),
                minutesOf(totalDuration(session, SleepSegmentCommand::isAsleep)),
                minutesOf(stageDuration(session, SleepStage.DEEP)),
                minutesOf(stageDuration(session, SleepStage.REM)),
                minutesOf(stageDuration(session, SleepStage.CORE)),
                awakeEpisodes.size(),
                minutesOf(awakeEpisodes.stream().reduce(Duration.ZERO, Duration::plus)),
                hrv,
                command.restingHeartRate(),
                session,
                payloadHash(session, hrv, command.restingHeartRate())
        );
    }

    /**
     * 1단계 — 시간순 정렬과 구간 검사.
     *
     * <p><b>앱이 정렬해 보낸다고 가정하지 않는다.</b> 정렬을 믿고 건너뛰면 뒤집힌 배열에서
     * 겹침 검사가 통과하고, 그 뒤의 모든 계산이 조용히 틀린다.
     */
    private List<SleepSegmentCommand> sortAndValidate(List<SleepSegmentCommand> segments) {
        if (segments == null || segments.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "수면 단계 구간이 비어 있습니다.");
        }

        for (SleepSegmentCommand segment : segments) {
            if (!segment.startTime().isBefore(segment.endTime())) {
                throw new BusinessException(ErrorCode.SLEEP_TIME_INVALID,
                        "구간의 시작이 종료보다 늦거나 같습니다: " + segment.startTime() + " ~ " + segment.endTime());
            }
        }

        List<SleepSegmentCommand> sorted = new ArrayList<>(segments);
        sorted.sort(Comparator.comparing(SleepSegmentCommand::startTime));

        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).startTime().isBefore(sorted.get(i - 1).endTime())) {
                throw new BusinessException(ErrorCode.SLEEP_STAGE_INVALID,
                        "구간이 겹칩니다: " + sorted.get(i - 1).endTime() + " > " + sorted.get(i).startTime());
            }
        }
        return sorted;
    }

    /**
     * {@code asleep}이 하나도 없으면 수면 세션이 아니다. 앱이 각성만 보고한 밤은 저장할 것이 없다.
     */
    private int firstAsleepIndex(List<SleepSegmentCommand> sorted) {
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).isAsleep()) {
                return i;
            }
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT, "잠든 구간이 없어 수면 세션을 만들 수 없습니다.");
    }

    /**
     * 2단계 — 첫 기상을 찾는다.
     *
     * <p><b>탐색은 잠든 뒤부터 시작한다.</b> 잠들기 전에 70분을 뒤척인 밤을 "기상"으로 보면
     * 그날 수면 전체가 통째로 버려진다. 기상은 잠든 적이 있어야 성립한다.
     *
     * <p><b>연속 {@code AWAKE}의 길이는 구간 길이의 합으로 잰다</b> — 구간 사이 공백(측정 누락)까지
     * 각성으로 세면 잰 적 없는 시간 때문에 세션이 잘린다.
     */
    private WakeUp findWakeUp(List<SleepSegmentCommand> sorted, int onsetIndex) {
        int lastAsleepIndex = onsetIndex;
        int i = onsetIndex;

        while (i < sorted.size()) {
            if (sorted.get(i).isAsleep()) {
                lastAsleepIndex = i++;
                continue;
            }
            int episodeStart = i;
            Duration episode = Duration.ZERO;
            while (i < sorted.size() && !sorted.get(i).isAsleep()) {
                episode = episode.plus(sorted.get(i).duration());
                i++;
            }
            if (episode.compareTo(SleepNormalizationPolicy.WAKE_UP_THRESHOLD) >= 0) {
                return new WakeUp(episodeStart, sorted.get(episodeStart).startTime());
            }
        }

        // 60분 이상 각성 없이 끝난 밤 — 마지막 asleep이 끝나는 시점이 기상이다.
        // 그 뒤에 남은 AWAKE는 야간 각성이 아니라 기상이므로 세션에서 뺀다. 포함하면 모든 사용자가
        // 매일 최소 1회를 깔고 시작해 다크서클 예보가 전반적으로 눌린다(erd.md §3.3).
        return new WakeUp(lastAsleepIndex + 1, sorted.get(lastAsleepIndex).endTime());
    }

    /**
     * 세션 내부의 각성 구간들. 연속 {@code AWAKE}는 하나로 묶고, 5분 미만은 뒤척임으로 버린다.
     *
     * <p>세션이 {@code asleep}으로 시작해 {@code asleep}으로 끝나므로 여기 걸리는 각성은 전부
     * 수면 사이에 낀 것이다 — 입면 전·기상 후는 이미 잘려 나갔다.
     *
     * <p><b>횟수와 총 시간이 같은 구간 집합에서 나온다.</b> 한쪽에만 임계값을 걸면
     * {@code count=0} · {@code minutes=12} 같은 모순 조합이 화면에 뜬다.
     */
    private List<Duration> awakeEpisodes(List<SleepSegmentCommand> session) {
        List<Duration> episodes = new ArrayList<>();
        int i = 0;
        while (i < session.size()) {
            if (session.get(i).isAsleep()) {
                i++;
                continue;
            }
            Duration episode = Duration.ZERO;
            while (i < session.size() && !session.get(i).isAsleep()) {
                episode = episode.plus(session.get(i).duration());
                i++;
            }
            if (episode.compareTo(SleepNormalizationPolicy.AWAKE_EPISODE_THRESHOLD) >= 0) {
                episodes.add(episode);
            }
        }
        return episodes;
    }

    private Duration totalDuration(List<SleepSegmentCommand> session,
                                   Predicate<SleepSegmentCommand> filter) {
        return session.stream()
                .filter(filter)
                .map(SleepSegmentCommand::duration)
                .reduce(Duration.ZERO, Duration::plus);
    }

    private Duration stageDuration(List<SleepSegmentCommand> session, SleepStage stage) {
        return totalDuration(session, segment -> segment.stage() == stage);
    }

    /**
     * <b>분 환산은 마지막에 한 번만 한다.</b> 구간마다 분으로 내림한 뒤 더하면 초 단위 나머지가
     * 구간 수만큼 사라져, 30개짜리 밤에서 총 수면이 실제보다 수십 분 적게 나올 수 있다.
     */
    private int minutesOf(Duration duration) {
        return Math.toIntExact(duration.toMinutes());
    }

    /**
     * 저장 정밀도(소수점 2자리)에 맞춰 자른다. 자르지 않으면 {@code 41.234}와 {@code 41.239}가
     * 같은 값으로 저장되면서 해시만 달라져, <b>같은 수면인데 재산출이 돈다.</b>
     */
    private BigDecimal normalizeHrv(BigDecimal hrv) {
        return hrv == null ? null : hrv.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 5단계 — 정규화 결과의 SHA-256.
     *
     * <p><b>원본 요청이 아니라 잘라낸 세션을 해싱한다.</b> 앱이 매번 낮잠까지 붙여 보내도, 세션
     * 부분이 같으면 같은 해시가 나와야 재처리가 걸러진다.
     *
     * <p>시각은 {@link java.time.Instant}로 환산해 넣는다 — {@code +09:00}과 {@code Z}로 표기만 다른
     * 같은 순간이 다른 해시를 만들면 안 된다.
     *
     * <p>집계값이 아니라 구간을 해싱하는 이유는 {@code sleep_stage_segment}도 함께 갱신되기
     * 때문이다. 집계만 보면 집계가 같고 구간 배치만 다른 밤이 통과해 타임라인이 낡은 채로 남는다.
     */
    private String payloadHash(List<SleepSegmentCommand> session, BigDecimal hrv, Integer restingHeartRate) {
        StringBuilder canonical = new StringBuilder();
        for (SleepSegmentCommand segment : session) {
            canonical.append(segment.stage().name()).append('|')
                    .append(segment.startTime().toInstant().toEpochMilli()).append('|')
                    .append(segment.endTime().toInstant().toEpochMilli()).append('\n');
        }
        canonical.append("hrv=").append(hrv == null ? "" : hrv.toPlainString())
                .append("|rhr=").append(restingHeartRate == null ? "" : restingHeartRate);

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }

    /**
     * @param endIndex 세션에 포함되지 않는 첫 인덱스
     */
    private record WakeUp(int endIndex, OffsetDateTime wakeTime) {
    }

}
