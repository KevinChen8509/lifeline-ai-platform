import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  Index,
  ManyToOne,
  JoinColumn,
} from 'typeorm';
import { Device, DeviceStatus } from './device.entity';

/**
 * 设备状态变更历史实体
 * 用于追溯设备状态变化原因
 */
@Entity('device_status_history')
@Index('idx_device_status_history_device', ['deviceId'])
@Index('idx_device_status_history_timestamp', ['timestamp'])
export class DeviceStatusHistory {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ name: 'device_id', type: 'uuid', comment: '设备ID' })
  deviceId: string;

  @ManyToOne(() => Device)
  @JoinColumn({ name: 'device_id' })
  device: Device;

  @Column({
    name: 'from_status',
    type: 'enum',
    enum: DeviceStatus,
    nullable: true,
    comment: '变更前状态',
  })
  fromStatus: DeviceStatus | null;

  @Column({
    name: 'to_status',
    type: 'enum',
    enum: DeviceStatus,
    comment: '变更后状态',
  })
  toStatus: DeviceStatus;

  @Column({ length: 255, nullable: true, comment: '变更原因' })
  reason: string | null;

  @Column({ name: 'operator_id', type: 'uuid', nullable: true, comment: '操作者ID（如果是手动操作）' })
  operatorId: string | null;

  @Column({ type: 'jsonb', nullable: true, comment: '附加信息' })
  metadata: Record<string, any> | null;

  @Column({ type: 'timestamp', comment: '状态变更时间' })
  timestamp: Date;

  @CreateDateColumn({ name: 'created_at', comment: '记录创建时间' })
  createdAt: Date;
}
