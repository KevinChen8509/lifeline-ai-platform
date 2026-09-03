package com.datafabric.dataservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * B2 安全配置（W6-A 扩展双通道）
 *
 * PoC 策略：全局共享 API Key（X-API-Key 请求头）+ 服务级 API Key
 *
 *   /actuator/health                          → permitAll（健康探针不鉴权）
 *   /api/v1/**                                → 有效 API Key（全局或服务级）
 *   其他                                       → denyAll
 *
 * 双通道：
 *   - 全局 key（平台方/dashboard 代理）：原有行为不变，可访问全部 /api/v1/**
 *   - 服务级 key（第三方）：仅当路径为 /api/v1/services/{slug}/query 且 key
 *     恒时等于该 slug 发布时生成的 key 才认证（ROLE_SERVICE_KEY）——
 *     换 slug、调 POST 发布、DELETE 下线等其他路径一律 401
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    public static final String HEADER_API_KEY = "X-API-Key";
    private static final Pattern SERVICE_QUERY_PATH =
            Pattern.compile("^/api/v1/services/([a-z0-9-]+)/query$");

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, SecurityProperties props,
            com.datafabric.dataservice.service.ServiceRegistry serviceRegistry) throws Exception {
        if (props.apiKey().length() < props.minKeyLength()) {
            log.warn("⚠️ PoC API Key 长度 {} < {}，生产环境必须替换为强随机值",
                    props.apiKey().length(), props.minKeyLength());
        }

        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(new ApiKeyFilter(props.apiKey(),
                        (path, key) -> matchesServiceKey(serviceRegistry, path, key)),
                        BasicAuthenticationFilter.class)
                .exceptionHandling(eh -> eh.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .build();
    }

    /** 解析 /api/v1/services/{slug}/query 并校验服务级 key（恒时比较在 registry 内） */
    private static boolean matchesServiceKey(
            com.datafabric.dataservice.service.ServiceRegistry registry, String path, String providedKey) {
        if (providedKey == null) {
            return false;
        }
        var m = SERVICE_QUERY_PATH.matcher(path);
        return m.matches() && registry.isValidServiceKey(m.group(1), providedKey);
    }

    /**
     * 一次性过滤器：正确 key 一律设 Authentication（认证），其他路径交给 Spring Security 的 authorization 决策。
     * 仅 /api/v1/** 在 key 缺失/错误时直接写 401（该路径不允许匿名）。
     *
     * 通道一：全局 key → ROLE_API_CLIENT（平台方，全端点）
     * 通道二：服务级 key → ROLE_SERVICE_KEY（仅自己 slug 的 /query，其余路径照旧 401）
     *
     * 关键修复（踩坑）：
     *   - AnonymousAuthenticationToken 被 authenticated() 视为未认证 →
     *     用 UsernamePasswordAuthenticationToken 3 参（标记已认证）
     *   - 不能拦截所有路径（permitAll 的 /actuator/health 也会被门禁）
     *   - denyAll 路径 + 已认证 → 403；denyAll + 匿名 → 401（由 Spring Security 区分）
     */
    static class ApiKeyFilter extends OncePerRequestFilter {

        private final String expectedKey;
        private final java.util.function.BiPredicate<String, String> serviceKeyMatcher;

        ApiKeyFilter(String expectedKey, java.util.function.BiPredicate<String, String> serviceKeyMatcher) {
            this.expectedKey = expectedKey;
            this.serviceKeyMatcher = serviceKeyMatcher;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
                throws ServletException, IOException {

            String path = req.getRequestURI();
            String provided = req.getHeader(HEADER_API_KEY);

            // 通道一：全局 key 匹配 → 一律认证（即便后续 denyAll，也是"已认证但无权限"=403，更语义化）
            if (provided != null && provided.equals(expectedKey)) {
                setAuth("api-client", "ROLE_API_CLIENT");
                chain.doFilter(req, resp);
                return;
            }

            // 通道二：服务级 key —— 仅绑定 slug 的 /query 有效
            if (serviceKeyMatcher.test(path, provided)) {
                var m = SERVICE_QUERY_PATH.matcher(path);
                m.matches(); // serviceKeyMatcher 已确认路径形状，这里仅为提取 slug
                setAuth("service-key:" + m.group(1), "ROLE_SERVICE_KEY");
                chain.doFilter(req, resp);
                return;
            }

            // key 不匹配：仅 /api/v1/** 直接挡掉 401；其他路径交给 Spring Security 决策
            if (path.startsWith("/api/v1/")) {
                resp.setStatus(HttpStatus.UNAUTHORIZED.value());
                resp.setContentType("application/json");
                resp.getWriter().write("{\"error\":\"UNAUTHENTICATED\",\"message\":\"API key 缺失或无效\"}");
                return;
            }
            // actuator/health（permitAll）或其他路径（denyAll）→ 交给后续 filter chain
            chain.doFilter(req, resp);
        }

        private static void setAuth(String principal, String role) {
            var auth = new UsernamePasswordAuthenticationToken(
                    principal, null, AuthorityUtils.createAuthorityList(role));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
    }
}
