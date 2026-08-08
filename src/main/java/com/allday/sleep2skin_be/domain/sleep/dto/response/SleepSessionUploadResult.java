package com.allday.sleep2skin_be.domain.sleep.dto.response;

/**
 * 서비스 → 컨트롤러 전달용. 상태 코드를 정하는 데 필요한 정보가 응답 본문에 없어서 따로 싣는다.
 *
 * <p><b>{@code created}와 {@code processed}는 다르다.</b> 내용이 바뀐 데이터를 받아 갱신·재산출한
 * 경우는 서버 상태가 바뀌었지만({@code processed = true}) 새로 만든 것은 아니라 {@code 200}이다.
 * 새 행이 생긴 그날 첫 수신만 {@code 201}이다.
 */
public record SleepSessionUploadResult(boolean created, SleepSessionUploadResponse response) {
}
