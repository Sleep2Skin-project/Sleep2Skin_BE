package com.allday.sleep2skin_be.global.response;

import com.allday.sleep2skin_be.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 모든 API 응답을 감싸는 공통 래퍼.
 *
 * <p>성공과 실패의 응답 모양이 같아야 클라이언트가 분기를 하나만 두면 된다.
 * 성공이면 {@code data}가, 실패면 {@code error}가 채워진다.
 */
@Schema(description = "공통 응답 래퍼")
public record ApiResponse<T>(

        @Schema(description = "요청 성공 여부", example = "true")
        boolean success,

        @Schema(description = "응답 데이터. 실패 시 null")
        T data,

        @Schema(description = "에러 정보. 성공 시 null")
        ErrorResponse error
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /**
     * 반환할 데이터가 없는 성공 응답 (삭제, 상태 변경 등).
     *
     * <p>레코드 컴포넌트 {@code success}와 이름이 겹쳐 무인자 {@code success()}를 둘 수 없다.
     */
    public static ApiResponse<Void> noContent() {
        return new ApiResponse<>(true, null, null);
    }

    public static ApiResponse<Void> fail(ErrorCode errorCode) {
        return new ApiResponse<>(false, null, ErrorResponse.from(errorCode));
    }

    public static ApiResponse<Void> fail(ErrorCode errorCode, String message) {
        return new ApiResponse<>(false, null, ErrorResponse.of(errorCode, message));
    }

}
