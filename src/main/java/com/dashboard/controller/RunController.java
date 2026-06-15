package com.dashboard.controller;

import com.dashboard.dto.request.TestRunRequest;
import com.dashboard.dto.response.FlakyTestDto;
import com.dashboard.dto.response.RunSummaryResponse;
import com.dashboard.service.HtmlReportService;
import com.dashboard.service.TestRunService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Validated
public class RunController {

    private final TestRunService testRunService;
    private final HtmlReportService htmlReportService;

    @GetMapping("/runs")
    public List<Map<String, Object>> listRuns(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return testRunService.listRecentRuns(limit);
    }

    @PostMapping("/runs")
    public ResponseEntity<Map<String, String>> saveRun(@Valid @RequestBody TestRunRequest request) {
        testRunService.saveRun(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("runId", request.getRunId(), "message", "Run saved successfully"));
    }

    @GetMapping("/runs/{runId}/summary")
    public RunSummaryResponse getRunSummary(@PathVariable String runId) {
        return testRunService.getRunSummary(runId);
    }

    @GetMapping(value = "/runs/{runId}/report", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getRunReport(@PathVariable String runId) {
        RunSummaryResponse summary = testRunService.getRunSummary(runId);
        List<FlakyTestDto> flaky = testRunService.getFlakyTests();
        String html = htmlReportService.generateReport(summary, flaky, false);
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }

    @GetMapping(value = "/runs/{runId}/download", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> downloadRunReport(@PathVariable String runId) {
        RunSummaryResponse summary = testRunService.getRunSummary(runId);
        List<FlakyTestDto> flaky = testRunService.getFlakyTests();
        String html = htmlReportService.generateReport(summary, flaky, true);
        String project  = summary.getProjectName() != null ? summary.getProjectName() : "report";
        String date     = summary.getStartedAt()   != null ? summary.getStartedAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")) : "unknown";
        String filename = project + "-" + date + ".html";
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(html);
    }

    @DeleteMapping("/runs/{runId}")
    public ResponseEntity<Void> deleteRun(@PathVariable String runId) {
        testRunService.deleteRun(runId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/runs/batch-delete")
    public ResponseEntity<Map<String, Object>> batchDeleteRuns(@RequestBody List<String> runIds) {
        int deleted = testRunService.bulkDeleteRuns(runIds);
        return ResponseEntity.ok(Map.of("deleted", deleted));
    }
}
