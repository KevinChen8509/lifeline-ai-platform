package com.datafabric.dataservice.governance;

import com.datafabric.dataservice.domain.CustomerProfileDto;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DataMaskingAspect 单元测试
 *
 * 验证：
 *   1. ADMIN 角色不脱敏（full 视图）
 *   2. SUPPORT 角色全脱敏 + 隐藏风险分
 *   3. brief 视图强制隐藏身份证和风险分（无视角色）
 *   4. role 为 null（未认证）走 SUPPORT 策略
 */
class DataMaskingAspectTest {

    private DataMaskingAspect aspect;
    private MetadataClient metadata;
    private ProceedingJoinPoint pjp;

    private static final CustomerProfileDto ORIGINAL = new CustomerProfileDto(
            "C0001", "李娜", "13800000001", "110101199001011234",
            "VIP3", "华东", 42L, 128000.0, "high", 88,
            "2023-01-15 10:30:00", "2026-07-20 14:00:00");

    @BeforeEach
    void setUp() throws Throwable {
        metadata = mock(MetadataClient.class);
        pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn(ORIGINAL);

        aspect = new DataMaskingAspect(metadata);
    }

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void mockRole(String role) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        if (role != null) req.addHeader("X-User-Role", role);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    }

    @Test
    void admin_full_view_no_masking() throws Throwable {
        mockRole("ADMIN");
        when(metadata.getPolicy("C0001", "ADMIN")).thenReturn(FieldPolicy.none());

        CustomerProfileDto result = (CustomerProfileDto) aspect.applyPolicy(pjp, buildAnnotation("full"));

        assertEquals("13800000001", result.phone(), "ADMIN full 视图不脱敏 phone");
        assertEquals("110101199001011234", result.idCard(), "ADMIN full 视图不脱敏 idCard");
        assertEquals(88, result.riskScore(), "ADMIN full 视图不隐藏风险分");
    }

    @Test
    void support_full_view_full_masking() throws Throwable {
        mockRole("SUPPORT");
        when(metadata.getPolicy("C0001", "SUPPORT")).thenReturn(new FieldPolicy(true, true, true));

        CustomerProfileDto result = (CustomerProfileDto) aspect.applyPolicy(pjp, buildAnnotation("full"));

        assertEquals("138****0001", result.phone(), "SUPPORT 脱敏 phone");
        assertEquals("110101********1234", result.idCard(), "SUPPORT 脱敏 idCard");
        assertNull(result.riskScore(), "SUPPORT 隐藏风险分");
    }

    @Test
    void brief_view_forces_strong_masking_even_for_admin() throws Throwable {
        mockRole("ADMIN");
        when(metadata.getPolicy("C0001", "ADMIN")).thenReturn(FieldPolicy.none());

        CustomerProfileDto result = (CustomerProfileDto) aspect.applyPolicy(pjp, buildAnnotation("brief"));

        assertEquals("110101********1234", result.idCard(), "brief 视图强制脱敏 idCard");
        assertNull(result.riskScore(), "brief 视图强制隐藏风险分");
    }

    @Test
    void anonymous_role_uses_strict_policy() throws Throwable {
        mockRole(null);
        when(metadata.getPolicy(eq("C0001"), isNull())).thenReturn(FieldPolicy.all());

        CustomerProfileDto result = (CustomerProfileDto) aspect.applyPolicy(pjp, buildAnnotation("full"));

        assertEquals("138****0001", result.phone());
        assertEquals("110101********1234", result.idCard());
        assertNull(result.riskScore());
    }

    @Test
    void non_dto_returned_unchanged() throws Throwable {
        when(pjp.proceed()).thenReturn("not a dto");
        Object result = aspect.applyPolicy(pjp, buildAnnotation("full"));
        assertEquals("not a dto", result);
    }

    private GetProfile buildAnnotation(String view) {
        GetProfile ann = mock(GetProfile.class);
        when(ann.view()).thenReturn(view);
        return ann;
    }
}
