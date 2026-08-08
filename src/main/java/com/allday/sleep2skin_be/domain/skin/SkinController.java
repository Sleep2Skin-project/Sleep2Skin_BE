package com.allday.sleep2skin_be.domain.skin;

import com.allday.sleep2skin_be.domain.skin.dto.response.SkinForecastQueryResponse;
import com.allday.sleep2skin_be.global.resolver.CurrentUserId;
import com.allday.sleep2skin_be.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @Override
    @GetMapping("/forecast")
    public ApiResponse<SkinForecastQueryResponse> getForecast(
            @CurrentUserId Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baseDate) {

        return ApiResponse.success(skinForecastService.getForecast(userId, baseDate));
    }

}
