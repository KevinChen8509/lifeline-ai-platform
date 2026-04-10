import { Test, TestingModule } from '@nestjs/testing';
import { ProjectController } from './project.controller';
import { ProjectService } from './project.service';
import { ProjectStatus } from './project.entity';

describe('ProjectController', () => {
  let controller: ProjectController;
  let service: ProjectService;

  const mockProject = {
    id: 'project-uuid',
    name: '测试项目',
    code: 'TEST001',
    description: '这是一个测试项目',
    status: ProjectStatus.ACTIVE,
    settings: {},
    createdAt: new Date(),
    updatedAt: new Date(),
  };

  const mockProjectService = {
    create: jest.fn().mockResolvedValue(mockProject),
    findAll: jest.fn().mockResolvedValue({
      items: [mockProject],
      total: 1,
      page: 1,
      pageSize: 20,
    }),
    findOne: jest.fn().mockResolvedValue(mockProject),
    update: jest.fn().mockResolvedValue(mockProject),
    remove: jest.fn().mockResolvedValue(undefined),
  };

  const mockGuard = { canActivate: jest.fn().mockReturnValue(true) };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      controllers: [ProjectController],
      providers: [
        {
          provide: ProjectService,
          useValue: mockProjectService,
        },
      ],
    })
      .overrideGuard(require('@nestjs/passport').AuthGuard('jwt'))
      .useValue(mockGuard)
      .overrideGuard(require('../../common/guards/permissions.guard').PermissionsGuard)
      .useValue(mockGuard)
      .compile();

    controller = module.get<ProjectController>(ProjectController);
    service = module.get<ProjectService>(ProjectService);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('should be defined', () => {
    expect(controller).toBeDefined();
  });

  describe('create', () => {
    it('应该成功创建项目', async () => {
      const createDto = {
        name: '测试项目',
        code: 'TEST001',
        description: '这是一个测试项目',
      };
      const req = { user: { sub: 'user-uuid' }, headers: {} };

      const result = await controller.create(createDto, req as any);

      expect(service.create).toHaveBeenCalledWith(
        createDto,
        'user-uuid',
        expect.objectContaining({ ipAddress: 'unknown', userAgent: undefined }),
      );
      expect(result).toEqual(mockProject);
    });
  });

  describe('findAll', () => {
    it('应该返回项目列表', async () => {
      const result = await controller.findAll(1, 20);

      expect(service.findAll).toHaveBeenCalledWith({
        page: 1,
        pageSize: 20,
        search: undefined,
        status: undefined,
      });
      expect(result.items).toHaveLength(1);
      expect(result.total).toBe(1);
    });
  });

  describe('findOne', () => {
    it('应该返回项目详情', async () => {
      const result = await controller.findOne('project-uuid');

      expect(service.findOne).toHaveBeenCalledWith('project-uuid');
      expect(result).toEqual(mockProject);
    });
  });

  describe('update', () => {
    it('应该成功更新项目', async () => {
      const updateDto = { name: '更新后的项目名' };
      const req = { user: { sub: 'user-uuid' }, headers: {} };

      const result = await controller.update('project-uuid', updateDto, req as any);

      expect(service.update).toHaveBeenCalledWith(
        'project-uuid',
        updateDto,
        'user-uuid',
        expect.objectContaining({ ipAddress: 'unknown', userAgent: undefined }),
      );
      expect(result).toEqual(mockProject);
    });
  });

  describe('remove', () => {
    it('应该成功归档项目', async () => {
      const req = { user: { sub: 'user-uuid' }, headers: {} };

      await controller.remove('project-uuid', req as any);

      expect(service.remove).toHaveBeenCalledWith(
        'project-uuid',
        'user-uuid',
        expect.objectContaining({ ipAddress: 'unknown', userAgent: undefined }),
      );
    });
  });
});
