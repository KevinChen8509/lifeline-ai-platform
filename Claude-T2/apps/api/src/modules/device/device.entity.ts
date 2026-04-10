import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
  ManyToOne,
  JoinColumn,
  Index,
} from 'typeorm';
import { Project } from '../project/project.entity';

/**
 * 设备状态枚举
 */
export enum DeviceStatus {
  PENDING = 'pending',         // 待激活
  ACTIVATING = 'activating',   // 激活中
  ONLINE = 'online',           // 在线
  OFFLINE = 'offline',         // 离线
  ALERT = 'alert',             // 告警
  MAINTENANCE = 'maintenance', // 维护中
  FAILED = 'failed',           // 激活失败
}

/**
 * 设备来源枚举
 */
export enum DeviceSource {
  SELF_DEVELOPED = 'self_developed', // 自研设备
  THIRD_PARTY = 'third_party',       // 第三方设备
}

/**
 * 通信协议枚举
 */
export enum DeviceProtocol {
  MQTT = 'mqtt',
  MODBUS_TCP = 'modbus_tcp',
  MODBUS_RTU = 'modbus_rtu',
  HTTP = 'http',
}

/**
 * 设备实体
 * 用于管理IoT设备
 */
@Entity('devices')
export class Device {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ length: 100, comment: '设备名称' })
  name: string;

  @Index('idx_devices_serial', { unique: true })
  @Column({ name: 'serial_number', length: 50, unique: true, comment: '设备序列号（全局唯一）' })
  serialNumber: string;

  @Column({ name: 'device_type', length: 50, nullable: true, comment: '设备类型' })
  deviceType: string;

  @Column({ name: 'model', length: 100, nullable: true, comment: '设备型号' })
  model: string;

  @Column({ name: 'manufacturer', length: 100, nullable: true, comment: '制造商' })
  manufacturer: string;

  @Index('idx_devices_project')
  @Column({ name: 'project_id', type: 'uuid', nullable: true, comment: '所属项目ID' })
  projectId: string | null;

  @ManyToOne(() => Project, { nullable: true })
  @JoinColumn({ name: 'project_id' })
  project: Project | null;

  @Index('idx_devices_status')
  @Column({
    type: 'enum',
    enum: DeviceStatus,
    default: DeviceStatus.PENDING,
    comment: '设备状态',
  })
  status: DeviceStatus;

  @Column({
    type: 'enum',
    enum: DeviceSource,
    default: DeviceSource.SELF_DEVELOPED,
    name: 'source',
    comment: '设备来源',
  })
  source: DeviceSource;

  @Column({
    type: 'jsonb',
    default: {},
    comment: '设备配置（JSON格式）',
  })
  config: Record<string, any>;

  @Column({
    type: 'enum',
    enum: DeviceProtocol,
    name: 'protocol',
    nullable: true,
    comment: '通信协议',
  })
  protocol: DeviceProtocol | null;

  @Column({
    name: 'firmware_version',
    length: 50,
    nullable: true,
    comment: '当前固件版本',
  })
  firmwareVersion: string | null;

  @Column({ type: 'text', nullable: true, comment: '设备描述' })
  description: string | null;

  @Column({ name: 'last_online_at', type: 'timestamp', nullable: true, comment: '最后在线时间' })
  lastOnlineAt: Date | null;

  @CreateDateColumn({ name: 'created_at', comment: '创建时间' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at', comment: '更新时间' })
  updatedAt: Date;
}
