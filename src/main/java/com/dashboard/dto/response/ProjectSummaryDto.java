package com.dashboard.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ProjectSummaryDto {
    private String projectName;
    private int totalRuns;
    private LocalDateTime lastRunAt;
    private double avgPassRate;
    private long totalTests;
    private long totalFailed;
    private List<Double> passRateTrend;  // last 5 runs, oldest first — for sparkline
}
