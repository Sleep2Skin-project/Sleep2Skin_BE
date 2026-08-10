package com.allday.sleep2skin_be.global.infra.openai;

/**
 * 셀피 이미지를 피부 지표 3종으로 바꾸는 외부 연동 (HOME-06).
 *
 * <p><b>⚠️ 스토리지 키를 받는 시그니처를 만들지 말 것.</b> 바이트를 직접 받는 이 형태가
 * "셀피 원본을 보관하지 않는다"는 정책을 구조로 지키는 장치다(architecture.md §5) —
 * 키를 받게 되는 순간 그 키를 만들 업로드가 필요해지고, 업로드가 생기면 지울 것이 생긴다.
 *
 * <p>인터페이스로 감싼 것은 제공자를 바꿀 가능성 때문이다. 테스트는 고정값을 돌려주는
 * 스텁으로 대체한다 — 실제 호출은 비용이 들고 값이 매번 다르다.
 */
public interface SkinVisionClient {

    /**
     * @param image       셀피 원본 바이트. <b>메서드 밖으로 새어 나가면 안 된다</b> —
     *                    필드·정적 변수·캐시에 담지 않고, 예외 로그에도 찍지 않는다
     * @param contentType {@code image/jpeg} 등. base64 data URL 의 MIME 타입이 된다
     * @return 0~100 지표 3종
     * @throws com.allday.sleep2skin_be.global.exception.BusinessException
     *         {@code SELFIE_ANALYSIS_TIMEOUT}(504) 또는 {@code SELFIE_ANALYSIS_FAILED}(502)
     */
    SkinVisionScores analyze(byte[] image, String contentType);

}
