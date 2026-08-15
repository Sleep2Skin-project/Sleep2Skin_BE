package com.allday.sleep2skin_be.domain.report;

import com.allday.sleep2skin_be.domain.report.dto.CorrelationStrength;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationPolicyTest {

    @ParameterizedTest(name = "|r|={0} → {1}")
    @CsvSource({
            "1.0, VERY_STRONG",
            "0.7, VERY_STRONG",   // 경계값 포함
            "0.69, STRONG",
            "0.4, STRONG",        // 경계값 포함
            "0.39, MODERATE",
            "0.2, MODERATE",      // 경계값 포함
            "0.19, WEAK",
            "0.0, WEAK"
    })
    void 구간_경계를_지킨다(double absCorrelation, CorrelationStrength expected) {
        assertThat(CorrelationPolicy.strengthOf(absCorrelation)).isEqualTo(expected);
    }

}
