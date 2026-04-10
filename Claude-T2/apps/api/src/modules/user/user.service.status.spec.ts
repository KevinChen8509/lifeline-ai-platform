import { Test, TestingModule } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { NotFoundException } from '@nestjs/common';
import { UserService } from './user.service';
import { User, UserStatus } from './user.entity';
import { Role, RoleCode } from '../role/role.entity';
import { RoleService } from '../role/role.service';
import { EmailService } from '../email/email.service';
import { AuditLogService } from '../audit/audit-log.service';
import { RedisService } from '../redis/redis.service';

// Mock bcrypt
jest.mock('bcrypt', () => ({
  hash: jest.fn().mockResolvedValue('hashed-password'),
  compare: jest.fn().mockResolvedValue(true),
}));

describe('UserService - Status Management', () => {
  let service: UserService;
  let userRepository: Repository<User>;
  let redisService: RedisService;

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

  const mockRoleService = {
    findOne: jest.fn(),
  };

  const mockAuditLogService = {
    createLog: jest.fn().mockResolvedValue({}),
  };

  const mockRedisService = {
    delByPattern: jest.fn().mockResolvedValue(3),
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
          useValue: mockRoleService,
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
    redisService = module.get<RedisService>(RedisService);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  describe('updateStatus - 禁用用户时Token撤销', () => {
    it('禁用用户时应该撤销所有Token', async () => {
      const operatorId = 'admin-uuid';
      const freshMockUser = { ...mockUser, status: UserStatus.ACTIVE };

      jest.spyOn(userRepository, 'findOne').mockResolvedValue(freshMockUser);
      jest.spyOn(userRepository, 'save').mockResolvedValue({
        ...freshMockUser,
        status: UserStatus.DISABLED,
      });

      const result = await service.updateStatus(freshMockUser.id, UserStatus.DISABLED, operatorId);

      expect(result.status).toBe(UserStatus.DISABLED);
      expect(mockRedisService.delByPattern).toHaveBeenCalledWith(`refresh:${freshMockUser.id}:*`);
    });

    it('激活用户时不应该撤销Token', async () => {
      const freshMockUser = { ...mockUser, status: UserStatus.PENDING };
      const operatorId = 'admin-uuid';

      jest.spyOn(userRepository, 'findOne').mockResolvedValue(freshMockUser);
      jest.spyOn(userRepository, 'save').mockResolvedValue({
        ...freshMockUser,
        status: UserStatus.ACTIVE,
      });

      await service.updateStatus(freshMockUser.id, UserStatus.ACTIVE, operatorId);

      expect(mockRedisService.delByPattern).not.toHaveBeenCalled();
    });

    it('禁用用户时应该记录审计日志', async () => {
      const operatorId = 'admin-uuid';
      const freshMockUser = { ...mockUser, status: UserStatus.ACTIVE };

      jest.spyOn(userRepository, 'findOne').mockResolvedValue(freshMockUser);
      jest.spyOn(userRepository, 'save').mockResolvedValue({
        ...freshMockUser,
        status: UserStatus.DISABLED,
      });

      await service.updateStatus(freshMockUser.id, UserStatus.DISABLED, operatorId);

      expect(mockAuditLogService.createLog).toHaveBeenCalledWith(
        expect.objectContaining({
          action: 'UPDATE_STATUS',
          targetType: 'User',
          targetId: freshMockUser.id,
          operatorId,
          oldValue: { status: UserStatus.ACTIVE },
          newValue: { status: UserStatus.DISABLED },
        }),
      );
    });

    it('用户不存在时应该抛出 NotFoundException', async () => {
      jest.spyOn(userRepository, 'findOne').mockResolvedValue(null);

      await expect(
        service.updateStatus('non-existent-user', UserStatus.DISABLED),
      ).rejects.toThrow(NotFoundException);
    });
  });

  describe('remove - 删除用户时Token撤销', () => {
    it('删除用户时应该撤销所有Token', async () => {
      const operatorId = 'admin-uuid';
      const freshMockUser = { ...mockUser };

      jest.spyOn(userRepository, 'findOne').mockResolvedValue(freshMockUser);
      jest.spyOn(userRepository, 'softRemove').mockResolvedValue(freshMockUser);

      await service.remove(freshMockUser.id, operatorId);

      expect(mockRedisService.delByPattern).toHaveBeenCalledWith(`refresh:${freshMockUser.id}:*`);
    });

    it('删除用户时应该记录审计日志', async () => {
      const operatorId = 'admin-uuid';
      const freshMockUser = { ...mockUser };

      jest.spyOn(userRepository, 'findOne').mockResolvedValue(freshMockUser);
      jest.spyOn(userRepository, 'softRemove').mockResolvedValue(freshMockUser);

      await service.remove(freshMockUser.id, operatorId);

      expect(mockAuditLogService.createLog).toHaveBeenCalledWith(
        expect.objectContaining({
          action: 'DELETE_USER',
          targetType: 'User',
          targetId: freshMockUser.id,
          operatorId,
          oldValue: {
            username: freshMockUser.username,
            name: freshMockUser.name,
            email: freshMockUser.email,
          },
        }),
      );
    });

    it('删除用户时应该使用软删除', async () => {
      const operatorId = 'admin-uuid';
      const freshMockUser = { ...mockUser };

      jest.spyOn(userRepository, 'findOne').mockResolvedValue(freshMockUser);
      jest.spyOn(userRepository, 'softRemove').mockResolvedValue(freshMockUser);

      await service.remove(freshMockUser.id, operatorId);

      expect(userRepository.softRemove).toHaveBeenCalledWith(freshMockUser);
    });

    it('删除不存在的用户时应该抛出 NotFoundException', async () => {
      jest.spyOn(userRepository, 'findOne').mockResolvedValue(null);

      await expect(service.remove('non-existent-user')).rejects.toThrow(NotFoundException);
    });
  });

  describe('Token撤销错误处理', () => {
    it('Token撤销失败不应影响禁用操作', async () => {
      const operatorId = 'admin-uuid';
      const freshMockUser = { ...mockUser, status: UserStatus.ACTIVE };

      jest.spyOn(userRepository, 'findOne').mockResolvedValue(freshMockUser);
      jest.spyOn(userRepository, 'save').mockResolvedValue({
        ...freshMockUser,
        status: UserStatus.DISABLED,
      });
      mockRedisService.delByPattern.mockRejectedValueOnce(new Error('Redis connection error'));

      // 不应该抛出错误
      const result = await service.updateStatus(freshMockUser.id, UserStatus.DISABLED, operatorId);

      expect(result.status).toBe(UserStatus.DISABLED);
    });

    it('Token撤销失败不应影响删除操作', async () => {
      const operatorId = 'admin-uuid';
      const freshMockUser = { ...mockUser };

      jest.spyOn(userRepository, 'findOne').mockResolvedValue(freshMockUser);
      jest.spyOn(userRepository, 'softRemove').mockResolvedValue(freshMockUser);
      mockRedisService.delByPattern.mockRejectedValueOnce(new Error('Redis connection error'));

      // 不应该抛出错误
      await expect(service.remove(freshMockUser.id, operatorId)).resolves.not.toThrow();
    });
  });
});
