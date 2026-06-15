package com.dashboard.controller;

import com.dashboard.dto.response.ProjectSummaryDto;
import com.dashboard.service.HtmlReportService;
import com.dashboard.service.TestRunService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Validated
public class ProjectController {

    private final TestRunService testRunService;
    private final HtmlReportService htmlReportService;

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> mainDashboard() {
        List<ProjectSummaryDto> projects = testRunService.getProjectsSummary();
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(htmlReportService.generateMainDashboard(projects));
    }

    @GetMapping("/projects")
    public List<ProjectSummaryDto> listProjects() {
        return testRunService.getProjectsSummary();
    }

    @GetMapping(value = "/projects/{projectName}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> projectPage(
            @PathVariable String projectName,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        List<Map<String, Object>> runs  = testRunService.getProjectRuns(projectName, limit);
        List<Map<String, Object>> flaky = testRunService.getProjectFlakyTests(projectName, 10);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(htmlReportService.generateProjectPage(projectName, runs, flaky));
    }

    @GetMapping("/projects/{projectName}/flaky")
    public List<Map<String, Object>> projectFlakyTests(
            @PathVariable String projectName,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int lastN) {
        return testRunService.getProjectFlakyTests(projectName, lastN);
    }

    @GetMapping(value = "/projects/{projectName}/combined", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> combinedReport(
            @PathVariable String projectName,
            @RequestParam List<String> runIds) {
        Map<String, Object> report = testRunService.getCombinedReport(runIds);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(htmlReportService.generateCombinedReport(projectName, report));
    }
}
