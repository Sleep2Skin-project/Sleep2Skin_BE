package com.allday.sleep2skin_be.domain.report;

import com.allday.sleep2skin_be.domain.report.dto.response.DailyTimelineResponse;
import com.allday.sleep2skin_be.domain.report.dto.response.DailyTimelineResponse.SegmentResponse;
import com.allday.sleep2skin_be.domain.sleep.entity.SleepSession;
import com.allday.sleep2skin_be.domain.sleep.entity.SleepStage;
import com.allday.sleep2skin_be.domain.sleep.entity.SleepStageSegment;
import com.allday.sleep2skin_be.domain.sleep.repository.SleepSessionRepository;
import com.allday.sleep2skin_be.domain.sleep.repository.SleepStageSegmentRepository;
import com.allday.sleep2skin_be.domain.user.repository.UserRepository;
import com.allday.sleep2skin_be.global.exception.BusinessException;
import com.allday.sleep2skin_be.global.exception.ErrorCode;
import com.allday.sleep2skin_be.global.response.QueryStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DailyTimelineServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 14);

    @Mock
    private UserRepository userRepository;
    @Mock
    private SleepSessionRepository sleepSessionRepository;
    @Mock
    private SleepStageSegmentRepository sleepStageSegmentRepository;

    @InjectMocks
    private DailyTimelineService dailyTimelineService;

    @Test
    @DisplayName("세션이 있으면 시각과 구간을 startTime 오름차순 그대로 반환한다")
    void 세션이_있으면_구간을_반환한다() {
        given(userRepository.existsById(USER_ID)).willReturn(true);
        SleepSession session = session();
        given(sleepSessionRepository.findByUserIdAndSleepDate(USER_ID, BASE_DATE))
                .willReturn(Optional.of(session));
        // 세션이 영속화 전이라 id는 null이다 — 서비스가 findByUserIdAndSleepDate로 얻은 바로 그
        // 인스턴스의 getId()를 그대로 넘긴다는 것만 확인하면 되므로 null 그대로 매칭한다
        given(sleepStageSegmentRepository.findBySleepSessionIdOrderByStartTimeAsc(session.getId()))
                .willReturn(List.of(segment(SleepStage.DEEP), segment(SleepStage.AWAKE)));

        DailyTimelineResponse response = dailyTimelineService.getTimeline(USER_ID, BASE_DATE);

        assertThat(response.status()).isEqualTo(QueryStatus.AVAILABLE);
        assertThat(response.sleepOnsetTime()).isEqualTo(session.getSleepOnsetTime());
        assertThat(response.wakeTime()).isEqualTo(session.getWakeTime());
        assertThat(response.segments()).extracting(SegmentResponse::stage)
                .containsExactly(SleepStage.DEEP, SleepStage.AWAKE);
    }

    @Test
    @DisplayName("세션이 없으면 빈 상태다 — 시각은 null이고 구간은 빈 배열이다")
    void 세션이_없으면_빈_상태다() {
        given(userRepository.existsById(USER_ID)).willReturn(true);
        given(sleepSessionRepository.findByUserIdAndSleepDate(USER_ID, BASE_DATE))
                .willReturn(Optional.empty());

        DailyTimelineResponse response = dailyTimelineService.getTimeline(USER_ID, BASE_DATE);

        assertThat(response.status()).isEqualTo(QueryStatus.NO_SLEEP_DATA);
        assertThat(response.message()).isNotBlank();
        assertThat(response.sleepOnsetTime()).isNull();
        assertThat(response.wakeTime()).isNull();
        assertThat(response.segments()).isEmpty();

        verify(sleepStageSegmentRepository, never())
                .findBySleepSessionIdOrderByStartTimeAsc(anyLong());
    }

    @Test
    @DisplayName("존재하지 않는 사용자는 404 USER_NOT_FOUND다")
    void 없는_사용자는_404다() {
        given(userRepository.existsById(USER_ID)).willReturn(false);

        assertThatThrownBy(() -> dailyTimelineService.getTimeline(USER_ID, BASE_DATE))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(sleepSessionRepository, never()).findByUserIdAndSleepDate(anyLong(), any());
    }

    // ===== 픽스처 =====

    private static SleepSession session() {
        return SleepSession.builder()
                .userId(USER_ID).sleepDate(BASE_DATE)
                .sleepOnsetTime(OffsetDateTime.parse("2026-08-13T14:40:00Z"))
                .wakeTime(OffsetDateTime.parse("2026-08-13T22:10:00Z"))
                .totalSleepMinutes(432).deepSleepMinutes(126)
                .remSleepMinutes(36).coreSleepMinutes(270)
                .awakeCount(2).awakeMinutes(7)
                .hrv(null).restingHeartRate(null)
                .payloadHash("a".repeat(64))
                .build();
    }

    private static SleepStageSegment segment(SleepStage stage) {
        return SleepStageSegment.builder()
                .sleepSession(session())
                .stage(stage)
                .startTime(OffsetDateTime.parse("2026-08-13T14:40:00Z"))
                .endTime(OffsetDateTime.parse("2026-08-13T15:00:00Z"))
                .build();
    }

}
