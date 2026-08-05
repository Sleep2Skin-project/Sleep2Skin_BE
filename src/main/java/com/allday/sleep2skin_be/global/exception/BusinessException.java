package com.allday.sleep2skin_be.global.exception;

import lombok.Getter;

/**
 * 비즈니스 규칙 위반과 빈 상태를 함께 표현하는 예외.
 *
 * <p>도메인별 하위 예외 클래스를 만들지 않는다. {@link ErrorCode}가 이미 도메인을
 * 구분하므로 클래스를 늘리면 중복이다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /**
     * 로그에 남길 상세 사유가 있는 경우. 사용자에게는 {@code errorCode}의 메시지만 나간다.
     */
    public BusinessException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

}
