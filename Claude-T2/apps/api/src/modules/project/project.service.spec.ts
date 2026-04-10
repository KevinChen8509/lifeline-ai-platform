import { Test, TestingModule } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { DataSource, Repository } from 'typeorm';
import { ConflictException, NotFoundException } from '@nestjs/common';
import { ProjectService } from './project.service';
import { Project, ProjectStatus } from './project.entity';
import { ProjectUser, ProjectRole } from './project-user.entity';
import { AuditLogService } from '../audit/audit-log.service';

describe('ProjectService', () => {
  let service: ProjectService;
  let projectRepository: Repository<Project>;
  let projectUserRepository: Repository<ProjectUser>;
  let auditLogService: AuditLogService;
  let dataSource: DataSource;

  const mockProjectRepository = {
    findOne: jest.fn(),
    create: jest.fn(),
    save: jest.fn(),
    createQueryBuilder: jest.fn(),
  };

  const mockProjectUserRepository = {
    findOne: jest.fn(),
    create: jest.fn(),
    save: jest.fn(),
  };

  const mockAuditLogService = {
    createLog: jest.fn().mockResolvedValue(undefined),
  };

  const mockDataSource = {
    transaction: jest.fn((cb) => {
      const mockManager = {
        create: jest.fn((entityClass, data) => data),
        save: jest.fn((entity) => Promise.resolve({ ...entity, id: 'test-uuid' })),
      };
      return cb(mockManager);
    }),
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        ProjectService,
        {
          provide: getRepositoryToken(Project),
          useValue: mockProjectRepository,
        },
        {
          provide: getRepositoryToken(ProjectUser),
          useValue: mockProjectUserRepository,
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

    service = module.get<ProjectService>(ProjectService);
    projectRepository = module.get(getRepositoryToken(Project));
    projectUserRepository = module.get(getRepositoryToken(ProjectUser));
    auditLogService = module.get(AuditLogService);
    dataSource = module.get(DataSource);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  describe('create', () => {
    it('应该成功创建项目', async () => {
      const createDto = {
        name: '测试项目',
        code: 'TEST001',
        description: '这是一个测试项目',
      };
      const creatorId = 'user-uuid';

      mockProjectRepository.findOne.mockResolvedValue(null);

      const result = await service.create(createDto, creatorId);

      expect(mockProjectRepository.findOne).toHaveBeenCalledWith({
        where: { code: 'TEST001' },
      });
      expect(mockAuditLogService.createLog).toHaveBeenCalled();
    });

    it('当项目编码已存在时应该抛出冲突异常', async () => {
      const createDto = {
        name: '测试项目',
        code: 'TEST001',
        description: '这是一个测试项目',
      };
      const creatorId = 'user-uuid';

      mockProjectRepository.findOne.mockResolvedValue({
        id: 'existing-uuid',
        code: 'TEST001',
      });

      await expect(service.create(createDto, creatorId)).rejects.toThrow(
        ConflictException,
      );
    });
  });

  describe('findOne', () => {
    it('应该返回项目详情', async () => {
      const mockProject = {
        id: 'project-uuid',
        name: '测试项目',
        code: 'TEST001',
        status: ProjectStatus.ACTIVE,
      };

      mockProjectRepository.findOne.mockResolvedValue(mockProject);

      const result = await service.findOne('project-uuid');

      expect(result).toEqual(mockProject);
    });

    it('当项目不存在时应该抛出 NotFoundException', async () => {
      mockProjectRepository.findOne.mockResolvedValue(null);

      await expect(service.findOne('non-existent-uuid')).rejects.toThrow(
        NotFoundException,
      );
    });
  });

  describe('isProjectMember', () => {
    it('当用户是项目成员时应该返回 true', async () => {
      mockProjectUserRepository.findOne.mockResolvedValue({
        projectId: 'project-uuid',
        userId: 'user-uuid',
        role: ProjectRole.MEMBER,
      });

      const result = await service.isProjectMember('project-uuid', 'user-uuid');

      expect(result).toBe(true);
    });

    it('当用户不是项目成员时应该返回 false', async () => {
      mockProjectUserRepository.findOne.mockResolvedValue(null);

      const result = await service.isProjectMember('project-uuid', 'user-uuid');

      expect(result).toBe(false);
    });
  });

  describe('isProjectAdmin', () => {
    it('当用户是项目管理员时应该返回 true', async () => {
      mockProjectUserRepository.findOne.mockResolvedValue({
        projectId: 'project-uuid',
        userId: 'user-uuid',
        role: ProjectRole.ADMIN,
      });

      const result = await service.isProjectAdmin('project-uuid', 'user-uuid');

      expect(result).toBe(true);
    });

    it('当用户不是项目管理员时应该返回 false', async () => {
      // 当查找 ADMIN 角色时返回 null（用户只是 MEMBER）
      mockProjectUserRepository.findOne.mockResolvedValue(null);

      const result = await service.isProjectAdmin('project-uuid', 'user-uuid');

      expect(result).toBe(false);
    });
  });
});
