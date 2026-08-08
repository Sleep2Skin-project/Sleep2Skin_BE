package com.allday.sleep2skin_be.global.response;

import com.allday.sleep2skin_be.global.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 모든 API 응답을 감싸는 공통 래퍼.
 *
 * <p>클라이언트는 {@code success}로 분기한다. 성공이면 {@code data}가, 실패면 {@code error}가 채워진다.
 *
 * <p><b>비어 있는 쪽은 직렬화하지 않는다.</b> {@code success}가 이미 같은 정보를 담고 있어
 * {@code "error": null}은 중복이다.
 *
 * <p><b>이 설정을 전역({@code spring.jackson.default-property-inclusion})으로 올리지 말 것.</b>
 * 그러면 중첩 DTO의 null까지 사라지는데, 이 서비스에서는 그게 의미 있는 값이다 —
 * 예보 응답의 {@code "complexion": null}은 "그 지표를 산출할 수 없었다"는 뜻이고
 * {@code unavailable}의 사유와 짝을 이룬다(api.md). 키가 통째로 없어지면 클라이언트가
 * 산출 불가와 키 이름 오류를 구분할 수 없다. 클래스 단위라 이 레코드의 필드에만 적용된다.
 */
@Schema(description = "공통 응답 래퍼")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(

        @Schema(description = "요청 성공 여부", example = "true")
        boolean success,

        @Schema(description = "응답 데이터. 실패 응답에는 이 키가 없다")
        T data,

        @Schema(description = "에러 정보. 성공 응답에는 이 키가 없다")
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
