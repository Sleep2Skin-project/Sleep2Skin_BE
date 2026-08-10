package com.allday.sleep2skin_be.global.infra.openai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 셀피 분석 프롬프트와 Structured Outputs 스키마.
 *
 * <p><b>클라이언트에서 떼어낸 이유는 여기가 유일한 방어선이기 때문이다.</b> 점수 방향이 뒤집혀도
 * 값은 0~100 정수라 {@code CHECK} 제약도 스키마 검증도 걸리지 않는다. 문장 한 줄이 지표의
 * 의미를 정하는 자리라, HTTP 배선과 섞여 있으면 리뷰에서 눈에 띄지 않는다.
 *
 * <h2>프롬프트를 고쳤다면 실호출로 회귀를 확인한다</h2>
 *
 * <p>눈 밑이 뚜렷하게 어두운 샘플에서 {@code darkCircle} 이 <b>낮게</b> 나오는지 본다.
 * 스텁이 아니라 실제 호출이어야 한다 — 스텁은 프롬프트를 읽지 않는다(architecture.md §7).
 */
final class SkinVisionPrompt {

    /**
     * 세 지표의 정의와 <b>점수 방향</b>. 예보(prd.md §10)와 같은 방향이어야 HOME-07 대조가
     * 성립한다 — 한쪽만 뒤집히면 검증이 통째로 무의미해지고 개인 가중치가 반대로 학습된다.
     *
     * <p>진단·의료 판단으로 읽히지 않게 <b>"보이는 상태"</b> 로 한정한다. 우리가 만드는 것은
     * 수면 예보와 대조할 관찰값이지 피부과 소견이 아니다.
     */
    static final String INSTRUCTIONS = """
            You rate the visible skin condition in a selfie for a sleep-and-skin tracking app.

            Rate three metrics. All three use the SAME scale: an integer from 0 to 100 where
            a HIGHER score always means a BETTER (healthier-looking) condition.

            This is an observational rating of what is visible in the photo. It is not a
            medical diagnosis. Judge only what you can see; do not speculate about causes.

            If lighting, angle, or resolution make a metric hard to judge, give your best
            estimate near the middle of the range rather than an extreme value.

            Answer only with the JSON object required by the schema.
            """;

    /** 사용자 메시지. 이미지와 함께 보낸다. */
    static final String USER_TEXT = "Rate this selfie on the three metrics defined in the schema.";

    /** Structured Outputs 스키마 이름. 응답 파싱에는 쓰지 않고 요청에만 들어간다. */
    static final String SCHEMA_NAME = "skin_metric_scores";

    /**
     * ⚠️ <b>각 필드의 {@code description} 이 양 끝을 문장으로 말한다.</b> 이름만 보면
     * {@code darkCircle} 은 "다크서클이 심한 정도"로 읽혀 모델이 방향을 뒤집기 쉽다.
     * "0 = ..., 100 = ..." 형태를 유지할 것.
     *
     * <p><b>{@code minimum}/{@code maximum} 을 넣지 않는다.</b> strict 모드가 지원하지 않는
     * 키워드라 요청 자체가 400 으로 거절된다. 범위 검증은
     * {@link OpenAiSkinVisionClient} 가 코드로 한다.
     */
    static Map<String, Object> schema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("darkCircle", integerField("""
                Dark-circle RECOVERY under the eyes. \
                0 = very dark, heavy shadows under the eyes. \
                100 = the under-eye area looks clear and bright, with no visible shadow. \
                Note the direction: a HIGH score means dark circles are ABSENT."""));
        properties.put("complexion", integerField("""
                Complexion vitality. \
                0 = pale, dull, sallow, lifeless-looking skin tone. \
                100 = even, rosy, radiant-looking skin tone."""));
        properties.put("barrier", integerField("""
                Skin barrier condition. \
                0 = visibly dry, flaky, red, irritated or rough. \
                100 = smooth, calm and well-hydrated-looking, with no redness or flaking."""));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        // strict 모드는 모든 프로퍼티가 required 여야 한다 — 하나라도 빠지면 요청이 거절된다
        schema.put("required", List.of("darkCircle", "complexion", "barrier"));
        schema.put("additionalProperties", false);
        return schema;
    }

    private static Map<String, Object> integerField(String description) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("type", "integer");
        field.put("description", description);
        return field;
    }

    private SkinVisionPrompt() {
    }

}
