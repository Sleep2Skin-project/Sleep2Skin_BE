package com.allday.sleep2skin_be.domain.sleep.dto.response;

import com.allday.sleep2skin_be.domain.skin.entity.SleepFeature;
import com.allday.sleep2skin_be.domain.sleep.SleepInterpretationPolicy;
import com.allday.sleep2skin_be.domain.sleep.dto.InterpretationTone;
import com.allday.sleep2skin_be.global.response.QueryStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * 어젯밤 수면 통역 카드 (HOME-02).
 *
 * <p>모든 조회 API가 공유하는 {@code {status, message, 페이로드}} 형태다(conventions.md §2).
 */
@Schema(description = "어젯밤 수면 통역 카드 응답")
public record SleepInterpretationResponse(

        @Schema(description = "조회 상태", example = "AVAILABLE")
        QueryStatus status,

        @Schema(description = "빈 상태일 때 보여줄 안내 문구. 정상이면 `null`", nullable = true)
        String message,

        @Schema(description = "조회 기준일 (기상일 기준)", example = "2026-08-07")
        LocalDate baseDate,

        @Schema(description = "통역 카드. 그날 수면 데이터가 없으면 `null`", nullable = true)
        Interpretation interpretation
) {

    private static final String NO_SLEEP_DATA_MESSAGE = "수면 데이터가 없어 어젯밤을 읽어드릴 수 없어요.";

    public static SleepInterpretationResponse of(LocalDate baseDate, Interpretation interpretation) {
        return new SleepInterpretationResponse(QueryStatus.AVAILABLE, null, baseDate, interpretation);
    }

    public static SleepInterpretationResponse empty(LocalDate baseDate) {
        return new SleepInterpretationResponse(QueryStatus.NO_SLEEP_DATA, NO_SLEEP_DATA_MESSAGE,
                baseDate, null);
    }

    /**
     * @param focus 짚어낸 피처. <b>{@code tone}이 {@code PRAISE}면 {@code null}</b>이다
     */
    @Schema(description = "통역 카드 본문")
    public record Interpretation(

            @Schema(description = "어조. 앱은 문구가 아니라 이 값으로 분기한다", example = "IMPROVE")
            InterpretationTone tone,

            @Schema(description = "헤드라인 문장. 그대로 보여줄 수 있다",
                    example = "밤중에 3번 깼어요. 다크서클 회복이 더뎌질 수 있어요.")
            String headline,

            @Schema(description = "기준치에서 가장 멀어진 피처. 지적할 것이 없으면 `null`",
                    nullable = true)
            FocusFeature focus
    ) {

        public static Interpretation praise(String headline) {
            return new Interpretation(InterpretationTone.PRAISE, headline, null);
        }

        public static Interpretation improve(String headline, SleepFeature feature, double score) {
            return new Interpretation(InterpretationTone.IMPROVE, headline,
                    FocusFeature.of(feature, score));
        }
    }

    @Schema(description = "짚어낸 수면 피처")
    public record FocusFeature(

            @Schema(description = "피처", example = "AWAKE_COUNT")
            SleepFeature feature,

            @Schema(description = "표시용 한국어 이름", example = "야간 각성")
            String label,

            @Schema(description = """
                    이 피처의 0~100 부분점수. **높을수록 좋다**

                    예보 점수와 같은 정규화 곡선에서 나온 값이라, 카드와 예보가 서로 다른 근거를
                    말하지 않는다.
                    """, example = "50")
            int score
    ) {
        static FocusFeature of(SleepFeature feature, double score) {
            return new FocusFeature(feature, SleepInterpretationPolicy.label(feature),
                    (int) Math.round(score));
        }
    }

}
