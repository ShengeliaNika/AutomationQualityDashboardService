package com.dashboard.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class DashboardResponse {
    private String branch;
    private String environment;
    private int totalRuns;
    private List<TrendPoint> passRateTrend;
    private List<TrendPoint> failureCountTrend;
    private List<TrendPoint> avgDurationTrend;
}
