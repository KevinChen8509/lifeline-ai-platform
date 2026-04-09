import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
  ManyToOne,
  OneToMany,
  JoinColumn,
  Index,
  Unique,
} from 'typeorm';
import { AiModel } from './ai-model.entity';

/**
 * 部署任务状态枚举
 */
export enum DeploymentStatus {
  PENDING = 'pending',         // 待处理
  IN_PROGRESS = 'in_progress', // 进行中
  COMPLETED = 'completed',     // 已完成
  FAILED = 'failed',           // 失败
  CANCELLED = 'cancelled',     // 已取消
}

/**
 * 单个设备的部署状态枚举
 */
export enum DeviceDeploymentStatus {
  PENDING = 'pending',           // 待处理
  DOWNLOADING = 'downloading',   // 下载中
  INSTALLING = 'installing',     // 安装中
  SUCCESS = 'success',           // 成功
  FAILED = 'failed',             // 失败
  SKIPPED = 'skipped',           // 已跳过（已有该版本）
}

/**
 * 失败原因枚举
 */
export enum DeploymentFailureReason {
  DEVICE_OFFLINE = 'device_offline',           // 设备离线
  DOWNLOAD_FAILED = 'download_failed',         // 下载失败
  CHECKSUM_MISMATCH = 'checksum_mismatch',     // 校验和不匹配
  INSTALL_FAILED = 'install_failed',           // 安装失败
  TIMEOUT = 'timeout',                         // 超时
  DEVICE_NOT_COMPATIBLE = 'device_not_compatible', // 设备不兼容
  BINDING_NOT_FOUND = 'binding_not_found',     // 绑定关系不存在
}

/**
 * 模型部署任务实体
 */
@Entity('model_deployments')
@Index('idx_model_deployments_model', ['modelId'])
@Index('idx_model_deployments_status', ['status'])
@Index('idx_model_deployments_created', ['createdAt'])
export class ModelDeployment {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ name: 'model_id', type: 'uuid', comment: '模型ID' })
  modelId: string;

  @ManyToOne(() => AiModel)
  @JoinColumn({ name: 'model_id' })
  model: AiModel;

  @Column({ length: 20, name: 'target_version', comment: '目标版本号' })
  targetVersion: string;

  @Column({
    type: 'enum',
    enum: DeploymentStatus,
    default: DeploymentStatus.PENDING,
    comment: '部署状态',
  })
  status: DeploymentStatus;

  @Column({ name: 'total_devices', type: 'integer', default: 0, comment: '总设备数' })
  totalDevices: number;

  @Column({ name: 'success_count', type: 'integer', default: 0, comment: '成功数量' })
  successCount: number;

  @Column({ name: 'failed_count', type: 'integer', default: 0, comment: '失败数量' })
  failedCount: number;

  @Column({ name: 'in_progress_count', type: 'integer', default: 0, comment: '进行中数量' })
  inProgressCount: number;

  @Column({ name: 'pending_count', type: 'integer', default: 0, comment: '待处理数量' })
  pendingCount: number;

  @Column({ name: 'created_by', type: 'uuid', comment: '创建人ID' })
  createdBy: string;

  @Column({ type: 'text', nullable: true, comment: '部署说明' })
  description: string | null;

  @Column({ name: 'started_at', type: 'timestamp', nullable: true, comment: '开始时间' })
  startedAt: Date | null;

  @Column({ name: 'completed_at', type: 'timestamp', nullable: true, comment: '完成时间' })
  completedAt: Date | null;

  @CreateDateColumn({ name: 'created_at', comment: '创建时间' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at', comment: '更新时间' })
  updatedAt: Date;

  @OneToMany(() => DeviceDeployment, (dd) => dd.deployment)
  deviceDeployments: DeviceDeployment[];
}

/**
 * 单个设备部署记录实体
 */
@Entity('device_deployments')
@Index('idx_device_deployments_deployment', ['deploymentId'])
@Index('idx_device_deployments_device', ['deviceId'])
@Unique('uq_device_deployment', ['deploymentId', 'deviceId'])
export class DeviceDeployment {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ name: 'deployment_id', type: 'uuid', comment: '部署任务ID' })
  deploymentId: string;

  @ManyToOne(() => ModelDeployment, (d) => d.deviceDeployments)
  @JoinColumn({ name: 'deployment_id' })
  deployment: ModelDeployment;

  @Column({ name: 'device_id', type: 'uuid', comment: '设备ID' })
  deviceId: string;

  @Column({
    type: 'enum',
    enum: DeviceDeploymentStatus,
    default: DeviceDeploymentStatus.PENDING,
    comment: '部署状态',
  })
  status: DeviceDeploymentStatus;

  @Column({
    type: 'enum',
    enum: DeploymentFailureReason,
    nullable: true,
    name: 'failure_reason',
    comment: '失败原因',
  })
  failureReason: DeploymentFailureReason | null;

  @Column({ type: 'text', nullable: true, comment: '失败详情' })
  error: string | null;

  @Column({ name: 'progress', type: 'integer', default: 0, comment: '进度百分比 (0-100)' })
  progress: number;

  @Column({ name: 'started_at', type: 'timestamp', nullable: true, comment: '开始时间' })
  startedAt: Date | null;

  @Column({ name: 'completed_at', type: 'timestamp', nullable: true, comment: '完成时间' })
  completedAt: Date | null;

  @Column({ name: 'retry_count', type: 'integer', default: 0, comment: '重试次数' })
  retryCount: number;

  @CreateDateColumn({ name: 'created_at', comment: '创建时间' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at', comment: '更新时间' })
  updatedAt: Date;
}
