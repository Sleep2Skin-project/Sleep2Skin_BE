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
 * <p><b>척도 보정도 여기서만 한다.</b> 모델이 후하게 준다고 코드에서 빼거나 곱해 맞추지 말 것 —
 * 그 순간 실측값이 두 개의 척도를 갖게 되고 어느 쪽이 저장된 값인지 로그에도 남지 않는다.
 * 0~100이라는 범위는 그대로라 어떤 검증에도 걸리지 않는다.
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
     *
     * <h2>⚠️ 구간 경계 25·50·75는 §10.1 등급 컷오프의 사본이다</h2>
     *
     * <p>{@code ScoringPolicy.GRADE_*} 를 참조하지 못한다 — {@code global} 이 {@code domain} 을
     * 참조할 수 없다(의존 방향은 한쪽뿐이다). <b>컷오프를 조정하면 이 문장도 함께 고친다.</b>
     * 어긋나도 값은 0~100이라 아무 데도 안 걸리고, 모델이 매긴 "보통"과 화면에 나가는 등급 라벨만
     * 조용히 달라진다.
     *
     * <h2>⚠️ 양쪽으로 치우칠 수 있다 — 한쪽만 막으면 반대쪽으로 넘어간다</h2>
     *
     * <p>척도가 어느 방향으로 치우치든 실측이 예보와 계통적으로 어긋나는데, <b>개인 가중치 학습은
     * 계통 편차를 잡지 못한다</b>(절편 항을 두지 않기로 했다 — prd.md §10.7). 학습으로 회복되지
     * 않으므로 여기서 잡아야 한다.
     *
     * <p><b>둘 다 실제로 한 번씩 일어났다.</b>
     *
     * <ul>
     *   <li><b>위로 치우침</b> — 양 끝만 정의하니 모델이 사람 얼굴을 낮게 매기기를 꺼려 70~85 에
     *       몰았다. 트러블이 뚜렷한 사진도 70점대였다</li>
     *   <li><b>아래로 치우침</b>(그 보정 직후) — "76~100 은 정말 좋은 피부에만" 이라고 적었더니
     *       <b>지적할 문제가 없는 깨끗한 피부까지 60점대</b>가 됐다. 예보 90 에 실측 60 이 대조됐다</li>
     * </ul>
     *
     * <p><b>그래서 구간을 "얼마나 인상적인가"가 아니라 "보이는 문제가 있는가"로 정의한다.</b>
     * 지적할 문제가 없으면 {@code 76} 이상이고 한눈에 보이면 {@code 50} 미만이다 — <b>두 규칙이
     * 대칭이라야 한쪽을 조여도 반대쪽으로 넘어가지 않는다.</b> 지시문과 지표별
     * {@code description} 양쪽에 두 방향이 모두 적혀 있어야 한다.
     */
    static final String INSTRUCTIONS = """
            You rate the visible skin condition in a selfie for a sleep-and-skin tracking app.

            Rate three metrics. All three use the SAME scale: an integer from 0 to 100 where
            a HIGHER score always means a BETTER (healthier-looking) condition.

            CALIBRATION — the bands are defined by HOW VISIBLE THE PROBLEM IS, not by how
            impressive the skin is. The app maps these bands to fixed grade labels, so a
            score in the wrong band is wrong even when the ordering is right:
              0-25    severe — the problem dominates; obvious at a glance
              26-50   clear problem — plainly visible, anyone would notice it
              51-75   mild — something is visible, but you have to look to see it
              76-100  clean — nothing visible for this metric

            Both ends of that scale are reachable and both get used. Two failures are
            equally wrong, and you must avoid both:

            Scoring too high. A face with obvious dark circles, visible breakouts or a dull
            tone is a 30-50 face on that metric, not a 70 face — do not soften it because
            the person looks fine overall. These scores are never shown to the user as a
            compliment or as feedback on their appearance; they are measurements compared
            against a prediction made from that night of sleep, and scoring generously
            spares nobody's feelings.

            Scoring too low. The top band is NOT reserved for exceptional or professionally
            photographed skin — it is simply where skin with no visible problem belongs. If
            you cannot name what is wrong with a metric, that metric is above 75. Do not
            settle at 60-70 out of caution, and do not deduct points for an ordinary selfie
            being an ordinary selfie.

            Do not compress everything toward the middle. Rating every photo into 40-70 is
            exactly as wrong as rating every photo into the 80s.

            Rate each metric independently. A face can be excellent on one metric and poor on
            another — do not let a single overall impression pull all three to the same
            number.

            This is an observational rating of what is visible in the photo. It is not a
            medical diagnosis. Judge only what you can see; do not speculate about causes.

            If lighting, angle or resolution make a metric hard to judge, give your best
            estimate near the middle of the range rather than an extreme value. That rule is
            only for photos you cannot read. It does not apply in either direction once you
            can see the skin:
              - if a condition described at the 0 end is visible without looking for it,
                the score belongs below 50
              - if nothing described at the 0 end is present, the score belongs above 75

            A clear, well-lit selfie of clean skin is a readable photo, not an uncertain
            one — it does not get pulled toward the middle.

            Also flag four additional conditions as true/false only — do NOT rate their
            severity, only whether they are visibly present. Answer false when you cannot
            tell.

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
     * <p><b>중간 앵커(25·50·75)를 함께 준다.</b> 양 끝만 있으면 모델이 그 사이를 임의로 나누고
     * 사람 얼굴에 대해서는 늘 위쪽으로 치우친다. {@link #INSTRUCTIONS} 의 구간 정의와 같은
     * 경계여야 한다 — 한쪽만 고치면 지시문과 스키마가 서로 다른 척도를 말한다.
     *
     * <p><b>{@code minimum}/{@code maximum} 을 넣지 않는다.</b> strict 모드가 지원하지 않는
     * 키워드라 요청 자체가 400 으로 거절된다. 범위 검증은
     * {@link OpenAiSkinVisionClient} 가 코드로 한다.
     */
    static Map<String, Object> schema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("darkCircle", integerField("""
                Dark-circle RECOVERY under the eyes.
                0 = very dark, heavy shadows; the under-eye area looks sunken or discolored.
                25 = dark circles obvious at a glance.
                50 = clearly visible shadowing that anyone would notice.
                75 = a faint shadow only, easy to miss.
                90 = no shadow worth noting under ordinary lighting.
                100 = the under-eye area looks clear and bright, with no visible shadow.
                Note the direction: a HIGH score means dark circles are ABSENT. If you can see
                under-eye darkness without looking for it, the score is below 50 — and if you
                cannot point at any under-eye darkness, the score is above 75."""));
        properties.put("complexion", integerField("""
                Complexion vitality.
                0 = pale, dull, sallow, grey or lifeless tone; uneven and washed out.
                25 = clearly dull or sallow at a glance.
                50 = flat, tired-looking tone with little vitality.
                75 = mostly even with some healthy color.
                90 = even and healthy-looking, with nothing dull or sallow to point at.
                100 = even, rosy, radiant-looking skin tone.
                If you cannot point at dullness, sallowness or unevenness, the score is above
                75 — a healthy everyday complexion does not have to look radiant to clear that
                line.
                Judge tone and vitality, NOT skin color itself — every skin tone has a healthy
                and an unhealthy-looking version, and a darker or lighter complexion is not by
                itself a lower score."""));
        properties.put("barrier", integerField("""
                Skin barrier condition.
                0 = visibly dry, flaky, rough, red, irritated, or covered in active inflamed
                breakouts.
                25 = irritation, roughness or active acne obvious at a glance.
                50 = some visible redness, rough texture, or a few active blemishes.
                75 = mostly calm with only minor texture.
                90 = calm and even, with no redness, flaking or active breakout to point at.
                100 = smooth, calm and well-hydrated-looking, with no redness, flaking or
                active breakouts.
                If there is no redness, flaking, roughness or active breakout you can point
                at, the score is above 75. Normal visible pores and ordinary skin texture are
                not barrier damage — do not deduct for them.
                Active inflamed acne belongs in THIS score — it is visible barrier irritation.
                Healed acne scarring does not; report that with acneScarDetected instead."""));
        properties.put("pigmentationDetected", booleanField("""
                Whether visible pigmentation (dark spots, melasma, uneven pigmented patches) is \
                present anywhere on the face. true = visibly present. false = not visible or absent. \
                Presence only — do NOT judge severity."""));
        properties.put("acneScarDetected", booleanField("""
                Whether visible acne scarring (pitted, indented, or discolored scar tissue from \
                past acne) is present anywhere on the face. true = visibly present. \
                false = not visible or absent. Presence only — do NOT judge severity."""));
        properties.put("agingDetected", booleanField("""
                Whether visible structural aging signs (wrinkles, fine lines, sagging, loss of \
                elasticity) are present. true = visibly present. false = not visible or absent. \
                Presence only — do NOT judge severity."""));
        properties.put("blackheadDetected", booleanField("""
                Whether visible blackheads (open comedones, dark dots in pores, typically on nose/\
                T-zone) are present anywhere on the face. true = visibly present. false = not \
                visible or absent. Presence only — do NOT judge severity."""));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        // strict 모드는 모든 프로퍼티가 required 여야 한다 — 하나라도 빠지면 요청이 거절된다
        schema.put("required", List.of("darkCircle", "complexion", "barrier",
                "pigmentationDetected", "acneScarDetected", "agingDetected", "blackheadDetected"));
        schema.put("additionalProperties", false);
        return schema;
    }

    private static Map<String, Object> integerField(String description) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("type", "integer");
        field.put("description", description);
        return field;
    }

    /** 감지 여부만 묻는 필드 — 심각도 점수가 아니라 {@code boolean}이다. */
    private static Map<String, Object> booleanField(String description) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("type", "boolean");
        field.put("description", description);
        return field;
    }

    private SkinVisionPrompt() {
    }

}
