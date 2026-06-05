package com.dashboard.service;

import com.dashboard.entity.TestResult;
import com.dashboard.entity.TestRun;
import com.dashboard.enums.TestStatus;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private WireMockServer wireMockServer;
    private NotificationService notificationService;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
        baseUrl = "http://localhost:" + wireMockServer.port();

        RestTemplate restTemplate = new RestTemplate();
        notificationService = new NotificationService(restTemplate);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    // ── notifyRunCompleted ───────────────────────────────────────────────────

    @Test
    void notifyRunCompleted_shouldSendPostRequest_whenCalledWithValidData() {
        // Given
        TestRun run = buildTestRun("Run-001", 3);
        String callbackUrl = baseUrl + "/webhook";

        stubFor(post(urlEqualTo("/webhook"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"received\"}")));

        // When
        notificationService.notifyRunCompleted(run, callbackUrl);

        // Then
        verify(postRequestedFor(urlEqualTo("/webhook"))
                .withHeader("Content-Type", containing("application/json")));
    }

    @Test
    void notifyRunCompleted_shouldSendCorrectPayload_whenCalledWithTestRun() {
        // Given
        TestRun run = buildTestRun("Run-PAYLOAD", 5);
        String callbackUrl = baseUrl + "/webhook";

        stubFor(post(urlEqualTo("/webhook"))
                .willReturn(aResponse().withStatus(200)));

        // When
        notificationService.notifyRunCompleted(run, callbackUrl);

        // Then
        verify(postRequestedFor(urlEqualTo("/webhook"))
                .withRequestBody(matchingJsonPath("$.runId", equalTo("Run-PAYLOAD")))
                .withRequestBody(matchingJsonPath("$.status", equalTo("COMPLETED")))
                .withRequestBody(matchingJsonPath("$.totalTests", equalTo("5"))));
    }

    @Test
    void notifyRunCompleted_shouldHandleEmptyTests_whenTestListIsEmpty() {
        // Given
        TestRun run = buildTestRun("Run-EMPTY", 0);
        String callbackUrl = baseUrl + "/webhook";

        stubFor(post(urlEqualTo("/webhook"))
                .willReturn(aResponse().withStatus(200)));

        // When
        notificationService.notifyRunCompleted(run, callbackUrl);

        // Then
        verify(postRequestedFor(urlEqualTo("/webhook"))
                .withRequestBody(matchingJsonPath("$.totalTests", equalTo("0"))));
    }

    @Test
    void notifyRunCompleted_shouldNotThrowException_whenCallbackReturns400() {
        // Given
        TestRun run = buildTestRun("Run-400", 2);
        String callbackUrl = baseUrl + "/webhook";

        stubFor(post(urlEqualTo("/webhook"))
                .willReturn(aResponse().withStatus(400)));

        // When / Then - Should not throw exception
        notificationService.notifyRunCompleted(run, callbackUrl);

        verify(postRequestedFor(urlEqualTo("/webhook")));
    }

    @Test
    void notifyRunCompleted_shouldNotThrowException_whenCallbackReturns500() {
        // Given
        TestRun run = buildTestRun("Run-500", 2);
        String callbackUrl = baseUrl + "/webhook";

        stubFor(post(urlEqualTo("/webhook"))
                .willReturn(aResponse().withStatus(500)));

        // When / Then - Should not throw exception
        notificationService.notifyRunCompleted(run, callbackUrl);

        verify(postRequestedFor(urlEqualTo("/webhook")));
    }

    @Test
    void notifyRunCompleted_shouldNotThrowException_whenConnectionRefused() {
        // Given
        TestRun run = buildTestRun("Run-CONN-REFUSED", 2);
        String callbackUrl = "http://localhost:99999/webhook"; // Invalid port

        // When / Then - Should not throw exception
        notificationService.notifyRunCompleted(run, callbackUrl);

        // No exception should be thrown
    }

    @Test
    void notifyRunCompleted_shouldNotThrowException_whenTimeoutOccurs() {
        // Given
        TestRun run = buildTestRun("Run-TIMEOUT", 2);
        String callbackUrl = baseUrl + "/webhook";

        stubFor(post(urlEqualTo("/webhook"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(5000))); // 5 second delay

        // When / Then - Should not throw exception (will timeout but be caught)
        notificationService.notifyRunCompleted(run, callbackUrl);
    }

    @Test
    void notifyRunCompleted_shouldHandleMultipleRequests_whenCalledMultipleTimes() {
        // Given
        TestRun run1 = buildTestRun("Run-001", 2);
        TestRun run2 = buildTestRun("Run-002", 3);
        TestRun run3 = buildTestRun("Run-003", 4);
        String callbackUrl = baseUrl + "/webhook";

        stubFor(post(urlEqualTo("/webhook"))
                .willReturn(aResponse().withStatus(200)));

        // When
        notificationService.notifyRunCompleted(run1, callbackUrl);
        notificationService.notifyRunCompleted(run2, callbackUrl);
        notificationService.notifyRunCompleted(run3, callbackUrl);

        // Then
        verify(3, postRequestedFor(urlEqualTo("/webhook")));
    }

    @Test
    void notifyRunCompleted_shouldPostToDifferentUrls_whenCalledWithDifferentCallbacks() {
        // Given
        TestRun run = buildTestRun("Run-001", 2);
        String callbackUrl1 = baseUrl + "/webhook1";
        String callbackUrl2 = baseUrl + "/webhook2";

        stubFor(post(urlEqualTo("/webhook1"))
                .willReturn(aResponse().withStatus(200)));
        stubFor(post(urlEqualTo("/webhook2"))
                .willReturn(aResponse().withStatus(200)));

        // When
        notificationService.notifyRunCompleted(run, callbackUrl1);
        notificationService.notifyRunCompleted(run, callbackUrl2);

        // Then
        verify(1, postRequestedFor(urlEqualTo("/webhook1")));
        verify(1, postRequestedFor(urlEqualTo("/webhook2")));
    }

    @Test
    void notifyRunCompleted_shouldHandleSuccessResponse_whenServerReturns200() {
        // Given
        TestRun run = buildTestRun("Run-SUCCESS", 2);
        String callbackUrl = baseUrl + "/webhook";

        stubFor(post(urlEqualTo("/webhook"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"Notification received successfully\"}")));

        // When / Then
        notificationService.notifyRunCompleted(run, callbackUrl);

        verify(1, postRequestedFor(urlEqualTo("/webhook")));
    }

    @Test
    void notifyRunCompleted_shouldHandleSuccessResponse_whenServerReturns201() {
        // Given
        TestRun run = buildTestRun("Run-CREATED", 2);
        String callbackUrl = baseUrl + "/webhook";

        stubFor(post(urlEqualTo("/webhook"))
                .willReturn(aResponse().withStatus(201)));

        // When / Then
        notificationService.notifyRunCompleted(run, callbackUrl);

        verify(1, postRequestedFor(urlEqualTo("/webhook")));
    }

    @Test
    void notifyRunCompleted_shouldHandleSuccessResponse_whenServerReturns204() {
        // Given
        TestRun run = buildTestRun("Run-NO-CONTENT", 2);
        String callbackUrl = baseUrl + "/webhook";

        stubFor(post(urlEqualTo("/webhook"))
                .willReturn(aResponse().withStatus(204)));

        // When / Then
        notificationService.notifyRunCompleted(run, callbackUrl);

        verify(1, postRequestedFor(urlEqualTo("/webhook")));
    }

    @Test
    void notifyRunCompleted_shouldHandleRedirect_whenServerReturns302() {
        // Given
        TestRun run = buildTestRun("Run-REDIRECT", 2);
        String callbackUrl = baseUrl + "/webhook";

        stubFor(post(urlEqualTo("/webhook"))
                .willReturn(aResponse()
                        .withStatus(302)
                        .withHeader("Location", baseUrl + "/new-webhook")));

        // When / Then - Should not throw exception
        notificationService.notifyRunCompleted(run, callbackUrl);

        verify(postRequestedFor(urlEqualTo("/webhook")));
    }

    @Test
    void notifyRunCompleted_shouldIncludeAllRequiredFields_whenSendingNotification() {
        // Given
        TestRun run = buildTestRun("Run-FIELDS", 10);
        String callbackUrl = baseUrl + "/webhook";

        stubFor(post(urlEqualTo("/webhook"))
                .willReturn(aResponse().withStatus(200)));

        // When
        notificationService.notifyRunCompleted(run, callbackUrl);

        // Then - Verify all required fields are present
        verify(postRequestedFor(urlEqualTo("/webhook"))
                .withRequestBody(matchingJsonPath("$.runId"))
                .withRequestBody(matchingJsonPath("$.status"))
                .withRequestBody(matchingJsonPath("$.totalTests")));
    }

    @Test
    void notifyRunCompleted_shouldHandleLargeTestCount_whenManyTestsExist() {
        // Given
        TestRun run = buildTestRun("Run-LARGE", 1000);
        String callbackUrl = baseUrl + "/webhook";

        stubFor(post(urlEqualTo("/webhook"))
                .willReturn(aResponse().withStatus(200)));

        // When
        notificationService.notifyRunCompleted(run, callbackUrl);

        // Then
        verify(postRequestedFor(urlEqualTo("/webhook"))
                .withRequestBody(matchingJsonPath("$.totalTests", equalTo("1000"))));
    }

    // ── Helper Methods ───────────────────────────────────────────────────────

    private TestRun buildTestRun(String runId, int testCount) {
        TestRun run = new TestRun();
        run.setId(1L);
        run.setRunId(runId);
        run.setBranch("main");
        run.setEnvironment("test");
        run.setCommitHash("abc123");
        run.setStartedAt(LocalDateTime.now());

        List<TestResult> tests = new ArrayList<>();
        for (int i = 0; i < testCount; i++) {
            TestResult test = new TestResult();
            test.setId((long) i);
            test.setTestId("test-" + i);
            test.setTestName("Test " + i);
            test.setSuite("Suite");
            test.setStatus(TestStatus.PASSED);
            test.setDurationMs(1000L);
            test.setTestRun(run);
            tests.add(test);
        }
        run.setTests(tests);

        return run;
    }
}
