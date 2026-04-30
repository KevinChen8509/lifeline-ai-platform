package com.iot.platform.domain.subscription.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订阅配置实体。
 * 定义客户第三方系统如何接收平台推送数据。
 */
@Entity
@Table(name = "subscription_config")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SubscriptionConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subscription_id", nullable = false, unique = true, length = 32)
    private String subscriptionId;

    /** 关联项目ID（顶级隔离维度） */
    @Column(name = "project_id", nullable = false, length = 32)
    private String projectId;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    /** 客户接收推送的 URL */
    @Column(name = "push_url", nullable = false, length = 512)
    private String pushUrl;

    /** HMAC-SHA256 签名密钥（创建时生成，仅展示一次） */
    @Column(name = "secret", nullable = false, length = 128)
    private String secret;

    /** PLAIN=明文，AES=AES-256-GCM加密 */
    @Enumerated(EnumType.STRING)
    @Column(name = "encryption_mode", nullable = false, length = 8)
    private EncryptionMode encryptionMode;

    /** UNIFIED=统一地址，INDEPENDENT=独立地址 */
    @Enumerated(EnumType.STRING)
    @Column(name = "address_mode", nullable = false, length = 16)
    private AddressMode addressMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SubscriptionStatus status;

    /** 订阅的数据类型列表，JSON数组 */
    @Column(name = "data_types", nullable = false, length = 256)
    private String dataTypes;

    /** 订阅的设备类型过滤，JSON数组，空=全部 */
    @Column(name = "device_types", length = 128)
    private String deviceTypes;

    /** AES-256-GCM 加密密钥（Base64 编码，创建时自动生成） */
    @Column(name = "aes_key", length = 64)
    private String aesKey;

    /** 端点验证状态 */
    @Enumerated(EnumType.STRING)
    @Column(name = "verify_status", nullable = false, length = 16)
    private VerifyStatus verifyStatus;

    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = SubscriptionStatus.UNSUBSCRIBED;
        if (encryptionMode == null) encryptionMode = EncryptionMode.PLAIN;
        if (addressMode == null) addressMode = AddressMode.UNIFIED;
        if (verifyStatus == null) verifyStatus = VerifyStatus.PENDING;
        if (maxRetries == null) maxRetries = 16;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum EncryptionMode { PLAIN, AES }
    public enum AddressMode { UNIFIED, INDEPENDENT }
    public enum SubscriptionStatus { SUBSCRIBED, UNSUBSCRIBED }
    public enum VerifyStatus { PENDING, VERIFIED, FAILED }
}
