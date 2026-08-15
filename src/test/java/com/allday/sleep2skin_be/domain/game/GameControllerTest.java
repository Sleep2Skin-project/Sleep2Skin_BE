package com.allday.sleep2skin_be.domain.game;

import com.allday.sleep2skin_be.domain.game.dto.response.AttendanceResponse;
import com.allday.sleep2skin_be.domain.game.dto.response.AttendanceResponse.AttendanceDayResponse;
import com.allday.sleep2skin_be.domain.game.dto.response.ExpResponse;
import com.allday.sleep2skin_be.domain.game.dto.response.ExpResponse.ExpReasonResponse;
import com.allday.sleep2skin_be.domain.game.entity.ExpReason;
import com.allday.sleep2skin_be.global.exception.BusinessException;
import com.allday.sleep2skin_be.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 출석 체크인 API의 요청·응답 계약 검증 (HOME-04).
 */
@WebMvcTest(GameController.class)
class GameControllerTest {

    private static final String PATH = "/api/v1/users/me/attendance";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final Long USER_ID = 1L;
    /** 2026-08-14는 금요일 — 그 주의 월요일이 08-10이고 토·일이 아직 오지 않았다. */
    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 14);
    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 10);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AttendanceService attendanceService;

    @Test
    @DisplayName("그날 첫 호출은 200 + checkedIn true + exp 객체를 준다")
    void 첫_호출은_적립_결과를_준다() throws Exception {
        given(attendanceService.checkIn(USER_ID, BASE_DATE)).willReturn(
                AttendanceResponse.of(BASE_DATE, 3, ExpResponse.of(310, 320,
                        List.of(new ExpReasonResponse(ExpReason.ATTENDANCE, 10))), week()));

        mockMvc.perform(post(PATH).header(USER_ID_HEADER, USER_ID)
                        .param("baseDate", BASE_DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.data.baseDate").value("2026-08-14"))
                .andExpect(jsonPath("$.data.checkedIn").value(true))
                .andExpect(jsonPath("$.data.streakCount").value(3))
                .andExpect(jsonPath("$.data.exp.gained").value(10))
                .andExpect(jsonPath("$.data.exp.reasons[0].reason").value("ATTENDANCE"))
                .andExpect(jsonPath("$.data.exp.reasons[0].amount").value(10))
                .andExpect(jsonPath("$.data.exp.totalExp").value(320))
                .andExpect(jsonPath("$.data.exp.level").value(3))
                .andExpect(jsonPath("$.data.exp.levelUp").value(false))
                .andExpect(jsonPath("$.data.exp.nextLevelExp").value(450));
    }

    /**
     * <b>409가 아니라 200이다.</b> 앱은 시작할 때마다 호출하므로 하루에 다섯 번 켜면 네 번은
     * 재호출이다 — 정상 흐름을 에러로 만들면 진짜 문제가 묻힌다.
     */
    @Test
    @DisplayName("같은 날 재호출도 200이고 checkedIn false · gained 0이다")
    void 재호출도_200이다() throws Exception {
        given(attendanceService.checkIn(USER_ID, BASE_DATE))
                .willReturn(AttendanceResponse.of(BASE_DATE, 3, ExpResponse.unchanged(320), week()));

        mockMvc.perform(post(PATH).header(USER_ID_HEADER, USER_ID)
                        .param("baseDate", BASE_DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checkedIn").value(false))
                .andExpect(jsonPath("$.data.exp.gained").value(0))
                .andExpect(jsonPath("$.data.exp.reasons").isEmpty())
                .andExpect(jsonPath("$.data.exp.totalExp").value(320));
    }

    @Test
    @DisplayName("만렙이면 nextLevelExp가 없다")
    void 만렙은_nextLevelExp가_없다() throws Exception {
        given(attendanceService.checkIn(USER_ID, BASE_DATE))
                .willReturn(AttendanceResponse.of(BASE_DATE, 0, ExpResponse.unchanged(900),
                        week()));

        mockMvc.perform(post(PATH).header(USER_ID_HEADER, USER_ID)
                        .param("baseDate", BASE_DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exp.level").value(5))
                .andExpect(jsonPath("$.data.exp.nextLevelExp").doesNotExist());
    }

    /**
     * <b>7칸이 고정이라는 것과 요일이 영어 상수라는 것이 계약이다.</b> 칸 수가 흔들리면 앱이
     * 도장판 레이아웃을 잡지 못하고, {@code MISSED}와 {@code UPCOMING}이 섞이면 아직 오지 않은
     * 날이 빠뜨린 날로 그려진다.
     */
    @Test
    @DisplayName("도장판이 월~일 7칸으로 직렬화된다")
    void 도장판이_7칸으로_나간다() throws Exception {
        given(attendanceService.checkIn(USER_ID, BASE_DATE)).willReturn(
                AttendanceResponse.of(BASE_DATE, 3, ExpResponse.unchanged(320),
                        new AttendanceWeekCalculator().calculate(BASE_DATE,
                                List.of(MONDAY, BASE_DATE))));

        mockMvc.perform(post(PATH).header(USER_ID_HEADER, USER_ID)
                        .param("baseDate", BASE_DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weekStartDate").value("2026-08-10"))
                .andExpect(jsonPath("$.data.weekDays.length()").value(7))
                .andExpect(jsonPath("$.data.weekDays[0].date").value("2026-08-10"))
                .andExpect(jsonPath("$.data.weekDays[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.data.weekDays[0].status").value("ATTENDED"))
                .andExpect(jsonPath("$.data.weekDays[1].status").value("MISSED"))
                .andExpect(jsonPath("$.data.weekDays[4].date").value("2026-08-14"))
                .andExpect(jsonPath("$.data.weekDays[4].status").value("ATTENDED"))
                .andExpect(jsonPath("$.data.weekDays[6].dayOfWeek").value("SUNDAY"))
                .andExpect(jsonPath("$.data.weekDays[6].status").value("UPCOMING"));
    }

    /** 서버는 "오늘"을 모른다 — 없이 처리하면 출석이 어제 날짜로 찍히고 오늘 몫이 또 나간다. */
    @Test
    @DisplayName("baseDate가 없으면 400이고 서비스를 부르지 않는다")
    void 기준일이_필수다() throws Exception {
        mockMvc.perform(post(PATH).header(USER_ID_HEADER, USER_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.name()));

        verify(attendanceService, never()).checkIn(anyLong(), any());
    }

    @Test
    @DisplayName("X-User-Id 헤더가 없으면 400이다")
    void 헤더가_없으면_400이다() throws Exception {
        mockMvc.perform(post(PATH).param("baseDate", BASE_DATE.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.USER_ID_HEADER_INVALID.name()));

        verify(attendanceService, never()).checkIn(anyLong(), any());
    }

    @Test
    @DisplayName("없는 사용자면 404 USER_NOT_FOUND다")
    void 없는_사용자는_404다() throws Exception {
        given(attendanceService.checkIn(USER_ID, BASE_DATE))
                .willThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

        mockMvc.perform(post(PATH).header(USER_ID_HEADER, USER_ID)
                        .param("baseDate", BASE_DATE.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.USER_NOT_FOUND.name()));
    }

    /** 도장판 자체를 보지 않는 테스트를 위한 최소 픽스처 — 계산은 진짜를 쓴다. */
    private static List<AttendanceDayResponse> week() {
        return new AttendanceWeekCalculator().calculate(BASE_DATE, List.of(BASE_DATE));
    }

}
