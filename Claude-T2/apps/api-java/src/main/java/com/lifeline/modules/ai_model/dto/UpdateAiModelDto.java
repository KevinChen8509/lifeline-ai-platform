package com.lifeline.modules.ai_model.dto;

import lombok.Data;

@Data
public class UpdateAiModelDto {

    private String name;
    private String version;
    private String description;
    private String fileUrl;
    private Integer fileSize;
    private String checksum;
    private String specs;
    private String applicableDeviceTypes;
}
