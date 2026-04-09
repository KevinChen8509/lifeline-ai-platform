import { Test, TestingModule } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { DataSource } from 'typeorm';
import {
  ConflictException,
  NotFoundException,
  BadRequestException,
} from '@nestjs/common';
import { AiModelService } from './ai-model.service';
import { AiModel, AiModelStatus, AiModelType } from './ai-model.entity';
import { DeviceModelBinding, BindingStatus } from './device-model-binding.entity';
import { AiModelVersion } from './ai-model-version.entity';
import { ModelDeployment, DeviceDeployment } from './model-deployment.entity';
import { AuditLogService } from '../audit/audit-log.service';

describe('AiModelService', () => {
  let service: AiModelService;
  let aiModelRepository: any;
  let bindingRepository: any;
  let auditLogService: any;

  const mockAiModelRepository = {
    findOne: jest.fn(),
    create: jest.fn(),
    save: jest.fn(),
    remove: jest.fn(),
    createQueryBuilder: jest.fn(),
  };

  const mockBindingRepository = {
    count: jest.fn(),
    createQueryBuilder: jest.fn(),
    findAndCount: jest.fn(),
  };

  const mockVersionRepository = {
    findOne: jest.fn(),
    create: jest.fn(),
    save: jest.fn(),
    find: jest.fn(),
  };

  const mockDeploymentRepository = {
    findOne: jest.fn(),
    create: jest.fn(),
    save: jest.fn(),
    createQueryBuilder: jest.fn(),
  };

  const mockDeviceDeploymentRepository = {
    findOne: jest.fn(),
    find: jest.fn(),
    insert: jest.fn(),
    save: jest.fn(),
    update: jest.fn(),
  };

  const mockAuditLogService = {
    createLog: jest.fn().mockResolvedValue(undefined),
  };

  const mockDataSource = {
    transaction: jest.fn((cb) => cb({
      update: jest.fn().mockResolvedValue(undefined),
      save: jest.fn((entity) => entity),
    })),
  };

  const mockQueryBuilder = {
    andWhere: jest.fn().mockReturnThis(),
    orderBy: jest.fn().mockReturnThis(),
    skip: jest.fn().mockReturnThis(),
    take: jest.fn().mockReturnThis(),
    getManyAndCount: jest.fn(),
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        AiModelService,
        {
          provide: getRepositoryToken(AiModel),
          useValue: mockAiModelRepository,
        },
        {
          provide: getRepositoryToken(DeviceModelBinding),
          useValue: mockBindingRepository,
        },
        {
          provide: getRepositoryToken(AiModelVersion),
          useValue: mockVersionRepository,
        },
        {
          provide: getRepositoryToken(ModelDeployment),
          useValue: mockDeploymentRepository,
        },
        {
          provide: getRepositoryToken(DeviceDeployment),
          useValue: mockDeviceDeploymentRepository,
        },
        {
          provide: AuditLogService,
          useValue: mockAuditLogService,
        },
        {
          provide: DataSource,
          useValue: mockDataSource,
        },
      ],
    }).compile();

    service = module.get<AiModelService>(AiModelService);
    aiModelRepository = module.get(getRepositoryToken(AiModel));
    bindingRepository = module.get(getRepositoryToken(DeviceModelBinding));
    auditLogService = module.get(AuditLogService);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  describe('create', () => {
    it('应该成功创建AI模型', async () => {
      const createDto = {
        name: '错混接检测模型',
        code: 'MIXED_CONNECTION_V2',
        version: 'v2.1.0',
        type: AiModelType.MIXED_CONNECTION,
        description: '基于深度学习的管网错混接智能检测模型',
      };
      const operatorId = 'user-uuid';

      mockAiModelRepository.findOne.mockResolvedValue(null);
      mockAiModelRepository.create.mockReturnValue({
        ...createDto,
        status: AiModelStatus.DRAFT,
      });
      mockAiModelRepository.save.mockResolvedValue({
        id: 'model-uuid',
        ...createDto,
        status: AiModelStatus.DRAFT,
      });

      const result = await service.create(createDto, operatorId);

      expect(mockAiModelRepository.findOne).toHaveBeenCalledWith({
        where: { code: 'MIXED_CONNECTION_V2' },
      });
      expect(mockAuditLogService.createLog).toHaveBeenCalled();
      expect(result.code).toBe('MIXED_CONNECTION_V2');
    });

    it('当模型编码已存在时应该抛出冲突异常', async () => {
      const createDto = {
        name: '错混接检测模型',
        code: 'MIXED_CONNECTION_V2',
        version: 'v2.1.0',
        type: AiModelType.MIXED_CONNECTION,
      };
      const operatorId = 'user-uuid';

      mockAiModelRepository.findOne.mockResolvedValue({
        id: 'existing-uuid',
        code: 'MIXED_CONNECTION_V2',
      });

      await expect(service.create(createDto, operatorId)).rejects.toThrow(
        ConflictException,
      );
    });
  });

  describe('findAll', () => {
    const mockCountQueryBuilder = {
      select: jest.fn().mockReturnThis(),
      addSelect: jest.fn().mockReturnThis(),
      where: jest.fn().mockReturnThis(),
      groupBy: jest.fn().mockReturnThis(),
      getRawMany: jest.fn(),
    };

    it('应该返回模型列表并包含设备数量', async () => {
      const mockModels = [
        {
          id: 'model-1',
          name: '模型1',
          code: 'MODEL_1',
          status: AiModelStatus.PUBLISHED,
        },
        {
          id: 'model-2',
          name: '模型2',
          code: 'MODEL_2',
          status: AiModelStatus.DRAFT,
        },
      ];

      mockAiModelRepository.createQueryBuilder.mockReturnValue(mockQueryBuilder);
      mockQueryBuilder.getManyAndCount.mockResolvedValue([mockModels, 2]);
      mockBindingRepository.createQueryBuilder.mockReturnValue(mockCountQueryBuilder);
      mockCountQueryBuilder.getRawMany.mockResolvedValue([
        { modelId: 'model-1', count: '5' },
        { modelId: 'model-2', count: '3' },
      ]);

      const result = await service.findAll({ page: 1, pageSize: 20 });

      expect(result.items).toHaveLength(2);
      expect(result.total).toBe(2);
      expect(result.items[0].deviceCount).toBe(5);
      expect(result.items[1].deviceCount).toBe(3);
    });

    it('应该支持按类型筛选', async () => {
      mockAiModelRepository.createQueryBuilder.mockReturnValue(mockQueryBuilder);
      mockQueryBuilder.getManyAndCount.mockResolvedValue([[], 0]);
      mockBindingRepository.createQueryBuilder.mockReturnValue(mockCountQueryBuilder);
      mockCountQueryBuilder.getRawMany.mockResolvedValue([]);

      await service.findAll({ type: AiModelType.MIXED_CONNECTION });

      expect(mockQueryBuilder.andWhere).toHaveBeenCalledWith(
        'model.type = :type',
        { type: AiModelType.MIXED_CONNECTION },
      );
    });

    it('应该支持按状态筛选', async () => {
      mockAiModelRepository.createQueryBuilder.mockReturnValue(mockQueryBuilder);
      mockQueryBuilder.getManyAndCount.mockResolvedValue([[], 0]);
      mockBindingRepository.createQueryBuilder.mockReturnValue(mockCountQueryBuilder);
      mockCountQueryBuilder.getRawMany.mockResolvedValue([]);

      await service.findAll({ status: AiModelStatus.PUBLISHED });

      expect(mockQueryBuilder.andWhere).toHaveBeenCalledWith(
        'model.status = :status',
        { status: AiModelStatus.PUBLISHED },
      );
    });

    it('应该支持搜索', async () => {
      mockAiModelRepository.createQueryBuilder.mockReturnValue(mockQueryBuilder);
      mockQueryBuilder.getManyAndCount.mockResolvedValue([[], 0]);
      mockBindingRepository.createQueryBuilder.mockReturnValue(mockCountQueryBuilder);
      mockCountQueryBuilder.getRawMany.mockResolvedValue([]);

      await service.findAll({ search: '错混接' });

      expect(mockQueryBuilder.andWhere).toHaveBeenCalledWith(
        '(model.name ILIKE :search OR model.code ILIKE :search)',
        { search: '%错混接%' },
      );
    });
  });

  describe('findOne', () => {
    it('应该返回模型详情', async () => {
      const mockModel = {
        id: 'model-uuid',
        name: '错混接检测模型',
        code: 'MIXED_CONNECTION_V2',
        status: AiModelStatus.PUBLISHED,
        bindings: [],
      };

      mockAiModelRepository.findOne.mockResolvedValue(mockModel);

      const result = await service.findOne('model-uuid');

      expect(result).toEqual(mockModel);
      expect(mockAiModelRepository.findOne).toHaveBeenCalledWith({
        where: { id: 'model-uuid' },
        relations: ['bindings', 'bindings.device'],
      });
    });

    it('当模型不存在时应该抛出 NotFoundException', async () => {
      mockAiModelRepository.findOne.mockResolvedValue(null);

      await expect(service.findOne('non-existent-uuid')).rejects.toThrow(
        NotFoundException,
      );
    });
  });

  describe('findOneDetail', () => {
    const mockBindings = [
      {
        id: 'binding-1',
        modelId: 'model-uuid',
        deviceId: 'device-1',
        status: BindingStatus.RUNNING,
        device: { id: 'device-1', name: '设备1' },
      },
      {
        id: 'binding-2',
        modelId: 'model-uuid',
        deviceId: 'device-2',
        status: BindingStatus.PENDING,
        device: { id: 'device-2', name: '设备2' },
      },
    ];

    it('应该返回模型详情和分页绑定列表', async () => {
      const mockModel = {
        id: 'model-uuid',
        name: '错混接检测模型',
        code: 'MIXED_CONNECTION_V2',
        status: AiModelStatus.PUBLISHED,
        specs: { size: '20MB', latency: '≤100ms' },
        applicableDeviceTypes: ['WATER_LEVEL_SENSOR', 'FLOW_METER'],
      };

      mockAiModelRepository.findOne.mockResolvedValue(mockModel);
      mockBindingRepository.findAndCount.mockResolvedValue([mockBindings, 2]);

      const result = await service.findOneDetail('model-uuid', { page: 1, pageSize: 20 });

      expect(result.model).toEqual(mockModel);
      expect(result.specs).toEqual({ size: '20MB', latency: '≤100ms' });
      expect(result.applicableDeviceTypes).toEqual(['WATER_LEVEL_SENSOR', 'FLOW_METER']);
      expect(result.bindings.items).toHaveLength(2);
      expect(result.bindings.total).toBe(2);
      expect(result.bindings.page).toBe(1);
      expect(result.bindings.pageSize).toBe(20);
    });

    it('当模型不存在时应该抛出 NotFoundException', async () => {
      mockAiModelRepository.findOne.mockResolvedValue(null);

      await expect(service.findOneDetail('non-existent-uuid')).rejects.toThrow(
        NotFoundException,
      );
    });

    it('应该使用默认分页参数', async () => {
      const mockModel = {
        id: 'model-uuid',
        name: '错混接检测模型',
        code: 'MIXED_CONNECTION_V2',
        specs: {},
        applicableDeviceTypes: [],
      };

      mockAiModelRepository.findOne.mockResolvedValue(mockModel);
      mockBindingRepository.findAndCount = jest.fn().mockResolvedValue([[], 0]);

      await service.findOneDetail('model-uuid');

      expect(mockBindingRepository.findAndCount).toHaveBeenCalledWith(
        expect.objectContaining({
          skip: 0,
          take: 20,
        }),
      );
    });
  });

  describe('update', () => {
    it('应该成功更新模型', async () => {
      const mockModel = {
        id: 'model-uuid',
        name: '错混接检测模型',
        code: 'MIXED_CONNECTION_V2',
        version: 'v2.0.0',
      };
      const updateDto = { version: 'v2.1.0' };

      mockAiModelRepository.findOne.mockResolvedValue(mockModel);
      mockAiModelRepository.save.mockResolvedValue({
        ...mockModel,
        version: 'v2.1.0',
      });

      const result = await service.update('model-uuid', updateDto, 'user-uuid');

      expect(result.version).toBe('v2.1.0');
      expect(mockAuditLogService.createLog).toHaveBeenCalled();
    });

    it('更新编码时应该检查唯一性', async () => {
      const mockModel = {
        id: 'model-uuid',
        name: '错混接检测模型',
        code: 'MIXED_CONNECTION_V2',
      };
      const updateDto = { code: 'NEW_CODE' };

      mockAiModelRepository.findOne
        .mockResolvedValueOnce(mockModel) // findOne call
        .mockResolvedValueOnce({ id: 'other-uuid', code: 'NEW_CODE' }); // check unique

      await expect(
        service.update('model-uuid', updateDto, 'user-uuid'),
      ).rejects.toThrow(ConflictException);
    });
  });

  describe('publish', () => {
    it('应该成功发布模型', async () => {
      const mockModel = {
        id: 'model-uuid',
        name: '错混接检测模型',
        code: 'MIXED_CONNECTION_V2',
        status: AiModelStatus.DRAFT,
        version: 'v2.1.0',
      };

      mockAiModelRepository.findOne.mockResolvedValue(mockModel);
      mockAiModelRepository.save.mockResolvedValue({
        ...mockModel,
        status: AiModelStatus.PUBLISHED,
      });

      const result = await service.publish('model-uuid', 'user-uuid');

      expect(result.status).toBe(AiModelStatus.PUBLISHED);
      expect(mockAuditLogService.createLog).toHaveBeenCalledWith(
        expect.objectContaining({ action: 'publish' }),
      );
    });

    it('当模型已发布时应该抛出 BadRequestException', async () => {
      const mockModel = {
        id: 'model-uuid',
        name: '错混接检测模型',
        code: 'MIXED_CONNECTION_V2',
        status: AiModelStatus.PUBLISHED,
      };

      mockAiModelRepository.findOne.mockResolvedValue(mockModel);

      await expect(service.publish('model-uuid', 'user-uuid')).rejects.toThrow(
        BadRequestException,
      );
    });
  });

  describe('deprecate', () => {
    it('应该成功下线模型', async () => {
      const mockModel = {
        id: 'model-uuid',
        name: '错混接检测模型',
        code: 'MIXED_CONNECTION_V2',
        status: AiModelStatus.PUBLISHED,
      };

      mockAiModelRepository.findOne.mockResolvedValue(mockModel);
      mockAiModelRepository.save.mockResolvedValue({
        ...mockModel,
        status: AiModelStatus.DEPRECATED,
      });

      const result = await service.deprecate('model-uuid', 'user-uuid');

      expect(result.status).toBe(AiModelStatus.DEPRECATED);
    });

    it('当模型未发布时应该抛出 BadRequestException', async () => {
      const mockModel = {
        id: 'model-uuid',
        name: '错混接检测模型',
        code: 'MIXED_CONNECTION_V2',
        status: AiModelStatus.DRAFT,
      };

      mockAiModelRepository.findOne.mockResolvedValue(mockModel);

      await expect(
        service.deprecate('model-uuid', 'user-uuid'),
      ).rejects.toThrow(BadRequestException);
    });
  });

  describe('remove', () => {
    it('应该成功删除模型', async () => {
      const mockModel = {
        id: 'model-uuid',
        name: '错混接检测模型',
        code: 'MIXED_CONNECTION_V2',
      };

      mockAiModelRepository.findOne.mockResolvedValue(mockModel);
      mockBindingRepository.count.mockResolvedValue(0);
      mockAiModelRepository.remove.mockResolvedValue(mockModel);

      await service.remove('model-uuid', 'user-uuid');

      expect(mockAiModelRepository.remove).toHaveBeenCalledWith(mockModel);
      expect(mockAuditLogService.createLog).toHaveBeenCalledWith(
        expect.objectContaining({ action: 'delete' }),
      );
    });

    it('当模型有设备绑定时应该抛出 BadRequestException', async () => {
      const mockModel = {
        id: 'model-uuid',
        name: '错混接检测模型',
        code: 'MIXED_CONNECTION_V2',
      };

      mockAiModelRepository.findOne.mockResolvedValue(mockModel);
      mockBindingRepository.count.mockResolvedValue(5);

      await expect(service.remove('model-uuid', 'user-uuid')).rejects.toThrow(
        BadRequestException,
      );
    });
  });

  describe('getDeviceCount', () => {
    it('应该返回模型绑定的设备数量', async () => {
      mockBindingRepository.count.mockResolvedValue(10);

      const result = await service.getDeviceCount('model-uuid');

      expect(result).toBe(10);
    });
  });

  describe('getDeviceCounts', () => {
    it('应该返回多个模型的设备绑定数量', async () => {
      const mockCounts = [
        { modelId: 'model-1', count: '5' },
        { modelId: 'model-2', count: '10' },
      ];

      const mockCountQueryBuilder = {
        select: jest.fn().mockReturnThis(),
        addSelect: jest.fn().mockReturnThis(),
        where: jest.fn().mockReturnThis(),
        groupBy: jest.fn().mockReturnThis(),
        getRawMany: jest.fn().mockResolvedValue(mockCounts),
      };

      mockBindingRepository.createQueryBuilder.mockReturnValue(
        mockCountQueryBuilder,
      );

      const result = await service.getDeviceCounts(['model-1', 'model-2', 'model-3']);

      expect(result['model-1']).toBe(5);
      expect(result['model-2']).toBe(10);
      expect(result['model-3']).toBe(0); // 未找到时默认为0
    });
  });
});
