package com.allday.sleep2skin_be.domain.game;

import com.allday.sleep2skin_be.domain.game.dto.ExpGrantCommand;
import com.allday.sleep2skin_be.domain.game.dto.response.ExpResponse;
import com.allday.sleep2skin_be.domain.game.dto.response.ExpResponse.ExpReasonResponse;
import com.allday.sleep2skin_be.domain.game.entity.ExpGrant;
import com.allday.sleep2skin_be.domain.game.entity.ExpReason;
import com.allday.sleep2skin_be.domain.game.repository.ExpGrantRepository;
import com.allday.sleep2skin_be.domain.user.entity.User;
import com.allday.sleep2skin_be.domain.user.repository.UserRepository;
import com.allday.sleep2skin_be.global.exception.BusinessException;
import com.allday.sleep2skin_be.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExpServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 14);

    @Mock
    private UserRepository userRepository;
    @Mock
    private ExpGrantRepository expGrantRepository;

    @InjectMocks
    private ExpService expService;

    @Nested
    @DisplayName("하루 1회 적립 (exp_grant)")
    class 하루_1회_적립 {

        @Test
        @DisplayName("이력 행을 남기고 users.exp를 올린다")
        void 이력을_남기고_적립한다() {
            User user = user(0);

            ExpResponse response = expService.grantDaily(USER_ID, BASE_DATE,
                    List.of(new ExpGrantCommand(ExpReason.ATTENDANCE, 10)));

            ArgumentCaptor<ExpGrant> saved = ArgumentCaptor.forClass(ExpGrant.class);
            verify(expGrantRepository).save(saved.capture());
            assertThat(saved.getValue().getReason()).isEqualTo(ExpReason.ATTENDANCE);
            assertThat(saved.getValue().getBaseDate()).isEqualTo(BASE_DATE);
            assertThat(saved.getValue().getAmount()).isEqualTo(10);

            assertThat(user.getExp()).isEqualTo(10);
            assertThat(response.gained()).isEqualTo(10);
            assertThat(response.totalExp()).isEqualTo(10);
            assertThat(response.reasons())
                    .extracting(ExpReasonResponse::reason, ExpReasonResponse::amount)
                    .containsExactly(tuple(ExpReason.ATTENDANCE, 10));
        }

        /**
         * <b>앱은 시작할 때마다 호출한다.</b> 하루에 다섯 번 켜면 네 번은 재호출이고, 그때
         * {@code gained: 0}이 정상 응답이다 — 에러로 만들면 진짜 문제가 묻힌다.
         */
        @Test
        @DisplayName("오늘 이미 받은 사유는 조용히 건너뛴다 — 에러가 아니라 gained 0이다")
        void 이미_받았으면_0이다() {
            User user = user(310);
            alreadyGranted(ExpReason.ATTENDANCE);

            ExpResponse response = expService.grantDaily(USER_ID, BASE_DATE,
                    List.of(new ExpGrantCommand(ExpReason.ATTENDANCE, 10)));

            verify(expGrantRepository, never()).save(any());
            assertThat(user.getExp()).isEqualTo(310);
            assertThat(response.gained()).isZero();
            assertThat(response.reasons()).isEmpty();
        }

        /** 수면 업로드에서 증가 보상과 90점 보상이 겹친다 (§10.9). */
        @Test
        @DisplayName("한 요청에 둘이 함께 실린다")
        void 둘이_함께_실린다() {
            User user = user(0);

            ExpResponse response = expService.grantDaily(USER_ID, BASE_DATE, List.of(
                    new ExpGrantCommand(ExpReason.SLEEP_SCORE_IMPROVED, 26),
                    new ExpGrantCommand(ExpReason.SLEEP_SCORE_HIGH, 10)));

            assertThat(user.getExp()).isEqualTo(36);
            assertThat(response.gained()).isEqualTo(36);
            assertThat(response.reasons()).hasSize(2);
        }

        /**
         * <b>행이 생기면 나중에 조건이 성립해도 유니크에 막혀 영영 못 받는다.</b> 연속 1일차와
         * 오르지 않은 수면 점수가 이 경로로 온다.
         */
        @Test
        @DisplayName("양이 0 이하면 이력 행도 만들지 않는다")
        void 양이_0이하면_행을_만들지_않는다() {
            User user = user(50);

            ExpResponse response = expService.grantDaily(USER_ID, BASE_DATE,
                    List.of(new ExpGrantCommand(ExpReason.VERIFICATION_STREAK, 0)));

            verify(expGrantRepository, never()).save(any());
            assertThat(user.getExp()).isEqualTo(50);
            assertThat(response.gained()).isZero();
        }

        /**
         * TODO 적립은 되돌릴 수 있어 이력 행을 만들면 <b>회수할 때 지워야 하고, 그러면 그
         * 테이블은 이력이 아니라 현재 상태의 사본이 된다</b>(erd.md §3.10).
         */
        @Test
        @DisplayName("되돌릴 수 있는 사유는 이력에 남기지 않는다")
        void TODO_사유는_거부한다() {
            user(0);

            assertThatThrownBy(() -> expService.grantDaily(USER_ID, BASE_DATE,
                    List.of(new ExpGrantCommand(ExpReason.TODO_DONE, 5))))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("없는 사용자면 USER_NOT_FOUND다")
        void 없는_사용자는_404다() {
            given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> expService.grantDaily(USER_ID, BASE_DATE,
                    List.of(new ExpGrantCommand(ExpReason.ATTENDANCE, 10))))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("이력 없는 조정 (TODO)")
    class 이력_없는_조정 {

        @Test
        @DisplayName("이력 행을 만들지 않고 exp만 움직인다")
        void 행을_만들지_않는다() {
            User user = user(100);

            ExpResponse response = expService.adjust(USER_ID,
                    List.of(new ExpGrantCommand(ExpReason.TODO_DONE, 5)));

            verify(expGrantRepository, never()).save(any());
            assertThat(user.getExp()).isEqualTo(105);
            assertThat(response.gained()).isEqualTo(5);
        }

        /**
         * <b>적립과 회수가 대칭이라야 무한 적립이 닫힌다.</b> 회수를 빼면 판정이 "이번에 DONE이
         * 됐는가"뿐이라 껐다 켜는 것만으로 계속 붙는다(erd.md §3.1).
         */
        @Test
        @DisplayName("음수는 회수한다 — 적립과 대칭이다")
        void 음수는_회수한다() {
            User user = user(100);

            ExpResponse response = expService.adjust(USER_ID,
                    List.of(new ExpGrantCommand(ExpReason.TODO_DONE, -5)));

            assertThat(user.getExp()).isEqualTo(95);
            assertThat(response.gained()).isEqualTo(-5);
        }

        /** 누적 경험치에 음수는 뜻을 갖지 않는다. 그때 gained는 요청한 양보다 작다. */
        @Test
        @DisplayName("회수는 0에서 멈추고 gained에 실제 증감이 담긴다")
        void 회수는_0에서_멈춘다() {
            User user = user(3);

            ExpResponse response = expService.adjust(USER_ID,
                    List.of(new ExpGrantCommand(ExpReason.TODO_DONE, -5)));

            assertThat(user.getExp()).isZero();
            assertThat(response.gained()).isEqualTo(-3);       // 요청한 −5가 아니다
        }

        @Test
        @DisplayName("되돌릴 수 없는 사유는 이력을 남겨야 하므로 거부한다")
        void 이력_대상_사유는_거부한다() {
            user(0);

            assertThatThrownBy(() -> expService.adjust(USER_ID,
                    List.of(new ExpGrantCommand(ExpReason.ATTENDANCE, 10))))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("레벨 업 판정")
    class 레벨_업_판정 {

        @Test
        @DisplayName("컷오프를 넘긴 요청만 levelUp이 true다")
        void 컷오프를_넘기면_레벨업이다() {
            user(95);

            ExpResponse response = expService.grantDaily(USER_ID, BASE_DATE,
                    List.of(new ExpGrantCommand(ExpReason.ATTENDANCE, 10)));

            assertThat(response.totalExp()).isEqualTo(105);
            assertThat(response.level()).isEqualTo(2);
            assertThat(response.levelUp()).isTrue();
            assertThat(response.nextLevelExp()).isEqualTo(250);
        }

        @Test
        @DisplayName("같은 레벨 안에서 오르면 levelUp은 false다")
        void 같은_레벨이면_false다() {
            user(10);

            ExpResponse response = expService.grantDaily(USER_ID, BASE_DATE,
                    List.of(new ExpGrantCommand(ExpReason.ATTENDANCE, 10)));

            assertThat(response.levelUp()).isFalse();
        }
    }

    @Test
    @DisplayName("current는 적립 없이 현재 상태만 준다")
    void 현재_상태만_준다() {
        user(320);

        ExpResponse response = expService.current(USER_ID);

        verify(expGrantRepository, never()).save(any());
        assertThat(response.gained()).isZero();
        assertThat(response.reasons()).isEmpty();
        assertThat(response.totalExp()).isEqualTo(320);
        assertThat(response.level()).isEqualTo(3);
        assertThat(response.levelUp()).isFalse();
    }

    // ===== 픽스처 =====

    private User user(int exp) {
        User user = User.builder().nickname("테스트유저1").build();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        user.addExp(exp);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        return user;
    }

    private void alreadyGranted(ExpReason reason) {
        given(expGrantRepository.existsByUserIdAndBaseDateAndReason(USER_ID, BASE_DATE, reason))
                .willReturn(true);
    }

}
