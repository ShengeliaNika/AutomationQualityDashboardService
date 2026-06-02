package com.dashboard.controller;

import com.dashboard.enums.TestStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RunControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void postRuns_validPayload_returns201() throws Exception {
        mockMvc.perform(post("/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRunPayload("Run-CTL-001"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.runId").value("Run-CTL-001"));
    }

    @Test
    void postRuns_duplicateRunId_returns400() throws Exception {
        Object payload = buildRunPayload("Run-CTL-DUP");

        mockMvc.perform(post("/runs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)));

        mockMvc.perform(post("/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("Run-CTL-DUP")));
    }

    @Test
    void postRuns_missingRequiredFields_returns400() throws Exception {
        Map<String, Object> incomplete = Map.of("runId", "");

        mockMvc.perform(post("/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(incomplete)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getRunSummary_existingRun_returnsCorrectCounts() throws Exception {
        mockMvc.perform(post("/runs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildRunPayload("Run-CTL-SUM"))));

        mockMvc.perform(get("/runs/Run-CTL-SUM/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value("Run-CTL-SUM"))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.passed").value(1))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.passRate").value(50.0));
    }

    @Test
    void getRunSummary_unknownRunId_returns404() throws Exception {
        mockMvc.perform(get("/runs/DOES-NOT-EXIST/summary"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void getDashboard_returnsOk() throws Exception {
        mockMvc.perform(post("/runs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildRunPayload("Run-CTL-DASH"))));

        mockMvc.perform(get("/dashboard").param("branch", "main"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branch").value("main"))
                .andExpect(jsonPath("$.totalRuns").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void getFlakyTests_returnsOkList() throws Exception {
        mockMvc.perform(get("/tests/flaky"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    void getSlowestTests_defaultLimit() throws Exception {
        mockMvc.perform(get("/tests/slowest"))
                .andExpect(status().isOk());
    }

    @Test
    void getSlowestTests_customLimit() throws Exception {
        mockMvc.perform(get("/tests/slowest").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(lessThanOrEqualTo(3)));
    }

    @Test
    void getRunReport_returnsHtml() throws Exception {
        mockMvc.perform(post("/runs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildRunPayload("Run-CTL-RPT"))));

        mockMvc.perform(get("/runs/Run-CTL-RPT/report"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Run-CTL-RPT")));
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private Map<String, Object> buildRunPayload(String runId) {
        return Map.of(
                "runId", runId,
                "branch", "main",
                "environment", "test",
                "commitHash", "abc123",
                "startedAt", LocalDateTime.now().toString(),
                "tests", List.of(
                        Map.of("testId", "t-1", "testName", "Login test", "suite", "Smoke",
                                "status", TestStatus.PASSED.name(), "durationMs", 1200),
                        Map.of("testId", "t-2", "testName", "Logout test", "suite", "Smoke",
                                "status", TestStatus.FAILED.name(), "durationMs", 800,
                                "errorMessage", "Element not found")
                )
        );
    }
}
