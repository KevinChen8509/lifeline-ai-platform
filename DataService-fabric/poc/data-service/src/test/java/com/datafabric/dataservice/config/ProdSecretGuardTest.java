package com.datafabric.dataservice.config;

import com.datafabric.dataservice.DataServiceApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * production-audit Blocker 1：prod profile 凭据守卫。
 *
 * 用例 1 走完整应用（真实证明 application.yml 默认值在 prod 下被拒绝启动）；
 * 其余用例用 ApplicationContextRunner 切片只加载守卫 —— 完整应用在 prod 下
 * 还有其他既有缺口（StubMetadataClient @Profile("!prod") 无替身），不属于本守卫的验证范围。
 */
class ProdSecretGuardTest {

    private static final String[] REAL_SECRETS = {
            "datafabric.security.api-key=prod-real-key-0123456789abcdef",
            "datafabric.raw-db.mysql-password=prod-real-mysql-pw",
            "datafabric.raw-db.postgres-password=prod-real-pg-pw",
            "cube.api-secret=prod-real-cube-secret"};

    @Test
    @DisplayName("prod + 提交在库的默认凭据 → 完整应用拒绝启动，报出违规属性与环境变量")
    void prodWithCommittedDefaults_refusesToBoot() {
        Throwable thrown = null;
        try {
            new SpringApplicationBuilder(DataServiceApplication.class)
                    .properties("spring.profiles.active=prod", "server.port=0")
                    .run();
        } catch (RuntimeException ex) {
            thrown = ex;
        }
        if (thrown == null) {
            fail("prod + 默认凭据竟然启动成功了");
        }
        Throwable root = thrown;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        assertThat(root).hasMessageContaining("datafabric.security.api-key")
                .hasMessageContaining("datafabric.raw-db.mysql-password")
                .hasMessageContaining("cube.api-secret")
                .hasMessageContaining("DATAFABRIC_API_KEY");
    }

    @Test
    @DisplayName("prod + 注入真实凭据（切片）→ 守卫放行")
    void prodWithInjectedSecrets_guardPasses() {
        runner().withPropertyValues(REAL_SECRETS).run(ctx ->
                assertThat(ctx).hasNotFailed().hasSingleBean(ProdSecretGuard.class));
    }

    @Test
    @DisplayName("无 prod profile（dev/PoC）+ 默认凭据 → 守卫不激活，零影响")
    void devProfile_guardInactive() {
        new ApplicationContextRunner()
                .withPropertyValues("datafabric.security.api-key=datafabric-poc-api-key-2026-please-rotate")
                .withUserConfiguration(ProdSecretGuard.class)
                .run(ctx -> assertThat(ctx).hasNotFailed().doesNotHaveBean(ProdSecretGuard.class));
    }

    private static ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withPropertyValues("spring.profiles.active=prod")
                .withUserConfiguration(ProdSecretGuard.class);
    }
}
