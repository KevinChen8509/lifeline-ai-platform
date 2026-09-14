package com.datafabric.dataservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * production-audit Blocker 1 修复：prod profile 启动守卫。
 *
 * 背景：application.yml 提交了 PoC 默认凭据（全局 API Key / 源库密码 / Cube secret），
 * 且默认 key 长度 44 &gt; min-key-length 16 —— SecurityConfig 的长度 WARN 静默放行，
 * 生产忘配 env 就等于带着仓库里公开的凭据上线。
 *
 * 策略：仅 prod profile 激活（dev/PoC 不设 profile，零影响）；命中任一默认值即
 * IllegalStateException 拒绝启动，错误信息列出全部违规属性与对应环境变量。
 */
@Configuration
@Profile("prod")
public class ProdSecretGuard implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(ProdSecretGuard.class);

    /** 提交在库的 PoC 默认值（属性名 → 哨兵值）；env 覆盖任一即解除该项 */
    private static final Map<String, String> COMMITTED_DEFAULTS;

    /** 提示用的 属性名 → 环境变量 映射（与 application.yml 的 ${ENV:default} 对应） */
    private static final Map<String, String> ENV_HINTS;

    static {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("datafabric.security.api-key", "datafabric-poc-api-key-2026-please-rotate");
        defaults.put("datafabric.raw-db.mysql-password", "poc123");
        defaults.put("datafabric.raw-db.postgres-password", "external123");
        defaults.put("cube.api-secret", "datafabric-poc-secret-2026");
        COMMITTED_DEFAULTS = defaults;

        Map<String, String> hints = new LinkedHashMap<>();
        hints.put("datafabric.security.api-key", "DATAFABRIC_API_KEY");
        hints.put("datafabric.raw-db.mysql-password", "MYSQL_PASSWORD");
        hints.put("datafabric.raw-db.postgres-password", "PG_PASSWORD");
        hints.put("cube.api-secret", "CUBEJS_API_SECRET");
        ENV_HINTS = hints;
    }

    private final Environment env;

    public ProdSecretGuard(Environment env) {
        this.env = env;
    }

    @Override
    public void afterPropertiesSet() {
        List<String> offenders = new ArrayList<>();
        List<String> hints = new ArrayList<>();
        COMMITTED_DEFAULTS.forEach((prop, sentinel) -> {
            if (sentinel.equals(env.getProperty(prop))) {
                offenders.add(prop);
                hints.add(ENV_HINTS.get(prop));
            }
        });
        if (!offenders.isEmpty()) {
            throw new IllegalStateException(
                    "prod 环境检测到未替换的 PoC 默认凭据（拒绝启动）: " + offenders
                            + " —— 请通过环境变量注入真实值后重启: " + hints
                            + "。dev/PoC 环境请勿激活 prod profile。");
        }
        log.info("prod 凭据守卫通过：未检测到提交在库的默认凭据（{} 项已核查）", COMMITTED_DEFAULTS.size());
    }
}
