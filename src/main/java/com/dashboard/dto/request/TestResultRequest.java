package com.dashboard.dto.request;

import com.dashboard.enums.TestStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TestResultRequest {

    @NotBlank
    private String testId;

    @NotBlank
    private String testName;

    @NotBlank
    private String suite;

    @NotNull
    private TestStatus status;

    @NotNull
    private Long durationMs;

    private String errorMessage;

    private String errorDetails;
}
