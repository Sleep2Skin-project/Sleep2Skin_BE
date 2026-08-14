package com.allday.sleep2skin_be.domain.game;

import com.allday.sleep2skin_be.domain.game.dto.response.AttendanceResponse;
import com.allday.sleep2skin_be.global.resolver.CurrentUserId;
import com.allday.sleep2skin_be.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 레벨 · 경험치 API (HOME-04).
 *
 * <p><b>경로가 {@code /users/me/...}인 것은 의도한 것이다.</b> 패키지 이름이 URL을 정하지 않는다 —
 * 화면상 출석은 사용자 행위이고, 경로는 api.md가 유일한 출처다(architecture.md §2).
 * 패키지를 {@code game}으로 뺀 이유는 <b>exp가 붙는 자리가 네 도메인에 흩어져 있어서</b>이지
 * 별도의 URL 공간이 필요해서가 아니다.
 *
 * <p><b>게이미피케이션 전용 조회 API는 만들지 않는다</b>(api.md §5). 레벨·exp를 읽는 화면이
 * 둘인데 둘 다 이미 부르는 API가 있다 — 마이페이지 로드맵은 {@code GET /users/me}가, 홈 화면은
 * 앱 시작 시 부르는 이 API의 응답이 채운다. 세 번째 엔드포인트를 만들면 <b>같은 숫자를 내는
 * 자리가 셋이 되고 어긋나도 알아채기 어렵다.</b>
 *
 * <p>Swagger 문서는 {@link GameControllerSpec}에 있다. {@link CurrentUserId}는 파라미터
 * 어노테이션이라 인터페이스에서 상속되지 않으므로 <b>여기에도 반드시 붙어 있어야 한다.</b>
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class GameController implements GameControllerSpec {

    private final AttendanceService attendanceService;

    @Override
    @PostMapping("/me/attendance")
    public ApiResponse<AttendanceResponse> checkIn(
            @CurrentUserId Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baseDate) {

        // 재호출도 200이다 — 앱은 시작할 때마다 호출하므로 하루에 대부분이 재호출이다
        return ApiResponse.success(attendanceService.checkIn(userId, baseDate));
    }

}
