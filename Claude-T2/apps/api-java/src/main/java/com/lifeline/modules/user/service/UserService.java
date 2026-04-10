package com.lifeline.modules.user.service;

import com.lifeline.common.exception.BusinessException;
import com.lifeline.common.response.PageResponse;
import com.lifeline.modules.role.service.RoleService;
import com.lifeline.modules.user.dto.CreateUserDto;
import com.lifeline.modules.user.dto.UpdateUserDto;
import com.lifeline.modules.user.dto.UpdateUserRoleDto;
import com.lifeline.modules.user.entity.User;
import com.lifeline.modules.user.entity.UserStatus;
import com.lifeline.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleService roleService;

    public PageResponse<User> findAll(int page, int pageSize, UserStatus status, String search, String roleId) {
        String statusStr = status != null ? status.name() : null;
        Page<User> result = userRepository.findAllWithFilters(
                statusStr, roleId, search,
                PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return PageResponse.of(result.getContent(), result.getTotalElements(), page, pageSize);
    }

    public User findOne(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("User with ID \"" + id + "\" not found"));
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username).orElse(null);
    }

    @Transactional
    public User create(CreateUserDto dto) {
        if (userRepository.findByUsername(dto.getUsername()).isPresent()) {
            throw BusinessException.badRequest("用户名已存在");
        }
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        user.setName(dto.getName());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setRoleId(dto.getRoleId());
        user.setStatus(UserStatus.PENDING);
        return userRepository.save(user);
    }

    public boolean checkUsername(String username) {
        return userRepository.findByUsername(username).isEmpty();
    }

    @Transactional
    public User update(String id, UpdateUserDto dto) {
        User user = findOne(id);
        if (dto.getName() != null) user.setName(dto.getName());
        if (dto.getEmail() != null) user.setEmail(dto.getEmail());
        if (dto.getPhone() != null) user.setPhone(dto.getPhone());
        return userRepository.save(user);
    }

    @Transactional
    public User updateStatus(String id, UserStatus status) {
        User user = findOne(id);
        user.setStatus(status);
        return userRepository.save(user);
    }

    @Transactional
    public User updateRole(String userId, UpdateUserRoleDto dto, String operatorId) {
        if (userId.equals(operatorId)) {
            throw BusinessException.badRequest("不能修改自己的角色");
        }
        roleService.findOne(dto.getRoleId());
        User user = findOne(userId);
        user.setRoleId(dto.getRoleId());
        return userRepository.save(user);
    }

    @Transactional
    public void remove(String id) {
        User user = findOne(id);
        user.setDeletedAt(java.time.LocalDateTime.now());
        userRepository.save(user);
    }
}
