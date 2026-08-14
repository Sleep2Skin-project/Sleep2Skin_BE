package com.allday.sleep2skin_be.domain.todo.repository;

import com.allday.sleep2skin_be.domain.todo.entity.DailyTodo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;

import java.time.LocalDate;
import java.util.List;

/**
 * 일자별 TODO 조회.
 */
public interface DailyTodoRepository extends JpaRepository<DailyTodo, Long> {

    /**
     * 그날의 TODO 전체.
     *
     * <p><b>빈 리스트는 "아직 생성 전"을 뜻한다</b> — 이때 추천 엔진을 돌려 행을 만들고,
     * 있으면 그대로 반환해 목록을 고정한다.
     */
    List<DailyTodo> findByUserIdAndBaseDate(Long userId, LocalDate baseDate);

    /**
     * 사용자의 TODO 전량 삭제 (MY-04 전체 삭제).
     *
     * <p><b>DB에 `users` 외래키가 없어 CASCADE가 걸리지 않는다</b> — DailyTodo.userId 는 연관관계가
     * 아니라 단순 컬럼이라 Hibernate 가 제약을 만들지 않았다. 그래서 삭제를 손으로 지운다.
     *
     * <p>파생 {@code deleteBy}가 아니라 벌크 삭제인 이유는 전부 엔티티로 읽어 하나씩 지우지
     * 않기 위해서다.
     */
    @Modifying
    @Query("delete from DailyTodo e where e.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);

}
