package com.datafabric.dataservice.api;

import com.datafabric.dataservice.domain.CustomerOverviewDto;
import com.datafabric.dataservice.domain.CustomerProfileDto;
import com.datafabric.dataservice.domain.CustomerProfileService;
import com.datafabric.dataservice.exception.CustomerNotFoundException;
import com.datafabric.dataservice.governance.GetProfile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 客户画像 / 指标 REST API
 *
 * 端口 8090，对外契约：/api/v1/*
 * 不直接连数据库，所有数据通过 Cube.dev 语义层获取（跨源 JOIN 透明）
 *
 * W2.3：方法上加 @GetProfile 即触发三个 AOP 切面：
 *   - DataMaskingAspect  脱敏 phone / idCard / riskScore
 *   - AuditAspect        记录每次访问到 audit_log
 *   - LineageAspect      发射 OpenLineage 事件
 */
@RestController
@RequestMapping("/api/v1")
public class CustomerProfileController {

    private final CustomerProfileService service;

    public CustomerProfileController(CustomerProfileService service) {
        this.service = service;
    }

    /**
     * 单客户画像 - 默认 full 视图
     * 角色 X-User-Role: ADMIN 决定是否脱敏
     */
    @GetMapping("/customers/{custId}/profile")
    @GetProfile(view = "full")
    public CustomerProfileDto getProfile(
            @PathVariable String custId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        return service.findById(custId)
                .orElseThrow(() -> new CustomerNotFoundException(custId));
    }

    /**
     * 简要画像 - 强制隐藏身份证和风险分（brief 视图）
     */
    @GetMapping("/customers/{custId}/brief")
    @GetProfile(view = "brief")
    public CustomerProfileDto getBriefProfile(
            @PathVariable String custId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        return service.findById(custId)
                .orElseThrow(() -> new CustomerNotFoundException(custId));
    }

    /** 客户分群查询（按等级过滤、分页） */
    @GetMapping("/customers")
    @GetProfile(view = "full")
    public List<CustomerProfileDto> searchCustomers(
            @RequestParam(required = false) String level,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (size < 1 || size > 200) {
            throw new IllegalArgumentException("size 必须在 1-200 之间");
        }
        if (page < 0) {
            throw new IllegalArgumentException("page 不能为负");
        }
        return service.search(level, page, size);
    }

    /** 全局客户指标快照（ARPU / VIP3 / 风险分布） */
    @GetMapping("/metrics/customer-overview")
    public CustomerOverviewDto overview() {
        return service.overview();
    }
}
