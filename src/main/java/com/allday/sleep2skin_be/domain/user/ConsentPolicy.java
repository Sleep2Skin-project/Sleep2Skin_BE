package com.allday.sleep2skin_be.domain.user;

/**
 * 동의 정책 상수.
 *
 * <p>약관 버전을 서비스 로직에 하드코딩하지 않고 여기 한 곳에 모은다.
 */
public final class ConsentPolicy {

    /**
     * 현재 약관 버전.
     *
     * <p><b>클라이언트가 보내는 값이 아니라 서버 상수다.</b> 요청 본문으로 받으면 임의 문자열이
     * 이력에 섞여 재동의 판정({@code WHERE terms_version <> ?})이 무의미해진다(erd.md §3.2).
     *
     * <p>약관 원문이 개정되면 이 값만 올린다. 기존 이력은 손대지 않는다 — append-only라
     * 다음 동의부터 새 버전으로 새 행이 쌓이고, "언제 어느 버전에 동의했는가"가 그대로 남는다.
     * 원문 확정은 prd.md §7 P4.
     */
    public static final String CURRENT_TERMS_VERSION = "1.0";

    private ConsentPolicy() {
    }

}
