package com.dashboard.service;

import com.dashboard.entity.TestResult;
import com.dashboard.entity.TestRun;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
public class ClickUpService {

    private static final String CLICKUP_API = "https://api.clickup.com/api/v2";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Value("${clickup.enabled:false}")
    private boolean enabled;

    @Value("${clickup.api-key:}")
    private String apiKey;

    @Value("${clickup.list-id:}")
    private String listId;

    @Value("${dashboard.base-url:http://localhost:8080}")
    private String dashboardBaseUrl;

    private final HttpClient http = HttpClient.newHttpClient();

    @Async
    public void reportFailedRun(TestRun run, List<TestResult> failedTests) {
        if (!enabled || apiKey.isBlank() || listId.isBlank() || failedTests.isEmpty()) return;

        try {
            String projectDisplay = formatProjectName(run.getProjectName());
            String started = run.getStartedAt() != null ? run.getStartedAt().format(FMT) : "N/A";
            String taskName = projectDisplay + " Automation Report - " + started;
            String reportUrl = dashboardBaseUrl + "/runs/" + run.getRunId() + "/report";

            String mainDesc = buildMainDescription(run, failedTests.size(), reportUrl);
            String taskId = createTask(taskName, mainDesc, null);
            if (taskId == null) return;

            for (TestResult test : failedTests) {
                createTask(test.getTestName(), buildSubtaskDescription(test), taskId);
            }

            log.info("[ClickUp] Created task '{}' with {} subtasks for run {}", taskName, failedTests.size(), run.getRunId());
        } catch (Exception e) {
            log.warn("[ClickUp] Failed to create tasks for run {}: {}", run.getRunId(), e.getMessage());
        }
    }

    private String createTask(String name, String description, String parentId) {
        try {
            String body = buildTaskJson(name, description, parentId);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(CLICKUP_API + "/list/" + listId + "/task"))
                    .header("Authorization", apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                String id = extractJsonField(resp.body(), "id");
                log.debug("[ClickUp] Task created id={} name={}", id, name);
                return id;
            } else {
                log.warn("[ClickUp] Task creation returned {}: {}", resp.statusCode(), resp.body().substring(0, Math.min(200, resp.body().length())));
                return null;
            }
        } catch (Exception e) {
            log.warn("[ClickUp] HTTP error creating task '{}': {}", name, e.getMessage());
            return null;
        }
    }

    private String buildMainDescription(TestRun run, int failCount, String reportUrl) {
        int total = run.getTests().size();
        int passed = total - failCount;
        return "## Run Report\n"
             + "[View Full Dashboard Report](" + reportUrl + ")\n\n"
             + "| Field | Value |\n|---|---|\n"
             + "| Project | " + run.getProjectName() + " |\n"
             + "| Branch | " + run.getBranch() + " |\n"
             + "| Environment | " + run.getEnvironment() + " |\n"
             + (run.getTargetUrl() != null ? "| Target URL | " + run.getTargetUrl() + " |\n" : "")
             + (run.getUsername() != null ? "| User | " + run.getUsername() + " |\n" : "")
             + "| Total | " + total + " |\n"
             + "| Passed | " + passed + " |\n"
             + "| **Failed** | **" + failCount + "** |\n";
    }

    private String buildSubtaskDescription(TestResult test) {
        StringBuilder sb = new StringBuilder();

        if (test.getErrorDetails() != null && !test.getErrorDetails().isBlank()) {
            sb.append("**Steps:**\n");
            for (String line : test.getErrorDetails().split("\n")) {
                String t = line.trim();
                if (t.isEmpty()) continue;
                sb.append(t.contains("-> FAILED") ? "✗ " : "✓ ").append(t).append("\n");
            }
            sb.append("\n");
        }

        if (test.getErrorMessage() != null && !test.getErrorMessage().isBlank()) {
            sb.append("**Error:** ").append(test.getErrorMessage().trim()).append("\n\n");
        }

        if (test.getRequestUrl() != null) {
            sb.append("**Request:** `").append(test.getRequestMethod() != null ? test.getRequestMethod() : "").append(" ").append(test.getRequestUrl()).append("`\n");
            if (test.getRequestBody() != null && !test.getRequestBody().isBlank()) {
                sb.append("```json\n").append(test.getRequestBody().trim()).append("\n```\n\n");
            }
        }

        if (test.getResponseStatus() != null) {
            sb.append("**Response:** `").append(test.getResponseStatus()).append("`\n");
            if (test.getResponseBody() != null && !test.getResponseBody().isBlank()) {
                sb.append("```json\n").append(test.getResponseBody().trim()).append("\n```\n");
            }
        }

        return sb.toString();
    }

    private String buildTaskJson(String name, String description, String parentId) {
        StringBuilder json = new StringBuilder("{");
        json.append("\"name\":").append(jsonString(name)).append(",");
        json.append("\"markdown_description\":").append(jsonString(description));
        if (parentId != null) json.append(",\"parent\":").append(jsonString(parentId));
        json.append("}");
        return json.toString();
    }

    private String formatProjectName(String name) {
        if (name == null) return "Automation";
        return java.util.Arrays.stream(name.replace("-", " ").replace("_", " ").split(" "))
                .map(w -> w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private String jsonString(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "") + "\"";
    }

    private String extractJsonField(String json, String field) {
        String key = "\"" + field + "\":\"";
        int start = json.indexOf(key);
        if (start < 0) return null;
        start += key.length();
        int end = json.indexOf("\"", start);
        return end > start ? json.substring(start, end) : null;
    }
}
