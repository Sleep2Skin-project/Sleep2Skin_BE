package com.allday.sleep2skin_be.domain.skin;

import com.allday.sleep2skin_be.domain.skin.dto.response.PersonalModelResponse;
import com.allday.sleep2skin_be.domain.skin.dto.response.SelfieVerificationResponse;
import com.allday.sleep2skin_be.domain.skin.dto.response.SkinForecastQueryResponse;
import com.allday.sleep2skin_be.domain.skin.dto.response.VerificationSummaryResponse;
import com.allday.sleep2skin_be.global.resolver.CurrentUserId;
import com.allday.sleep2skin_be.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

/**
 * 피부 예보 · 검증 · 개인 모델 API.
 *
 * <p>Swagger 문서는 {@link SkinControllerSpec}에 있다. {@link CurrentUserId}는 파라미터
 * 어노테이션이라 인터페이스에서 상속되지 않으므로 <b>여기에도 반드시 붙어 있어야 한다.</b>
 */
@RestController
@RequestMapping("/api/v1/skin")
@RequiredArgsConstructor
public class SkinController implements SkinControllerSpec {

    private final SkinForecastService skinForecastService;
    private final SelfieAnalysisService selfieAnalysisService;
    private final SkinVerificationSummaryService skinVerificationSummaryService;
    private final SkinModelQueryService skinModelQueryService;

    @Override
    @GetMapping("/forecast")
    public ApiResponse<SkinForecastQueryResponse> getForecast(
            @CurrentUserId Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baseDate) {

        return ApiResponse.success(skinForecastService.getForecast(userId, baseDate));
    }

    /**
     * <b>{@code baseDate}는 폼 필드가 아니라 쿼리 파라미터다.</b> 멀티파트 요청이라도 날짜는 다른
     * API와 같은 자리에서 받는다(conventions.md §8) — 요청 형식마다 위치가 달라지면 앱이 API마다
     * 다른 규칙을 외워야 한다.
     *
     * <p><b>{@code required = false}는 파트를 선택으로 만들려는 게 아니다.</b> 기본값
     * ({@code true})으로 두면 파트 누락이 {@code MissingServletRequestPartException}으로 컨트롤러
     * 앞에서 터지고, 그걸 {@code SELFIE_IMAGE_INVALID}로 바꾸려면 <b>{@code global}이 셀피라는
     * 도메인 사정을 알아야 한다.</b> 의존 방향이 {@code domain → global} 한쪽이므로 그럴 수 없다.
     * {@code null}로 받아 Service가 판정하면 코드도 정확하고 의존 방향도 지켜진다.
     */
    @Override
    @PostMapping(value = "/selfie", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<SelfieVerificationResponse> verifySelfie(
            @CurrentUserId Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baseDate,
            @RequestPart(value = "image", required = false) MultipartFile image) {

        return ApiResponse.success(
                selfieAnalysisService.analyzeAndVerify(userId, baseDate, image));
    }

    @Override
    @GetMapping("/verification/summary")
    public ApiResponse<VerificationSummaryResponse> getVerificationSummary(
            @CurrentUserId Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baseDate) {

        return ApiResponse.success(skinVerificationSummaryService.getSummary(userId, baseDate));
    }

    /**
     * <b>여기만 {@code baseDate}를 받지 않는다.</b> 누적 검증 횟수와 가중치는 "오늘"이 필요 없다 —
     * 날짜가 필요한 API만 받는다는 규약(conventions.md §8)의 반대편이다.
     */
    @Override
    @GetMapping("/model")
    public ApiResponse<PersonalModelResponse> getModel(@CurrentUserId Long userId) {
        return ApiResponse.success(skinModelQueryService.getModel(userId));
    }

}
