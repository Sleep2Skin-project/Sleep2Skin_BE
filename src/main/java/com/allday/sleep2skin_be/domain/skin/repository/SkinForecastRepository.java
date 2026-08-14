package com.allday.sleep2skin_be.domain.skin.repository;

import com.allday.sleep2skin_be.domain.skin.entity.SkinForecast;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 피부 예보 조회.
 */
public interface SkinForecastRepository extends JpaRepository<SkinForecast, Long> {

    /**
     * 유니크 키 {@code (user_id, base_date)} 조회.
     *
     * <p>비어 있는 것은 에러가 아니라 <b>정상적인 빈 상태</b>다 — 조회 API(HOME-03)는
     * 200 + 빈 상태로 응답한다. 반면 동작 API인 셀피 검증에서 비면 대조 기준이 없다는 뜻이므로
     * {@code 404 SKIN_FORECAST_NOT_FOUND}다.
     */
    Optional<SkinForecast> findByUserIdAndBaseDate(Long userId, LocalDate baseDate);

    /**
     * 사용자의 예보 전량 삭제 (MY-04 전체 삭제).
     *
     * <p><b>DB에 `users` 외래키가 없어 CASCADE가 걸리지 않는다</b> — SkinForecast.userId 는 연관관계가
     * 아니라 단순 컬럼이라 Hibernate 가 제약을 만들지 않았다. 그래서 삭제를 손으로 지운다.
     *
     * <p>파생 {@code deleteBy}가 아니라 벌크 삭제인 이유는 전부 엔티티로 읽어 하나씩 지우지
     * 않기 위해서다.
     */
    @Modifying
    @Query("delete from SkinForecast e where e.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);

}
