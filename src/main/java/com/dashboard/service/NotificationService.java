package com.dashboard.service;

import com.dashboard.entity.TestRun;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final RestTemplate restTemplate;

    public void notifyRunCompleted(TestRun run, String callbackUrl) {
        try {
            Map<String, Object> payload = Map.of(
                    "runId", run.getRunId(),
                    "status", "COMPLETED",
                    "totalTests", run.getTests().size()
            );
            restTemplate.postForEntity(callbackUrl, payload, Void.class);
            log.info("Notified callback {} for run {}", callbackUrl, run.getRunId());
        } catch (Exception e) {
            log.warn("Callback notification failed for run {}: {}", run.getRunId(), e.getMessage());
        }
    }
}
