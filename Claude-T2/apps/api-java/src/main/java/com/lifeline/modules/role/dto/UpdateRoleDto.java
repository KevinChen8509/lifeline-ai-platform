package com.lifeline.modules.role.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateRoleDto {

    @Size(max = 50)
    private String name;

    @Size(max = 200)
    private String description;

    private String[] permissions;
}
