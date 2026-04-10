package com.lifeline.modules.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateDeviceDto {

    @NotBlank(message = "设备名称不能为空")
    @Size(max = 100)
    private String name;

    @NotBlank(message = "序列号不能为空")
    @Size(max = 50)
    private String serialNumber;

    private String deviceType;
    private String model;
    private String manufacturer;
    private String projectId;
    private String source;
    private String config;
    private String protocol;
    private String firmwareVersion;
    private String description;
}
