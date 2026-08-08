package com.allday.sleep2skin_be.domain.sleep.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 정규화 계층의 입력. {@code POST /api/v1/sleep/sessions}의 요청 본문과 같은 모양이다.
 *
 * <p><b>집계값이 없다.</b> 총 수면·단계별 분·각성 횟수를 앱에서 받지 않는다 — 서버가 세션을
 * 첫 기상에서 자르므로 앱이 보고한 총합에는 그 뒤의 낮잠이 섞여 있을 수 있다(api.md §3).
 * 서버가 자를 거면 서버가 세는 것이 맞다.
 *
 * @param segments          시간순일 필요는 없다. 정규화가 정렬한다
 * @param hrv               야간 RMSSD(ms). 워치 미착용 시 {@code null}
 * @param restingHeartRate  야간 안정시 심박(bpm). 워치 미착용 시 {@code null}
 */
public record SleepNormalizationCommand(

        List<SleepSegmentCommand> segments,

        BigDecimal hrv,

        Integer restingHeartRate
) {
}
