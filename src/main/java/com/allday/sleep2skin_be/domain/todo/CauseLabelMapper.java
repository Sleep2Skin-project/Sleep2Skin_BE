package com.allday.sleep2skin_be.domain.todo;

import com.allday.sleep2skin_be.domain.skin.entity.SkinMetric;

/**
 * AVOID 카드에 노출되는 원인 태그. {@code target_metric}에서 1:1로 고정 매핑된다.
 *
 * <p><b>DB 컬럼으로 두지 않는다.</b> 24개 행마다 중복 저장할 이유가 없고, 문구를 바꿀 때
 * 코드 한 줄만 고치면 되게 하기 위해서다.
 */
public final class CauseLabelMapper {

    private CauseLabelMapper() {
    }

    public static String labelOf(SkinMetric metric) {
        return switch (metric) {
            case DARK_CIRCLE -> "다크서클의 원인";
            case COMPLEXION -> "혈색 저하의 원인";
            case BARRIER -> "피부 장벽 약화의 원인";
        };
    }

}
