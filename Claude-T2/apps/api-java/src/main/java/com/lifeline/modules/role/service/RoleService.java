package com.lifeline.modules.role.service;

import com.lifeline.common.exception.BusinessException;
import com.lifeline.modules.role.dto.CreateRoleDto;
import com.lifeline.modules.role.dto.UpdateRoleDto;
import com.lifeline.modules.role.entity.Role;
import com.lifeline.modules.role.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;

    public List<Role> findAll() {
        return roleRepository.findAll(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.ASC, "createdAt"));
    }

    public Role findOne(String id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Role with ID \"" + id + "\" not found"));
    }

    public Role findByCode(String code) {
        return roleRepository.findByCode(code).orElse(null);
    }

    @Transactional
    public Role create(CreateRoleDto dto) {
        Role role = new Role();
        role.setName(dto.getName());
        role.setCode(dto.getCode());
        role.setDescription(dto.getDescription());
        role.setPermissions(dto.getPermissions());
        return roleRepository.save(role);
    }

    @Transactional
    public Role update(String id, UpdateRoleDto dto) {
        Role role = findOne(id);
        if (dto.getName() != null) role.setName(dto.getName());
        if (dto.getDescription() != null) role.setDescription(dto.getDescription());
        if (dto.getPermissions() != null) role.setPermissions(dto.getPermissions());
        return roleRepository.save(role);
    }

    @Transactional
    public void remove(String id) {
        Role role = findOne(id);
        roleRepository.delete(role);
    }

    @Transactional
    public void seedDefaultRoles() {
        seedIfNotExists("管理员", "admin", "系统管理员，拥有所有权限", new String[]{"*"});
        seedIfNotExists("运维员", "operator", "运维人员，可操作设备和处理预警",
                new String[]{"device:read", "device:write", "alert:read", "alert:write", "telemetry:read"});
        seedIfNotExists("观察员", "observer", "观察员，只读权限",
                new String[]{"device:read", "alert:read", "telemetry:read"});
    }

    private void seedIfNotExists(String name, String code, String desc, String[] perms) {
        if (roleRepository.findByCode(code).isEmpty()) {
            Role role = new Role();
            role.setName(name);
            role.setCode(code);
            role.setDescription(desc);
            role.setPermissions(perms);
            roleRepository.save(role);
        }
    }
}
