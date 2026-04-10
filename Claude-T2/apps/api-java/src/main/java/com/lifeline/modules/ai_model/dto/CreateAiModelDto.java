package com.lifeline.modules.ai_model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateAiModelDto {

    @NotBlank(message = "模型名称不能为空")
    @Size(max = 100)
    private String name;

    @NotBlank(message = "模型编码不能为空")
    @Size(max = 50)
    private String code;

    @NotBlank(message = "版本号不能为空")
    @Size(max = 20)
    private String version;

    @NotBlank(message = "模型类型不能为空")
    private String type;

    private String description;
    private String fileUrl;
    private Integer fileSize;
    private String checksum;
    private String specs;
    private String applicableDeviceTypes;
}
