import { Test, TestingModule } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { NotFoundException, BadRequestException } from '@nestjs/common';
import { AlertService } from './alert.service';
import {
  Alert,
  AlertType,
  AlertLevel,
  AlertStatus,
  AlertStatusHistory,
  WorkOrder,
  AlertNotification,
} from './alert.entity';
import { AuditLogService } from '../audit/audit-log.service';

describe('AlertService', () => {
  let service: AlertService;
  let alertRepository: any;
  let statusHistoryRepository: any;
  let workOrderRepository: any;

  const mockAlertRepository = {
    findOne: jest.fn(),
    create: jest.fn(),
    save: jest.fn(),
    count: jest.fn(),
    createQueryBuilder: jest.fn(),
  };

  const mockStatusHistoryRepository = {
    save: jest.fn(),
    find: jest.fn(),
  };

  const mockWorkOrderRepository = {
    create: jest.fn(),
    save: jest.fn(),
    count: jest.fn(),
    find: jest.fn(),
  };

  const mockNotificationRepository = {
    find: jest.fn(),
    save: jest.fn(),
  };

  const mockAuditLogService = {
    createLog: jest.fn().mockResolvedValue(undefined),
  };

  const mockQb = {
    leftJoinAndSelect: jest.fn().mockReturnThis(),
    orderBy: jest.fn().mockReturnThis(),
    addOrderBy: jest.fn().mockReturnThis(),
    andWhere: jest.fn().mockReturnThis(),
    skip: jest.fn().mockReturnThis(),
    take: jest.fn().mockReturnThis(),
    getManyAndCount: jest.fn(),
    select: jest.fn().mockReturnThis(),
    addSelect: jest.fn().mockReturnThis(),
    where: jest.fn().mockReturnThis(),
    groupBy: jest.fn().mockReturnThis(),
    getRawMany: jest.fn(),
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        AlertService,
        { provide: getRepositoryToken(Alert), useValue: mockAlertRepository },
        { provide: getRepositoryToken(AlertStatusHistory), useValue: mockStatusHistoryRepository },
        { provide: getRepositoryToken(WorkOrder), useValue: mockWorkOrderRepository },
        { provide: getRepositoryToken(AlertNotification), useValue: mockNotificationRepository },
        { provide: AuditLogService, useValue: mockAuditLogService },
      ],
    }).compile();

    service = module.get<AlertService>(AlertService);
    alertRepository = module.get(getRepositoryToken(Alert));
    statusHistoryRepository = module.get(getRepositoryToken(AlertStatusHistory));
    workOrderRepository = module.get(getRepositoryToken(WorkOrder));
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  describe('classifyLevel', () => {
    it('OVERFLOW 应为 CRITICAL', () => {
      expect(service.classifyLevel(AlertType.OVERFLOW, 0.5)).toBe(AlertLevel.CRITICAL);
    });

    it('FULL_PIPE 应为 CRITICAL', () => {
      expect(service.classifyLevel(AlertType.FULL_PIPE, 0.5)).toBe(AlertLevel.CRITICAL);
    });

    it('SILT 应为 HIGH', () => {
      expect(service.classifyLevel(AlertType.SILT, 0.5)).toBe(AlertLevel.HIGH);
    });

    it('置信度>=0.9 应为 HIGH', () => {
      expect(service.classifyLevel(AlertType.MIXED_CONNECTION, 0.92)).toBe(AlertLevel.HIGH);
    });

    it('置信度0.7-0.89 应为 MEDIUM', () => {
      expect(service.classifyLevel(AlertType.MIXED_CONNECTION, 0.75)).toBe(AlertLevel.MEDIUM);
    });

    it('置信度<0.7 应为 LOW', () => {
      expect(service.classifyLevel(AlertType.THRESHOLD_EXCEEDED, 0.5)).toBe(AlertLevel.LOW);
    });
  });

  describe('create', () => {
    it('应该成功创建预警', async () => {
      const data = {
        deviceId: 'device-1',
        type: AlertType.OVERFLOW,
        title: '检测到溢流',
        content: '设备检测到溢流现象',
        confidence: 0.95,
      };

      mockAlertRepository.create.mockReturnValue({ ...data, level: AlertLevel.CRITICAL, status: AlertStatus.PENDING });
      mockAlertRepository.save.mockResolvedValue({ id: 'alert-1', ...data, level: AlertLevel.CRITICAL });
      mockStatusHistoryRepository.save.mockResolvedValue({});

      const result = await service.create(data);

      expect(result.level).toBe(AlertLevel.CRITICAL);
      expect(mockStatusHistoryRepository.save).toHaveBeenCalled();
    });
  });

  describe('acknowledge', () => {
    it('应该成功确认预警', async () => {
      const alert = {
        id: 'alert-1',
        status: AlertStatus.PENDING,
      };

      mockAlertRepository.findOne.mockResolvedValue(alert);
      mockAlertRepository.save.mockResolvedValue({ ...alert, status: AlertStatus.ACKNOWLEDGED });
      mockStatusHistoryRepository.save.mockResolvedValue({});

      const result = await service.acknowledge('alert-1', 'user-1');

      expect(result.status).toBe(AlertStatus.ACKNOWLEDGED);
    });

    it('非PENDING状态应抛出异常', async () => {
      mockAlertRepository.findOne.mockResolvedValue({ status: AlertStatus.CLOSED });

      await expect(service.acknowledge('alert-1', 'user-1')).rejects.toThrow(BadRequestException);
    });

    it('预警不存在应抛出异常', async () => {
      mockAlertRepository.findOne.mockResolvedValue(null);

      await expect(service.acknowledge('non-existent', 'user-1')).rejects.toThrow(NotFoundException);
    });
  });

  describe('process', () => {
    it('应该成功开始处置', async () => {
      const alert = { id: 'alert-1', status: AlertStatus.ACKNOWLEDGED };
      mockAlertRepository.findOne.mockResolvedValue(alert);
      mockAlertRepository.save.mockResolvedValue({ ...alert, status: AlertStatus.IN_PROGRESS });
      mockStatusHistoryRepository.save.mockResolvedValue({});

      const result = await service.process('alert-1', 'user-1', '开始处理');

      expect(result.status).toBe(AlertStatus.IN_PROGRESS);
    });

    it('处置说明为空应抛出异常', async () => {
      mockAlertRepository.findOne.mockResolvedValue({ status: AlertStatus.ACKNOWLEDGED });

      await expect(service.process('alert-1', 'user-1', '')).rejects.toThrow(BadRequestException);
    });
  });

  describe('close', () => {
    it('应该成功关闭预警', async () => {
      const alert = {
        id: 'alert-1',
        status: AlertStatus.IN_PROGRESS,
        createdAt: new Date(Date.now() - 3600000),
      };
      mockAlertRepository.findOne.mockResolvedValue(alert);
      mockAlertRepository.save.mockResolvedValue({ ...alert, status: AlertStatus.CLOSED });
      mockStatusHistoryRepository.save.mockResolvedValue({});

      const result = await service.close('alert-1', 'user-1', '已修复', '管道老化');

      expect(result.status).toBe(AlertStatus.CLOSED);
    });

    it('处置结果为空应抛出异常', async () => {
      mockAlertRepository.findOne.mockResolvedValue({ status: AlertStatus.IN_PROGRESS });

      await expect(service.close('alert-1', 'user-1', '')).rejects.toThrow(BadRequestException);
    });
  });

  describe('createWorkOrder', () => {
    it('应该成功创建工单', async () => {
      const alert = { id: 'alert-1', status: AlertStatus.PENDING, level: AlertLevel.HIGH };
      mockAlertRepository.findOne.mockResolvedValue(alert);
      mockWorkOrderRepository.count.mockResolvedValue(0);
      mockWorkOrderRepository.create.mockReturnValue({});
      mockWorkOrderRepository.save.mockResolvedValue({ workOrderNo: 'WO-20260409-0001' });
      mockAlertRepository.save.mockResolvedValue({});
      mockStatusHistoryRepository.save.mockResolvedValue({});

      const result = await service.createWorkOrder('alert-1', {
        title: '处置工单',
        description: '请尽快处理',
      }, 'user-1');

      expect(result.workOrderNo).toBe('WO-20260409-0001');
    });
  });

  describe('getStats', () => {
    it('应该返回预警统计', async () => {
      mockAlertRepository.createQueryBuilder.mockReturnValue(mockQb);
      mockQb.getRawMany.mockResolvedValue([
        { level: 'critical', count: '2' },
        { level: 'high', count: '5' },
      ]);
      mockAlertRepository.count.mockResolvedValue(3);

      const result = await service.getStats();

      expect(result.critical).toBe(2);
      expect(result.high).toBe(5);
      expect(result.unacknowledged).toBe(3);
    });
  });

  describe('checkEscalation', () => {
    it('应该检查并升级超时预警', async () => {
      const overdueAlert = {
        id: 'alert-1',
        status: AlertStatus.PENDING,
        level: AlertLevel.CRITICAL,
        isEscalated: false,
      };

      const escalationQb = {
        where: jest.fn().mockReturnThis(),
        andWhere: jest.fn().mockReturnThis(),
        getMany: jest.fn()
          .mockResolvedValueOnce([overdueAlert])  // CRITICAL level
          .mockResolvedValueOnce([])               // HIGH level
          .mockResolvedValueOnce([])               // MEDIUM level
          .mockResolvedValueOnce([]),              // LOW level
      };
      mockAlertRepository.createQueryBuilder.mockReturnValue(escalationQb);
      mockAlertRepository.save.mockResolvedValue({ ...overdueAlert, isEscalated: true });
      mockStatusHistoryRepository.save.mockResolvedValue({});

      const result = await service.checkEscalation();

      expect(result.escalatedCount).toBe(1);
    });
  });
});
