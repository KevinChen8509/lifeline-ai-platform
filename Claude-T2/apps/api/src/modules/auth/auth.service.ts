import { Injectable, UnauthorizedException, ForbiddenException, Logger } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { ConfigService } from '@nestjs/config';
import * as bcrypt from 'bcrypt';
import { v4 as uuidv4 } from 'uuid';
import { UserService } from '../user/user.service';
import { RedisService } from '../redis/redis.service';
import { AuditLogService } from '../audit/audit-log.service';
import { AuditAction, AuditTargetType } from '../audit/audit-log.entity';
import { User, UserStatus } from '../user/user.entity';
import { LoginDto } from './dto/login.dto';
import { AuthResponseDto, UserInfoDto } from './dto/auth-response.dto';
import { RefreshTokenDto } from './dto/refresh-token.dto';
import { AbilityFactory } from './ability/ability.factory';
import { RoleCode } from '../role/role.entity';
import { PermissionsResponseDto } from './dto/permissions.dto';

export interface RequestInfo {
  ipAddress?: string;
  userAgent?: string;
}

export interface JwtPayload {
  sub: string;
  username: string;
  role: string;
}

export interface RefreshTokenPayload {
  sub: string;
  tokenId: string;
  type: 'refresh';
}

@Injectable()
export class AuthService {
  private readonly logger = new Logger(AuthService.name);

  constructor(
    private readonly userService: UserService,
    private readonly jwtService: JwtService,
    private readonly configService: ConfigService,
    private readonly redisService: RedisService,
    private readonly abilityFactory: AbilityFactory,
    private readonly auditLogService: AuditLogService,
  ) {}

  /**
   * 验证用户凭证
   */
  async validateUser(username: string, password: string): Promise<User | null> {
    const user = await this.userService.findByUsername(username);

    if (!user) {
      return null;
    }

    const isPasswordValid = await bcrypt.compare(password, user.passwordHash);
    if (!isPasswordValid) {
      return null;
    }

    return user;
  }

  /**
   * 用户登录
   */
  async login(loginDto: LoginDto, requestInfo?: RequestInfo): Promise<AuthResponseDto> {
    const user = await this.validateUser(loginDto.username, loginDto.password);

    if (!user) {
      // 记录登录失败日志
      this.logger.warn(`登录失败: 用户名或密码错误 - ${loginDto.username}`);
      await this.auditLogService.createLog({
        action: AuditAction.LOGIN_FAILED,
        targetType: 'User',
        targetId: null,
        operatorId: 'system',
        ipAddress: requestInfo?.ipAddress,
        userAgent: requestInfo?.userAgent,
        description: `登录失败: ${loginDto.username} - 用户名或密码错误`,
      });
      throw new UnauthorizedException('用户名或密码错误');
    }

    // 检查用户状态
    if (user.status === UserStatus.DISABLED) {
      this.logger.warn(`登录失败: 账号已被禁用 - ${user.username}`);
      await this.auditLogService.createLog({
        action: AuditAction.LOGIN_FAILED,
        targetType: 'User',
        targetId: user.id,
        operatorId: 'system',
        ipAddress: requestInfo?.ipAddress,
        userAgent: requestInfo?.userAgent,
        description: `登录失败: ${user.username} - 账号已被禁用`,
      });
      throw new ForbiddenException('账号已被禁用');
    }

    if (user.status === UserStatus.PENDING) {
      this.logger.warn(`登录失败: 账号尚未激活 - ${user.username}`);
      await this.auditLogService.createLog({
        action: AuditAction.LOGIN_FAILED,
        targetType: 'User',
        targetId: user.id,
        operatorId: 'system',
        ipAddress: requestInfo?.ipAddress,
        userAgent: requestInfo?.userAgent,
        description: `登录失败: ${user.username} - 账号尚未激活`,
      });
      throw new ForbiddenException('账号尚未激活');
    }

    // 生成 Token
    const accessToken = this.generateAccessToken(user);
    const refreshToken = await this.generateRefreshToken(user.id);

    // 获取用户权限
    const permissions = this.getUserPermissionsForRole(user.role?.code as RoleCode | undefined);

    // 记录登录成功日志
    this.logger.log(`用户登录成功: ${user.username}`);
    await this.auditLogService.createLog({
      action: AuditAction.LOGIN,
      targetType: 'User',
      targetId: user.id,
      operatorId: user.id,
      operator: { id: user.id, username: user.username, name: user.name },
      ipAddress: requestInfo?.ipAddress,
      userAgent: requestInfo?.userAgent,
      description: `用户登录成功: ${user.username}`,
    });

    return {
      accessToken,
      refreshToken,
      user: this.toUserInfoDto(user),
      permissions,
    };
  }

  /**
   * 生成 Access Token
   */
  private generateAccessToken(user: User): string {
    const payload: JwtPayload = {
      sub: user.id,
      username: user.username,
      role: user.role?.code || 'user',
    };

    return this.jwtService.sign(payload, {
      expiresIn: this.configService.get<string>('JWT_ACCESS_EXPIRATION', '15m'),
    });
  }

  /**
   * 生成 Refresh Token
   */
  private async generateRefreshToken(userId: string): Promise<string> {
    const tokenId = uuidv4();
    const payload: RefreshTokenPayload = {
      sub: userId,
      tokenId,
      type: 'refresh',
    };

    const refreshToken = this.jwtService.sign(payload, {
      expiresIn: this.configService.get<string>('JWT_REFRESH_EXPIRATION', '7d'),
    });

    // 存储 Refresh Token 到 Redis
    // key: refresh:{userId}:{tokenId}
    // TTL: 7天（604800秒）
    const ttl = 7 * 24 * 60 * 60;
    await this.redisService.set(
      `refresh:${userId}:${tokenId}`,
      refreshToken,
      ttl,
    );

    return refreshToken;
  }

  /**
   * 转换为用户信息 DTO
   */
  private toUserInfoDto(user: User): UserInfoDto {
    return {
      id: user.id,
      username: user.username,
      name: user.name,
      email: user.email,
      status: user.status,
      roleId: user.roleId,
      role: user.role ? {
        id: user.role.id,
        name: user.role.name,
        code: user.role.code,
      } : null,
    };
  }

  /**
   * 刷新 Token
   */
  async refreshToken(refreshTokenDto: RefreshTokenDto): Promise<AuthResponseDto> {
    try {
      // 验证 Refresh Token
      const payload = this.jwtService.verify<RefreshTokenPayload>(
        refreshTokenDto.refreshToken,
      );

      if (payload.type !== 'refresh') {
        throw new UnauthorizedException('无效的Refresh Token');
      }

      // 使用原子操作获取并删除 Token（防止并发使用）
      const key = `refresh:${payload.sub}:${payload.tokenId}`;
      const storedToken = await this.redisService.getDel(key);

      if (!storedToken) {
        throw new UnauthorizedException('Refresh Token已失效或已被使用');
      }

      // 获取用户信息
      const user = await this.userService.findOne(payload.sub);
      if (!user) {
        throw new UnauthorizedException('用户不存在');
      }

      if (user.status !== UserStatus.ACTIVE) {
        throw new UnauthorizedException('用户已被禁用或未激活');
      }

      // 生成新的 Token 对
      const accessToken = this.generateAccessToken(user);
      const newRefreshToken = await this.generateRefreshToken(user.id);

      // 获取用户权限
      const permissions = this.getUserPermissionsForRole(user.role?.code as RoleCode | undefined);

      this.logger.log(`Token刷新成功: ${user.username}`);

      return {
        accessToken,
        refreshToken: newRefreshToken,
        user: this.toUserInfoDto(user),
        permissions,
      };
    } catch (error) {
      if (error instanceof UnauthorizedException) {
        throw error;
      }
      // 区分不同的JWT错误类型
      if (error.name === 'TokenExpiredError') {
        throw new UnauthorizedException('Refresh Token已过期');
      }
      if (error.name === 'JsonWebTokenError') {
        throw new UnauthorizedException('无效的Refresh Token格式');
      }
      throw new UnauthorizedException('无效的Refresh Token');
    }
  }

  /**
   * 用户登出
   */
  async logout(userId: string, tokenId: string, requestInfo?: RequestInfo): Promise<void> {
    const key = `refresh:${userId}:${tokenId}`;
    await this.redisService.del(key);
    this.logger.log(`用户登出成功: ${userId}`);

    // 获取用户信息用于审计日志
    const user = await this.userService.findOne(userId).catch(() => null);
    await this.auditLogService.createLog({
      action: AuditAction.LOGOUT,
      targetType: 'User',
      targetId: userId,
      operatorId: userId,
      operator: user ? { id: user.id, username: user.username, name: user.name } : null,
      ipAddress: requestInfo?.ipAddress,
      userAgent: requestInfo?.userAgent,
      description: `用户登出成功: ${user?.username || userId}`,
    });
  }

  /**
   * 按用户ID登出（删除该用户所有Refresh Token）
   */
  async logoutByUserId(userId: string, requestInfo?: RequestInfo): Promise<void> {
    const pattern = `refresh:${userId}:*`;
    const count = await this.redisService.delByPattern(pattern);
    this.logger.log(`用户登出成功: ${userId}，删除 ${count} 个Refresh Token`);

    // 获取用户信息用于审计日志
    const user = await this.userService.findOne(userId).catch(() => null);
    await this.auditLogService.createLog({
      action: AuditAction.LOGOUT,
      targetType: 'User',
      targetId: userId,
      operatorId: userId,
      operator: user ? { id: user.id, username: user.username, name: user.name } : null,
      ipAddress: requestInfo?.ipAddress,
      userAgent: requestInfo?.userAgent,
      description: `用户登出成功: ${user?.username || userId}`,
    });
  }

  /**
   * 获取用户权限列表
   */
  async getUserPermissions(userId: string): Promise<PermissionsResponseDto> {
    const user = await this.userService.findOne(userId);
    if (!user) {
      throw new UnauthorizedException('用户不存在');
    }

    const roleCode = user.role?.code as RoleCode | undefined;
    const permissions = this.abilityFactory.getPermissionsForRole(roleCode || RoleCode.OBSERVER);

    return { permissions };
  }

  /**
   * 根据角色获取权限列表（同步方法）
   */
  private getUserPermissionsForRole(roleCode: RoleCode | undefined): Array<{ action: string; subject: string }> {
    return this.abilityFactory.getPermissionsForRole(roleCode || RoleCode.OBSERVER);
  }
}
