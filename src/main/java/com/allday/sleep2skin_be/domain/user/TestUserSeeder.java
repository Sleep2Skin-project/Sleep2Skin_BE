package com.allday.sleep2skin_be.domain.user;

import com.allday.sleep2skin_be.domain.user.entity.User;
import com.allday.sleep2skin_be.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 테스트 유저 시딩.
 *
 * <p>인증이 없으므로 사용자를 만드는 API가 없다. 모든 요청은 {@code X-User-Id} 헤더로 사용자를
 * 식별하는데, 그 헤더에 넣을 값이 DB에 먼저 있어야 한다(architecture.md §6).
 *
 * <p><b>{@code data.sql}이 아니라 {@link CommandLineRunner}인 이유</b> — 엔티티를 파괴적으로
 * 바꾸면 DB를 지우고 다시 만들어야 하고({@code docker compose down -v}) 그때마다 시딩도 다시
 * 돌아야 한다(workflow.md §1). 앱이 뜰 때 자동으로 돌면 팀원이 실행을 까먹을 수가 없다.
 * 엔티티 빌더를 쓰므로 컬럼이 바뀌면 런타임이 아니라 컴파일에서 깨지는 것도 이유다.
 *
 * <p><b>운영에서도 돈다.</b> {@code application.yml}에 운영용 프로파일이 따로 없고 세 환경의
 * 차이는 {@code DB_HOST} 값뿐이다(workflow.md §1). 인증이 없어 운영도 사실상 데모 환경이며,
 * 거기에 테스트 유저가 없으면 시연할 수단이 없다. 아래 멱등 검사가 재배포 시 중복 생성을 막는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TestUserSeeder implements CommandLineRunner {

    private static final String COMPLETED_USER_NICKNAME = "테스트유저1";
    private static final String NEW_USER_NICKNAME = "테스트유저2";

    private final UserRepository userRepository;

    /**
     * <b>멱등하다.</b> 사용자가 한 명이라도 있으면 아무것도 하지 않는다 — 사용자를 만드는 API가
     * 없으므로 "행이 존재한다"는 곧 "시딩이 끝났다"는 뜻이다.
     */
    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("테스트 유저가 이미 있어 시딩을 건너뛴다");
            return;
        }

        // 온보딩을 마친 사용자. 대부분의 API는 이쪽으로 테스트한다.
        User completed = User.builder()
                .nickname(COMPLETED_USER_NICKNAME)
                .build();
        completed.completeOnboarding();

        // 신규 사용자. 온보딩 플로우(ONB-02·05)와 빈 상태 응답을 확인하는 자리다.
        // 빈 상태는 이 서비스의 정상 흐름이라(조회 API는 200 + 상태 필드) 전용 사용자가 있는 편이 낫다.
        User created = User.builder()
                .nickname(NEW_USER_NICKNAME)
                .build();

        List<User> seeded = userRepository.saveAll(List.of(completed, created));

        // ID를 직접 지정하지 않고 로그로 알린다. AUTO_INCREMENT라 DB를 비운 이력에 따라
        // 1·2가 아닐 수 있고, X-User-Id 헤더에 넣을 값은 실제로 저장된 값이어야 한다.
        log.info("테스트 유저 시딩 완료 — X-User-Id: {}(온보딩 완료) · {}(신규)",
                seeded.get(0).getId(), seeded.get(1).getId());
    }

}
