package com.lifeline.modules.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateRoleDto {

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 50)
    private String name;

    @NotBlank(message = "角色代码不能为空")
    @Size(max = 50)
    private String code;

    @Size(max = 200)
    private String description;

    private String[] permissions;
}
