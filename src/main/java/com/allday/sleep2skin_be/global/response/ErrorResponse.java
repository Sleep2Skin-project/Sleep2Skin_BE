package com.allday.sleep2skin_be.global.response;

import com.allday.sleep2skin_be.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "에러 정보")
public record ErrorResponse(

        @Schema(description = "에러 코드", example = "SLEEP_SESSION_NOT_FOUND")
        String code,

        @Schema(description = "사용자에게 보여줄 메시지", example = "수면 데이터가 없어 예보를 산출할 수 없습니다.")
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
