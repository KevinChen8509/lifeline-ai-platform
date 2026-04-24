package com.project.subscription.model.dto;

import lombok.Data;

import java.util.List;

public class DeliveryLogDto {

    @Data
    public static class QueryParams {
        private Long subscriptionId;
        private Long endpointId;
        private String status;
        private String startTime;
        private String endTime;
        private int page = 0;
        private int size = 20;
    }

    @Data
    public static class Response {
        private Long id;
        private String eventId;
        private String event;
        private Long subscriptionId;
        private String subscriptionName;
        private Long configId;
        private String endpointUrl;
        private Long deviceId;
        private String status;
        private Integer attemptCount;
        private Integer maxRetries;
        private Integer responseCode;
        private String responseBody;
        private String errorMsg;
        private String source;
        private Boolean stormGuardDegraded;
        private Boolean ruleMatchSkipped;
        private String nextRetryAt;
        private String deliveredAt;
        private String createdAt;
    }

    @Data
    public static class StatsResponse {
        private long totalLast24h;
        private double successRate;
        private double avgLatencyMs;
        private double p95LatencyMs;
    }

    @Data
    public static class RetryTimeline {
        private String time;
        private String status;
        private Integer responseCode;
        private String errorMsg;
    }
}
