package com.datafabric.dataservice.governance;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * PoC 用的桩实现 - 按 X-User-Role 请求头决定策略
 *
 * 真实环境替换为调 OpenMetadata REST 读取字段标签的实现。
 *
 * 角色策略矩阵（演示用，可调整）：
 *   ADMIN           : 不脱敏
 *   CUSTOMER_VIEWER : 脱敏 idCard，隐藏 riskScore（看名字但不能查户口 / 风险分）
 *   SUPPORT         : 全脱敏 + 隐藏风险分（一线客服最小权限）
 *   未认证          : 同 SUPPORT
 */
@Component
@Profile("!prod")
public class StubMetadataClient implements MetadataClient {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_VIEWER = "CUSTOMER_VIEWER";
    public static final String ROLE_SUPPORT = "SUPPORT";

    @Override
    public FieldPolicy getPolicy(String custId, String role) {
        if (role == null || role.isBlank()) {
            return FieldPolicy.all();
        }
        return switch (role) {
            case ROLE_ADMIN -> FieldPolicy.none();
            case ROLE_VIEWER -> new FieldPolicy(false, true, true);
            case ROLE_SUPPORT -> new FieldPolicy(true, true, true);
            default -> FieldPolicy.all();
        };
    }

    /**
     * 从当前 HTTP 请求读 X-User-Role header。
     * 测试或非 HTTP 上下文返回 null（走默认 SUPPORT 策略）。
     */
    public static String currentRole() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes sra)) {
            return null;
        }
        HttpServletRequest req = sra.getRequest();
        String header = req.getHeader("X-User-Role");
        return header != null ? header.toUpperCase() : null;
    }
}
