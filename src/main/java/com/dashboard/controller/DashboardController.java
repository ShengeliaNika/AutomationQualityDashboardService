package com.dashboard.controller;

import com.dashboard.dto.response.DashboardResponse;
import com.dashboard.service.TestRunService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DashboardController {

    private final TestRunService testRunService;

    @GetMapping("/dashboard")
    public DashboardResponse getDashboard(
            @RequestParam(required = false) String branch,
            @RequestParam(required = false) String environment,
            @RequestParam(defaultValue = "10") int lastN) {
        return testRunService.getDashboard(branch, environment, lastN);
    }
}
