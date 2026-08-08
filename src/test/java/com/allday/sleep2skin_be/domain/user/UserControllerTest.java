package com.allday.sleep2skin_be.domain.user;

import com.allday.sleep2skin_be.domain.user.dto.response.ConsentAgreeResponse;
import com.allday.sleep2skin_be.domain.user.dto.response.OnboardingCompleteResponse;
import com.allday.sleep2skin_be.global.exception.BusinessException;
import com.allday.sleep2skin_be.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 동의·온보딩 API의 요청·응답 계약 검증.
 *
 * <p>{@code X-User-Id} 헤더를 읽는 자리가 {@code CurrentUserIdArgumentResolver} 하나뿐이므로,
 * 헤더 관련 케이스는 여기서 한 번 검증해두면 이후 모든 API에 그대로 적용된다.
 */
@WebMvcTest(UserController.class)
class UserControllerTest {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final Long USER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConsentService consentService;

    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("첫 동의는 201과 함께 저장된 이력을 반환한다")
    void 첫_동의는_201이다() throws Exception {
        given(consentService.agree(USER_ID)).willReturn(new ConsentAgreeResponse(
                10L, "1.0", OffsetDateTime.of(2026, 8, 8, 0, 12, 33, 0, ZoneOffset.UTC), true));

        mockMvc.perform(post("/api/v1/users/me/consents").header(USER_ID_HEADER, USER_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.data.consentId").value(10))
                .andExpect(jsonPath("$.data.termsVersion").value("1.0"))
                .andExpect(jsonPath("$.data.newlyAgreed").value(true));
    }

    @Test
    @DisplayName("같은 버전에 이미 동의한 상태면 200과 기존 이력을 반환한다")
    void 같은_버전_재요청은_200이다() throws Exception {
        given(consentService.agree(USER_ID)).willReturn(new ConsentAgreeResponse(
                10L, "1.0", OffsetDateTime.of(2026, 8, 8, 0, 12, 33, 0, ZoneOffset.UTC), false));

        mockMvc.perform(post("/api/v1/users/me/consents").header(USER_ID_HEADER, USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.newlyAgreed").value(false));
    }

    @Test
    @DisplayName("존재하지 않는 사용자의 동의 요청은 404 USER_NOT_FOUND다")
    void 없는_사용자의_동의는_404다() throws Exception {
        given(consentService.agree(USER_ID))
                .willThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

        mockMvc.perform(post("/api/v1/users/me/consents").header(USER_ID_HEADER, USER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("X-User-Id 헤더가 없으면 서비스를 호출하지 않고 400을 반환한다")
    void 헤더가_없으면_400이다() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/consents"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("USER_ID_HEADER_INVALID"));

        verify(consentService, never()).agree(anyLong());
    }

    @Test
    @DisplayName("X-User-Id 헤더가 숫자가 아니면 400을 반환한다")
    void 헤더가_숫자가_아니면_400이다() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/consents").header(USER_ID_HEADER, "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("USER_ID_HEADER_INVALID"));

        verify(consentService, never()).agree(anyLong());
    }

    /**
     * {@code jsonPath("$.error").doesNotExist()}로는 이걸 검증할 수 없다 — JsonPath가 명시적 null을
     * 부재로 취급해 {@code "error": null}이 그대로 실려 나가도 통과한다. 그래서 원문을 본다.
     */
    @Test
    @DisplayName("래퍼는 비어 있는 쪽을 아예 직렬화하지 않는다")
    void 래퍼는_비어있는_필드를_내보내지_않는다() throws Exception {
        given(consentService.agree(USER_ID)).willReturn(new ConsentAgreeResponse(
                10L, "1.0", OffsetDateTime.of(2026, 8, 8, 0, 12, 33, 0, ZoneOffset.UTC), true));

        mockMvc.perform(post("/api/v1/users/me/consents").header(USER_ID_HEADER, USER_ID))
                .andExpect(content().string(not(containsString("\"error\""))));

        given(consentService.agree(USER_ID))
                .willThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

        mockMvc.perform(post("/api/v1/users/me/consents").header(USER_ID_HEADER, USER_ID))
                .andExpect(content().string(not(containsString("\"data\""))));
    }

    @Test
    @DisplayName("온보딩 완료는 200과 함께 바뀐 상태를 반환한다")
    void 온보딩_완료는_200이다() throws Exception {
        given(userService.completeOnboarding(USER_ID))
                .willReturn(new OnboardingCompleteResponse(USER_ID, true, true));

        mockMvc.perform(patch("/api/v1/users/me/onboarding").header(USER_ID_HEADER, USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.onboardingCompleted").value(true))
                .andExpect(jsonPath("$.data.newlyCompleted").value(true));
    }

    @Test
    @DisplayName("이미 온보딩을 마친 사용자도 200이며 newlyCompleted만 false다")
    void 이미_완료된_온보딩도_200이다() throws Exception {
        given(userService.completeOnboarding(USER_ID))
                .willReturn(new OnboardingCompleteResponse(USER_ID, true, false));

        mockMvc.perform(patch("/api/v1/users/me/onboarding").header(USER_ID_HEADER, USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboardingCompleted").value(true))
                .andExpect(jsonPath("$.data.newlyCompleted").value(false));
    }

}
