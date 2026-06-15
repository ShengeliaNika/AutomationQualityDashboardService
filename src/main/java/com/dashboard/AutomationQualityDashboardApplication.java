package com.dashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class AutomationQualityDashboardApplication {
    public static void main(String[] args) {
        SpringApplication.run(AutomationQualityDashboardApplication.class, args);
    }
}
