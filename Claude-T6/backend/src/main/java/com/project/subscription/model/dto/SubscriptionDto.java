package com.project.subscription.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

public class SubscriptionDto {

    @Data
    public static class CreateRequest {
        @NotBlank
        private String name;
        @NotNull
        private Long endpointId;
        @NotNull
        private Short subscriptionType;  // 0=设备级 1=设备类型级 2=分组级
        @NotNull
        private Long targetId;
        private List<Long> dataPointIds;
        private List<RuleDto> rules;
    }

    @Data
    public static class UpdateRequest {
        private String name;
        private List<RuleDto> rules;
    }

    @Data
    public static class BatchStatusRequest {
        @NotNull
        private List<Long> ids;
        @NotNull
        private Short status; // 0=启用 1=暂停
    }

    @Data
    public static class Response {
        private Long id;
        private String name;
        private Long endpointId;
        private String endpointName;
        private Short subscriptionType;
        private Long targetId;
        private String targetName;
        private List<Long> dataPointIds;
        private Short status;
        private String statusLabel;
        private List<RuleDto> rules;
        private String createdAt;
        private String updatedAt;
    }
}
