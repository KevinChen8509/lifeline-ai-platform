import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  ManyToOne,
  JoinColumn,
  Index,
  Unique,
} from 'typeorm';
import { Device } from '../device/device.entity';
import { AiModel } from './ai-model.entity';

/**
 * 设备-模型绑定状态枚举
 */
export enum BindingStatus {
  PENDING = 'pending',     // 待同步
  SYNCING = 'syncing',     // 同步中
  RUNNING = 'running',     // 运行中
  ERROR = 'error',         // 异常
  OFFLINE = 'offline',     // 离线
}

/**
 * 设备-模型绑定实体
 * 记录设备与AI模型的绑定关系
 */
@Entity('device_model_bindings')
@Unique('uq_device_model', ['deviceId', 'modelId'])
@Index('idx_binding_device', ['deviceId'])
@Index('idx_binding_model', ['modelId'])
@Index('idx_binding_status', ['status'])
export class DeviceModelBinding {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ name: 'device_id', type: 'uuid', comment: '设备ID' })
  deviceId: string;

  @ManyToOne(() => Device)
  @JoinColumn({ name: 'device_id' })
  device: Device;

  @Column({ name: 'model_id', type: 'uuid', comment: '模型ID' })
  modelId: string;

  @ManyToOne(() => AiModel, (model) => model.bindings)
  @JoinColumn({ name: 'model_id' })
  model: AiModel;

  @Column({
    type: 'enum',
    enum: BindingStatus,
    default: BindingStatus.PENDING,
    comment: '绑定状态',
  })
  status: BindingStatus;

  @Column({
    name: 'bound_version',
    length: 20,
    nullable: true,
    comment: '绑定的模型版本',
  })
  boundVersion: string | null;

  @Column({
    name: 'bound_at',
    type: 'timestamp',
    default: () => 'CURRENT_TIMESTAMP',
    comment: '绑定时间',
  })
  boundAt: Date;

  @Column({
    name: 'last_sync_at',
    type: 'timestamp',
    nullable: true,
    comment: '最后同步时间',
  })
  lastSyncAt: Date | null;

  @Column({
    type: 'text',
    nullable: true,
    comment: '错误信息',
  })
  error: string | null;

  @Column({
    type: 'jsonb',
    nullable: true,
    comment: '扩展信息',
  })
  metadata: Record<string, any> | null;

  @CreateDateColumn({ name: 'created_at', comment: '创建时间' })
  createdAt: Date;
}
