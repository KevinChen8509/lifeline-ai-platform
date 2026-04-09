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
import { AiModel } from './ai-model.entity';

/**
 * 模型版本状态枚举
 */
export enum ModelVersionStatus {
  DRAFT = 'draft',         // 草稿
  PUBLISHED = 'published', // 已发布
  DEPRECATED = 'deprecated', // 已下线
}

/**
 * AI模型版本实体
 * 跟踪模型的版本迭代历史
 */
@Entity('ai_model_versions')
@Index('idx_model_versions_model', ['modelId'])
@Index('idx_model_versions_model_status', ['modelId', 'status'])
export class AiModelVersion {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ name: 'model_id', type: 'uuid', comment: '所属模型ID' })
  modelId: string;

  @ManyToOne(() => AiModel, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'model_id' })
  model: AiModel;

  @Column({ length: 20, comment: '版本号 (语义化版本)' })
  version: string;

  @Column({
    type: 'enum',
    enum: ModelVersionStatus,
    default: ModelVersionStatus.DRAFT,
    comment: '版本状态',
  })
  status: ModelVersionStatus;

  @Column({ name: 'file_url', length: 500, nullable: true, comment: '模型文件URL' })
  fileUrl: string | null;

  @Column({ name: 'file_size', type: 'integer', nullable: true, comment: '文件大小（字节）' })
  fileSize: number | null;

  @Column({ length: 128, nullable: true, comment: '文件校验和（SHA256）' })
  checksum: string | null;

  @Column({ name: 'signature', type: 'text', nullable: true, comment: '文件签名（RSA-2048）' })
  signature: string | null;

  @Column({ type: 'text', nullable: true, name: 'change_log', comment: '变更说明' })
  changeLog: string | null;

  @Column({
    type: 'jsonb',
    default: {},
    comment: '技术规格（大小、延迟、输入要求等）',
  })
  specs: Record<string, any>;

  @Column({ name: 'published_at', type: 'timestamp', nullable: true, comment: '发布时间' })
  publishedAt: Date | null;

  @Column({ name: 'published_by', type: 'uuid', nullable: true, comment: '发布人ID' })
  publishedBy: string | null;

  @Column({ name: 'is_current', type: 'boolean', default: false, comment: '是否为当前发布版本' })
  isCurrent: boolean;

  @CreateDateColumn({ name: 'created_at', comment: '创建时间' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at', comment: '更新时间' })
  updatedAt: Date;
}
