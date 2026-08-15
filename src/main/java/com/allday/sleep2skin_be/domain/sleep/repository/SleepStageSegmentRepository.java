package com.allday.sleep2skin_be.domain.sleep.repository;

import com.allday.sleep2skin_be.domain.sleep.entity.SleepStageSegment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 수면 단계 구간 저장·삭제·조회.
 */
public interface SleepStageSegmentRepository extends JpaRepository<SleepStageSegment, Long> {

    /**
     * 타임라인(REP-03) 조회. <b>정렬은 파생 쿼리의 {@code OrderByStartTimeAsc}가 SQL {@code ORDER BY}로
     * 보장한다</b> — 호출부(서비스·DTO)가 다시 정렬할 필요가 없다.
     */
    List<SleepStageSegment> findBySleepSessionIdOrderByStartTimeAsc(Long sleepSessionId);

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

    /**
     * 사용자의 모든 구간 삭제 (MY-04 전체 삭제).
     *
     * <p><b>{@code sleep_session}보다 먼저 지워야 한다.</b> 이 테이블만 진짜 FK를 갖고 있어
     * (엔티티에 {@code @ManyToOne}이 있다) 순서를 바꾸면 제약 위반으로 실패한다.
     *
     * <p>{@code userId} 컬럼이 없으므로 세션을 거쳐 찾는다.
     */
    @Modifying
    @Query("delete from SleepStageSegment s where s.sleepSession.id in"
            + " (select ss.id from SleepSession ss where ss.userId = :userId)")
    void deleteByUserId(@Param("userId") Long userId);

}
