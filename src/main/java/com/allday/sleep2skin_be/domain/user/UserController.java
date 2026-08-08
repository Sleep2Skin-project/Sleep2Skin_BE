package com.allday.sleep2skin_be.domain.user;

import com.allday.sleep2skin_be.domain.user.dto.response.ConsentAgreeResponse;
import com.allday.sleep2skin_be.domain.user.dto.response.OnboardingCompleteResponse;
import com.allday.sleep2skin_be.global.resolver.CurrentUserId;
import com.allday.sleep2skin_be.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User", description = "사용자 · 동의 · 온보딩 API")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final ConsentService consentService;
    private final UserService userService;

    @Operation(summary = "개인정보 수집·이용 동의 저장",
            description = """
                    현재 약관 버전에 대한 동의를 이력에 남긴다 (ONB-02).
                    약관 버전은 서버 상수이므로 요청 본문이 없다.
                    같은 버전에 이미 동의한 상태면 새 이력을 만들지 않고 기존 이력을 200으로 돌려준다.""")
    // Swagger의 @ApiResponse는 우리 래퍼와 이름이 겹쳐 완전 수식한다.
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "동의 이력 저장됨")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "이미 같은 버전에 동의한 상태 — 기존 이력 반환")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "X-User-Id 헤더 누락 또는 형식 오류")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 사용자")
    @PostMapping("/me/consents")
    public ResponseEntity<ApiResponse<ConsentAgreeResponse>> agreeConsent(@CurrentUserId Long userId) {
        ConsentAgreeResponse response = consentService.agree(userId);

        // 새 이력이 생긴 요청만 201이다. 상태 코드를 직접 정해야 해 ResponseEntity를 쓴다.
        HttpStatus status = response.newlyAgreed() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.success(response));
    }

    @Operation(summary = "온보딩 완료 처리",
            description = """
                    사용자의 온보딩 완료 상태를 true로 바꾼다 (ONB-05).
                    상태 하나만 바꾸므로 PATCH이며 요청 본문이 없다.
                    이미 완료된 사용자도 에러가 아니라 정상 응답이다.""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "처리 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "X-User-Id 헤더 누락 또는 형식 오류")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 사용자")
    @PatchMapping("/me/onboarding")
    public ApiResponse<OnboardingCompleteResponse> completeOnboarding(@CurrentUserId Long userId) {
        return ApiResponse.success(userService.completeOnboarding(userId));
    }

}
