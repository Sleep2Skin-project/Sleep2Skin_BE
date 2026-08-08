package com.allday.sleep2skin_be.domain.user;

import com.allday.sleep2skin_be.domain.user.dto.response.OnboardingCompleteResponse;
import com.allday.sleep2skin_be.domain.user.entity.User;
import com.allday.sleep2skin_be.domain.user.repository.UserRepository;
import com.allday.sleep2skin_be.global.exception.BusinessException;
import com.allday.sleep2skin_be.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 상태 관리.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * 온보딩 완료 처리 (ONB-05).
     *
     * <p><b>동의 이력이 있는지 확인하지 않는다.</b> ONB-02 → ONB-05 순서를 지키는 것은
     * 클라이언트 몫이고, 서버가 막으면 시연용 데이터를 파이프라인에 주입하는 경로가 좁아진다.
     *
     * <p><b>멱등하다.</b> 이미 완료된 사용자도 에러가 아니라 정상 응답이다 — 되돌리는 경로가
     * 없으므로 다시 호출해도 상태가 달라질 여지가 없다.
     */
    @Transactional
    public OnboardingCompleteResponse completeOnboarding(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND,
                        "온보딩을 완료할 사용자가 없다 userId=" + userId));

        boolean newlyCompleted = !user.isOnboardingCompleted();
        if (newlyCompleted) {
            user.completeOnboarding();
        }

        return OnboardingCompleteResponse.of(user, newlyCompleted);
    }

}
