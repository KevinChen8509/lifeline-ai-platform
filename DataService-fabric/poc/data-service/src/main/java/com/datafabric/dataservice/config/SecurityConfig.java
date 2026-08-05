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

/**
 * B2 安全配置
 *
 * PoC 策略：单一共享 API Key（X-API-Key 请求头）
 *
 *   /actuator/health          → permitAll（健康探针不鉴权）
 *   /api/v1/**                → 必须有有效 API Key
 *   其他                       → denyAll
 *
 * 角色（X-User-Role 头）在 API Key 通过后由治理层读取，仍由
 * StubMetadataClient 决定脱敏策略——攻击者无法伪造，因为整个
 * 请求必须先通过 API Key 才能进 controller。
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    public static final String HEADER_API_KEY = "X-API-Key";

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, SecurityProperties props) throws Exception {
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
                .addFilterBefore(new ApiKeyFilter(props.apiKey()), BasicAuthenticationFilter.class)
                .exceptionHandling(eh -> eh.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .build();
    }

    /**
     * 一次性过滤器：正确 key 一律设 Authentication（认证），其他路径交给 Spring Security 的 authorization 决策。
     * 仅 /api/v1/** 在 key 缺失/错误时直接写 401（该路径不允许匿名）。
     *
     * 关键修复（踩坑）：
     *   - AnonymousAuthenticationToken 被 authenticated() 视为未认证 →
     *     用 UsernamePasswordAuthenticationToken 3 参（标记已认证）
     *   - 不能拦截所有路径（permitAll 的 /actuator/health 也会被门禁）
     *   - denyAll 路径 + 已认证 → 403；denyAll + 匿名 → 401（由 Spring Security 区分）
     */
    static class ApiKeyFilter extends OncePerRequestFilter {

        private final String expectedKey;

        ApiKeyFilter(String expectedKey) {
            this.expectedKey = expectedKey;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
                throws ServletException, IOException {

            String path = req.getRequestURI();
            String provided = req.getHeader(HEADER_API_KEY);

            // key 匹配 → 一律认证（即便后续 denyAll，也是"已认证但无权限"=403，更语义化）
            if (provided != null && provided.equals(expectedKey)) {
                var auth = new UsernamePasswordAuthenticationToken(
                        "api-client", null,
                        AuthorityUtils.createAuthorityList("ROLE_API_CLIENT"));
                SecurityContextHolder.getContext().setAuthentication(auth);
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
    }
}
