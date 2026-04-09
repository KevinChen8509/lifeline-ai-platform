import { Injectable, Logger, NotFoundException, BadRequestException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository, In, Like } from 'typeorm';
import {
  Alert,
  AlertType,
  AlertLevel,
  AlertStatus,
  AlertStatusHistory,
  WorkOrder,
  WorkOrderStatus,
  AlertNotification,
} from './alert.entity';
import { AuditLogService } from '../audit/audit-log.service';

@Injectable()
export class AlertService {
  private readonly logger = new Logger(AlertService.name);

  constructor(
    @InjectRepository(Alert)
    private readonly alertRepository: Repository<Alert>,
    @InjectRepository(AlertStatusHistory)
    private readonly statusHistoryRepository: Repository<AlertStatusHistory>,
    @InjectRepository(WorkOrder)
    private readonly workOrderRepository: Repository<WorkOrder>,
    @InjectRepository(AlertNotification)
    private readonly notificationRepository: Repository<AlertNotification>,
    private readonly auditLogService: AuditLogService,
  ) {}

  // ==================== Story 5.2: 预警看板列表 ====================

  async findAll(options: {
    page?: number;
    pageSize?: number;
    type?: AlertType;
    level?: AlertLevel;
    status?: AlertStatus;
    deviceId?: string;
    projectId?: string;
    startTime?: string;
    endTime?: string;
    search?: string;
  }) {
    const {
      page = 1,
      pageSize = 20,
      type,
      level,
      status,
      deviceId,
      projectId,
      startTime,
      endTime,
      search,
    } = options;

    const qb = this.alertRepository
      .createQueryBuilder('alert')
      .leftJoinAndSelect('alert.device', 'device')
      .orderBy(
        `CASE alert.level
          WHEN 'critical' THEN 4
          WHEN 'high' THEN 3
          WHEN 'medium' THEN 2
          WHEN 'low' THEN 1
        END`,
        'DESC',
      )
      .addOrderBy('alert.createdAt', 'DESC')
      .skip((page - 1) * pageSize)
      .take(pageSize);

    if (type) qb.andWhere('alert.type = :type', { type });
    if (level) qb.andWhere('alert.level = :level', { level });
    if (status) qb.andWhere('alert.status = :status', { status });
    if (deviceId) qb.andWhere('alert.deviceId = :deviceId', { deviceId });
    if (projectId) qb.andWhere('alert.projectId = :projectId', { projectId });
    if (startTime) qb.andWhere('alert.createdAt >= :startTime', { startTime });
    if (endTime) qb.andWhere('alert.createdAt <= :endTime', { endTime });
    if (search) {
      qb.andWhere('(alert.title ILIKE :search OR device.name ILIKE :search)', {
        search: `%${search}%`,
      });
    }

    const [items, total] = await qb.getManyAndCount();

    // 统计
    const statsQb = this.alertRepository.createQueryBuilder('alert');
    const statsRaw = await statsQb
      .select('alert.status', 'status')
      .addSelect('COUNT(*)', 'count')
      .where('alert.status != :closedStatus', { closedStatus: AlertStatus.CLOSED })
      .groupBy('alert.status')
      .getRawMany();

    const stats: Record<string, number> = {
      pending: 0,
      acknowledged: 0,
      in_progress: 0,
      resolved: 0,
    };
    for (const row of statsRaw) {
      const status = row.status as string;
      if (status in stats) {
        stats[status] = parseInt(row.count, 10);
      }
    }

    return { items, total, page, pageSize, stats };
  }

  // ==================== Story 5.3: 预警详情 ====================

  async findOne(alertId: string) {
    const alert = await this.alertRepository.findOne({
      where: { id: alertId },
      relations: ['device', 'statusHistory', 'workOrders'],
    });

    if (!alert) {
      throw new NotFoundException(`预警不存在: ${alertId}`);
    }

    // 获取处置时间线
    const statusHistory = await this.statusHistoryRepository.find({
      where: { alertId },
      order: { createdAt: 'ASC' },
    });

    // 计算进度
    const statusOrder = [
      AlertStatus.PENDING,
      AlertStatus.ACKNOWLEDGED,
      AlertStatus.IN_PROGRESS,
      AlertStatus.RESOLVED,
      AlertStatus.CLOSED,
    ];
    const currentIndex = statusOrder.indexOf(alert.status);
    const progress = Math.round((currentIndex / (statusOrder.length - 1)) * 100);

    return {
      alert,
      statusHistory,
      progress,
      duration: this.formatDuration(alert.createdAt, alert.closedAt),
    };
  }

  // ==================== Story 5.13: 预警等级自动分类 ====================

  classifyLevel(type: AlertType, confidence: number): AlertLevel {
    if (type === AlertType.OVERFLOW || type === AlertType.FULL_PIPE) {
      return AlertLevel.CRITICAL;
    }
    if (type === AlertType.SILT || confidence >= 0.9) {
      return AlertLevel.HIGH;
    }
    if (confidence >= 0.7) {
      return AlertLevel.MEDIUM;
    }
    return AlertLevel.LOW;
  }

  // ==================== 创建预警 ====================

  async create(data: {
    deviceId: string;
    projectId?: string;
    type: AlertType;
    title: string;
    content: string;
    confidence?: number;
    analysisResultId?: string;
    analysisData?: Record<string, any>;
  }): Promise<Alert> {
    const level = this.classifyLevel(data.type, data.confidence || 0);

    const alert = this.alertRepository.create({
      ...data,
      level,
      status: AlertStatus.PENDING,
    });

    const saved = await this.alertRepository.save(alert);

    // 记录初始状态
    await this.statusHistoryRepository.save({
      alertId: saved.id,
      oldStatus: null,
      newStatus: AlertStatus.PENDING,
      operatorId: null,
      note: '预警自动创建',
    });

    this.logger.log(`预警创建: ${saved.id}, 级别: ${level}, 类型: ${data.type}`);
    return saved;
  }

  // ==================== Story 5.5: 预警确认 ====================

  async acknowledge(alertId: string, operatorId: string, note?: string) {
    const alert = await this.findAlertOrThrow(alertId);

    if (alert.status !== AlertStatus.PENDING) {
      throw new BadRequestException('只有待处理状态的预警可以确认');
    }

    const oldStatus = alert.status;
    alert.status = AlertStatus.ACKNOWLEDGED;
    alert.acknowledgedAt = new Date();
    alert.acknowledgedBy = operatorId;

    await this.alertRepository.save(alert);
    await this.recordStatusChange(alertId, oldStatus, AlertStatus.ACKNOWLEDGED, operatorId, note);

    this.logger.log(`预警确认: ${alertId} by ${operatorId}`);
    return alert;
  }

  // ==================== Story 5.6: 预警处置 ====================

  async process(alertId: string, operatorId: string, description: string) {
    const alert = await this.findAlertOrThrow(alertId);

    if (alert.status !== AlertStatus.PENDING && alert.status !== AlertStatus.ACKNOWLEDGED) {
      throw new BadRequestException('只有待处理或已确认状态的预警可以开始处置');
    }

    if (!description) {
      throw new BadRequestException('处置说明不能为空');
    }

    const oldStatus = alert.status;
    alert.status = AlertStatus.IN_PROGRESS;

    await this.alertRepository.save(alert);
    await this.recordStatusChange(alertId, oldStatus, AlertStatus.IN_PROGRESS, operatorId, description);

    this.logger.log(`预警处置中: ${alertId} by ${operatorId}`);
    return alert;
  }

  // ==================== Story 5.7: 预警关闭 ====================

  async close(alertId: string, operatorId: string, resolution: string, rootCause?: string) {
    const alert = await this.findAlertOrThrow(alertId);

    if (alert.status !== AlertStatus.IN_PROGRESS && alert.status !== AlertStatus.RESOLVED) {
      throw new BadRequestException('只有处置中或已解决状态的预警可以关闭');
    }

    if (!resolution) {
      throw new BadRequestException('处置结果不能为空');
    }

    const oldStatus = alert.status;
    alert.status = AlertStatus.CLOSED;
    alert.closedAt = new Date();
    alert.closedBy = operatorId;
    alert.resolution = resolution;
    alert.rootCause = rootCause || null;

    await this.alertRepository.save(alert);
    await this.recordStatusChange(alertId, oldStatus, AlertStatus.CLOSED, operatorId, `关闭原因: ${resolution}`);

    this.logger.log(`预警关闭: ${alertId}, 耗时: ${this.formatDuration(alert.createdAt, alert.closedAt)}`);
    return alert;
  }

  // ==================== Story 5.8: 工单生成 ====================

  async createWorkOrder(
    alertId: string,
    data: { title: string; description?: string; assigneeId?: string; priority?: AlertLevel; dueDate?: string },
    operatorId: string,
  ) {
    const alert = await this.findAlertOrThrow(alertId);

    // 生成工单编号
    const today = new Date();
    const dateStr = today.toISOString().slice(0, 10).replace(/-/g, '');
    const count = await this.workOrderRepository.count({
      where: { workOrderNo: Like(`WO-${dateStr}%`) },
    });
    const workOrderNo = `WO-${dateStr}-${String(count + 1).padStart(4, '0')}`;

    const workOrder = this.workOrderRepository.create({
      alertId,
      workOrderNo,
      title: data.title,
      description: data.description || null,
      assigneeId: data.assigneeId || null,
      priority: data.priority || alert.level,
      dueDate: data.dueDate ? new Date(data.dueDate) : null,
      status: WorkOrderStatus.PENDING,
    });

    const saved = await this.workOrderRepository.save(workOrder);

    // 自动将预警状态更新为处置中
    if (alert.status === AlertStatus.PENDING || alert.status === AlertStatus.ACKNOWLEDGED) {
      const oldStatus = alert.status;
      alert.status = AlertStatus.IN_PROGRESS;
      await this.alertRepository.save(alert);
      await this.recordStatusChange(alertId, oldStatus, AlertStatus.IN_PROGRESS, operatorId, `生成工单: ${workOrderNo}`);
    }

    this.logger.log(`工单创建: ${workOrderNo}, 预警: ${alertId}`);
    return saved;
  }

  // ==================== Story 5.12: 处置闭环时间线 ====================

  async getTimeline(alertId: string) {
    const alert = await this.findAlertOrThrow(alertId);

    const statusHistory = await this.statusHistoryRepository.find({
      where: { alertId },
      order: { createdAt: 'ASC' },
    });

    const workOrders = await this.workOrderRepository.find({
      where: { alertId },
    });

    // 超时阈值
    const timeoutThresholds = {
      [AlertLevel.CRITICAL]: 30 * 60 * 1000,     // 30分钟
      [AlertLevel.HIGH]: 2 * 60 * 60 * 1000,     // 2小时
      [AlertLevel.MEDIUM]: 8 * 60 * 60 * 1000,   // 8小时
      [AlertLevel.LOW]: 24 * 60 * 60 * 1000,     // 24小时
    };

    const statusOrder = [
      AlertStatus.PENDING,
      AlertStatus.ACKNOWLEDGED,
      AlertStatus.IN_PROGRESS,
      AlertStatus.RESOLVED,
      AlertStatus.CLOSED,
    ];
    const currentIndex = statusOrder.indexOf(alert.status);
    const progress = Math.round((currentIndex / (statusOrder.length - 1)) * 100);

    // 检查是否超时
    const isOverdue = !alert.closedAt &&
      Date.now() - alert.createdAt.getTime() > (timeoutThresholds[alert.level] || 24 * 60 * 60 * 1000);

    return {
      nodes: statusHistory,
      progress,
      isOverdue,
      workOrders,
    };
  }

  // ==================== Story 5.14: 预警统计卡片 ====================

  async getStats(projectId?: string) {
    const qb = this.alertRepository.createQueryBuilder('alert')
      .select('alert.level', 'level')
      .addSelect('COUNT(*)', 'count')
      .where('alert.status != :closedStatus', { closedStatus: AlertStatus.CLOSED });

    if (projectId) {
      qb.andWhere('alert.projectId = :projectId', { projectId });
    }

    const raw = await qb.groupBy('alert.level').getRawMany();

    const stats: Record<string, number> = {
      critical: 0,
      high: 0,
      medium: 0,
      low: 0,
      unacknowledged: 0,
    };

    for (const row of raw) {
      const level = row.level as string;
      if (level in stats) {
        stats[level] = parseInt(row.count, 10);
      }
    }

    // 未确认数量
    const unacknowledgedCount = await this.alertRepository.count({
      where: { status: AlertStatus.PENDING, ...(projectId ? { projectId } : {}) },
    });
    stats.unacknowledged = unacknowledgedCount;

    return stats;
  }

  // ==================== Story 5.15: 预警升级检查 ====================

  async checkEscalation() {
    const timeoutThresholds = [
      { level: AlertLevel.CRITICAL, minutes: 30 },
      { level: AlertLevel.HIGH, minutes: 120 },
      { level: AlertLevel.MEDIUM, minutes: 480 },
      { level: AlertLevel.LOW, minutes: 1440 },
    ];

    let escalatedCount = 0;

    for (const threshold of timeoutThresholds) {
      const cutoff = new Date(Date.now() - threshold.minutes * 60 * 1000);
      const alerts = await this.alertRepository
        .createQueryBuilder('alert')
        .where('alert.status = :status', { status: AlertStatus.PENDING })
        .andWhere('alert.level = :level', { level: threshold.level })
        .andWhere('alert.isEscalated = :isEscalated', { isEscalated: false })
        .andWhere('alert.createdAt < :cutoff', { cutoff })
        .getMany();

      for (const alert of alerts) {
        alert.isEscalated = true;
        alert.escalatedAt = new Date();
        await this.alertRepository.save(alert);

        await this.recordStatusChange(alert.id, alert.status, alert.status, null, `预警升级: ${threshold.level}级别超过${threshold.minutes}分钟未处理`);
        escalatedCount++;
      }
    }

    if (escalatedCount > 0) {
      this.logger.log(`预警升级检查: 升级 ${escalatedCount} 条预警`);
    }

    return { escalatedCount };
  }

  // ==================== 辅助方法 ====================

  private async findAlertOrThrow(alertId: string): Promise<Alert> {
    const alert = await this.alertRepository.findOne({ where: { id: alertId } });
    if (!alert) {
      throw new NotFoundException(`预警不存在: ${alertId}`);
    }
    return alert;
  }

  private async recordStatusChange(
    alertId: string,
    oldStatus: AlertStatus | null,
    newStatus: AlertStatus,
    operatorId: string | null,
    note?: string | null,
  ) {
    await this.statusHistoryRepository.save({
      alertId,
      oldStatus,
      newStatus,
      operatorId,
      note: note || null,
    });
  }

  private formatDuration(start: Date, end?: Date | null): string {
    const endMs = end ? end.getTime() : Date.now();
    const diffMs = endMs - start.getTime();
    const hours = Math.floor(diffMs / 3600000);
    const minutes = Math.floor((diffMs % 3600000) / 60000);
    if (hours > 0) return `${hours}小时${minutes}分`;
    return `${minutes}分钟`;
  }
}
