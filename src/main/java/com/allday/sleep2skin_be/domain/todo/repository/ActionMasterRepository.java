package com.allday.sleep2skin_be.domain.todo.repository;

import com.allday.sleep2skin_be.domain.todo.entity.ActionMaster;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 액션 마스터 조회.
 *
 * <p>유니크 제약이 없는 콘텐츠 테이블이라 조회 메서드를 두지 않았다.
 * TODO-02의 후보 추출 쿼리({@code target_metric} × {@code threshold} 매칭)는 추천 엔진 구현 시
 * 추가한다 — <b>추출까지만 SQL로 하고 가중·정렬·절단은 Java에서 한다.</b>
 */
public interface ActionMasterRepository extends JpaRepository<ActionMaster, Long> {
}
