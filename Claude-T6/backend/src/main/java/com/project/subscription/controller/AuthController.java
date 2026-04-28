package com.project.subscription.controller;

import com.project.subscription.config.JwtUtil;
import com.project.subscription.model.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtUtil jwtUtil;

    /**
     * Dev-only: generates a JWT for a given userId + tenantId.
     * Production should use a real identity provider (OAuth2 / LDAP).
     */
    @PostMapping("/token")
    public ApiResponse<Map<String, Object>> generateToken(@RequestBody TokenRequest req) {
        String token = jwtUtil.generateToken(req.userId(), req.tenantId());
        return ApiResponse.ok(Map.of(
            "token", token,
            "userId", req.userId(),
            "tenantId", req.tenantId()
        ));
    }

    public record TokenRequest(Long userId, Long tenantId) {}
}
