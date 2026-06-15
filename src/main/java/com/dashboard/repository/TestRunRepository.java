package com.dashboard.repository;

import com.dashboard.entity.TestRun;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TestRunRepository extends JpaRepository<TestRun, Long> {

    Optional<TestRun> findByRunId(String runId);

    boolean existsByRunId(String runId);

    @Query("SELECT r FROM TestRun r WHERE (:branch IS NULL OR r.branch = :branch) " +
           "AND (:environment IS NULL OR r.environment = :environment) " +
           "ORDER BY r.startedAt DESC")
    List<TestRun> findByFilters(@Param("branch") String branch,
                                @Param("environment") String environment,
                                Pageable pageable);

    @Query("SELECT DISTINCT r.projectName FROM TestRun r WHERE r.projectName IS NOT NULL AND r.projectName <> '' ORDER BY r.projectName")
    List<String> findDistinctProjectNames();

    @Query("SELECT r FROM TestRun r WHERE r.projectName = :projectName ORDER BY r.startedAt DESC")
    List<TestRun> findByProjectName(@Param("projectName") String projectName, Pageable pageable);
}
