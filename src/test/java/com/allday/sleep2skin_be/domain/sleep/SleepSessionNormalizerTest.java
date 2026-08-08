package com.allday.sleep2skin_be.domain.sleep;

import com.allday.sleep2skin_be.domain.sleep.dto.SleepNormalizationCommand;
import com.allday.sleep2skin_be.domain.sleep.dto.SleepNormalizationResult;
import com.allday.sleep2skin_be.domain.sleep.dto.SleepSegmentCommand;
import com.allday.sleep2skin_be.domain.sleep.entity.SleepStage;
import com.allday.sleep2skin_be.global.exception.BusinessException;
import com.allday.sleep2skin_be.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SleepSessionNormalizerTest {

    private final SleepSessionNormalizer normalizer = new SleepSessionNormalizer();

    @Nested
    @DisplayName("세션 경계")
    class SessionBoundary {

        @Test
        @DisplayName("연속 각성 60분 이상이면 거기서 세션이 끝나고 이후는 낮잠으로 버린다")
        void 각성_60분에서_세션이_끝난다() {
            SleepNormalizationResult result = normalize(builder()
                    .segment(SleepStage.CORE, "2026-08-06T23:40", "2026-08-07T05:00")
                    .segment(SleepStage.AWAKE, "2026-08-07T05:00", "2026-08-07T06:12")   // 72분 → 기상
                    .segment(SleepStage.CORE, "2026-08-07T06:12", "2026-08-07T07:30")    // 낮잠
                    .segment(SleepStage.REM, "2026-08-07T07:30", "2026-08-07T08:00"));

            assertThat(result.wakeTime()).isEqualTo(at("2026-08-07T05:00"));
            assertThat(result.totalSleepMinutes()).isEqualTo(320);
            assertThat(result.remSleepMinutes()).isZero();
            assertThat(result.segments()).hasSize(1);
        }

        @Test
        @DisplayName("60분 미만 각성으로는 세션이 끊기지 않는다 — 끊기면 각성 횟수가 구조적으로 항상 0이 된다")
        void 각성_59분으로는_세션이_끊기지_않는다() {
            SleepNormalizationResult result = normalize(builder()
                    .segment(SleepStage.CORE, "2026-08-06T23:40", "2026-08-07T03:00")
                    .segment(SleepStage.AWAKE, "2026-08-07T03:00", "2026-08-07T03:59")   // 59분
                    .segment(SleepStage.CORE, "2026-08-07T03:59", "2026-08-07T07:10"));

            assertThat(result.wakeTime()).isEqualTo(at("2026-08-07T07:10"));
            assertThat(result.awakeCount()).isEqualTo(1);
            assertThat(result.awakeMinutes()).isEqualTo(59);
        }

        @Test
        @DisplayName("마지막 수면 이후의 각성은 기상이므로 세션에서 빠지고 각성으로 세지 않는다")
        void 마지막_수면_이후_각성은_기상이다() {
            SleepNormalizationResult result = normalize(builder()
                    .segment(SleepStage.CORE, "2026-08-06T23:40", "2026-08-07T07:10")
                    .segment(SleepStage.AWAKE, "2026-08-07T07:10", "2026-08-07T07:28")); // 18분

            assertThat(result.wakeTime()).isEqualTo(at("2026-08-07T07:10"));
            assertThat(result.awakeCount()).isZero();
            assertThat(result.awakeMinutes()).isZero();
            assertThat(result.segments()).hasSize(1);
        }

        @Test
        @DisplayName("잠들기 전 60분 넘게 뒤척여도 기상으로 보지 않는다 — 기상은 잠든 적이 있어야 성립한다")
        void 입면_전_각성은_기상이_아니다() {
            SleepNormalizationResult result = normalize(builder()
                    .segment(SleepStage.AWAKE, "2026-08-06T22:20", "2026-08-06T23:40")   // 80분
                    .segment(SleepStage.CORE, "2026-08-06T23:40", "2026-08-07T07:10"));

            assertThat(result.sleepOnsetTime()).isEqualTo(at("2026-08-06T23:40"));
            assertThat(result.wakeTime()).isEqualTo(at("2026-08-07T07:10"));
            assertThat(result.totalSleepMinutes()).isEqualTo(450);
            assertThat(result.awakeCount()).isZero();
        }

        @Test
        @DisplayName("떨어져 있어도 연속된 각성 구간이면 합쳐 60분을 판정한다")
        void 나뉜_각성_구간도_합쳐서_판정한다() {
            SleepNormalizationResult result = normalize(builder()
                    .segment(SleepStage.CORE, "2026-08-06T23:40", "2026-08-07T05:00")
                    .segment(SleepStage.AWAKE, "2026-08-07T05:00", "2026-08-07T05:35")   // 35분
                    .segment(SleepStage.AWAKE, "2026-08-07T05:35", "2026-08-07T06:05")   // 30분 → 합 65분
                    .segment(SleepStage.CORE, "2026-08-07T06:05", "2026-08-07T07:10"));

            assertThat(result.wakeTime()).isEqualTo(at("2026-08-07T05:00"));
            assertThat(result.totalSleepMinutes()).isEqualTo(320);
        }
    }

    @Nested
    @DisplayName("각성 집계")
    class AwakeAggregation {

        @Test
        @DisplayName("5분 미만 뒤척임은 횟수와 총 시간 양쪽에서 버린다")
        void 뒤척임은_양쪽에서_버린다() {
            SleepNormalizationResult result = normalize(builder()
                    .segment(SleepStage.CORE, "2026-08-06T23:40", "2026-08-07T01:00")
                    .segment(SleepStage.AWAKE, "2026-08-07T01:00", "2026-08-07T01:04")   // 4분 — 버림
                    .segment(SleepStage.CORE, "2026-08-07T01:04", "2026-08-07T03:00")
                    .segment(SleepStage.AWAKE, "2026-08-07T03:00", "2026-08-07T03:07")   // 7분 — 1회
                    .segment(SleepStage.CORE, "2026-08-07T03:07", "2026-08-07T07:10"));

            assertThat(result.awakeCount()).isEqualTo(1);
            assertThat(result.awakeMinutes()).isEqualTo(7);
        }

        @Test
        @DisplayName("정확히 5분인 각성은 센다 — 임계값은 이상 기준이다")
        void 정확히_5분인_각성은_센다() {
            SleepNormalizationResult result = normalize(builder()
                    .segment(SleepStage.CORE, "2026-08-06T23:40", "2026-08-07T03:00")
                    .segment(SleepStage.AWAKE, "2026-08-07T03:00", "2026-08-07T03:05")
                    .segment(SleepStage.CORE, "2026-08-07T03:05", "2026-08-07T07:10"));

            assertThat(result.awakeCount()).isEqualTo(1);
            assertThat(result.awakeMinutes()).isEqualTo(5);
        }

        @Test
        @DisplayName("각성 횟수와 총 시간은 같은 구간 집합에서 나온다 — 한쪽만 0인 조합이 생기지 않는다")
        void 횟수와_총시간이_같은_집합에서_나온다() {
            SleepNormalizationResult result = normalize(builder()
                    .segment(SleepStage.CORE, "2026-08-06T23:40", "2026-08-07T01:00")
                    .segment(SleepStage.AWAKE, "2026-08-07T01:00", "2026-08-07T01:02")
                    .segment(SleepStage.CORE, "2026-08-07T01:02", "2026-08-07T03:00")
                    .segment(SleepStage.AWAKE, "2026-08-07T03:00", "2026-08-07T03:03")
                    .segment(SleepStage.CORE, "2026-08-07T03:03", "2026-08-07T07:10"));

            assertThat(result.awakeCount()).isZero();
            assertThat(result.awakeMinutes()).isZero();
        }
    }

    @Nested
    @DisplayName("집계")
    class Aggregation {

        @Test
        @DisplayName("단계 미상 구간은 총 수면에는 들어가고 단계별 분에는 들어가지 않는다")
        void 미상_구간은_총_수면에만_들어간다() {
            SleepNormalizationResult result = normalize(builder()
                    .segment(SleepStage.CORE, "2026-08-06T23:40", "2026-08-07T00:40")     // 60
                    .segment(SleepStage.UNSPECIFIED, "2026-08-07T00:40", "2026-08-07T02:40") // 120
                    .segment(SleepStage.DEEP, "2026-08-07T02:40", "2026-08-07T03:00")     // 20
                    .segment(SleepStage.REM, "2026-08-07T03:00", "2026-08-07T03:25"));    // 25

            assertThat(result.totalSleepMinutes()).isEqualTo(225);
            assertThat(result.coreSleepMinutes()).isEqualTo(60);
            assertThat(result.deepSleepMinutes()).isEqualTo(20);
            assertThat(result.remSleepMinutes()).isEqualTo(25);
            // 비율 분모는 총 수면(225)이 아니라 단계 합(105)이다 — 미상 구간이 분모에 들어가면
            // 측정하지 못한 시간이 "깊은 수면이 아니었던 시간"으로 계산된다 (prd.md §10.5)
            assertThat(result.stagedSleepMinutes()).isEqualTo(105);
        }

        @Test
        @DisplayName("단계가 하나도 안 잡힌 밤은 단계 합이 0이다 — 장벽을 빈 상태로 낼 근거가 된다")
        void 단계가_안_잡힌_밤은_단계_합이_0이다() {
            SleepNormalizationResult result = normalize(builder()
                    .segment(SleepStage.UNSPECIFIED, "2026-08-06T23:40", "2026-08-07T07:10"));

            assertThat(result.totalSleepMinutes()).isEqualTo(450);
            assertThat(result.stagedSleepMinutes()).isZero();
        }

        @Test
        @DisplayName("초 단위가 섞여도 마지막에 한 번만 분으로 환산해 누락이 쌓이지 않는다")
        void 분_환산은_마지막에_한_번만_한다() {
            SleepNormalizationResult result = normalize(builder()
                    .segment(SleepStage.CORE, "2026-08-06T23:40:00", "2026-08-06T23:40:40")
                    .segment(SleepStage.CORE, "2026-08-06T23:40:40", "2026-08-06T23:41:20")
                    .segment(SleepStage.CORE, "2026-08-06T23:41:20", "2026-08-06T23:42:00"));

            // 40초 × 3 = 2분. 구간마다 분으로 내렸다면 0분이 된다
            assertThat(result.totalSleepMinutes()).isEqualTo(2);
        }

        @Test
        @DisplayName("잠든 시각은 첫 수면 구간의 시작이고 기준일은 기상일이다")
        void 잠든_시각과_기준일() {
            SleepNormalizationResult result = normalize(builder()
                    .segment(SleepStage.CORE, "2026-08-06T23:40", "2026-08-07T07:10"));

            assertThat(result.sleepOnsetTime()).isEqualTo(at("2026-08-06T23:40"));
            assertThat(result.sleepDate()).isEqualTo(LocalDate.of(2026, 8, 7));
        }

        @Test
        @DisplayName("기준일은 요청의 오프셋으로 계산한다 — UTC로 해석하면 하루 밀린다")
        void 기준일은_요청_오프셋으로_계산한다() {
            OffsetDateTime onset = OffsetDateTime.parse("2026-08-06T23:40:00+09:00");
            OffsetDateTime wake = OffsetDateTime.parse("2026-08-07T07:10:00+09:00");

            SleepNormalizationResult result = normalizer.normalize(new SleepNormalizationCommand(
                    List.of(new SleepSegmentCommand(SleepStage.CORE, onset, wake)), null, null));

            // 같은 순간을 UTC로 보면 2026-08-06T22:10Z — 오프셋을 버리면 8/6이 된다
            assertThat(wake.toInstant().atOffset(ZoneOffset.UTC).toLocalDate())
                    .isEqualTo(LocalDate.of(2026, 8, 6));
            assertThat(result.sleepDate()).isEqualTo(LocalDate.of(2026, 8, 7));
        }

        @Test
        @DisplayName("HRV와 안정시 심박은 결측이면 null 그대로 통과한다")
        void 결측_생체지표는_null로_통과한다() {
            SleepNormalizationResult result = normalize(builder()
                    .segment(SleepStage.CORE, "2026-08-06T23:40", "2026-08-07T07:10"));

            assertThat(result.hrv()).isNull();
            assertThat(result.restingHeartRate()).isNull();
        }
    }

    @Nested
    @DisplayName("페이로드 해시")
    class PayloadHash {

        @Test
        @DisplayName("같은 세션이면 같은 해시다 — 앱이 시작할 때마다 보내는 재수신을 걸러낸다")
        void 같은_세션은_같은_해시다() {
            SegmentBuilder night = builder()
                    .segment(SleepStage.CORE, "2026-08-06T23:40", "2026-08-07T07:10");

            assertThat(normalize(night).payloadHash())
                    .isEqualTo(normalize(night).payloadHash())
                    .hasSize(64);
        }

        @Test
        @DisplayName("세션 뒤에 낮잠이 붙어도 해시가 같다 — 잘라낸 세션만 해싱하기 때문이다")
        void 낮잠이_붙어도_해시가_같다() {
            String withoutNap = normalize(builder()
                    .segment(SleepStage.CORE, "2026-08-06T23:40", "2026-08-07T05:00")
                    .segment(SleepStage.AWAKE, "2026-08-07T05:00", "2026-08-07T06:12"))
                    .payloadHash();

            String withNap = normalize(builder()
                    .segment(SleepStage.CORE, "2026-08-06T23:40", "2026-08-07T05:00")
                    .segment(SleepStage.AWAKE, "2026-08-07T05:00", "2026-08-07T06:12")
                    .segment(SleepStage.CORE, "2026-08-07T14:00", "2026-08-07T15:00"))
                    .payloadHash();

            assertThat(withNap).isEqualTo(withoutNap);
        }

        @Test
        @DisplayName("표기만 다른 같은 순간은 같은 해시다")
        void 오프셋_표기가_달라도_같은_해시다() {
            String kst = normalizer.normalize(new SleepNormalizationCommand(List.of(
                    new SleepSegmentCommand(SleepStage.CORE,
                            OffsetDateTime.parse("2026-08-06T23:40:00+09:00"),
                            OffsetDateTime.parse("2026-08-07T07:10:00+09:00"))), null, null)).payloadHash();

            String utc = normalizer.normalize(new SleepNormalizationCommand(List.of(
                    new SleepSegmentCommand(SleepStage.CORE,
                            OffsetDateTime.parse("2026-08-06T14:40:00Z"),
                            OffsetDateTime.parse("2026-08-06T22:10:00Z"))), null, null)).payloadHash();

            assertThat(utc).isEqualTo(kst);
        }

        @Test
        @DisplayName("집계가 같아도 구간 배치가 다르면 해시가 다르다 — 타임라인이 낡은 채로 남지 않는다")
        void 구간_배치가_다르면_해시가_다르다() {
            String first = normalize(builder()
                    .segment(SleepStage.DEEP, "2026-08-06T23:40", "2026-08-07T00:40")
                    .segment(SleepStage.CORE, "2026-08-07T00:40", "2026-08-07T01:40"))
                    .payloadHash();

            String second = normalize(builder()
                    .segment(SleepStage.CORE, "2026-08-06T23:40", "2026-08-07T00:40")
                    .segment(SleepStage.DEEP, "2026-08-07T00:40", "2026-08-07T01:40"))
                    .payloadHash();

            assertThat(second).isNotEqualTo(first);
        }

        @Test
        @DisplayName("저장하면 같은 값이 되는 HRV는 같은 해시다 — 같은 수면이 재산출되면 안 된다")
        void 저장_정밀도_아래의_HRV_차이는_해시를_바꾸지_않는다() {
            // 소수점 2자리로 저장되므로 41.2 · 41.20 · 41.2044는 전부 41.20이다.
            // 정규화하지 않으면 표기 차이만으로 해시가 갈려 저장값은 그대로인 채 재산출이 돈다
            String plain = hashWithHrv(new BigDecimal("41.2"));

            assertThat(hashWithHrv(new BigDecimal("41.20"))).isEqualTo(plain);
            assertThat(hashWithHrv(new BigDecimal("41.2044"))).isEqualTo(plain);
            assertThat(hashWithHrv(new BigDecimal("41.21"))).isNotEqualTo(plain);
        }

        private String hashWithHrv(BigDecimal hrv) {
            return normalizer.normalize(new SleepNormalizationCommand(List.of(
                    new SleepSegmentCommand(SleepStage.CORE, at("2026-08-06T23:40"), at("2026-08-07T07:10"))),
                    hrv, 63)).payloadHash();
        }
    }

    @Nested
    @DisplayName("입력 검증")
    class Validation {

        @Test
        @DisplayName("구간이 비어 있으면 INVALID_INPUT")
        void 빈_구간은_거부한다() {
            assertThatThrownBy(() -> normalizer.normalize(new SleepNormalizationCommand(List.of(), null, null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
        }

        @Test
        @DisplayName("시작이 종료보다 늦거나 같으면 SLEEP_TIME_INVALID")
        void 뒤집힌_구간은_거부한다() {
            assertThatThrownBy(() -> normalize(builder()
                    .segment(SleepStage.CORE, "2026-08-07T07:10", "2026-08-06T23:40")))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.SLEEP_TIME_INVALID);
        }

        @Test
        @DisplayName("구간이 겹치면 SLEEP_STAGE_INVALID")
        void 겹치는_구간은_거부한다() {
            assertThatThrownBy(() -> normalize(builder()
                    .segment(SleepStage.CORE, "2026-08-06T23:40", "2026-08-07T03:00")
                    .segment(SleepStage.DEEP, "2026-08-07T02:30", "2026-08-07T04:00")))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.SLEEP_STAGE_INVALID);
        }

        @Test
        @DisplayName("잠든 구간이 없으면 INVALID_INPUT")
        void 각성만_있는_밤은_거부한다() {
            assertThatThrownBy(() -> normalize(builder()
                    .segment(SleepStage.AWAKE, "2026-08-06T23:40", "2026-08-07T01:00")))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
        }

        @Test
        @DisplayName("순서가 뒤섞여 와도 정렬한 뒤 계산한다 — 정렬을 앱에 맡기지 않는다")
        void 뒤섞인_구간을_정렬한다() {
            SleepNormalizationResult result = normalize(builder()
                    .segment(SleepStage.REM, "2026-08-07T03:00", "2026-08-07T07:10")
                    .segment(SleepStage.CORE, "2026-08-06T23:40", "2026-08-07T03:00"));

            assertThat(result.sleepOnsetTime()).isEqualTo(at("2026-08-06T23:40"));
            assertThat(result.wakeTime()).isEqualTo(at("2026-08-07T07:10"));
            assertThat(result.totalSleepMinutes()).isEqualTo(450);
        }
    }

    // ===== 픽스처 =====

    private SleepNormalizationResult normalize(SegmentBuilder builder) {
        return normalizer.normalize(new SleepNormalizationCommand(builder.build(), null, null));
    }

    private static SegmentBuilder builder() {
        return new SegmentBuilder();
    }

    /** 초를 생략하면 {@code :00}으로 본다. 모든 시각은 KST 기준이다. */
    private static OffsetDateTime at(String localDateTime) {
        String normalized = localDateTime.length() == 16 ? localDateTime + ":00" : localDateTime;
        return OffsetDateTime.parse(normalized + "+09:00");
    }

    private static final class SegmentBuilder {

        private final List<SleepSegmentCommand> segments = new ArrayList<>();

        SegmentBuilder segment(SleepStage stage, String start, String end) {
            segments.add(new SleepSegmentCommand(stage, at(start), at(end)));
            return this;
        }

        List<SleepSegmentCommand> build() {
            return List.copyOf(segments);
        }
    }

}
