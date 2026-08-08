package com.allday.sleep2skin_be.global.response;

import com.allday.sleep2skin_be.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 실패 응답의 에러 정보.
 *
 * <p><b>필드에 {@code example}을 두지 않는다.</b> 이 레코드는 모든 API가 공유하는 스키마 하나라,
 * 여기 예시를 박으면 도메인·상황과 무관하게 전부 같은 값이 나온다. 게다가 손으로 적은 문구는
 * {@link ErrorCode}의 메시지와 어긋나도 아무 데서도 걸리지 않는다 — 실제로 그런 적이 있다.
 *
 * <p>상황별 예시는 {@code SwaggerConfig}가 {@link ErrorCode}에서 만들어 API마다 붙인다.
 */
@Schema(description = "에러 정보")
public record ErrorResponse(

        @Schema(description = "에러 코드. 클라이언트는 이 값으로 분기한다.")
        String code,

        @Schema(description = "사용자에게 보여줄 한국어 메시지. 문구가 다듬어질 수 있어 분기 조건으로 쓰지 않는다.")
        String message
) {

    public static ErrorResponse from(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage());
    }

    /**
     * 검증 실패처럼 상황별 메시지가 필요한 경우.
     */
    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.name(), message);
    }

}
