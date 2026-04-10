import { Controller, Post, Get, Body, HttpCode, HttpStatus, UseGuards, Req } from '@nestjs/common';
import { AuthGuard } from '@nestjs/passport';
import { ApiTags, ApiOperation, ApiResponse, ApiBearerAuth } from '@nestjs/swagger';
import { Request } from 'express';
import { AuthService, JwtPayload, RefreshTokenPayload, RequestInfo } from './auth.service';
import { LoginDto } from './dto/login.dto';
import { RefreshTokenDto } from './dto/refresh-token.dto';
import { AuthResponseDto } from './dto/auth-response.dto';
import { PermissionsResponseDto } from './dto/permissions.dto';

@Controller('auth')
@ApiTags('Auth')
export class AuthController {
  constructor(private readonly authService: AuthService) {}

  @Post('login')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: '用户登录' })
  @ApiResponse({ status: 200, description: '登录成功', type: AuthResponseDto })
  @ApiResponse({ status: 401, description: '用户名或密码错误' })
  @ApiResponse({ status: 403, description: '账号已被禁用或尚未激活' })
  async login(@Body() loginDto: LoginDto, @Req() req: Request): Promise<AuthResponseDto> {
    const requestInfo: RequestInfo = {
      ipAddress: this.extractIpAddress(req),
      userAgent: this.sanitizeUserAgent(req.headers['user-agent']),
    };
    return this.authService.login(loginDto, requestInfo);
  }

  @Post('refresh')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: '刷新Token' })
  @ApiResponse({ status: 200, description: '刷新成功', type: AuthResponseDto })
  @ApiResponse({ status: 401, description: '无效的Refresh Token' })
  async refreshToken(@Body() refreshTokenDto: RefreshTokenDto): Promise<AuthResponseDto> {
    return this.authService.refreshToken(refreshTokenDto);
  }

  @Post('logout')
  @UseGuards(AuthGuard('jwt'))
  @HttpCode(HttpStatus.OK)
  @ApiBearerAuth()
  @ApiOperation({ summary: '用户登出' })
  @ApiResponse({ status: 200, description: '登出成功' })
  @ApiResponse({ status: 401, description: '未授权' })
  async logout(@Req() req: Request & { user: JwtPayload & { tokenId?: string } }): Promise<{ message: string }> {
    // 从 JWT payload 中获取用户ID
    // 由于 access token 不包含 tokenId，我们按 userId:* 模式删除所有 refresh token
    const requestInfo: RequestInfo = {
      ipAddress: this.extractIpAddress(req),
      userAgent: this.sanitizeUserAgent(req.headers['user-agent']),
    };
    await this.authService.logoutByUserId(req.user.sub, requestInfo);
    return { message: '登出成功' };
  }

  @Get('permissions')
  @UseGuards(AuthGuard('jwt'))
  @ApiBearerAuth()
  @ApiOperation({ summary: '获取当前用户权限' })
  @ApiResponse({ status: 200, description: '获取成功', type: PermissionsResponseDto })
  @ApiResponse({ status: 401, description: '未授权' })
  async getPermissions(@Req() req: { user: JwtPayload }): Promise<PermissionsResponseDto> {
    return this.authService.getUserPermissions(req.user.sub);
  }

  /**
   * 提取客户端真实IP地址
   */
  private extractIpAddress(req: Request): string {
    let ip: string | undefined;

    // 优先从代理头获取真实IP
    const xForwardedFor = req.headers['x-forwarded-for'] as string | string[] | undefined;
    if (xForwardedFor) {
      ip = Array.isArray(xForwardedFor) ? xForwardedFor[0] : xForwardedFor.split(',')[0];
      ip = ip?.trim();
    }

    // 从 X-Real-IP 获取（Nginx）
    if (!ip) {
      const xRealIp = req.headers['x-real-ip'] as string | string[] | undefined;
      if (xRealIp) {
        ip = Array.isArray(xRealIp) ? xRealIp[0] : xRealIp;
      }
    }

    // 使用 socket 远程地址
    if (!ip) {
      const socket = (req as any).socket;
      if (socket?.remoteAddress) {
        ip = socket.remoteAddress;
      }
    }

    // 验证IP格式，防止IP欺骗和注入
    return this.validateIpAddress(ip);
  }

  /**
   * 验证IP地址格式
   * 防止IP欺骗和日志注入攻击
   */
  private validateIpAddress(ip: string | undefined): string {
    if (!ip || ip === 'unknown') {
      return 'unknown';
    }

    // 移除端口部分（如 ::1:3000 -> ::1）
    const ipWithoutPort = ip.split(':')[0];

    // IPv4 和 IPv6 正则验证
    const ipv4Regex = /^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$/;
    const ipv6Regex = /^(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$|^::(?:[0-9a-fA-F]{1,4}:){0,6}[0-9a-fA-F]{1,4}$|^(?:[0-9a-fA-F]{1,4}:){1,7}:$|^(?:[0-9a-fA-F]{1,4}:){0,6}::(?:[0-9a-fA-F]{1,4}:){0,5}[0-9a-fA-F]{1,4}$/;

    // 也允许 localhost 和内网地址
    if (ipWithoutPort === 'localhost' || ipWithoutPort === '::1' || ipWithoutPort === '127.0.0.1') {
      return ipWithoutPort;
    }

    if (ipv4Regex.test(ipWithoutPort) || ipv6Regex.test(ipWithoutPort)) {
      return ipWithoutPort;
    }

    // 验证失败，返回 unknown 而不是无效IP
    return 'unknown';
  }

  /**
   * 清理 User-Agent 字符串
   * 防止日志注入攻击
   */
  private sanitizeUserAgent(userAgent: string | string[] | undefined): string {
    if (!userAgent) {
      return 'unknown';
    }

    // 处理数组情况
    const ua = Array.isArray(userAgent) ? userAgent[0] : userAgent;

    if (!ua || typeof ua !== 'string') {
      return 'unknown';
    }

    // 限制长度，防止超长字符串
    const maxLength = 500;
    let sanitized = ua.length > maxLength ? ua.substring(0, maxLength) : ua;

    // 移除可能导致日志解析问题的控制字符
    // 保留可打印ASCII字符和常见Unicode
    sanitized = sanitized.replace(/[\x00-\x1F\x7F]/g, '');

    // 转义可能导致日志注入的字符
    sanitized = sanitized.replace(/[\n\r\t]/g, ' ');

    return sanitized.trim() || 'unknown';
  }
}
