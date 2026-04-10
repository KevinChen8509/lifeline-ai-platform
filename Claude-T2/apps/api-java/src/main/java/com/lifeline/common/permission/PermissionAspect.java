package com.lifeline.common.permission;

import com.lifeline.common.security.JwtUserDetails;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Aspect
@Component
public class PermissionAspect {

    private static final Map<String, Set<String>> ROLE_PERMISSIONS = Map.of(
            "ADMIN", Set.of("manage:all"),
            "OPERATOR", Set.of(
                    "read:Project",
                    "create:Device", "read:Device", "update:Device", "delete:Device",
                    "create:Model", "read:Model", "update:Model", "delete:Model",
                    "create:Alert", "read:Alert", "update:Alert", "delete:Alert",
                    "read:Telemetry", "read:ApiKey"
            ),
            "OBSERVER", Set.of(
                    "read:Project", "read:Device", "read:Model", "read:Alert",
                    "read:ApiKey", "read:Telemetry", "read:Webhook", "read:AuditLog"
            )
    );

    @Around("@annotation(com.lifeline.common.permission.RequirePermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequirePermission annotation = method.getAnnotation(RequirePermission.class);

        String requiredAction = annotation.action();
        String requiredSubject = annotation.subject();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getDetails() instanceof JwtUserDetails userDetails)) {
            throw new AccessDeniedException("用户未登录");
        }

        String role = userDetails.getRole().toUpperCase();
        Set<String> permissions = ROLE_PERMISSIONS.getOrDefault(role, Set.of());

        // ADMIN with manage:all has everything
        if (permissions.contains("manage:all")) {
            return joinPoint.proceed();
        }

        // Check exact match or manage:subject
        String required = requiredAction + ":" + requiredSubject;
        if (permissions.contains(required) || permissions.contains("manage:" + requiredSubject)) {
            return joinPoint.proceed();
        }

        throw new AccessDeniedException("没有权限执行此操作: " + requiredAction + " " + requiredSubject);
    }
}
