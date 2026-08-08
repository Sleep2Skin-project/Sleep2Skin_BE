package com.allday.sleep2skin_be.domain.skin.repository;

import com.allday.sleep2skin_be.domain.skin.entity.PersonalWeight;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 개인 가중치 조회.
 */
public interface PersonalWeightRepository extends JpaRepository<PersonalWeight, Long> {

    /**
     * 사용자의 가중치 전체(있으면 7행).
     *
     * <p>예보 산출도 학습도 7행을 한꺼번에 쓰므로 단건 조회를 두지 않았다.
     * <b>빈 리스트는 "아직 검증한 적 없음"을 뜻하며</b>, 이때는 {@code ScoringPolicy}의 일반
     * 가중치를 쓴다 — 행의 존재 자체가 개인화 시작 여부다.
     */
    List<PersonalWeight> findByUserId(Long userId);

}
