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
import com.allday.sleep2skin_be.domain.user.dto.response.UserDeleteResponse;
import com.allday.sleep2skin_be.domain.user.dto.response.UserProfileResponse;
import com.allday.sleep2skin_be.domain.user.entity.User;
import com.allday.sleep2skin_be.domain.user.repository.ConsentHistoryRepository;
import com.allday.sleep2skin_be.domain.user.repository.UserRepository;
import com.allday.sleep2skin_be.global.exception.BusinessException;
import com.allday.sleep2skin_be.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 사용자 상태 관리.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ConsentHistoryRepository consentHistoryRepository;
    private final SkinMeasurementRepository skinMeasurementRepository;
    private final SleepSessionRepository sleepSessionRepository;
    private final VerificationStreakCalculator streakCalculator;

    // 아래 넷은 MY-04 전체 삭제에만 쓰인다 (DB에 users FK가 없어 손으로 지운다 — delete 참조)
    private final SleepStageSegmentRepository sleepStageSegmentRepository;
    private final SkinForecastRepository skinForecastRepository;
    private final PersonalWeightRepository personalWeightRepository;
    private final DailyTodoRepository dailyTodoRepository;

    /**
     * 온보딩 완료 처리 (ONB-05).
     *
     * <p><b>동의 이력이 있는지 확인하지 않는다.</b> ONB-02 → ONB-05 순서를 지키는 것은
     * 클라이언트 몫이고, 서버가 막으면 시연용 데이터를 파이프라인에 주입하는 경로가 좁아진다.
     *
     * <p><b>멱등하다.</b> 이미 완료된 사용자도 에러가 아니라 정상 응답이다 — 되돌리는 경로가
     * 없으므로 다시 호출해도 상태가 달라질 여지가 없다.
     */
    @Transactional
    public OnboardingCompleteResponse completeOnboarding(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND,
                        "온보딩을 완료할 사용자가 없다 userId=" + userId));

        boolean newlyCompleted = !user.isOnboardingCompleted();
        if (newlyCompleted) {
            user.completeOnboarding();
        }

        return OnboardingCompleteResponse.of(user, newlyCompleted);
    }

    /**
     * 온보딩·동의 상태 + 프로필 (ONB-01 진입 분기 + MY-01).
     *
     * <p><b>연속 검증 횟수는 {@link VerificationStreakCalculator}가 계산한다.</b> HOME-09 배너와
     * 같은 숫자여야 하고(prd.md §4.2), 각자 계산하면 두 화면이 어긋난다 — 어긋나도 값 범위는
     * 정상이라 알아채기 어렵다. <b>여기에 계산을 다시 적지 말 것.</b>
     *
     * <p><b>빈 상태가 없다.</b> 검증 이력이 없는 신규 사용자는 두 숫자가 {@code 0}일 뿐이고
     * 그것이 정상적인 값이다 — 다른 조회 API처럼 {@code status}로 가릴 것이 없다.
     *
     * @param baseDate 앱이 알려준 "오늘". <b>연속 횟수에 필요하다</b> — 서버 시각으로 대신하면
     *                 한국 시간 오전 9시 이전에 연속이 하루 밀린다
     */
    public UserProfileResponse getProfile(Long userId, LocalDate baseDate) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND,
                        "프로필을 조회할 사용자가 없다 userId=" + userId));

        int streakCount = streakCalculator.calculate(baseDate,
                skinMeasurementRepository.findVerifiedBaseDates(userId, baseDate));

        return UserProfileResponse.of(user,
                consentHistoryRepository.findFirstByUserIdOrderByCreatedAtDesc(userId).orElse(null),
                skinMeasurementRepository.countByUserId(userId),
                streakCount);
    }

    /**
     * 수면 데이터 연결 상태 (MY-02).
     *
     * <p><b>마지막 수신 시각뿐이다.</b> 서버 배치가 없어 그 이상 알 수 있는 게 없고, HealthKit
     * 권한 상태는 애초에 서버가 알 수 없다(erd.md §2).
     */
    public SleepDataStatusResponse getSleepDataStatus(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND,
                    "연결 상태를 조회할 사용자가 없다 userId=" + userId);
        }

        return sleepSessionRepository.findLastReceivedAt(userId)
                .map(SleepDataStatusResponse::of)
                .orElseGet(SleepDataStatusResponse::empty);
    }

    /**
     * 전체 삭제 (MY-04). <b>복구 불가 영구 삭제다.</b> soft delete가 아니다(erd.md §5).
     *
     * <h2>⚠️ DB가 대신 지워주지 않는다 — 자식 테이블을 손으로 지운다</h2>
     *
     * <p>erd.md §5는 "모든 FK에 {@code ON DELETE CASCADE}를 건다"고 적었지만 <b>실제 스키마에
     * {@code users} 외래키가 하나도 없다.</b> 자식 테이블이 {@code userId}를 연관관계가 아니라
     * 단순 {@code Long} 컬럼으로 들고 있어(architecture.md §4 연관관계 최소화) Hibernate가 제약을
     * 만들지 않았기 때문이다. {@code users} 행만 지우면 <b>나머지 6개 테이블에 고아 행이 남는다.</b>
     *
     * <p>고아 행은 조회에 잡히지 않아 알아채기 어렵고, 같은 {@code userId}가 재사용되는 순간
     * <b>남의 수면·검증 이력이 새 사용자에게 붙는다.</b>
     *
     * <p><b>순서가 하나 강제된다.</b> {@code sleep_stage_segment}만 진짜 FK를 갖고 있어
     * ({@code @ManyToOne}) {@code sleep_session}보다 먼저 지워야 한다. 나머지는 서로 참조하지
     * 않아 순서가 자유롭다.
     *
     * <p><b>새 테이블에 {@code userId}가 생기면 여기에 한 줄을 추가해야 한다.</b> 빠뜨려도
     * 컴파일도 테스트도 통과한다 — 이 목록이 유일한 방어선이다.
     *
     * <p>⚠️ <b>마지막 사용자를 지우면 {@code TestUserSeeder}가 다음 기동에 다시 시딩한다</b> —
     * 사용자가 한 명도 없을 때만 도는 조건이라 그렇다. 삭제가 되돌려진 것처럼 보이지만
     * <b>새 사용자이고 데이터는 돌아오지 않는다.</b>
     */
    @Transactional
    public UserDeleteResponse delete(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND,
                        "삭제할 사용자가 없다 userId=" + userId));

        sleepStageSegmentRepository.deleteByUserId(userId);   // 세션보다 먼저 — 유일한 FK다
        sleepSessionRepository.deleteByUserId(userId);
        skinForecastRepository.deleteByUserId(userId);
        skinMeasurementRepository.deleteByUserId(userId);
        personalWeightRepository.deleteByUserId(userId);
        dailyTodoRepository.deleteByUserId(userId);
        consentHistoryRepository.deleteByUserId(userId);
        userRepository.delete(user);

        log.info("사용자 전체 삭제 userId={}", userId);

        return UserDeleteResponse.of(userId);
    }

}
