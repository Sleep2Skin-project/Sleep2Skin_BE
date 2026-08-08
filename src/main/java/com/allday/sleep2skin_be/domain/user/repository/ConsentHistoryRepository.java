package com.allday.sleep2skin_be.domain.user.repository;

import com.allday.sleep2skin_be.domain.user.entity.ConsentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 동의 이력 저장·조회.
 *
 * <p>append-only라 재동의도 {@code save()}로 새 행을 넣는다. 갱신 메서드가 없는 것이 정상이다.
 */
public interface ConsentHistoryRepository extends JpaRepository<ConsentHistory, Long> {

    /**
     * 이 사용자가 해당 약관 버전에 동의한 가장 최근 이력.
     *
     * <p>같은 버전으로 들어온 재요청을 새 행 없이 되돌려주기 위한 조회다(ONB-02).
     * 인덱스 {@code (user_id, created_at)}를 그대로 타는 형태다.
     */
    Optional<ConsentHistory> findFirstByUserIdAndTermsVersionOrderByCreatedAtDesc(
            Long userId, String termsVersion);

}
