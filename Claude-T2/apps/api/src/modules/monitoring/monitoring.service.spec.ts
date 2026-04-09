import { Test, TestingModule } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { NotFoundException, BadRequestException } from '@nestjs/common';
import { MonitoringService } from './monitoring.service';
import { Device, DeviceStatus } from '../device/device.entity';
import { DeviceModelBinding, BindingStatus } from '../ai-model/device-model-binding.entity';
import { ModelDeployment, DeviceDeployment, DeploymentStatus, DeviceDeploymentStatus } from '../ai-model/model-deployment.entity';
import { AiModel, AiModelStatus, AiModelType } from '../ai-model/ai-model.entity';

describe('MonitoringService', () => {
  let service: MonitoringService;
  let deviceRepository: any;
  let bindingRepository: any;
  let deploymentRepository: any;
  let deviceDeploymentRepository: any;
  let aiModelRepository: any;

  const mockDeviceRepository = {
    count: jest.fn(),
    find: jest.fn(),
  };

  const mockBindingRepository = {
    find: jest.fn(),
    findOne: jest.fn(),
    create: jest.fn(),
    save: jest.fn(),
  };

  const mockDeploymentRepository = {
    findOne: jest.fn(),
  };

  const mockDeviceDeploymentRepository = {
    find: jest.fn(),
  };

  const mockAiModelRepository = {
    findOne: jest.fn(),
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        MonitoringService,
        { provide: getRepositoryToken(Device), useValue: mockDeviceRepository },
        { provide: getRepositoryToken(DeviceModelBinding), useValue: mockBindingRepository },
        { provide: getRepositoryToken(ModelDeployment), useValue: mockDeploymentRepository },
        { provide: getRepositoryToken(DeviceDeployment), useValue: mockDeviceDeploymentRepository },
        { provide: getRepositoryToken(AiModel), useValue: mockAiModelRepository },
      ],
    }).compile();

    service = module.get<MonitoringService>(MonitoringService);
    deviceRepository = module.get(getRepositoryToken(Device));
    bindingRepository = module.get(getRepositoryToken(DeviceModelBinding));
    deploymentRepository = module.get(getRepositoryToken(ModelDeployment));
    deviceDeploymentRepository = module.get(getRepositoryToken(DeviceDeployment));
    aiModelRepository = module.get(getRepositoryToken(AiModel));
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  describe('getModelStatus', () => {
    it('应该返回模型运行状态统计', async () => {
      const bindings = [
        {
          deviceId: 'device-1',
          status: BindingStatus.RUNNING,
          device: { id: 'device-1', name: '设备1' },
          createdAt: new Date(),
        },
        {
          deviceId: 'device-2',
          status: BindingStatus.ERROR,
          error: 'MODEL_CRASH',
          device: { id: 'device-2', name: '设备2' },
          createdAt: new Date(Date.now() - 7200000),
          lastSyncAt: new Date(Date.now() - 7200000),
        },
      ];

      mockBindingRepository.find.mockResolvedValue(bindings);
      mockDeviceRepository.count.mockResolvedValue(10);

      const result = await service.getModelStatus();

      expect(result.stats.running).toBe(1);
      expect(result.stats.error).toBe(1);
      expect(result.stats.unbound).toBe(8); // 10 - 2 bound
      expect(result.errorDevices).toHaveLength(1);
      expect(result.errorDevices[0].name).toBe('设备2');
      expect(result.errorDevices[0].errorType).toBe('MODEL_CRASH');
    });

    it('没有绑定时应返回全未绑定', async () => {
      mockBindingRepository.find.mockResolvedValue([]);
      mockDeviceRepository.count.mockResolvedValue(5);

      const result = await service.getModelStatus();

      expect(result.stats.running).toBe(0);
      expect(result.stats.error).toBe(0);
      expect(result.stats.unbound).toBe(5);
      expect(result.errorDevices).toHaveLength(0);
    });
  });

  describe('getDeploymentProgress', () => {
    it('应该返回部署进度详情', async () => {
      const deployment = {
        id: 'deployment-1',
        status: DeploymentStatus.IN_PROGRESS,
        totalDevices: 3,
      };
      const deviceDeployments = [
        { deviceId: 'd1', status: DeviceDeploymentStatus.SUCCESS },
        { deviceId: 'd2', status: DeviceDeploymentStatus.DOWNLOADING },
        { deviceId: 'd3', status: DeviceDeploymentStatus.PENDING },
      ];

      mockDeploymentRepository.findOne.mockResolvedValue(deployment);
      mockDeviceDeploymentRepository.find.mockResolvedValue(deviceDeployments);

      const result = await service.getDeploymentProgress('deployment-1');

      expect(result.deployment).toEqual(deployment);
      expect(result.progress.total).toBe(3);
      expect(result.progress.success).toBe(1);
      expect(result.progress.downloading).toBe(1);
      expect(result.progress.pending).toBe(1);
    });

    it('部署任务不存在时应抛出异常', async () => {
      mockDeploymentRepository.findOne.mockResolvedValue(null);

      await expect(service.getDeploymentProgress('non-existent')).rejects.toThrow(
        NotFoundException,
      );
    });
  });

  describe('batchBindModel', () => {
    it('设备列表为空时应抛出异常', async () => {
      await expect(service.batchBindModel([], 'model-1')).rejects.toThrow(
        BadRequestException,
      );
    });

    it('超过50台设备时应抛出异常', async () => {
      const ids = Array(51).fill('device-id');
      await expect(service.batchBindModel(ids, 'model-1')).rejects.toThrow(
        BadRequestException,
      );
    });

    it('模型不存在时应抛出异常', async () => {
      mockAiModelRepository.findOne.mockResolvedValue(null);

      await expect(service.batchBindModel(['d1'], 'non-existent')).rejects.toThrow(
        NotFoundException,
      );
    });

    it('模型未发布时应抛出异常', async () => {
      mockAiModelRepository.findOne.mockResolvedValue({
        id: 'model-1',
        status: AiModelStatus.DRAFT,
      });

      await expect(service.batchBindModel(['d1'], 'model-1')).rejects.toThrow(
        BadRequestException,
      );
    });

    it('应该成功批量绑定兼容设备', async () => {
      const model = {
        id: 'model-1',
        code: 'TEST_MODEL',
        version: 'v1.0.0',
        status: AiModelStatus.PUBLISHED,
        applicableDeviceTypes: ['WATER_LEVEL_SENSOR'],
      };
      const devices = [
        { id: 'd1', deviceType: 'WATER_LEVEL_SENSOR', name: '设备1' },
        { id: 'd2', deviceType: 'FLOW_METER', name: '设备2' },
      ];

      mockAiModelRepository.findOne.mockResolvedValue(model);
      mockDeviceRepository.find.mockResolvedValue(devices);
      mockBindingRepository.findOne.mockResolvedValue(null);
      mockBindingRepository.create.mockReturnValue({});
      mockBindingRepository.save.mockResolvedValue({});

      const result = await service.batchBindModel(['d1', 'd2'], 'model-1');

      expect(result.success).toBe(true);
      expect(result.compatibleDevices).toBe(1);
      expect(result.incompatibleDevices).toBe(1);
    });
  });

  describe('getAiResultsSummary', () => {
    it('应该返回AI分析结果聚合统计', async () => {
      const result = await service.getAiResultsSummary({
        projectId: 'proj-1',
      });

      expect(result.summary).toBeDefined();
      expect(result.summary.totalAnalysisCount).toBe(0);
      expect(result.filters.projectId).toBe('proj-1');
    });
  });
});
