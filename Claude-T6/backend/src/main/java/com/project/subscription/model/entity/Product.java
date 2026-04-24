package com.project.subscription.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "product", uniqueConstraints = @UniqueConstraint(columnNames = "product_key"))
public class Product extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "product_key", nullable = false, length = 64)
    private String productKey;

    @Column(nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Protocol protocol = Protocol.MQTT;

    @Column(name = "data_model", columnDefinition = "JSON")
    private String dataModel;

    @Column(nullable = false)
    private Short status = 0;

    public enum Protocol { MQTT, COAP, HTTP, TCP }
}
