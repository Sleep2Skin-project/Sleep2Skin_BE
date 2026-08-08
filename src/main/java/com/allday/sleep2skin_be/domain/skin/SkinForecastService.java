package com.allday.sleep2skin_be.domain.skin;

import com.allday.sleep2skin_be.domain.skin.dto.response.SkinForecastQueryResponse;
import com.allday.sleep2skin_be.domain.skin.entity.SkinForecast;
import com.allday.sleep2skin_be.domain.skin.repository.SkinForecastRepository;
import com.allday.sleep2skin_be.domain.sleep.entity.SleepSession;
import com.allday.sleep2skin_be.domain.sleep.repository.SleepSessionRepository;
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
 * 피부 예보 조회 (HOME-03).
 *
 * <p><b>여기서 스코어링을 돌리지 않는다.</b> 산출은 수면 업로드 시점에 이미 끝나 있고, 저장된
 * 점수가 그날의 정답이다. 조회할 때마다 다시 계산하면 그 사이 학습으로 바뀐 개인 가중치가
 * 반영되어 <b>이미 마친 검증의 대조 기준이 사후에 달라진다.</b>
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SkinForecastService {

    private final UserRepository userRepository;
    private final SkinForecastRepository skinForecastRepository;
    private final SleepSessionRepository sleepSessionRepository;

    public SkinForecastQueryResponse getForecast(Long userId, LocalDate baseDate) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND,
                    "예보를 조회할 사용자가 없다 userId=" + userId);
        }

        Optional<SkinForecast> forecast = skinForecastRepository.findByUserIdAndBaseDate(userId, baseDate);
        if (forecast.isEmpty()) {
            // 신규 사용자에게 일상적으로 발생한다 — 에러가 아니라 정상 흐름이라 info로 남긴다
            log.info("예보 없음 userId={} baseDate={}", userId, baseDate);
            return SkinForecastQueryResponse.empty(baseDate);
        }

        return SkinForecastQueryResponse.of(forecast.get(), watchDataMissing(userId, baseDate));
    }

    /**
     * 그날 워치를 찼는지. <b>빈 지표의 사유를 가르는 데만 쓴다</b> — 예보에는 사유 컬럼이 없어
     * (파생값이라 두지 않았다) 그날 세션에서 되짚는다.
     *
     * <p>세션은 예보와 같은 트랜잭션에서 만들어지므로 항상 있다. 없다면 데이터가 어긋난 것이지만,
     * <b>조회 API를 500으로 떨어뜨릴 만한 일은 아니라</b> 사유만 기본값으로 두고 로그를 남긴다.
     */
    private boolean watchDataMissing(Long userId, LocalDate baseDate) {
        Optional<SleepSession> session = sleepSessionRepository.findByUserIdAndSleepDate(userId, baseDate);
        if (session.isEmpty()) {
            log.warn("예보는 있는데 수면 세션이 없다 userId={} baseDate={}", userId, baseDate);
            return true;
        }
        SleepSession found = session.get();
        return found.getHrv() == null && found.getRestingHeartRate() == null;
    }

}
