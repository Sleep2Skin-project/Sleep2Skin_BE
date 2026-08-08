package com.allday.sleep2skin_be.domain.skin.repository;

import com.allday.sleep2skin_be.domain.skin.entity.SkinForecast;
import org.springframework.data.jpa.repository.JpaRepository;

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

}
