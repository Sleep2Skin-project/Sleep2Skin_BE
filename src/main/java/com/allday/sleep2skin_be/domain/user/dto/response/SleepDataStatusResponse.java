package com.allday.sleep2skin_be.domain.user.dto.response;

import com.allday.sleep2skin_be.global.response.QueryStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * 수면 데이터 연결 상태 (MY-02).
 *
 * <p><b>마지막 수신 시각뿐이다.</b> 서버 배치가 없으므로 그 이상 알 수 있는 게 없다 —
 * <b>HealthKit 권한이 살아 있는지는 서버가 알 방법이 없고</b>(클라이언트 권한 상태다),
 * 동기화 주기도 앱의 업로드 정책(앱 시작 시)이라 서버가 정하지 않는다(erd.md §2).
 *
 * <p><b>"마지막으로 잔 날"이 아니라 "마지막으로 받은 시각"이다.</b> 앞엣것을 쓰면 며칠 전
 * 데이터를 방금 올린 경우에 "동기화가 며칠째 안 됐다"고 잘못 말하게 된다.
 *
 * <p>수신 이력이 없는 것은 <b>신규 사용자에게 일상적</b>이라 에러가 아니다 —
 * {@code NO_SLEEP_DATA} + {@code lastReceivedAt: null}로 나간다.
 *
 * @param status         앱은 <b>문구가 아니라 이 값으로 분기한다</b>
 * @param message        사용자에게 그대로 보여줄 수 있는 한국어 문장. 정상이면 {@code null}
 * @param lastReceivedAt 마지막 수신 시각(UTC). 이력이 없으면 {@code null}
 */
@Schema(description = "수면 데이터 연결 상태")
public record SleepDataStatusResponse(

        @Schema(description = "조회 상태", example = "AVAILABLE")
        QueryStatus status,

        @Schema(description = "빈 상태일 때 보여줄 안내 문구. 정상이면 `null`", nullable = true,
                example = "null")
        String message,

        @Schema(description = "마지막으로 수면 데이터가 서버에 도착한 시각. 이력이 없으면 `null`",
                nullable = true, example = "2026-08-14T07:10:00Z")
        OffsetDateTime lastReceivedAt
) {

    private static final String NO_SLEEP_DATA_MESSAGE = "아직 수면 데이터를 받은 적이 없습니다.";

    public static SleepDataStatusResponse of(OffsetDateTime lastReceivedAt) {
        return new SleepDataStatusResponse(QueryStatus.AVAILABLE, null, lastReceivedAt);
    }

    public static SleepDataStatusResponse empty() {
        return new SleepDataStatusResponse(QueryStatus.NO_SLEEP_DATA, NO_SLEEP_DATA_MESSAGE, null);
    }

}
