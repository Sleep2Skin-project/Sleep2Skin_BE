package com.allday.sleep2skin_be.domain.sleep.repository;

import com.allday.sleep2skin_be.domain.sleep.entity.SleepStageSegment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 수면 단계 구간 저장·삭제.
 *
 * <p>타임라인(REP-03) 조회 메서드는 그 기능 구현 시 추가한다.
 */
public interface SleepStageSegmentRepository extends JpaRepository<SleepStageSegment, Long> {

    /**
     * 세션의 구간 전량 삭제. <b>세션이 갱신되면 구간은 부분 수정이 아니라 전량 교체된다</b>(erd.md §3.4).
     *
     * <p>다시 정규화하면 경계가 어디서든 달라질 수 있어 옛 구간과 새 구간을 짝지을 방법이 없다.
     * 남겨두면 타임라인에 사라진 구간이 계속 그려진다.
     *
     * <p>파생 {@code deleteBy}가 아니라 벌크 삭제인 이유는 한 세션이 구간 수백 개를 갖기 때문이다 —
     * 파생 삭제는 전부 엔티티로 읽어 하나씩 지운다.
     */
    @Modifying
    @Query("delete from SleepStageSegment s where s.sleepSession.id = :sleepSessionId")
    void deleteBySleepSessionId(@Param("sleepSessionId") Long sleepSessionId);

}
