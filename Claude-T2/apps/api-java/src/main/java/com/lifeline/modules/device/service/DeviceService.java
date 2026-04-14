package com.lifeline.modules.device.service;

import com.lifeline.common.exception.BusinessException;
import com.lifeline.common.response.PageResponse;
import com.lifeline.modules.ai_model.entity.AiModel;
import com.lifeline.modules.ai_model.repository.AiModelRepository;
import com.lifeline.modules.device.dto.CreateDeviceDto;
import com.lifeline.modules.device.dto.UpdateDeviceDto;
import com.lifeline.modules.device.entity.DeviceModelBinding;
import com.lifeline.modules.device.entity.DeviceStatusHistory;
import com.lifeline.modules.device.entity.enums.BindingStatus;
import com.lifeline.modules.device.repository.DeviceModelBindingRepository;
import com.lifeline.modules.device.repository.DeviceStatusHistoryRepository;
import com.lifeline.modules.device.entity.Device;
import com.lifeline.modules.device.entity.DeviceProtocol;
import com.lifeline.modules.device.entity.DeviceSource;
import com.lifeline.modules.device.entity.DeviceStatus;
import com.lifeline.modules.device.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final DeviceStatusHistoryRepository statusHistoryRepository;
    private final DeviceModelBindingRepository modelBindingRepository;
    private final AiModelRepository aiModelRepository;

    private static final Map<String, String[]> DEVICE_TYPE_MAP = Map.of(
            "WATER_LEVEL_SENSOR", new String[]{"液位传感器", "生命线科技"},
            "FLOW_METER", new String[]{"流量计", "生命线科技"},
            "PRESSURE_SENSOR", new String[]{"压力传感器", "生命线科技"},
            "TEMPERATURE_SENSOR", new String[]{"温度传感器", "生命线科技"},
            "HUMIDITY_SENSOR", new String[]{"湿度传感器", "生命线科技"}
    );

    public PageResponse<Device> findAll(int page, int pageSize, DeviceStatus status,
                                        String projectId, DeviceProtocol protocol, String search) {
        String statusStr = status != null ? status.name() : null;
        String protocolStr = protocol != null ? protocol.name() : null;
        Page<Device> result = deviceRepository.findAllWithFilters(
                statusStr, projectId, protocolStr, search,
                PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return PageResponse.of(result.getContent(), result.getTotalElements(), page, pageSize);
    }

    public Device findOne(String id) {
        return deviceRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("设备不存在: " + id));
    }

    @Transactional
    public Device create(CreateDeviceDto dto) {
        deviceRepository.findBySerialNumber(dto.getSerialNumber()).ifPresent(d -> {
            throw BusinessException.badRequest("序列号已存在: " + dto.getSerialNumber());
        });
        Device device = new Device();
        device.setName(dto.getName());
        device.setSerialNumber(dto.getSerialNumber());
        device.setDeviceType(dto.getDeviceType());
        device.setModel(dto.getModel());
        device.setManufacturer(dto.getManufacturer());
        device.setProjectId(dto.getProjectId());
        if (dto.getSource() != null) {
            device.setSource(DeviceSource.valueOf(dto.getSource()));
        }
        device.setConfig(dto.getConfig() != null ? dto.getConfig() : "{}");
        if (dto.getProtocol() != null) {
            device.setProtocol(DeviceProtocol.valueOf(dto.getProtocol()));
        }
        device.setFirmwareVersion(dto.getFirmwareVersion());
        device.setDescription(dto.getDescription());
        device.setStatus(DeviceStatus.pending);
        return deviceRepository.save(device);
    }

    @Transactional
    public Device update(String id, UpdateDeviceDto dto) {
        Device device = findOne(id);
        if (dto.getName() != null) device.setName(dto.getName());
        if (dto.getDeviceType() != null) device.setDeviceType(dto.getDeviceType());
        if (dto.getModel() != null) device.setModel(dto.getModel());
        if (dto.getManufacturer() != null) device.setManufacturer(dto.getManufacturer());
        if (dto.getProjectId() != null) device.setProjectId(dto.getProjectId());
        if (dto.getConfig() != null) device.setConfig(dto.getConfig());
        if (dto.getProtocol() != null) device.setProtocol(DeviceProtocol.valueOf(dto.getProtocol()));
        if (dto.getFirmwareVersion() != null) device.setFirmwareVersion(dto.getFirmwareVersion());
        if (dto.getDescription() != null) device.setDescription(dto.getDescription());
        return deviceRepository.save(device);
    }

    @Transactional
    public Device updateConfig(String id, String config) {
        Device device = findOne(id);
        device.setConfig(config);
        return deviceRepository.save(device);
    }

    @Transactional
    public void remove(String id) {
        deviceRepository.delete(findOne(id));
    }

    // === Extended Features ===

    public PageResponse<DeviceStatusHistory> getStatusHistory(String deviceId, String startDate, String endDate,
                                                               int page, int pageSize) {
        findOne(deviceId); // verify exists
        Page<DeviceStatusHistory> result = statusHistoryRepository.findByDeviceIdWithDateFilter(
                deviceId, startDate, endDate,
                PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "timestamp"))
        );
        return PageResponse.of(result.getContent(), result.getTotalElements(), page, pageSize);
    }

    @Transactional
    public Map<String, Object> activate(String id) {
        Device device = findOne(id);
        if (device.getStatus() != DeviceStatus.pending && device.getStatus() != DeviceStatus.failed) {
            throw BusinessException.badRequest("只能激活待处理或失败的设备");
        }
        DeviceStatus oldStatus = device.getStatus();
        device.setStatus(DeviceStatus.activating);
        deviceRepository.save(device);
        recordStatusChange(id, oldStatus, DeviceStatus.activating, "设备激活", null, null);

        Map<String, Object> activationCommand = new LinkedHashMap<>();
        activationCommand.put("cmd", "activate");
        activationCommand.put("timestamp", LocalDateTime.now().toString());
        activationCommand.put("server", "mqtt.lifeline.com");
        activationCommand.put("port", 1883);

        return Map.of("device", device, "activationCommand", activationCommand);
    }

    @Transactional
    public Map<String, Object> otaUpgrade(String id, String targetVersion) {
        Device device = findOne(id);
        if (device.getStatus() != DeviceStatus.online) {
            throw BusinessException.badRequest("只能对在线设备进行OTA升级");
        }
        if (targetVersion != null && targetVersion.equals(device.getFirmwareVersion())) {
            throw BusinessException.badRequest("目标版本与当前版本相同");
        }

        String taskId = "ota-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 10000);

        Map<String, Object> otaCommand = new LinkedHashMap<>();
        otaCommand.put("cmd", "ota_upgrade");
        otaCommand.put("version", targetVersion);
        otaCommand.put("url", "https://firmware.lifeline.com/" + targetVersion + ".bin");
        otaCommand.put("timestamp", LocalDateTime.now().toString());

        return Map.of(
                "taskId", taskId,
                "status", "downloading",
                "progress", 0,
                "device", device,
                "otaCommand", otaCommand
        );
    }

    @Transactional
    public Map<String, Object> scanRegister(String qrData) {
        // Parse QR format: LK://{serialNumber}:{deviceType}:{factoryId}
        if (qrData == null || !qrData.startsWith("LK://")) {
            throw BusinessException.badRequest("无效的二维码数据");
        }
        String[] parts = qrData.substring(5).split(":");
        if (parts.length < 1) {
            throw BusinessException.badRequest("无效的二维码格式");
        }
        String serialNumber = parts[0];
        String deviceType = parts.length > 1 ? parts[1] : null;
        String factoryId = parts.length > 2 ? parts[2] : null;

        // Check if device already exists
        Optional<Device> existing = deviceRepository.findBySerialNumber(serialNumber);
        if (existing.isPresent()) {
            return Map.of("isNew", false, "device", existing.get(),
                    "preview", Map.of("deviceType", (Object) null, "recommendedProject", null));
        }

        // Create new device
        String name = deviceType != null && DEVICE_TYPE_MAP.containsKey(deviceType)
                ? DEVICE_TYPE_MAP.get(deviceType)[0] : "未知设备";
        String manufacturer = deviceType != null && DEVICE_TYPE_MAP.containsKey(deviceType)
                ? DEVICE_TYPE_MAP.get(deviceType)[1] : null;

        Device device = new Device();
        device.setName(name);
        device.setSerialNumber(serialNumber);
        device.setDeviceType(deviceType);
        device.setManufacturer(manufacturer);
        device.setSource(DeviceSource.self_developed);
        device.setStatus(DeviceStatus.pending);
        device.setConfig("{}");
        deviceRepository.save(device);

        return Map.of("isNew", true, "device", device,
                "preview", Map.of("deviceType", deviceType, "recommendedProject", null));
    }

    @Transactional
    public Device assignProject(String id, String projectId) {
        Device device = findOne(id);
        String oldProjectId = device.getProjectId();
        device.setProjectId(projectId);
        return deviceRepository.save(device);
    }

    @Transactional
    public Map<String, Object> batchAssignProject(List<String> deviceIds, String projectId) {
        List<Device> devices = deviceRepository.findAllById(deviceIds);
        for (Device device : devices) {
            device.setProjectId(projectId);
        }
        deviceRepository.saveAll(devices);

        List<String> notFound = new ArrayList<>(deviceIds);
        devices.forEach(d -> notFound.remove(d.getId()));

        return Map.of("success", devices.size(), "failed", notFound);
    }

    @Transactional
    public Map<String, Object> batchConfig(List<String> deviceIds, Map<String, Object> config) {
        List<Device> devices = deviceRepository.findAllById(deviceIds);
        if (devices.isEmpty()) {
            throw BusinessException.notFound("未找到指定设备");
        }

        // Validate all same deviceType
        String firstType = devices.get(0).getDeviceType();
        for (Device d : devices) {
            if (!Objects.equals(d.getDeviceType(), firstType)) {
                throw BusinessException.badRequest("批量配置要求所有设备类型相同");
            }
        }

        String taskId = "batch-config-" + System.currentTimeMillis();
        int success = 0;
        List<Map<String, String>> errors = new ArrayList<>();

        for (Device d : devices) {
            if (d.getStatus() != DeviceStatus.online) {
                errors.add(Map.of("deviceId", d.getId(), "reason", "设备不在线"));
                continue;
            }
            // Merge config
            d.setConfig(config.toString());
            success++;
        }
        deviceRepository.saveAll(devices);

        String status = errors.isEmpty() ? "completed" : "partial_success";
        return Map.of("taskId", taskId, "status", status,
                "total", devices.size(), "success", success,
                "failed", errors.size(), "errors", errors);
    }

    @Transactional
    public Map<String, Object> bindModels(String deviceId, List<String> modelIds) {
        Device device = findOne(deviceId);
        if (device.getStatus() != DeviceStatus.online) {
            throw BusinessException.badRequest("只能为在线设备绑定模型");
        }

        List<DeviceModelBinding> bindings = new ArrayList<>();
        List<Map<String, Object>> modelCommands = new ArrayList<>();

        for (String modelId : modelIds) {
            AiModel aiModel = aiModelRepository.findById(modelId)
                    .orElseThrow(() -> BusinessException.notFound("模型不存在: " + modelId));

            if (aiModel.getStatus() != com.lifeline.modules.ai_model.entity.AiModelStatus.published) {
                throw BusinessException.badRequest("只能绑定已发布的模型: " + modelId);
            }

            // Skip if already bound
            if (modelBindingRepository.findByDeviceIdAndModelId(deviceId, modelId).isPresent()) {
                continue;
            }

            DeviceModelBinding binding = new DeviceModelBinding();
            binding.setDeviceId(deviceId);
            binding.setModelId(modelId);
            binding.setStatus(BindingStatus.pending);
            binding.setBoundVersion(aiModel.getVersion());
            binding.setBoundAt(LocalDateTime.now());
            modelBindingRepository.save(binding);
            bindings.add(binding);

            Map<String, Object> cmd = new LinkedHashMap<>();
            cmd.put("id", aiModel.getId());
            cmd.put("code", aiModel.getCode());
            cmd.put("version", aiModel.getVersion());
            cmd.put("url", aiModel.getFileUrl());
            cmd.put("checksum", aiModel.getChecksum());
            modelCommands.add(cmd);
        }

        Map<String, Object> mqttCommand = new LinkedHashMap<>();
        mqttCommand.put("cmd", "load_model");
        mqttCommand.put("models", modelCommands);
        mqttCommand.put("timestamp", LocalDateTime.now().toString());

        return Map.of("bindings", bindings, "mqttCommand", mqttCommand);
    }

    @Transactional
    public Map<String, Object> unbindModel(String deviceId, String modelId) {
        DeviceModelBinding binding = modelBindingRepository.findByDeviceIdAndModelId(deviceId, modelId)
                .orElseThrow(() -> BusinessException.notFound("绑定关系不存在"));
        modelBindingRepository.delete(binding);

        Map<String, Object> mqttCommand = new LinkedHashMap<>();
        mqttCommand.put("cmd", "unload_model");
        mqttCommand.put("modelId", modelId);
        mqttCommand.put("timestamp", LocalDateTime.now().toString());

        return Map.of("success", true, "mqttCommand", mqttCommand);
    }

    public PageResponse<DeviceModelBinding> getBoundModels(String deviceId, int page, int pageSize) {
        findOne(deviceId); // verify exists
        Page<DeviceModelBinding> result = modelBindingRepository.findByDeviceId(
                deviceId, PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "boundAt"))
        );
        return PageResponse.of(result.getContent(), result.getTotalElements(), page, pageSize);
    }

    private void recordStatusChange(String deviceId, DeviceStatus from, DeviceStatus to,
                                     String reason, String operatorId, String metadata) {
        DeviceStatusHistory history = new DeviceStatusHistory();
        history.setDeviceId(deviceId);
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setReason(reason);
        history.setOperatorId(operatorId);
        history.setMetadata(metadata);
        history.setTimestamp(LocalDateTime.now());
        statusHistoryRepository.save(history);
    }
}
