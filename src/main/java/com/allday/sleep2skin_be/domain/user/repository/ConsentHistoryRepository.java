package com.allday.sleep2skin_be.domain.user.repository;

import com.allday.sleep2skin_be.domain.user.entity.ConsentHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;

import java.util.Optional;

/**
 * 동의 이력 저장·조회.
 *
 * <p>append-only라 재동의도 {@code save()}로 새 행을 넣는다. 갱신 메서드가 없는 것이 정상이다.
 */
public interface ConsentHistoryRepository extends JpaRepository<ConsentHistory, Long> {

    /**
     * 이 사용자가 해당 약관 버전에 동의한 가장 최근 이력.
     *
     * <p>같은 버전으로 들어온 재요청을 새 행 없이 되돌려주기 위한 조회다(ONB-02).
     * 인덱스 {@code (user_id, created_at)}를 그대로 타는 형태다.
     */
    Optional<ConsentHistory> findFirstByUserIdAndTermsVersionOrderByCreatedAtDesc(
            Long userId, String termsVersion);

    /**
     * 이 사용자의 가장 최근 동의 이력 (버전 무관).
     *
     * <p>MY-01 프로필이 <b>"동의했는가"와 "어느 버전에 동의했는가"를 한 번에</b> 알아내는
     * 조회다. 두 질문에 각각 쿼리를 날리지 않아도 되는 이유는 <b>약관 버전이 앞으로만
     * 올라가기 때문</b>이다 — append-only라 가장 최근 행이 곧 가장 최근에 동의한 버전이고,
     * 그 값이 {@link com.allday.sleep2skin_be.domain.user.ConsentPolicy#CURRENT_TERMS_VERSION}과
     * 같은지만 보면 재동의 필요 여부가 나온다.
     *
     * <p>⚠️ 상수를 <b>되돌리면</b>(2.0 → 1.0) 이 전제가 깨진다. 그럴 일이 없도록 버전은 올리기만 한다.
     */
    Optional<ConsentHistory> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * 사용자의 동의 이력 전량 삭제 (MY-04 전체 삭제).
     *
     * <p><b>DB에 `users` 외래키가 없어 CASCADE가 걸리지 않는다</b> — ConsentHistory.userId 는 연관관계가
     * 아니라 단순 컬럼이라 Hibernate 가 제약을 만들지 않았다. 그래서 삭제를 손으로 지운다.
     *
     * <p>파생 {@code deleteBy}가 아니라 벌크 삭제인 이유는 전부 엔티티로 읽어 하나씩 지우지
     * 않기 위해서다.
     */
    @Modifying
    @Query("delete from ConsentHistory e where e.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);

}
