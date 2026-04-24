package com.project.subscription.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "device_group_member", uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "device_id"}))
public class DeviceGroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;
}
