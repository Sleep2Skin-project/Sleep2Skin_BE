package com.allday.sleep2skin_be.domain.skin;

import com.allday.sleep2skin_be.domain.skin.dto.response.SelfieVerificationResponse;
import com.allday.sleep2skin_be.domain.skin.repository.SkinForecastRepository;
import com.allday.sleep2skin_be.domain.skin.repository.SkinMeasurementRepository;
import com.allday.sleep2skin_be.domain.user.repository.UserRepository;
import com.allday.sleep2skin_be.global.exception.BusinessException;
import com.allday.sleep2skin_be.global.exception.ErrorCode;
import com.allday.sleep2skin_be.global.infra.openai.SkinVisionClient;
import com.allday.sleep2skin_be.global.infra.openai.SkinVisionScores;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;

/**
 * 셀피 분석 (HOME-06) — 멀티파트를 받아 LLM에 넘기고 검증으로 넘긴다.
 *
 * <h2>이미지가 어디에도 남지 않는다는 것을 구조가 말한다</h2>
 *
 * <p>바이트는 {@link #analyzeAndVerify}의 <b>지역 변수로만 존재한다.</b> 필드·정적 변수·캐시에
 * 담지 않고, 메서드를 벗어나면 남는 참조가 없다. 저장할 버킷도, 저장한 것을 가리킬 컬럼도
 * 없다(architecture.md §5).
 *
 * <p><b>{@link SkinVerificationService}로 넘어가는 것은 숫자 3개뿐이다.</b> 이미지가 트랜잭션
 * 안쪽까지 따라 들어가지 않는다는 뜻이며, 시그니처가 그걸 증명한다.
 *
 * <h2>순서가 비용을 정한다</h2>
 *
 * <pre>
 * 선검사(사용자·중복·예보) → LLM 호출 → 저장·대조(트랜잭션)
 * </pre>
 *
 * <p><b>선검사가 LLM 호출보다 먼저다.</b> 뒤바뀌면 어차피 {@code 409}/{@code 404}로 끝날 요청에
 * 분석 비용을 쓴다. 앱이 재시도를 반복하는 상황에서 특히 아프다.
 *
 * <p><b>LLM 호출은 트랜잭션 밖이다.</b> 최대 30초 걸리는 외부 호출이 DB 커넥션을 잡고 있으면
 * 셀피가 몰릴 때 커넥션 풀이 고갈되어 수면 업로드까지 함께 막힌다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SelfieAnalysisService {

    private static final String IMAGE_CONTENT_TYPE_PREFIX = "image/";

    private final UserRepository userRepository;
    private final SkinForecastRepository skinForecastRepository;
    private final SkinMeasurementRepository skinMeasurementRepository;
    private final SkinVisionClient visionClient;
    private final SkinVerificationService verificationService;

    public SelfieVerificationResponse analyzeAndVerify(Long userId, LocalDate baseDate,
                                                       MultipartFile selfie) {
        precheck(userId, baseDate);

        byte[] image = readBytes(selfie);                                   // 메모리에만 존재한다
        SkinVisionScores scores = visionClient.analyze(image, selfie.getContentType());

        return verificationService.record(userId, baseDate, scores);        // 숫자만 넘어간다
    }

    /**
     * 분석 전에 끝낼 수 있는 실패를 전부 끝낸다.
     *
     * <p><b>동작 API라 예보가 없으면 404다.</b> 조회 API였다면 200 + 빈 상태이지만, 여기는
     * 대조할 기준이 없으면 동작 자체가 성립하지 않는다(conventions.md §2).
     */
    private void precheck(Long userId, LocalDate baseDate) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND,
                    "셀피를 검증할 사용자가 없다 userId=" + userId);
        }
        if (skinMeasurementRepository.findByUserIdAndBaseDate(userId, baseDate).isPresent()) {
            throw new BusinessException(ErrorCode.VERIFICATION_ALREADY_DONE,
                    "이미 검증한 날이다 userId=" + userId + " baseDate=" + baseDate);
        }
        if (skinForecastRepository.findByUserIdAndBaseDate(userId, baseDate).isEmpty()) {
            throw new BusinessException(ErrorCode.SKIN_FORECAST_NOT_FOUND,
                    "대조할 예보가 없다 userId=" + userId + " baseDate=" + baseDate);
        }
    }

    /**
     * ⚠️ <b>예외에 바이트나 파일명을 담지 않는다.</b> 얼굴 데이터를 보관하지 않겠다는 약속은
     * 로그 파일에도 적용된다 — 남기는 것은 크기와 MIME 타입뿐이다.
     */
    private byte[] readBytes(MultipartFile selfie) {
        if (selfie == null || selfie.isEmpty()) {
            throw new BusinessException(ErrorCode.SELFIE_IMAGE_INVALID, "이미지 파트가 비어 있다");
        }

        String contentType = selfie.getContentType();
        if (contentType == null || !contentType.startsWith(IMAGE_CONTENT_TYPE_PREFIX)) {
            throw new BusinessException(ErrorCode.SELFIE_IMAGE_INVALID,
                    "이미지가 아닌 타입이다 contentType=" + contentType);
        }

        try {
            return selfie.getBytes();
        } catch (IOException e) {
            log.error("셀피 바이트를 읽지 못했다 size={} contentType={}", selfie.getSize(), contentType, e);
            throw new BusinessException(ErrorCode.SELFIE_IMAGE_INVALID, "이미지를 읽지 못했다");
        }
    }

}
