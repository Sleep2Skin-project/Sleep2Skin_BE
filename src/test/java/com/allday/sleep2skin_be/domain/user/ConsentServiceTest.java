package com.allday.sleep2skin_be.domain.user;

import com.allday.sleep2skin_be.domain.user.dto.response.ConsentAgreeResponse;
import com.allday.sleep2skin_be.domain.user.entity.ConsentHistory;
import com.allday.sleep2skin_be.domain.user.repository.ConsentHistoryRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ConsentServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ConsentHistoryRepository consentHistoryRepository;

    @InjectMocks
    private ConsentService consentService;

    @Test
    @DisplayName("첫 동의는 현재 약관 버전으로 새 이력을 저장한다")
    void 첫_동의는_새_이력을_저장한다() {
        given(userRepository.existsById(USER_ID)).willReturn(true);
        given(consentHistoryRepository.findFirstByUserIdAndTermsVersionOrderByCreatedAtDesc(
                USER_ID, ConsentPolicy.CURRENT_TERMS_VERSION)).willReturn(Optional.empty());
        given(consentHistoryRepository.save(any(ConsentHistory.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ConsentAgreeResponse response = consentService.agree(USER_ID);

        assertThat(response.newlyAgreed()).isTrue();
        assertThat(response.termsVersion()).isEqualTo(ConsentPolicy.CURRENT_TERMS_VERSION);
        verify(consentHistoryRepository).save(any(ConsentHistory.class));
    }

    @Test
    @DisplayName("같은 버전에 이미 동의했으면 새 이력을 만들지 않고 기존 이력을 돌려준다")
    void 같은_버전_재요청은_이력을_늘리지_않는다() {
        ConsentHistory existing = ConsentHistory.builder()
                .userId(USER_ID)
                .termsVersion(ConsentPolicy.CURRENT_TERMS_VERSION)
                .agreed(true)
                .build();
        given(userRepository.existsById(USER_ID)).willReturn(true);
        given(consentHistoryRepository.findFirstByUserIdAndTermsVersionOrderByCreatedAtDesc(
                USER_ID, ConsentPolicy.CURRENT_TERMS_VERSION)).willReturn(Optional.of(existing));

        ConsentAgreeResponse response = consentService.agree(USER_ID);

        assertThat(response.newlyAgreed()).isFalse();
        assertThat(response.termsVersion()).isEqualTo(ConsentPolicy.CURRENT_TERMS_VERSION);
        verify(consentHistoryRepository, never()).save(any(ConsentHistory.class));
    }

    @Test
    @DisplayName("존재하지 않는 사용자면 USER_NOT_FOUND이고 이력을 조회조차 하지 않는다")
    void 없는_사용자는_USER_NOT_FOUND다() {
        given(userRepository.existsById(USER_ID)).willReturn(false);

        assertThatThrownBy(() -> consentService.agree(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(consentHistoryRepository, never())
                .findFirstByUserIdAndTermsVersionOrderByCreatedAtDesc(anyLong(), anyString());
    }

}
