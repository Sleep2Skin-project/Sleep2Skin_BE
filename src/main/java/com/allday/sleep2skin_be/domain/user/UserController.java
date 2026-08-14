package com.allday.sleep2skin_be.domain.user;

import com.allday.sleep2skin_be.domain.user.dto.response.ConsentAgreeResponse;
import com.allday.sleep2skin_be.domain.user.dto.response.OnboardingCompleteResponse;
import com.allday.sleep2skin_be.domain.user.dto.response.SleepDataStatusResponse;
import com.allday.sleep2skin_be.domain.user.dto.response.UserDeleteResponse;
import com.allday.sleep2skin_be.domain.user.dto.response.UserProfileResponse;
import com.allday.sleep2skin_be.global.resolver.CurrentUserId;
import com.allday.sleep2skin_be.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 사용자·동의·온보딩 API.
 *
 * <p>Swagger 문서는 {@link UserControllerSpec}에 있다. {@link CurrentUserId}는 파라미터
 * 어노테이션이라 인터페이스에서 상속되지 않으므로 <b>여기에도 반드시 붙어 있어야 한다.</b>
 * 빼면 리졸버가 동작하지 않아 헤더가 주입되지 않는다.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController implements UserControllerSpec {

    private final ConsentService consentService;
    private final UserService userService;

    @Override
    @PostMapping("/me/consents")
    public ResponseEntity<ApiResponse<ConsentAgreeResponse>> agreeConsent(@CurrentUserId Long userId) {
        ConsentAgreeResponse response = consentService.agree(userId);

        // 새 이력이 생긴 요청만 201이다. 상태 코드를 직접 정해야 해 ResponseEntity를 쓴다.
        HttpStatus status = response.newlyAgreed() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.success(response));
    }

    @Override
    @PatchMapping("/me/onboarding")
    public ApiResponse<OnboardingCompleteResponse> completeOnboarding(@CurrentUserId Long userId) {
        return ApiResponse.success(userService.completeOnboarding(userId));
    }

    @Override
    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> getProfile(
            @CurrentUserId Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baseDate) {

        return ApiResponse.success(userService.getProfile(userId, baseDate));
    }

    @Override
    @GetMapping("/me/data-status")
    public ApiResponse<SleepDataStatusResponse> getSleepDataStatus(@CurrentUserId Long userId) {
        return ApiResponse.success(userService.getSleepDataStatus(userId));
    }

    @Override
    @DeleteMapping("/me")
    public ApiResponse<UserDeleteResponse> delete(@CurrentUserId Long userId) {
        return ApiResponse.success(userService.delete(userId));
    }

}
