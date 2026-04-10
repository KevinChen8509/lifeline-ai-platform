package com.lifeline.modules.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateProjectDto {

    @NotBlank(message = "项目名称不能为空")
    @Size(max = 100)
    private String name;

    @NotBlank(message = "项目编码不能为空")
    @Size(max = 20)
    private String code;

    private String description;
}
