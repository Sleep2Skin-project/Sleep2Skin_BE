package com.allday.sleep2skin_be.domain.todo;

import com.allday.sleep2skin_be.domain.skin.ScoringPolicy;
import com.allday.sleep2skin_be.domain.skin.dto.VerifiedDay;
import com.allday.sleep2skin_be.domain.skin.dto.VerificationVerdict;
import com.allday.sleep2skin_be.domain.skin.entity.SkinForecast;
import com.allday.sleep2skin_be.domain.skin.entity.SkinMetric;
import com.allday.sleep2skin_be.domain.skin.repository.SkinForecastRepository;
import com.allday.sleep2skin_be.domain.skin.repository.SkinMeasurementRepository;
import com.allday.sleep2skin_be.domain.todo.dto.response.TodoListResponse;
import com.allday.sleep2skin_be.domain.todo.dto.response.TodoStatusUpdateResponse;
import com.allday.sleep2skin_be.domain.todo.entity.ActionCategory;
import com.allday.sleep2skin_be.domain.todo.entity.ActionMaster;
import com.allday.sleep2skin_be.domain.todo.entity.DailyTodo;
import com.allday.sleep2skin_be.domain.todo.entity.TodoStatus;
import com.allday.sleep2skin_be.domain.todo.repository.ActionMasterRepository;
import com.allday.sleep2skin_be.domain.todo.repository.DailyTodoRepository;
import com.allday.sleep2skin_be.domain.user.entity.User;
import com.allday.sleep2skin_be.domain.user.repository.UserRepository;
import com.allday.sleep2skin_be.global.exception.BusinessException;
import com.allday.sleep2skin_be.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * TODO-01~05.
 *
 * <p><b>목록은 첫 조회 시 생성하고 고정한다</b>(DailyTodo 클래스 주석 참조). 이 클래스는
 * "생성이 필요한가"만 판단하고, 실제 매칭·가중·정렬·절단은 {@link TodoScoringPolicy}
 * (DB 없이 단위 테스트 가능한 순수 로직)에 위임한다.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TodoService {

    private static final int AVOID_LIMIT = 3;
    private static final int DO_LIMIT = 5;
    private static final int EXP_PER_DONE = 10;

    private final UserRepository userRepository;
    private final DailyTodoRepository dailyTodoRepository;
    private final ActionMasterRepository actionMasterRepository;
    private final SkinForecastRepository skinForecastRepository;
    private final SkinMeasurementRepository skinMeasurementRepository;

    @Transactional
    public TodoListResponse getTodos(Long userId, LocalDate baseDate) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND,
                    "TODO를 조회할 사용자가 없다 userId=" + userId);
        }

        List<DailyTodo> existing = dailyTodoRepository.findByUserIdAndBaseDate(userId, baseDate);
        if (!existing.isEmpty()) {
            return TodoListResponse.of(baseDate, existing);
        }
        return generate(userId, baseDate);
    }

    /**
     * 그날 첫 조회 시 추천 엔진을 돌려 최대 8행(AVOID 3 + DO 5)을 만든다. 후보가 부족하면
     * 그보다 적을 수 있다.
     *
     * <p><b>예보가 없으면 에러가 아니라 빈 상태다.</b> 조회 API의 빈 상태는 200이라는 규칙
     * (conventions.md §2)을 따른다 — 수면을 아직 올리지 않은 신규 사용자가 TODO 탭을 열면
     * 일상적으로 발생하므로, 4xx로 내리면 진짜 문제가 그 안에 묻힌다.
     */
    @Transactional
    public TodoListResponse generate(Long userId, LocalDate baseDate) {
        // 동시 요청 레이스 대비 재확인
        List<DailyTodo> existing = dailyTodoRepository.findByUserIdAndBaseDate(userId, baseDate);
        if (!existing.isEmpty()) {
            return TodoListResponse.of(baseDate, existing);
        }

        Optional<SkinForecast> found = skinForecastRepository.findByUserIdAndBaseDate(userId, baseDate);
        if (found.isEmpty()) {
            log.info("예보가 없어 TODO를 만들지 않는다 userId={} baseDate={}", userId, baseDate);
            return TodoListResponse.empty(baseDate);
        }

        Map<SkinMetric, Integer> forecastScores = scoresOf(found.get());
        Map<SkinMetric, VerificationVerdict> latestVerdicts = latestVerdictsOf(userId, baseDate);

        List<ActionMaster> selectedAvoid = TodoScoringPolicy.selectTop(
                actionMasterRepository.findByCategoryAndActiveTrue(ActionCategory.AVOID),
                forecastScores, latestVerdicts, AVOID_LIMIT);
        List<ActionMaster> selectedDo = TodoScoringPolicy.selectTop(
                actionMasterRepository.findByCategoryAndActiveTrue(ActionCategory.DO),
                forecastScores, latestVerdicts, DO_LIMIT);

        List<DailyTodo> saved = dailyTodoRepository.saveAll(
                Stream.concat(selectedAvoid.stream(), selectedDo.stream())
                        .map(action -> DailyTodo.builder()
                                .userId(userId)
                                .baseDate(baseDate)
                                .actionMaster(action)
                                .build())
                        .toList());

        log.info("TODO 생성 userId={} baseDate={} avoid={} do={}",
                userId, baseDate, selectedAvoid.size(), selectedDo.size());

        return TodoListResponse.of(baseDate, saved);
    }

    /**
     * TODO-05. AVOID 항목은 체크 대상이 아니므로 {@code 400 ACTION_NOT_CHECKABLE}로 막는다.
     *
     * <p><b>exp는 상태가 실제로 바뀔 때만 움직인다.</b> 완료로 바뀌면 지급하고 되돌리면 회수한다 —
     * 같은 상태로 다시 요청하면 0이다(멱등).
     *
     * <p><b>회수가 없으면 무한 적립이 된다.</b> 판정이 "이번에 DONE이 됐는가"뿐이라
     * {@code DONE → PENDING → DONE}을 반복하는 것만으로 매번 exp가 붙는다. 중복 호출
     * ({@code DONE → DONE})만 막는 것으로는 닫히지 않는다.
     */
    @Transactional
    public TodoStatusUpdateResponse updateStatus(Long userId, Long todoId, TodoStatus status) {
        DailyTodo todo = dailyTodoRepository.findById(todoId)
                .filter(t -> t.getUserId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND,
                        "TODO 항목이 없다 id=" + todoId + " userId=" + userId));

        if (todo.getActionMaster().getCategory() == ActionCategory.AVOID) {
            throw new BusinessException(ErrorCode.ACTION_NOT_CHECKABLE);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND,
                        "exp를 조정할 사용자가 없다 userId=" + userId));

        boolean newlyDone = status == TodoStatus.DONE && todo.getStatus() != TodoStatus.DONE;
        boolean undone = status == TodoStatus.PENDING && todo.getStatus() == TodoStatus.DONE;
        todo.changeStatus(status);

        // 실제 증감을 응답에 싣는다 — 회수가 0에서 막히면 요청한 양보다 적게 줄어든다
        int before = user.getExp();
        if (newlyDone) {
            user.addExp(EXP_PER_DONE);
        } else if (undone) {
            user.subtractExp(EXP_PER_DONE);
        }
        int expGained = user.getExp() - before;

        return new TodoStatusUpdateResponse(todo.getId(), todo.getStatus(), expGained, user.getExp());
    }

    private Map<SkinMetric, Integer> scoresOf(SkinForecast forecast) {
        Map<SkinMetric, Integer> scores = new EnumMap<>(SkinMetric.class);
        scores.put(SkinMetric.DARK_CIRCLE, forecast.getDarkCircle());
        if (forecast.getComplexion() != null) {
            scores.put(SkinMetric.COMPLEXION, forecast.getComplexion());
        }
        if (forecast.getBarrier() != null) {
            scores.put(SkinMetric.BARRIER, forecast.getBarrier());
        }
        return scores;
    }

    /**
     * 지표별 "직전 검증" 판정. findVerifiedDays가 최신순으로 주므로 첫 원소가 가장 최근 검증이다.
     * 검증 이력이 없으면 빈 맵을 반환하고, 그러면 TodoScoringPolicy가 보너스 없이 계산한다.
     */
    private Map<SkinMetric, VerificationVerdict> latestVerdictsOf(Long userId, LocalDate baseDate) {
        List<VerifiedDay> verifiedDays = skinMeasurementRepository.findVerifiedDays(userId, baseDate);
        if (verifiedDays.isEmpty()) {
            return Map.of();
        }

        VerifiedDay latest = verifiedDays.getFirst();
        Map<SkinMetric, VerificationVerdict> verdicts = new EnumMap<>(SkinMetric.class);
        verdicts.put(SkinMetric.DARK_CIRCLE,
                ScoringPolicy.verdict(latest.forecastDarkCircle(), latest.measuredDarkCircle()));
        if (latest.forecastComplexion() != null) {
            verdicts.put(SkinMetric.COMPLEXION,
                    ScoringPolicy.verdict(latest.forecastComplexion(), latest.measuredComplexion()));
        }
        if (latest.forecastBarrier() != null) {
            verdicts.put(SkinMetric.BARRIER,
                    ScoringPolicy.verdict(latest.forecastBarrier(), latest.measuredBarrier()));
        }
        return verdicts;
    }

}
