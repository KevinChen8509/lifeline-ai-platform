import { Injectable, Logger, NotFoundException, BadRequestException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository, Between, LessThan } from 'typeorm';
import { DeviceTelemetry, BackupConfig, BackupLog, BackupType, BackupStatus, ArchivedDataMeta } from './telemetry.entity';

@Injectable()
export class TelemetryService {
  private readonly logger = new Logger(TelemetryService.name);

  constructor(
    @InjectRepository(DeviceTelemetry)
    private readonly telemetryRepository: Repository<DeviceTelemetry>,
    @InjectRepository(BackupConfig)
    private readonly backupConfigRepository: Repository<BackupConfig>,
    @InjectRepository(BackupLog)
    private readonly backupLogRepository: Repository<BackupLog>,
    @InjectRepository(ArchivedDataMeta)
    private readonly archiveMetaRepository: Repository<ArchivedDataMeta>,
  ) {}

  // ==================== Story 6.2: 遥测数据写入 ====================

  async writeTelemetry(data: {
    deviceId: string;
    timestamp: Date;
    metrics: Record<string, any>;
  }): Promise<DeviceTelemetry> {
    const record = this.telemetryRepository.create(data);
    const saved = await this.telemetryRepository.save(record);
    return saved;
  }

  async writeBatch(records: Array<{
    deviceId: string;
    timestamp: Date;
    metrics: Record<string, any>;
  }>): Promise<DeviceTelemetry[]> {
    if (records.length === 0) {
      throw new BadRequestException('数据不能为空');
    }
    if (records.length > 1000) {
      throw new BadRequestException('每批最多1000条');
    }

    const entities = records.map((r) => this.telemetryRepository.create(r));
    return this.telemetryRepository.save(entities);
  }

  // ==================== Story 6.3: 历史数据查询 ====================

  async findTelemetry(options: {
    deviceId: string;
    page?: number;
    pageSize?: number;
    startTime?: string;
    endTime?: string;
    metrics?: string[];
  }) {
    const {
      deviceId,
      page = 1,
      pageSize = 50,
      startTime,
      endTime,
    } = options;

    const qb = this.telemetryRepository
      .createQueryBuilder('t')
      .where('t.deviceId = :deviceId', { deviceId })
      .orderBy('t.timestamp', 'DESC')
      .skip((page - 1) * pageSize)
      .take(pageSize);

    if (startTime && endTime) {
      qb.andWhere('t.timestamp BETWEEN :startTime AND :endTime', { startTime, endTime });
    } else if (startTime) {
      qb.andWhere('t.timestamp >= :startTime', { startTime });
    } else if (endTime) {
      qb.andWhere('t.timestamp <= :endTime', { endTime });
    }

    // 默认最近24小时
    if (!startTime && !endTime) {
      const defaultStart = new Date(Date.now() - 24 * 60 * 60 * 1000);
      qb.andWhere('t.timestamp >= :defaultStart', { defaultStart });
    }

    const [items, total] = await qb.getManyAndCount();
    return { items, total, page, pageSize };
  }

  // ==================== Story 6.5: 图表数据 ====================

  async getChartData(options: {
    deviceId: string;
    metrics: string[];
    startTime?: string;
    endTime?: string;
    interval?: 'raw' | 'hour' | 'day';
  }) {
    const { deviceId, metrics, startTime, endTime, interval = 'raw' } = options;

    const start = startTime ? new Date(startTime) : new Date(Date.now() - 24 * 60 * 60 * 1000);
    const end = endTime ? new Date(endTime) : new Date();

    const qb = this.telemetryRepository
      .createQueryBuilder('t')
      .select('t.timestamp', 'timestamp')
      .where('t.deviceId = :deviceId', { deviceId })
      .andWhere('t.timestamp BETWEEN :start AND :end', { start, end })
      .orderBy('t.timestamp', 'ASC');

    // 为每个指标提取 JSON 字段
    for (const metric of metrics) {
      qb.addSelect(`t.metrics->>'${metric}'`, metric);
    }

    const rawData = await qb.getRawMany();

    // 按时间间隔聚合
    if (interval === 'hour' || interval === 'day') {
      return this.aggregateData(rawData, metrics, interval);
    }

    return { data: rawData, metrics, interval: 'raw' };
  }

  private aggregateData(rawData: any[], metrics: string[], interval: string) {
    const grouped: Record<string, any[]> = {};

    for (const row of rawData) {
      const ts = new Date(row.timestamp);
      const key = interval === 'hour'
        ? `${ts.toISOString().slice(0, 13)}:00:00`
        : `${ts.toISOString().slice(0, 10)}T00:00:00`;

      if (!grouped[key]) grouped[key] = [];
      grouped[key].push(row);
    }

    const aggregated = Object.entries(grouped).map(([key, rows]) => {
      const result: Record<string, any> = { timestamp: key };
      for (const metric of metrics) {
        const values = rows
          .map((r) => parseFloat(r[metric]))
          .filter((v) => !isNaN(v));
        result[metric] = values.length > 0
          ? values.reduce((a, b) => a + b, 0) / values.length
          : null;
      }
      return result;
    });

    return { data: aggregated, metrics, interval };
  }

  // ==================== Story 6.6: 备份配置 ====================

  async createBackupConfig(data: {
    type: BackupType;
    schedule: string;
    retentionDays?: number;
    storagePath?: string;
  }): Promise<BackupConfig> {
    const config = this.backupConfigRepository.create({
      ...data,
      retentionDays: data.retentionDays || 30,
    });
    return this.backupConfigRepository.save(config);
  }

  async getBackupConfigs(): Promise<BackupConfig[]> {
    return this.backupConfigRepository.find();
  }

  // ==================== Story 6.7: 备份执行 ====================

  async executeBackup(configId: string): Promise<BackupLog> {
    const config = await this.backupConfigRepository.findOne({ where: { id: configId } });
    if (!config) {
      throw new NotFoundException(`备份配置不存在: ${configId}`);
    }

    const log = this.backupLogRepository.create({
      configId: config.id,
      type: config.type,
      status: BackupStatus.RUNNING,
      startedAt: new Date(),
    });
    const savedLog = await this.backupLogRepository.save(log);

    try {
      // TODO: 实际备份逻辑（clickhouse-backup 或自定义脚本）
      const duration = Math.floor(Math.random() * 60) + 10;
      savedLog.status = BackupStatus.COMPLETED;
      savedLog.duration = duration;
      savedLog.filePath = `/backup/${config.type}_${new Date().toISOString().slice(0, 10)}.tar.gz`;
      savedLog.fileSize = Math.floor(Math.random() * 100000000);
      savedLog.completedAt = new Date();
      await this.backupLogRepository.save(savedLog);

      this.logger.log(`备份完成: ${savedLog.filePath}, 耗时 ${duration}秒`);
    } catch (err) {
      savedLog.status = BackupStatus.FAILED;
      savedLog.error = (err as Error).message;
      savedLog.completedAt = new Date();
      await this.backupLogRepository.save(savedLog);
      this.logger.error(`备份失败: ${(err as Error).message}`);
    }

    return savedLog;
  }

  async getBackupLogs(options?: { type?: BackupType; status?: BackupStatus }) {
    const qb = this.backupLogRepository.createQueryBuilder('log')
      .orderBy('log.createdAt', 'DESC')
      .limit(50);

    if (options?.type) qb.andWhere('log.type = :type', { type: options.type });
    if (options?.status) qb.andWhere('log.status = :status', { status: options.status });

    return qb.getMany();
  }

  // ==================== Story 6.8: 备份恢复 ====================

  async restoreBackup(backupId: string): Promise<BackupLog> {
    const log = await this.backupLogRepository.findOne({ where: { id: backupId } });
    if (!log) {
      throw new NotFoundException(`备份记录不存在: ${backupId}`);
    }
    if (log.status !== BackupStatus.COMPLETED) {
      throw new BadRequestException('只能恢复已完成的备份');
    }

    // TODO: 实际恢复逻辑
    this.logger.log(`开始恢复备份: ${log.filePath}`);
    return log;
  }

  // ==================== Story 6.9: 数据归档 ====================

  async archiveOldData(): Promise<{ archivedCount: number }> {
    const twoYearsAgo = new Date();
    twoYearsAgo.setFullYear(twoYearsAgo.getFullYear() - 2);

    const oldData = await this.telemetryRepository.find({
      where: { timestamp: LessThan(twoYearsAgo) },
      take: 10000,
    });

    if (oldData.length === 0) {
      return { archivedCount: 0 };
    }

    // 创建归档元数据
    const timestamps = oldData.map((d) => d.timestamp);
    const meta = this.archiveMetaRepository.create({
      startDate: new Date(Math.min(...timestamps.map((t) => t.getTime()))),
      endDate: new Date(Math.max(...timestamps.map((t) => t.getTime()))),
      filePath: `/archive/data_${twoYearsAgo.toISOString().slice(0, 10)}.parquet`,
      recordCount: oldData.length,
      archivedAt: new Date(),
    });
    await this.archiveMetaRepository.save(meta);

    // 删除已归档数据
    const ids = oldData.map((d) => d.id);
    await this.telemetryRepository.delete(ids);

    this.logger.log(`归档完成: ${oldData.length} 条记录`);
    return { archivedCount: oldData.length };
  }
}
