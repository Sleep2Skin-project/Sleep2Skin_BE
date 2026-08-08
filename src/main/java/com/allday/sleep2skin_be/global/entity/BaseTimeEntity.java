package com.allday.sleep2skin_be.global.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

/**
 * 생성·수정 시각을 자동으로 채우는 베이스 엔티티.
 *
 * <p>수정이 일어나는 엔티티가 상속한다. {@code sleep_session}처럼 재수신 시 갱신되거나,
 * {@code daily_todo}처럼 체크 상태가 바뀌는 것들이다.
 *
 * <p>수정되지 않는 append-only 이력 테이블은 {@link BaseCreatedEntity}를 쓴다.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private OffsetDateTime updatedAt;

}
