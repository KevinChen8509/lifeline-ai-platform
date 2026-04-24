package com.project.subscription.repository;

import com.project.subscription.model.entity.DeviceGroupMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeviceGroupMemberRepository extends JpaRepository<DeviceGroupMember, Long> {
    List<DeviceGroupMember> findByGroupId(Long groupId);
    void deleteByGroupId(Long groupId);
}
