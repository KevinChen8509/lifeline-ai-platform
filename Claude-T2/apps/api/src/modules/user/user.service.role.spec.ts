import { Test, TestingModule } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { BadRequestException, NotFoundException } from '@nestjs/common';
import { UserService } from './user.service';
import { User, UserStatus } from './user.entity';
import { Role, RoleCode } from '../role/role.entity';
import { RoleService } from '../role/role.service';
import { EmailService } from '../email/email.service';
import { AuditLogService } from '../audit/audit-log.service';
import { RedisService } from '../redis/redis.service';
import { UpdateUserRoleDto } from './dto/update-user-role.dto';

// Mock bcrypt
jest.mock('bcrypt', () => ({
  hash: jest.fn().mockResolvedValue('hashed-password'),
  compare: jest.fn().mockResolvedValue(true),
}));

describe('UserService - updateRole', () => {
  let service: UserService;
  let userRepository: Repository<User>;
  let roleService: RoleService;

  const mockAdminRole: Role = {
    id: 'admin-role-uuid',
    name: '管理员',
    code: RoleCode.ADMIN,
    description: '系统管理员',
    permissions: [],
    createdAt: new Date(),
    updatedAt: new Date(),
  } as Role;

  const mockOperatorRole: Role = {
    id: 'operator-role-uuid',
    name: '运维员',
    code: RoleCode.OPERATOR,
    description: '运维操作员',
    permissions: [],
    createdAt: new Date(),
    updatedAt: new Date(),
  } as Role;

  const mockObserverRole: Role = {
    id: 'observer-role-uuid',
    name: '观察员',
    code: RoleCode.OBSERVER,
    description: '只读观察员',
    permissions: [],
    createdAt: new Date(),
    updatedAt: new Date(),
  } as Role;

  const mockUser: User = {
    id: 'user-uuid',
    username: 'testuser',
    name: 'Test User',
    email: 'test@example.com',
    passwordHash: 'hash',
    status: UserStatus.ACTIVE,
    roleId: 'observer-role-uuid',
    role: mockObserverRole,
    createdAt: new Date(),
    updatedAt: new Date(),
  } as User;

  const mockEmailService = {
    sendActivationEmail: jest.fn(),
  };

  const mockAuditLogService = {
    createLog: jest.fn().mockResolvedValue({}),
  };

  const mockRedisService = {
    delByPattern: jest.fn().mockResolvedValue(0),
    get: jest.fn(),
    set: jest.fn(),
    del: jest.fn(),
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        UserService,
        {
          provide: getRepositoryToken(User),
          useValue: {
            findOne: jest.fn(),
            save: jest.fn(),
            create: jest.fn(),
            findAndCount: jest.fn(),
            softRemove: jest.fn(),
          },
        },
        {
          provide: EmailService,
          useValue: mockEmailService,
        },
        {
          provide: RoleService,
          useValue: {
            findOne: jest.fn(),
          },
        },
        {
          provide: AuditLogService,
          useValue: mockAuditLogService,
        },
        {
          provide: RedisService,
          useValue: mockRedisService,
        },
      ],
    }).compile();

    service = module.get<UserService>(UserService);
    userRepository = module.get<Repository<User>>(getRepositoryToken(User));
    roleService = module.get<RoleService>(RoleService);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  describe('updateRole', () => {
    it('应该成功更新用户角色', async () => {
      const dto: UpdateUserRoleDto = { roleId: 'operator-role-uuid' };
      const operatorId = 'admin-uuid';

      jest.spyOn(userRepository, 'findOne').mockResolvedValue(mockUser);
      jest.spyOn(roleService, 'findOne').mockResolvedValue(mockOperatorRole);
      jest.spyOn(userRepository, 'save').mockResolvedValue({
        ...mockUser,
        roleId: 'operator-role-uuid',
        role: mockOperatorRole,
      });

      const result = await service.updateRole(mockUser.id, dto, operatorId);

      expect(result.roleId).toBe('operator-role-uuid');
      expect(result.role?.code).toBe(RoleCode.OPERATOR);
    });

    it('应该拒绝用户修改自己的角色', async () => {
      const dto: UpdateUserRoleDto = { roleId: 'admin-role-uuid' };

      await expect(
        service.updateRole(mockUser.id, dto, mockUser.id),
      ).rejects.toThrow(BadRequestException);

      await expect(
        service.updateRole(mockUser.id, dto, mockUser.id),
      ).rejects.toThrow('不能修改自己的角色');
    });

    it('角色不存在时应该抛出 NotFoundException', async () => {
      const dto: UpdateUserRoleDto = { roleId: 'non-existent-uuid' };
      const operatorId = 'admin-uuid';

      jest.spyOn(userRepository, 'findOne').mockResolvedValue(mockUser);
      jest.spyOn(roleService, 'findOne').mockRejectedValue(new NotFoundException('角色不存在'));

      await expect(
        service.updateRole(mockUser.id, dto, operatorId),
      ).rejects.toThrow(NotFoundException);
    });

    it('用户不存在时应该抛出 NotFoundException', async () => {
      const dto: UpdateUserRoleDto = { roleId: 'operator-role-uuid' };
      const operatorId = 'admin-uuid';

      // Role exists
      jest.spyOn(roleService, 'findOne').mockResolvedValue(mockOperatorRole);
      // User not found
      jest.spyOn(userRepository, 'findOne').mockResolvedValue(null);

      await expect(
        service.updateRole('non-existent-user', dto, operatorId),
      ).rejects.toThrow(NotFoundException);
    });

    it('从观察员升级到运维员应该成功', async () => {
      const dto: UpdateUserRoleDto = { roleId: 'operator-role-uuid' };
      const operatorId = 'admin-uuid';

      jest.spyOn(userRepository, 'findOne').mockResolvedValue(mockUser);
      jest.spyOn(roleService, 'findOne').mockResolvedValue(mockOperatorRole);
      jest.spyOn(userRepository, 'save').mockResolvedValue({
        ...mockUser,
        roleId: 'operator-role-uuid',
        role: mockOperatorRole,
      });

      const result = await service.updateRole(mockUser.id, dto, operatorId);

      expect(result.role?.code).toBe(RoleCode.OPERATOR);
    });

    it('从管理员降级到观察员应该成功', async () => {
      const adminUser = { ...mockUser, role: mockAdminRole, roleId: mockAdminRole.id };
      const dto: UpdateUserRoleDto = { roleId: 'observer-role-uuid' };
      const operatorId = 'super-admin-uuid';

      jest.spyOn(userRepository, 'findOne').mockResolvedValue(adminUser);
      jest.spyOn(roleService, 'findOne').mockResolvedValue(mockObserverRole);
      jest.spyOn(userRepository, 'save').mockResolvedValue({
        ...adminUser,
        roleId: 'observer-role-uuid',
        role: mockObserverRole,
      });

      const result = await service.updateRole(adminUser.id, dto, operatorId);

      expect(result.role?.code).toBe(RoleCode.OBSERVER);
    });

    it('更新角色时应该创建审计日志', async () => {
      const dto: UpdateUserRoleDto = { roleId: 'operator-role-uuid' };
      const operatorId = 'admin-uuid';

      jest.spyOn(userRepository, 'findOne').mockResolvedValue(mockUser);
      jest.spyOn(roleService, 'findOne').mockResolvedValue(mockOperatorRole);
      jest.spyOn(userRepository, 'save').mockResolvedValue({
        ...mockUser,
        roleId: 'operator-role-uuid',
        role: mockOperatorRole,
      });

      await service.updateRole(mockUser.id, dto, operatorId);

      expect(mockAuditLogService.createLog).toHaveBeenCalledWith(
        expect.objectContaining({
          action: 'ASSIGN_ROLE',
          targetType: 'User',
          targetId: mockUser.id,
          operatorId,
        }),
      );
    });
  });
});
