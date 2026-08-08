package com.allday.sleep2skin_be.domain.todo.repository;

import com.allday.sleep2skin_be.domain.todo.entity.DailyTodo;
import org.springframework.data.jpa.repository.JpaRepository;

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

}
