package com.allday.sleep2skin_be.global.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

/**
 * 생성 시각만 갖는 베이스 엔티티.
 *
 * <p>append-only 이력 테이블이 상속한다. {@code consent_history}가 그 예로,
 * 약관이 개정되면 기존 행을 수정하지 않고 새 행을 넣는다. 이런 테이블에서
 * {@code updatedAt}은 항상 {@code createdAt}과 같아 있으나 마나 한 컬럼이 된다.
 *
 * <p>이 경우 {@code createdAt}이 곧 도메인상의 시각이다 — 동의 이력이라면 동의한 시각.
 * 같은 값을 가진 컬럼을 두 개 두면 나중에 둘이 어긋날 때 어느 쪽이 맞는지 알 수 없다.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseCreatedEntity {

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

}
