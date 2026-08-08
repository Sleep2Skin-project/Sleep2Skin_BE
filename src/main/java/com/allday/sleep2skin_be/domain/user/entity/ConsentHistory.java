package com.allday.sleep2skin_be.domain.user.entity;

import com.allday.sleep2skin_be.global.entity.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 개인정보 동의 이력 (ONB-02).
 *
 * <p><b>append-only다.</b> 약관이 개정되면 기존 행을 수정하지 않고 새 행을 넣는다.
 * 그래야 "언제 어느 버전에 동의했는가"가 남는다 — 그래서 {@link BaseCreatedEntity}를 상속하고
 * {@code updated_at}이 없다.
 *
 * <p>{@code agreed_at}을 따로 두지 않았다. 행이 생기는 순간이 곧 동의하는 순간이라
 * {@code created_at}과 값이 항상 같다. 같은 값을 가진 컬럼이 둘이면 나중에 어긋날 때
 * 어느 쪽이 맞는지 알 수 없다.
 *
 * <p>{@code consent_type}도 없다. 동의 항목을 필수 하나(개인정보 수집·이용)로 고정했다.
 * 값이 하나뿐인 구분 컬럼은 분기도 조회 조건도 만들지 못한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "consent_history",
        indexes = @Index(name = "idx_consent_history_user_created", columnList = "user_id, created_at")
)
public class ConsentHistory extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** 예: {@code "1.0"}. 재동의 판정을 {@code WHERE terms_version <> ?} 한 줄로 끝내기 위한 컬럼이다. */
    @Column(nullable = false, length = 20)
    private String termsVersion;

    /**
     * 현재 정책("미동의 = 계정 미생성")상 항상 {@code true}다.
     * 그럼에도 남겨둔 것은 prd.md §7 P1(스토어 심사 리스크)이 검토 중이기 때문이다.
     */
    @Column(nullable = false)
    private boolean agreed;

    @Builder
    private ConsentHistory(Long userId, String termsVersion, boolean agreed) {
        this.userId = userId;
        this.termsVersion = termsVersion;
        this.agreed = agreed;
    }

}
