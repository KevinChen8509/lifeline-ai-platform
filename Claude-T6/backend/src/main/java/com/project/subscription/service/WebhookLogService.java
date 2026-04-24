package com.project.subscription.service;

import com.project.subscription.model.dto.DeliveryLogDto;
import com.project.subscription.model.entity.WebhookDeliveryLog;
import com.project.subscription.model.entity.WebhookEndpoint;
import com.project.subscription.repository.WebhookDeliveryLogRepository;
import com.project.subscription.repository.WebhookEndpointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WebhookLogService {

    private final WebhookDeliveryLogRepository logRepository;
    private final WebhookEndpointRepository endpointRepository;

    public Page<WebhookDeliveryLog> listLogs(DeliveryLogDto.QueryParams params) {
        if (params.getSubscriptionId() != null) {
            return logRepository.findBySubscriptionId(params.getSubscriptionId(),
                PageRequest.of(params.getPage(), params.getSize()));
        }
        if (params.getEndpointId() != null) {
            return logRepository.findByConfigId(params.getEndpointId(),
                PageRequest.of(params.getPage(), params.getSize()));
        }
        return logRepository.findAll(PageRequest.of(params.getPage(), params.getSize()));
    }

    public WebhookDeliveryLog getDetail(Long id) {
        return logRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("推送日志不存在"));
    }

    @Transactional
    public void retry(Long id) {
        WebhookDeliveryLog log = getDetail(id);
        if (!"FAILED".equals(log.getStatus()) && !"DEAD".equals(log.getStatus())) {
            throw new RuntimeException("仅失败或死信状态的日志可重试");
        }
        log.setStatus("PENDING");
        log.setAttemptCount(0);
        log.setNextRetryAt(LocalDateTime.now());
        log.setSource("MANUAL_RETRY");
        logRepository.save(log);
    }

    public DeliveryLogDto.StatsResponse getStats(Long endpointId) {
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        long total = logRepository.countRecentByEndpoint(endpointId, since);
        long success = logRepository.countSuccessByEndpoint(endpointId, since);

        var stats = new DeliveryLogDto.StatsResponse();
        stats.setTotalLast24h(total);
        stats.setSuccessRate(total > 0 ? (double) success / total * 100 : 100.0);
        return stats;
    }
}
