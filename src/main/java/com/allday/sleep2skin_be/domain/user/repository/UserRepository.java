package com.allday.sleep2skin_be.domain.user.repository;

import com.allday.sleep2skin_be.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 사용자 조회.
 *
 * <p>인증이 없어 {@code X-User-Id} 헤더의 PK로만 찾으므로 별도 조회 메서드가 없다.
 */
public interface UserRepository extends JpaRepository<User, Long> {
}
