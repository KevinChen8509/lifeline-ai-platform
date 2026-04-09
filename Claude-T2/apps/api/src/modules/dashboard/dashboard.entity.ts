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
import { User } from '../user/user.entity';
import { Project } from '../project/project.entity';

// ==================== Story 8.6: 报告模板 ====================

export enum ReportType {
  DAILY = 'DAILY',
  WEEKLY = 'WEEKLY',
  MONTHLY = 'MONTHLY',
  CUSTOM = 'CUSTOM',
}

@Entity()
export class ReportTemplate {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column()
  name: string;

  @Column({ type: 'enum', enum: ReportType })
  type: ReportType;

  @Column({ type: 'jsonb' })
  sections: ReportSection[];

  @Column({ default: true })
  isDefault: boolean;

  @Index()
  @Column({ nullable: true })
  projectId: string;

  @ManyToOne(() => Project, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'projectId' })
  project: Project;

  @CreateDateColumn()
  createdAt: Date;

  @UpdateDateColumn()
  updatedAt: Date;
}

export interface ReportSection {
  type: string;
  title: string;
  enabled: boolean;
  config?: Record<string, any>;
}

// ==================== Story 8.7-8.8: 运营报告 ====================

export enum ReportStatus {
  GENERATING = 'GENERATING',
  COMPLETED = 'COMPLETED',
  FAILED = 'FAILED',
}

@Entity()
export class Report {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column()
  title: string;

  @Column({ type: 'enum', enum: ReportType })
  type: ReportType;

  @Index()
  @Column()
  projectId: string;

  @ManyToOne(() => Project, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'projectId' })
  project: Project;

  @Column()
  templateId: string;

  @ManyToOne(() => ReportTemplate, { onDelete: 'SET NULL' })
  @JoinColumn({ name: 'templateId' })
  template: ReportTemplate;

  @Column({ type: 'enum', enum: ReportStatus, default: ReportStatus.GENERATING })
  status: ReportStatus;

  @Column({ type: 'timestamptz' })
  startDate: Date;

  @Column({ type: 'timestamptz' })
  endDate: Date;

  @Column({ type: 'jsonb', nullable: true })
  data: Record<string, any>;

  @Column({ nullable: true })
  filePath: string;

  @Column({ nullable: true })
  fileUrl: string;

  @Column({ nullable: true })
  fileSize: number;

  @Column({ type: 'timestamptz', nullable: true })
  generatedAt: Date;

  @Index()
  @Column()
  generatedBy: string;

  @ManyToOne(() => User, { onDelete: 'SET NULL' })
  @JoinColumn({ name: 'generatedBy' })
  generator: User;

  @CreateDateColumn()
  createdAt: Date;
}

// ==================== Story 8.9-8.10: 定时报告 ====================

export enum ScheduleStatus {
  ACTIVE = 'ACTIVE',
  PAUSED = 'PAUSED',
}

@Entity()
export class ScheduledReport {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column()
  name: string;

  @Column({ type: 'enum', enum: ReportType })
  type: ReportType;

  @Column()
  cron: string;

  @Column({ type: 'jsonb' })
  projectIds: string[];

  @Column({ type: 'jsonb' })
  recipients: string[];

  @Column()
  templateId: string;

  @ManyToOne(() => ReportTemplate, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'templateId' })
  template: ReportTemplate;

  @Column({ type: 'enum', enum: ScheduleStatus, default: ScheduleStatus.ACTIVE })
  status: ScheduleStatus;

  @Column({ type: 'timestamptz', nullable: true })
  lastRunAt: Date;

  @Column({ type: 'timestamptz', nullable: true })
  nextRunAt: Date;

  @Column()
  createdBy: string;

  @ManyToOne(() => User, { onDelete: 'SET NULL' })
  @JoinColumn({ name: 'createdBy' })
  creator: User;

  @CreateDateColumn()
  createdAt: Date;

  @UpdateDateColumn()
  updatedAt: Date;
}

export enum DeliveryStatus {
  PENDING = 'PENDING',
  SENT = 'SENT',
  FAILED = 'FAILED',
}

@Entity()
export class ReportDeliveryLog {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column()
  scheduledReportId: string;

  @ManyToOne(() => ScheduledReport, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'scheduledReportId' })
  scheduledReport: ScheduledReport;

  @Column()
  reportId: string;

  @ManyToOne(() => Report, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'reportId' })
  report: Report;

  @Column()
  recipient: string;

  @Column({ type: 'enum', enum: DeliveryStatus, default: DeliveryStatus.PENDING })
  status: DeliveryStatus;

  @Column({ default: 0 })
  attempts: number;

  @Column({ nullable: true })
  error: string;

  @Column({ type: 'timestamptz', nullable: true })
  sentAt: Date;

  @CreateDateColumn()
  createdAt: Date;
}
