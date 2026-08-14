package com.allday.sleep2skin_be.domain.game.repository;

import com.allday.sleep2skin_be.domain.game.entity.ExpGrant;
import com.allday.sleep2skin_be.domain.game.entity.ExpReason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

/**
 * 하루 1회 보상 지급 이력 조회.
 */
public interface ExpGrantRepository extends JpaRepository<ExpGrant, Long> {

    /**
     * 오늘 이 사유로 이미 받았는가. 유니크 키 {@code (user_id, base_date, reason)} 조회다.
     *
     * <p><b>이 검사가 하루 1회를 보장하는 것이 아니다.</b> 보장하는 것은 DB 유니크이고, 여기는
     * <b>정상 흐름(재호출)에서 200을 내주기 위한 빠른 경로</b>다 — 앱은 시작할 때마다 호출하므로
     * 하루에 다섯 번 켜면 네 번은 재호출이고, 그걸 예외로 처리하면 진짜 문제가 묻힌다.
     *
     * <p>검사와 저장 사이에 동시 요청이 끼면 두 번째는 유니크 위반으로 실패한다. <b>이중 지급이
     * 일어나지 않는다는 것이 이 설계가 지키는 것이고</b>, 실패한 요청은 앱이 다시 부르면
     * {@code gained: 0}으로 정상 응답한다.
     */
    boolean existsByUserIdAndBaseDateAndReason(Long userId, LocalDate baseDate, ExpReason reason);

    /**
     * 사용자의 적립 이력 전량 삭제 (MY-04 전체 삭제).
     *
     * <p><b>DB에 {@code users} 외래키가 없어 CASCADE가 걸리지 않는다</b> — {@code ExpGrant.userId}는
     * 연관관계가 아니라 단순 컬럼이라 Hibernate가 제약을 만들지 않았다. 그래서 손으로 지운다.
     *
     * <p>⚠️ <b>빠뜨리면 조용히 틀린다.</b> 고아 행이 남은 채 같은 {@code userId}로 새 사용자가
     * 생기면 그 사용자는 <b>오늘 출석 보상을 받을 수 없다</b> — 유니크에 남의 행이 이미 앉아
     * 있다. 에러도 로그도 남지 않고 exp만 붙지 않는다(erd.md §5).
     */
    @Modifying
    @Query("delete from ExpGrant e where e.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);

}
