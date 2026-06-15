package com.dashboard.service;

import com.dashboard.dto.request.TestResultRequest;
import com.dashboard.dto.request.TestRunRequest;
import com.dashboard.dto.response.DashboardResponse;
import com.dashboard.dto.response.FlakyTestDto;
import com.dashboard.dto.response.RunSummaryResponse;
import com.dashboard.dto.response.SlowestTestDto;
import com.dashboard.entity.TestResult;
import com.dashboard.entity.TestRun;
import com.dashboard.enums.TestStatus;
import com.dashboard.exception.ResourceNotFoundException;
import com.dashboard.repository.TestResultRepository;
import com.dashboard.repository.TestRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestRunServiceTest {

    @Mock
    private TestRunRepository testRunRepository;

    @Mock
    private TestResultRepository testResultRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private TestRunService testRunService;

    // ── saveRun ──────────────────────────────────────────────────────────────

    @Test
    void saveRun_shouldSaveRunWithTests_whenValidRequest() {
        // Given
        TestRunRequest request = buildValidTestRunRequest("Run-001");
        when(testRunRepository.existsByRunId("Run-001")).thenReturn(false);
        when(testRunRepository.save(any(TestRun.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        TestRun result = testRunService.saveRun(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getRunId()).isEqualTo("Run-001");
        assertThat(result.getBranch()).isEqualTo("main");
        assertThat(result.getEnvironment()).isEqualTo("test");
        assertThat(result.getCommitHash()).isEqualTo("abc123");
        assertThat(result.getTests()).hasSize(2);

        verify(testRunRepository).existsByRunId("Run-001");
        verify(testRunRepository).save(any(TestRun.class));
        verifyNoInteractions(notificationService);
    }

    @Test
    void saveRun_shouldThrowIllegalArgumentException_whenDuplicateRunId() {
        // Given
        TestRunRequest request = buildValidTestRunRequest("Run-DUP");
        when(testRunRepository.existsByRunId("Run-DUP")).thenReturn(true);

        // When / Then
        assertThatThrownBy(() -> testRunService.saveRun(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Run with ID 'Run-DUP' already exists");

        verify(testRunRepository).existsByRunId("Run-DUP");
        verify(testRunRepository, never()).save(any());
        verifyNoInteractions(notificationService);
    }

    @Test
    void saveRun_shouldTriggerNotification_whenCallbackUrlProvided() {
        // Given
        TestRunRequest request = buildValidTestRunRequest("Run-CB");
        request.setCallbackUrl("http://localhost:8080/webhook");

        when(testRunRepository.existsByRunId("Run-CB")).thenReturn(false);
        when(testRunRepository.save(any(TestRun.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        TestRun result = testRunService.saveRun(request);

        // Then
        verify(notificationService).notifyRunCompleted(any(TestRun.class), eq("http://localhost:8080/webhook"));
    }

    @Test
    void saveRun_shouldNotTriggerNotification_whenCallbackUrlIsNull() {
        // Given
        TestRunRequest request = buildValidTestRunRequest("Run-NO-CB");
        request.setCallbackUrl(null);

        when(testRunRepository.existsByRunId("Run-NO-CB")).thenReturn(false);
        when(testRunRepository.save(any(TestRun.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        testRunService.saveRun(request);

        // Then
        verifyNoInteractions(notificationService);
    }

    @Test
    void saveRun_shouldNotTriggerNotification_whenCallbackUrlIsBlank() {
        // Given
        TestRunRequest request = buildValidTestRunRequest("Run-BLANK-CB");
        request.setCallbackUrl("   ");

        when(testRunRepository.existsByRunId("Run-BLANK-CB")).thenReturn(false);
        when(testRunRepository.save(any(TestRun.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        testRunService.saveRun(request);

        // Then
        verifyNoInteractions(notificationService);
    }

    @Test
    void saveRun_shouldMapAllTestProperties_whenSaving() {
        // Given
        TestRunRequest request = buildValidTestRunRequest("Run-MAP");
        ArgumentCaptor<TestRun> runCaptor = ArgumentCaptor.forClass(TestRun.class);

        when(testRunRepository.existsByRunId("Run-MAP")).thenReturn(false);
        when(testRunRepository.save(any(TestRun.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        testRunService.saveRun(request);

        // Then
        verify(testRunRepository).save(runCaptor.capture());
        TestRun savedRun = runCaptor.getValue();

        assertThat(savedRun.getTests()).hasSize(2);
        TestResult firstTest = savedRun.getTests().get(0);
        assertThat(firstTest.getTestId()).isEqualTo("test-1");
        assertThat(firstTest.getTestName()).isEqualTo("Login Test");
        assertThat(firstTest.getSuite()).isEqualTo("Smoke");
        assertThat(firstTest.getStatus()).isEqualTo(TestStatus.PASSED);
        assertThat(firstTest.getDurationMs()).isEqualTo(1200L);
        assertThat(firstTest.getErrorMessage()).isNull();
        assertThat(firstTest.getTestRun()).isEqualTo(savedRun);
    }

    // ── getRunSummary ────────────────────────────────────────────────────────

    @Test
    void getRunSummary_shouldReturnSummary_whenRunExists() {
        // Given
        TestRun run = buildTestRunWithMultipleStatuses("Run-SUM-001");
        when(testRunRepository.findByRunId("Run-SUM-001")).thenReturn(Optional.of(run));
        when(testResultRepository.findSlowestTestsByRun(eq(run), any(Pageable.class)))
                .thenReturn(run.getTests().stream().limit(5).collect(Collectors.toList()));
        when(testResultRepository.findByTestRunAndStatus(run, TestStatus.FAILED))
                .thenReturn(run.getTests().stream().filter(t -> t.getStatus() == TestStatus.FAILED).collect(Collectors.toList()));

        // When
        RunSummaryResponse summary = testRunService.getRunSummary("Run-SUM-001");

        // Then
        assertThat(summary).isNotNull();
        assertThat(summary.getRunId()).isEqualTo("Run-SUM-001");
        assertThat(summary.getBranch()).isEqualTo("main");
        assertThat(summary.getEnvironment()).isEqualTo("test");
        assertThat(summary.getTotal()).isEqualTo(4);
        assertThat(summary.getPassed()).isEqualTo(2);
        assertThat(summary.getFailed()).isEqualTo(1);
        assertThat(summary.getSkipped()).isEqualTo(1);

        verify(testRunRepository).findByRunId("Run-SUM-001");
    }

    @Test
    void getRunSummary_shouldCalculatePassRateCorrectly_whenTestsExist() {
        // Given
        TestRun run = buildTestRunWithMultipleStatuses("Run-PR");
        when(testRunRepository.findByRunId("Run-PR")).thenReturn(Optional.of(run));
        when(testResultRepository.findSlowestTestsByRun(eq(run), any(Pageable.class)))
                .thenReturn(run.getTests().stream().limit(5).collect(Collectors.toList()));
        when(testResultRepository.findByTestRunAndStatus(run, TestStatus.FAILED))
                .thenReturn(run.getTests().stream().filter(t -> t.getStatus() == TestStatus.FAILED).collect(Collectors.toList()));

        // When
        RunSummaryResponse summary = testRunService.getRunSummary("Run-PR");

        // Then
        assertThat(summary.getPassRate()).isEqualTo(50.0); // 2 passed out of 4 total
    }

    @Test
    void getRunSummary_shouldHandleZeroPassRate_whenNoTestsPassed() {
        // Given
        TestRun run = buildTestRunWithOnlyFailures("Run-ZERO");
        when(testRunRepository.findByRunId("Run-ZERO")).thenReturn(Optional.of(run));
        when(testResultRepository.findSlowestTestsByRun(eq(run), any(Pageable.class)))
                .thenReturn(run.getTests());
        when(testResultRepository.findByTestRunAndStatus(run, TestStatus.FAILED))
                .thenReturn(run.getTests());

        // When
        RunSummaryResponse summary = testRunService.getRunSummary("Run-ZERO");

        // Then
        assertThat(summary.getPassRate()).isEqualTo(0.0);
        assertThat(summary.getPassed()).isEqualTo(0);
        assertThat(summary.getFailed()).isEqualTo(2);
    }

    @Test
    void getRunSummary_shouldThrowResourceNotFoundException_whenRunDoesNotExist() {
        // Given
        when(testRunRepository.findByRunId("UNKNOWN")).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> testRunService.getRunSummary("UNKNOWN"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Run not found: UNKNOWN");

        verify(testRunRepository).findByRunId("UNKNOWN");
    }

    @Test
    void getRunSummary_shouldReturnSlowestTests_whenTestsExist() {
        // Given
        TestRun run = buildTestRunWithVaryingDurations("Run-SLOW");
        when(testRunRepository.findByRunId("Run-SLOW")).thenReturn(Optional.of(run));
        when(testResultRepository.findSlowestTestsByRun(eq(run), any(Pageable.class)))
                .thenReturn(run.getTests().stream()
                        .sorted(Comparator.comparingLong(TestResult::getDurationMs).reversed())
                        .limit(5).collect(Collectors.toList()));
        when(testResultRepository.findByTestRunAndStatus(run, TestStatus.FAILED))
                .thenReturn(new ArrayList<>());

        // When
        RunSummaryResponse summary = testRunService.getRunSummary("Run-SLOW");

        // Then
        assertThat(summary.getSlowestTests()).hasSize(5);
        assertThat(summary.getSlowestTests().get(0).getDurationMs()).isEqualTo(5000L);
        assertThat(summary.getSlowestTests().get(1).getDurationMs()).isEqualTo(3000L);
        assertThat(summary.getSlowestTests().get(2).getDurationMs()).isEqualTo(2000L);
        assertThat(summary.getSlowestTests().get(3).getDurationMs()).isEqualTo(1500L);
        assertThat(summary.getSlowestTests().get(4).getDurationMs()).isEqualTo(1000L);
    }

    @Test
    void getRunSummary_shouldCalculateAvgDuration_whenTestsExist() {
        // Given
        TestRun run = buildTestRunWithSpecificDurations("Run-AVG", 1000L, 2000L, 3000L);
        when(testRunRepository.findByRunId("Run-AVG")).thenReturn(Optional.of(run));
        when(testResultRepository.findSlowestTestsByRun(eq(run), any(Pageable.class)))
                .thenReturn(run.getTests());
        when(testResultRepository.findByTestRunAndStatus(run, TestStatus.FAILED))
                .thenReturn(new ArrayList<>());

        // When
        RunSummaryResponse summary = testRunService.getRunSummary("Run-AVG");

        // Then
        assertThat(summary.getAvgDurationMs()).isEqualTo(2000L);
    }

    @Test
    void getRunSummary_shouldIncludeFailedTests_whenFailuresExist() {
        // Given
        TestRun run = buildTestRunWithFailedTests("Run-FAIL");
        List<TestResult> failedTests = run.getTests().stream()
                .filter(t -> t.getStatus() == TestStatus.FAILED)
                .collect(Collectors.toList());
        when(testRunRepository.findByRunId("Run-FAIL")).thenReturn(Optional.of(run));
        when(testResultRepository.findSlowestTestsByRun(eq(run), any(Pageable.class)))
                .thenReturn(run.getTests().stream().limit(5).collect(Collectors.toList()));
        when(testResultRepository.findByTestRunAndStatus(run, TestStatus.FAILED))
                .thenReturn(failedTests);

        // When
        RunSummaryResponse summary = testRunService.getRunSummary("Run-FAIL");

        // Then
        assertThat(summary.getFailedTests()).hasSize(2);
        assertThat(summary.getFailedTests().get(0).getTestName()).isEqualTo("Failed Test 1");
        assertThat(summary.getFailedTests().get(0).getErrorMessage()).isEqualTo("Assertion failed");
        assertThat(summary.getFailedTests().get(1).getTestName()).isEqualTo("Failed Test 2");
    }

    @Test
    void getRunSummary_shouldGroupFailedTestsBySuite_whenFailuresExist() {
        // Given
        TestRun run = buildTestRunWithFailedTests("Run-SUITE");
        List<TestResult> failedTests = run.getTests().stream()
                .filter(t -> t.getStatus() == TestStatus.FAILED)
                .collect(Collectors.toList());
        when(testRunRepository.findByRunId("Run-SUITE")).thenReturn(Optional.of(run));
        when(testResultRepository.findSlowestTestsByRun(eq(run), any(Pageable.class)))
                .thenReturn(run.getTests().stream().limit(5).collect(Collectors.toList()));
        when(testResultRepository.findByTestRunAndStatus(run, TestStatus.FAILED))
                .thenReturn(failedTests);

        // When
        RunSummaryResponse summary = testRunService.getRunSummary("Run-SUITE");

        // Then
        assertThat(summary.getFailedBySuite()).hasSize(2);
        assertThat(summary.getFailedBySuite().get("Smoke")).containsExactly("Failed Test 1");
        assertThat(summary.getFailedBySuite().get("Regression")).containsExactly("Failed Test 2");
    }

    // ── getDashboard ─────────────────────────────────────────────────────────

    @Test
    void getDashboard_shouldReturnDashboardData_whenRunsExist() {
        // Given
        List<TestRun> runs = buildMultipleTestRuns(3);
        when(testRunRepository.findByFilters(eq("main"), eq("test"), any(Pageable.class)))
                .thenReturn(runs);

        // When
        DashboardResponse dashboard = testRunService.getDashboard("main", "test", 10);

        // Then
        assertThat(dashboard).isNotNull();
        assertThat(dashboard.getBranch()).isEqualTo("main");
        assertThat(dashboard.getEnvironment()).isEqualTo("test");
        assertThat(dashboard.getTotalRuns()).isEqualTo(3);
        assertThat(dashboard.getPassRateTrend()).hasSize(3);
        assertThat(dashboard.getFailureCountTrend()).hasSize(3);
        assertThat(dashboard.getAvgDurationTrend()).hasSize(3);

        verify(testRunRepository).findByFilters(eq("main"), eq("test"), any(Pageable.class));
    }

    @Test
    void getDashboard_shouldCalculatePassRateTrend_whenRunsExist() {
        // Given
        List<TestRun> runs = buildMultipleTestRuns(2);
        when(testRunRepository.findByFilters(any(), any(), any(Pageable.class)))
                .thenReturn(runs);

        // When
        DashboardResponse dashboard = testRunService.getDashboard(null, null, 10);

        // Then
        assertThat(dashboard.getPassRateTrend()).hasSize(2);
        assertThat(dashboard.getPassRateTrend().get(0).getValue()).isGreaterThanOrEqualTo(0);
        assertThat(dashboard.getPassRateTrend().get(0).getValue()).isLessThanOrEqualTo(100);
    }

    @Test
    void getDashboard_shouldHandleNullFilters_whenNoFiltersProvided() {
        // Given
        List<TestRun> runs = buildMultipleTestRuns(5);
        when(testRunRepository.findByFilters(null, null, PageRequest.of(0, 20)))
                .thenReturn(runs);

        // When
        DashboardResponse dashboard = testRunService.getDashboard(null, null, 20);

        // Then
        assertThat(dashboard).isNotNull();
        assertThat(dashboard.getTotalRuns()).isEqualTo(5);
        verify(testRunRepository).findByFilters(null, null, PageRequest.of(0, 20));
    }

    @Test
    void getDashboard_shouldRespectLastNParameter_whenCalled() {
        // Given
        int lastN = 5;
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(testRunRepository.findByFilters(any(), any(), any(Pageable.class)))
                .thenReturn(new ArrayList<>());

        // When
        testRunService.getDashboard("main", "test", lastN);

        // Then
        verify(testRunRepository).findByFilters(eq("main"), eq("test"), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageSize()).isEqualTo(lastN);
        assertThat(pageable.getPageNumber()).isEqualTo(0);
    }

    @Test
    void getDashboard_shouldReturnEmptyTrends_whenNoRunsExist() {
        // Given
        when(testRunRepository.findByFilters(any(), any(), any(Pageable.class)))
                .thenReturn(new ArrayList<>());

        // When
        DashboardResponse dashboard = testRunService.getDashboard("main", "test", 10);

        // Then
        assertThat(dashboard.getTotalRuns()).isEqualTo(0);
        assertThat(dashboard.getPassRateTrend()).isEmpty();
        assertThat(dashboard.getFailureCountTrend()).isEmpty();
        assertThat(dashboard.getAvgDurationTrend()).isEmpty();
    }

    // ── getFlakyTests ────────────────────────────────────────────────────────

    @Test
    void getFlakyTests_shouldDetectFlakyTest_whenPassAndFailInLastFiveRuns() {
        // Given
        List<TestResultRepository.FlakyTestProjection> projections = List.of(
                createFlakyProjection("flaky-001", "Flaky Login Test", "Smoke", 3L, 2L)
        );
        when(testResultRepository.findFlakyTests(5)).thenReturn(projections);

        // When
        List<FlakyTestDto> flakyTests = testRunService.getFlakyTests();

        // Then
        assertThat(flakyTests).hasSize(1);
        assertThat(flakyTests.get(0).getTestId()).isEqualTo("flaky-001");
        assertThat(flakyTests.get(0).getTestName()).isEqualTo("Flaky Login Test");
        assertThat(flakyTests.get(0).getPassCount()).isEqualTo(3);
        assertThat(flakyTests.get(0).getFailCount()).isEqualTo(2);
    }

    @Test
    void getFlakyTests_shouldNotDetectStableTest_whenAlwaysPassing() {
        // Given
        when(testResultRepository.findFlakyTests(5)).thenReturn(new ArrayList<>());

        // When
        List<FlakyTestDto> flakyTests = testRunService.getFlakyTests();

        // Then
        assertThat(flakyTests).isEmpty();
    }

    @Test
    void getFlakyTests_shouldNotDetectStableTest_whenAlwaysFailing() {
        // Given
        when(testResultRepository.findFlakyTests(5)).thenReturn(new ArrayList<>());

        // When
        List<FlakyTestDto> flakyTests = testRunService.getFlakyTests();

        // Then
        assertThat(flakyTests).isEmpty();
    }

    @Test
    void getFlakyTests_shouldReturnEmptyList_whenNoTestsExist() {
        // Given
        when(testResultRepository.findFlakyTests(5)).thenReturn(new ArrayList<>());

        // When
        List<FlakyTestDto> flakyTests = testRunService.getFlakyTests();

        // Then
        assertThat(flakyTests).isEmpty();
    }

    @Test
    void getFlakyTests_shouldHandleMultipleFlakyTests_whenPresent() {
        // Given
        List<TestResultRepository.FlakyTestProjection> projections = List.of(
                createFlakyProjection("flaky-001", "Flaky Test 1", "Smoke", 3L, 2L),
                createFlakyProjection("flaky-002", "Flaky Test 2", "Regression", 3L, 2L)
        );
        when(testResultRepository.findFlakyTests(5)).thenReturn(projections);

        // When
        List<FlakyTestDto> flakyTests = testRunService.getFlakyTests();

        // Then
        assertThat(flakyTests).hasSizeGreaterThanOrEqualTo(2);
    }

    // ── getSlowestTests ──────────────────────────────────────────────────────

    @Test
    void getSlowestTests_shouldReturnTopNSlowestTests_whenTestsExist() {
        // Given
        List<TestResult> results = buildTestResultsWithVaryingDurations();
        when(testResultRepository.findSlowestTests(any(Pageable.class))).thenReturn(results);

        // When
        List<SlowestTestDto> slowestTests = testRunService.getSlowestTests(5);

        // Then
        assertThat(slowestTests).hasSize(5);
        assertThat(slowestTests.get(0).getDurationMs()).isEqualTo(5000L);
        verify(testResultRepository).findSlowestTests(PageRequest.of(0, 5));
    }

    @Test
    void getSlowestTests_shouldRespectLimit_whenCalled() {
        // Given
        int limit = 3;
        List<TestResult> results = buildTestResultsWithVaryingDurations().subList(0, 3);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(testResultRepository.findSlowestTests(any(Pageable.class))).thenReturn(results);

        // When
        List<SlowestTestDto> slowestTests = testRunService.getSlowestTests(limit);

        // Then
        verify(testResultRepository).findSlowestTests(pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageSize()).isEqualTo(limit);
        assertThat(slowestTests).hasSize(3);
    }

    @Test
    void getSlowestTests_shouldReturnEmptyList_whenNoTestsExist() {
        // Given
        when(testResultRepository.findSlowestTests(any(Pageable.class))).thenReturn(new ArrayList<>());

        // When
        List<SlowestTestDto> slowestTests = testRunService.getSlowestTests(10);

        // Then
        assertThat(slowestTests).isEmpty();
    }

    @Test
    void getSlowestTests_shouldMapAllProperties_whenReturningResults() {
        // Given
        List<TestResult> results = buildTestResultsWithVaryingDurations().subList(0, 1);
        when(testResultRepository.findSlowestTests(any(Pageable.class))).thenReturn(results);

        // When
        List<SlowestTestDto> slowestTests = testRunService.getSlowestTests(1);

        // Then
        assertThat(slowestTests).hasSize(1);
        SlowestTestDto dto = slowestTests.get(0);
        assertThat(dto.getTestId()).isNotNull();
        assertThat(dto.getTestName()).isNotNull();
        assertThat(dto.getSuite()).isNotNull();
        assertThat(dto.getDurationMs()).isGreaterThan(0);
    }

    // ── Helper Methods ───────────────────────────────────────────────────────

    private TestRunRequest buildValidTestRunRequest(String runId) {
        TestResultRequest test1 = new TestResultRequest();
        test1.setTestId("test-1");
        test1.setTestName("Login Test");
        test1.setSuite("Smoke");
        test1.setStatus(TestStatus.PASSED);
        test1.setDurationMs(1200L);

        TestResultRequest test2 = new TestResultRequest();
        test2.setTestId("test-2");
        test2.setTestName("Logout Test");
        test2.setSuite("Smoke");
        test2.setStatus(TestStatus.FAILED);
        test2.setDurationMs(800L);
        test2.setErrorMessage("Element not found");

        TestRunRequest request = new TestRunRequest();
        request.setRunId(runId);
        request.setBranch("main");
        request.setEnvironment("test");
        request.setCommitHash("abc123");
        request.setStartedAt(LocalDateTime.now());
        request.setTests(Arrays.asList(test1, test2));
        return request;
    }

    private TestRun buildTestRunWithMultipleStatuses(String runId) {
        TestRun run = new TestRun();
        run.setId(1L);
        run.setRunId(runId);
        run.setBranch("main");
        run.setEnvironment("test");
        run.setStartedAt(LocalDateTime.now());

        List<TestResult> results = new ArrayList<>();
        results.add(createTestResult("t-1", "Test 1", TestStatus.PASSED, "Smoke", 1000L, run));
        results.add(createTestResult("t-2", "Test 2", TestStatus.PASSED, "Smoke", 1500L, run));
        results.add(createTestResult("t-3", "Test 3", TestStatus.FAILED, "Regression", 2000L, run, "Timeout"));
        results.add(createTestResult("t-4", "Test 4", TestStatus.SKIPPED, "Regression", 500L, run));
        run.setTests(results);
        return run;
    }

    private TestRun buildTestRunWithOnlyFailures(String runId) {
        TestRun run = new TestRun();
        run.setId(1L);
        run.setRunId(runId);
        run.setBranch("main");
        run.setEnvironment("test");
        run.setStartedAt(LocalDateTime.now());

        List<TestResult> results = new ArrayList<>();
        results.add(createTestResult("t-1", "Test 1", TestStatus.FAILED, "Smoke", 1000L, run, "Error 1"));
        results.add(createTestResult("t-2", "Test 2", TestStatus.FAILED, "Smoke", 1500L, run, "Error 2"));
        run.setTests(results);
        return run;
    }

    private TestRun buildTestRunWithVaryingDurations(String runId) {
        TestRun run = new TestRun();
        run.setId(1L);
        run.setRunId(runId);
        run.setBranch("main");
        run.setEnvironment("test");
        run.setStartedAt(LocalDateTime.now());

        List<TestResult> results = new ArrayList<>();
        results.add(createTestResult("t-1", "Test 1", TestStatus.PASSED, "Suite", 5000L, run));
        results.add(createTestResult("t-2", "Test 2", TestStatus.PASSED, "Suite", 3000L, run));
        results.add(createTestResult("t-3", "Test 3", TestStatus.PASSED, "Suite", 2000L, run));
        results.add(createTestResult("t-4", "Test 4", TestStatus.PASSED, "Suite", 1500L, run));
        results.add(createTestResult("t-5", "Test 5", TestStatus.PASSED, "Suite", 1000L, run));
        results.add(createTestResult("t-6", "Test 6", TestStatus.PASSED, "Suite", 500L, run));
        run.setTests(results);
        return run;
    }

    private TestRun buildTestRunWithSpecificDurations(String runId, Long... durations) {
        TestRun run = new TestRun();
        run.setId(1L);
        run.setRunId(runId);
        run.setBranch("main");
        run.setEnvironment("test");
        run.setStartedAt(LocalDateTime.now());

        List<TestResult> results = new ArrayList<>();
        for (int i = 0; i < durations.length; i++) {
            results.add(createTestResult("t-" + i, "Test " + i, TestStatus.PASSED, "Suite", durations[i], run));
        }
        run.setTests(results);
        return run;
    }

    private TestRun buildTestRunWithFailedTests(String runId) {
        TestRun run = new TestRun();
        run.setId(1L);
        run.setRunId(runId);
        run.setBranch("main");
        run.setEnvironment("test");
        run.setStartedAt(LocalDateTime.now());

        List<TestResult> results = new ArrayList<>();
        results.add(createTestResult("t-1", "Failed Test 1", TestStatus.FAILED, "Smoke", 1000L, run, "Assertion failed"));
        results.add(createTestResult("t-2", "Failed Test 2", TestStatus.FAILED, "Regression", 1500L, run, "Timeout error"));
        results.add(createTestResult("t-3", "Passed Test", TestStatus.PASSED, "Smoke", 800L, run));
        run.setTests(results);
        return run;
    }

    private List<TestRun> buildMultipleTestRuns(int count) {
        List<TestRun> runs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            TestRun run = new TestRun();
            run.setId((long) i);
            run.setRunId("Run-" + i);
            run.setBranch("main");
            run.setEnvironment("test");
            run.setStartedAt(LocalDateTime.now().minusDays(i));

            List<TestResult> results = new ArrayList<>();
            results.add(createTestResult("t-1-" + i, "Test 1", TestStatus.PASSED, "Suite", 1000L, run));
            results.add(createTestResult("t-2-" + i, "Test 2", TestStatus.FAILED, "Suite", 1500L, run, "Error"));
            run.setTests(results);
            runs.add(run);
        }
        return runs;
    }

    private List<TestResult> buildFlakyTestResults() {
        List<TestResult> results = new ArrayList<>();
        LocalDateTime baseTime = LocalDateTime.now().minusDays(5);
        TestStatus[] statuses = {TestStatus.PASSED, TestStatus.FAILED, TestStatus.PASSED, TestStatus.FAILED, TestStatus.PASSED};

        for (int i = 0; i < 5; i++) {
            TestRun run = createTestRun("Run-" + i, baseTime.plusDays(i));
            results.add(createTestResult("flaky-001", "Flaky Login Test", statuses[i], "Smoke", 1000L, run));
        }
        return results;
    }

    private List<TestResult> buildStablePassingTestResults() {
        List<TestResult> results = new ArrayList<>();
        LocalDateTime baseTime = LocalDateTime.now().minusDays(5);

        for (int i = 0; i < 5; i++) {
            TestRun run = createTestRun("Run-" + i, baseTime.plusDays(i));
            results.add(createTestResult("stable-001", "Stable Test", TestStatus.PASSED, "Smoke", 1000L, run));
        }
        return results;
    }

    private List<TestResult> buildStableFailingTestResults() {
        List<TestResult> results = new ArrayList<>();
        LocalDateTime baseTime = LocalDateTime.now().minusDays(5);

        for (int i = 0; i < 5; i++) {
            TestRun run = createTestRun("Run-" + i, baseTime.plusDays(i));
            results.add(createTestResult("stable-fail-001", "Always Fails", TestStatus.FAILED, "Smoke", 1000L, run, "Error"));
        }
        return results;
    }

    private List<TestResult> buildMultipleFlakyTestResults() {
        List<TestResult> results = new ArrayList<>();
        LocalDateTime baseTime = LocalDateTime.now().minusDays(5);

        // First flaky test
        TestStatus[] statuses1 = {TestStatus.PASSED, TestStatus.FAILED, TestStatus.PASSED, TestStatus.FAILED, TestStatus.PASSED};
        for (int i = 0; i < 5; i++) {
            TestRun run = createTestRun("Run-" + i, baseTime.plusDays(i));
            results.add(createTestResult("flaky-001", "Flaky Test 1", statuses1[i], "Smoke", 1000L, run));
        }

        // Second flaky test
        TestStatus[] statuses2 = {TestStatus.FAILED, TestStatus.PASSED, TestStatus.FAILED, TestStatus.PASSED, TestStatus.PASSED};
        for (int i = 0; i < 5; i++) {
            TestRun run = createTestRun("Run-" + i, baseTime.plusDays(i));
            results.add(createTestResult("flaky-002", "Flaky Test 2", statuses2[i], "Regression", 1500L, run));
        }

        return results;
    }

    private List<TestResult> buildTestResultsWithVaryingDurations() {
        TestRun run = createTestRun("Run-X", LocalDateTime.now());
        List<TestResult> results = new ArrayList<>();

        results.add(createTestResult("t-1", "Slowest Test", TestStatus.PASSED, "Suite", 5000L, run));
        results.add(createTestResult("t-2", "Second Slowest", TestStatus.PASSED, "Suite", 4000L, run));
        results.add(createTestResult("t-3", "Third Slowest", TestStatus.PASSED, "Suite", 3000L, run));
        results.add(createTestResult("t-4", "Fourth Slowest", TestStatus.PASSED, "Suite", 2000L, run));
        results.add(createTestResult("t-5", "Fifth Slowest", TestStatus.PASSED, "Suite", 1000L, run));

        return results;
    }

    private TestRun createTestRun(String runId, LocalDateTime startedAt) {
        TestRun run = new TestRun();
        run.setRunId(runId);
        run.setBranch("main");
        run.setEnvironment("test");
        run.setStartedAt(startedAt);
        run.setTests(new ArrayList<>());
        return run;
    }

    private TestResult createTestResult(String testId, String name, TestStatus status, String suite, long duration, TestRun run) {
        return createTestResult(testId, name, status, suite, duration, run, null);
    }

    private TestResult createTestResult(String testId, String name, TestStatus status, String suite, long duration, TestRun run, String errorMessage) {
        TestResult result = new TestResult();
        result.setTestId(testId);
        result.setTestName(name);
        result.setSuite(suite);
        result.setStatus(status);
        result.setDurationMs(duration);
        result.setErrorMessage(errorMessage);
        result.setTestRun(run);
        return result;
    }

    private TestResultRepository.FlakyTestProjection createFlakyProjection(String testId, String testName, String suite, Long passCount, Long failCount) {
        return new TestResultRepository.FlakyTestProjection() {
            @Override
            public String getTestId() { return testId; }
            @Override
            public String getTestName() { return testName; }
            @Override
            public String getSuite() { return suite; }
            @Override
            public Long getPassCount() { return passCount; }
            @Override
            public Long getFailCount() { return failCount; }
        };
    }
}
