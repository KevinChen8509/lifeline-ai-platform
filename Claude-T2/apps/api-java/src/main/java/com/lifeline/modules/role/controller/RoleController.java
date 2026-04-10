package com.lifeline.modules.role.controller;

import com.lifeline.common.permission.RequirePermission;
import com.lifeline.modules.role.dto.CreateRoleDto;
import com.lifeline.modules.role.dto.UpdateRoleDto;
import com.lifeline.modules.role.entity.Role;
import com.lifeline.modules.role.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @RequirePermission(action = "manage", subject = "Role")
    public List<Role> findAll() {
        return roleService.findAll();
    }

    @GetMapping("/{id}")
    @RequirePermission(action = "manage", subject = "Role")
    public Role findOne(@PathVariable String id) {
        return roleService.findOne(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(action = "manage", subject = "Role")
    public Role create(@Valid @RequestBody CreateRoleDto dto) {
        return roleService.create(dto);
    }

    @PutMapping("/{id}")
    @RequirePermission(action = "manage", subject = "Role")
    public Role update(@PathVariable String id, @Valid @RequestBody UpdateRoleDto dto) {
        return roleService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission(action = "manage", subject = "Role")
    public void remove(@PathVariable String id) {
        roleService.remove(id);
    }
}
