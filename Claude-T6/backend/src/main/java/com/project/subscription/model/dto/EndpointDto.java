package com.project.subscription.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

public class EndpointDto {

    @Data
    public static class CreateRequest {
        @NotBlank @Size(max = 128)
        private String name;
        @NotBlank @Size(max = 512)
        private String url;
        private Map<String, String> customHeaders;
    }

    @Data
    public static class UpdateRequest {
        @Size(max = 128)
        private String name;
        @Size(max = 512)
        private String url;
        private Map<String, String> customHeaders;
    }

    @Data
    public static class Response {
        private Long id;
        private String name;
        private String url;
        private Map<String, String> customHeaders;
        private Short status;
        private Integer consecutiveFailures;
        private String lastPushAt;
        private String lastSuccessAt;
        private String createdAt;
    }

    @Data
    public static class SecretResponse {
        private Long endpointId;
        private String secret;
    }

    @Data
    public static class QuotaInfo {
        private int used;
        private int limit;
        private int percentage;
    }
}
