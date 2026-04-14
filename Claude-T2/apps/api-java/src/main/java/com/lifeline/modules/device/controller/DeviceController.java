package com.lifeline.modules.device.controller;

import com.lifeline.common.permission.RequirePermission;
import com.lifeline.common.response.PageResponse;
import com.lifeline.modules.device.dto.CreateDeviceDto;
import com.lifeline.modules.device.dto.UpdateDeviceDto;
import com.lifeline.modules.device.entity.Device;
import com.lifeline.modules.device.entity.DeviceModelBinding;
import com.lifeline.modules.device.entity.DeviceProtocol;
import com.lifeline.modules.device.entity.DeviceStatus;
import com.lifeline.modules.device.entity.DeviceStatusHistory;
import com.lifeline.modules.device.service.DeviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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

    // === Extended Features ===

    @GetMapping("/{id}/status-history")
    @RequirePermission(action = "read", subject = "Device")
    public PageResponse<DeviceStatusHistory> getStatusHistory(
            @PathVariable String id,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return deviceService.getStatusHistory(id, startDate, endDate, page, pageSize);
    }

    @PostMapping("/{id}/activate")
    @RequirePermission(action = "manage", subject = "Device")
    public Map<String, Object> activate(@PathVariable String id) {
        return deviceService.activate(id);
    }

    @PostMapping("/{id}/ota")
    @RequirePermission(action = "manage", subject = "Device")
    public Map<String, Object> otaUpgrade(@PathVariable String id, @RequestBody Map<String, String> body) {
        return deviceService.otaUpgrade(id, body.get("targetVersion"));
    }

    @PostMapping("/scan-register")
    @RequirePermission(action = "create", subject = "Device")
    public Map<String, Object> scanRegister(@RequestBody Map<String, String> body) {
        return deviceService.scanRegister(body.get("qrData"));
    }

    @PutMapping("/{id}/project")
    @RequirePermission(action = "manage", subject = "Device")
    public Device assignProject(@PathVariable String id, @RequestBody Map<String, String> body) {
        return deviceService.assignProject(id, body.get("projectId"));
    }

    @PostMapping("/batch-assign-project")
    @RequirePermission(action = "manage", subject = "Device")
    public Map<String, Object> batchAssignProject(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> deviceIds = (List<String>) body.get("deviceIds");
        String projectId = (String) body.get("projectId");
        return deviceService.batchAssignProject(deviceIds, projectId);
    }

    @PostMapping("/batch-config")
    @RequirePermission(action = "manage", subject = "Device")
    public Map<String, Object> batchConfig(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> deviceIds = (List<String>) body.get("deviceIds");
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) body.get("config");
        return deviceService.batchConfig(deviceIds, config);
    }

    @PostMapping("/{id}/models")
    @RequirePermission(action = "manage", subject = "Device")
    public Map<String, Object> bindModels(@PathVariable String id, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> modelIds = (List<String>) body.get("modelIds");
        return deviceService.bindModels(id, modelIds);
    }

    @DeleteMapping("/{id}/models/{modelId}")
    @RequirePermission(action = "manage", subject = "Device")
    public Map<String, Object> unbindModel(@PathVariable String id, @PathVariable String modelId) {
        return deviceService.unbindModel(id, modelId);
    }

    @GetMapping("/{id}/models")
    @RequirePermission(action = "read", subject = "Device")
    public PageResponse<DeviceModelBinding> getBoundModels(
            @PathVariable String id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return deviceService.getBoundModels(id, page, pageSize);
    }
}
