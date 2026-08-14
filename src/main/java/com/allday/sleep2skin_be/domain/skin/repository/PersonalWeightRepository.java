package com.allday.sleep2skin_be.domain.skin.repository;

import com.allday.sleep2skin_be.domain.skin.entity.PersonalWeight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;

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

    /**
     * 사용자의 개인 가중치 전량 삭제 (MY-04 전체 삭제).
     *
     * <p><b>DB에 `users` 외래키가 없어 CASCADE가 걸리지 않는다</b> — PersonalWeight.userId 는 연관관계가
     * 아니라 단순 컬럼이라 Hibernate 가 제약을 만들지 않았다. 그래서 삭제를 손으로 지운다.
     *
     * <p>파생 {@code deleteBy}가 아니라 벌크 삭제인 이유는 전부 엔티티로 읽어 하나씩 지우지
     * 않기 위해서다.
     */
    @Modifying
    @Query("delete from PersonalWeight e where e.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);

}
