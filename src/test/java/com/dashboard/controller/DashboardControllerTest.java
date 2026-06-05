package com.dashboard.controller;

import com.dashboard.dto.response.DashboardResponse;
import com.dashboard.dto.response.TrendPoint;
import com.dashboard.service.TestRunService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TestRunService testRunService;

    // ── GET /dashboard ───────────────────────────────────────────────────────

    @Test
    void getDashboard_shouldReturnDashboard_whenCalledWithDefaultParameters() throws Exception {
        // Given
        DashboardResponse dashboard = buildDashboard("main", "test", 3);
        when(testRunService.getDashboard(null, null, 10))
                .thenReturn(dashboard);

        // When / Then
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRuns").value(3))
                .andExpect(jsonPath("$.passRateTrend").isArray())
                .andExpect(jsonPath("$.failureCountTrend").isArray())
                .andExpect(jsonPath("$.avgDurationTrend").isArray());

        verify(testRunService).getDashboard(null, null, 10);
    }

    @Test
    void getDashboard_shouldFilterByBranch_whenBranchProvided() throws Exception {
        // Given
        DashboardResponse dashboard = buildDashboard("main", null, 5);
        when(testRunService.getDashboard(eq("main"), eq(null), eq(10)))
                .thenReturn(dashboard);

        // When / Then
        mockMvc.perform(get("/dashboard")
                        .param("branch", "main"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branch").value("main"))
                .andExpect(jsonPath("$.totalRuns").value(5));

        verify(testRunService).getDashboard("main", null, 10);
    }

    @Test
    void getDashboard_shouldFilterByEnvironment_whenEnvironmentProvided() throws Exception {
        // Given
        DashboardResponse dashboard = buildDashboard(null, "production", 5);
        when(testRunService.getDashboard(eq(null), eq("production"), eq(10)))
                .thenReturn(dashboard);

        // When / Then
        mockMvc.perform(get("/dashboard")
                        .param("environment", "production"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.environment").value("production"))
                .andExpect(jsonPath("$.totalRuns").value(5));

        verify(testRunService).getDashboard(null, "production", 10);
    }

    @Test
    void getDashboard_shouldFilterByBoth_whenBranchAndEnvironmentProvided() throws Exception {
        // Given
        DashboardResponse dashboard = buildDashboard("develop", "staging", 7);
        when(testRunService.getDashboard(eq("develop"), eq("staging"), eq(10)))
                .thenReturn(dashboard);

        // When / Then
        mockMvc.perform(get("/dashboard")
                        .param("branch", "develop")
                        .param("environment", "staging"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branch").value("develop"))
                .andExpect(jsonPath("$.environment").value("staging"))
                .andExpect(jsonPath("$.totalRuns").value(7));

        verify(testRunService).getDashboard("develop", "staging", 10);
    }

    @Test
    void getDashboard_shouldRespectLastNParameter_whenProvided() throws Exception {
        // Given
        DashboardResponse dashboard = buildDashboard("main", "test", 20);
        when(testRunService.getDashboard(eq("main"), eq("test"), eq(20)))
                .thenReturn(dashboard);

        // When / Then
        mockMvc.perform(get("/dashboard")
                        .param("branch", "main")
                        .param("environment", "test")
                        .param("lastN", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRuns").value(20));

        verify(testRunService).getDashboard("main", "test", 20);
    }

    @Test
    void getDashboard_shouldUseDefaultLastN_whenNotProvided() throws Exception {
        // Given
        DashboardResponse dashboard = buildDashboard("main", "test", 10);
        when(testRunService.getDashboard(any(), any(), eq(10)))
                .thenReturn(dashboard);

        // When / Then
        mockMvc.perform(get("/dashboard")
                        .param("branch", "main"))
                .andExpect(status().isOk());

        verify(testRunService).getDashboard("main", null, 10);
    }

    @Test
    void getDashboard_shouldReturn400_whenLastNIsZero() throws Exception {
        // When / Then
        mockMvc.perform(get("/dashboard")
                        .param("lastN", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    void getDashboard_shouldReturn400_whenLastNIsNegative() throws Exception {
        // When / Then
        mockMvc.perform(get("/dashboard")
                        .param("lastN", "-5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    void getDashboard_shouldReturn400_whenLastNExceedsMaximum() throws Exception {
        // When / Then
        mockMvc.perform(get("/dashboard")
                        .param("lastN", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    void getDashboard_shouldAcceptMinimumLastN_whenLastNIsOne() throws Exception {
        // Given
        DashboardResponse dashboard = buildDashboard("main", "test", 1);
        when(testRunService.getDashboard(any(), any(), eq(1)))
                .thenReturn(dashboard);

        // When / Then
        mockMvc.perform(get("/dashboard")
                        .param("lastN", "1"))
                .andExpect(status().isOk());

        verify(testRunService).getDashboard(null, null, 1);
    }

    @Test
    void getDashboard_shouldAcceptMaximumLastN_whenLastNIs100() throws Exception {
        // Given
        DashboardResponse dashboard = buildDashboard("main", "test", 100);
        when(testRunService.getDashboard(any(), any(), eq(100)))
                .thenReturn(dashboard);

        // When / Then
        mockMvc.perform(get("/dashboard")
                        .param("lastN", "100"))
                .andExpect(status().isOk());

        verify(testRunService).getDashboard(null, null, 100);
    }

    @Test
    void getDashboard_shouldReturnEmptyDashboard_whenNoRunsExist() throws Exception {
        // Given
        DashboardResponse dashboard = buildEmptyDashboard();
        when(testRunService.getDashboard(any(), any(), any()))
                .thenReturn(dashboard);

        // When / Then
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRuns").value(0))
                .andExpect(jsonPath("$.passRateTrend").isEmpty())
                .andExpect(jsonPath("$.failureCountTrend").isEmpty())
                .andExpect(jsonPath("$.avgDurationTrend").isEmpty());
    }

    @Test
    void getDashboard_shouldReturnTrendData_whenRunsExist() throws Exception {
        // Given
        DashboardResponse dashboard = buildDashboardWithTrends();
        when(testRunService.getDashboard(any(), any(), any()))
                .thenReturn(dashboard);

        // When / Then
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRuns").value(3))
                .andExpect(jsonPath("$.passRateTrend").isArray())
                .andExpect(jsonPath("$.passRateTrend.length()").value(3))
                .andExpect(jsonPath("$.passRateTrend[0].runId").value("Run-1"))
                .andExpect(jsonPath("$.passRateTrend[0].value").value(85.5))
                .andExpect(jsonPath("$.failureCountTrend").isArray())
                .andExpect(jsonPath("$.failureCountTrend.length()").value(3))
                .andExpect(jsonPath("$.avgDurationTrend").isArray())
                .andExpect(jsonPath("$.avgDurationTrend.length()").value(3));
    }

    @Test
    void getDashboard_shouldHandleSpecialCharactersInBranch_whenProvided() throws Exception {
        // Given
        String branch = "feature/JIRA-123_test-branch";
        DashboardResponse dashboard = buildDashboard(branch, "test", 5);
        when(testRunService.getDashboard(eq(branch), any(), any()))
                .thenReturn(dashboard);

        // When / Then
        mockMvc.perform(get("/dashboard")
                        .param("branch", branch))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branch").value(branch));

        verify(testRunService).getDashboard(branch, null, 10);
    }

    @Test
    void getDashboard_shouldHandleSpacesInEnvironment_whenProvided() throws Exception {
        // Given
        String environment = "qa environment";
        DashboardResponse dashboard = buildDashboard("main", environment, 5);
        when(testRunService.getDashboard(any(), eq(environment), any()))
                .thenReturn(dashboard);

        // When / Then
        mockMvc.perform(get("/dashboard")
                        .param("environment", environment))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.environment").value(environment));

        verify(testRunService).getDashboard(null, environment, 10);
    }

    @Test
    void getDashboard_shouldReturn400_whenLastNIsNotNumeric() throws Exception {
        // When / Then
        mockMvc.perform(get("/dashboard")
                        .param("lastN", "invalid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getDashboard_shouldHandleMultipleRequests_whenCalledConcurrently() throws Exception {
        // Given
        DashboardResponse dashboard1 = buildDashboard("main", "test", 5);
        DashboardResponse dashboard2 = buildDashboard("develop", "staging", 3);

        when(testRunService.getDashboard(eq("main"), eq("test"), eq(10)))
                .thenReturn(dashboard1);
        when(testRunService.getDashboard(eq("develop"), eq("staging"), eq(10)))
                .thenReturn(dashboard2);

        // When / Then
        mockMvc.perform(get("/dashboard")
                        .param("branch", "main")
                        .param("environment", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branch").value("main"));

        mockMvc.perform(get("/dashboard")
                        .param("branch", "develop")
                        .param("environment", "staging"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branch").value("develop"));
    }

    @Test
    void getDashboard_shouldHandleNullBranchAndEnvironment_whenNotProvided() throws Exception {
        // Given
        DashboardResponse dashboard = buildDashboard(null, null, 10);
        when(testRunService.getDashboard(null, null, 10))
                .thenReturn(dashboard);

        // When / Then
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRuns").value(10));

        verify(testRunService).getDashboard(null, null, 10);
    }

    // ── Helper Methods ───────────────────────────────────────────────────────

    private DashboardResponse buildDashboard(String branch, String environment, int totalRuns) {
        List<TrendPoint> passRateTrend = new ArrayList<>();
        List<TrendPoint> failureCountTrend = new ArrayList<>();
        List<TrendPoint> avgDurationTrend = new ArrayList<>();

        for (int i = 0; i < totalRuns; i++) {
            passRateTrend.add(new TrendPoint("Run-" + i, 80.0 + i));
            failureCountTrend.add(new TrendPoint("Run-" + i, (double) (2 + i)));
            avgDurationTrend.add(new TrendPoint("Run-" + i, 1500.0 + (i * 100)));
        }

        return DashboardResponse.builder()
                .branch(branch)
                .environment(environment)
                .totalRuns(totalRuns)
                .passRateTrend(passRateTrend)
                .failureCountTrend(failureCountTrend)
                .avgDurationTrend(avgDurationTrend)
                .build();
    }

    private DashboardResponse buildEmptyDashboard() {
        return DashboardResponse.builder()
                .branch(null)
                .environment(null)
                .totalRuns(0)
                .passRateTrend(new ArrayList<>())
                .failureCountTrend(new ArrayList<>())
                .avgDurationTrend(new ArrayList<>())
                .build();
    }

    private DashboardResponse buildDashboardWithTrends() {
        List<TrendPoint> passRateTrend = List.of(
                new TrendPoint("Run-1", 85.5),
                new TrendPoint("Run-2", 90.0),
                new TrendPoint("Run-3", 87.5)
        );

        List<TrendPoint> failureCountTrend = List.of(
                new TrendPoint("Run-1", 3.0),
                new TrendPoint("Run-2", 2.0),
                new TrendPoint("Run-3", 2.5)
        );

        List<TrendPoint> avgDurationTrend = List.of(
                new TrendPoint("Run-1", 1500.0),
                new TrendPoint("Run-2", 1450.0),
                new TrendPoint("Run-3", 1480.0)
        );

        return DashboardResponse.builder()
                .branch("main")
                .environment("test")
                .totalRuns(3)
                .passRateTrend(passRateTrend)
                .failureCountTrend(failureCountTrend)
                .avgDurationTrend(avgDurationTrend)
                .build();
    }
}
