package com.dashboard.controller;

import com.dashboard.dto.response.FlakyTestDto;
import com.dashboard.dto.response.SlowestTestDto;
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

@WebMvcTest(TestController.class)
class TestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TestRunService testRunService;

    // ── GET /tests/flaky ─────────────────────────────────────────────────────

    @Test
    void getFlakyTests_shouldReturnFlakyTests_whenTestsExist() throws Exception {
        // Given
        List<FlakyTestDto> flakyTests = buildFlakyTests();
        when(testRunService.getFlakyTests()).thenReturn(flakyTests);

        // When / Then
        mockMvc.perform(get("/tests/flaky"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].testId").value("flaky-001"))
                .andExpect(jsonPath("$[0].testName").value("Flaky Login Test"))
                .andExpect(jsonPath("$[0].suite").value("Smoke"))
                .andExpect(jsonPath("$[0].passCount").value(3))
                .andExpect(jsonPath("$[0].failCount").value(2));

        verify(testRunService).getFlakyTests();
    }

    @Test
    void getFlakyTests_shouldReturnEmptyArray_whenNoFlakyTestsExist() throws Exception {
        // Given
        when(testRunService.getFlakyTests()).thenReturn(new ArrayList<>());

        // When / Then
        mockMvc.perform(get("/tests/flaky"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        verify(testRunService).getFlakyTests();
    }

    @Test
    void getFlakyTests_shouldReturnAllFlakyTests_whenMultipleExist() throws Exception {
        // Given
        List<FlakyTestDto> flakyTests = List.of(
                new FlakyTestDto("t-1", "Test 1", "Smoke", 5, 3),
                new FlakyTestDto("t-2", "Test 2", "Regression", 4, 4),
                new FlakyTestDto("t-3", "Test 3", "Integration", 6, 2),
                new FlakyTestDto("t-4", "Test 4", "E2E", 3, 5)
        );
        when(testRunService.getFlakyTests()).thenReturn(flakyTests);

        // When / Then
        mockMvc.perform(get("/tests/flaky"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(4));

        verify(testRunService).getFlakyTests();
    }

    @Test
    void getFlakyTests_shouldIncludeAllProperties_whenReturningTests() throws Exception {
        // Given
        List<FlakyTestDto> flakyTests = List.of(
                new FlakyTestDto("test-id-1", "Full Test Name", "Test Suite", 10, 5)
        );
        when(testRunService.getFlakyTests()).thenReturn(flakyTests);

        // When / Then
        mockMvc.perform(get("/tests/flaky"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].testId").value("test-id-1"))
                .andExpect(jsonPath("$[0].testName").value("Full Test Name"))
                .andExpect(jsonPath("$[0].suite").value("Test Suite"))
                .andExpect(jsonPath("$[0].passCount").value(10))
                .andExpect(jsonPath("$[0].failCount").value(5));

        verify(testRunService).getFlakyTests();
    }

    @Test
    void getFlakyTests_shouldHandleMultipleRequests_whenCalledConcurrently() throws Exception {
        // Given
        List<FlakyTestDto> flakyTests = buildFlakyTests();
        when(testRunService.getFlakyTests()).thenReturn(flakyTests);

        // When / Then
        mockMvc.perform(get("/tests/flaky"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        mockMvc.perform(get("/tests/flaky"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        verify(testRunService, org.mockito.Mockito.times(2)).getFlakyTests();
    }

    // ── GET /tests/slowest ───────────────────────────────────────────────────

    @Test
    void getSlowestTests_shouldReturnSlowestTests_whenCalledWithDefaultLimit() throws Exception {
        // Given
        List<SlowestTestDto> slowestTests = buildSlowestTests(10);
        when(testRunService.getSlowestTests(10)).thenReturn(slowestTests);

        // When / Then
        mockMvc.perform(get("/tests/slowest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[0].testId").value("slow-0"))
                .andExpect(jsonPath("$[0].testName").value("Slowest Test 0"))
                .andExpect(jsonPath("$[0].suite").value("Performance"))
                .andExpect(jsonPath("$[0].durationMs").value(5000));

        verify(testRunService).getSlowestTests(10);
    }

    @Test
    void getSlowestTests_shouldReturnSlowestTests_whenCalledWithCustomLimit() throws Exception {
        // Given
        List<SlowestTestDto> slowestTests = buildSlowestTests(5);
        when(testRunService.getSlowestTests(5)).thenReturn(slowestTests);

        // When / Then
        mockMvc.perform(get("/tests/slowest")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(5));

        verify(testRunService).getSlowestTests(5);
    }

    @Test
    void getSlowestTests_shouldReturnEmptyArray_whenNoTestsExist() throws Exception {
        // Given
        when(testRunService.getSlowestTests(any())).thenReturn(new ArrayList<>());

        // When / Then
        mockMvc.perform(get("/tests/slowest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        verify(testRunService).getSlowestTests(10);
    }

    @Test
    void getSlowestTests_shouldReturn400_whenLimitIsZero() throws Exception {
        // When / Then
        mockMvc.perform(get("/tests/slowest")
                        .param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    void getSlowestTests_shouldReturn400_whenLimitIsNegative() throws Exception {
        // When / Then
        mockMvc.perform(get("/tests/slowest")
                        .param("limit", "-5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    void getSlowestTests_shouldReturn400_whenLimitExceedsMaximum() throws Exception {
        // When / Then
        mockMvc.perform(get("/tests/slowest")
                        .param("limit", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    void getSlowestTests_shouldAcceptMinimumLimit_whenLimitIsOne() throws Exception {
        // Given
        List<SlowestTestDto> slowestTests = buildSlowestTests(1);
        when(testRunService.getSlowestTests(1)).thenReturn(slowestTests);

        // When / Then
        mockMvc.perform(get("/tests/slowest")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1));

        verify(testRunService).getSlowestTests(1);
    }

    @Test
    void getSlowestTests_shouldAcceptMaximumLimit_whenLimitIs100() throws Exception {
        // Given
        List<SlowestTestDto> slowestTests = buildSlowestTests(100);
        when(testRunService.getSlowestTests(100)).thenReturn(slowestTests);

        // When / Then
        mockMvc.perform(get("/tests/slowest")
                        .param("limit", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(100));

        verify(testRunService).getSlowestTests(100);
    }

    @Test
    void getSlowestTests_shouldReturn400_whenLimitIsNotNumeric() throws Exception {
        // When / Then
        mockMvc.perform(get("/tests/slowest")
                        .param("limit", "invalid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getSlowestTests_shouldReturnTestsInOrder_whenMultipleTestsExist() throws Exception {
        // Given
        List<SlowestTestDto> slowestTests = List.of(
                new SlowestTestDto("t-1", "Very Slow Test", "Performance", 10000L),
                new SlowestTestDto("t-2", "Slow Test", "Performance", 8000L),
                new SlowestTestDto("t-3", "Medium Test", "Performance", 5000L),
                new SlowestTestDto("t-4", "Fast Test", "Performance", 3000L)
        );
        when(testRunService.getSlowestTests(4)).thenReturn(slowestTests);

        // When / Then
        mockMvc.perform(get("/tests/slowest")
                        .param("limit", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].durationMs").value(10000))
                .andExpect(jsonPath("$[1].durationMs").value(8000))
                .andExpect(jsonPath("$[2].durationMs").value(5000))
                .andExpect(jsonPath("$[3].durationMs").value(3000));

        verify(testRunService).getSlowestTests(4);
    }

    @Test
    void getSlowestTests_shouldIncludeAllProperties_whenReturningTests() throws Exception {
        // Given
        List<SlowestTestDto> slowestTests = List.of(
                new SlowestTestDto("test-id-slow", "Complete Test Name", "Integration Suite", 15000L)
        );
        when(testRunService.getSlowestTests(any())).thenReturn(slowestTests);

        // When / Then
        mockMvc.perform(get("/tests/slowest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].testId").value("test-id-slow"))
                .andExpect(jsonPath("$[0].testName").value("Complete Test Name"))
                .andExpect(jsonPath("$[0].suite").value("Integration Suite"))
                .andExpect(jsonPath("$[0].durationMs").value(15000));

        verify(testRunService).getSlowestTests(10);
    }

    @Test
    void getSlowestTests_shouldHandleMultipleRequests_whenCalledWithDifferentLimits() throws Exception {
        // Given
        List<SlowestTestDto> slowestTests5 = buildSlowestTests(5);
        List<SlowestTestDto> slowestTests10 = buildSlowestTests(10);

        when(testRunService.getSlowestTests(5)).thenReturn(slowestTests5);
        when(testRunService.getSlowestTests(10)).thenReturn(slowestTests10);

        // When / Then
        mockMvc.perform(get("/tests/slowest")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));

        mockMvc.perform(get("/tests/slowest")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10));

        verify(testRunService).getSlowestTests(5);
        verify(testRunService).getSlowestTests(10);
    }

    @Test
    void getSlowestTests_shouldHandleLargeLimit_whenWithinBounds() throws Exception {
        // Given
        List<SlowestTestDto> slowestTests = buildSlowestTests(50);
        when(testRunService.getSlowestTests(50)).thenReturn(slowestTests);

        // When / Then
        mockMvc.perform(get("/tests/slowest")
                        .param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(50));

        verify(testRunService).getSlowestTests(50);
    }

    @Test
    void getSlowestTests_shouldHandleSmallLimit_whenWithinBounds() throws Exception {
        // Given
        List<SlowestTestDto> slowestTests = buildSlowestTests(3);
        when(testRunService.getSlowestTests(3)).thenReturn(slowestTests);

        // When / Then
        mockMvc.perform(get("/tests/slowest")
                        .param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        verify(testRunService).getSlowestTests(3);
    }

    @Test
    void getSlowestTests_shouldReturnFewerTests_whenFewerExistThanLimit() throws Exception {
        // Given
        List<SlowestTestDto> slowestTests = buildSlowestTests(3);
        when(testRunService.getSlowestTests(10)).thenReturn(slowestTests);

        // When / Then
        mockMvc.perform(get("/tests/slowest")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        verify(testRunService).getSlowestTests(10);
    }

    // ── Helper Methods ───────────────────────────────────────────────────────

    private List<FlakyTestDto> buildFlakyTests() {
        return List.of(
                new FlakyTestDto("flaky-001", "Flaky Login Test", "Smoke", 3, 2),
                new FlakyTestDto("flaky-002", "Flaky Checkout Test", "E2E", 4, 3),
                new FlakyTestDto("flaky-003", "Flaky API Test", "Integration", 5, 1)
        );
    }

    private List<SlowestTestDto> buildSlowestTests(int count) {
        List<SlowestTestDto> tests = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            tests.add(new SlowestTestDto(
                    "slow-" + i,
                    "Slowest Test " + i,
                    "Performance",
                    5000L - (i * 100)
            ));
        }
        return tests;
    }
}
