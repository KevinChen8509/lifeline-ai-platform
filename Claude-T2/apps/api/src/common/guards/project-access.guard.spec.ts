import { Test, TestingModule } from '@nestjs/testing';
import { Reflector } from '@nestjs/core';
import { ForbiddenException } from '@nestjs/common';
import { ProjectAccessGuard, RequireProjectAccess } from './project-access.guard';
import { ProjectService } from '../../modules/project/project.service';
import { AbilityFactory } from '../../modules/auth/ability/ability.factory';
import { User, UserStatus } from '../../modules/user/user.entity';
import { Role, RoleCode } from '../../modules/role/role.entity';

describe('ProjectAccessGuard', () => {
  let guard: ProjectAccessGuard;
  let projectService: ProjectService;
  let abilityFactory: AbilityFactory;
  let reflector: Reflector;

  const mockAdminUser: User = {
    id: 'admin-uuid',
    username: 'admin',
    name: 'Admin User',
    email: 'admin@example.com',
    status: UserStatus.ACTIVE,
    roleId: 'admin-role-uuid',
    role: {
      id: 'admin-role-uuid',
      name: '管理员',
      code: RoleCode.ADMIN,
      description: '系统管理员角色',
      permissions: [],
      createdAt: new Date(),
      updatedAt: new Date(),
    } as Role,
  } as User;

  const mockOperatorUser: User = {
    id: 'operator-uuid',
    username: 'operator',
    name: 'Operator User',
    email: 'operator@example.com',
    status: UserStatus.ACTIVE,
    roleId: 'operator-role-uuid',
    role: {
      id: 'operator-role-uuid',
      name: '运维员',
      code: RoleCode.OPERATOR,
      description: '运维人员角色',
      permissions: [],
      createdAt: new Date(),
      updatedAt: new Date(),
    } as Role,
  } as User;

  const mockProjectService = {
    isProjectMember: jest.fn(),
  };

  const mockAbilityFactory = {
    createForUser: jest.fn((user: User) => ({
      can: jest.fn((action: string, subject: string) => {
        // Admin can manage all
        if (user.role?.code === RoleCode.ADMIN) {
          return action === 'manage' && subject === 'all';
        }
        return false;
      }),
    })),
  };

  const createMockExecutionContext = (user: User | undefined, params: any = {}) => {
    return {
      switchToHttp: () => ({
        getRequest: () => ({
          user,
          params,
          query: {},
        }),
      }),
      getHandler: jest.fn(),
      getClass: jest.fn(),
    } as any;
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        ProjectAccessGuard,
        {
          provide: Reflector,
          useValue: {
            getAllAndOverride: jest.fn(),
          },
        },
        {
          provide: ProjectService,
          useValue: mockProjectService,
        },
        {
          provide: AbilityFactory,
          useValue: mockAbilityFactory,
        },
      ],
    }).compile();

    guard = module.get<ProjectAccessGuard>(ProjectAccessGuard);
    projectService = module.get(ProjectService);
    abilityFactory = module.get(AbilityFactory);
    reflector = module.get(Reflector);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  describe('canActivate', () => {
    it('应该允许访问没有配置项目访问检查的路由', async () => {
      const context = createMockExecutionContext(mockOperatorUser);

      jest.spyOn(reflector, 'getAllAndOverride').mockReturnValue(undefined);

      const result = await guard.canActivate(context);

      expect(result).toBe(true);
    });

    it('管理员应该可以访问所有项目', async () => {
      const context = createMockExecutionContext(mockAdminUser, {
        projectId: 'project-uuid',
      });

      jest.spyOn(reflector, 'getAllAndOverride').mockReturnValue({ paramName: 'projectId' });

      const result = await guard.canActivate(context);

      expect(result).toBe(true);
    });

    it('项目成员应该可以访问项目', async () => {
      const context = createMockExecutionContext(mockOperatorUser, {
        projectId: 'project-uuid',
      });

      jest.spyOn(reflector, 'getAllAndOverride').mockReturnValue({ paramName: 'projectId' });
      mockProjectService.isProjectMember.mockResolvedValue(true);

      const result = await guard.canActivate(context);

      expect(result).toBe(true);
      expect(mockProjectService.isProjectMember).toHaveBeenCalledWith(
        'project-uuid',
        'operator-uuid',
      );
    });

    it('非项目成员应该被拒绝访问', async () => {
      const context = createMockExecutionContext(mockOperatorUser, {
        projectId: 'other-project-uuid',
      });

      jest.spyOn(reflector, 'getAllAndOverride').mockReturnValue({ paramName: 'projectId' });
      mockProjectService.isProjectMember.mockResolvedValue(false);

      await expect(guard.canActivate(context)).rejects.toThrow(ForbiddenException);
    });

    it('未登录用户应该被拒绝访问', async () => {
      const context = createMockExecutionContext(undefined, {
        projectId: 'project-uuid',
      });

      jest.spyOn(reflector, 'getAllAndOverride').mockReturnValue({ paramName: 'projectId' });

      await expect(guard.canActivate(context)).rejects.toThrow(ForbiddenException);
    });

    it('没有项目ID时应该允许访问（由服务层处理）', async () => {
      const context = createMockExecutionContext(mockOperatorUser, {});

      jest.spyOn(reflector, 'getAllAndOverride').mockReturnValue({ paramName: 'projectId' });

      const result = await guard.canActivate(context);

      expect(result).toBe(true);
    });
  });

  describe('RequireProjectAccess decorator', () => {
    it('应该正确设置元数据', () => {
      @RequireProjectAccess('customProjectId')
      class TestClass {}

      const metadata = Reflect.getMetadata('projectAccess', TestClass);
      expect(metadata).toEqual({ paramName: 'customProjectId' });
    });

    it('应该使用默认参数名', () => {
      @RequireProjectAccess()
      class TestClass {}

      const metadata = Reflect.getMetadata('projectAccess', TestClass);
      expect(metadata).toEqual({ paramName: 'projectId' });
    });
  });
});
