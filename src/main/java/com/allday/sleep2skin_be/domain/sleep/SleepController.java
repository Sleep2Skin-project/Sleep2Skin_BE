package com.allday.sleep2skin_be.domain.sleep;

import com.allday.sleep2skin_be.domain.sleep.dto.request.SleepSessionUploadRequest;
import com.allday.sleep2skin_be.domain.sleep.dto.response.SleepInterpretationResponse;
import com.allday.sleep2skin_be.domain.sleep.dto.response.SleepSessionUploadResponse;
import com.allday.sleep2skin_be.domain.sleep.dto.response.SleepSessionUploadResult;
import com.allday.sleep2skin_be.global.resolver.CurrentUserId;
import com.allday.sleep2skin_be.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 수면 수집·해석 API.
 *
 * <p>Swagger 문서는 {@link SleepControllerSpec}에 있다. {@link CurrentUserId}는 파라미터
 * 어노테이션이라 인터페이스에서 상속되지 않으므로 <b>여기에도 반드시 붙어 있어야 한다.</b>
 */
@RestController
@RequestMapping("/api/v1/sleep")
@RequiredArgsConstructor
public class SleepController implements SleepControllerSpec {

    private final SleepSessionService sleepSessionService;
    private final SleepInterpretationService sleepInterpretationService;

    @Override
    @GetMapping("/interpretation")
    public ApiResponse<SleepInterpretationResponse> getInterpretation(
            @CurrentUserId Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baseDate) {

        return ApiResponse.success(sleepInterpretationService.getInterpretation(userId, baseDate));
    }

    @Override
    @PostMapping("/sessions")
    public ResponseEntity<ApiResponse<SleepSessionUploadResponse>> uploadSession(
            @CurrentUserId Long userId,
            @Valid @RequestBody SleepSessionUploadRequest request) {

        SleepSessionUploadResult result = sleepSessionService.upload(userId, request.toCommand());

        // 새 행이 생긴 그날 첫 수신만 201이다. 갱신·재산출은 상태가 바뀌어도 200이다.
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.success(result.response()));
    }

}
