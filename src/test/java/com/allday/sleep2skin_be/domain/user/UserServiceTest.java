package com.allday.sleep2skin_be.domain.user;

import com.allday.sleep2skin_be.domain.skin.VerificationStreakCalculator;
import com.allday.sleep2skin_be.domain.skin.repository.PersonalWeightRepository;
import com.allday.sleep2skin_be.domain.skin.repository.SkinForecastRepository;
import com.allday.sleep2skin_be.domain.skin.repository.SkinMeasurementRepository;
import com.allday.sleep2skin_be.domain.sleep.repository.SleepSessionRepository;
import com.allday.sleep2skin_be.domain.sleep.repository.SleepStageSegmentRepository;
import com.allday.sleep2skin_be.domain.todo.repository.DailyTodoRepository;
import com.allday.sleep2skin_be.domain.user.dto.response.OnboardingCompleteResponse;
import com.allday.sleep2skin_be.domain.user.dto.response.SleepDataStatusResponse;
import com.allday.sleep2skin_be.domain.user.dto.response.UserProfileResponse;
import com.allday.sleep2skin_be.domain.user.entity.ConsentHistory;
import com.allday.sleep2skin_be.domain.user.entity.User;
import com.allday.sleep2skin_be.domain.user.repository.ConsentHistoryRepository;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 14);

    @Mock
    private UserRepository userRepository;
    @Mock
    private ConsentHistoryRepository consentHistoryRepository;
    @Mock
    private SkinMeasurementRepository skinMeasurementRepository;
    @Mock
    private SleepSessionRepository sleepSessionRepository;
    @Mock
    private SleepStageSegmentRepository sleepStageSegmentRepository;
    @Mock
    private SkinForecastRepository skinForecastRepository;
    @Mock
    private PersonalWeightRepository personalWeightRepository;
    @Mock
    private DailyTodoRepository dailyTodoRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        // 연속 계산은 진짜를 쓴다 — 스텁으로 두면 검증하려는 규칙을 테스트가 직접 정하게 된다
        userService = new UserService(userRepository, consentHistoryRepository,
                skinMeasurementRepository, sleepSessionRepository, new VerificationStreakCalculator(),
                sleepStageSegmentRepository, skinForecastRepository, personalWeightRepository,
                dailyTodoRepository);
    }

    @Nested
    @DisplayName("온보딩 완료 (ONB-05)")
    class 온보딩_완료 {

        @Test
        @DisplayName("온보딩 미완료 사용자를 완료 상태로 바꾼다")
        void 온보딩을_완료_상태로_바꾼다() {
            User user = user("테스트유저2");

            OnboardingCompleteResponse response = userService.completeOnboarding(USER_ID);

            assertThat(user.isOnboardingCompleted()).isTrue();
            assertThat(response.onboardingCompleted()).isTrue();
            assertThat(response.newlyCompleted()).isTrue();
        }

        @Test
        @DisplayName("이미 완료된 사용자는 에러가 아니라 newlyCompleted=false로 응답한다")
        void 이미_완료된_사용자도_정상_응답이다() {
            user("테스트유저1").completeOnboarding();

            OnboardingCompleteResponse response = userService.completeOnboarding(USER_ID);

            assertThat(response.onboardingCompleted()).isTrue();
            assertThat(response.newlyCompleted()).isFalse();
        }

        @Test
        @DisplayName("존재하지 않는 사용자면 USER_NOT_FOUND다")
        void 없는_사용자는_USER_NOT_FOUND다() {
            given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.completeOnboarding(USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("프로필 조회 (ONB-01 · MY-01)")
    class 프로필_조회 {

        @Test
        @DisplayName("현재 약관 버전에 동의했으면 consentAgreed=true다")
        void 현재_버전_동의는_true다() {
            user("테스트유저1").completeOnboarding();
            latestConsent(ConsentPolicy.CURRENT_TERMS_VERSION);
            verifiedOn();

            UserProfileResponse response = userService.getProfile(USER_ID, BASE_DATE);

            assertThat(response.consentAgreed()).isTrue();
            assertThat(response.onboardingCompleted()).isTrue();
            assertThat(response.currentTermsVersion()).isEqualTo(ConsentPolicy.CURRENT_TERMS_VERSION);
            assertThat(response.agreedTermsVersion()).isEqualTo(ConsentPolicy.CURRENT_TERMS_VERSION);
            assertThat(response.agreedAt()).isNotNull();
        }

        /**
         * <b>이 API의 핵심이다.</b> 약관이 개정되면 기존 사용자도 false가 되어 재동의 화면으로
         * 간다 — 로컬 플래그로는 버전이 올라간 것을 알 방법이 없다.
         */
        @Test
        @DisplayName("옛 버전에만 동의했으면 consentAgreed=false지만 이력은 그대로 실린다")
        void 옛_버전_동의는_false다() {
            user("테스트유저1");
            latestConsent("0.9");
            verifiedOn();

            UserProfileResponse response = userService.getProfile(USER_ID, BASE_DATE);

            assertThat(response.consentAgreed()).isFalse();
            assertThat(response.agreedTermsVersion()).isEqualTo("0.9");   // 무엇에 동의했는지는 남는다
            assertThat(response.currentTermsVersion()).isEqualTo(ConsentPolicy.CURRENT_TERMS_VERSION);
        }

        @Test
        @DisplayName("동의 이력이 없으면 버전·시각이 null이고 consentAgreed=false다")
        void 이력이_없으면_null이다() {
            user("테스트유저1");
            given(consentHistoryRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID))
                    .willReturn(Optional.empty());
            verifiedOn();

            UserProfileResponse response = userService.getProfile(USER_ID, BASE_DATE);

            assertThat(response.consentAgreed()).isFalse();
            assertThat(response.agreedTermsVersion()).isNull();
            assertThat(response.agreedAt()).isNull();
        }

        /** HOME-09 배너와 같은 계산이라야 두 화면이 같은 숫자를 보여준다 (prd.md §4.2). */
        @Test
        @DisplayName("연속 검증 횟수가 HOME-09와 같은 규칙으로 계산된다")
        void 연속_횟수를_같은_규칙으로_센다() {
            user("테스트유저1");
            noConsent();
            // 오늘 · 어제 · 그제 연속 후 하루 비었다
            verifiedOn(BASE_DATE, BASE_DATE.minusDays(1), BASE_DATE.minusDays(2),
                    BASE_DATE.minusDays(4));
            given(skinMeasurementRepository.countByUserId(USER_ID)).willReturn(4L);

            UserProfileResponse response = userService.getProfile(USER_ID, BASE_DATE);

            assertThat(response.streakCount()).isEqualTo(3);
            assertThat(response.verificationCount()).isEqualTo(4);   // 누적은 4다
        }

        /** 저녁에 검증하는 사용자가 아침에 0을 보면 아직 하지 않은 일로 벌주는 것처럼 읽힌다. */
        @Test
        @DisplayName("오늘 미검증이 연속을 끊지 않는다")
        void 오늘_미검증은_연속을_끊지_않는다() {
            user("테스트유저1");
            noConsent();
            verifiedOn(BASE_DATE.minusDays(1), BASE_DATE.minusDays(2));

            UserProfileResponse response = userService.getProfile(USER_ID, BASE_DATE);

            assertThat(response.streakCount()).isEqualTo(2);
        }

        /** 신규 사용자에게 정상이다 — 빈 상태로 가리지 않는다. */
        @Test
        @DisplayName("검증 이력이 없는 신규 사용자는 두 숫자가 0일 뿐 정상 응답이다")
        void 신규_사용자도_정상_응답이다() {
            user("테스트유저1");
            noConsent();
            verifiedOn();

            UserProfileResponse response = userService.getProfile(USER_ID, BASE_DATE);

            assertThat(response.verificationCount()).isZero();
            assertThat(response.streakCount()).isZero();
            assertThat(response.onboardingCompleted()).isFalse();
            assertThat(response.userId()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("존재하지 않는 사용자면 USER_NOT_FOUND다")
        void 없는_사용자는_404다() {
            given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getProfile(USER_ID, BASE_DATE))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("수면 데이터 연결 상태 (MY-02)")
    class 연결_상태 {

        @Test
        @DisplayName("마지막 수신 시각을 반환한다")
        void 마지막_수신_시각을_준다() {
            userExists();
            OffsetDateTime receivedAt = OffsetDateTime.parse("2026-08-14T07:10:00Z");
            given(sleepSessionRepository.findLastReceivedAt(USER_ID)).willReturn(Optional.of(receivedAt));

            SleepDataStatusResponse response = userService.getSleepDataStatus(USER_ID);

            assertThat(response.status()).isEqualTo(QueryStatus.AVAILABLE);
            assertThat(response.message()).isNull();
            assertThat(response.lastReceivedAt()).isEqualTo(receivedAt);
        }

        @Test
        @DisplayName("수신 이력이 없으면 에러가 아니라 빈 상태다")
        void 이력이_없으면_빈_상태다() {
            userExists();
            given(sleepSessionRepository.findLastReceivedAt(USER_ID)).willReturn(Optional.empty());

            SleepDataStatusResponse response = userService.getSleepDataStatus(USER_ID);

            assertThat(response.status()).isEqualTo(QueryStatus.NO_SLEEP_DATA);
            assertThat(response.message()).isNotBlank();
            assertThat(response.lastReceivedAt()).isNull();
        }

        @Test
        @DisplayName("존재하지 않는 사용자면 USER_NOT_FOUND다 — 이건 빈 상태가 아니다")
        void 없는_사용자는_404다() {
            given(userRepository.existsById(USER_ID)).willReturn(false);

            assertThatThrownBy(() -> userService.getSleepDataStatus(USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);

            verify(sleepSessionRepository, never()).findLastReceivedAt(anyLong());
        }
    }

    @Nested
    @DisplayName("전체 삭제 (MY-04)")
    class 전체_삭제 {

        /**
         * <b>DB에 users 외래키가 없어 CASCADE가 걸리지 않는다.</b> users 행만 지우면 나머지 6개
         * 테이블에 고아 행이 남고, 같은 userId가 재사용되면 남의 이력이 새 사용자에게 붙는다.
         */
        @Test
        @DisplayName("자식 테이블 6개를 전부 지운다")
        void 자식_테이블을_전부_지운다() {
            User user = user("테스트유저1");

            userService.delete(USER_ID);

            verify(sleepStageSegmentRepository).deleteByUserId(USER_ID);
            verify(sleepSessionRepository).deleteByUserId(USER_ID);
            verify(skinForecastRepository).deleteByUserId(USER_ID);
            verify(skinMeasurementRepository).deleteByUserId(USER_ID);
            verify(personalWeightRepository).deleteByUserId(USER_ID);
            verify(dailyTodoRepository).deleteByUserId(USER_ID);
            verify(consentHistoryRepository).deleteByUserId(USER_ID);
            verify(userRepository).delete(user);
        }

        /** sleep_stage_segment만 진짜 FK를 갖고 있어 세션보다 먼저 지워야 한다. */
        @Test
        @DisplayName("단계 구간을 수면 세션보다 먼저 지운다")
        void 구간이_세션보다_먼저다() {
            user("테스트유저1");

            userService.delete(USER_ID);

            var order = inOrder(sleepStageSegmentRepository, sleepSessionRepository);
            order.verify(sleepStageSegmentRepository).deleteByUserId(USER_ID);
            order.verify(sleepSessionRepository).deleteByUserId(USER_ID);
        }

        @Test
        @DisplayName("존재하지 않는 사용자면 아무것도 지우지 않고 USER_NOT_FOUND다")
        void 없는_사용자는_404다() {
            given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.delete(USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);

            verify(sleepSessionRepository, never()).deleteByUserId(anyLong());
            verify(userRepository, never()).delete(any());
        }
    }

    // ===== 픽스처 =====

    private User user(String nickname) {
        User user = User.builder().nickname(nickname).build();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(userRepository.existsById(USER_ID)).willReturn(true);
        return user;
    }

    private void userExists() {
        given(userRepository.existsById(USER_ID)).willReturn(true);
    }

    private void latestConsent(String termsVersion) {
        ConsentHistory consent = ConsentHistory.builder()
                .userId(USER_ID).termsVersion(termsVersion).agreed(true).build();
        ReflectionTestUtils.setField(consent, "createdAt",
                OffsetDateTime.parse("2026-08-08T00:12:33Z"));
        given(consentHistoryRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID))
                .willReturn(Optional.of(consent));
    }

    private void noConsent() {
        given(consentHistoryRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID))
                .willReturn(Optional.empty());
    }

    /** 조회는 최신순으로 준다 — 연속 계산이 앞에서부터 읽는다. */
    private void verifiedOn(LocalDate... dates) {
        given(skinMeasurementRepository.findVerifiedBaseDates(USER_ID, BASE_DATE))
                .willReturn(List.of(dates));
    }

}
