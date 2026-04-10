package com.lifeline.modules.device.dto;

import lombok.Data;

@Data
public class UpdateDeviceDto {

    private String name;
    private String deviceType;
    private String model;
    private String manufacturer;
    private String projectId;
    private String config;
    private String protocol;
    private String firmwareVersion;
    private String description;
}
