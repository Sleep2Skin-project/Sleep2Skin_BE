package com.allday.sleep2skin_be.domain.report;

import com.allday.sleep2skin_be.domain.report.dto.response.DailyTimelineResponse;
import com.allday.sleep2skin_be.domain.sleep.entity.SleepSession;
import com.allday.sleep2skin_be.domain.sleep.repository.SleepSessionRepository;
import com.allday.sleep2skin_be.domain.sleep.repository.SleepStageSegmentRepository;
import com.allday.sleep2skin_be.domain.user.repository.UserRepository;
import com.allday.sleep2skin_be.global.exception.BusinessException;
import com.allday.sleep2skin_be.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 일간 수면 타임라인 (REP-03).
 *
 * <p>{@code SleepStageSegment}는 <b>렌더링 전용</b>이다(erd.md §3.4) — 집계는 이미
 * {@code SleepSession}이 들고 있으므로({@code GET /report/daily}가 내보낸다) 여기서는 구간을
 * 시간순으로 나열해 그대로 돌려주기만 한다. 계산이 없다.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DailyTimelineService {

    private final UserRepository userRepository;
    private final SleepSessionRepository sleepSessionRepository;
    private final SleepStageSegmentRepository sleepStageSegmentRepository;

    public DailyTimelineResponse getTimeline(Long userId, LocalDate baseDate) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND,
                    "타임라인을 조회할 사용자가 없다 userId=" + userId);
        }

        Optional<SleepSession> session = sleepSessionRepository.findByUserIdAndSleepDate(userId, baseDate);
        if (session.isEmpty()) {
            // 신규 사용자·앱 미실행 등에서 일상적으로 발생한다 — 에러가 아니라 정상 흐름이다
            log.info("일간 타임라인 — 수면 데이터 없음 userId={} baseDate={}", userId, baseDate);
            return DailyTimelineResponse.empty(baseDate);
        }

        SleepSession found = session.get();
        return DailyTimelineResponse.of(baseDate, found,
                sleepStageSegmentRepository.findBySleepSessionIdOrderByStartTimeAsc(found.getId()));
    }

}
