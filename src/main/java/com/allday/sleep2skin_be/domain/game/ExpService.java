package com.allday.sleep2skin_be.domain.game;

import com.allday.sleep2skin_be.domain.game.dto.ExpGrantCommand;
import com.allday.sleep2skin_be.domain.game.dto.response.ExpResponse;
import com.allday.sleep2skin_be.domain.game.dto.response.ExpResponse.ExpReasonResponse;
import com.allday.sleep2skin_be.domain.game.entity.ExpGrant;
import com.allday.sleep2skin_be.domain.game.entity.ExpReason;
import com.allday.sleep2skin_be.domain.game.repository.ExpGrantRepository;
import com.allday.sleep2skin_be.domain.user.entity.User;
import com.allday.sleep2skin_be.domain.user.repository.UserRepository;
import com.allday.sleep2skin_be.global.exception.BusinessException;
import com.allday.sleep2skin_be.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * exp 적립·회수의 <b>유일한 창구</b> (HOME-04 — prd.md §10.9).
 *
 * <h2>왜 한 곳으로 모으나</h2>
 *
 * <p>적립 트리거가 {@code user}·{@code sleep}·{@code skin}·{@code todo} 네 도메인에 흩어져 있다
 * (architecture.md §2). 하루 1회 판정과 {@code users.exp} 갱신·0 하한이 <b>한 곳에서만</b>
 * 일어나야 한다 — 도메인마다 따로 구현하면 <b>어느 하나가 회수를 빼먹어도 컴파일도 테스트도
 * 통과한다.</b>
 *
 * <h2>중복 적립을 막는 방법이 두 갈래다</h2>
 *
 * <table border="1">
 *   <caption>"하루 1회"의 보장 장치</caption>
 *   <tr><th>메서드</th><th>보장 장치</th><th>해당 사유</th></tr>
 *   <tr><td>{@link #grantDaily}</td><td>{@code exp_grant} 유니크</td>
 *       <td>{@code ATTENDANCE} · {@code VERIFICATION_STREAK} · {@code SLEEP_SCORE_*}</td></tr>
 *   <tr><td>{@link #adjust}</td><td>{@code daily_todo.status} — 호출부가 판정한다</td>
 *       <td>{@code TODO_DONE} · {@code TODO_ALL_DONE}</td></tr>
 * </table>
 *
 * <p><b>한쪽으로 통일하려다 보면 다른 쪽이 깨진다.</b> TODO 적립은 되돌릴 수 있어 이력 행을
 * 만들면 회수할 때 지워야 하고, 그러면 그 테이블은 이력이 아니라 현재 상태의 사본이 된다.
 * 반대로 나머지 넷은 상태로 환원되지 않아 이력 없이는 앱을 켤 때마다 다시 지급된다.
 *
 * <p><b>트랜잭션 경계를 호출부와 공유한다.</b> {@code REQUIRED}라 수면 업로드·셀피 검증의
 * 트랜잭션 안에서 실행된다 — 저장이 롤백되면 exp도 함께 롤백돼야 하기 때문이다.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ExpService {

    private final UserRepository userRepository;
    private final ExpGrantRepository expGrantRepository;

    /**
     * 하루 1회 보상을 적립하고 이력 행을 남긴다.
     *
     * <p><b>이미 오늘 받은 사유는 조용히 건너뛴다.</b> {@code gained: 0} · {@code reasons: []}로
     * 나가며 <b>에러가 아니다</b> — 앱은 시작할 때마다 호출하므로 하루에 다섯 번 켜면 네 번은
     * 재호출이고, 정상 흐름을 에러로 만들면 진짜 문제가 묻힌다(api.md §2.1).
     *
     * <p><b>{@code amount}가 0 이하인 명령도 건너뛴다.</b> 조건이 성립하지 않은 보상(수면 점수가
     * 오르지 않은 날, 연속 1일차)을 호출부가 걸러내지 않고 넘겨도 이력 행이 생기지 않는다 —
     * 행이 생기면 <b>나중에 조건이 성립해도 유니크에 막혀 영영 못 받는다.</b>
     *
     * <p>⚠️ <b>되돌릴 수 있는 사유({@code TODO_*})는 여기로 오면 안 된다.</b> 타입이 막아주지
     * 않으므로 {@link ExpReason#recordable()}로 걸러 실패시킨다.
     *
     * @param baseDate 클라이언트가 보낸 기준일. <b>서버 시각으로 대신하지 않는다</b> —
     *                 한국 시간 오전 9시 이전에 하루 밀려, 그날 다시 호출할 때 오늘 몫이 또 나간다
     */
    @Transactional
    public ExpResponse grantDaily(Long userId, LocalDate baseDate, List<ExpGrantCommand> commands) {
        User user = findUser(userId);
        int before = user.getExp();

        List<ExpReasonResponse> granted = new ArrayList<>();
        for (ExpGrantCommand command : commands) {
            if (!command.reason().recordable()) {
                throw new IllegalArgumentException(
                        "되돌릴 수 있는 사유는 이력에 남기지 않는다: " + command.reason());
            }
            if (command.amount() <= 0) {
                continue;                       // 조건이 성립하지 않은 보상 — 행을 만들지 않는다
            }
            if (expGrantRepository.existsByUserIdAndBaseDateAndReason(
                    userId, baseDate, command.reason())) {
                log.debug("이미 적립된 사유 userId={} baseDate={} reason={}",
                        userId, baseDate, command.reason());
                continue;
            }

            expGrantRepository.save(ExpGrant.builder()
                    .userId(userId)
                    .baseDate(baseDate)
                    .reason(command.reason())
                    .amount(command.amount())
                    .build());
            user.addExp(command.amount());
            granted.add(new ExpReasonResponse(command.reason(), command.amount()));

            log.info("exp 적립 userId={} baseDate={} reason={} amount={}",
                    userId, baseDate, command.reason(), command.amount());
        }

        return ExpResponse.of(before, user.getExp(), granted);
    }

    /**
     * 이력 행 없이 {@code users.exp}만 조정한다. <b>TODO 적립·회수의 자리다.</b>
     *
     * <p>하루 1회 판정을 여기가 하지 않는다 — {@code daily_todo.status}가 이미 지급 여부를 말하고
     * 있어서, <b>호출부가 상태 전이를 보고 무엇을 넘길지 정한다.</b>
     *
     * <p>⚠️ <b>적립과 회수는 대칭이라야 한다</b>(erd.md §3.1). {@code PENDING → DONE}에
     * {@code +5}면 {@code DONE → PENDING}에 {@code −5}다. 회수를 빼면 판정이 "이번에
     * {@code DONE}이 됐는가"뿐이라 <b>껐다 켜는 것만으로 무한 적립이 된다</b> — 중복 호출
     * ({@code DONE → DONE})만 막히고 토글은 막히지 않는다.
     *
     * <p><b>회수는 0에서 멈춘다.</b> 누적 경험치에 음수는 뜻을 갖지 않으므로, 그 경우
     * {@code gained}에 요청한 양보다 작은 실제 증감이 담긴다.
     */
    @Transactional
    public ExpResponse adjust(Long userId, List<ExpGrantCommand> commands) {
        User user = findUser(userId);
        int before = user.getExp();

        List<ExpReasonResponse> applied = new ArrayList<>();
        for (ExpGrantCommand command : commands) {
            if (command.reason().recordable()) {
                throw new IllegalArgumentException(
                        "되돌릴 수 없는 사유는 이력을 남겨야 한다: " + command.reason());
            }
            if (command.amount() == 0) {
                continue;
            }

            if (command.amount() > 0) {
                user.addExp(command.amount());
            } else {
                user.subtractExp(-command.amount());
            }
            applied.add(new ExpReasonResponse(command.reason(), command.amount()));
        }

        return ExpResponse.of(before, user.getExp(), applied);
    }

    /**
     * 적립 없이 현재 상태만. 재수신·재호출처럼 <b>아무것도 지급하지 않은 요청</b>이 쓴다.
     *
     * <p>응답에서 {@code exp} 객체를 빼는 대신 값이 {@code 0}인 객체를 내보내는 것이라,
     * 앱은 응답마다 존재 여부를 분기하지 않아도 된다(api.md §1).
     */
    public ExpResponse current(Long userId) {
        return ExpResponse.unchanged(findUser(userId).getExp());
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND,
                        "exp를 조정할 사용자가 없다 userId=" + userId));
    }

}
