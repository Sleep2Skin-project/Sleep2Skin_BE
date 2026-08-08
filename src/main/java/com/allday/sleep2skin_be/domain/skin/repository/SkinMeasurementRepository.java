package com.allday.sleep2skin_be.domain.skin.repository;

import com.allday.sleep2skin_be.domain.skin.entity.SkinMeasurement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 셀피 실측 조회.
 */
public interface SkinMeasurementRepository extends JpaRepository<SkinMeasurement, Long> {

    /**
     * 유니크 키 {@code (user_id, base_date)} 조회. 하루 1회 검증을 판정한다.
     *
     * <p>수면 재수신 시 <b>"그날 검증을 마쳤는가"의 판정에도 이 메서드를 쓴다</b> — 행이 있으면
     * 예보를 갱신하지 않는다. 검증을 마친 날의 예보가 바뀌면 이미 끝난 대조의 기준이 사후에
     * 달라져 적중률이 훼손되고 개인 가중치가 중복 학습된다.
     */
    Optional<SkinMeasurement> findByUserIdAndBaseDate(Long userId, LocalDate baseDate);

}
