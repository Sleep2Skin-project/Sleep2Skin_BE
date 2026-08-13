package com.allday.sleep2skin_be.domain.todo.repository;

import com.allday.sleep2skin_be.domain.todo.entity.ActionCategory;
import com.allday.sleep2skin_be.domain.todo.entity.ActionMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 액션 마스터 조회.
 *
 * <p><b>카테고리별로 활성 행 전체를 가져온 뒤, threshold 비교·가중·정렬·절단은
 * {@code TodoScoringPolicy}(Java)에서 한다.</b> 카테고리당 12행뿐이라(지표 3 × 4개) 전체를
 * 읽어도 비용이 크지 않고, 매칭 조건을 SQL로 표현하면 지표가 늘 때마다 쿼리를 고쳐야 한다.
 */
public interface ActionMasterRepository extends JpaRepository<ActionMaster, Long> {

    List<ActionMaster> findByCategoryAndActiveTrue(ActionCategory category);

}