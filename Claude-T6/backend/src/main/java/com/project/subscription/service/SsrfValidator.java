package com.project.subscription.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;

@Service
public class SsrfValidator {

    @Value("${webhook.ssrf.allow-http:false}")
    private boolean allowHttp;

    private static final Set<String> BLOCKED_HOSTS = Set.of(
        "localhost", "127.0.0.1", "0.0.0.0", "::1"
    );

    private static final List<String> PRIVATE_PREFIXES = List.of(
        "10.", "172.16.", "172.17.", "172.18.", "172.19.",
        "172.20.", "172.21.", "172.22.", "172.23.", "172.24.",
        "172.25.", "172.26.", "172.27.", "172.28.", "172.29.",
        "172.30.", "172.31.", "192.168."
    );

    public ValidationResult validate(String url) {
        if (url == null || url.isBlank()) {
            return ValidationResult.fail("URL不能为空");
        }

        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();

            // 协议检查
            if (!"https".equals(scheme)) {
                if ("http".equals(scheme) && allowHttp) {
                    // 开发模式允许 HTTP
                } else {
                    return ValidationResult.fail("仅支持 HTTPS 协议" + (allowHttp ? "（开发模式允许 HTTP）" : ""));
                }
            }

            String host = uri.getHost();
            if (host == null) {
                return ValidationResult.fail("URL格式无效：缺少主机地址");
            }

            // 内网地址检查
            if (BLOCKED_HOSTS.contains(host.toLowerCase())) {
                return ValidationResult.fail("禁止使用本地地址");
            }

            for (String prefix : PRIVATE_PREFIXES) {
                if (host.startsWith(prefix)) {
                    return ValidationResult.fail("禁止使用内网地址");
                }
            }

            // 解析 DNS 检查
            try {
                InetAddress address = InetAddress.getByName(host);
                String ip = address.getHostAddress();
                for (String prefix : PRIVATE_PREFIXES) {
                    if (ip.startsWith(prefix)) {
                        return ValidationResult.fail("域名解析到内网地址：" + ip);
                    }
                }
            } catch (Exception e) {
                return ValidationResult.fail("无法解析域名：" + host);
            }

            return ValidationResult.ok();
        } catch (URISyntaxException e) {
            return ValidationResult.fail("URL格式无效：" + e.getMessage());
        }
    }

    public record ValidationResult(boolean valid, String errorMessage) {
        static ValidationResult ok() { return new ValidationResult(true, null); }
        static ValidationResult fail(String msg) { return new ValidationResult(false, msg); }
    }
}
