import { Test, TestingModule } from '@nestjs/testing';
import { ExecutionContext, ForbiddenException } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { PermissionsGuard } from './permissions.guard';
import { AbilityFactory } from '../../modules/auth/ability/ability.factory';
import { PERMISSIONS_KEY } from '../decorators/permissions.decorator';
import { User, UserStatus } from '../../modules/user/user.entity';
import { Role, RoleCode } from '../../modules/role/role.entity';

describe('PermissionsGuard', () => {
  let guard: PermissionsGuard;
  let reflector: Reflector;
  let abilityFactory: AbilityFactory;

  const mockUser: User = {
    id: 'user-uuid',
    username: 'testuser',
    name: 'Test User',
    email: 'test@example.com',
    passwordHash: 'hash',
    status: UserStatus.ACTIVE,
    roleId: 'role-uuid',
    role: {
      id: 'role-uuid',
      name: '管理员',
      code: RoleCode.ADMIN,
      description: '系统管理员',
      permissions: [],
      createdAt: new Date(),
      updatedAt: new Date(),
    } as Role,
    createdAt: new Date(),
    updatedAt: new Date(),
  } as User;

  const mockOperatorUser: User = {
    ...mockUser,
    id: 'operator-uuid',
    username: 'operator',
    role: {
      id: 'operator-role-uuid',
      name: '运维员',
      code: RoleCode.OPERATOR,
      description: '运维操作员',
      permissions: [],
      createdAt: new Date(),
      updatedAt: new Date(),
    } as Role,
  } as User;

  const mockObserverUser: User = {
    ...mockUser,
    id: 'observer-uuid',
    username: 'observer',
    role: {
      id: 'observer-role-uuid',
      name: '观察员',
      code: RoleCode.OBSERVER,
      description: '只读观察员',
      permissions: [],
      createdAt: new Date(),
      updatedAt: new Date(),
    } as Role,
  } as User;

  const createMockExecutionContext = (user?: User): ExecutionContext => {
    return {
      switchToHttp: () => ({
        getRequest: () => ({ user }),
      }),
      getHandler: () => ({}),
      getClass: () => ({}),
    } as ExecutionContext;
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        PermissionsGuard,
        {
          provide: Reflector,
          useValue: {
            getAllAndOverride: jest.fn(),
          },
        },
        {
          provide: AbilityFactory,
          useValue: {
            createForUser: jest.fn(),
          },
        },
      ],
    }).compile();

    guard = module.get<PermissionsGuard>(PermissionsGuard);
    reflector = module.get<Reflector>(Reflector);
    abilityFactory = module.get<AbilityFactory>(AbilityFactory);
  });

  it('should be defined', () => {
    expect(guard).toBeDefined();
  });

  describe('canActivate', () => {
    it('应该允许访问没有权限要求的路由', () => {
      // 没有权限要求
      jest.spyOn(reflector, 'getAllAndOverride').mockReturnValue(undefined);

      const context = createMockExecutionContext(mockUser);
      const result = guard.canActivate(context);

      expect(result).toBe(true);
    });

    it('未登录用户访问需要权限的路由应该抛出 ForbiddenException', () => {
      jest.spyOn(reflector, 'getAllAndOverride').mockReturnValue({
        action: 'manage',
        subject: 'User',
      });

      const context = createMockExecutionContext(undefined);

      expect(() => guard.canActivate(context)).toThrow(ForbiddenException);
      expect(() => guard.canActivate(context)).toThrow('用户未登录');
    });

    it('管理员用户应该有所有权限', () => {
      jest.spyOn(reflector, 'getAllAndOverride').mockReturnValue({
        action: 'manage',
        subject: 'User',
      });

      // 模拟管理员的 ability
      jest.spyOn(abilityFactory, 'createForUser').mockReturnValue({
        can: jest.fn().mockReturnValue(true),
        cannot: jest.fn().mockReturnValue(false),
      } as any);

      const context = createMockExecutionContext(mockUser);
      const result = guard.canActivate(context);

      expect(result).toBe(true);
    });

    it('运维员访问 User 管理应该被拒绝', () => {
      jest.spyOn(reflector, 'getAllAndOverride').mockReturnValue({
        action: 'manage',
        subject: 'User',
      });

      // 模拟运维员的 ability - 不能管理用户
      jest.spyOn(abilityFactory, 'createForUser').mockReturnValue({
        can: jest.fn().mockReturnValue(false),
        cannot: jest.fn().mockReturnValue(true),
      } as any);

      const context = createMockExecutionContext(mockOperatorUser);

      expect(() => guard.canActivate(context)).toThrow(ForbiddenException);
      expect(() => guard.canActivate(context)).toThrow('没有权限执行此操作: manage User');
    });

    it('运维员访问 Device 管理应该被允许', () => {
      jest.spyOn(reflector, 'getAllAndOverride').mockReturnValue({
        action: 'manage',
        subject: 'Device',
      });

      // 模拟运维员的 ability - 可以管理设备
      jest.spyOn(abilityFactory, 'createForUser').mockReturnValue({
        can: jest.fn().mockReturnValue(true),
        cannot: jest.fn().mockReturnValue(false),
      } as any);

      const context = createMockExecutionContext(mockOperatorUser);
      const result = guard.canActivate(context);

      expect(result).toBe(true);
    });

    it('观察员访问只读权限应该被允许', () => {
      jest.spyOn(reflector, 'getAllAndOverride').mockReturnValue({
        action: 'read',
        subject: 'Device',
      });

      // 模拟观察员的 ability - 可以读取设备
      jest.spyOn(abilityFactory, 'createForUser').mockReturnValue({
        can: jest.fn().mockReturnValue(true),
        cannot: jest.fn().mockReturnValue(false),
      } as any);

      const context = createMockExecutionContext(mockObserverUser);
      const result = guard.canActivate(context);

      expect(result).toBe(true);
    });

    it('观察员访问写权限应该被拒绝', () => {
      jest.spyOn(reflector, 'getAllAndOverride').mockReturnValue({
        action: 'manage',
        subject: 'Device',
      });

      // 模拟观察员的 ability - 不能管理设备
      jest.spyOn(abilityFactory, 'createForUser').mockReturnValue({
        can: jest.fn().mockReturnValue(false),
        cannot: jest.fn().mockReturnValue(true),
      } as any);

      const context = createMockExecutionContext(mockObserverUser);

      expect(() => guard.canActivate(context)).toThrow(ForbiddenException);
    });

    it('无角色用户应该只有观察员权限', () => {
      const userWithoutRole: User = {
        ...mockUser,
        roleId: null,
        role: null,
      } as User;

      jest.spyOn(reflector, 'getAllAndOverride').mockReturnValue({
        action: 'read',
        subject: 'Device',
      });

      // 模拟无角色用户的 ability - 默认观察员权限
      jest.spyOn(abilityFactory, 'createForUser').mockReturnValue({
        can: jest.fn().mockReturnValue(true),
        cannot: jest.fn().mockReturnValue(false),
      } as any);

      const context = createMockExecutionContext(userWithoutRole);
      const result = guard.canActivate(context);

      expect(result).toBe(true);
    });
  });
});
