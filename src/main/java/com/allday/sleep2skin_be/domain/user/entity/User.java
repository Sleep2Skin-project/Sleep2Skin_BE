package com.allday.sleep2skin_be.domain.user.entity;

import com.allday.sleep2skin_be.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자.
 *
 * <p>인증이 없으므로 {@code email}·{@code password}·{@code provider_id}가 없다.
 * 테스트 유저를 DB에 직접 주입하고, 모든 요청은 {@code X-User-Id} 헤더로 사용자를 식별한다.
 *
 * <p>{@code time_zone}을 두지 않는다 — 날짜가 필요한 API가 전부 {@code baseDate}를
 * 파라미터로 받는다(erd.md §3.1). 서버가 "오늘"을 정할 필요가 없다.
 *
 * <p>{@code deleted_at}도 없다. MY-04는 복구 불가 영구 삭제이며 soft delete가 아니다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users")
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String nickname;

    /** ONB-05. 생성 시점에는 항상 false이며 {@link #completeOnboarding()}으로만 바뀐다. */
    @Column(nullable = false)
    private boolean onboardingCompleted;

    @Builder
    private User(String nickname) {
        this.nickname = nickname;
        this.onboardingCompleted = false;
    }

    /** 온보딩 완료 처리 (ONB-05). 되돌리는 경로는 없다. */
    public void completeOnboarding() {
        this.onboardingCompleted = true;
    }

}
