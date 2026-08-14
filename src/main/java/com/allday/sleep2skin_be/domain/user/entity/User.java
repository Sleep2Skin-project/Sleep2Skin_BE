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

    /** 캐릭터 경험치 누적 총합. TODO(DO) 완료 시 +10. */
    @Column(nullable = false)
    private int exp;

    @Builder
    private User(String nickname) {
        this.nickname = nickname;
        this.onboardingCompleted = false;
        this.exp = 0;
    }

    /** 온보딩 완료 처리 (ONB-05). 되돌리는 경로는 없다. */
    public void completeOnboarding() {
        this.onboardingCompleted = true;
    }

    /** exp 적립 (TODO DO 완료 시). 음수 방어만 하고 상한은 두지 않는다. */
    public void addExp(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("exp는 음수가 될 수 없습니다: " + amount);
        }
        this.exp += amount;
    }

    /**
     * exp 회수 (TODO DO 되돌리기).
     *
     * <p><b>적립과 대칭이어야 한다.</b> 회수하지 않으면 {@code DONE → PENDING → DONE}을 반복하는
     * 것만으로 exp가 계속 붙는다 — 판정이 "이번에 DONE이 됐는가"뿐이라 중복 호출만 막히고
     * 껐다 켜는 것은 막히지 않는다.
     *
     * <p><b>0 밑으로 내려가지 않는다.</b> 데이터를 손으로 건드려 {@code exp}가 실제 완료 개수와
     * 어긋난 상태에서 되돌리면 음수가 될 수 있는데, 누적 경험치에 음수는 뜻을 갖지 않는다.
     * 실제로 얼마나 줄었는지는 호출부가 전후 값을 비교해 안다.
     */
    public void subtractExp(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("회수량은 음수가 될 수 없습니다: " + amount);
        }
        this.exp = Math.max(0, this.exp - amount);
    }

}