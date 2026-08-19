package com.allday.sleep2skin_be.domain.skin;

import com.allday.sleep2skin_be.domain.skin.entity.SkinForecast;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMeasurement;
import com.allday.sleep2skin_be.domain.skin.repository.SkinForecastRepository;
import com.allday.sleep2skin_be.domain.skin.repository.SkinMeasurementRepository;
import com.allday.sleep2skin_be.domain.user.repository.UserRepository;
import com.allday.sleep2skin_be.global.exception.BusinessException;
import com.allday.sleep2skin_be.global.exception.ErrorCode;
import com.allday.sleep2skin_be.global.infra.openai.SkinVisionClient;
import com.allday.sleep2skin_be.global.infra.openai.SkinVisionScores;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * <b>이 테스트의 대부분은 "LLM을 부르지 않았다"를 확인한다.</b> 분석은 유료이고 최대 30초
 * 걸리므로, 어차피 실패할 요청이 거기까지 가면 돈과 시간을 함께 잃는다.
 */
@ExtendWith(MockitoExtension.class)
class SelfieAnalysisServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 7);

    @Mock
    private UserRepository userRepository;
    @Mock
    private SkinForecastRepository skinForecastRepository;
    @Mock
    private SkinMeasurementRepository skinMeasurementRepository;
    @Mock
    private SkinVisionClient visionClient;
    @Mock
    private SkinVerificationService verificationService;

    @InjectMocks
    private SelfieAnalysisService selfieAnalysisService;

    @Test
    @DisplayName("선검사를 통과하면 이미지 바이트를 LLM에 넘기고 검증으로 이어진다")
    void 분석하고_검증으로_넘긴다() {
        userExists();
        notVerifiedYet();
        forecastExists();
        given(visionClient.analyze(any(), anyString())).willReturn(new SkinVisionScores(61, 55, 78, false, false, false, false));

        selfieAnalysisService.analyzeAndVerify(USER_ID, BASE_DATE, selfie());

        verify(visionClient).analyze(eq("셀피".getBytes(StandardCharsets.UTF_8)), eq("image/jpeg"));
        verify(verificationService).record(USER_ID, BASE_DATE, new SkinVisionScores(61, 55, 78, false, false, false, false));
    }

    @Test
    @DisplayName("없는 사용자면 분석하지 않는다")
    void 없는_사용자는_분석하지_않는다() {
        given(userRepository.existsById(USER_ID)).willReturn(false);

        assertThatThrownBy(() -> selfieAnalysisService.analyzeAndVerify(USER_ID, BASE_DATE, selfie()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);

        verifyNothingAnalyzed();
    }

    /**
     * 하루 1회 제약에 걸릴 요청이다. <b>순서가 뒤바뀌면 어차피 409로 끝날 요청에 분석 비용을
     * 쓴다</b> — 앱이 재시도를 반복하는 상황에서 특히 아프다.
     */
    @Test
    @DisplayName("이미 검증한 날이면 분석하지 않고 409다")
    void 중복_검증은_분석하지_않는다() {
        userExists();
        given(skinMeasurementRepository.findByUserIdAndBaseDate(USER_ID, BASE_DATE))
                .willReturn(Optional.of(SkinMeasurement.builder()
                        .userId(USER_ID).baseDate(BASE_DATE)
                        .darkCircle(61).complexion(55).barrier(78)
                        .analyzedAt(OffsetDateTime.parse("2026-08-07T12:33:12Z"))
                        .build()));

        assertThatThrownBy(() -> selfieAnalysisService.analyzeAndVerify(USER_ID, BASE_DATE, selfie()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VERIFICATION_ALREADY_DONE);

        verifyNothingAnalyzed();
    }

    /**
     * <b>동작 API라 예보 부재가 에러다.</b> 조회 API였다면 200 + 빈 상태이지만, 여기는 대조할
     * 기준이 없으면 동작 자체가 성립하지 않는다.
     */
    @Test
    @DisplayName("그날 예보가 없으면 분석하지 않고 404다")
    void 예보가_없으면_분석하지_않는다() {
        userExists();
        notVerifiedYet();
        given(skinForecastRepository.findByUserIdAndBaseDate(USER_ID, BASE_DATE))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> selfieAnalysisService.analyzeAndVerify(USER_ID, BASE_DATE, selfie()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SKIN_FORECAST_NOT_FOUND);

        verifyNothingAnalyzed();
    }

    @Test
    @DisplayName("파트가 아예 없으면 400 SELFIE_IMAGE_INVALID다")
    void 파트가_없으면_400이다() {
        userExists();
        notVerifiedYet();
        forecastExists();

        assertThatThrownBy(() -> selfieAnalysisService.analyzeAndVerify(USER_ID, BASE_DATE, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SELFIE_IMAGE_INVALID);

        verifyNothingAnalyzed();
    }

    @Test
    @DisplayName("빈 파일이면 400 SELFIE_IMAGE_INVALID다")
    void 빈_파일은_400이다() {
        userExists();
        notVerifiedYet();
        forecastExists();
        MultipartFile empty = new MockMultipartFile("image", "s.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> selfieAnalysisService.analyzeAndVerify(USER_ID, BASE_DATE, empty))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SELFIE_IMAGE_INVALID);

        verifyNothingAnalyzed();
    }

    /**
     * 이미지가 아닌 것을 base64로 실어 보내면 LLM 쪽에서 실패하는데, <b>그 실패는 502라 앱이
     * 재시도한다.</b> 재시도해도 결과가 같으므로 여기서 400으로 끊는다.
     */
    @Test
    @DisplayName("이미지가 아닌 타입이면 분석하지 않고 400이다")
    void 이미지가_아니면_400이다() {
        userExists();
        notVerifiedYet();
        forecastExists();
        MultipartFile pdf = new MockMultipartFile("image", "s.pdf", "application/pdf",
                "not-an-image".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> selfieAnalysisService.analyzeAndVerify(USER_ID, BASE_DATE, pdf))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SELFIE_IMAGE_INVALID);

        verifyNothingAnalyzed();
    }

    // ===== 픽스처 =====

    private static MultipartFile selfie() {
        return new MockMultipartFile("image", "selfie.jpg", "image/jpeg",
                "셀피".getBytes(StandardCharsets.UTF_8));
    }

    private void userExists() {
        given(userRepository.existsById(USER_ID)).willReturn(true);
    }

    private void notVerifiedYet() {
        given(skinMeasurementRepository.findByUserIdAndBaseDate(USER_ID, BASE_DATE))
                .willReturn(Optional.empty());
    }

    private void forecastExists() {
        given(skinForecastRepository.findByUserIdAndBaseDate(USER_ID, BASE_DATE))
                .willReturn(Optional.of(SkinForecast.builder()
                        .userId(USER_ID).baseDate(BASE_DATE)
                        .darkCircle(67).complexion(62).barrier(81)
                        .build()));
    }

    private void verifyNothingAnalyzed() {
        verify(visionClient, never()).analyze(any(), anyString());
        verify(verificationService, never()).record(any(), any(), any());
    }

}
