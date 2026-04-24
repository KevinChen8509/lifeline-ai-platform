package com.project.subscription.model.dto;

import lombok.Data;

public class NotificationDto {

    @Data
    public static class Response {
        private Long id;
        private String type;
        private String title;
        private String content;
        private Long relatedId;
        private String relatedType;
        private Boolean isRead;
        private String createdAt;
    }

    @Data
    public static class PreferenceUpdate {
        private Boolean endpointFailureEnabled;
        private Boolean endpointRecoveredEnabled;
        private Boolean pushDeadEnabled;
        private String failureFrequency;
    }
}
