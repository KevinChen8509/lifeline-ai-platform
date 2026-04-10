package com.lifeline.modules.auth.controller;

import com.lifeline.modules.auth.dto.AuthResponseDto;
import com.lifeline.modules.auth.dto.LoginDto;
import com.lifeline.modules.auth.dto.RefreshTokenDto;
import com.lifeline.modules.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public AuthResponseDto login(@Valid @RequestBody LoginDto dto) {
        return authService.login(dto);
    }

    @PostMapping("/refresh")
    public AuthResponseDto refreshToken(@Valid @RequestBody RefreshTokenDto dto) {
        return authService.refreshToken(dto);
    }

    @PostMapping("/logout")
    public Map<String, String> logout() {
        String userId = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getName();
        authService.logout(userId);
        return Map.of("message", "登出成功");
    }

    @GetMapping("/permissions")
    public Map<String, List<Map<String, String>>> getPermissions() {
        String userId = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getName();
        List<Map<String, String>> permissions = authService.getPermissions(userId);
        return Map.of("permissions", permissions);
    }
}
