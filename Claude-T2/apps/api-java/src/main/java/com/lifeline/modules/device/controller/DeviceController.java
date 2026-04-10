package com.lifeline.modules.device.controller;

import com.lifeline.common.permission.RequirePermission;
import com.lifeline.common.response.PageResponse;
import com.lifeline.modules.device.dto.CreateDeviceDto;
import com.lifeline.modules.device.dto.UpdateDeviceDto;
import com.lifeline.modules.device.entity.Device;
import com.lifeline.modules.device.entity.DeviceProtocol;
import com.lifeline.modules.device.entity.DeviceStatus;
import com.lifeline.modules.device.service.DeviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @GetMapping
    @RequirePermission(action = "read", subject = "Device")
    public PageResponse<Device> findAll(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) DeviceStatus status,
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) DeviceProtocol protocol,
            @RequestParam(required = false) String search) {
        return deviceService.findAll(page, pageSize, status, projectId, protocol, search);
    }

    @GetMapping("/{id}")
    @RequirePermission(action = "read", subject = "Device")
    public Device findOne(@PathVariable String id) {
        return deviceService.findOne(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(action = "create", subject = "Device")
    public Device create(@Valid @RequestBody CreateDeviceDto dto) {
        return deviceService.create(dto);
    }

    @PutMapping("/{id}")
    @RequirePermission(action = "update", subject = "Device")
    public Device update(@PathVariable String id, @Valid @RequestBody UpdateDeviceDto dto) {
        return deviceService.update(id, dto);
    }

    @PutMapping("/{id}/config")
    @RequirePermission(action = "update", subject = "Device")
    public Device updateConfig(@PathVariable String id, @RequestBody Map<String, String> body) {
        return deviceService.updateConfig(id, body.get("config"));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission(action = "delete", subject = "Device")
    public void remove(@PathVariable String id) {
        deviceService.remove(id);
    }
}
