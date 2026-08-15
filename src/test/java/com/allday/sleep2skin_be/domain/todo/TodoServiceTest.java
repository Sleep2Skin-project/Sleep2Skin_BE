package com.allday.sleep2skin_be.domain.todo;

import com.allday.sleep2skin_be.domain.game.ExpService;
import com.allday.sleep2skin_be.domain.game.LevelPolicy;
import com.allday.sleep2skin_be.domain.game.entity.ExpReason;
import com.allday.sleep2skin_be.domain.game.repository.ExpGrantRepository;
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
import com.allday.sleep2skin_be.global.response.QueryStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TodoServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 13);

    /** 확정값 (PRD §10.9). 리터럴로 적으면 정책이 바뀌어도 테스트가 옛 값을 지킨다. */
    private static final int DONE_EXP = LevelPolicy.TODO_DONE_EXP;
    private static final int ALL_DONE_EXP = LevelPolicy.TODO_ALL_DONE_EXP;

    @Mock
    private UserRepository userRepository;
    @Mock
    private DailyTodoRepository dailyTodoRepository;
    @Mock
    private ActionMasterRepository actionMasterRepository;
    @Mock
    private SkinForecastRepository skinForecastRepository;
    @Mock
    private SkinMeasurementRepository skinMeasurementRepository;
    @Mock
    private ExpGrantRepository expGrantRepository;

    private TodoService service;

    @BeforeEach
    void setUp() {
        // ExpService는 실물을 쓴다 — 0 하한과 "0은 건너뛴다"가 여기 있어서, 모킹하면
        // 이 테스트가 지키려는 무한 적립 차단이 실제로 검증되지 않는다.
        // adjust()는 이력 행을 만들지 않으므로 expGrantRepository는 호출되지 않는다.
        ExpService expService = new ExpService(userRepository, expGrantRepository);
        service = new TodoService(userRepository, dailyTodoRepository, actionMasterRepository,
                skinForecastRepository, skinMeasurementRepository, expService);
        given(userRepository.existsById(USER_ID)).willReturn(true);
        given(skinMeasurementRepository.findVerifiedDays(any(), any())).willReturn(List.of());
        given(dailyTodoRepository.saveAll(anyList())).willAnswer(call -> call.getArgument(0));
    }

    @Nested
    @DisplayName("목록 조회")
    class 목록_조회 {

        /**
         * <b>수면을 아직 올리지 않은 신규 사용자가 TODO 탭을 열면 일상적으로 발생한다.</b>
         * 404로 내리면 경로 오타·잘못된 userId와 섞여 모니터링에서 신규 유입이 에러 급증으로 보인다.
         */
        @Test
        @DisplayName("그날 예보가 없으면 에러가 아니라 빈 상태로 응답한다")
        void 예보가_없으면_빈_상태다() {
            todosOf();
            given(skinForecastRepository.findByUserIdAndBaseDate(USER_ID, BASE_DATE))
                    .willReturn(Optional.empty());

            TodoListResponse response = service.getTodos(USER_ID, BASE_DATE);

            assertThat(response.status()).isEqualTo(QueryStatus.NO_SLEEP_DATA);
            assertThat(response.message()).isNotBlank();
            assertThat(response.baseDate()).isEqualTo(BASE_DATE);
            assertThat(response.avoidItems()).isEmpty();
            assertThat(response.checklistItems()).isEmpty();
            verify(dailyTodoRepository, never()).saveAll(anyList());
        }

        /**
         * <b>"예보가 없다"와 "예보는 있는데 처방할 것이 없다"는 다른 상태다.</b> 앱이 띄울 문구가
         * 달라야 하므로 둘을 같은 status로 묶으면 안 된다.
         */
        @Test
        @DisplayName("후보가 0개인 날은 AVAILABLE이고 배열만 비어 있다")
        void 후보가_없어도_정상_상태다() {
            todosOf();
            forecast(90, 90, 90);                       // 전부 임계값보다 좋다
            candidates(ActionCategory.AVOID, action(1L, ActionCategory.AVOID, SkinMetric.BARRIER, 50, 5));
            candidates(ActionCategory.DO, action(2L, ActionCategory.DO, SkinMetric.BARRIER, 50, 5));

            TodoListResponse response = service.getTodos(USER_ID, BASE_DATE);

            assertThat(response.status()).isEqualTo(QueryStatus.AVAILABLE);
            assertThat(response.message()).isNull();
            assertThat(response.avoidItems()).isEmpty();
            assertThat(response.checklistItems()).isEmpty();
        }

        /**
         * 매 조회마다 계산하면 오전에 본 목록과 오후에 본 목록이 달라진다 — verdictBonus가
         * 그날 셀피 검증으로 바뀌기 때문이다.
         */
        @Test
        @DisplayName("그날 행이 이미 있으면 추천 엔진을 다시 돌리지 않는다")
        void 목록은_첫_조회에_고정된다() {
            todosOf(todo(11L, action(1L, ActionCategory.DO, SkinMetric.BARRIER, 50, 5)));

            TodoListResponse response = service.getTodos(USER_ID, BASE_DATE);

            assertThat(response.status()).isEqualTo(QueryStatus.AVAILABLE);
            assertThat(response.checklistItems()).hasSize(1);
            verify(actionMasterRepository, never()).findByCategoryAndActiveTrue(any());
            verify(skinForecastRepository, never()).findByUserIdAndBaseDate(any(), any());
        }

        @Test
        @DisplayName("첫 조회에 AVOID 3개 + DO 5개까지 만든다")
        void 절단_개수는_3과_5다() {
            todosOf();
            forecast(20, 20, 20);
            candidates(ActionCategory.AVOID, actions(ActionCategory.AVOID, 4));
            candidates(ActionCategory.DO, actions(ActionCategory.DO, 6));

            TodoListResponse response = service.getTodos(USER_ID, BASE_DATE);

            assertThat(response.avoidItems()).hasSize(3);
            assertThat(response.checklistItems()).hasSize(5);
        }

        /** AVOID 카드는 체크 대상이 아니다 — 저장된 PENDING을 노출하면 앱이 체크박스를 그린다. */
        @Test
        @DisplayName("AVOID 항목은 status 없이, DO 항목은 causeLabel 없이 나간다")
        void 두_섹션이_다른_필드를_채운다() {
            todosOf(
                    todo(11L, action(1L, ActionCategory.AVOID, SkinMetric.BARRIER, 50, 5)),
                    todo(12L, action(2L, ActionCategory.DO, SkinMetric.BARRIER, 50, 5)));

            TodoListResponse response = service.getTodos(USER_ID, BASE_DATE);

            assertThat(response.avoidItems().getFirst().status()).isNull();
            assertThat(response.avoidItems().getFirst().causeLabel()).isEqualTo("장벽 약화의 원인");
            assertThat(response.avoidItems().getFirst().reason()).isNotBlank();
            assertThat(response.checklistItems().getFirst().status()).isEqualTo(TodoStatus.PENDING);
            assertThat(response.checklistItems().getFirst().causeLabel()).isNull();
            assertThat(response.checklistItems().getFirst().reason()).isNull();
        }

        @Test
        @DisplayName("존재하지 않는 사용자는 404 USER_NOT_FOUND다 — 이건 빈 상태가 아니다")
        void 없는_사용자는_404다() {
            given(userRepository.existsById(USER_ID)).willReturn(false);

            assertThatThrownBy(() -> service.getTodos(USER_ID, BASE_DATE))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);

            verify(skinForecastRepository, never()).findByUserIdAndBaseDate(any(), any());
        }
    }

    @Nested
    @DisplayName("상태 변경과 exp")
    class 상태_변경 {

        @Test
        @DisplayName("완료로 바뀌면 확정값 +5를 지급한다")
        void 완료하면_적립된다() {
            User user = user(100);
            DailyTodo todo = savedTodo(21L, ActionCategory.DO, TodoStatus.PENDING);
            dayAlsoHas(todo, todo(22L, ActionCategory.DO, TodoStatus.PENDING));   // 아직 남은 항목

            TodoStatusUpdateResponse response = service.updateStatus(USER_ID, 21L, TodoStatus.DONE);

            assertThat(response.status()).isEqualTo(TodoStatus.DONE);
            assertThat(response.exp().gained()).isEqualTo(DONE_EXP);
            assertThat(response.exp().totalExp()).isEqualTo(105);
            assertThat(response.allCompleted()).isFalse();
            assertThat(user.getExp()).isEqualTo(105);
            assertThat(todo.getStatus()).isEqualTo(TodoStatus.DONE);
        }

        /**
         * <b>회수가 없으면 무한 적립이 된다.</b> 판정이 "이번에 DONE이 됐는가"뿐이라 중복 호출만
         * 막히고, 껐다 켜는 것은 막히지 않는다.
         */
        @Test
        @DisplayName("되돌리면 exp를 회수한다")
        void 되돌리면_회수한다() {
            User user = user(100);
            DailyTodo todo = savedTodo(21L, ActionCategory.DO, TodoStatus.DONE);
            dayAlsoHas(todo, todo(22L, ActionCategory.DO, TodoStatus.PENDING));

            TodoStatusUpdateResponse response = service.updateStatus(USER_ID, 21L, TodoStatus.PENDING);

            assertThat(response.status()).isEqualTo(TodoStatus.PENDING);
            assertThat(response.exp().gained()).isEqualTo(-DONE_EXP);
            assertThat(user.getExp()).isEqualTo(95);
        }

        /** 이 테스트가 원래 버그를 붙들어 둔다 — 회수 전에는 반복할수록 계속 올라갔다. */
        @Test
        @DisplayName("껐다 켜기를 반복해도 순증은 +5를 넘지 않는다")
        void 반복_토글로_적립되지_않는다() {
            User user = user(100);
            DailyTodo todo = savedTodo(21L, ActionCategory.DO, TodoStatus.PENDING);
            dayAlsoHas(todo, todo(22L, ActionCategory.DO, TodoStatus.PENDING));

            for (int i = 0; i < 5; i++) {
                service.updateStatus(USER_ID, 21L, TodoStatus.DONE);
                service.updateStatus(USER_ID, 21L, TodoStatus.PENDING);
            }
            service.updateStatus(USER_ID, 21L, TodoStatus.DONE);

            assertThat(user.getExp()).isEqualTo(100 + DONE_EXP);
        }

        @Test
        @DisplayName("같은 상태로 다시 요청하면 exp가 움직이지 않는다")
        void 같은_상태_재요청은_멱등이다() {
            User user = user(100);
            DailyTodo todo = savedTodo(21L, ActionCategory.DO, TodoStatus.DONE);
            dayAlsoHas(todo, todo(22L, ActionCategory.DO, TodoStatus.PENDING));

            TodoStatusUpdateResponse response = service.updateStatus(USER_ID, 21L, TodoStatus.DONE);

            assertThat(response.exp().gained()).isZero();
            assertThat(response.exp().reasons()).isEmpty();
            assertThat(user.getExp()).isEqualTo(100);
        }

        /** 누적 경험치에 음수는 뜻을 갖지 않는다. 응답에는 요청한 양이 아니라 실제 증감이 담긴다. */
        @Test
        @DisplayName("exp가 0일 때 되돌려도 음수가 되지 않는다")
        void 회수는_0에서_멈춘다() {
            User user = user(0);
            DailyTodo todo = savedTodo(21L, ActionCategory.DO, TodoStatus.DONE);
            dayAlsoHas(todo, todo(22L, ActionCategory.DO, TodoStatus.PENDING));

            TodoStatusUpdateResponse response = service.updateStatus(USER_ID, 21L, TodoStatus.PENDING);

            assertThat(user.getExp()).isZero();
            assertThat(response.exp().totalExp()).isZero();
            assertThat(response.exp().gained()).isZero();
        }

        /**
         * 응답에서 status를 가리는 것만으로는 막히지 않는다 — 그 id로 PATCH가 통과하면
         * AVOID 행이 DONE이 되어 달성률 분모·분자가 오염된다.
         */
        @Test
        @DisplayName("AVOID 항목은 400 ACTION_NOT_CHECKABLE로 막는다")
        void 피하세요는_체크할_수_없다() {
            User user = user(100);
            DailyTodo todo = savedTodo(21L, ActionCategory.AVOID, TodoStatus.PENDING);

            assertThatThrownBy(() -> service.updateStatus(USER_ID, 21L, TodoStatus.DONE))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ACTION_NOT_CHECKABLE);

            assertThat(todo.getStatus()).isEqualTo(TodoStatus.PENDING);
            assertThat(user.getExp()).isEqualTo(100);
        }

        /** 403이면 "그 id는 존재한다"가 새어 나간다 — 인증이 없는 상태에서는 404로 덮는다. */
        @Test
        @DisplayName("다른 사용자의 항목은 404 TODO_NOT_FOUND다")
        void 남의_항목은_보이지_않는다() {
            DailyTodo todo = todo(21L, action(1L, ActionCategory.DO, SkinMetric.BARRIER, 50, 5));
            ReflectionTestUtils.setField(todo, "userId", 999L);
            given(dailyTodoRepository.findById(21L)).willReturn(Optional.of(todo));

            assertThatThrownBy(() -> service.updateStatus(USER_ID, 21L, TodoStatus.DONE))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.TODO_NOT_FOUND);
        }
    }

    /**
     * 그날 {@code DO}를 전부 채웠을 때의 {@code +30} (prd.md §10.9).
     *
     * <p><b>회수({@code −30})가 빠지면 무한 적립이 된다</b> — 개별 완료와 정확히 같은 형태이며,
     * 마지막 항목 하나를 껐다 켜는 것만으로 계속 붙는다.
     */
    @Nested
    @DisplayName("전체 완료 보너스")
    class 전체_완료_보너스 {

        @Test
        @DisplayName("마지막 DO를 완료하면 +5와 +30이 함께 붙는다")
        void 마지막을_채우면_보너스가_붙는다() {
            User user = user(100);
            DailyTodo todo = savedTodo(21L, ActionCategory.DO, TodoStatus.PENDING);
            dayAlsoHas(todo, todo(22L, ActionCategory.DO, TodoStatus.DONE));

            TodoStatusUpdateResponse response = service.updateStatus(USER_ID, 21L, TodoStatus.DONE);

            assertThat(response.allCompleted()).isTrue();
            assertThat(response.exp().gained()).isEqualTo(DONE_EXP + ALL_DONE_EXP);
            assertThat(response.exp().reasons())
                    .extracting("reason")
                    .containsExactly(ExpReason.TODO_DONE, ExpReason.TODO_ALL_DONE);
            assertThat(user.getExp()).isEqualTo(135);
        }

        @Test
        @DisplayName("전부 완료 상태에서 하나를 풀면 −5와 −30이 함께 회수된다")
        void 하나를_풀면_보너스도_회수된다() {
            User user = user(135);
            DailyTodo todo = savedTodo(21L, ActionCategory.DO, TodoStatus.DONE);
            dayAlsoHas(todo, todo(22L, ActionCategory.DO, TodoStatus.DONE));

            TodoStatusUpdateResponse response = service.updateStatus(USER_ID, 21L, TodoStatus.PENDING);

            assertThat(response.allCompleted()).isFalse();
            assertThat(response.exp().gained()).isEqualTo(-(DONE_EXP + ALL_DONE_EXP));
            assertThat(user.getExp()).isEqualTo(100);
        }

        /** 개별 완료와 같은 자리의 버그다 — 회수를 빼면 여기서 무한 적립이 되살아난다. */
        @Test
        @DisplayName("마지막 항목을 껐다 켜기를 반복해도 순증은 +35를 넘지 않는다")
        void 보너스도_반복_토글로_적립되지_않는다() {
            User user = user(100);
            DailyTodo todo = savedTodo(21L, ActionCategory.DO, TodoStatus.PENDING);
            dayAlsoHas(todo, todo(22L, ActionCategory.DO, TodoStatus.DONE));

            for (int i = 0; i < 5; i++) {
                service.updateStatus(USER_ID, 21L, TodoStatus.DONE);
                service.updateStatus(USER_ID, 21L, TodoStatus.PENDING);
            }
            service.updateStatus(USER_ID, 21L, TodoStatus.DONE);

            assertThat(user.getExp()).isEqualTo(100 + DONE_EXP + ALL_DONE_EXP);
        }

        /**
         * <b>AVOID는 체크 대상이 아니라 판정에서 빠진다</b>(prd.md §10.9). 분모에 넣으면
         * 사용자가 체크할 수 없는 항목 때문에 보너스를 영영 못 받는다.
         */
        @Test
        @DisplayName("AVOID가 PENDING이어도 DO만 다 채우면 보너스가 붙는다")
        void AVOID는_판정에서_빠진다() {
            user(100);
            DailyTodo todo = savedTodo(21L, ActionCategory.DO, TodoStatus.PENDING);
            dayAlsoHas(todo, todo(22L, ActionCategory.AVOID, TodoStatus.PENDING));

            TodoStatusUpdateResponse response = service.updateStatus(USER_ID, 21L, TodoStatus.DONE);

            assertThat(response.allCompleted()).isTrue();
            assertThat(response.exp().gained()).isEqualTo(DONE_EXP + ALL_DONE_EXP);
        }

        /**
         * <b>전이가 아니라 현재 상태다.</b> 같은 요청을 다시 보내면 exp는 0이지만
         * {@code allCompleted}는 참으로 남는다 — 전이는 {@code exp.reasons}가 말한다.
         */
        @Test
        @DisplayName("전부 완료 상태로 재요청하면 gained는 0이고 allCompleted는 참이다")
        void allCompleted는_상태다() {
            user(135);
            DailyTodo todo = savedTodo(21L, ActionCategory.DO, TodoStatus.DONE);
            dayAlsoHas(todo, todo(22L, ActionCategory.DO, TodoStatus.DONE));

            TodoStatusUpdateResponse response = service.updateStatus(USER_ID, 21L, TodoStatus.DONE);

            assertThat(response.allCompleted()).isTrue();
            assertThat(response.exp().gained()).isZero();
            assertThat(response.exp().reasons()).isEmpty();
        }
    }

    // ===== 픽스처 =====

    /** 그날 목록에 다른 항목이 함께 있는 상태로 만든다 (전체 완료 판정용). */
    private void dayAlsoHas(DailyTodo target, DailyTodo... others) {
        List<DailyTodo> all = new java.util.ArrayList<>();
        all.add(target);
        all.addAll(List.of(others));
        given(dailyTodoRepository.findByUserIdAndBaseDate(USER_ID, BASE_DATE)).willReturn(all);
    }

    private static DailyTodo todo(Long id, ActionCategory category, TodoStatus status) {
        DailyTodo todo = todo(id, action(id, category, SkinMetric.BARRIER, 50, 5));
        todo.changeStatus(status);
        return todo;
    }

    private void todosOf(DailyTodo... todos) {
        given(dailyTodoRepository.findByUserIdAndBaseDate(USER_ID, BASE_DATE))
                .willReturn(List.of(todos));
    }

    private void forecast(int darkCircle, Integer complexion, Integer barrier) {
        given(skinForecastRepository.findByUserIdAndBaseDate(USER_ID, BASE_DATE))
                .willReturn(Optional.of(SkinForecast.builder()
                        .userId(USER_ID).baseDate(BASE_DATE)
                        .darkCircle(darkCircle).complexion(complexion).barrier(barrier)
                        .build()));
    }

    private void candidates(ActionCategory category, ActionMaster... actions) {
        given(actionMasterRepository.findByCategoryAndActiveTrue(category))
                .willReturn(List.of(actions));
    }

    private void candidates(ActionCategory category, List<ActionMaster> actions) {
        given(actionMasterRepository.findByCategoryAndActiveTrue(category)).willReturn(actions);
    }

    private static List<ActionMaster> actions(ActionCategory category, int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(i -> action((long) i, category, SkinMetric.BARRIER, 50, i))
                .toList();
    }

    private User user(int exp) {
        User user = User.builder().nickname("테스트유저").build();
        ReflectionTestUtils.setField(user, "exp", exp);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        return user;
    }

    /** 조회되는 상태로 준비한 DO/AVOID 행 하나. */
    private DailyTodo savedTodo(Long id, ActionCategory category, TodoStatus status) {
        DailyTodo todo = todo(id, action(1L, category, SkinMetric.BARRIER, 50, 5));
        todo.changeStatus(status);
        given(dailyTodoRepository.findById(id)).willReturn(Optional.of(todo));
        return todo;
    }

    private static DailyTodo todo(Long id, ActionMaster action) {
        DailyTodo todo = DailyTodo.builder()
                .userId(USER_ID).baseDate(BASE_DATE).actionMaster(action)
                .build();
        ReflectionTestUtils.setField(todo, "id", id);
        return todo;
    }

    private static ActionMaster action(Long id, ActionCategory category, SkinMetric metric,
                                       int threshold, int impactScore) {
        ActionMaster action = ActionMaster.builder()
                .category(category)
                .title("테스트 액션 " + id)
                .reason("테스트 근거 " + id)
                .targetMetric(metric)
                .threshold(threshold)
                .impactScore(impactScore)
                .active(true)
                .build();
        ReflectionTestUtils.setField(action, "id", id);
        return action;
    }

}
