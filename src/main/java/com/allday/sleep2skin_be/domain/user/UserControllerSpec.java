package com.allday.sleep2skin_be.domain.user;

import com.allday.sleep2skin_be.domain.user.dto.response.ConsentAgreeResponse;
import com.allday.sleep2skin_be.domain.user.dto.response.OnboardingCompleteResponse;
import com.allday.sleep2skin_be.domain.user.dto.response.SleepDataStatusResponse;
import com.allday.sleep2skin_be.domain.user.dto.response.UserDeleteResponse;
import com.allday.sleep2skin_be.domain.user.dto.response.UserProfileResponse;
import com.allday.sleep2skin_be.global.resolver.CurrentUserId;
import com.allday.sleep2skin_be.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;

/**
 * {@link UserController}의 API 문서.
 *
 * <p><b>Swagger 어노테이션을 컨트롤러에서 분리한 자리다.</b> 프론트가 Swagger UI만 보고 개발하므로
 * 설명이 길어질 수밖에 없는데, 그게 컨트롤러에 그대로 붙으면 실제 코드가 어노테이션에 파묻힌다.
 * 컨트롤러는 위임 로직만 남기고 문서는 여기서 관리한다.
 *
 * <p><b>공통 규약도 API마다 되풀이해 적는다.</b> {@code @Tag} 설명은 Swagger UI에서 태그를 접어도
 * 계속 펼쳐진 채 목록 맨 위를 차지해, 거기에 규약을 넣으면 API 목록을 보기가 불편해진다.
 * 읽는 사람은 자기가 쓸 API 하나만 펼치므로 그 안에 다 있는 편이 낫다.
 *
 * <p><b>{@link CurrentUserId}는 구현체 쪽에도 반드시 붙어 있어야 한다.</b> 파라미터 어노테이션은
 * 인터페이스에서 상속되지 않아, 여기에만 두면 리졸버가 파라미터를 인식하지 못한다.
 */
@Tag(name = "User", description = "사용자 · 동의 · 온보딩 API")
public interface UserControllerSpec {

    @Operation(summary = "개인정보 수집·이용 동의 저장 (ONB-02)", description = """
            현재 약관 버전에 대한 동의를 이력에 남긴다.

            ### 언제 호출하나

            온보딩 개인정보 동의 화면에서 사용자가 **[동의]를 누른 직후** 한 번 호출한다.
            동의하지 않으면 호출하지 않는다 — 미동의는 서비스 이용 자체가 성립하지 않는 정책이라
            "동의하지 않음"을 보내는 경로가 없다.

            ### 요청

            **본문이 없다.** `X-User-Id` 헤더만 있으면 된다.

            `X-User-Id`는 요청을 보낸 사용자의 식별자다. 인증이 없어 클라이언트가 직접 지정하며,
            아래 파라미터 입력란에 값을 넣고 실행하면 된다.

            약관 버전은 **서버가 정한다.** 클라이언트가 버전을 보내면 임의의 문자열이 이력에 섞여
            "언제 어느 버전에 동의했는가"를 신뢰할 수 없게 되므로 받지 않는다. 현재 버전은 응답의
            `termsVersion`으로 확인할 수 있다.

            ### 응답

            공통 래퍼에 담겨 나간다. **성공이면 `error` 키가, 실패면 `data` 키가 아예 없다.**
            `success`로 분기하면 된다.

            ```jsonc
            { "success": true,
              "data": { "consentId": 1, "termsVersion": "1.0",
                        "agreedAt": "2026-08-08T11:28:19Z", "newlyAgreed": true } }
            ```

            | 상황 | 코드 | `newlyAgreed` | 서버가 한 일 |
            |---|---|---|---|
            | 이 버전에 처음 동의 | `201` | `true` | 동의 이력을 새로 저장 |
            | 같은 버전에 이미 동의한 상태 | `200` | `false` | **저장하지 않고** 기존 이력을 그대로 반환 |

            **둘 다 성공이다.** 화면 흐름을 나눌 필요가 없다면 `newlyAgreed`는 무시하고 다음 단계로
            진행하면 된다.

            `agreedAt`은 동의한 시각이다(ISO 8601, UTC).

            ### 재호출해도 안전하다

            **같은 버전에 대해 멱등하다.** 네트워크 오류로 재시도하거나, 앱을 재설치해 온보딩을 다시
            밟아도 이력이 중복으로 쌓이지 않는다. 응답으로 처음 동의한 시각(`agreedAt`)이 유지된다.

            약관이 개정되면 서버가 버전을 올리고, 그 다음 호출부터 **새 이력이 생기며 다시 `201`**이
            온다. 기존 이력은 지워지지 않는다.

            ### 예외

            실패 응답은 `{ "success": false, "error": { "code": ..., "message": ... } }` 모양이다.
            **분기는 `error.code`(문자열)로 한다.** `error.message`는 사용자에게 그대로 보여줄 수 있는
            한국어 문장이지만 문구가 다듬어질 수 있으므로 분기 조건으로 쓰지 않는다.

            | 코드 | `error.code` | 언제 | 앱이 할 일 |
            |---|---|---|---|
            | `400` | `USER_ID_HEADER_INVALID` | `X-User-Id` 헤더가 없거나 숫자가 아님 | 요청 버그다. 헤더를 넣고 다시 호출 |
            | `404` | `USER_NOT_FOUND` | 그 `userId`의 사용자가 DB에 없음 | 시딩된 테스트 유저 ID인지 확인 |
            | `500` | `INTERNAL_ERROR` | 서버 오류 | 재시도 안내 |
            """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201", description = "동의 이력을 새로 저장했다 (`newlyAgreed: true`)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "이미 같은 버전에 동의한 상태여서 기존 이력을 반환했다 (`newlyAgreed: false`)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", description = "`USER_ID_HEADER_INVALID` — `X-User-Id` 헤더 누락 또는 형식 오류",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(
                    name = "USER_ID_HEADER_INVALID",
                    ref = "#/components/examples/USER_ID_HEADER_INVALID")))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "`USER_NOT_FOUND` — 존재하지 않는 사용자",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(
                    name = "USER_NOT_FOUND",
                    ref = "#/components/examples/USER_NOT_FOUND")))
    ResponseEntity<ApiResponse<ConsentAgreeResponse>> agreeConsent(@CurrentUserId Long userId);

    @Operation(summary = "온보딩 완료 처리 (ONB-05)", description = """
            사용자의 온보딩 완료 상태를 `true`로 바꾼다.

            ### 언제 호출하나

            온보딩의 **마지막 단계에서 홈 화면으로 진입하기 직전** 한 번 호출한다.
            수면 데이터 연결(ONB-03)에 성공했든 건너뛰었든 상관없이 호출한다.

            ### 요청

            **본문이 없다.** `X-User-Id` 헤더만 있으면 된다. 바꿀 상태가 하나뿐이라 `PATCH` + 빈 본문이다.

            `X-User-Id`는 요청을 보낸 사용자의 식별자다. 인증이 없어 클라이언트가 직접 지정하며,
            아래 파라미터 입력란에 값을 넣고 실행하면 된다.

            ### 응답

            공통 래퍼에 담겨 나간다. **성공이면 `error` 키가, 실패면 `data` 키가 아예 없다.**
            `success`로 분기하면 된다.

            ```jsonc
            { "success": true,
              "data": { "userId": 2, "onboardingCompleted": true, "newlyCompleted": true } }
            ```

            성공하면 항상 `200`이고 `onboardingCompleted`는 언제나 `true`다.

            | 상황 | `newlyCompleted` | 서버가 한 일 |
            |---|---|---|
            | 온보딩 미완료였던 사용자 | `true` | 상태를 `true`로 변경 |
            | 이미 완료된 사용자 | `false` | 아무것도 하지 않음 |

            **이미 완료된 사용자도 에러가 아니다.** 되돌리는 경로가 없어 다시 호출해도 상태가 달라질
            여지가 없으므로 재호출해도 안전하다.

            ### 서버가 확인하지 않는 것

            **동의(ONB-02)를 먼저 했는지 검사하지 않는다.** 온보딩 단계 순서를 지키는 것은 클라이언트
            몫이다. 서버가 막으면 시연용 데이터를 만들 때 순서에 묶여 곤란해진다.

            ### 예외

            실패 응답은 `{ "success": false, "error": { "code": ..., "message": ... } }` 모양이다.
            **분기는 `error.code`(문자열)로 한다.** `error.message`는 사용자에게 그대로 보여줄 수 있는
            한국어 문장이지만 문구가 다듬어질 수 있으므로 분기 조건으로 쓰지 않는다.

            | 코드 | `error.code` | 언제 | 앱이 할 일 |
            |---|---|---|---|
            | `400` | `USER_ID_HEADER_INVALID` | `X-User-Id` 헤더가 없거나 숫자가 아님 | 요청 버그다. 헤더를 넣고 다시 호출 |
            | `404` | `USER_NOT_FOUND` | 그 `userId`의 사용자가 DB에 없음 | 시딩된 테스트 유저 ID인지 확인 |
            | `500` | `INTERNAL_ERROR` | 서버 오류 | 재시도 안내 |
            """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "처리 성공. 이미 완료된 사용자도 여기에 해당한다 (`newlyCompleted: false`)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", description = "`USER_ID_HEADER_INVALID` — `X-User-Id` 헤더 누락 또는 형식 오류",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(
                    name = "USER_ID_HEADER_INVALID",
                    ref = "#/components/examples/USER_ID_HEADER_INVALID")))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "`USER_NOT_FOUND` — 존재하지 않는 사용자",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(
                    name = "USER_NOT_FOUND",
                    ref = "#/components/examples/USER_NOT_FOUND")))
    ApiResponse<OnboardingCompleteResponse> completeOnboarding(@CurrentUserId Long userId);

    @Operation(summary = "온보딩·동의 상태 + 프로필 조회 (ONB-01 · MY-01)", description = """
            **앱이 시작될 때 가장 먼저 호출한다.** 온보딩 화면을 띄울지, 동의 화면을 띄울지,
            바로 홈으로 갈지를 이 한 번의 응답으로 결정한다.

            ### 앱은 두 불리언만 보면 된다

            | `consentAgreed` | `onboardingCompleted` | 띄울 화면 |
            |---|---|---|
            | `false` | — | 동의 화면(ONB-02)부터 |
            | `true` | `false` | 온보딩 이어서(ONB-03~05) |
            | `true` | `true` | 온보딩 전체를 건너뛰고 홈으로 |

            ### `consentAgreed`는 "동의한 적이 있는가"가 아니다

            **"현재 약관 버전에 동의했는가"다.** 약관이 개정돼 서버 상수가 올라가면 기존 사용자도
            `false`가 되어 자연스럽게 재동의 화면으로 간다. **로컬 플래그로는 이걸 알 방법이 없다** —
            앱은 "동의 완료"만 기억하고 있어서 버전이 올라간 것을 영원히 모른다.

            `agreedTermsVersion`이 `null`이면 첫 사용자이고, 값이 있는데 `currentTermsVersion`과
            다르면 재동의 상황이다. **분기 자체는 `consentAgreed` 하나로 충분하다.**

            ### 프로필 숫자 (MY-01)

            `verificationCount`(누적 검증 횟수)와 `streakCount`(연속 검증 횟수)가 함께 나온다.
            **`streakCount`는 HOME-09 배너와 같은 계산에서 나오므로 두 화면이 같은 값을 보여준다.**

            **등급이 아니라 숫자를 준다.** 신뢰도 해석("믿을 만함" 등)은 클라이언트가 한다 —
            컷오프를 서버에 두면 바꿀 때마다 배포해야 한다.

            **`baseDate`가 필수인 이유가 이 두 숫자다.** 연속 횟수는 "오늘"을 알아야 계산되는데
            서버는 모른다. 없이 계산하면 연속이 하루 밀린다. **오늘 미검증은 연속을 끊지 않는다** —
            오늘 또는 어제부터 이어져 있으면 유효하다.

            ### 빈 상태가 없다

            다른 조회 API와 달리 **`status`·`message`를 쓰지 않는다.** 사용자가 존재하면 이 응답은
            언제나 완전하다 — 신규 사용자도 `onboardingCompleted: false`, `streakCount: 0`이라는
            **정상적인 값**을 받는다. 사용자가 없으면 그건 진짜 오류이므로 `404`다.
            """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "조회 성공. 신규 사용자도 여기에 해당한다 (값이 `false`·`0`일 뿐이다)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", description = "`INVALID_INPUT` — `baseDate` 누락 또는 형식 오류 · `USER_ID_HEADER_INVALID` — 헤더 누락",
            content = @Content(mediaType = "application/json", examples = {
                    @ExampleObject(name = "INVALID_INPUT",
                            ref = "#/components/examples/INVALID_INPUT"),
                    @ExampleObject(name = "USER_ID_HEADER_INVALID",
                            ref = "#/components/examples/USER_ID_HEADER_INVALID")}))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "`USER_NOT_FOUND` — 존재하지 않는 사용자",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(
                    name = "USER_NOT_FOUND",
                    ref = "#/components/examples/USER_NOT_FOUND")))
    ApiResponse<UserProfileResponse> getProfile(
            @CurrentUserId Long userId,

            @Parameter(description = "기준일 (`YYYY-MM-DD`). **앱의 로컬 날짜를 보낸다** — "
                    + "연속 검증 횟수에 \"오늘\"이 필요한데 서버는 모른다. 서버 시각(UTC)으로 "
                    + "계산하면 한국 시간 오전 9시 이전에 연속이 하루 밀린다",
                    example = "2026-08-14")
            LocalDate baseDate);

    @Operation(summary = "수면 데이터 연결 상태 (MY-02)", description = """
            **마지막으로 수면 데이터가 서버에 도착한 시각**만 반환한다.

            ### 서버가 알 수 없는 것

            - **HealthKit 권한이 살아 있는지** — 클라이언트 권한 상태라 서버가 볼 방법이 없다
            - **다음 동기화가 언제인지** — 서버 배치가 없다. 앱이 시작될 때 올리는 것이 전부이므로
              동기화 주기 표기는 앱의 업로드 정책을 그대로 노출하면 된다

            ### "마지막으로 잔 날"이 아니라 "마지막으로 받은 시각"이다

            며칠 전 데이터를 방금 올린 경우, 잔 날짜를 쓰면 **"동기화가 며칠째 안 됐다"고 잘못
            말하게 된다.** 화면이 말하려는 것은 연결 상태이므로 수신 시각이 맞다.

            ### 빈 상태

            수신 이력이 없으면 `status: NO_SLEEP_DATA` + `lastReceivedAt: null`이다.
            **에러가 아니다** — 아직 한 번도 앱을 켜지 않은 신규 사용자에게 일상적으로 발생한다.
            앱은 `message`가 아니라 **`status`로 분기**한다.

            **`baseDate`를 받지 않는다.** 이 응답에는 날짜에 따라 달라지는 값이 없다.
            """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "조회 성공. 수신 이력이 없는 경우도 여기로 나간다 (`status: NO_SLEEP_DATA`)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", description = "`USER_ID_HEADER_INVALID` — `X-User-Id` 헤더 누락 또는 형식 오류",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(
                    name = "USER_ID_HEADER_INVALID",
                    ref = "#/components/examples/USER_ID_HEADER_INVALID")))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "`USER_NOT_FOUND` — 존재하지 않는 사용자",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(
                    name = "USER_NOT_FOUND",
                    ref = "#/components/examples/USER_NOT_FOUND")))
    ApiResponse<SleepDataStatusResponse> getSleepDataStatus(@CurrentUserId Long userId);

    @Operation(summary = "모든 기록 삭제 (MY-04)", description = """
            ⚠️ **복구 불가 영구 삭제다.** soft delete가 아니라 행을 지운다.

            ### 무엇이 지워지나

            사용자와 함께 **수면 세션·단계 구간·예보·실측·개인 가중치·TODO·동의 이력**이 전부
            사라진다. 되살릴 방법이 없다 — 특히 **개인 가중치는 셀피를 다시 찍어 검증을 반복해야만**
            같은 상태로 돌아온다.

            액션 마스터(`action_master`)는 사용자에 속하지 않는 콘텐츠라 지워지지 않는다.

            ### 서버가 하지 않는 것

            **2단계 확인 다이얼로그는 클라이언트 몫이다.** 서버는 요청을 받으면 즉시 지운다 —
            되돌리는 경로가 없으므로 앱이 반드시 사용자 확인을 받고 호출해야 한다.

            **삭제 후 어느 화면으로 갈지도 서버가 정하지 않는다.** 온보딩으로 돌아갈지는 앱이
            결정한다.

            ### 응답

            본문 없는 `204`가 아니라 **`200` + 공통 래퍼**다. 모든 응답이 같은 모양이어야 앱이
            여기서만 다르게 파싱하지 않는다.

            **멱등하지 않다.** 이미 지워진 사용자로 다시 호출하면 `404 USER_NOT_FOUND`다.
            """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "삭제 완료. **되돌릴 수 없다**")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", description = "`USER_ID_HEADER_INVALID` — `X-User-Id` 헤더 누락 또는 형식 오류",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(
                    name = "USER_ID_HEADER_INVALID",
                    ref = "#/components/examples/USER_ID_HEADER_INVALID")))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "`USER_NOT_FOUND` — 존재하지 않는 사용자 (이미 삭제된 경우 포함)",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(
                    name = "USER_NOT_FOUND",
                    ref = "#/components/examples/USER_NOT_FOUND")))
    ApiResponse<UserDeleteResponse> delete(@CurrentUserId Long userId);

}
