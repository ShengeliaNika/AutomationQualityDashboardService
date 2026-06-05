package com.dashboard.repository;

import com.dashboard.entity.TestResult;
import com.dashboard.entity.TestRun;
import com.dashboard.enums.TestStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestResultRepository extends JpaRepository<TestResult, Long> {

    @Query("SELECT tr FROM TestResult tr JOIN FETCH tr.testRun r ORDER BY tr.durationMs DESC")
    List<TestResult> findSlowestTests(Pageable pageable);

    @Query("SELECT tr FROM TestResult tr WHERE tr.testRun = :run ORDER BY tr.durationMs DESC")
    List<TestResult> findSlowestTestsByRun(@Param("run") TestRun run, Pageable pageable);

    @Query("SELECT tr FROM TestResult tr WHERE tr.testRun = :run AND tr.status = :status")
    List<TestResult> findByTestRunAndStatus(@Param("run") TestRun run, @Param("status") TestStatus status);

    @Query("""
        SELECT tr.testId as testId,
               tr.testName as testName,
               tr.suite as suite,
               SUM(CASE WHEN tr.status = 'PASSED' THEN 1 ELSE 0 END) as passCount,
               SUM(CASE WHEN tr.status = 'FAILED' THEN 1 ELSE 0 END) as failCount
        FROM TestResult tr
        WHERE tr.testRun.id IN (
            SELECT r.id FROM TestRun r ORDER BY r.startedAt DESC LIMIT :windowSize
        )
        GROUP BY tr.testId, tr.testName, tr.suite
        HAVING SUM(CASE WHEN tr.status = 'PASSED' THEN 1 ELSE 0 END) > 0
           AND SUM(CASE WHEN tr.status = 'FAILED' THEN 1 ELSE 0 END) > 0
        """)
    List<FlakyTestProjection> findFlakyTests(@Param("windowSize") int windowSize);

    interface FlakyTestProjection {
        String getTestId();
        String getTestName();
        String getSuite();
        Long getPassCount();
        Long getFailCount();
    }
}
