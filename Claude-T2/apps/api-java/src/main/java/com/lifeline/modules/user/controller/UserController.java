package com.lifeline.modules.user.controller;

import com.lifeline.common.permission.RequirePermission;
import com.lifeline.common.response.PageResponse;
import com.lifeline.modules.user.dto.CreateUserDto;
import com.lifeline.modules.user.dto.UpdateUserDto;
import com.lifeline.modules.user.dto.UpdateUserRoleDto;
import com.lifeline.modules.user.dto.UpdateUserStatusDto;
import com.lifeline.modules.user.entity.User;
import com.lifeline.modules.user.entity.UserStatus;
import com.lifeline.modules.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/check-username")
    @RequirePermission(action = "manage", subject = "User")
    public Map<String, Boolean> checkUsername(@RequestParam String username) {
        return Map.of("available", userService.checkUsername(username));
    }

    @GetMapping
    @RequirePermission(action = "manage", subject = "User")
    public PageResponse<User> findAll(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String roleId) {
        return userService.findAll(page, pageSize, status, search, roleId);
    }

    @GetMapping("/{id}")
    @RequirePermission(action = "manage", subject = "User")
    public User findOne(@PathVariable String id) {
        return userService.findOne(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(action = "manage", subject = "User")
    public User create(@Valid @RequestBody CreateUserDto dto) {
        return userService.create(dto);
    }

    @PutMapping("/{id}")
    @RequirePermission(action = "manage", subject = "User")
    public User update(@PathVariable String id, @Valid @RequestBody UpdateUserDto dto) {
        return userService.update(id, dto);
    }

    @PutMapping("/{id}/status")
    @RequirePermission(action = "manage", subject = "User")
    public User updateStatus(@PathVariable String id, @Valid @RequestBody UpdateUserStatusDto dto) {
        return userService.updateStatus(id, dto.getStatus());
    }

    @PutMapping("/{id}/role")
    @RequirePermission(action = "manage", subject = "User")
    public User updateRole(@PathVariable String id, @Valid @RequestBody UpdateUserRoleDto dto) {
        String operatorId = SecurityContextHolder.getContext().getAuthentication().getName();
        return userService.updateRole(id, dto, operatorId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission(action = "manage", subject = "User")
    public void remove(@PathVariable String id) {
        userService.remove(id);
    }
}
