import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  Index,
  ManyToOne,
  JoinColumn,
} from 'typeorm';
import { Device } from '../device/device.entity';
import { AiModel } from '../ai-model/ai-model.entity';

/**
 * AI分析类型枚举
 */
export enum AnalysisType {
  MIXED_CONNECTION = 'mixed_connection', // 错混接分析
  SILT = 'silt',                         // 淤堵分析
  OVERFLOW = 'overflow',                 // 溢流分析
  FULL_PIPE = 'full_pipe',               // 满管分析
}

/**
 * AI分析结果枚举
 */
export enum AnalysisResult {
  NORMAL = 'normal',     // 正常
  ABNORMAL = 'abnormal', // 异常
  WARNING = 'warning',   // 警告
}

/**
 * AI分析结果实体
 * 存储设备AI分析结果
 */
@Entity('ai_analysis_results')
@Index('idx_ai_analysis_device_time', ['deviceId', 'timestamp'])
@Index('idx_ai_analysis_device_type_time', ['deviceId', 'analysisType', 'timestamp'])
export class AiAnalysisResult {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ name: 'device_id', type: 'uuid', comment: '设备ID' })
  deviceId: string;

  @ManyToOne(() => Device)
  @JoinColumn({ name: 'device_id' })
  device: Device;

  @Column({ name: 'model_id', type: 'uuid', comment: 'AI模型ID' })
  modelId: string;

  @ManyToOne(() => AiModel)
  @JoinColumn({ name: 'model_id' })
  model: AiModel;

  @Column({
    type: 'enum',
    enum: AnalysisType,
    name: 'analysis_type',
    comment: '分析类型',
  })
  analysisType: AnalysisType;

  @Column({
    type: 'enum',
    enum: AnalysisResult,
    name: 'analysis_result',
    comment: '分析结果',
  })
  analysisResult: AnalysisResult;

  @Column({
    type: 'decimal',
    precision: 5,
    scale: 2,
    comment: '置信度 (0-100)',
  })
  confidence: number;

  @Column({
    type: 'jsonb',
    nullable: true,
    comment: '分析详情',
  })
  details: Record<string, any> | null;

  @Column({
    type: 'jsonb',
    nullable: true,
    name: 'confidence_factors',
    comment: '置信度影响因素',
  })
  confidenceFactors: {
    dataQuality: number;
    modelVersion: string;
    recentAnomalies: number;
    [key: string]: any;
  } | null;

  @Column({
    type: 'jsonb',
    nullable: true,
    comment: '原始数据快照',
  })
  rawData: Record<string, any> | null;

  @Column({
    type: 'timestamp',
    comment: '分析时间',
  })
  timestamp: Date;

  @CreateDateColumn({ name: 'created_at', comment: '记录创建时间' })
  createdAt: Date;
}
