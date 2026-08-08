package com.allday.sleep2skin_be.domain.user.repository;

import com.allday.sleep2skin_be.domain.user.entity.ConsentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 동의 이력 저장·조회.
 *
 * <p>append-only라 재동의도 {@code save()}로 새 행을 넣는다. 갱신 메서드가 없는 것이 정상이다.
 *
 * <p>"이 사용자의 가장 최근 동의는?"이 주 조회 패턴이고 인덱스도 그 형태({@code user_id, created_at})지만,
 * 조회 메서드는 ONB-02 구현 시 실제 반환 타입이 정해질 때 추가한다.
 */
public interface ConsentHistoryRepository extends JpaRepository<ConsentHistory, Long> {
}
