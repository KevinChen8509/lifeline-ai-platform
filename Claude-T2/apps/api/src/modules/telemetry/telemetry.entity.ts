import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  ManyToOne,
  JoinColumn,
  Index,
} from 'typeorm';
import { Device } from '../device/device.entity';

/**
 * 备份类型枚举
 */
export enum BackupType {
  INCREMENTAL = 'incremental', // 增量备份
  FULL = 'full',               // 全量备份
}

/**
 * 备份状态枚举
 */
export enum BackupStatus {
  PENDING = 'pending',         // 待执行
  RUNNING = 'running',         // 执行中
  COMPLETED = 'completed',     // 已完成
  FAILED = 'failed',           // 失败
}

/**
 * 设备遥测数据实体
 * 注: 生产环境应替换为 ClickHouse
 */
@Entity('device_telemetry')
@Index('idx_telemetry_device_time', ['deviceId', 'timestamp'])
@Index('idx_telemetry_timestamp', ['timestamp'])
export class DeviceTelemetry {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ name: 'device_id', type: 'uuid', comment: '设备ID' })
  deviceId: string;

  @ManyToOne(() => Device)
  @JoinColumn({ name: 'device_id' })
  device: Device;

  @Column({ type: 'timestamp', comment: '数据时间戳' })
  timestamp: Date;

  @Column({ type: 'jsonb', comment: '指标数据 JSON' })
  metrics: Record<string, any>;

  @CreateDateColumn({ name: 'created_at', comment: '写入时间' })
  createdAt: Date;
}

/**
 * 备份配置实体
 */
@Entity('backup_configs')
export class BackupConfig {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({
    type: 'enum',
    enum: BackupType,
    comment: '备份类型',
  })
  type: BackupType;

  @Column({ length: 50, comment: 'Cron 表达式', default: '0 2 * * *' })
  schedule: string;

  @Column({ name: 'retention_days', type: 'integer', default: 30, comment: '保留天数' })
  retentionDays: number;

  @Column({ name: 'storage_path', length: 500, nullable: true, comment: '存储路径' })
  storagePath: string | null;

  @Column({ name: 'is_enabled', type: 'boolean', default: true, comment: '是否启用' })
  isEnabled: boolean;

  @CreateDateColumn({ name: 'created_at', comment: '创建时间' })
  createdAt: Date;

  @Column({ name: 'updated_at', type: 'timestamp', nullable: true })
  updatedAt: Date | null;
}

/**
 * 备份日志实体
 */
@Entity('backup_logs')
export class BackupLog {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ name: 'config_id', type: 'uuid', nullable: true, comment: '备份配置ID' })
  configId: string | null;

  @Column({
    type: 'enum',
    enum: BackupType,
    comment: '备份类型',
  })
  type: BackupType;

  @Column({
    type: 'enum',
    enum: BackupStatus,
    default: BackupStatus.PENDING,
    comment: '备份状态',
  })
  status: BackupStatus;

  @Column({ name: 'file_path', length: 500, nullable: true, comment: '备份文件路径' })
  filePath: string | null;

  @Column({ name: 'file_size', type: 'bigint', nullable: true, comment: '文件大小(字节)' })
  fileSize: number | null;

  @Column({ type: 'integer', nullable: true, comment: '执行耗时(秒)' })
  duration: number | null;

  @Column({ type: 'text', nullable: true, comment: '错误信息' })
  error: string | null;

  @Column({ name: 'started_at', type: 'timestamp', nullable: true, comment: '开始时间' })
  startedAt: Date | null;

  @Column({ name: 'completed_at', type: 'timestamp', nullable: true, comment: '完成时间' })
  completedAt: Date | null;

  @CreateDateColumn({ name: 'created_at', comment: '创建时间' })
  createdAt: Date;
}

/**
 * 归档数据元数据实体
 */
@Entity('archived_data_meta')
export class ArchivedDataMeta {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ name: 'start_date', type: 'date', comment: '数据起始日期' })
  startDate: Date;

  @Column({ name: 'end_date', type: 'date', comment: '数据结束日期' })
  endDate: Date;

  @Column({ name: 'device_id', type: 'uuid', nullable: true, comment: '设备ID(空=全部)' })
  deviceId: string | null;

  @Column({ name: 'file_path', length: 500, comment: '归档文件路径' })
  filePath: string;

  @Column({ name: 'file_size', type: 'bigint', nullable: true, comment: '文件大小(字节)' })
  fileSize: number | null;

  @Column({ name: 'record_count', type: 'integer', nullable: true, comment: '记录数' })
  recordCount: number | null;

  @Column({ name: 'archived_at', type: 'timestamp', comment: '归档时间' })
  archivedAt: Date;
}
