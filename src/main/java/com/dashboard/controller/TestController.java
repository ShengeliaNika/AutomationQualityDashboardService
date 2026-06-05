package com.dashboard.controller;

import com.dashboard.dto.response.FlakyTestDto;
import com.dashboard.dto.response.SlowestTestDto;
import com.dashboard.service.TestRunService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/tests")
@RequiredArgsConstructor
@Validated
public class TestController {

    private final TestRunService testRunService;

    @GetMapping("/flaky")
    public List<FlakyTestDto> getFlakyTests() {
        return testRunService.getFlakyTests();
    }

    @GetMapping("/slowest")
    public List<SlowestTestDto> getSlowestTests(@RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        return testRunService.getSlowestTests(limit);
    }
}
