import { Test, TestingModule } from '@nestjs/testing';
import { AuthController } from './auth.controller';
import { AuthService, JwtPayload } from './auth.service';
import { LoginDto } from './dto/login.dto';
import { RefreshTokenDto } from './dto/refresh-token.dto';
import { UnauthorizedException, ForbiddenException } from '@nestjs/common';
import { UserStatus } from '../user/user.entity';
import { RoleCode } from '../role/role.entity';

// Mock bcrypt to avoid native binding issues
jest.mock('bcrypt', () => ({
  compare: jest.fn(),
  hash: jest.fn(),
}));

describe('AuthController', () => {
  let controller: AuthController;
  let service: AuthService;

  const mockAuthService = {
    login: jest.fn(),
    refreshToken: jest.fn(),
    logout: jest.fn(),
    logoutByUserId: jest.fn(),
    getUserPermissions: jest.fn(),
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      controllers: [AuthController],
      providers: [
        {
          provide: AuthService,
          useValue: mockAuthService,
        },
      ],
    }).compile();

    controller = module.get<AuthController>(AuthController);
    service = module.get<AuthService>(AuthService);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('should be defined', () => {
    expect(controller).toBeDefined();
  });

  describe('login', () => {
    const loginDto: LoginDto = {
      username: 'admin',
      password: 'password123',
    };

    const mockRequest = {
      headers: {
        'user-agent': 'jest-test',
        'x-forwarded-for': '127.0.0.1',
      },
      socket: { remoteAddress: '127.0.0.1' },
    } as any;

    it('应该成功登录并返回 AuthResponseDto', async () => {
      const mockResponse = {
        accessToken: 'access-token',
        refreshToken: 'refresh-token',
        user: {
          id: 'user-uuid',
          username: 'admin',
          name: 'Admin User',
          email: 'admin@example.com',
          status: UserStatus.ACTIVE,
          roleId: 'role-uuid',
          role: { id: 'role-uuid', name: '管理员', code: RoleCode.ADMIN },
        },
        permissions: [{ action: 'manage', subject: 'all' }],
      };

      mockAuthService.login.mockResolvedValue(mockResponse);

      const result = await controller.login(loginDto, mockRequest);

      expect(service.login).toHaveBeenCalledWith(loginDto, expect.objectContaining({
        ipAddress: expect.any(String),
        userAgent: 'jest-test',
      }));
      expect(result).toEqual(mockResponse);
    });

    it('用户名或密码错误应该抛出 UnauthorizedException', async () => {
      mockAuthService.login.mockRejectedValue(
        new UnauthorizedException('用户名或密码错误'),
      );

      await expect(controller.login(loginDto, mockRequest)).rejects.toThrow(
        UnauthorizedException,
      );
    });

    it('账号已被禁用应该抛出 ForbiddenException', async () => {
      mockAuthService.login.mockRejectedValue(
        new ForbiddenException('账号已被禁用'),
      );

      await expect(controller.login(loginDto, mockRequest)).rejects.toThrow(
        ForbiddenException,
      );
    });
  });

  describe('refreshToken', () => {
    const refreshTokenDto: RefreshTokenDto = {
      refreshToken: 'valid-refresh-token',
    };

    it('应该成功刷新Token', async () => {
      const mockResponse = {
        accessToken: 'new-access-token',
        refreshToken: 'new-refresh-token',
        user: {
          id: 'user-uuid',
          username: 'admin',
          name: 'Admin User',
          status: UserStatus.ACTIVE,
          roleId: 'role-uuid',
          role: { id: 'role-uuid', name: '管理员', code: RoleCode.ADMIN },
        },
        permissions: [{ action: 'manage', subject: 'all' }],
      };

      mockAuthService.refreshToken.mockResolvedValue(mockResponse);

      const result = await controller.refreshToken(refreshTokenDto);

      expect(service.refreshToken).toHaveBeenCalledWith(refreshTokenDto);
      expect(result).toEqual(mockResponse);
    });

    it('无效Refresh Token应该抛出 UnauthorizedException', async () => {
      mockAuthService.refreshToken.mockRejectedValue(
        new UnauthorizedException('无效的Refresh Token'),
      );

      await expect(
        controller.refreshToken(refreshTokenDto),
      ).rejects.toThrow(UnauthorizedException);
    });
  });

  describe('logout', () => {
    it('应该成功登出', async () => {
      const mockRequest = {
        user: { sub: 'user-uuid', username: 'admin', role: 'admin' } as JwtPayload & { tokenId?: string },
        headers: {
          'user-agent': 'jest-test',
          'x-forwarded-for': '127.0.0.1',
        },
        socket: { remoteAddress: '127.0.0.1' },
      } as any;

      mockAuthService.logoutByUserId.mockResolvedValue(undefined);

      const result = await controller.logout(mockRequest);

      expect(service.logoutByUserId).toHaveBeenCalledWith('user-uuid', expect.objectContaining({
        ipAddress: expect.any(String),
        userAgent: 'jest-test',
      }));
      expect(result).toEqual({ message: '登出成功' });
    });
  });

  describe('getPermissions', () => {
    it('应该返回当前用户的权限列表', async () => {
      const mockRequest = {
        user: { sub: 'user-uuid', username: 'admin', role: 'admin' } as JwtPayload,
      };

      const mockPermissions = {
        permissions: [{ action: 'manage', subject: 'all' }],
      };

      mockAuthService.getUserPermissions.mockResolvedValue(mockPermissions);

      const result = await controller.getPermissions(mockRequest);

      expect(service.getUserPermissions).toHaveBeenCalledWith('user-uuid');
      expect(result).toEqual(mockPermissions);
    });

    it('应该返回运维员的权限列表', async () => {
      const mockRequest = {
        user: { sub: 'operator-uuid', username: 'operator', role: 'operator' } as JwtPayload,
      };

      const mockPermissions = {
        permissions: [
          { action: 'read', subject: 'Project' },
          { action: 'manage', subject: 'Device' },
          { action: 'manage', subject: 'Model' },
          { action: 'manage', subject: 'Alert' },
          { action: 'read', subject: 'Telemetry' },
        ],
      };

      mockAuthService.getUserPermissions.mockResolvedValue(mockPermissions);

      const result = await controller.getPermissions(mockRequest);

      expect(service.getUserPermissions).toHaveBeenCalledWith('operator-uuid');
      expect(result.permissions).toHaveLength(5);
      expect(result.permissions).toContainEqual({ action: 'read', subject: 'Project' });
      expect(result.permissions).toContainEqual({ action: 'manage', subject: 'Device' });
    });

    it('应该返回观察员的权限列表', async () => {
      const mockRequest = {
        user: { sub: 'observer-uuid', username: 'observer', role: 'observer' } as JwtPayload,
      };

      const mockPermissions = {
        permissions: [
          { action: 'read', subject: 'Project' },
          { action: 'read', subject: 'Device' },
          { action: 'read', subject: 'Model' },
          { action: 'read', subject: 'Alert' },
          { action: 'read', subject: 'ApiKey' },
          { action: 'read', subject: 'Telemetry' },
        ],
      };

      mockAuthService.getUserPermissions.mockResolvedValue(mockPermissions);

      const result = await controller.getPermissions(mockRequest);

      expect(service.getUserPermissions).toHaveBeenCalledWith('observer-uuid');
      expect(result.permissions).toHaveLength(6);
      // 验证所有权限都是 read
      result.permissions.forEach((p) => {
        expect(p.action).toBe('read');
      });
    });

    it('用户不存在应该抛出 UnauthorizedException', async () => {
      const mockRequest = {
        user: { sub: 'non-existent-uuid', username: 'unknown', role: 'user' } as JwtPayload,
      };

      mockAuthService.getUserPermissions.mockRejectedValue(
        new UnauthorizedException('用户不存在'),
      );

      await expect(controller.getPermissions(mockRequest)).rejects.toThrow(
        UnauthorizedException,
      );
    });
  });
});
