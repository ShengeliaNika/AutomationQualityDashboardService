package com.dashboard.controller;

import com.dashboard.dto.response.FlakyTestDto;
import com.dashboard.dto.response.RunSummaryResponse;
import com.dashboard.entity.TestRun;
import com.dashboard.enums.TestStatus;
import com.dashboard.exception.ResourceNotFoundException;
import com.dashboard.service.HtmlReportService;
import com.dashboard.service.TestRunService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RunController.class)
class RunControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TestRunService testRunService;

    @MockBean
    private HtmlReportService htmlReportService;

    // ── POST /runs ───────────────────────────────────────────────────────────

    @Test
    void saveRun_shouldReturn201_whenValidRequest() throws Exception {
        // Given
        Map<String, Object> payload = buildValidRunPayload("Run-001");
        when(testRunService.saveRun(any())).thenReturn(new TestRun());

        // When / Then
        mockMvc.perform(post("/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.runId").value("Run-001"))
                .andExpect(jsonPath("$.message").value("Run saved successfully"));

        verify(testRunService).saveRun(any());
    }

    @Test
    void saveRun_shouldReturn400_whenRunIdIsBlank() throws Exception {
        // Given
        Map<String, Object> payload = buildValidRunPayload("");

        // When / Then
        mockMvc.perform(post("/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());

        verify(testRunService, never()).saveRun(any());
    }

    @Test
    void saveRun_shouldReturn400_whenBranchIsMissing() throws Exception {
        // Given
        Map<String, Object> payload = buildValidRunPayload("Run-001");
        payload.remove("branch");

        // When / Then
        mockMvc.perform(post("/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());

        verify(testRunService, never()).saveRun(any());
    }

    @Test
    void saveRun_shouldReturn400_whenEnvironmentIsMissing() throws Exception {
        // Given
        Map<String, Object> payload = buildValidRunPayload("Run-001");
        payload.remove("environment");

        // When / Then
        mockMvc.perform(post("/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());

        verify(testRunService, never()).saveRun(any());
    }

    @Test
    void saveRun_shouldReturn400_whenCommitHashIsMissing() throws Exception {
        // Given
        Map<String, Object> payload = buildValidRunPayload("Run-001");
        payload.remove("commitHash");

        // When / Then
        mockMvc.perform(post("/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());

        verify(testRunService, never()).saveRun(any());
    }

    @Test
    void saveRun_shouldReturn400_whenStartedAtIsMissing() throws Exception {
        // Given
        Map<String, Object> payload = buildValidRunPayload("Run-001");
        payload.remove("startedAt");

        // When / Then
        mockMvc.perform(post("/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());

        verify(testRunService, never()).saveRun(any());
    }

    @Test
    void saveRun_shouldReturn400_whenTestsListIsEmpty() throws Exception {
        // Given
        Map<String, Object> payload = buildValidRunPayload("Run-001");
        payload.put("tests", List.of());

        // When / Then
        mockMvc.perform(post("/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());

        verify(testRunService, never()).saveRun(any());
    }

    @Test
    void saveRun_shouldReturn400_whenTestsListIsNull() throws Exception {
        // Given
        Map<String, Object> payload = buildValidRunPayload("Run-001");
        payload.put("tests", null);

        // When / Then
        mockMvc.perform(post("/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());

        verify(testRunService, never()).saveRun(any());
    }

    @Test
    void saveRun_shouldReturn400_whenTestHasMissingFields() throws Exception {
        // Given
        Map<String, Object> payload = buildValidRunPayload("Run-001");
        List<Map<String, Object>> tests = List.of(
                Map.of("testId", "t-1", "testName", "Test 1") // Missing suite, status, durationMs
        );
        payload.put("tests", tests);

        // When / Then
        mockMvc.perform(post("/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());

        verify(testRunService, never()).saveRun(any());
    }

    @Test
    void saveRun_shouldReturn400_whenDuplicateRunId() throws Exception {
        // Given
        Map<String, Object> payload = buildValidRunPayload("Run-DUP");
        when(testRunService.saveRun(any()))
                .thenThrow(new IllegalArgumentException("Run with ID 'Run-DUP' already exists"));

        // When / Then
        mockMvc.perform(post("/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("Run-DUP")));

        verify(testRunService).saveRun(any());
    }

    @Test
    void saveRun_shouldAcceptCallbackUrl_whenProvided() throws Exception {
        // Given
        Map<String, Object> payload = buildValidRunPayload("Run-CB");
        payload.put("callbackUrl", "http://localhost:8080/webhook");
        when(testRunService.saveRun(any())).thenReturn(new TestRun());

        // When / Then
        mockMvc.perform(post("/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated());

        verify(testRunService).saveRun(any());
    }

    @Test
    void saveRun_shouldAcceptMultipleTests_whenProvided() throws Exception {
        // Given
        Map<String, Object> payload = buildValidRunPayload("Run-MULTI");
        List<Map<String, Object>> tests = List.of(
                buildTestPayload("t-1", "Test 1", "Smoke", TestStatus.PASSED, 1000L),
                buildTestPayload("t-2", "Test 2", "Smoke", TestStatus.FAILED, 1500L),
                buildTestPayload("t-3", "Test 3", "Regression", TestStatus.SKIPPED, 500L)
        );
        payload.put("tests", tests);
        when(testRunService.saveRun(any())).thenReturn(new TestRun());

        // When / Then
        mockMvc.perform(post("/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated());

        verify(testRunService).saveRun(any());
    }

    // ── GET /runs/{runId}/summary ────────────────────────────────────────────

    @Test
    void getRunSummary_shouldReturnSummary_whenRunExists() throws Exception {
        // Given
        RunSummaryResponse summary = buildRunSummary("Run-001");
        when(testRunService.getRunSummary("Run-001")).thenReturn(summary);

        // When / Then
        mockMvc.perform(get("/runs/Run-001/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value("Run-001"))
                .andExpect(jsonPath("$.branch").value("main"))
                .andExpect(jsonPath("$.environment").value("test"))
                .andExpect(jsonPath("$.total").value(10))
                .andExpect(jsonPath("$.passed").value(7))
                .andExpect(jsonPath("$.failed").value(2))
                .andExpect(jsonPath("$.skipped").value(1))
                .andExpect(jsonPath("$.passRate").value(70.0));

        verify(testRunService).getRunSummary("Run-001");
    }

    @Test
    void getRunSummary_shouldReturn404_whenRunNotFound() throws Exception {
        // Given
        when(testRunService.getRunSummary("UNKNOWN"))
                .thenThrow(new ResourceNotFoundException("Run not found: UNKNOWN"));

        // When / Then
        mockMvc.perform(get("/runs/UNKNOWN/summary"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(containsString("Run not found")));

        verify(testRunService).getRunSummary("UNKNOWN");
    }

    @Test
    void getRunSummary_shouldHandleSpecialCharacters_whenRunIdContainsSpecialChars() throws Exception {
        // Given
        String runId = "Run-2024-01-01_12:30";
        RunSummaryResponse summary = buildRunSummary(runId);
        when(testRunService.getRunSummary(runId)).thenReturn(summary);

        // When / Then
        mockMvc.perform(get("/runs/" + runId + "/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(runId));

        verify(testRunService).getRunSummary(runId);
    }

    // ── GET /runs/{runId}/report ─────────────────────────────────────────────

    @Test
    void getRunReport_shouldReturnHtmlReport_whenRunExists() throws Exception {
        // Given
        RunSummaryResponse summary = buildRunSummary("Run-RPT");
        List<FlakyTestDto> flakyTests = new ArrayList<>();
        String htmlReport = "<html><body>Test Report</body></html>";

        when(testRunService.getRunSummary("Run-RPT")).thenReturn(summary);
        when(testRunService.getFlakyTests()).thenReturn(flakyTests);
        when(htmlReportService.generateReport(summary, flakyTests)).thenReturn(htmlReport);

        // When / Then
        mockMvc.perform(get("/runs/Run-RPT/report"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Test Report")));

        verify(testRunService).getRunSummary("Run-RPT");
        verify(testRunService).getFlakyTests();
        verify(htmlReportService).generateReport(summary, flakyTests);
    }

    @Test
    void getRunReport_shouldReturn404_whenRunNotFound() throws Exception {
        // Given
        when(testRunService.getRunSummary("UNKNOWN"))
                .thenThrow(new ResourceNotFoundException("Run not found: UNKNOWN"));

        // When / Then
        mockMvc.perform(get("/runs/UNKNOWN/report"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(containsString("Run not found")));

        verify(testRunService).getRunSummary("UNKNOWN");
        verify(testRunService, never()).getFlakyTests();
        verify(htmlReportService, never()).generateReport(any(), any());
    }

    @Test
    void getRunReport_shouldIncludeFlakyTests_whenGeneratingReport() throws Exception {
        // Given
        RunSummaryResponse summary = buildRunSummary("Run-FLAKY");
        List<FlakyTestDto> flakyTests = List.of(
                new FlakyTestDto("t-1", "Flaky Test 1", "Smoke", 3, 2),
                new FlakyTestDto("t-2", "Flaky Test 2", "Regression", 4, 1)
        );
        String htmlReport = "<html><body>Report with flaky tests</body></html>";

        when(testRunService.getRunSummary("Run-FLAKY")).thenReturn(summary);
        when(testRunService.getFlakyTests()).thenReturn(flakyTests);
        when(htmlReportService.generateReport(summary, flakyTests)).thenReturn(htmlReport);

        // When / Then
        mockMvc.perform(get("/runs/Run-FLAKY/report"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_HTML));

        verify(htmlReportService).generateReport(summary, flakyTests);
    }

    @Test
    void getRunReport_shouldHandleEmptyFlakyTests_whenNoFlakyTestsExist() throws Exception {
        // Given
        RunSummaryResponse summary = buildRunSummary("Run-NO-FLAKY");
        List<FlakyTestDto> flakyTests = new ArrayList<>();
        String htmlReport = "<html><body>Report without flaky tests</body></html>";

        when(testRunService.getRunSummary("Run-NO-FLAKY")).thenReturn(summary);
        when(testRunService.getFlakyTests()).thenReturn(flakyTests);
        when(htmlReportService.generateReport(summary, flakyTests)).thenReturn(htmlReport);

        // When / Then
        mockMvc.perform(get("/runs/Run-NO-FLAKY/report"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_HTML));

        verify(htmlReportService).generateReport(summary, flakyTests);
    }

    // ── Helper Methods ───────────────────────────────────────────────────────

    private Map<String, Object> buildValidRunPayload(String runId) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("runId", runId);
        payload.put("branch", "main");
        payload.put("environment", "test");
        payload.put("commitHash", "abc123");
        payload.put("startedAt", LocalDateTime.now().toString());
        payload.put("tests", new java.util.ArrayList<>(List.of(
                buildTestPayload("t-1", "Login Test", "Smoke", TestStatus.PASSED, 1200L),
                buildTestPayload("t-2", "Logout Test", "Smoke", TestStatus.FAILED, 800L)
        )));
        return payload;
    }

    private Map<String, Object> buildTestPayload(String testId, String testName, String suite,
                                                   TestStatus status, Long durationMs) {
        Map<String, Object> test = new java.util.HashMap<>();
        test.put("testId", testId);
        test.put("testName", testName);
        test.put("suite", suite);
        test.put("status", status.name());
        test.put("durationMs", durationMs);
        if (status == TestStatus.FAILED) {
            test.put("errorMessage", "Test failed");
        }
        return test;
    }

    private RunSummaryResponse buildRunSummary(String runId) {
        return RunSummaryResponse.builder()
                .runId(runId)
                .branch("main")
                .environment("test")
                .startedAt(LocalDateTime.now())
                .total(10)
                .passed(7)
                .failed(2)
                .skipped(1)
                .passRate(70.0)
                .avgDurationMs(1500L)
                .slowestTests(new ArrayList<>())
                .failedTests(new ArrayList<>())
                .failedBySuite(Map.of())
                .build();
    }
}
