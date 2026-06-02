package com.dashboard.service;

import com.dashboard.dto.request.TestResultRequest;
import com.dashboard.dto.request.TestRunRequest;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestRunServiceTest {

    @Mock private TestRunRepository testRunRepository;
    @Mock private TestResultRepository testResultRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks private TestRunService testRunService;

    // ── saveRun ──────────────────────────────────────────────────────────────

    @Test
    void saveRun_persistsRunWithTests() {
        when(testRunRepository.existsByRunId("Run-001")).thenReturn(false);
        when(testRunRepository.save(any(TestRun.class))).thenAnswer(inv -> inv.getArgument(0));

        testRunService.saveRun(buildRequest("Run-001"));

        verify(testRunRepository).save(any(TestRun.class));
    }

    @Test
    void saveRun_duplicateRunId_throwsIllegalArgument() {
        when(testRunRepository.existsByRunId("Run-DUP")).thenReturn(true);

        assertThatThrownBy(() -> testRunService.saveRun(buildRequest("Run-DUP")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Run-DUP");
    }

    @Test
    void saveRun_withCallbackUrl_triggersNotification() {
        when(testRunRepository.existsByRunId(any())).thenReturn(false);
        when(testRunRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TestRunRequest req = buildRequest("Run-CB");
        req.setCallbackUrl("http://localhost:9999/webhook");

        testRunService.saveRun(req);

        verify(notificationService).notifyRunCompleted(any(), eq("http://localhost:9999/webhook"));
    }

    @Test
    void saveRun_withoutCallbackUrl_noNotification() {
        when(testRunRepository.existsByRunId(any())).thenReturn(false);
        when(testRunRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        testRunService.saveRun(buildRequest("Run-NOCB"));

        verifyNoInteractions(notificationService);
    }

    // ── getRunSummary ────────────────────────────────────────────────────────

    @Test
    void getRunSummary_countsStatusesCorrectly() {
        TestRun run = buildRunWithResults("Run-S1");
        when(testRunRepository.findByRunId("Run-S1")).thenReturn(Optional.of(run));

        RunSummaryResponse summary = testRunService.getRunSummary("Run-S1");

        assertThat(summary.getTotal()).isEqualTo(3);
        assertThat(summary.getPassed()).isEqualTo(1);
        assertThat(summary.getFailed()).isEqualTo(1);
        assertThat(summary.getSkipped()).isEqualTo(1);
    }

    @Test
    void getRunSummary_passRateCalculated() {
        TestRun run = buildRunWithResults("Run-PR");
        when(testRunRepository.findByRunId("Run-PR")).thenReturn(Optional.of(run));

        RunSummaryResponse summary = testRunService.getRunSummary("Run-PR");

        assertThat(summary.getPassRate()).isEqualTo(33.33);
    }

    @Test
    void getRunSummary_unknownRunId_throwsNotFound() {
        when(testRunRepository.findByRunId("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> testRunService.getRunSummary("UNKNOWN"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getRunSummary_slowestTestsAreSorted() {
        TestRun run = buildRunWithDurations("Run-SL", 300L, 1500L, 100L, 800L, 500L, 250L);
        when(testRunRepository.findByRunId("Run-SL")).thenReturn(Optional.of(run));

        RunSummaryResponse summary = testRunService.getRunSummary("Run-SL");

        List<SlowestTestDto> slowest = summary.getSlowestTests();
        assertThat(slowest).hasSize(5);
        assertThat(slowest.get(0).getDurationMs()).isEqualTo(1500L);
    }

    // ── getFlakyTests ────────────────────────────────────────────────────────

    @Test
    void getFlakyTests_detectsFlakyTest() {
        when(testResultRepository.findAllWithRunOrderByStartedAt()).thenReturn(buildFlakyResults());

        List<FlakyTestDto> flaky = testRunService.getFlakyTests();

        assertThat(flaky).hasSize(1);
        assertThat(flaky.get(0).getTestId()).isEqualTo("flaky-001");
    }

    @Test
    void getFlakyTests_stableTestIsNotFlaky() {
        when(testResultRepository.findAllWithRunOrderByStartedAt()).thenReturn(buildStableResults());

        List<FlakyTestDto> flaky = testRunService.getFlakyTests();

        assertThat(flaky).isEmpty();
    }

    // ── getSlowestTests ──────────────────────────────────────────────────────

    @Test
    void getSlowestTests_returnsTopN() {
        when(testResultRepository.findAll()).thenReturn(buildResultsWithDurations(100L, 500L, 200L, 300L, 50L));

        List<SlowestTestDto> slowest = testRunService.getSlowestTests(3);

        assertThat(slowest).hasSize(3);
        assertThat(slowest.get(0).getDurationMs()).isEqualTo(500L);
        assertThat(slowest.get(1).getDurationMs()).isEqualTo(300L);
        assertThat(slowest.get(2).getDurationMs()).isEqualTo(200L);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private TestRunRequest buildRequest(String runId) {
        TestResultRequest tr = new TestResultRequest();
        tr.setTestId("t-001");
        tr.setTestName("Sample test");
        tr.setSuite("Smoke");
        tr.setStatus(TestStatus.PASSED);
        tr.setDurationMs(500L);

        TestRunRequest req = new TestRunRequest();
        req.setRunId(runId);
        req.setBranch("main");
        req.setEnvironment("test");
        req.setCommitHash("abc123");
        req.setStartedAt(LocalDateTime.now());
        req.setTests(List.of(tr));
        return req;
    }

    private TestRun buildRunWithResults(String runId) {
        TestRun run = new TestRun();
        run.setId(1L);
        run.setRunId(runId);
        run.setBranch("main");
        run.setEnvironment("test");
        run.setStartedAt(LocalDateTime.now());

        List<TestResult> results = new ArrayList<>();
        results.add(result("t-1", "Test 1", TestStatus.PASSED, "Smoke", 1000L, run));
        results.add(result("t-2", "Test 2", TestStatus.FAILED, "Smoke", 2000L, run, "Timeout"));
        results.add(result("t-3", "Test 3", TestStatus.SKIPPED, "Regression", 500L, run));
        run.setTests(results);
        return run;
    }

    private TestRun buildRunWithDurations(String runId, Long... durations) {
        TestRun run = new TestRun();
        run.setId(1L);
        run.setRunId(runId);
        run.setBranch("main");
        run.setEnvironment("test");
        run.setStartedAt(LocalDateTime.now());

        List<TestResult> results = new ArrayList<>();
        for (int i = 0; i < durations.length; i++) {
            results.add(result("t-" + i, "Test " + i, TestStatus.PASSED, "Suite", durations[i], run));
        }
        run.setTests(results);
        return run;
    }

    private List<TestResult> buildFlakyResults() {
        LocalDateTime base = LocalDateTime.now().minusDays(5);
        List<TestResult> all = new ArrayList<>();

        // flaky-001: 3 passes + 2 failures across 5 runs → flaky
        TestStatus[] statuses = {TestStatus.PASSED, TestStatus.FAILED, TestStatus.PASSED, TestStatus.FAILED, TestStatus.PASSED};
        for (int i = 0; i < 5; i++) {
            TestRun r = makeRun("Run-" + i, base.plusDays(i));
            all.add(result("flaky-001", "Flaky Login", statuses[i], "Smoke", 1000L, r));
        }
        // stable-002: always passes → not flaky
        for (int i = 0; i < 5; i++) {
            TestRun r = makeRun("Run-S" + i, base.plusDays(i));
            all.add(result("stable-002", "Stable Test", TestStatus.PASSED, "Smoke", 800L, r));
        }
        return all;
    }

    private List<TestResult> buildStableResults() {
        LocalDateTime base = LocalDateTime.now().minusDays(5);
        List<TestResult> all = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            TestRun r = makeRun("Run-" + i, base.plusDays(i));
            all.add(result("stable-001", "Always Passes", TestStatus.PASSED, "Smoke", 500L, r));
        }
        return all;
    }

    private List<TestResult> buildResultsWithDurations(Long... durations) {
        TestRun run = makeRun("Run-X", LocalDateTime.now());
        List<TestResult> list = new ArrayList<>();
        for (int i = 0; i < durations.length; i++) {
            list.add(result("t-" + i, "Test " + i, TestStatus.PASSED, "Suite", durations[i], run));
        }
        return list;
    }

    private TestRun makeRun(String runId, LocalDateTime startedAt) {
        TestRun r = new TestRun();
        r.setRunId(runId);
        r.setStartedAt(startedAt);
        r.setBranch("main");
        r.setEnvironment("test");
        r.setTests(new ArrayList<>());
        return r;
    }

    private TestResult result(String testId, String name, TestStatus status, String suite, long duration, TestRun run) {
        return result(testId, name, status, suite, duration, run, null);
    }

    private TestResult result(String testId, String name, TestStatus status, String suite, long duration, TestRun run, String error) {
        TestResult t = new TestResult();
        t.setTestId(testId);
        t.setTestName(name);
        t.setSuite(suite);
        t.setStatus(status);
        t.setDurationMs(duration);
        t.setErrorMessage(error);
        t.setTestRun(run);
        return t;
    }
}
