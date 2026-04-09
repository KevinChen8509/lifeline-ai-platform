import { Test, TestingModule } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { NotFoundException, BadRequestException } from '@nestjs/common';
import { DashboardService } from './dashboard.service';
import {
  ReportTemplate,
  Report,
  ReportType,
  ReportStatus,
  ScheduledReport,
  ScheduleStatus,
  ReportDeliveryLog,
  DeliveryStatus,
} from './dashboard.entity';

describe('DashboardService', () => {
  let service: DashboardService;

  const mockTemplateRepo = {
    create: jest.fn(),
    save: jest.fn(),
    find: jest.fn(),
    findOne: jest.fn(),
    delete: jest.fn(),
    count: jest.fn(),
  };

  const mockReportRepo = {
    create: jest.fn(),
    save: jest.fn(),
    findOne: jest.fn(),
    delete: jest.fn(),
    createQueryBuilder: jest.fn(),
  };

  const mockScheduledRepo = {
    create: jest.fn(),
    save: jest.fn(),
    find: jest.fn(),
    findOne: jest.fn(),
    delete: jest.fn(),
  };

  const mockDeliveryLogRepo = {
    create: jest.fn(),
    save: jest.fn(),
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        DashboardService,
        { provide: getRepositoryToken(ReportTemplate), useValue: mockTemplateRepo },
        { provide: getRepositoryToken(Report), useValue: mockReportRepo },
        { provide: getRepositoryToken(ScheduledReport), useValue: mockScheduledRepo },
        { provide: getRepositoryToken(ReportDeliveryLog), useValue: mockDeliveryLogRepo },
      ],
    }).compile();

    service = module.get<DashboardService>(DashboardService);
  });

  afterEach(() => jest.clearAllMocks());

  // ==================== 看板统计 ====================

  describe('getDeviceStats', () => {
    it('应该返回设备统计数据', async () => {
      const result = await service.getDeviceStats('proj-1');
      expect(result).toHaveProperty('total');
      expect(result).toHaveProperty('online');
      expect(result).toHaveProperty('offline');
      expect(result).toHaveProperty('onlineRate');
      expect(result).toHaveProperty('trend');
    });
  });

  describe('getAlertStats', () => {
    it('应该返回预警统计数据', async () => {
      const result = await service.getAlertStats('proj-1');
      expect(result).toHaveProperty('todayTotal');
      expect(result).toHaveProperty('pending');
      expect(result).toHaveProperty('byLevel');
      expect(result.byLevel).toHaveProperty('critical');
    });
  });

  describe('getKpi', () => {
    it('应该返回关键指标', async () => {
      const result = await service.getKpi('proj-1');
      expect(result).toHaveProperty('avgHandleTime');
      expect(result).toHaveProperty('handleRate');
      expect(result).toHaveProperty('aiAnalysisCount');
      expect(result).toHaveProperty('trends');
    });
  });

  // ==================== 统计分析 ====================

  describe('getAlertTypeDistribution', () => {
    it('应该返回预警类型分布', async () => {
      const result = await service.getAlertTypeDistribution({});
      expect(result.distribution).toHaveLength(5);
      expect(result.distribution[0]).toHaveProperty('type');
      expect(result.distribution[0]).toHaveProperty('count');
    });
  });

  describe('getAlertHandlingEfficiency', () => {
    it('应该返回处置效率统计', async () => {
      const result = await service.getAlertHandlingEfficiency({});
      expect(result).toHaveProperty('avgResponseTime');
      expect(result).toHaveProperty('avgHandleTime');
      expect(result).toHaveProperty('timelinessRate');
      expect(result).toHaveProperty('slaThresholds');
      expect(result.slaThresholds.CRITICAL).toBe(4);
    });
  });

  // ==================== 报告模板 ====================

  describe('initDefaultTemplates', () => {
    it('有模板时应跳过初始化', async () => {
      mockTemplateRepo.count.mockResolvedValue(3);
      await service.initDefaultTemplates();
      expect(mockTemplateRepo.create).not.toHaveBeenCalled();
    });

    it('无模板时应创建3个默认模板', async () => {
      mockTemplateRepo.count.mockResolvedValue(0);
      mockTemplateRepo.create.mockImplementation((d) => d);
      mockTemplateRepo.save.mockResolvedValue({});

      await service.initDefaultTemplates();
      expect(mockTemplateRepo.create).toHaveBeenCalledTimes(3);
    });
  });

  describe('createTemplate', () => {
    it('应该创建自定义模板', async () => {
      const data = {
        name: '自定义模板',
        type: ReportType.CUSTOM,
        sections: [{ type: 'alert_summary', title: '预警汇总', enabled: true }],
      };
      mockTemplateRepo.create.mockReturnValue({ ...data, id: 'tpl-1' });
      mockTemplateRepo.save.mockResolvedValue({ ...data, id: 'tpl-1' });

      const result = await service.createTemplate(data);
      expect(result.id).toBe('tpl-1');
    });
  });

  describe('listTemplates', () => {
    it('应该返回模板列表', async () => {
      const templates = [{ id: 'tpl-1', name: '日报' }];
      mockTemplateRepo.find.mockResolvedValue(templates);

      const result = await service.listTemplates();
      expect(result).toHaveLength(1);
    });
  });

  describe('getTemplate', () => {
    it('不存在应抛出异常', async () => {
      mockTemplateRepo.findOne.mockResolvedValue(null);
      await expect(service.getTemplate('non-existent')).rejects.toThrow(NotFoundException);
    });
  });

  describe('deleteTemplate', () => {
    it('默认模板不能删除', async () => {
      mockTemplateRepo.findOne.mockResolvedValue({ id: 'tpl-1', isDefault: true });
      await expect(service.deleteTemplate('tpl-1')).rejects.toThrow(BadRequestException);
    });
  });

  // ==================== 报告生成 ====================

  describe('generateReport', () => {
    it('应该生成报告', async () => {
      const template = {
        id: 'tpl-1',
        name: '日报',
        sections: [
          { type: 'alert_summary', title: '预警汇总', enabled: true },
        ],
      };
      mockTemplateRepo.findOne.mockResolvedValue(template);
      mockReportRepo.create.mockImplementation((d) => ({ ...d, id: 'rpt-1' }));
      mockReportRepo.save.mockImplementation(async (d) => d);

      const result = await service.generateReport({
        type: ReportType.DAILY,
        projectId: 'proj-1',
        startDate: '2026-04-08',
        endDate: '2026-04-09',
        templateId: 'tpl-1',
        userId: 'user-1',
      });
      expect(result.status).toBe(ReportStatus.COMPLETED);
    });

    it('模板不存在应抛出异常', async () => {
      mockTemplateRepo.findOne.mockResolvedValue(null);
      await expect(service.generateReport({
        type: ReportType.DAILY,
        projectId: 'proj-1',
        startDate: '2026-04-08',
        endDate: '2026-04-09',
        templateId: 'non-existent',
        userId: 'user-1',
      })).rejects.toThrow(NotFoundException);
    });
  });

  describe('listReports', () => {
    it('应该返回分页报告列表', async () => {
      const reportsQb = {
        orderBy: jest.fn().mockReturnThis(),
        skip: jest.fn().mockReturnThis(),
        take: jest.fn().mockReturnThis(),
        andWhere: jest.fn().mockReturnThis(),
        getManyAndCount: jest.fn().mockResolvedValue([[{ id: 'rpt-1' }], 1]),
      };
      mockReportRepo.createQueryBuilder.mockReturnValue(reportsQb);

      const result = await service.listReports({ page: 1 });
      expect(result.items).toHaveLength(1);
      expect(result.total).toBe(1);
    });
  });

  describe('getReport', () => {
    it('不存在应抛出异常', async () => {
      mockReportRepo.findOne.mockResolvedValue(null);
      await expect(service.getReport('non-existent')).rejects.toThrow(NotFoundException);
    });
  });

  // ==================== PDF 导出 ====================

  describe('exportPdf', () => {
    it('未完成的报告应抛出异常', async () => {
      mockReportRepo.findOne.mockResolvedValue({ id: 'rpt-1', status: ReportStatus.GENERATING });
      await expect(service.exportPdf('rpt-1')).rejects.toThrow(BadRequestException);
    });

    it('应该返回PDF路径', async () => {
      mockReportRepo.findOne.mockResolvedValue({
        id: 'rpt-1',
        status: ReportStatus.COMPLETED,
        filePath: '/reports/daily/rpt-1.pdf',
      });

      const result = await service.exportPdf('rpt-1');
      expect(result.filePath).toBeDefined();
    });
  });

  // ==================== 定时报告 ====================

  describe('createScheduledReport', () => {
    it('应该创建定时报告', async () => {
      const data = {
        name: '每日报告',
        type: ReportType.DAILY,
        projectIds: ['proj-1'],
        recipients: ['admin@example.com'],
        templateId: 'tpl-1',
        createdBy: 'user-1',
      };
      mockScheduledRepo.create.mockImplementation((d) => ({ ...d, id: 'sched-1' }));
      mockScheduledRepo.save.mockResolvedValue({ ...data, id: 'sched-1' });

      const result = await service.createScheduledReport(data);
      expect(result.id).toBe('sched-1');
    });
  });

  describe('deleteScheduledReport', () => {
    it('不存在应抛出异常', async () => {
      mockScheduledRepo.delete.mockResolvedValue({ affected: 0 });
      await expect(service.deleteScheduledReport('non-existent')).rejects.toThrow(NotFoundException);
    });
  });

  // ==================== 系统监控 ====================

  describe('getSystemStatus', () => {
    it('应该返回系统状态', async () => {
      const result = await service.getSystemStatus();
      expect(result.services).toHaveProperty('api');
      expect(result.services).toHaveProperty('database');
      expect(result).toHaveProperty('availability30d');
      expect(result).toHaveProperty('timestamp');
    });
  });

  describe('getSystemResources', () => {
    it('应该返回系统资源信息', async () => {
      const result = await service.getSystemResources();
      expect(result).toHaveProperty('cpu');
      expect(result).toHaveProperty('memory');
      expect(result).toHaveProperty('disk');
      expect(result.cpu).toHaveProperty('cores');
      expect(result.memory).toHaveProperty('usagePercent');
      expect(result).toHaveProperty('uptime');
    });
  });
});
