package com.dashboard.entity;

import com.dashboard.enums.TestStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "test_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String testId;

    @Column(nullable = false)
    private String testName;

    private String suite;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TestStatus status;

    private Long durationMs;

    @Column(length = 2000)
    private String errorMessage;

    @Column(columnDefinition = "TEXT")
    private String errorDetails;

    @Column(length = 2000)
    private String requestUrl;

    @Column(length = 10)
    private String requestMethod;

    @Column(columnDefinition = "TEXT")
    private String requestBody;

    @Column
    private Integer responseStatus;

    @Column(columnDefinition = "TEXT")
    private String responseBody;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_run_id", nullable = false)
    private TestRun testRun;
}
