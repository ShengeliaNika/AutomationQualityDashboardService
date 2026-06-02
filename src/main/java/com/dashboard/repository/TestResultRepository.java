package com.dashboard.repository;

import com.dashboard.entity.TestResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestResultRepository extends JpaRepository<TestResult, Long> {

    @Query("SELECT tr FROM TestResult tr JOIN FETCH tr.testRun r ORDER BY r.startedAt DESC")
    List<TestResult> findAllWithRunOrderByStartedAt();
}
