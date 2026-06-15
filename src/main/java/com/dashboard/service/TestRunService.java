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

import java.time.LocalDateTime;
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
    private final ClickUpService clickUpService;

    @Transactional
    public TestRun saveRun(TestRunRequest request) {
        if (testRunRepository.existsByRunId(request.getRunId())) {
            throw new IllegalArgumentException("Run with ID '" + request.getRunId() + "' already exists");
        }

        TestRun run = TestRun.builder()
                .runId(request.getRunId())
                .projectName(request.getProjectName() != null && !request.getProjectName().isBlank()
                        ? request.getProjectName() : "unknown")
                .targetUrl(request.getTargetUrl())
                .username(request.getUsername())
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
                        .errorDetails(t.getErrorDetails())
                        .requestUrl(t.getRequestUrl())
                        .requestMethod(t.getRequestMethod())
                        .requestBody(truncate(t.getRequestBody(), 5000))
                        .responseStatus(t.getResponseStatus())
                        .responseBody(truncate(t.getResponseBody(), 5000))
                        .testRun(run)
                        .build())
                .collect(Collectors.toList());

        run.setTests(results);
        TestRun saved = testRunRepository.save(run);

        if (request.getCallbackUrl() != null && !request.getCallbackUrl().isBlank()) {
            notificationService.notifyRunCompleted(saved, request.getCallbackUrl());
        }

        List<TestResult> failedTests = saved.getTests().stream()
                .filter(t -> t.getStatus() == TestStatus.FAILED)
                .collect(Collectors.toList());
        clickUpService.reportFailedRun(saved, failedTests);

        return saved;
    }

    @Transactional
    public void deleteRun(String runId) {
        TestRun run = testRunRepository.findByRunId(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Run not found: " + runId));
        testRunRepository.delete(run);
    }

    @Transactional
    public int bulkDeleteRuns(List<String> runIds) {
        List<TestRun> runs = runIds.stream()
                .map(id -> testRunRepository.findByRunId(id).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        testRunRepository.deleteAll(runs);
        return runs.size();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getProjectFlakyTests(String projectName, int lastNRuns) {
        List<TestRun> runs = testRunRepository.findByProjectName(projectName, PageRequest.of(0, lastNRuns));

        Map<String, List<TestResult>> byTest = runs.stream()
                .flatMap(r -> r.getTests().stream())
                .collect(Collectors.groupingBy(TestResult::getTestId));

        return byTest.entrySet().stream()
                .filter(e -> e.getValue().size() >= 2)
                .filter(e -> e.getValue().stream().anyMatch(t -> t.getStatus() == TestStatus.FAILED))
                .map(e -> {
                    List<TestResult> results = e.getValue();
                    long failCount = results.stream().filter(t -> t.getStatus() == TestStatus.FAILED).count();
                    long passCount = results.stream().filter(t -> t.getStatus() == TestStatus.PASSED).count();
                    double failRate = round2((double) failCount / results.size() * 100);
                    TestResult sample = results.get(0);
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("testId",      sample.getTestId());
                    m.put("testName",    sample.getTestName());
                    m.put("suite",       sample.getSuite());
                    m.put("totalRuns",   results.size());
                    m.put("passed",      passCount);
                    m.put("failed",      failCount);
                    m.put("failureRate", failRate);
                    return m;
                })
                .sorted((a, b) -> Double.compare((Double) b.get("failureRate"), (Double) a.get("failureRate")))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getCombinedReport(List<String> runIds) {
        List<TestRun> runs = runIds.stream()
                .map(id -> testRunRepository.findByRunId(id).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Map<String, List<TestResult>> byTest = runs.stream()
                .flatMap(r -> r.getTests().stream())
                .collect(Collectors.groupingBy(TestResult::getTestId));

        long totalExecutions = byTest.values().stream().mapToLong(List::size).sum();
        long totalPassed     = byTest.values().stream().flatMap(List::stream)
                .filter(t -> t.getStatus() == TestStatus.PASSED).count();
        double overallPassRate = totalExecutions > 0
                ? round2((double) totalPassed / totalExecutions * 100) : 0;

        List<Map<String, Object>> tests = byTest.entrySet().stream().map(e -> {
            List<TestResult> results = e.getValue();
            long passed  = results.stream().filter(t -> t.getStatus() == TestStatus.PASSED).count();
            long failed  = results.stream().filter(t -> t.getStatus() == TestStatus.FAILED).count();
            long avgDur  = (long) results.stream()
                    .mapToLong(t -> t.getDurationMs() != null ? t.getDurationMs() : 0).average().orElse(0);
            double passRate = round2((double) passed / results.size() * 100);
            TestResult sample = results.get(0);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("testName",     sample.getTestName());
            m.put("suite",        sample.getSuite());
            m.put("runs",         results.size());
            m.put("passed",       passed);
            m.put("failed",       failed);
            m.put("passRate",     passRate);
            m.put("avgDurationMs", avgDur);
            return m;
        }).sorted((a, b) -> Long.compare((Long) b.get("failed"), (Long) a.get("failed")))
        .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runIds",           runIds);
        result.put("totalRuns",        runs.size());
        result.put("totalUniqueTests", byTest.size());
        result.put("totalExecutions",  totalExecutions);
        result.put("overallPassRate",  overallPassRate);
        result.put("tests",            tests);
        return result;
    }

    @Transactional(readOnly = true)
    public List<ProjectSummaryDto> getProjectsSummary() {
        List<String> names = testRunRepository.findDistinctProjectNames();
        return names.stream().<ProjectSummaryDto>map(name -> {
            List<TestRun> runs = testRunRepository.findByProjectName(name, PageRequest.of(0, 100));
            long totalTests  = runs.stream().mapToLong(r -> r.getTests().size()).sum();
            long totalFailed = runs.stream().mapToLong(r -> countByStatus(r.getTests(), TestStatus.FAILED)).sum();

            List<Double> trend = runs.stream()
                    .limit(5)
                    .map(r -> {
                        long total  = r.getTests().size();
                        long passed = countByStatus(r.getTests(), TestStatus.PASSED);
                        return total > 0 ? round2((double) passed / total * 100) : 0.0;
                    })
                    .collect(Collectors.collectingAndThen(Collectors.toList(), list -> {
                        java.util.Collections.reverse(list);
                        return list;
                    }));

            double avgPassRate = runs.stream()
                    .mapToDouble(r -> {
                        long total  = r.getTests().size();
                        long passed = countByStatus(r.getTests(), TestStatus.PASSED);
                        return total > 0 ? (double) passed / total * 100 : 0;
                    })
                    .average().orElse(0);

            LocalDateTime lastRunAt = runs.isEmpty() ? null : runs.get(0).getStartedAt();

            return ProjectSummaryDto.builder()
                    .projectName(name)
                    .totalRuns(runs.size())
                    .lastRunAt(lastRunAt)
                    .avgPassRate(round2(avgPassRate))
                    .totalTests(totalTests)
                    .totalFailed(totalFailed)
                    .passRateTrend(trend)
                    .build();
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getProjectRuns(String projectName, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return testRunRepository.findByProjectName(projectName, pageable).stream()
                .map(r -> {
                    long total  = r.getTests().size();
                    long passed = countByStatus(r.getTests(), TestStatus.PASSED);
                    long failed = countByStatus(r.getTests(), TestStatus.FAILED);
                    double passRate = total > 0 ? round2((double) passed / total * 100) : 0;
                    Map<String, Object> entry = new java.util.LinkedHashMap<>();
                    entry.put("runId",       r.getRunId());
                    entry.put("branch",      r.getBranch());
                    entry.put("environment", r.getEnvironment());
                    entry.put("startedAt",   r.getStartedAt());
                    entry.put("total",       total);
                    entry.put("passed",      passed);
                    entry.put("failed",      failed);
                    entry.put("passRate",    passRate);
                    entry.put("reportUrl",   "/runs/" + r.getRunId() + "/report");
                    return entry;
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listRecentRuns(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return testRunRepository.findByFilters(null, null, pageable).stream()
                .map(r -> {
                    long total  = r.getTests().size();
                    long passed = countByStatus(r.getTests(), TestStatus.PASSED);
                    long failed = countByStatus(r.getTests(), TestStatus.FAILED);
                    Map<String, Object> entry = new java.util.LinkedHashMap<>();
                    entry.put("runId",       r.getRunId());
                    entry.put("branch",      r.getBranch());
                    entry.put("environment", r.getEnvironment());
                    entry.put("startedAt",   r.getStartedAt());
                    entry.put("total",       total);
                    entry.put("passed",      passed);
                    entry.put("failed",      failed);
                    entry.put("reportUrl",   "/runs/" + r.getRunId() + "/report");
                    return entry;
                })
                .collect(Collectors.toList());
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

        List<TestResultDetail> allTests = tests.stream()
                .map(t -> TestResultDetail.builder()
                        .testId(t.getTestId())
                        .testName(t.getTestName())
                        .suite(t.getSuite())
                        .status(t.getStatus())
                        .durationMs(t.getDurationMs() != null ? t.getDurationMs() : 0)
                        .errorMessage(t.getErrorMessage())
                        .errorDetails(t.getErrorDetails())
                        .requestUrl(t.getRequestUrl())
                        .requestMethod(t.getRequestMethod())
                        .requestBody(t.getRequestBody())
                        .responseStatus(t.getResponseStatus())
                        .responseBody(t.getResponseBody())
                        .build())
                .collect(Collectors.toList());

        return RunSummaryResponse.builder()
                .runId(run.getRunId())
                .projectName(run.getProjectName())
                .targetUrl(run.getTargetUrl())
                .username(run.getUsername())
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
                .allTests(allTests)
                .build();
    }

    private long countByStatus(List<TestResult> tests, TestStatus status) {
        return tests.stream().filter(t -> t.getStatus() == status).count();
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "\n... (truncated)";
    }
}
