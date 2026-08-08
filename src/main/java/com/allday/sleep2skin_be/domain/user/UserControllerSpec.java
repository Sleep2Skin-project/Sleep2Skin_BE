package com.allday.sleep2skin_be.domain.user;

import com.allday.sleep2skin_be.domain.user.dto.response.ConsentAgreeResponse;
import com.allday.sleep2skin_be.domain.user.dto.response.OnboardingCompleteResponse;
import com.allday.sleep2skin_be.global.resolver.CurrentUserId;
import com.allday.sleep2skin_be.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

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

}
