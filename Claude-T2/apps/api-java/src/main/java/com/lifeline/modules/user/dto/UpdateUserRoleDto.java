package com.lifeline.modules.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateUserRoleDto {

    @NotBlank(message = "角色ID不能为空")
    private String roleId;
}
