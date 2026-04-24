package com.project.subscription.service;

import com.project.subscription.model.entity.Device;
import com.project.subscription.model.entity.DeviceDataPoint;
import com.project.subscription.model.entity.DeviceGroup;
import com.project.subscription.model.entity.DeviceGroupMember;
import com.project.subscription.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final DeviceDataPointRepository dataPointRepository;
    private final DeviceGroupRepository groupRepository;
    private final DeviceGroupMemberRepository groupMemberRepository;

    public Page<Device> listDevices(Long tenantId, Long productId, int page, int size) {
        if (productId != null) {
            return deviceRepository.findByTenantIdAndProductId(tenantId, productId, PageRequest.of(page, size));
        }
        return deviceRepository.findByTenantId(tenantId, PageRequest.of(page, size));
    }

    public Device getDevice(Long id, Long tenantId) {
        return deviceRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new RuntimeException("设备不存在"));
    }

    public List<DeviceDataPoint> getDataPoints(Long deviceId) {
        return dataPointRepository.findByDeviceId(deviceId);
    }

    public List<Map<String, Object>> getGroupTree(Long tenantId) {
        List<DeviceGroup> roots = groupRepository.findByTenantIdAndParentIdIsNull(tenantId);
        List<Map<String, Object>> tree = new ArrayList<>();
        for (DeviceGroup root : roots) {
            tree.add(buildGroupNode(root, tenantId));
        }
        return tree;
    }

    private Map<String, Object> buildGroupNode(DeviceGroup group, Long tenantId) {
        Map<String, Object> node = new HashMap<>();
        node.put("id", group.getId());
        node.put("name", group.getName());
        node.put("groupType", group.getGroupType());

        List<DeviceGroupMember> members = groupMemberRepository.findByGroupId(group.getId());
        node.put("deviceCount", members.size());

        List<DeviceGroup> children = groupRepository.findByTenantIdAndParentId(tenantId, group.getId());
        if (!children.isEmpty()) {
            List<Map<String, Object>> childNodes = new ArrayList<>();
            for (DeviceGroup child : children) {
                childNodes.add(buildGroupNode(child, tenantId));
            }
            node.put("children", childNodes);
        }
        return node;
    }
}
