package com.lifeline.modules.project.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProjectDto {

    @Size(max = 100)
    private String name;

    @Size(max = 20)
    private String code;

    private String description;
}
