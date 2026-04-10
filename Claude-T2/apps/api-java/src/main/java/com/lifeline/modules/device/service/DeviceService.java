package com.lifeline.modules.device.service;

import com.lifeline.common.exception.BusinessException;
import com.lifeline.common.response.PageResponse;
import com.lifeline.modules.device.dto.CreateDeviceDto;
import com.lifeline.modules.device.dto.UpdateDeviceDto;
import com.lifeline.modules.device.entity.Device;
import com.lifeline.modules.device.entity.DeviceProtocol;
import com.lifeline.modules.device.entity.DeviceSource;
import com.lifeline.modules.device.entity.DeviceStatus;
import com.lifeline.modules.device.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;

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
        device.setConfig(dto.getConfig());
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
}
