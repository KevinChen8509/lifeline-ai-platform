package com.project.subscription.service;

import com.project.subscription.model.dto.NotificationDto;
import com.project.subscription.model.entity.Notification;
import com.project.subscription.model.entity.NotificationPreference;
import com.project.subscription.repository.NotificationPreferenceRepository;
import com.project.subscription.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;

    public Page<Notification> listByUser(Long userId, int page, int size) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
    }

    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markAsRead(Long id, Long userId) {
        Notification n = notificationRepository.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new RuntimeException("通知不存在"));
        n.setIsRead(true);
        notificationRepository.save(n);
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        Page<Notification> unread = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 100));
        unread.getContent().stream()
            .filter(n -> !n.getIsRead())
            .forEach(n -> { n.setIsRead(true); notificationRepository.save(n); });
    }

    public NotificationDto.PreferenceUpdate getPreference(Long userId) {
        NotificationPreference pref = preferenceRepository.findByUserId(userId)
            .orElseGet(() -> {
                NotificationPreference newPref = new NotificationPreference();
                newPref.setUserId(userId);
                return preferenceRepository.save(newPref);
            });
        var dto = new NotificationDto.PreferenceUpdate();
        dto.setEndpointFailureEnabled(pref.getEndpointFailureEnabled());
        dto.setEndpointRecoveredEnabled(pref.getEndpointRecoveredEnabled());
        dto.setPushDeadEnabled(pref.getPushDeadEnabled());
        dto.setFailureFrequency(pref.getFailureFrequency());
        return dto;
    }

    @Transactional
    public NotificationDto.PreferenceUpdate updatePreference(Long userId, NotificationDto.PreferenceUpdate req) {
        NotificationPreference pref = preferenceRepository.findByUserId(userId)
            .orElseGet(() -> {
                NotificationPreference newPref = new NotificationPreference();
                newPref.setUserId(userId);
                return newPref;
            });
        if (req.getEndpointFailureEnabled() != null) pref.setEndpointFailureEnabled(req.getEndpointFailureEnabled());
        if (req.getEndpointRecoveredEnabled() != null) pref.setEndpointRecoveredEnabled(req.getEndpointRecoveredEnabled());
        if (req.getPushDeadEnabled() != null) pref.setPushDeadEnabled(req.getPushDeadEnabled());
        if (req.getFailureFrequency() != null) pref.setFailureFrequency(req.getFailureFrequency());
        preferenceRepository.save(pref);
        return getPreference(userId);
    }

    @Transactional
    public void createNotification(Long userId, String type, String title, String content, Long relatedId, String relatedType) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setRelatedId(relatedId);
        n.setRelatedType(relatedType);
        notificationRepository.save(n);
    }
}
