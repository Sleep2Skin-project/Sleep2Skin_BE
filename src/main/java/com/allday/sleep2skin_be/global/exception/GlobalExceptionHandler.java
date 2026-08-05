package com.allday.sleep2skin_be.global.exception;

import com.allday.sleep2skin_be.global.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 전역 예외 처리.
 *
 * <p>예외 스택트레이스나 내부 메시지를 응답에 노출하지 않는다. 상세 정보는 로그로만 남긴다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 비즈니스 규칙 위반과 빈 상태. 의도된 흐름이므로 스택트레이스를 남기지 않는다.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.info("비즈니스 예외 code={} detail={}", errorCode.name(), e.getMessage());
        return toResponse(errorCode);
    }

    /**
     * 요청 본문 검증 실패(@Valid). 어느 필드가 왜 틀렸는지 사용자에게 알려준다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.info("요청 검증 실패 {}", detail);
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ApiResponse.fail(ErrorCode.INVALID_INPUT, detail));
    }

    /**
     * 필수 파라미터 누락, 타입 불일치. 클라이언트가 계약을 어긴 경우다.
     */
    @ExceptionHandler({MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception e) {
        log.info("잘못된 요청 {}", e.getMessage());
        return toResponse(ErrorCode.INVALID_INPUT);
    }

    /**
     * 존재하지 않는 경로. 404를 500으로 만들지 않기 위해 따로 받는다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
        log.info("존재하지 않는 경로 {}", e.getResourcePath());
        return toResponse(ErrorCode.RESOURCE_NOT_FOUND);
    }

    /**
     * 예상하지 못한 예외. 원인을 알 수 없으므로 스택트레이스를 남긴다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        return toResponse(ErrorCode.INTERNAL_ERROR);
    }

    private ResponseEntity<ApiResponse<Void>> toResponse(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.fail(errorCode));
    }

}
