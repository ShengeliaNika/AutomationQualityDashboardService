package com.dashboard.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FlakyTestDto {
    private String testId;
    private String testName;
    private String suite;
    private int passCount;
    private int failCount;
}
