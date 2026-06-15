package com.dashboard.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class RunSummaryResponse {
    private String runId;
    private String projectName;
    private String targetUrl;
    private String username;
    private String branch;
    private String environment;
    private LocalDateTime startedAt;

    private int total;
    private int passed;
    private int failed;
    private int skipped;
    private double passRate;
    private long avgDurationMs;

    private List<SlowestTestDto> slowestTests;
    private List<FailedTestDetail> failedTests;
    private Map<String, List<String>> failedBySuite;
    private List<TestResultDetail> allTests;
}
