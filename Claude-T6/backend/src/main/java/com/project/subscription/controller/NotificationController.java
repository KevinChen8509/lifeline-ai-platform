package com.project.subscription.controller;

import com.project.subscription.config.SecurityUtils;
import com.project.subscription.model.dto.ApiResponse;
import com.project.subscription.model.dto.NotificationDto;
import com.project.subscription.model.dto.PageResponse;
import com.project.subscription.model.entity.Notification;
import com.project.subscription.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<PageResponse<Notification>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Notification> result = notificationService.listByUser(SecurityUtils.getUserId(), page, size);
        return ApiResponse.ok(PageResponse.from(result));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Long> unreadCount() {
        return ApiResponse.ok(notificationService.getUnreadCount(SecurityUtils.getUserId()));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id, SecurityUtils.getUserId());
        return ApiResponse.ok();
    }

    @PostMapping("/read-all")
    public ApiResponse<Void> markAllAsRead() {
        notificationService.markAllAsRead(SecurityUtils.getUserId());
        return ApiResponse.ok();
    }

    @GetMapping("/preferences")
    public ApiResponse<NotificationDto.PreferenceUpdate> getPreference() {
        return ApiResponse.ok(notificationService.getPreference(SecurityUtils.getUserId()));
    }

    @PutMapping("/preferences")
    public ApiResponse<NotificationDto.PreferenceUpdate> updatePreference(
            @RequestBody NotificationDto.PreferenceUpdate req) {
        return ApiResponse.ok(notificationService.updatePreference(SecurityUtils.getUserId(), req));
    }
}
