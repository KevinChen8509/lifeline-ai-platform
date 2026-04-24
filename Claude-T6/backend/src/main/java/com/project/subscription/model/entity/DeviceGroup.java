package com.project.subscription.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "device_group")
public class DeviceGroup extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "group_type", nullable = false)
    private Short groupType = 0;
}
