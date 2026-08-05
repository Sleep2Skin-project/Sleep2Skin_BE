package com.allday.sleep2skin_be.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 서비스 전체의 에러 코드.
 *
 * <p>이 서비스는 <b>빈 상태가 정상 흐름</b>이다. 수면 데이터가 없거나, 검증 이력이 없거나,
 * 기록이 부족한 상황이 예외가 아니라 일상이다. 이런 경우도 에러 코드로 관리해
 * 클라이언트가 빈 상태 화면을 정확히 분기할 수 있게 한다.
 *
 * <p>코드 이름은 클라이언트가 문자열로 분기하므로 한번 정하면 함부로 바꾸지 않는다.
 * 메시지는 사용자에게 그대로 보여줄 수 있는 한국어 문장으로 쓰고,
 * 개발자용 상세 정보는 로그로 남긴다.
 *
 * <p>도메인별 구역을 지켜 추가한다. 여러 명이 동시에 건드리면 충돌이 잦은 파일이다.
 */
@Getter
public enum ErrorCode {

    // ===== 공통 =====
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요."),

    // ===== 사용자 =====
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),

    // ===== 수면 =====
    SLEEP_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "수면 데이터가 없습니다. 앱에서 수면 기록을 동기화해주세요."),
    SLEEP_STAGE_INVALID(HttpStatus.BAD_REQUEST, "수면 단계 값이 올바르지 않습니다."),
    SLEEP_TIME_INVALID(HttpStatus.BAD_REQUEST, "취침 시각이 기상 시각보다 늦을 수 없습니다."),

    // ===== 피부 =====
    SKIN_FORECAST_NOT_FOUND(HttpStatus.NOT_FOUND, "오늘의 예보가 아직 산출되지 않았습니다."),
    SELFIE_IMAGE_INVALID(HttpStatus.BAD_REQUEST, "이미지 파일이 올바르지 않습니다."),
    SELFIE_ANALYSIS_FAILED(HttpStatus.BAD_GATEWAY, "셀피 분석에 실패했습니다. 다시 시도해주세요."),
    SELFIE_ANALYSIS_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "분석이 지연되고 있습니다. 다시 시도해주세요."),
    VERIFICATION_ALREADY_DONE(HttpStatus.CONFLICT, "오늘은 이미 검증을 완료했습니다."),
    VERIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "아직 검증 이력이 없습니다. 첫 검증을 시작해보세요."),

    // ===== TODO =====
    ACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 액션 항목입니다."),
    TODO_NOT_FOUND(HttpStatus.NOT_FOUND, "오늘의 TODO를 찾을 수 없습니다."),
    TODO_ALREADY_ADDED(HttpStatus.CONFLICT, "이미 오늘의 목록에 있는 항목입니다."),

    // ===== 리포트 =====
    REPORT_DATA_INSUFFICIENT(HttpStatus.NOT_FOUND, "리포트를 만들기에 기록이 부족합니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

}
