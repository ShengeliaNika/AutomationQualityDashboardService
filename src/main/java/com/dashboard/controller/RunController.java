package com.dashboard.controller;

import com.dashboard.dto.request.TestRunRequest;
import com.dashboard.dto.response.FlakyTestDto;
import com.dashboard.dto.response.RunSummaryResponse;
import com.dashboard.service.HtmlReportService;
import com.dashboard.service.TestRunService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class RunController {

    private final TestRunService testRunService;
    private final HtmlReportService htmlReportService;

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
        String html = htmlReportService.generateReport(summary, flaky);
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }
}
