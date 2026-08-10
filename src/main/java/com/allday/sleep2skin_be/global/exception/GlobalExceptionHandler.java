package com.allday.sleep2skin_be.global.exception;

import com.allday.sleep2skin_be.global.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
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
     * 요청 본문을 읽지 못했다 — 깨진 JSON, 알 수 없는 enum 값, 오프셋 없는 시각 등.
     *
     * <p>이 핸들러가 없으면 <b>페이로드 형식 오류가 500으로 나간다.</b> 서버 잘못이 아니라
     * 클라이언트가 계약을 어긴 것이므로 400이 맞다.
     *
     * <p><b>어느 필드가 틀렸는지로 에러 코드를 나누지 않는다.</b> 그러려면 도메인 타입
     * (수면 단계 enum 등)을 여기서 알아야 하는데, 의존 방향이 {@code domain → global}
     * 한쪽이라 그 반대가 성립하지 않는다. 상세 사유는 로그로 남긴다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException e) {
        log.info("요청 본문을 읽을 수 없음 {}", e.getMostSpecificCause().getMessage());
        return toResponse(ErrorCode.INVALID_INPUT);
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
     * 멀티파트 요청 처리 실패 — 파일이 상한을 넘었거나 본문이 멀티파트 형식이 아니다.
     *
     * <p><b>이 핸들러가 없으면 용량 초과가 500으로 나간다.</b> 서버 잘못이 아니라 클라이언트가
     * 상한을 넘긴 것이므로 400이 맞다. 상한은 {@code spring.servlet.multipart}에 있으며, 셀피가
     * 아이폰 원본으로 들어오는 것을 감안한 값이다.
     *
     * <p><b>여기서 {@code SELFIE_IMAGE_INVALID}로 바꾸지 않는다.</b> 멀티파트를 쓰는 API가 셀피
     * 하나뿐이라 그러고 싶어지지만, {@code global}이 도메인 사정을 알게 되면 의존 방향
     * ({@code domain → global})이 깨진다. 파트 자체에 대한 판정은 Service가 한다.
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipart(MultipartException e) {
        log.info("멀티파트 요청 처리 실패 {}", e.getMessage());
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
