package com.allday.sleep2skin_be.domain.game;

import com.allday.sleep2skin_be.domain.game.dto.ExpGrantCommand;
import com.allday.sleep2skin_be.domain.game.dto.response.AttendanceResponse;
import com.allday.sleep2skin_be.domain.game.dto.response.ExpResponse;
import com.allday.sleep2skin_be.domain.game.dto.response.ExpResponse.ExpReasonResponse;
import com.allday.sleep2skin_be.domain.game.entity.ExpReason;
import com.allday.sleep2skin_be.domain.skin.VerificationStreakCalculator;
import com.allday.sleep2skin_be.domain.skin.repository.SkinMeasurementRepository;
import com.allday.sleep2skin_be.domain.user.repository.UserRepository;
import com.allday.sleep2skin_be.global.exception.BusinessException;
import com.allday.sleep2skin_be.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttendanceServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 14);

    @Mock
    private UserRepository userRepository;
    @Mock
    private ExpService expService;
    @Mock
    private SkinMeasurementRepository skinMeasurementRepository;

    private AttendanceService attendanceService;

    @BeforeEach
    void setUp() {
        // 연속 계산은 진짜를 쓴다 — 스텁으로 두면 검증하려는 규칙을 테스트가 직접 정하게 된다
        attendanceService = new AttendanceService(userRepository, expService,
                skinMeasurementRepository, new VerificationStreakCalculator());
    }

    @Test
    @DisplayName("그날 첫 호출이면 ATTENDANCE +10을 적립하고 checkedIn=true다")
    void 첫_호출은_적립한다() {
        userExists();
        verifiedOn(BASE_DATE, BASE_DATE.minusDays(1), BASE_DATE.minusDays(2));
        grants(0, 10, new ExpReasonResponse(ExpReason.ATTENDANCE, 10));

        AttendanceResponse response = attendanceService.checkIn(USER_ID, BASE_DATE);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ExpGrantCommand>> commands = ArgumentCaptor.forClass(List.class);
        verify(expService).grantDaily(eq(USER_ID), eq(BASE_DATE), commands.capture());
        assertThat(commands.getValue())
                .containsExactly(new ExpGrantCommand(ExpReason.ATTENDANCE,
                        LevelPolicy.ATTENDANCE_EXP));

        assertThat(response.checkedIn()).isTrue();
        assertThat(response.baseDate()).isEqualTo(BASE_DATE);
        assertThat(response.exp().gained()).isEqualTo(10);
    }

    /**
     * <b>409가 아니라 200이다.</b> 앱은 시작할 때마다 호출하므로 하루에 다섯 번 켜면 네 번은
     * 재호출이다 — 정상 흐름을 에러로 만들면 진짜 문제가 묻힌다(api.md §2.1).
     */
    @Test
    @DisplayName("같은 날 재호출은 에러가 아니라 checkedIn=false · gained 0이다")
    void 재호출은_에러가_아니다() {
        userExists();
        verifiedOn();
        grants(310, 310);                                    // 적립이 일어나지 않았다

        AttendanceResponse response = attendanceService.checkIn(USER_ID, BASE_DATE);

        assertThat(response.checkedIn()).isFalse();
        assertThat(response.exp().gained()).isZero();
        assertThat(response.exp().reasons()).isEmpty();
        assertThat(response.exp().totalExp()).isEqualTo(310);
    }

    /**
     * <b>같은 계산이라야 HOME-09·MY-01·출석 팝업이 같은 숫자를 보여준다</b>(prd.md §4.2).
     * 어긋나도 값 범위는 정상이라 알아채기 어렵다.
     */
    @Test
    @DisplayName("연속 검증 횟수가 HOME-09·MY-01과 같은 규칙으로 계산된다")
    void 연속을_같은_규칙으로_센다() {
        userExists();
        // 오늘 · 어제 · 그제 연속 후 하루 비었다
        verifiedOn(BASE_DATE, BASE_DATE.minusDays(1), BASE_DATE.minusDays(2),
                BASE_DATE.minusDays(4));
        grants(0, 10, new ExpReasonResponse(ExpReason.ATTENDANCE, 10));

        assertThat(attendanceService.checkIn(USER_ID, BASE_DATE).streakCount()).isEqualTo(3);
    }

    /** 저녁에 검증하는 사용자가 아침에 0을 보면 아직 하지 않은 일로 벌주는 것처럼 읽힌다. */
    @Test
    @DisplayName("오늘 미검증이 연속을 끊지 않는다")
    void 오늘_미검증은_연속을_끊지_않는다() {
        userExists();
        verifiedOn(BASE_DATE.minusDays(1), BASE_DATE.minusDays(2));
        grants(0, 10, new ExpReasonResponse(ExpReason.ATTENDANCE, 10));

        assertThat(attendanceService.checkIn(USER_ID, BASE_DATE).streakCount()).isEqualTo(2);
    }

    /**
     * <b>연속 검증 보상은 여기서 주지 않는다.</b> 검증이 일어나는 {@code POST /skin/selfie}가
     * 준다 — 여기서 함께 지급하면 셀피를 찍지 않아도 보상이 나간다.
     */
    @Test
    @DisplayName("연속이 길어도 VERIFICATION_STREAK은 적립하지 않는다")
    void 연속_보상은_주지_않는다() {
        userExists();
        verifiedOn(BASE_DATE, BASE_DATE.minusDays(1), BASE_DATE.minusDays(2),
                BASE_DATE.minusDays(3), BASE_DATE.minusDays(4));
        grants(0, 10, new ExpReasonResponse(ExpReason.ATTENDANCE, 10));

        AttendanceResponse response = attendanceService.checkIn(USER_ID, BASE_DATE);

        assertThat(response.streakCount()).isEqualTo(5);
        assertThat(response.exp().reasons())
                .extracting(ExpReasonResponse::reason)
                .containsExactly(ExpReason.ATTENDANCE);
    }

    @Test
    @DisplayName("존재하지 않는 사용자면 적립하지 않고 USER_NOT_FOUND다")
    void 없는_사용자는_404다() {
        given(userRepository.existsById(USER_ID)).willReturn(false);

        assertThatThrownBy(() -> attendanceService.checkIn(USER_ID, BASE_DATE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(expService, never()).grantDaily(anyLong(), any(), any());
    }

    // ===== 픽스처 =====

    private void userExists() {
        given(userRepository.existsById(USER_ID)).willReturn(true);
    }

    /** 조회는 최신순으로 준다 — 연속 계산이 앞에서부터 읽는다. */
    private void verifiedOn(LocalDate... dates) {
        given(skinMeasurementRepository.findVerifiedBaseDates(USER_ID, BASE_DATE))
                .willReturn(List.of(dates));
    }

    private void grants(int before, int after, ExpReasonResponse... reasons) {
        given(expService.grantDaily(eq(USER_ID), eq(BASE_DATE), any()))
                .willReturn(ExpResponse.of(before, after, List.of(reasons)));
    }

}
