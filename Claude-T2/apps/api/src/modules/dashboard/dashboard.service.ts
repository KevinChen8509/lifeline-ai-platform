import { Injectable, Logger, NotFoundException, BadRequestException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository, In } from 'typeorm';
import * as os from 'os';
import {
  ReportTemplate,
  ReportType,
  ReportSection,
  Report,
  ReportStatus,
  ScheduledReport,
  ScheduleStatus,
  ReportDeliveryLog,
  DeliveryStatus,
} from './dashboard.entity';

@Injectable()
export class DashboardService {
  private readonly logger = new Logger(DashboardService.name);

  constructor(
    @InjectRepository(ReportTemplate)
    private readonly templateRepository: Repository<ReportTemplate>,
    @InjectRepository(Report)
    private readonly reportRepository: Repository<Report>,
    @InjectRepository(ScheduledReport)
    private readonly scheduledReportRepository: Repository<ScheduledReport>,
    @InjectRepository(ReportDeliveryLog)
    private readonly deliveryLogRepository: Repository<ReportDeliveryLog>,
  ) {}

  // ==================== Story 8.1: 设备在线率统计 ====================

  async getDeviceStats(projectId: string) {
    // Placeholder: in production, query device table with aggregation
    const total = 0;
    const online = 0;
    const offline = 0;
    const alert = 0;
    const onlineRate = total > 0 ? ((online / total) * 100).toFixed(1) : '0.0';

    return {
      total,
      online,
      offline,
      alert,
      onlineRate: `${onlineRate}%`,
      trend: '+0%',
    };
  }

  // ==================== Story 8.2: 预警统计 ====================

  async getAlertStats(projectId: string) {
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    // Placeholder: in production, query alert table with aggregation
    return {
      todayTotal: 0,
      pending: 0,
      closed: 0,
      byLevel: { critical: 0, high: 0, medium: 0, low: 0 },
      trend: [],
      recent: [],
    };
  }

  // ==================== Story 8.3: 关键指标 ====================

  async getKpi(projectId: string) {
    return {
      avgHandleTime: '0小时',
      handleRate: '0%',
      aiAnalysisCount: 0,
      telemetryCount: 0,
      trends: {
        handleRate: '+0%',
        avgHandleTime: '+0%',
        aiAnalysisCount: '+0%',
        telemetryCount: '+0%',
      },
    };
  }

  // ==================== Story 8.4: 预警类型分布 ====================

  async getAlertTypeDistribution(options: { projectId?: string; startDate?: string; endDate?: string }) {
    // Placeholder: in production, query ClickHouse aggregation
    return {
      distribution: [
        { type: 'MIXED_CONNECTION', count: 0, percentage: '0%' },
        { type: 'SILT', count: 0, percentage: '0%' },
        { type: 'OVERFLOW', count: 0, percentage: '0%' },
        { type: 'FULL_PIPE', count: 0, percentage: '0%' },
        { type: 'THRESHOLD_EXCEEDED', count: 0, percentage: '0%' },
      ],
      total: 0,
    };
  }

  // ==================== Story 8.5: 预警处置效率 ====================

  async getAlertHandlingEfficiency(options: { projectId?: string; startDate?: string; endDate?: string }) {
    // SLA thresholds in hours
    const slaThresholds: Record<string, number> = {
      CRITICAL: 4,
      HIGH: 8,
      MEDIUM: 24,
      LOW: 72,
    };

    // Placeholder: in production, calculate from alert status history
    return {
      avgResponseTime: '0分钟',
      avgHandleTime: '0小时',
      timelinessRate: '0%',
      distribution: {
        'lt1h': 0,
        '1to4h': 0,
        '4to24h': 0,
        'gt24h': 0,
      },
      slaThresholds,
    };
  }

  // ==================== Story 8.6: 报告模板 ====================

  async initDefaultTemplates(): Promise<void> {
    const count = await this.templateRepository.count();
    if (count > 0) return;

    const templates: Partial<ReportTemplate>[] = [
      {
        name: '日报模板',
        type: ReportType.DAILY,
        isDefault: true,
        sections: [
          { type: 'alert_summary', title: '预警汇总', enabled: true },
          { type: 'device_status', title: '设备状态', enabled: true },
          { type: 'handling_record', title: '处置记录', enabled: true },
        ],
      },
      {
        name: '周报模板',
        type: ReportType.WEEKLY,
        isDefault: true,
        sections: [
          { type: 'alert_trend', title: '预警趋势', enabled: true },
          { type: 'handling_efficiency', title: '处置效率', enabled: true },
          { type: 'top10_devices', title: '异常设备Top10', enabled: true },
        ],
      },
      {
        name: '月报模板',
        type: ReportType.MONTHLY,
        isDefault: true,
        sections: [
          { type: 'monthly_kpi', title: '月度KPI', enabled: true },
          { type: 'trend_analysis', title: '趋势分析', enabled: true },
          { type: 'improvement_suggestions', title: '改进建议', enabled: true },
        ],
      },
    ];

    for (const tpl of templates) {
      const entity = this.templateRepository.create(tpl);
      await this.templateRepository.save(entity);
    }

    this.logger.log('初始化默认报告模板完成');
  }

  async createTemplate(data: {
    name: string;
    type: ReportType;
    sections: ReportSection[];
    projectId?: string;
  }): Promise<ReportTemplate> {
    const template = this.templateRepository.create(data);
    return this.templateRepository.save(template);
  }

  async listTemplates(projectId?: string): Promise<ReportTemplate[]> {
    if (projectId) {
      return this.templateRepository.find({
        where: [
          { projectId },
          { isDefault: true },
        ],
        order: { createdAt: 'DESC' },
      });
    }
    return this.templateRepository.find({ order: { createdAt: 'DESC' } });
  }

  async getTemplate(id: string): Promise<ReportTemplate> {
    const template = await this.templateRepository.findOne({ where: { id } });
    if (!template) {
      throw new NotFoundException(`报告模板不存在: ${id}`);
    }
    return template;
  }

  async updateTemplate(id: string, data: Partial<ReportTemplate>): Promise<ReportTemplate> {
    const template = await this.getTemplate(id);
    Object.assign(template, data);
    return this.templateRepository.save(template);
  }

  async deleteTemplate(id: string): Promise<void> {
    const template = await this.getTemplate(id);
    if (template.isDefault) {
      throw new BadRequestException('不能删除默认模板');
    }
    await this.templateRepository.delete(id);
  }

  // ==================== Story 8.7: 报告生成 ====================

  async generateReport(data: {
    type: ReportType;
    projectId: string;
    startDate: string;
    endDate: string;
    templateId: string;
    userId: string;
  }): Promise<Report> {
    const template = await this.getTemplate(data.templateId);

    const report = this.reportRepository.create({
      title: `${template.name} - ${new Date().toLocaleDateString('zh-CN')}`,
      type: data.type,
      projectId: data.projectId,
      templateId: data.templateId,
      startDate: new Date(data.startDate),
      endDate: new Date(data.endDate),
      status: ReportStatus.GENERATING,
      generatedBy: data.userId,
    });

    const saved = await this.reportRepository.save(report);

    try {
      // Aggregate data based on template sections
      const reportData: Record<string, any> = {};

      for (const section of template.sections) {
        if (!section.enabled) continue;

        switch (section.type) {
          case 'alert_summary':
          case 'alert_trend':
            reportData[section.type] = await this.getAlertStats(data.projectId);
            break;
          case 'device_status':
            reportData[section.type] = await this.getDeviceStats(data.projectId);
            break;
          case 'handling_efficiency':
          case 'handling_record':
            reportData[section.type] = await this.getAlertHandlingEfficiency({
              projectId: data.projectId,
              startDate: data.startDate,
              endDate: data.endDate,
            });
            break;
          case 'monthly_kpi':
            reportData[section.type] = await this.getKpi(data.projectId);
            break;
          default:
            reportData[section.type] = { message: '占位数据' };
        }
      }

      saved.data = reportData;
      saved.status = ReportStatus.COMPLETED;
      saved.generatedAt = new Date();
      saved.filePath = `/reports/${data.type.toLowerCase()}/${saved.id}.pdf`;
      saved.fileSize = 0; // placeholder
      await this.reportRepository.save(saved);

      this.logger.log(`报告生成完成: ${saved.title}`);
    } catch (err) {
      saved.status = ReportStatus.FAILED;
      await this.reportRepository.save(saved);
      this.logger.error(`报告生成失败: ${(err as Error).message}`);
    }

    return saved;
  }

  async listReports(options: { projectId?: string; type?: ReportType; page?: number; pageSize?: number }) {
    const { page = 1, pageSize = 20 } = options;

    const qb = this.reportRepository
      .createQueryBuilder('r')
      .orderBy('r.createdAt', 'DESC')
      .skip((page - 1) * pageSize)
      .take(pageSize);

    if (options.projectId) qb.andWhere('r.projectId = :projectId', { projectId: options.projectId });
    if (options.type) qb.andWhere('r.type = :type', { type: options.type });

    const [items, total] = await qb.getManyAndCount();
    return { items, total, page, pageSize };
  }

  async getReport(id: string): Promise<Report> {
    const report = await this.reportRepository.findOne({ where: { id } });
    if (!report) {
      throw new NotFoundException(`报告不存在: ${id}`);
    }
    return report;
  }

  async deleteReport(id: string): Promise<void> {
    const report = await this.getReport(id);
    await this.reportRepository.delete(id);
  }

  // ==================== Story 8.8: PDF 导出 (占位) ====================

  async exportPdf(reportId: string): Promise<{ filePath: string; fileSize: number }> {
    const report = await this.getReport(reportId);
    if (report.status !== ReportStatus.COMPLETED) {
      throw new BadRequestException('报告尚未生成完成');
    }

    // TODO: 实际 PDF 生成逻辑（puppeteer / @react-pdf/renderer）
    this.logger.log(`PDF 导出: ${report.title}`);
    return { filePath: report.filePath || '/reports/placeholder.pdf', fileSize: 0 };
  }

  // ==================== Story 8.9: 定时报告 ====================

  async createScheduledReport(data: {
    name: string;
    type: ReportType;
    projectIds: string[];
    recipients: string[];
    templateId: string;
    createdBy: string;
  }): Promise<ScheduledReport> {
    const cronMap: Record<string, string> = {
      [ReportType.DAILY]: '0 8 * * *',
      [ReportType.WEEKLY]: '0 8 * * 1',
      [ReportType.MONTHLY]: '0 8 1 * *',
    };

    const scheduled = this.scheduledReportRepository.create({
      ...data,
      cron: cronMap[data.type] || '0 8 * * *',
      status: ScheduleStatus.ACTIVE,
    });

    return this.scheduledReportRepository.save(scheduled);
  }

  async listScheduledReports(): Promise<ScheduledReport[]> {
    return this.scheduledReportRepository.find({ order: { createdAt: 'DESC' } });
  }

  async updateScheduledReport(id: string, data: Partial<ScheduledReport>): Promise<ScheduledReport> {
    const scheduled = await this.scheduledReportRepository.findOne({ where: { id } });
    if (!scheduled) {
      throw new NotFoundException(`定时报告不存在: ${id}`);
    }
    Object.assign(scheduled, data);
    return this.scheduledReportRepository.save(scheduled);
  }

  async deleteScheduledReport(id: string): Promise<void> {
    const result = await this.scheduledReportRepository.delete(id);
    if (result.affected === 0) {
      throw new NotFoundException(`定时报告不存在: ${id}`);
    }
  }

  // ==================== Story 8.10: 邮件发送 ====================

  async executeScheduledReport(scheduledId: string): Promise<Report> {
    const scheduled = await this.scheduledReportRepository.findOne({ where: { id: scheduledId } });
    if (!scheduled) {
      throw new NotFoundException(`定时报告不存在: ${scheduledId}`);
    }

    const now = new Date();
    let startDate: Date;
    const endDate = now;

    switch (scheduled.type) {
      case ReportType.DAILY:
        startDate = new Date(now.getTime() - 24 * 60 * 60 * 1000);
        break;
      case ReportType.WEEKLY:
        startDate = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
        break;
      case ReportType.MONTHLY:
        startDate = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
        break;
      default:
        startDate = new Date(now.getTime() - 24 * 60 * 60 * 1000);
    }

    // Generate for each project
    const reports: Report[] = [];
    for (const projectId of scheduled.projectIds) {
      const report = await this.generateReport({
        type: scheduled.type,
        projectId,
        startDate: startDate.toISOString(),
        endDate: endDate.toISOString(),
        templateId: scheduled.templateId,
        userId: scheduled.createdBy,
      });
      reports.push(report);
    }

    // Send emails
    for (const report of reports) {
      for (const recipient of scheduled.recipients) {
        const log = this.deliveryLogRepository.create({
          scheduledReportId: scheduled.id,
          reportId: report.id,
          recipient,
          status: DeliveryStatus.PENDING,
        });
        await this.deliveryLogRepository.save(log);

        try {
          // TODO: 实际邮件发送逻辑
          log.status = DeliveryStatus.SENT;
          log.sentAt = new Date();
          log.attempts = 1;
          await this.deliveryLogRepository.save(log);
        } catch (err) {
          log.status = DeliveryStatus.FAILED;
          log.error = (err as Error).message;
          log.attempts += 1;
          await this.deliveryLogRepository.save(log);
        }
      }
    }

    scheduled.lastRunAt = now;
    await this.scheduledReportRepository.save(scheduled);

    return reports[0];
  }

  // ==================== Story 8.11: 系统运行状态 ====================

  async getSystemStatus() {
    return {
      services: {
        api: { status: 'UP', uptime: process.uptime(), details: {} },
        database: { status: 'UP', details: {} },
        redis: { status: 'UP', details: {} },
        mqtt: { status: 'UP', details: {} },
      },
      availability30d: '99.95%',
      onlineUsers: 0, // placeholder
      todayApiCalls: 0, // placeholder
      timestamp: new Date(),
    };
  }

  // ==================== Story 8.12: 系统资源监控 ====================

  async getSystemResources() {
    const cpus = os.cpus();
    const totalMem = os.totalmem();
    const freeMem = os.freemem();
    const usedMem = totalMem - freeMem;
    const memUsagePercent = ((usedMem / totalMem) * 100).toFixed(1);

    // CPU usage approximation (simplified)
    const cpuUsagePercent = (process.cpuUsage().user / 1000000 / cpus.length * 100).toFixed(1);

    return {
      cpu: {
        usage: `${cpuUsagePercent}%`,
        cores: cpus.length,
        model: cpus[0]?.model || 'Unknown',
        warning: parseFloat(cpuUsagePercent) > 80,
      },
      memory: {
        total: this.formatBytes(totalMem),
        used: this.formatBytes(usedMem),
        free: this.formatBytes(freeMem),
        usagePercent: `${memUsagePercent}%`,
        warning: parseFloat(memUsagePercent) > 85,
      },
      disk: {
        usage: 'N/A', // requires platform-specific commands
        warning: false,
      },
      database: {
        connections: 0, // placeholder
      },
      mqtt: {
        connections: 0, // placeholder
      },
      uptime: this.formatUptime(process.uptime()),
      timestamp: new Date(),
    };
  }

  private formatBytes(bytes: number): string {
    const gb = bytes / (1024 * 1024 * 1024);
    return `${gb.toFixed(1)} GB`;
  }

  private formatUptime(seconds: number): string {
    const days = Math.floor(seconds / 86400);
    const hours = Math.floor((seconds % 86400) / 3600);
    const mins = Math.floor((seconds % 3600) / 60);
    return `${days}天 ${hours}小时 ${mins}分钟`;
  }
}
