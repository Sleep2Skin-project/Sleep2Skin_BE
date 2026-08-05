package com.allday.sleep2skin_be.domain.health;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller 슬라이스 테스트.
 *
 * <p>{@code @WebMvcTest}는 웹 계층만 띄우므로 DataSource가 필요 없다.
 * {@code @RestControllerAdvice}는 포함되므로 전역 예외 처리도 함께 검증된다.
 */
@WebMvcTest(HealthCheckController.class)
class HealthCheckControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("헬스체크는 공통 래퍼에 담긴 UP 상태를 반환한다")
    void health_returnsUp() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.applicationName").value("sleep2skin_be"))
                .andExpect(jsonPath("$.data.serverTime").exists());
    }

    @Test
    @DisplayName("존재하지 않는 경로는 공통 래퍼에 담긴 404 에러를 반환한다")
    void unknownPath_returnsWrappedNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/not-exists"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

}
