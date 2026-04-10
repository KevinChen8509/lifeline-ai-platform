import { Injectable, Logger } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { AuditLog, AuditAction, AuditTargetType } from './audit-log.entity';

export interface CreateAuditLogParams {
  action: AuditAction | string;
  targetType: AuditTargetType | string;
  targetId?: string | null;
  operatorId: string;
  operator?: {
    id: string;
    username: string;
    name: string;
  } | null;
  oldValue?: Record<string, any> | null;
  newValue?: Record<string, any> | null;
  description?: string | null;
  ipAddress?: string | null;
  userAgent?: string | null;
}

export interface FindAuditLogsOptions {
  page?: number;
  pageSize?: number;
  action?: string;
  targetType?: string;
  targetId?: string;
  operatorId?: string;
  startDate?: Date;
  endDate?: Date;
}

@Injectable()
export class AuditLogService {
  private readonly logger = new Logger(AuditLogService.name);

  constructor(
    @InjectRepository(AuditLog)
    private readonly auditLogRepository: Repository<AuditLog>,
  ) {}

  /**
   * 创建审计日志
   */
  async createLog(params: CreateAuditLogParams): Promise<AuditLog> {
    try {
      const auditLog = this.auditLogRepository.create({
        action: params.action,
        targetType: params.targetType,
        targetId: params.targetId || null,
        operatorId: params.operatorId,
        operator: params.operator || null,
        oldValue: params.oldValue || null,
        newValue: params.newValue || null,
        description: params.description || null,
        ipAddress: params.ipAddress || null,
        userAgent: params.userAgent || null,
      });

      const saved = await this.auditLogRepository.save(auditLog);

      this.logger.debug(
        `Audit log created: ${params.action} on ${params.targetType}:${params.targetId || 'N/A'} by ${params.operatorId}`,
      );

      return saved;
    } catch (error) {
      // 审计日志失败不应影响主业务流程
      this.logger.error(`Failed to create audit log: ${error.message}`);
      throw error;
    }
  }

  /**
   * 批量创建审计日志
   */
  async createBatch(params: CreateAuditLogParams[]): Promise<AuditLog[]> {
    try {
      const auditLogs = params.map((p) => this.auditLogRepository.create(p));
      return await this.auditLogRepository.save(auditLogs);
    } catch (error) {
      this.logger.error(`Failed to create batch audit logs: ${error.message}`);
      throw error;
    }
  }

  /**
   * 查询审计日志
   */
  async findAll(
    options: FindAuditLogsOptions = {},
  ): Promise<{ items: AuditLog[]; total: number; page: number; pageSize: number }> {
    const {
      page = 1,
      pageSize = 20,
      action,
      targetType,
      targetId,
      operatorId,
      startDate,
      endDate,
    } = options;

    const skip = (page - 1) * pageSize;

    const queryBuilder = this.auditLogRepository.createQueryBuilder('log');

    if (action) {
      queryBuilder.andWhere('log.action = :action', { action });
    }

    if (targetType) {
      queryBuilder.andWhere('log.targetType = :targetType', { targetType });
    }

    if (targetId) {
      queryBuilder.andWhere('log.targetId = :targetId', { targetId });
    }

    if (operatorId) {
      queryBuilder.andWhere('log.operatorId = :operatorId', { operatorId });
    }

    if (startDate) {
      queryBuilder.andWhere('log.createdAt >= :startDate', { startDate });
    }

    if (endDate) {
      queryBuilder.andWhere('log.createdAt <= :endDate', { endDate });
    }

    const [items, total] = await queryBuilder
      .skip(skip)
      .take(pageSize)
      .orderBy('log.createdAt', 'DESC')
      .getManyAndCount();

    return { items, total, page, pageSize };
  }

  /**
   * 获取单条审计日志
   */
  async findOne(id: string): Promise<AuditLog | null> {
    return this.auditLogRepository.findOne({ where: { id } });
  }

  /**
   * 获取某个目标的所有审计日志
   */
  async findByTarget(
    targetType: string,
    targetId: string,
    options: { page?: number; pageSize?: number } = {},
  ): Promise<{ items: AuditLog[]; total: number }> {
    const { page = 1, pageSize = 20 } = options;
    const skip = (page - 1) * pageSize;

    const [items, total] = await this.auditLogRepository.findAndCount({
      where: { targetType, targetId },
      skip,
      take: pageSize,
      order: { createdAt: 'DESC' },
    });

    return { items, total };
  }

  /**
   * 获取某个操作者的所有审计日志
   */
  async findByOperator(
    operatorId: string,
    options: { page?: number; pageSize?: number } = {},
  ): Promise<{ items: AuditLog[]; total: number }> {
    const { page = 1, pageSize = 20 } = options;
    const skip = (page - 1) * pageSize;

    const [items, total] = await this.auditLogRepository.findAndCount({
      where: { operatorId },
      skip,
      take: pageSize,
      order: { createdAt: 'DESC' },
    });

    return { items, total };
  }

  /**
   * 清理过期的审计日志（保留指定天数内的日志）
   */
  async cleanupOldLogs(retentionDays: number = 180): Promise<number> {
    const cutoffDate = new Date();
    cutoffDate.setDate(cutoffDate.getDate() - retentionDays);

    const result = await this.auditLogRepository
      .createQueryBuilder()
      .delete()
      .where('createdAt < :cutoffDate', { cutoffDate })
      .execute();

    this.logger.log(
      `Cleaned up ${result.affected} audit logs older than ${retentionDays} days`,
    );

    return result.affected || 0;
  }
}
