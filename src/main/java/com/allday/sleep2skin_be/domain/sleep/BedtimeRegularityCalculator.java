package com.allday.sleep2skin_be.domain.sleep;

import com.allday.sleep2skin_be.domain.skin.ScoringPolicy;
import com.allday.sleep2skin_be.domain.sleep.repository.SleepSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 최근 7일 취침 규칙성을 DB에서 모아 계산한다 (§10.6).
 *
 * <p>계산 자체는 {@link BedtimeRegularity}가 하고, 여기서는 <b>어떤 밤들을 넣을지</b>를 정한다.
 * 예보 산출과 수면 통역 카드가 같은 값을 써야 하므로 한 곳에 둔다 — 각자 조회하면 창(window)이
 * 어긋나 <b>같은 밤에 대해 두 화면이 다른 규칙성을 말하게 된다.</b>
 */
@Component
@RequiredArgsConstructor
public class BedtimeRegularityCalculator {

    private final SleepSessionRepository sleepSessionRepository;

    /**
     * @param thisNightOnset 이번 밤의 잠든 시각. <b>DB가 아니라 호출부가 넘긴다</b> — 갱신 경로에서는
     *                       DB에 아직 옛 값이 있어, 저장·flush 순서에 따라 규칙성이 달라진다.
     *                       같은 요청이 호출 순서 때문에 다른 점수를 내면 재현이 안 되는 버그가 된다
     * @return 표준편차(분). 기록이 3일 미만이면 {@code null}이고 스코어링이 그 항을 빼고 재정규화한다
     */
    @Transactional(readOnly = true)
    public Double calculate(Long userId, LocalDate sleepDate, OffsetDateTime thisNightOnset) {
        List<OffsetDateTime> onsetTimes = new ArrayList<>();
        onsetTimes.add(thisNightOnset);
        onsetTimes.addAll(sleepSessionRepository.findSleepOnsetTimes(userId,
                sleepDate.minusDays(ScoringPolicy.BEDTIME_REGULARITY_WINDOW_DAYS - 1L),
                sleepDate.minusDays(1)));

        return BedtimeRegularity.standardDeviationMinutes(onsetTimes);
    }

}
