import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
  Index,
  OneToMany,
} from 'typeorm';
import { DeviceModelBinding } from './device-model-binding.entity';

/**
 * AI模型类型枚举
 */
export enum AiModelType {
  MIXED_CONNECTION = 'mixed_connection', // 错混接分析
  SILT = 'silt',                         // 淤堵分析
  OVERFLOW = 'overflow',                 // 溢流分析
  FULL_PIPE = 'full_pipe',               // 满管分析
}

/**
 * AI模型状态枚举
 */
export enum AiModelStatus {
  DRAFT = 'draft',           // 草稿
  PUBLISHED = 'published',   // 已发布
  DEPRECATED = 'deprecated', // 已下线
}

/**
 * AI模型实体
 * 用于管理边缘AI推理模型
 */
@Entity('ai_models')
export class AiModel {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ length: 100, comment: '模型名称' })
  name: string;

  @Index('idx_ai_models_code', { unique: true })
  @Column({ length: 50, unique: true, comment: '模型编码（唯一标识）' })
  code: string;

  @Column({ length: 20, comment: '模型版本号' })
  version: string;

  @Column({
    type: 'enum',
    enum: AiModelType,
    name: 'type',
    comment: '模型类型',
  })
  type: AiModelType;

  @Column({ type: 'text', nullable: true, comment: '模型描述' })
  description: string | null;

  @Column({ name: 'file_url', length: 500, nullable: true, comment: '模型文件URL' })
  fileUrl: string | null;

  @Column({ name: 'file_size', type: 'integer', nullable: true, comment: '文件大小（字节）' })
  fileSize: number | null;

  @Column({ length: 128, nullable: true, comment: '文件校验和（SHA256）' })
  checksum: string | null;

  @Index('idx_ai_models_status')
  @Column({
    type: 'enum',
    enum: AiModelStatus,
    default: AiModelStatus.DRAFT,
    comment: '模型状态',
  })
  status: AiModelStatus;

  @Column({
    type: 'jsonb',
    default: {},
    comment: '技术规格（大小、延迟、输入要求等）',
  })
  specs: Record<string, any>;

  @Column({
    type: 'simple-array',
    name: 'applicable_device_types',
    nullable: true,
    comment: '适用设备类型列表',
  })
  applicableDeviceTypes: string[];

  @OneToMany(() => DeviceModelBinding, (binding) => binding.model)
  bindings: DeviceModelBinding[];

  @CreateDateColumn({ name: 'created_at', comment: '创建时间' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at', comment: '更新时间' })
  updatedAt: Date;
}
