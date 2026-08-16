package com.allday.sleep2skin_be.domain.report;

import com.allday.sleep2skin_be.domain.report.dto.response.DailyReportResponse;
import com.allday.sleep2skin_be.domain.report.dto.response.DailyTimelineResponse;
import com.allday.sleep2skin_be.domain.report.dto.response.MonthlyReportResponse;
import com.allday.sleep2skin_be.domain.report.dto.response.OverallReportResponse;
import com.allday.sleep2skin_be.domain.report.dto.response.WeeklyReportResponse;
import com.allday.sleep2skin_be.global.resolver.CurrentUserId;
import com.allday.sleep2skin_be.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 일간·주간·월간 리포트 API.
 *
 * <p>Swagger 문서는 {@link ReportControllerSpec}에 있다. {@link CurrentUserId}는 파라미터
 * 어노테이션이라 인터페이스에서 상속되지 않으므로 <b>여기에도 반드시 붙어 있어야 한다.</b>
 */
@RestController
@RequestMapping("/api/v1/report")
@RequiredArgsConstructor
public class ReportController implements ReportControllerSpec {

    private final DailyReportService dailyReportService;
    private final DailyTimelineService dailyTimelineService;
    private final WeeklyReportService weeklyReportService;
    private final MonthlyReportService monthlyReportService;
    private final OverallReportService overallReportService;

    @Override
    @GetMapping("/daily")
    public ApiResponse<DailyReportResponse> getDailyReport(
            @CurrentUserId Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baseDate) {

        return ApiResponse.success(dailyReportService.getDailyReport(userId, baseDate));
    }

    @Override
    @GetMapping("/daily/timeline")
    public ApiResponse<DailyTimelineResponse> getDailyTimeline(
            @CurrentUserId Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baseDate) {

        return ApiResponse.success(dailyTimelineService.getTimeline(userId, baseDate));
    }

    @Override
    @GetMapping("/weekly")
    public ApiResponse<WeeklyReportResponse> getWeeklyReport(
            @CurrentUserId Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baseDate) {

        return ApiResponse.success(weeklyReportService.getWeeklyReport(userId, baseDate));
    }

    @Override
    @GetMapping("/monthly")
    public ApiResponse<MonthlyReportResponse> getMonthlyReport(
            @CurrentUserId Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baseDate) {

        return ApiResponse.success(monthlyReportService.getMonthlyReport(userId, baseDate));
    }

    @Override
    @GetMapping("/overall")
    public ApiResponse<OverallReportResponse> getOverallReport(
            @CurrentUserId Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baseDate) {

        return ApiResponse.success(overallReportService.getOverallReport(userId, baseDate));
    }

}
