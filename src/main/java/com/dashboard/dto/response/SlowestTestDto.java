package com.dashboard.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SlowestTestDto {
    private String testId;
    private String testName;
    private String suite;
    private long durationMs;
}
