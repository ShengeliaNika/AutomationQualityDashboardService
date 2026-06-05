package com.dashboard.controller;

import com.dashboard.dto.response.DashboardResponse;
import com.dashboard.service.TestRunService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Validated
public class DashboardController {

    private final TestRunService testRunService;

    @GetMapping("/dashboard")
    public DashboardResponse getDashboard(
            @RequestParam(required = false) String branch,
            @RequestParam(required = false) String environment,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int lastN) {
        return testRunService.getDashboard(branch, environment, lastN);
    }
}
