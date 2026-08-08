package com.allday.sleep2skin_be.domain.health;

import com.allday.sleep2skin_be.domain.health.dto.HealthCheckResponse;
import com.allday.sleep2skin_be.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Health", description = "서비스 상태 확인 API")
@RestController
@RequestMapping("/api/v1")
public class HealthCheckController {

    private final String applicationName;

    public HealthCheckController(@Value("${spring.application.name:unknown}") String applicationName) {
        this.applicationName = applicationName;
    }

    @Operation(summary = "헬스체크", description = "애플리케이션이 정상 기동되었는지 확인한다. 배포 파이프라인 및 로드밸런서 헬스체크용.")
    // Swagger의 @ApiResponse는 우리 래퍼와 이름이 겹쳐 완전 수식한다.
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "서비스 정상")
    @GetMapping("/health")
    public ApiResponse<HealthCheckResponse> health() {
        return ApiResponse.success(HealthCheckResponse.up(applicationName));
    }

}
