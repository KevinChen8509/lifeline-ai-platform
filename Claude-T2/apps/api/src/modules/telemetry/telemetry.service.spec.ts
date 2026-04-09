import { Test, TestingModule } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { NotFoundException, BadRequestException } from '@nestjs/common';
import { TelemetryService } from './telemetry.service';
import { DeviceTelemetry, BackupConfig, BackupLog, BackupType, BackupStatus, ArchivedDataMeta } from './telemetry.entity';

describe('TelemetryService', () => {
  let service: TelemetryService;
  let telemetryRepo: any;
  let backupConfigRepo: any;
  let backupLogRepo: any;
  let archiveMetaRepo: any;

  const mockTelemetryRepo = {
    create: jest.fn(),
    save: jest.fn(),
    find: jest.fn(),
    delete: jest.fn(),
    createQueryBuilder: jest.fn(),
  };

  const mockBackupConfigRepo = {
    create: jest.fn(),
    save: jest.fn(),
    find: jest.fn(),
    findOne: jest.fn(),
  };

  const mockBackupLogRepo = {
    create: jest.fn(),
    save: jest.fn(),
    findOne: jest.fn(),
    createQueryBuilder: jest.fn(),
  };

  const mockArchiveMetaRepo = {
    create: jest.fn(),
    save: jest.fn(),
  };

  const mockQb = {
    where: jest.fn().mockReturnThis(),
    andWhere: jest.fn().mockReturnThis(),
    orderBy: jest.fn().mockReturnThis(),
    skip: jest.fn().mockReturnThis(),
    take: jest.fn().mockReturnThis(),
    select: jest.fn().mockReturnThis(),
    addSelect: jest.fn().mockReturnThis(),
    getManyAndCount: jest.fn(),
    getRawMany: jest.fn(),
    limit: jest.fn().mockReturnThis(),
    getMany: jest.fn(),
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        TelemetryService,
        { provide: getRepositoryToken(DeviceTelemetry), useValue: mockTelemetryRepo },
        { provide: getRepositoryToken(BackupConfig), useValue: mockBackupConfigRepo },
        { provide: getRepositoryToken(BackupLog), useValue: mockBackupLogRepo },
        { provide: getRepositoryToken(ArchivedDataMeta), useValue: mockArchiveMetaRepo },
      ],
    }).compile();

    service = module.get<TelemetryService>(TelemetryService);
    telemetryRepo = module.get(getRepositoryToken(DeviceTelemetry));
    backupConfigRepo = module.get(getRepositoryToken(BackupConfig));
    backupLogRepo = module.get(getRepositoryToken(BackupLog));
    archiveMetaRepo = module.get(getRepositoryToken(ArchivedDataMeta));
  });

  afterEach(() => jest.clearAllMocks());

  describe('writeTelemetry', () => {
    it('应该写入单条遥测数据', async () => {
      const data = { deviceId: 'd1', timestamp: new Date(), metrics: { level: 1.2 } };
      mockTelemetryRepo.create.mockReturnValue(data);
      mockTelemetryRepo.save.mockResolvedValue({ id: 't1', ...data });

      const result = await service.writeTelemetry(data);
      expect(result.id).toBe('t1');
    });
  });

  describe('writeBatch', () => {
    it('应该批量写入数据', async () => {
      const records = [
        { deviceId: 'd1', timestamp: new Date(), metrics: { level: 1.2 } },
        { deviceId: 'd1', timestamp: new Date(), metrics: { level: 1.3 } },
      ];
      mockTelemetryRepo.create.mockImplementation((r) => r);
      mockTelemetryRepo.save.mockResolvedValue(records);

      const result = await service.writeBatch(records);
      expect(result).toHaveLength(2);
    });

    it('空数据应抛出异常', async () => {
      await expect(service.writeBatch([])).rejects.toThrow(BadRequestException);
    });

    it('超过1000条应抛出异常', async () => {
      const records = Array(1001).fill({ deviceId: 'd1', timestamp: new Date(), metrics: {} });
      await expect(service.writeBatch(records)).rejects.toThrow(BadRequestException);
    });
  });

  describe('findTelemetry', () => {
    it('应该返回分页遥测数据', async () => {
      const items = [{ id: 't1', metrics: { level: 1.2 } }];
      mockTelemetryRepo.createQueryBuilder.mockReturnValue(mockQb);
      mockQb.getManyAndCount.mockResolvedValue([items, 1]);

      const result = await service.findTelemetry({ deviceId: 'd1' });
      expect(result.items).toHaveLength(1);
      expect(result.total).toBe(1);
    });
  });

  describe('getChartData', () => {
    it('应该返回图表数据', async () => {
      const rawData = [
        { timestamp: '2026-04-09T10:00:00', level: '1.2', flow: '50.5' },
      ];
      mockTelemetryRepo.createQueryBuilder.mockReturnValue(mockQb);
      mockQb.getRawMany.mockResolvedValue(rawData);

      const result = await service.getChartData({
        deviceId: 'd1',
        metrics: ['level', 'flow'],
      });

      expect(result.data).toHaveLength(1);
      expect(result.metrics).toEqual(['level', 'flow']);
    });
  });

  describe('createBackupConfig', () => {
    it('应该创建备份配置', async () => {
      const data = { type: BackupType.FULL, schedule: '0 3 * * 0', retentionDays: 30 };
      mockBackupConfigRepo.create.mockReturnValue({ ...data });
      mockBackupConfigRepo.save.mockResolvedValue({ id: 'cfg-1', ...data });

      const result = await service.createBackupConfig(data);
      expect(result.id).toBe('cfg-1');
    });
  });

  describe('executeBackup', () => {
    it('应该执行备份', async () => {
      mockBackupConfigRepo.findOne.mockResolvedValue({ id: 'cfg-1', type: BackupType.FULL });
      mockBackupLogRepo.create.mockReturnValue({ configId: 'cfg-1', type: BackupType.FULL, status: BackupStatus.RUNNING });
      mockBackupLogRepo.save.mockImplementation(async (e) => e);

      const result = await service.executeBackup('cfg-1');
      expect(result.status).toBe(BackupStatus.COMPLETED);
    });

    it('配置不存在应抛出异常', async () => {
      mockBackupConfigRepo.findOne.mockResolvedValue(null);
      await expect(service.executeBackup('non-existent')).rejects.toThrow(NotFoundException);
    });
  });

  describe('restoreBackup', () => {
    it('应该恢复已完成的备份', async () => {
      mockBackupLogRepo.findOne.mockResolvedValue({
        id: 'log-1',
        status: BackupStatus.COMPLETED,
        filePath: '/backup/full_2026-04-09.tar.gz',
      });

      const result = await service.restoreBackup('log-1');
      expect(result.filePath).toBeDefined();
    });

    it('非完成状态应抛出异常', async () => {
      mockBackupLogRepo.findOne.mockResolvedValue({ status: BackupStatus.FAILED });
      await expect(service.restoreBackup('log-1')).rejects.toThrow(BadRequestException);
    });
  });

  describe('archiveOldData', () => {
    it('应该归档超过2年的数据', async () => {
      const oldData = [{ id: 't1', timestamp: new Date('2023-01-01') }];
      mockTelemetryRepo.find.mockResolvedValue(oldData);
      mockArchiveMetaRepo.create.mockReturnValue({});
      mockArchiveMetaRepo.save.mockResolvedValue({});
      mockTelemetryRepo.delete.mockResolvedValue({});

      const result = await service.archiveOldData();
      expect(result.archivedCount).toBe(1);
    });

    it('无过期数据时应返回0', async () => {
      mockTelemetryRepo.find.mockResolvedValue([]);
      const result = await service.archiveOldData();
      expect(result.archivedCount).toBe(0);
    });
  });
});
