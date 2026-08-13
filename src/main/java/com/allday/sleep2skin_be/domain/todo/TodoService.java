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
            return TodoListResponse.from(baseDate, existing);
        }
        return generate(userId, baseDate);
    }

    /**
     * 그날 첫 조회 시 추천 엔진을 돌려 최대 8행(AVOID 3 + DO 5)을 만든다. 후보가 부족하면
     * 그보다 적을 수 있다.
     */
    @Transactional
    public TodoListResponse generate(Long userId, LocalDate baseDate) {
        // 동시 요청 레이스 대비 재확인
        List<DailyTodo> existing = dailyTodoRepository.findByUserIdAndBaseDate(userId, baseDate);
        if (!existing.isEmpty()) {
            return TodoListResponse.from(baseDate, existing);
        }

        SkinForecast forecast = skinForecastRepository.findByUserIdAndBaseDate(userId, baseDate)
                .orElseThrow(() -> new BusinessException(ErrorCode.SKIN_FORECAST_NOT_FOUND,
                        "예보가 없어 TODO를 만들 수 없다 userId=" + userId + " baseDate=" + baseDate));

        Map<SkinMetric, Integer> forecastScores = scoresOf(forecast);
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

        return TodoListResponse.from(baseDate, saved);
    }

    /**
     * TODO-05. AVOID 항목은 체크 대상이 아니므로 {@code 400 ACTION_NOT_CHECKABLE}로 막는다.
     * DO 항목이 처음 DONE으로 바뀔 때만 exp를 지급한다(멱등).
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

        boolean newlyDone = status == TodoStatus.DONE && todo.getStatus() != TodoStatus.DONE;
        todo.changeStatus(status);

        int expGained = 0;
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND,
                        "exp를 지급할 사용자가 없다 userId=" + userId));
        if (newlyDone) {
            user.addExp(EXP_PER_DONE);
            expGained = EXP_PER_DONE;
        }

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
