package com.allday.sleep2skin_be.domain.game;

import com.allday.sleep2skin_be.domain.game.dto.ExpGrantCommand;
import com.allday.sleep2skin_be.domain.game.dto.response.AttendanceResponse;
import com.allday.sleep2skin_be.domain.game.dto.response.ExpResponse;
import com.allday.sleep2skin_be.domain.game.entity.ExpReason;
import com.allday.sleep2skin_be.domain.skin.VerificationStreakCalculator;
import com.allday.sleep2skin_be.domain.skin.repository.SkinMeasurementRepository;
import com.allday.sleep2skin_be.domain.user.repository.UserRepository;
import com.allday.sleep2skin_be.global.exception.BusinessException;
import com.allday.sleep2skin_be.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 출석 체크인 (HOME-04 — api.md §2.1).
 *
 * <p><b>{@link ExpService}와 나눈 이유는 성격이 다르기 때문이다.</b> {@code ExpService}는 네
 * 도메인이 함께 쓰는 적립 창구이고, 여기는 <b>홈 화면 하나를 위한 흐름</b>이다 — 출석 적립에
 * 연속 검증 횟수를 곁들여 팝업 하나를 그린다. 이걸 적립 창구에 넣으면 {@code skin} 의존이
 * 창구 쪽에 붙어, 다른 세 도메인이 적립하려고 그것까지 끌고 오게 된다.
 *
 * <h2>하루 1회는 {@code exp_grant} 유니크가 보장한다</h2>
 *
 * <p>"오늘 앱을 켰다"는 사실을 담은 행이 <b>어디에도 없어서</b> 상태로 환원할 방법이 없다.
 * 이력 테이블이 없으면 앱을 재실행할 때마다 {@code +10}이 붙는데, <b>TODO exp에서 한 번 막았던
 * 무한 적립과 정확히 같은 형태다</b>(erd.md §3.1).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AttendanceService {

    private final UserRepository userRepository;
    private final ExpService expService;
    private final SkinMeasurementRepository skinMeasurementRepository;
    private final VerificationStreakCalculator streakCalculator;

    /**
     * 출석을 기록하고 {@code ATTENDANCE} 보상을 지급한다.
     *
     * <p><b>같은 날 재호출은 에러가 아니다.</b> {@code checkedIn: false} · {@code gained: 0}으로
     * 나가며, {@code ExpService}가 이력 행을 보고 조용히 건너뛴다.
     *
     * <p><b>연속 검증 횟수는 {@link VerificationStreakCalculator}가 계산한다.</b> HOME-09 배너·
     * MY-01 프로필과 같은 숫자여야 하고(prd.md §4.2), 각자 계산하면 화면들이 어긋난다 —
     * 어긋나도 값 범위는 정상이라 알아채기 어렵다. <b>여기에 계산을 다시 적지 말 것.</b>
     *
     * @param baseDate 앱이 알려준 "오늘". <b>필수다</b> — 서버는 "오늘"을 모르고, 없이 처리하면
     *                 한국 시간 오전 9시 이전에 출석이 어제 날짜로 찍힌다. 그러면 그날 다시
     *                 호출할 때 오늘 몫이 <b>또</b> 지급된다
     */
    @Transactional
    public AttendanceResponse checkIn(Long userId, LocalDate baseDate) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND,
                    "출석을 기록할 사용자가 없다 userId=" + userId);
        }

        ExpResponse exp = expService.grantDaily(userId, baseDate, List.of(
                new ExpGrantCommand(ExpReason.ATTENDANCE, LevelPolicy.ATTENDANCE_EXP)));

        int streakCount = streakCalculator.calculate(baseDate,
                skinMeasurementRepository.findVerifiedBaseDates(userId, baseDate));

        return AttendanceResponse.of(baseDate, streakCount, exp);
    }

}
