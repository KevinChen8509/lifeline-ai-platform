import { Test, TestingModule } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { ConflictException, NotFoundException } from '@nestjs/common';
import { UserService } from './user.service';
import { User, UserStatus } from './user.entity';
import { CreateUserDto } from './dto/create-user.dto';
import { EmailService } from '../email/email.service';
import { RoleService } from '../role/role.service';
import { AuditLogService } from '../audit/audit-log.service';
import { RedisService } from '../redis/redis.service';

// Mock bcrypt to avoid native module build issues
jest.mock('bcrypt', () => ({
  hash: jest.fn().mockResolvedValue('$2b$10$mockedHash'),
  compare: jest.fn().mockResolvedValue(true),
}));

describe('UserService', () => {
  let service: UserService;
  let repository: Repository<User>;

  const mockUser: User = {
    id: '123e4567-e89b-12d3-a456-426614174000',
    username: 'testuser',
    passwordHash: 'hashedpassword',
    name: 'Test User',
    email: 'test@example.com',
    phone: '13800138000',
    status: UserStatus.PENDING,
    roleId: null,
    role: null,
    createdAt: new Date(),
    updatedAt: new Date(),
    deletedAt: null,
  };

  const mockRepository = {
    create: jest.fn(),
    save: jest.fn(),
    findOne: jest.fn(),
    find: jest.fn(),
    softRemove: jest.fn(),
    createQueryBuilder: jest.fn(),
  };

  const mockEmailService = {
    sendActivationEmail: jest.fn().mockResolvedValue(true),
    isAvailable: jest.fn().mockReturnValue(true),
  };

  const mockRoleService = {
    findOne: jest.fn(),
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
          useValue: mockRepository,
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
    repository = module.get<Repository<User>>(getRepositoryToken(User));
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  describe('create', () => {
    const createUserDto: CreateUserDto = {
      username: 'newuser',
      password: 'Password123',
      name: 'New User',
      email: 'new@example.com',
    };

    it('应该成功创建用户', async () => {
      mockRepository.findOne.mockResolvedValue(null); // 用户名不存在
      mockRepository.create.mockReturnValue(mockUser);
      mockRepository.save.mockResolvedValue(mockUser);

      const result = await service.create(createUserDto);

      expect(result).toBeDefined();
      expect(mockRepository.findOne).toHaveBeenCalledWith({
        where: { username: createUserDto.username },
        relations: ['role'],
      });
      expect(mockRepository.create).toHaveBeenCalled();
      expect(mockRepository.save).toHaveBeenCalled();
    });

    it('用户名已存在时应该抛出 ConflictException', async () => {
      mockRepository.findOne.mockResolvedValue(mockUser); // 用户名已存在

      await expect(service.create(createUserDto)).rejects.toThrow(ConflictException);
      expect(mockRepository.save).not.toHaveBeenCalled();
    });

    it('应该使用 bcrypt 哈希密码', async () => {
      mockRepository.findOne.mockResolvedValue(null);
      mockRepository.create.mockImplementation((data) => data);
      mockRepository.save.mockImplementation((data) => data);

      await service.create(createUserDto);

      const createCall = mockRepository.create.mock.calls[0][0];
      expect(createCall.passwordHash).toBeDefined();
      expect(createCall.passwordHash).not.toBe(createUserDto.password);
    });

    it('应该设置默认状态为 PENDING', async () => {
      mockRepository.findOne.mockResolvedValue(null);
      mockRepository.create.mockImplementation((data) => data);
      mockRepository.save.mockImplementation((data) => data);

      await service.create(createUserDto);

      const createCall = mockRepository.create.mock.calls[0][0];
      expect(createCall.status).toBe(UserStatus.PENDING);
    });
  });

  describe('checkUsername', () => {
    it('用户名可用时返回 { available: true }', async () => {
      mockRepository.findOne.mockResolvedValue(null);

      const result = await service.checkUsername('availableuser');

      expect(result).toEqual({ available: true });
    });

    it('用户名已存在时返回 { available: false }', async () => {
      mockRepository.findOne.mockResolvedValue(mockUser);

      const result = await service.checkUsername('testuser');

      expect(result).toEqual({ available: false });
    });
  });

  describe('findOne', () => {
    it('应该返回用户', async () => {
      mockRepository.findOne.mockResolvedValue(mockUser);

      const result = await service.findOne(mockUser.id);

      expect(result).toEqual(mockUser);
    });

    it('用户不存在时应该抛出 NotFoundException', async () => {
      mockRepository.findOne.mockResolvedValue(null);

      await expect(service.findOne('non-existent-id')).rejects.toThrow(NotFoundException);
    });
  });

  describe('toResponseDto', () => {
    it('应该排除 passwordHash 字段', () => {
      const result = service.toResponseDto(mockUser);

      expect(result).not.toHaveProperty('passwordHash');
      expect(result).not.toHaveProperty('deletedAt');
      expect(result).toHaveProperty('id');
      expect(result).toHaveProperty('username');
      expect(result).toHaveProperty('name');
    });

    it('应该保留其他字段', () => {
      const result = service.toResponseDto(mockUser);

      expect(result.id).toBe(mockUser.id);
      expect(result.username).toBe(mockUser.username);
      expect(result.name).toBe(mockUser.name);
      expect(result.email).toBe(mockUser.email);
      expect(result.status).toBe(mockUser.status);
    });
  });

  describe('findAll - 角色筛选', () => {
    const mockQueryBuilder = {
      leftJoinAndSelect: jest.fn().mockReturnThis(),
      andWhere: jest.fn().mockReturnThis(),
      skip: jest.fn().mockReturnThis(),
      take: jest.fn().mockReturnThis(),
      orderBy: jest.fn().mockReturnThis(),
      getManyAndCount: jest.fn(),
    };

    it('应该支持按角色筛选', async () => {
      const roleId = 'role-uuid-123';
      mockRepository.createQueryBuilder.mockReturnValue(mockQueryBuilder);
      mockQueryBuilder.getManyAndCount.mockResolvedValue([[mockUser], 1]);

      const result = await service.findAll({ page: 1, pageSize: 20, roleId });

      expect(mockQueryBuilder.andWhere).toHaveBeenCalledWith(
        'user.roleId = :roleId',
        { roleId },
      );
      expect(result.items).toHaveLength(1);
    });

    it('应该支持组合筛选（状态+角色）', async () => {
      const roleId = 'role-uuid-123';
      const status = UserStatus.ACTIVE;
      mockRepository.createQueryBuilder.mockReturnValue(mockQueryBuilder);
      mockQueryBuilder.getManyAndCount.mockResolvedValue([[mockUser], 1]);

      const result = await service.findAll({
        page: 1,
        pageSize: 20,
        status,
        roleId,
      });

      expect(mockQueryBuilder.andWhere).toHaveBeenCalledWith(
        'user.status = :status',
        { status },
      );
      expect(mockQueryBuilder.andWhere).toHaveBeenCalledWith(
        'user.roleId = :roleId',
        { roleId },
      );
      expect(result.items).toHaveLength(1);
    });

    it('应该支持组合筛选（状态+角色+搜索）', async () => {
      const roleId = 'role-uuid-123';
      const status = UserStatus.ACTIVE;
      const search = 'test';
      mockRepository.createQueryBuilder.mockReturnValue(mockQueryBuilder);
      mockQueryBuilder.getManyAndCount.mockResolvedValue([[mockUser], 1]);

      const result = await service.findAll({
        page: 1,
        pageSize: 20,
        status,
        roleId,
        search,
      });

      expect(mockQueryBuilder.andWhere).toHaveBeenCalledWith(
        'user.status = :status',
        { status },
      );
      expect(mockQueryBuilder.andWhere).toHaveBeenCalledWith(
        'user.roleId = :roleId',
        { roleId },
      );
      expect(mockQueryBuilder.andWhere).toHaveBeenCalledWith(
        '(user.username LIKE :search OR user.name LIKE :search OR user.email LIKE :search)',
        { search: '%test%' },
      );
      expect(result.items).toHaveLength(1);
    });

    it('不传入角色参数时不应添加角色筛选', async () => {
      mockRepository.createQueryBuilder.mockReturnValue(mockQueryBuilder);
      mockQueryBuilder.getManyAndCount.mockResolvedValue([[mockUser], 1]);

      await service.findAll({ page: 1, pageSize: 20 });

      // 检查 andWhere 没有被调用过 roleId
      const roleIdCalls = mockQueryBuilder.andWhere.mock.calls.filter(
        (call) => call[1] && 'roleId' in call[1],
      );
      expect(roleIdCalls).toHaveLength(0);
    });
  });
});
