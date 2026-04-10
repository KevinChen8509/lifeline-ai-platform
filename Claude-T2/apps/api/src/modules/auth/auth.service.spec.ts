import { Test, TestingModule } from '@nestjs/testing';
import { JwtService } from '@nestjs/jwt';
import { ConfigService } from '@nestjs/config';
import { UnauthorizedException, ForbiddenException } from '@nestjs/common';
import { AuthService } from './auth.service';
import { UserService } from '../user/user.service';
import { RedisService } from '../redis/redis.service';
import { AbilityFactory } from './ability/ability.factory';
import { AuditLogService } from '../audit/audit-log.service';
import { User, UserStatus } from '../user/user.entity';
import { LoginDto } from './dto/login.dto';
import { RoleCode } from '../role/role.entity';

// Mock bcrypt
jest.mock('bcrypt', () => ({
  compare: jest.fn(),
}));

const bcrypt = require('bcrypt');

describe('AuthService', () => {
  let service: AuthService;
  let userService: UserService;
  let jwtService: JwtService;
  let redisService: RedisService;

  const mockUser: User = {
    id: '123e4567-e89b-12d3-a456-426614174000',
    username: 'testuser',
    passwordHash: 'hashedpassword',
    name: 'Test User',
    email: 'test@example.com',
    phone: '13800138000',
    status: UserStatus.ACTIVE,
    roleId: 'role-123',
    role: {
      id: 'role-123',
      name: '管理员',
      code: 'admin',
    } as any,
    createdAt: new Date(),
    updatedAt: new Date(),
    deletedAt: null,
  };

  const mockUserService = {
    findByUsername: jest.fn(),
    findOne: jest.fn(),
  };

  const mockJwtService = {
    sign: jest.fn().mockReturnValue('mock.jwt.token'),
    verify: jest.fn(),
  };

  const mockConfigService = {
    get: jest.fn((key: string, defaultValue?: any) => {
      const config: Record<string, any> = {
        JWT_SECRET: 'test-secret-key-at-least-32-characters',
        JWT_ACCESS_EXPIRATION: '15m',
        JWT_REFRESH_EXPIRATION: '7d',
      };
      return config[key] || defaultValue;
    }),
  };

  const mockRedisService = {
    set: jest.fn().mockResolvedValue(undefined),
    get: jest.fn().mockResolvedValue(null),
    del: jest.fn().mockResolvedValue(undefined),
    exists: jest.fn().mockResolvedValue(true),
    delByPattern: jest.fn().mockResolvedValue(1),
    getDel: jest.fn().mockResolvedValue('valid.refresh.token'),
  };

  const mockAbilityFactory = {
    createForUser: jest.fn().mockReturnValue({
      can: jest.fn(),
      cannot: jest.fn(),
    }),
    getPermissionsForRole: jest.fn().mockReturnValue([
      { action: 'manage', subject: 'all' },
    ]),
  };

  const mockAuditLogService = {
    createLog: jest.fn().mockResolvedValue({}),
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        AuthService,
        {
          provide: UserService,
          useValue: mockUserService,
        },
        {
          provide: JwtService,
          useValue: mockJwtService,
        },
        {
          provide: ConfigService,
          useValue: mockConfigService,
        },
        {
          provide: RedisService,
          useValue: mockRedisService,
        },
        {
          provide: AbilityFactory,
          useValue: mockAbilityFactory,
        },
        {
          provide: AuditLogService,
          useValue: mockAuditLogService,
        },
      ],
    }).compile();

    service = module.get<AuthService>(AuthService);
    userService = module.get<UserService>(UserService);
    jwtService = module.get<JwtService>(JwtService);
    redisService = module.get<RedisService>(RedisService);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  describe('validateUser', () => {
    const loginDto: LoginDto = {
      username: 'testuser',
      password: 'Password123',
    };

    it('应该成功验证有效用户', async () => {
      mockUserService.findByUsername.mockResolvedValue(mockUser);
      bcrypt.compare.mockResolvedValue(true);

      const result = await service.validateUser(loginDto.username, loginDto.password);

      expect(result).toEqual(mockUser);
      expect(mockUserService.findByUsername).toHaveBeenCalledWith(loginDto.username);
      expect(bcrypt.compare).toHaveBeenCalledWith(loginDto.password, mockUser.passwordHash);
    });

    it('用户名不存在时应该返回 null', async () => {
      mockUserService.findByUsername.mockResolvedValue(null);

      const result = await service.validateUser('nonexistent', loginDto.password);

      expect(result).toBeNull();
      expect(bcrypt.compare).not.toHaveBeenCalled();
    });

    it('密码错误时应该返回 null', async () => {
      mockUserService.findByUsername.mockResolvedValue(mockUser);
      bcrypt.compare.mockResolvedValue(false);

      const result = await service.validateUser(loginDto.username, 'wrongpassword');

      expect(result).toBeNull();
    });
  });

  describe('login', () => {
    const loginDto: LoginDto = {
      username: 'testuser',
      password: 'Password123',
    };

    it('应该成功登录 ACTIVE 状态用户', async () => {
      mockUserService.findByUsername.mockResolvedValue(mockUser);
      bcrypt.compare.mockResolvedValue(true);
      mockJwtService.sign
        .mockReturnValueOnce('access.token')
        .mockReturnValueOnce('refresh.token');

      const result = await service.login(loginDto);

      expect(result.accessToken).toBe('access.token');
      expect(result.refreshToken).toBe('refresh.token');
      expect(result.user.username).toBe(mockUser.username);
      expect(result.user.status).toBe(UserStatus.ACTIVE);
    });

    it('用户名或密码错误时应该抛出 UnauthorizedException', async () => {
      mockUserService.findByUsername.mockResolvedValue(null);

      await expect(service.login(loginDto)).rejects.toThrow(UnauthorizedException);
      await expect(service.login(loginDto)).rejects.toThrow('用户名或密码错误');
    });

    it('DISABLED 用户应该抛出 ForbiddenException', async () => {
      const disabledUser = { ...mockUser, status: UserStatus.DISABLED };
      mockUserService.findByUsername.mockResolvedValue(disabledUser);
      bcrypt.compare.mockResolvedValue(true);

      await expect(service.login(loginDto)).rejects.toThrow(ForbiddenException);
      await expect(service.login(loginDto)).rejects.toThrow('账号已被禁用');
    });

    it('PENDING 用户应该抛出 ForbiddenException', async () => {
      const pendingUser = { ...mockUser, status: UserStatus.PENDING };
      mockUserService.findByUsername.mockResolvedValue(pendingUser);
      bcrypt.compare.mockResolvedValue(true);

      await expect(service.login(loginDto)).rejects.toThrow(ForbiddenException);
      await expect(service.login(loginDto)).rejects.toThrow('账号尚未激活');
    });
  });

  describe('JWT Token 生成', () => {
    it('应该使用正确的 payload 生成 Access Token', async () => {
      mockUserService.findByUsername.mockResolvedValue(mockUser);
      bcrypt.compare.mockResolvedValue(true);
      mockJwtService.sign.mockReturnValue('access.token');

      await service.login({ username: 'testuser', password: 'Password123' });

      const signCall = mockJwtService.sign.mock.calls[0];
      expect(signCall[0]).toEqual({
        sub: mockUser.id,
        username: mockUser.username,
        role: 'admin',
      });
    });

    it('无角色用户应该使用默认 role', async () => {
      const userWithoutRole = { ...mockUser, role: null, roleId: null };
      mockUserService.findByUsername.mockResolvedValue(userWithoutRole);
      bcrypt.compare.mockResolvedValue(true);

      await service.login({ username: 'testuser', password: 'Password123' });

      const signCall = mockJwtService.sign.mock.calls[0];
      expect(signCall[0].role).toBe('user');
    });
  });

  describe('refreshToken', () => {
    const validRefreshToken = 'valid.refresh.token';
    const mockPayload = {
      sub: mockUser.id,
      tokenId: 'token-uuid-123',
      type: 'refresh' as const,
    };

    it('应该成功刷新Token', async () => {
      mockJwtService.verify.mockReturnValue(mockPayload);
      mockRedisService.getDel.mockResolvedValue(validRefreshToken);
      mockUserService.findOne.mockResolvedValue(mockUser);
      mockJwtService.sign
        .mockReturnValueOnce('new.access.token')
        .mockReturnValueOnce('new.refresh.token');

      const result = await service.refreshToken({ refreshToken: validRefreshToken });

      expect(result.accessToken).toBe('new.access.token');
      expect(result.refreshToken).toBe('new.refresh.token');
      expect(mockRedisService.getDel).toHaveBeenCalledWith(`refresh:${mockUser.id}:${mockPayload.tokenId}`);
      expect(mockRedisService.set).toHaveBeenCalled();
    });

    it('无效Refresh Token应该抛出UnauthorizedException', async () => {
      mockJwtService.verify.mockImplementation(() => {
        throw new Error('Invalid token');
      });

      await expect(service.refreshToken({ refreshToken: 'invalid.token' }))
        .rejects.toThrow(UnauthorizedException);
      await expect(service.refreshToken({ refreshToken: 'invalid.token' }))
        .rejects.toThrow('无效的Refresh Token');
    });

    it('Token不存在于Redis应该抛出UnauthorizedException', async () => {
      mockJwtService.verify.mockReturnValue(mockPayload);
      mockRedisService.getDel.mockResolvedValue(null);

      await expect(service.refreshToken({ refreshToken: validRefreshToken }))
        .rejects.toThrow(UnauthorizedException);
      await expect(service.refreshToken({ refreshToken: validRefreshToken }))
        .rejects.toThrow('Refresh Token已失效或已被使用');
    });

    it('用户不存在应该抛出UnauthorizedException', async () => {
      mockJwtService.verify.mockReturnValue(mockPayload);
      mockRedisService.getDel.mockResolvedValue(validRefreshToken);
      mockUserService.findOne.mockResolvedValue(null);

      await expect(service.refreshToken({ refreshToken: validRefreshToken }))
        .rejects.toThrow(UnauthorizedException);
      await expect(service.refreshToken({ refreshToken: validRefreshToken }))
        .rejects.toThrow('用户不存在');
    });

    it('用户已被禁用应该抛出UnauthorizedException', async () => {
      mockJwtService.verify.mockReturnValue(mockPayload);
      mockRedisService.getDel.mockResolvedValue(validRefreshToken);
      mockUserService.findOne.mockResolvedValue({ ...mockUser, status: UserStatus.DISABLED });

      await expect(service.refreshToken({ refreshToken: validRefreshToken }))
        .rejects.toThrow(UnauthorizedException);
      await expect(service.refreshToken({ refreshToken: validRefreshToken }))
        .rejects.toThrow('用户已被禁用或未激活');
    });
  });

  describe('logout', () => {
    it('应该成功删除Refresh Token', async () => {
      await service.logout(mockUser.id, 'token-uuid-123');

      expect(mockRedisService.del).toHaveBeenCalledWith(`refresh:${mockUser.id}:token-uuid-123`);
    });
  });

  describe('logoutByUserId', () => {
    it('应该删除用户所有Refresh Token', async () => {
      mockRedisService.delByPattern.mockResolvedValue(3);

      await service.logoutByUserId(mockUser.id);

      expect(mockRedisService.delByPattern).toHaveBeenCalledWith(`refresh:${mockUser.id}:*`);
    });
  });
});
