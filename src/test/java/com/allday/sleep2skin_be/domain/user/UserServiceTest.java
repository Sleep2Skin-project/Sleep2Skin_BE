package com.allday.sleep2skin_be.domain.user;

import com.allday.sleep2skin_be.domain.user.dto.response.OnboardingCompleteResponse;
import com.allday.sleep2skin_be.domain.user.entity.User;
import com.allday.sleep2skin_be.domain.user.repository.UserRepository;
import com.allday.sleep2skin_be.global.exception.BusinessException;
import com.allday.sleep2skin_be.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("온보딩 미완료 사용자를 완료 상태로 바꾼다")
    void 온보딩을_완료_상태로_바꾼다() {
        User user = User.builder().nickname("테스트유저2").build();
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

        OnboardingCompleteResponse response = userService.completeOnboarding(USER_ID);

        assertThat(user.isOnboardingCompleted()).isTrue();
        assertThat(response.onboardingCompleted()).isTrue();
        assertThat(response.newlyCompleted()).isTrue();
    }

    @Test
    @DisplayName("이미 완료된 사용자는 에러가 아니라 newlyCompleted=false로 응답한다")
    void 이미_완료된_사용자도_정상_응답이다() {
        User user = User.builder().nickname("테스트유저1").build();
        user.completeOnboarding();
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

        OnboardingCompleteResponse response = userService.completeOnboarding(USER_ID);

        assertThat(response.onboardingCompleted()).isTrue();
        assertThat(response.newlyCompleted()).isFalse();
    }

    @Test
    @DisplayName("존재하지 않는 사용자면 USER_NOT_FOUND다")
    void 없는_사용자는_USER_NOT_FOUND다() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.completeOnboarding(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

}
