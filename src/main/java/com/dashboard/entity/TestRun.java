package com.dashboard.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "test_runs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String runId;

    @Column
    private String projectName;

    @Column
    private String targetUrl;

    @Column
    private String username;

    @Column(nullable = false)
    private String branch;

    @Column(nullable = false)
    private String environment;

    private String commitHash;

    private LocalDateTime startedAt;

    @OneToMany(mappedBy = "testRun", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<TestResult> tests = new ArrayList<>();
}
