package com.lifeline.modules.ai_analysis.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "ai_analysis_results")
@EntityListeners(AuditingEntityListener.class)
public class AiAnalysisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private String id;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "model_id", nullable = false)
    private String modelId;

    @Column(name = "analysis_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AnalysisType analysisType;

    @Column(name = "analysis_result", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AnalysisResult analysisResult;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal confidence;

    @Column(columnDefinition = "jsonb")
    private String details;

    @Column(name = "confidence_factors", columnDefinition = "jsonb")
    private String confidenceFactors;

    @Column(name = "raw_data", columnDefinition = "jsonb")
    private String rawData;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
