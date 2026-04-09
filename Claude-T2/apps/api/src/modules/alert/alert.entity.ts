import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
  ManyToOne,
  JoinColumn,
  Index,
  OneToMany,
} from 'typeorm';
import { Device } from '../device/device.entity';
import { Project } from '../project/project.entity';

/**
 * 预警类型枚举
 */
export enum AlertType {
  MIXED_CONNECTION = 'mixed_connection',   // 错混接
  SILT = 'silt',                           // 淤堵
  OVERFLOW = 'overflow',                   // 溢流
  FULL_PIPE = 'full_pipe',                 // 满管
  THRESHOLD_EXCEEDED = 'threshold_exceeded', // 阈值超限
}

/**
 * 预警级别枚举 (红/橙/黄/蓝)
 */
export enum AlertLevel {
  CRITICAL = 'critical', // 红色 - 特别严重
  HIGH = 'high',         // 橙色 - 严重
  MEDIUM = 'medium',     // 黄色 - 较重
  LOW = 'low',           // 蓝色 - 一般
}

/**
 * 预警状态枚举
 */
export enum AlertStatus {
  PENDING = 'pending',             // 待处理
  ACKNOWLEDGED = 'acknowledged',   // 已确认
  IN_PROGRESS = 'in_progress',     // 处置中
  RESOLVED = 'resolved',           // 已解决
  CLOSED = 'closed',               // 已关闭
}

/**
 * 预警实体
 */
@Entity('alerts')
@Index('idx_alerts_device', ['deviceId'])
@Index('idx_alerts_project', ['projectId'])
@Index('idx_alerts_status', ['status'])
@Index('idx_alerts_type_level', ['type', 'level'])
@Index('idx_alerts_created', ['createdAt'])
export class Alert {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ name: 'device_id', type: 'uuid', comment: '设备ID' })
  deviceId: string;

  @ManyToOne(() => Device)
  @JoinColumn({ name: 'device_id' })
  device: Device;

  @Column({ name: 'project_id', type: 'uuid', nullable: true, comment: '项目ID' })
  projectId: string | null;

  @ManyToOne(() => Project, { nullable: true })
  @JoinColumn({ name: 'project_id' })
  project: Project | null;

  @Column({
    type: 'enum',
    enum: AlertType,
    comment: '预警类型',
  })
  type: AlertType;

  @Column({
    type: 'enum',
    enum: AlertLevel,
    comment: '预警级别',
  })
  level: AlertLevel;

  @Column({ length: 200, comment: '预警标题' })
  title: string;

  @Column({ type: 'text', comment: '预警内容' })
  content: string;

  @Column({ type: 'decimal', precision: 5, scale: 4, nullable: true, comment: '置信度 (0-1)' })
  confidence: number | null;

  @Column({
    type: 'enum',
    enum: AlertStatus,
    default: AlertStatus.PENDING,
    comment: '预警状态',
  })
  status: AlertStatus;

  @Column({ name: 'analysis_result_id', type: 'uuid', nullable: true, comment: '关联的AI分析结果ID' })
  analysisResultId: string | null;

  @Column({ type: 'jsonb', nullable: true, comment: 'AI分析元数据' })
  analysisData: Record<string, any> | null;

  // 状态变更时间
  @Column({ name: 'acknowledged_at', type: 'timestamp', nullable: true, comment: '确认时间' })
  acknowledgedAt: Date | null;

  @Column({ name: 'acknowledged_by', type: 'uuid', nullable: true, comment: '确认人ID' })
  acknowledgedBy: string | null;

  @Column({ name: 'resolved_at', type: 'timestamp', nullable: true, comment: '解决时间' })
  resolvedAt: Date | null;

  @Column({ name: 'resolved_by', type: 'uuid', nullable: true, comment: '解决人ID' })
  resolvedBy: string | null;

  @Column({ name: 'closed_at', type: 'timestamp', nullable: true, comment: '关闭时间' })
  closedAt: Date | null;

  @Column({ name: 'closed_by', type: 'uuid', nullable: true, comment: '关闭人ID' })
  closedBy: string | null;

  @Column({ name: 'resolution', type: 'text', nullable: true, comment: '处置结果' })
  resolution: string | null;

  @Column({ name: 'root_cause', type: 'text', nullable: true, comment: '根因分析' })
  rootCause: string | null;

  // 升级相关
  @Column({ name: 'is_escalated', type: 'boolean', default: false, comment: '是否已升级' })
  isEscalated: boolean;

  @Column({ name: 'escalated_at', type: 'timestamp', nullable: true, comment: '升级时间' })
  escalatedAt: Date | null;

  @CreateDateColumn({ name: 'created_at', comment: '创建时间' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at', comment: '更新时间' })
  updatedAt: Date;

  @OneToMany(() => AlertStatusHistory, (h) => h.alert)
  statusHistory: AlertStatusHistory[];

  @OneToMany(() => WorkOrder, (w) => w.alert)
  workOrders: WorkOrder[];

  @OneToMany(() => AlertNotification, (n) => n.alert)
  notifications: AlertNotification[];
}

/**
 * 预警状态变更历史
 */
@Entity('alert_status_history')
@Index('idx_alert_status_history_alert', ['alertId'])
export class AlertStatusHistory {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ name: 'alert_id', type: 'uuid', comment: '预警ID' })
  alertId: string;

  @ManyToOne(() => Alert, (a) => a.statusHistory)
  @JoinColumn({ name: 'alert_id' })
  alert: Alert;

  @Column({ name: 'old_status', type: 'enum', enum: AlertStatus, nullable: true, comment: '原状态' })
  oldStatus: AlertStatus | null;

  @Column({ name: 'new_status', type: 'enum', enum: AlertStatus, comment: '新状态' })
  newStatus: AlertStatus;

  @Column({ name: 'operator_id', type: 'uuid', nullable: true, comment: '操作人ID' })
  operatorId: string | null;

  @Column({ type: 'text', nullable: true, comment: '操作说明' })
  note: string | null;

  @CreateDateColumn({ name: 'created_at', comment: '操作时间' })
  createdAt: Date;
}

/**
 * 工单状态枚举
 */
export enum WorkOrderStatus {
  PENDING = 'pending',         // 待处理
  ASSIGNED = 'assigned',       // 已分配
  IN_PROGRESS = 'in_progress', // 进行中
  COMPLETED = 'completed',     // 已完成
  CANCELLED = 'cancelled',     // 已取消
}

/**
 * 处置工单实体
 */
@Entity('alert_work_orders')
@Index('idx_work_orders_alert', ['alertId'])
@Index('idx_work_orders_assignee', ['assigneeId'])
export class WorkOrder {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ name: 'work_order_no', length: 30, unique: true, comment: '工单编号 WO-YYYYMMDD-序号' })
  workOrderNo: string;

  @Column({ name: 'alert_id', type: 'uuid', comment: '关联预警ID' })
  alertId: string;

  @ManyToOne(() => Alert, (a) => a.workOrders)
  @JoinColumn({ name: 'alert_id' })
  alert: Alert;

  @Column({ length: 200, comment: '工单标题' })
  title: string;

  @Column({ type: 'text', nullable: true, comment: '工单描述' })
  description: string | null;

  @Column({ name: 'assignee_id', type: 'uuid', nullable: true, comment: '负责人ID' })
  assigneeId: string | null;

  @Column({
    type: 'enum',
    enum: WorkOrderStatus,
    default: WorkOrderStatus.PENDING,
    comment: '工单状态',
  })
  status: WorkOrderStatus;

  @Column({
    type: 'enum',
    enum: AlertLevel,
    default: AlertLevel.MEDIUM,
    comment: '优先级',
  })
  priority: AlertLevel;

  @Column({ name: 'due_date', type: 'timestamp', nullable: true, comment: '截止日期' })
  dueDate: Date | null;

  @Column({ name: 'completed_at', type: 'timestamp', nullable: true, comment: '完成时间' })
  completedAt: Date | null;

  @CreateDateColumn({ name: 'created_at', comment: '创建时间' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at', comment: '更新时间' })
  updatedAt: Date;
}

/**
 * 通知渠道枚举
 */
export enum NotificationChannel {
  IN_APP = 'in_app',   // 站内消息
  EMAIL = 'email',     // 邮件
  SMS = 'sms',         // 短信
  WECHAT = 'wechat',   // 微信
}

/**
 * 预警通知记录
 */
@Entity('alert_notifications')
@Index('idx_alert_notifications_alert', ['alertId'])
@Index('idx_alert_notifications_user', ['userId'])
export class AlertNotification {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ name: 'alert_id', type: 'uuid', comment: '预警ID' })
  alertId: string;

  @ManyToOne(() => Alert, (a) => a.notifications)
  @JoinColumn({ name: 'alert_id' })
  alert: Alert;

  @Column({ name: 'user_id', type: 'uuid', comment: '接收人ID' })
  userId: string;

  @Column({
    type: 'enum',
    enum: NotificationChannel,
    comment: '通知渠道',
  })
  channel: NotificationChannel;

  @Column({ name: 'sent_at', type: 'timestamp', nullable: true, comment: '发送时间' })
  sentAt: Date | null;

  @Column({ name: 'read_at', type: 'timestamp', nullable: true, comment: '阅读时间' })
  readAt: Date | null;

  @Column({ type: 'text', nullable: true, comment: '通知内容' })
  content: string | null;

  @CreateDateColumn({ name: 'created_at', comment: '创建时间' })
  createdAt: Date;
}
