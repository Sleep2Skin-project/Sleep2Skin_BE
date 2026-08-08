package com.allday.sleep2skin_be.domain.skin;

import com.allday.sleep2skin_be.domain.skin.dto.SkinGrade;
import com.allday.sleep2skin_be.domain.skin.dto.UnavailableReason;
import com.allday.sleep2skin_be.domain.skin.dto.response.SkinForecastQueryResponse;
import com.allday.sleep2skin_be.domain.skin.dto.response.SkinForecastResponse;
import com.allday.sleep2skin_be.domain.skin.dto.response.SkinForecastResponse.MetricScore;
import com.allday.sleep2skin_be.domain.skin.dto.response.SkinForecastResponse.UnavailableMetricResponse;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMetric;
import com.allday.sleep2skin_be.global.exception.BusinessException;
import com.allday.sleep2skin_be.global.exception.ErrorCode;
import com.allday.sleep2skin_be.global.response.QueryStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SkinController.class)
class SkinControllerTest {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final Long USER_ID = 1L;
    private static final String PATH = "/api/v1/skin/forecast";
    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 7);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SkinForecastService skinForecastService;

    @Test
    @DisplayName("예보가 있으면 200과 함께 점수·등급을 반환한다")
    void 예보를_반환한다() throws Exception {
        given(skinForecastService.getForecast(USER_ID, BASE_DATE)).willReturn(available());

        mockMvc.perform(get(PATH).header(USER_ID_HEADER, USER_ID).param("baseDate", "2026-08-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.baseDate").value("2026-08-07"))
                .andExpect(jsonPath("$.data.forecast.darkCircle.score").value(67))
                .andExpect(jsonPath("$.data.forecast.barrier.grade").value("STABLE"));
    }

    /**
     * 4xx로 내리면 경로 오타·잘못된 {@code userId}와 섞여 모니터링에서 신규 유입이 에러 급증으로
     * 보인다. <b>빈 상태는 이 서비스의 정상 흐름이다.</b>
     */
    @Test
    @DisplayName("예보가 없어도 200이고 status로 알린다")
    void 빈_상태도_200이다() throws Exception {
        given(skinForecastService.getForecast(USER_ID, BASE_DATE))
                .willReturn(SkinForecastQueryResponse.empty(BASE_DATE));

        mockMvc.perform(get(PATH).header(USER_ID_HEADER, USER_ID).param("baseDate", "2026-08-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("NO_SLEEP_DATA"))
                .andExpect(jsonPath("$.data.message").isNotEmpty());
    }

    /**
     * 래퍼의 {@code error}만 사라지고 <b>페이로드 안쪽의 {@code null}은 그대로 나간다.</b>
     * 키가 통째로 없어지면 클라이언트가 "산출 불가"와 "키 이름 오류"를 구분할 수 없다.
     */
    @Test
    @DisplayName("빈 상태에서도 forecast 키가 null로 실려 나간다")
    void 페이로드_안쪽_null은_직렬화된다() throws Exception {
        given(skinForecastService.getForecast(USER_ID, BASE_DATE))
                .willReturn(SkinForecastQueryResponse.empty(BASE_DATE));

        mockMvc.perform(get(PATH).header(USER_ID_HEADER, USER_ID).param("baseDate", "2026-08-07"))
                .andExpect(content().string(containsString("\"forecast\":null")))
                .andExpect(content().string(not(containsString("\"error\""))));
    }

    @Test
    @DisplayName("산출하지 못한 지표는 null로 나가고 사유가 함께 실린다")
    void 빈_지표는_사유와_함께_나간다() throws Exception {
        given(skinForecastService.getForecast(USER_ID, BASE_DATE)).willReturn(
                new SkinForecastQueryResponse(QueryStatus.AVAILABLE, null, BASE_DATE,
                        new SkinForecastResponse(
                                new MetricScore(67, SkinGrade.NORMAL), null,
                                new MetricScore(81, SkinGrade.STABLE),
                                List.of(new UnavailableMetricResponse(SkinMetric.COMPLEXION,
                                        UnavailableReason.MISSING_FEATURES)))));

        mockMvc.perform(get(PATH).header(USER_ID_HEADER, USER_ID).param("baseDate", "2026-08-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"))
                .andExpect(content().string(containsString("\"complexion\":null")))
                .andExpect(jsonPath("$.data.forecast.unavailable[0].metric").value("COMPLEXION"))
                .andExpect(jsonPath("$.data.forecast.unavailable[0].reason").value("MISSING_FEATURES"));
    }

    /**
     * 서버는 "오늘"을 모른다 — 타임존을 저장하지 않기 때문이다. 기본값을 넣어주면 UTC 기준이 되어
     * <b>한국 시간 오전 9시 이전에 하루 밀린 예보</b>가 나간다. 그래서 필수다.
     */
    @Test
    @DisplayName("baseDate가 없으면 400이고 서비스를 호출하지 않는다")
    void 기준일이_없으면_400이다() throws Exception {
        mockMvc.perform(get(PATH).header(USER_ID_HEADER, USER_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        verify(skinForecastService, never()).getForecast(anyLong(), any());
    }

    @Test
    @DisplayName("baseDate 형식이 틀리면 400이다")
    void 기준일_형식이_틀리면_400이다() throws Exception {
        mockMvc.perform(get(PATH).header(USER_ID_HEADER, USER_ID).param("baseDate", "2026/08/07"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        verify(skinForecastService, never()).getForecast(anyLong(), any());
    }

    @Test
    @DisplayName("존재하지 않는 사용자는 404 USER_NOT_FOUND다")
    void 없는_사용자는_404다() throws Exception {
        given(skinForecastService.getForecast(USER_ID, BASE_DATE))
                .willThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

        mockMvc.perform(get(PATH).header(USER_ID_HEADER, USER_ID).param("baseDate", "2026-08-07"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("X-User-Id 헤더가 없으면 400이고 서비스를 호출하지 않는다")
    void 헤더가_없으면_400이다() throws Exception {
        mockMvc.perform(get(PATH).param("baseDate", "2026-08-07"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("USER_ID_HEADER_INVALID"));

        verify(skinForecastService, never()).getForecast(anyLong(), any());
    }

    private static SkinForecastQueryResponse available() {
        return new SkinForecastQueryResponse(QueryStatus.AVAILABLE, null, BASE_DATE,
                new SkinForecastResponse(
                        new MetricScore(67, SkinGrade.NORMAL),
                        new MetricScore(62, SkinGrade.NORMAL),
                        new MetricScore(81, SkinGrade.STABLE),
                        List.of()));
    }

}
