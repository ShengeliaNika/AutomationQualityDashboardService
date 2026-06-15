package com.dashboard.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TestRunRequest {

    @NotBlank
    private String runId;

    /** Which project this run belongs to (e.g. "nugioscore-api"). Optional for backward compat. */
    private String projectName;

    private String targetUrl;
    private String username;

    @NotBlank
    private String branch;

    @NotBlank
    private String environment;

    @NotBlank
    private String commitHash;

    @NotNull
    private LocalDateTime startedAt;

    @NotNull
    @NotEmpty
    @Valid
    private List<TestResultRequest> tests;

    /** Optional: if set, service POSTs a completion notification to this URL. */
    private String callbackUrl;
}
