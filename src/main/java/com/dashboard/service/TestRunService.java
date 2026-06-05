package com.dashboard.service;

import com.dashboard.dto.request.TestRunRequest;
import com.dashboard.dto.response.*;
import com.dashboard.entity.TestResult;
import com.dashboard.entity.TestRun;
import com.dashboard.enums.TestStatus;
import com.dashboard.exception.ResourceNotFoundException;
import com.dashboard.repository.TestResultRepository;
import com.dashboard.repository.TestRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TestRunService {

    private static final int TOP_SLOWEST_TESTS_LIMIT = 5;
    private static final int FLAKY_TEST_WINDOW = 5;

    private final TestRunRepository testRunRepository;
    private final TestResultRepository testResultRepository;
    private final NotificationService notificationService;

    @Transactional
    public TestRun saveRun(TestRunRequest request) {
        if (testRunRepository.existsByRunId(request.getRunId())) {
            throw new IllegalArgumentException("Run with ID '" + request.getRunId() + "' already exists");
        }

        TestRun run = TestRun.builder()
                .runId(request.getRunId())
                .branch(request.getBranch())
                .environment(request.getEnvironment())
                .commitHash(request.getCommitHash())
                .startedAt(request.getStartedAt())
                .build();

        List<TestResult> results = request.getTests().stream()
                .map(t -> TestResult.builder()
                        .testId(t.getTestId())
                        .testName(t.getTestName())
                        .suite(t.getSuite())
                        .status(t.getStatus())
                        .durationMs(t.getDurationMs())
                        .errorMessage(t.getErrorMessage())
                        .testRun(run)
                        .build())
                .collect(Collectors.toList());

        run.setTests(results);
        TestRun saved = testRunRepository.save(run);

        if (request.getCallbackUrl() != null && !request.getCallbackUrl().isBlank()) {
            notificationService.notifyRunCompleted(saved, request.getCallbackUrl());
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public RunSummaryResponse getRunSummary(String runId) {
        TestRun run = testRunRepository.findByRunId(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Run not found: " + runId));
        return buildSummary(run);
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(String branch, String environment, int lastN) {
        Pageable pageable = PageRequest.of(0, lastN);
        List<TestRun> runs = testRunRepository.findByFilters(branch, environment, pageable);

        List<TrendPoint> passRateTrend = runs.stream()
                .map(r -> {
                    long total = r.getTests().size();
                    long passed = countByStatus(r.getTests(), TestStatus.PASSED);
                    double rate = total > 0 ? (double) passed / total * 100 : 0;
                    return new TrendPoint(r.getRunId(), round2(rate));
                }).collect(Collectors.toList());

        List<TrendPoint> failureTrend = runs.stream()
                .map(r -> new TrendPoint(r.getRunId(),
                        (double) countByStatus(r.getTests(), TestStatus.FAILED)))
                .collect(Collectors.toList());

        List<TrendPoint> durationTrend = runs.stream()
                .map(r -> new TrendPoint(r.getRunId(),
                        r.getTests().stream().mapToLong(TestResult::getDurationMs).average().orElse(0)))
                .collect(Collectors.toList());

        return DashboardResponse.builder()
                .branch(branch)
                .environment(environment)
                .totalRuns(runs.size())
                .passRateTrend(passRateTrend)
                .failureCountTrend(failureTrend)
                .avgDurationTrend(durationTrend)
                .build();
    }

    @Transactional(readOnly = true)
    public List<FlakyTestDto> getFlakyTests() {
        return testResultRepository.findFlakyTests(FLAKY_TEST_WINDOW).stream()
                .map(projection -> new FlakyTestDto(
                        projection.getTestId(),
                        projection.getTestName(),
                        projection.getSuite(),
                        projection.getPassCount().intValue(),
                        projection.getFailCount().intValue()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SlowestTestDto> getSlowestTests(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return testResultRepository.findSlowestTests(pageable).stream()
                .map(t -> new SlowestTestDto(t.getTestId(), t.getTestName(), t.getSuite(), t.getDurationMs()))
                .collect(Collectors.toList());
    }

    private RunSummaryResponse buildSummary(TestRun run) {
        List<TestResult> tests = run.getTests();
        int total = tests.size();
        int passed = (int) countByStatus(tests, TestStatus.PASSED);
        int failed = (int) countByStatus(tests, TestStatus.FAILED);
        int skipped = (int) countByStatus(tests, TestStatus.SKIPPED);
        double passRate = total > 0 ? round2((double) passed / total * 100) : 0;
        long avgDuration = (long) tests.stream().mapToLong(TestResult::getDurationMs).average().orElse(0);

        // Fetch slowest tests from repository with database-level sorting
        Pageable topN = PageRequest.of(0, TOP_SLOWEST_TESTS_LIMIT);
        List<SlowestTestDto> slowest = testResultRepository.findSlowestTestsByRun(run, topN).stream()
                .map(t -> new SlowestTestDto(t.getTestId(), t.getTestName(), t.getSuite(), t.getDurationMs()))
                .collect(Collectors.toList());

        // Fetch only failed tests from repository with database-level filtering
        List<TestResult> failedTestResults = testResultRepository.findByTestRunAndStatus(run, TestStatus.FAILED);
        List<FailedTestDetail> failedTests = failedTestResults.stream()
                .map(t -> new FailedTestDetail(t.getTestId(), t.getTestName(), t.getSuite(),
                        t.getErrorMessage(), t.getDurationMs()))
                .collect(Collectors.toList());

        Map<String, List<String>> failedBySuite = failedTestResults.stream()
                .collect(Collectors.groupingBy(
                        TestResult::getSuite,
                        Collectors.mapping(TestResult::getTestName, Collectors.toList())));

        return RunSummaryResponse.builder()
                .runId(run.getRunId())
                .branch(run.getBranch())
                .environment(run.getEnvironment())
                .startedAt(run.getStartedAt())
                .total(total)
                .passed(passed)
                .failed(failed)
                .skipped(skipped)
                .passRate(passRate)
                .avgDurationMs(avgDuration)
                .slowestTests(slowest)
                .failedTests(failedTests)
                .failedBySuite(failedBySuite)
                .build();
    }

    private long countByStatus(List<TestResult> tests, TestStatus status) {
        return tests.stream().filter(t -> t.getStatus() == status).count();
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
