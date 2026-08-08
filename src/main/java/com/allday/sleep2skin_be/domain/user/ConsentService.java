package com.allday.sleep2skin_be.domain.user;

import com.allday.sleep2skin_be.domain.user.dto.response.ConsentAgreeResponse;
import com.allday.sleep2skin_be.domain.user.entity.ConsentHistory;
import com.allday.sleep2skin_be.domain.user.repository.ConsentHistoryRepository;
import com.allday.sleep2skin_be.domain.user.repository.UserRepository;
import com.allday.sleep2skin_be.global.exception.BusinessException;
import com.allday.sleep2skin_be.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 개인정보 수집·이용 동의 (ONB-02).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ConsentService {

    private final UserRepository userRepository;
    private final ConsentHistoryRepository consentHistoryRepository;

    /**
     * 현재 약관 버전에 대한 동의를 기록한다.
     *
     * <p><b>같은 버전에 대해 멱등하다.</b> 이미 동의한 버전이면 새 행을 넣지 않고 기존 이력을
     * 돌려준다. 앱은 재설치·재실행으로 온보딩을 다시 밟으며 같은 호출을 반복하는데, 그때마다
     * append하면 "언제 어느 버전에 동의했는가"에 의미 없는 행이 쌓인다.
     *
     * <p>버전이 다르면 재동의이므로 UPDATE가 아니라 새 행이 된다(erd.md §3.2 append-only).
     * {@link ConsentPolicy#CURRENT_TERMS_VERSION}을 올리는 것만으로 그 동작이 나온다.
     */
    @Transactional
    public ConsentAgreeResponse agree(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND,
                    "동의를 저장할 사용자가 없다 userId=" + userId);
        }

        Optional<ConsentHistory> alreadyAgreed = consentHistoryRepository
                .findFirstByUserIdAndTermsVersionOrderByCreatedAtDesc(
                        userId, ConsentPolicy.CURRENT_TERMS_VERSION);

        if (alreadyAgreed.isPresent()) {
            return ConsentAgreeResponse.of(alreadyAgreed.get(), false);
        }

        ConsentHistory saved = consentHistoryRepository.save(ConsentHistory.builder()
                .userId(userId)
                .termsVersion(ConsentPolicy.CURRENT_TERMS_VERSION)
                // 미동의는 계정 자체가 만들어지지 않는 정책이라 false로 저장되는 경로가 없다 (erd.md §3.2).
                .agreed(true)
                .build());

        return ConsentAgreeResponse.of(saved, true);
    }

}
