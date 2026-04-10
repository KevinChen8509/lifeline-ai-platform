package com.lifeline.modules.ai_analysis.repository;

import com.lifeline.modules.ai_analysis.entity.AiAnalysisResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AiAnalysisResultRepository extends JpaRepository<AiAnalysisResult, String> {

    List<AiAnalysisResult> findByDeviceIdOrderByTimestampDesc(String deviceId, Pageable pageable);

    @Query("SELECT a FROM AiAnalysisResult a WHERE " +
           "a.deviceId = :deviceId " +
           "AND (:analysisType IS NULL OR a.analysisType = :analysisType) " +
           "AND (:analysisResult IS NULL OR a.analysisResult = :analysisResult) " +
           "AND (:startTime IS NULL OR a.timestamp >= :startTime) " +
           "AND (:endTime IS NULL OR a.timestamp <= :endTime)")
    Page<AiAnalysisResult> findHistory(@Param("deviceId") String deviceId,
                                       @Param("analysisType") String analysisType,
                                       @Param("analysisResult") String analysisResult,
                                       @Param("startTime") String startTime,
                                       @Param("endTime") String endTime,
                                       Pageable pageable);
}
