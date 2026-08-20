package com.allday.sleep2skin_be.global.infra.openai;

import com.allday.sleep2skin_be.global.exception.BusinessException;
import com.allday.sleep2skin_be.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.SocketTimeoutException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Responses API 로 셀피를 분석한다 (architecture.md §7).
 *
 * <p>이미지는 <b>요청 본문에 base64 인라인</b>으로 넣는다. URL 방식을 쓰지 않는 이유는 단순하다 —
 * 넘길 URL 이 존재하지 않는다. 셀피는 어디에도 업로드되지 않는다(architecture.md §5).
 *
 * <h2>이 클래스가 지키는 것</h2>
 *
 * <ul>
 *   <li><b>바이트를 필드에 담지 않는다.</b> 지역 변수로만 다루고 메서드를 벗어나면 참조가 없다</li>
 *   <li><b>로그에 바이트·base64 를 찍지 않는다.</b> 실패 로그에도 크기와 MIME 타입만 남긴다 —
 *       얼굴 데이터를 보관하지 않겠다는 약속은 로그 파일에도 적용된다</li>
 * </ul>
 *
 * <h2>Structured Outputs</h2>
 *
 * <p>자유 텍스트를 정규식으로 긁지 않는다. 스키마를 강제하면 파싱 실패라는 실패 모드가 통째로
 * 사라지고, 남는 것은 "값이 범위를 벗어났나" 하나뿐이다.
 */
@Slf4j
@Component
public class OpenAiSkinVisionClient implements SkinVisionClient {

    private static final String RESPONSES_PATH = "/v1/responses";

    private static final int SCORE_MIN = 0;
    private static final int SCORE_MAX = 100;

    private final RestClient restClient;
    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;

    public OpenAiSkinVisionClient(RestClient openAiRestClient, OpenAiProperties properties,
                                  ObjectMapper objectMapper) {
        this.restClient = openAiRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public SkinVisionScores analyze(byte[] image, String contentType) {
        if (!properties.hasApiKey()) {
            // 로컬에서 키 없이 개발하는 경우다. 운영에서는 CD 선검사가 배포 전에 잡는다
            log.error("OPENAI_API_KEY 가 비어 있어 셀피를 분석할 수 없다");
            throw new BusinessException(ErrorCode.SELFIE_ANALYSIS_FAILED, "OPENAI_API_KEY 미설정");
        }

        String responseBody = call(requestBody(image, contentType), image.length, contentType);
        return parse(responseBody);
    }

    private String call(Map<String, Object> body, int imageSize, String contentType) {
        try {
            return restClient.post()
                    .uri(RESPONSES_PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

        } catch (ResourceAccessException e) {
            // 읽기 타임아웃은 SocketTimeoutException 으로 감싸여 온다. 연결 실패와 구분하는
            // 이유는 앱이 할 일이 다르기 때문이다 — 지연은 재시도, 연결 실패는 장애 안내다
            if (e.getCause() instanceof SocketTimeoutException) {
                log.warn("셀피 분석 타임아웃 imageBytes={} contentType={} timeout={}",
                        imageSize, contentType, properties.timeout());
                throw new BusinessException(ErrorCode.SELFIE_ANALYSIS_TIMEOUT, "OpenAI 응답 지연");
            }
            throw analysisFailed(e, imageSize, contentType);

        } catch (RestClientException e) {
            throw analysisFailed(e, imageSize, contentType);
        }
    }

    /**
     * ⚠️ <b>{@code e.getMessage()} 에 이미지가 섞이지 않는다.</b> RestClient 의 예외 메시지는 응답
     * 본문과 상태 코드에서 나오고, 우리가 보낸 요청 본문(base64 를 담은 쪽)은 들어가지 않는다.
     * 예외 종류를 바꾸게 되면 이 전제를 다시 확인할 것.
     */
    private BusinessException analysisFailed(Exception e, int imageSize, String contentType) {
        log.error("셀피 분석 실패 imageBytes={} contentType={} model={}",
                imageSize, contentType, properties.visionModel(), e);
        return new BusinessException(ErrorCode.SELFIE_ANALYSIS_FAILED, "OpenAI 호출 실패");
    }

    /**
     * Responses API 요청 본문.
     *
     * <p>{@code instructions} 와 {@code input} 을 나눈 것은 역할이 달라서다 — 지표 정의는 매번
     * 같고 이미지만 바뀐다.
     */
    private Map<String, Object> requestBody(byte[] image, String contentType) {
        Map<String, Object> textPart = new LinkedHashMap<>();
        textPart.put("type", "input_text");
        textPart.put("text", SkinVisionPrompt.USER_TEXT);

        Map<String, Object> imagePart = new LinkedHashMap<>();
        imagePart.put("type", "input_image");
        imagePart.put("image_url", dataUrl(image, contentType));

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", List.of(textPart, imagePart));

        Map<String, Object> format = new LinkedHashMap<>();
        format.put("type", "json_schema");
        format.put("name", SkinVisionPrompt.SCHEMA_NAME);
        format.put("strict", true);
        format.put("schema", SkinVisionPrompt.schema());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.visionModel());
        body.put("instructions", SkinVisionPrompt.INSTRUCTIONS);
        body.put("input", List.of(message));
        body.put("text", Map.of("format", format));
        return body;
    }

    private String dataUrl(byte[] image, String contentType) {
        return "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(image);
    }

    /**
     * 스키마를 강제했으므로 내용은 항상 맞지만, <b>모델이 스키마를 만족시키지 못하고 거절할 수는
     * 있다</b>({@code incomplete}·{@code refusal}). 그때는 {@code output_text} 가 없으므로
     * 여기서 걸린다.
     *
     * <p>{@code output} 배열에는 메시지 외의 항목이 섞일 수 있어 <b>순서로 찾지 않고
     * {@code output_text} 타입으로 찾는다.</b>
     */
    private SkinVisionScores parse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode scores = objectMapper.readTree(outputText(root));

            return new SkinVisionScores(
                    requireScore(scores, "darkCircle"),
                    requireScore(scores, "complexion"),
                    requireScore(scores, "barrier"),
                    requireBoolean(scores, "pigmentationDetected"),
                    requireBoolean(scores, "acneScarDetected"),
                    requireBoolean(scores, "agingDetected"),
                    requireBoolean(scores, "blackheadDetected"));

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("셀피 분석 응답을 해석하지 못했다", e);
            throw new BusinessException(ErrorCode.SELFIE_ANALYSIS_FAILED, "응답 파싱 실패");
        }
    }

    private String outputText(JsonNode root) {
        for (JsonNode item : root.path("output")) {
            for (JsonNode content : item.path("content")) {
                if ("output_text".equals(content.path("type").asString(""))) {
                    return content.path("text").asString("");
                }
            }
        }
        log.error("셀피 분석 응답에 output_text 가 없다 status={} incompleteReason={}",
                root.path("status").asString(""),
                root.path("incomplete_details").path("reason").asString(""));
        throw new BusinessException(ErrorCode.SELFIE_ANALYSIS_FAILED, "모델이 결과를 내지 않았다");
    }

    /**
     * ⚠️ <b>범위를 벗어난 값을 자르지 않고 실패시킨다.</b> strict 스키마가
     * {@code minimum}/{@code maximum} 을 지원하지 않아 0~100 은 코드가 지켜야 하는데,
     * 클램프해 버리면 <b>모델이 다른 척도로 답했다는 사실이 숨는다</b> — 101 을 100 으로 만들면
     * 저장은 되고 적중률만 틀린다. 실패하면 앱이 재시도하고 행은 생기지 않는다(erd.md §3.6).
     */
    private int requireScore(JsonNode scores, String field) {
        JsonNode value = scores.path(field);
        if (!value.isInt() || value.asInt() < SCORE_MIN || value.asInt() > SCORE_MAX) {
            log.error("셀피 분석 점수가 0~100 범위를 벗어났다 field={} value={}", field, value);
            throw new BusinessException(ErrorCode.SELFIE_ANALYSIS_FAILED, "점수 범위 오류: " + field);
        }
        return value.asInt();
    }

    /** 감지 플래그 4종. strict 스키마의 {@code required}에 있어 항상 존재하지만, 타입만 확인한다. */
    private boolean requireBoolean(JsonNode scores, String field) {
        JsonNode value = scores.path(field);
        if (!value.isBoolean()) {
            log.error("셀피 분석 플래그가 boolean이 아니다 field={} value={}", field, value);
            throw new BusinessException(ErrorCode.SELFIE_ANALYSIS_FAILED, "플래그 타입 오류: " + field);
        }
        return value.asBoolean();
    }

}
