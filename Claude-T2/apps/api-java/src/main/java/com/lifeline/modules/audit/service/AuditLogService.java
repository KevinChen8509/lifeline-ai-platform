package com.lifeline.modules.audit.service;

import com.lifeline.common.response.PageResponse;
import com.lifeline.modules.audit.entity.AuditLog;
import com.lifeline.modules.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public PageResponse<AuditLog> findAll(int page, int pageSize, String action, String targetType, String operatorId) {
        Page<AuditLog> result = auditLogRepository.findAllWithFilters(
                action, targetType, operatorId,
                PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return PageResponse.of(result.getContent(), result.getTotalElements(), page, pageSize);
    }

    @Transactional
    public AuditLog create(AuditLog auditLog) {
        return auditLogRepository.save(auditLog);
    }
}
