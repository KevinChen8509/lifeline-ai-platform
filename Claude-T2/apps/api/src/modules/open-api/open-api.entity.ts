import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
  ManyToOne,
  OneToMany,
  Index,
  JoinColumn,
} from 'typeorm';
import { User } from '../user/user.entity';
import { Project } from '../project/project.entity';

// ==================== Story 7.1-7.3: API Key 管理 ====================

export enum ApiKeyStatus {
  ACTIVE = 'ACTIVE',
  DISABLED = 'DISABLED',
  EXPIRED = 'EXPIRED',
}

@Entity()
export class ApiKey {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ unique: true })
  key: string; // lk_live_xxx format

  @Column()
  name: string;

  @Column()
  secret: string; // for HMAC-SHA256 signing

  @Column({ nullable: true })
  description: string;

  @Index()
  @Column()
  userId: string;

  @ManyToOne(() => User, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'userId' })
  user: User;

  @Index()
  @Column({ nullable: true })
  projectId: string;

  @ManyToOne(() => Project, { onDelete: 'SET NULL' })
  @JoinColumn({ name: 'projectId' })
  project: Project;

  @Column({ type: 'jsonb', default: '[]' })
  permissions: string[]; // e.g. ['devices:read', 'alerts:read', 'telemetry:write']

  @Column({ type: 'enum', enum: ApiKeyStatus, default: ApiKeyStatus.ACTIVE })
  status: ApiKeyStatus;

  @Column({ type: 'timestamptz', nullable: true })
  expiresAt: Date;

  @Column({ type: 'timestamptz', nullable: true })
  lastUsedAt: Date;

  @CreateDateColumn()
  createdAt: Date;

  @UpdateDateColumn()
  updatedAt: Date;
}

// ==================== Story 7.6-7.8: Webhook 管理 ====================

export enum WebhookStatus {
  ACTIVE = 'ACTIVE',
  DISABLED = 'DISABLED',
  FAILED = 'FAILED',
}

export enum WebhookEventType {
  DEVICE_ONLINE = 'device.online',
  DEVICE_OFFLINE = 'device.offline',
  ALERT_CREATED = 'alert.created',
  ALERT_ACKNOWLEDGED = 'alert.acknowledged',
  ALERT_RESOLVED = 'alert.resolved',
  ALERT_ESCALATED = 'alert.escalated',
  TELEMETRY_THRESHOLD = 'telemetry.threshold_exceeded',
  BACKUP_COMPLETED = 'backup.completed',
  BACKUP_FAILED = 'backup.failed',
}

@Entity()
export class Webhook {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column()
  name: string;

  @Column()
  url: string;

  @Column()
  secret: string; // for payload signing

  @Column({ type: 'jsonb' })
  events: WebhookEventType[];

  @Column({ type: 'jsonb', nullable: true })
  headers: Record<string, string>;

  @Index()
  @Column()
  userId: string;

  @ManyToOne(() => User, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'userId' })
  user: User;

  @Index()
  @Column({ nullable: true })
  projectId: string;

  @Column({ type: 'enum', enum: WebhookStatus, default: WebhookStatus.ACTIVE })
  status: WebhookStatus;

  @Column({ default: true })
  enabled: boolean;

  @Column({ default: 3 })
  maxRetries: number;

  @Column({ default: 5000 })
  timeoutMs: number;

  @Column({ type: 'timestamptz', nullable: true })
  lastTriggeredAt: Date;

  @CreateDateColumn()
  createdAt: Date;

  @UpdateDateColumn()
  updatedAt: Date;

  @OneToMany(() => WebhookDelivery, (delivery) => delivery.webhook)
  deliveries: WebhookDelivery[];
}

export enum DeliveryStatus {
  PENDING = 'PENDING',
  DELIVERED = 'DELIVERED',
  FAILED = 'FAILED',
  RETRYING = 'RETRYING',
}

@Entity()
export class WebhookDelivery {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column()
  webhookId: string;

  @ManyToOne(() => Webhook, (webhook) => webhook.deliveries, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'webhookId' })
  webhook: Webhook;

  @Column({ type: 'enum', enum: WebhookEventType })
  eventType: WebhookEventType;

  @Column({ type: 'jsonb' })
  payload: Record<string, any>;

  @Column({ type: 'enum', enum: DeliveryStatus, default: DeliveryStatus.PENDING })
  status: DeliveryStatus;

  @Column({ nullable: true })
  responseCode: number;

  @Column({ nullable: true })
  responseBody: string;

  @Column({ default: 0 })
  attempts: number;

  @Column({ default: 0 })
  nextRetryAt: Date;

  @Column({ type: 'timestamptz', nullable: true })
  deliveredAt: Date;

  @CreateDateColumn()
  createdAt: Date;
}

// ==================== Story 7.13-7.15: API 调用日志 ====================

@Entity()
@Index(['apiKeyId', 'createdAt'])
@Index(['path', 'createdAt'])
export class ApiCallLog {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ nullable: true })
  apiKeyId: string;

  @ManyToOne(() => ApiKey, { onDelete: 'SET NULL' })
  @JoinColumn({ name: 'apiKeyId' })
  apiKey: ApiKey;

  @Index()
  @Column({ nullable: true })
  userId: string;

  @Column()
  method: string;

  @Column()
  path: string;

  @Column({ nullable: true })
  query: string;

  @Column({ nullable: true })
  userAgent: string;

  @Column({ nullable: true })
  ip: string;

  @Column()
  statusCode: number;

  @Column({ default: 0 })
  durationMs: number;

  @Column({ type: 'timestamptz' })
  timestamp: Date;

  @CreateDateColumn()
  createdAt: Date;
}
