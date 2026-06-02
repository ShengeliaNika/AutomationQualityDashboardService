package com.dashboard.integration;

import com.dashboard.enums.TestStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WireMockNotificationTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void whenRunSavedWithCallbackUrl_thenCallbackIsInvoked() {
        wireMock.stubFor(post(urlEqualTo("/notify"))
                .willReturn(aResponse().withStatus(200)));

        Map<String, Object> payload = buildPayload("Run-WM-001", wireMock.baseUrl() + "/notify");

        ResponseEntity<Map> response = restTemplate.postForEntity("/runs", payload, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        wireMock.verify(postRequestedFor(urlEqualTo("/notify"))
                .withRequestBody(matchingJsonPath("$.runId", equalTo("Run-WM-001"))));
    }

    @Test
    void whenRunSavedWithoutCallbackUrl_thenNoCallbackMade() {
        Map<String, Object> payload = buildPayload("Run-WM-002", null);

        ResponseEntity<Map> response = restTemplate.postForEntity("/runs", payload, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        wireMock.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void whenCallbackUrlFails_runIsSavedAnyway() {
        wireMock.stubFor(post(urlEqualTo("/notify-fail"))
                .willReturn(aResponse().withStatus(500)));

        Map<String, Object> payload = buildPayload("Run-WM-003", wireMock.baseUrl() + "/notify-fail");

        ResponseEntity<Map> response = restTemplate.postForEntity("/runs", payload, Map.class);

        // callback failure must not propagate — run was still persisted
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private Map<String, Object> buildPayload(String runId, String callbackUrl) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("runId", runId);
        map.put("branch", "main");
        map.put("environment", "staging");
        map.put("commitHash", "wm123");
        map.put("startedAt", LocalDateTime.now().toString());
        map.put("tests", List.of(
                Map.of("testId", "wm-t1", "testName", "WM Test", "suite", "Smoke",
                        "status", TestStatus.PASSED.name(), "durationMs", 500)
        ));
        if (callbackUrl != null) {
            map.put("callbackUrl", callbackUrl);
        }
        return map;
    }
}
