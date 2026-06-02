package com.dashboard.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FailedTestDetail {
    private String testId;
    private String testName;
    private String suite;
    private String errorMessage;
    private long durationMs;
}
