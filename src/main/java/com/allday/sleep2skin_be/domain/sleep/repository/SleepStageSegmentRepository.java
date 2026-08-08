package com.allday.sleep2skin_be.domain.sleep.repository;

import com.allday.sleep2skin_be.domain.sleep.entity.SleepStageSegment;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 수면 단계 구간 저장·조회.
 *
 * <p>유니크 제약이 없는 테이블이라 조회 메서드를 두지 않았다. 세션 갱신 시의 전량 삭제와
 * 타임라인(REP-03) 조회는 각각 해당 기능 구현 시 추가한다.
 */
public interface SleepStageSegmentRepository extends JpaRepository<SleepStageSegment, Long> {
}
