package com.allday.sleep2skin_be.domain.user;

import com.allday.sleep2skin_be.domain.user.entity.User;
import com.allday.sleep2skin_be.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 시더가 부팅 시 실제로 돌고, 다시 돌아도 사용자가 늘지 않는지 확인한다.
 *
 * <p>앱이 뜰 때마다 실행되므로 멱등성이 깨지면 재기동·재배포마다 사용자가 늘어난다.
 *
 * <p>{@code @DataJpaTest}가 아니라 {@code @SpringBootTest}인 이유는 이 프로젝트가
 * {@code spring-boot-starter-webmvc-test}만 쓰기 때문이다. 대신 컨텍스트가 뜨는 것 자체가
 * {@link org.springframework.boot.CommandLineRunner} 실행을 포함해 <b>부팅 시 시딩까지 검증된다.</b>
 */
@SpringBootTest
@ActiveProfiles("test")
class TestUserSeederTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestUserSeeder testUserSeeder;

    @Test
    @DisplayName("부팅 시 온보딩 완료 유저와 신규 유저가 한 명씩 생긴다")
    void 부팅하면_유저_두_명이_생긴다() {
        List<User> users = userRepository.findAll();

        assertThat(users).hasSize(2);
        assertThat(users).extracting(User::isOnboardingCompleted)
                .containsExactly(true, false);
    }

    @Test
    @DisplayName("다시 실행해도 유저가 늘지 않는다")
    void 재실행해도_중복_생성되지_않는다() {
        testUserSeeder.run();

        assertThat(userRepository.count()).isEqualTo(2);
    }

}
