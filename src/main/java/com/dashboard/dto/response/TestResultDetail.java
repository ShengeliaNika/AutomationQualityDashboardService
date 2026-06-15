package com.dashboard.dto.response;

import com.dashboard.enums.TestStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TestResultDetail {
    private String testId;
    private String testName;
    private String suite;
    private TestStatus status;
    private long durationMs;
    private String errorMessage;
    private String errorDetails;
    private String requestUrl;
    private String requestMethod;
    private String requestBody;
    private Integer responseStatus;
    private String responseBody;
}
