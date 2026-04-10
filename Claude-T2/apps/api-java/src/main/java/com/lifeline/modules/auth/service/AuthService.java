package com.lifeline.modules.auth.service;

import com.lifeline.common.exception.BusinessException;
import com.lifeline.common.security.JwtUtils;
import com.lifeline.modules.auth.dto.AuthResponseDto;
import com.lifeline.modules.auth.dto.LoginDto;
import com.lifeline.modules.auth.dto.RefreshTokenDto;
import com.lifeline.modules.user.entity.User;
import com.lifeline.modules.user.entity.UserStatus;
import com.lifeline.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final JwtUtils jwtUtils;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;

    private static final Map<String, List<String>> ROLE_PERMISSIONS = Map.of(
            "admin", List.of("manage:all"),
            "operator", List.of("read:Project", "manage:Device", "manage:Model", "manage:Alert", "read:Telemetry", "read:ApiKey"),
            "observer", List.of("read:Project", "read:Device", "read:Model", "read:Alert", "read:ApiKey", "read:Telemetry", "read:Webhook", "read:AuditLog")
    );

    public AuthResponseDto login(LoginDto dto) {
        User user = userService.findByUsername(dto.getUsername());
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            throw BusinessException.unauthorized("用户名或密码错误");
        }
        if (user.getStatus() == UserStatus.DISABLED) {
            throw BusinessException.forbidden("账号已被禁用");
        }
        if (user.getStatus() == UserStatus.PENDING) {
            throw BusinessException.forbidden("账号尚未激活");
        }

        String roleCode = user.getRole() != null ? user.getRole().getCode() : "observer";
        String accessToken = jwtUtils.generateAccessToken(user.getId(), user.getUsername(), roleCode);
        String refreshToken = jwtUtils.generateRefreshToken(user.getId());

        String tokenId = jwtUtils.getRefreshTokenId(refreshToken);
        redisTemplate.opsForValue().set(
                "refresh:" + user.getId() + ":" + tokenId,
                refreshToken, 7, TimeUnit.DAYS
        );

        List<Map<String, String>> permissions = getPermissionsForRole(roleCode);
        return new AuthResponseDto(accessToken, refreshToken, user, permissions);
    }

    public AuthResponseDto refreshToken(RefreshTokenDto dto) {
        if (!jwtUtils.validateToken(dto.getRefreshToken())) {
            throw BusinessException.unauthorized("无效的Refresh Token");
        }
        if (!jwtUtils.isRefreshToken(dto.getRefreshToken())) {
            throw BusinessException.unauthorized("无效的Refresh Token");
        }

        String userId = jwtUtils.getUserId(dto.getRefreshToken());
        String tokenId = jwtUtils.getRefreshTokenId(dto.getRefreshToken());
        String key = "refresh:" + userId + ":" + tokenId;

        String stored = redisTemplate.opsForValue().getAndDelete(key);
        if (stored == null) {
            throw BusinessException.unauthorized("Refresh Token已失效或已被使用");
        }

        User user = userService.findOne(userId);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw BusinessException.unauthorized("用户已被禁用或未激活");
        }

        String roleCode = user.getRole() != null ? user.getRole().getCode() : "observer";
        String accessToken = jwtUtils.generateAccessToken(user.getId(), user.getUsername(), roleCode);
        String newRefreshToken = jwtUtils.generateRefreshToken(user.getId());

        String newTokenId = jwtUtils.getRefreshTokenId(newRefreshToken);
        redisTemplate.opsForValue().set(
                "refresh:" + user.getId() + ":" + newTokenId,
                newRefreshToken, 7, TimeUnit.DAYS
        );

        List<Map<String, String>> permissions = getPermissionsForRole(roleCode);
        return new AuthResponseDto(accessToken, newRefreshToken, user, permissions);
    }

    public void logout(String userId) {
        Set<String> keys = redisTemplate.keys("refresh:" + userId + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    public List<Map<String, String>> getPermissions(String userId) {
        User user = userService.findOne(userId);
        String roleCode = user.getRole() != null ? user.getRole().getCode() : "observer";
        return getPermissionsForRole(roleCode);
    }

    private List<Map<String, String>> getPermissionsForRole(String roleCode) {
        List<String> perms = ROLE_PERMISSIONS.getOrDefault(roleCode, List.of());
        List<Map<String, String>> result = new ArrayList<>();
        for (String perm : perms) {
            String[] parts = perm.split(":", 2);
            result.add(Map.of("action", parts[0], "subject", parts[1]));
        }
        return result;
    }
}
