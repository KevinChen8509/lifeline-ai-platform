import {
  Controller,
  Get,
  Post,
  Put,
  Delete,
  Body,
  Param,
  Query,
  ParseUUIDPipe,
  UseGuards,
  Req,
} from '@nestjs/common';
import { AuthGuard } from '@nestjs/passport';
import {
  ApiTags,
  ApiOperation,
  ApiBearerAuth,
  ApiResponse,
} from '@nestjs/swagger';
import { Request } from 'express';
import { AiModelService } from './ai-model.service';
import { CreateAiModelDto } from './dto/create-ai-model.dto';
import { UpdateAiModelDto } from './dto/update-ai-model.dto';
import { AiModel, AiModelStatus, AiModelType } from './ai-model.entity';
import { PermissionsGuard } from '../../common/guards/permissions.guard';
import { RequirePermissions } from '../../common/decorators/permissions.decorator';

@Controller('ai-models')
@UseGuards(AuthGuard('jwt'), PermissionsGuard)
@ApiTags('AI Models')
@ApiBearerAuth()
export class AiModelController {
  constructor(private readonly aiModelService: AiModelService) {}

  @Post()
  @RequirePermissions('create', 'AiModel')
  @ApiOperation({ summary: '创建AI模型' })
  @ApiResponse({ status: 201, description: '创建成功' })
  @ApiResponse({ status: 400, description: '请求参数错误' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  @ApiResponse({ status: 409, description: '模型编码已存在' })
  async create(
    @Body() createDto: CreateAiModelDto,
    @Req() req: Request & { user: { sub: string } },
  ): Promise<AiModel> {
    const requestInfo = {
      ipAddress: this.extractIpAddress(req),
      userAgent: req.headers['user-agent'],
    };
    return this.aiModelService.create(createDto, req.user.sub, requestInfo);
  }

  @Get()
  @RequirePermissions('read', 'AiModel')
  @ApiOperation({ summary: '获取AI模型列表' })
  @ApiResponse({ status: 200, description: '获取成功' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  async findAll(
    @Query('page') page = 1,
    @Query('pageSize') pageSize = 20,
    @Query('type') type?: AiModelType,
    @Query('status') status?: AiModelStatus,
    @Query('search') search?: string,
  ): Promise<{ items: (AiModel & { deviceCount: number })[]; total: number; page: number; pageSize: number }> {
    return this.aiModelService.findAll({
      page,
      pageSize,
      type,
      status,
      search,
    });
  }

  @Get(':id')
  @RequirePermissions('read', 'AiModel')
  @ApiOperation({ summary: '获取AI模型详情' })
  @ApiResponse({ status: 200, description: '获取成功' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  @ApiResponse({ status: 404, description: '模型不存在' })
  async findOne(
    @Param('id', ParseUUIDPipe) id: string,
    @Query('page') page = 1,
    @Query('pageSize') pageSize = 20,
  ): Promise<{
    model: AiModel;
    specs: Record<string, any>;
    applicableDeviceTypes: string[];
    bindings: { items: any[]; total: number; page: number; pageSize: number };
  }> {
    return this.aiModelService.findOneDetail(id, { page, pageSize });
  }

  @Put(':id')
  @RequirePermissions('manage', 'AiModel')
  @ApiOperation({ summary: '更新AI模型' })
  @ApiResponse({ status: 200, description: '更新成功' })
  @ApiResponse({ status: 400, description: '请求参数错误' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  @ApiResponse({ status: 404, description: '模型不存在' })
  @ApiResponse({ status: 409, description: '模型编码已存在' })
  async update(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() updateDto: UpdateAiModelDto,
    @Req() req: Request & { user: { sub: string } },
  ): Promise<AiModel> {
    const requestInfo = {
      ipAddress: this.extractIpAddress(req),
      userAgent: req.headers['user-agent'],
    };
    return this.aiModelService.update(id, updateDto, req.user.sub, requestInfo);
  }

  @Put(':id/publish')
  @RequirePermissions('manage', 'AiModel')
  @ApiOperation({ summary: '发布AI模型' })
  @ApiResponse({ status: 200, description: '发布成功' })
  @ApiResponse({ status: 400, description: '模型已发布或状态不允许' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  @ApiResponse({ status: 404, description: '模型不存在' })
  async publish(
    @Param('id', ParseUUIDPipe) id: string,
    @Req() req: Request & { user: { sub: string } },
  ): Promise<AiModel> {
    const requestInfo = {
      ipAddress: this.extractIpAddress(req),
      userAgent: req.headers['user-agent'],
    };
    return this.aiModelService.publish(id, req.user.sub, requestInfo);
  }

  @Put(':id/deprecate')
  @RequirePermissions('manage', 'AiModel')
  @ApiOperation({ summary: '下线AI模型' })
  @ApiResponse({ status: 200, description: '下线成功' })
  @ApiResponse({ status: 400, description: '模型状态不允许下线' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  @ApiResponse({ status: 404, description: '模型不存在' })
  async deprecate(
    @Param('id', ParseUUIDPipe) id: string,
    @Req() req: Request & { user: { sub: string } },
  ): Promise<AiModel> {
    const requestInfo = {
      ipAddress: this.extractIpAddress(req),
      userAgent: req.headers['user-agent'],
    };
    return this.aiModelService.deprecate(id, req.user.sub, requestInfo);
  }

  @Delete(':id')
  @RequirePermissions('manage', 'AiModel')
  @ApiOperation({ summary: '删除AI模型' })
  @ApiResponse({ status: 200, description: '删除成功' })
  @ApiResponse({ status: 400, description: '模型有设备绑定，无法删除' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  @ApiResponse({ status: 404, description: '模型不存在' })
  async remove(
    @Param('id', ParseUUIDPipe) id: string,
    @Req() req: Request & { user: { sub: string } },
  ): Promise<{ success: boolean }> {
    const requestInfo = {
      ipAddress: this.extractIpAddress(req),
      userAgent: req.headers['user-agent'],
    };
    await this.aiModelService.remove(id, req.user.sub, requestInfo);
    return { success: true };
  }

  // ==================== 版本管理端点 ====================

  @Get(':id/versions')
  @RequirePermissions('read', 'AiModel')
  @ApiOperation({ summary: '获取模型版本列表' })
  @ApiResponse({ status: 200, description: '获取成功' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  @ApiResponse({ status: 404, description: '模型不存在' })
  async getVersions(
    @Param('id', ParseUUIDPipe) id: string,
    @Query('status') status?: string,
  ): Promise<any[]> {
    return this.aiModelService.getVersions(id, { status: status as any });
  }

  @Get(':id/versions/:versionId')
  @RequirePermissions('read', 'AiModel')
  @ApiOperation({ summary: '获取版本详情' })
  @ApiResponse({ status: 200, description: '获取成功' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  @ApiResponse({ status: 404, description: '版本不存在' })
  async getVersion(
    @Param('id', ParseUUIDPipe) id: string,
    @Param('versionId', ParseUUIDPipe) versionId: string,
  ): Promise<any> {
    return this.aiModelService.getVersion(id, versionId);
  }

  @Post(':id/versions')
  @RequirePermissions('manage', 'AiModel')
  @ApiOperation({ summary: '创建模型版本' })
  @ApiResponse({ status: 201, description: '创建成功' })
  @ApiResponse({ status: 400, description: '请求参数错误' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  @ApiResponse({ status: 404, description: '模型不存在' })
  @ApiResponse({ status: 409, description: '版本号已存在' })
  async createVersion(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() createDto: any,
    @Req() req: Request & { user: { sub: string } },
  ): Promise<any> {
    const requestInfo = {
      ipAddress: this.extractIpAddress(req),
      userAgent: req.headers['user-agent'],
    };
    return this.aiModelService.createVersion(id, createDto, req.user.sub, requestInfo);
  }

  @Put(':id/versions/:versionId')
  @RequirePermissions('manage', 'AiModel')
  @ApiOperation({ summary: '更新版本信息' })
  @ApiResponse({ status: 200, description: '更新成功' })
  @ApiResponse({ status: 400, description: '已发布的版本不允许修改' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  @ApiResponse({ status: 404, description: '版本不存在' })
  async updateVersion(
    @Param('id', ParseUUIDPipe) id: string,
    @Param('versionId', ParseUUIDPipe) versionId: string,
    @Body() updateDto: any,
    @Req() req: Request & { user: { sub: string } },
  ): Promise<any> {
    const requestInfo = {
      ipAddress: this.extractIpAddress(req),
      userAgent: req.headers['user-agent'],
    };
    return this.aiModelService.updateVersion(id, versionId, updateDto, req.user.sub, requestInfo);
  }

  @Put(':id/versions/:versionId/publish')
  @RequirePermissions('manage', 'AiModel')
  @ApiOperation({ summary: '发布版本' })
  @ApiResponse({ status: 200, description: '发布成功' })
  @ApiResponse({ status: 400, description: '版本已发布或缺少文件' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  @ApiResponse({ status: 404, description: '版本不存在' })
  async publishVersion(
    @Param('id', ParseUUIDPipe) id: string,
    @Param('versionId', ParseUUIDPipe) versionId: string,
    @Req() req: Request & { user: { sub: string } },
  ): Promise<any> {
    const requestInfo = {
      ipAddress: this.extractIpAddress(req),
      userAgent: req.headers['user-agent'],
    };
    return this.aiModelService.publishVersion(id, versionId, req.user.sub, requestInfo);
  }

  @Put(':id/versions/:versionId/deprecate')
  @RequirePermissions('manage', 'AiModel')
  @ApiOperation({ summary: '下线版本' })
  @ApiResponse({ status: 200, description: '下线成功' })
  @ApiResponse({ status: 400, description: '版本状态不允许或为当前版本' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  @ApiResponse({ status: 404, description: '版本不存在' })
  async deprecateVersion(
    @Param('id', ParseUUIDPipe) id: string,
    @Param('versionId', ParseUUIDPipe) versionId: string,
    @Req() req: Request & { user: { sub: string } },
  ): Promise<any> {
    const requestInfo = {
      ipAddress: this.extractIpAddress(req),
      userAgent: req.headers['user-agent'],
    };
    return this.aiModelService.deprecateVersion(id, versionId, req.user.sub, requestInfo);
  }

  @Get(':id/versions/current')
  @RequirePermissions('read', 'AiModel')
  @ApiOperation({ summary: '获取当前发布版本' })
  @ApiResponse({ status: 200, description: '获取成功' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  @ApiResponse({ status: 404, description: '模型不存在' })
  async getCurrentVersion(
    @Param('id', ParseUUIDPipe) id: string,
  ): Promise<any | null> {
    return this.aiModelService.getCurrentVersion(id);
  }

  // ==================== 部署管理端点 ====================

  @Post(':id/deploy')
  @RequirePermissions('manage', 'AiModel')
  @ApiOperation({ summary: '创建模型部署任务' })
  @ApiResponse({ status: 201, description: '部署任务创建成功' })
  @ApiResponse({ status: 400, description: '请求参数错误或无在线设备' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  @ApiResponse({ status: 404, description: '模型不存在' })
  async createDeployment(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() deployDto: any,
    @Req() req: Request & { user: { sub: string } },
  ): Promise<any> {
    const requestInfo = {
      ipAddress: this.extractIpAddress(req),
      userAgent: req.headers['user-agent'],
    };
    return this.aiModelService.createDeployment(id, deployDto, req.user.sub, requestInfo);
  }

  @Get(':id/deployments')
  @RequirePermissions('read', 'AiModel')
  @ApiOperation({ summary: '获取模型部署任务列表' })
  @ApiResponse({ status: 200, description: '获取成功' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  @ApiResponse({ status: 404, description: '模型不存在' })
  async getDeployments(
    @Param('id', ParseUUIDPipe) id: string,
    @Query('status') status?: string,
    @Query('page') page = 1,
    @Query('pageSize') pageSize = 20,
  ): Promise<any> {
    return this.aiModelService.getDeployments(id, { status: status as any, page, pageSize });
  }

  @Get(':id/deployments/:deploymentId')
  @RequirePermissions('read', 'AiModel')
  @ApiOperation({ summary: '获取部署任务详情' })
  @ApiResponse({ status: 200, description: '获取成功' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  @ApiResponse({ status: 404, description: '部署任务不存在' })
  async getDeployment(
    @Param('id', ParseUUIDPipe) id: string,
    @Param('deploymentId', ParseUUIDPipe) deploymentId: string,
  ): Promise<any> {
    return this.aiModelService.getDeployment(deploymentId);
  }

  @Post(':id/deployments/:deploymentId/retry')
  @RequirePermissions('manage', 'AiModel')
  @ApiOperation({ summary: '重试失败的设备部署' })
  @ApiResponse({ status: 200, description: '重试成功' })
  @ApiResponse({ status: 400, description: '没有失败的设备' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  @ApiResponse({ status: 404, description: '部署任务不存在' })
  async retryFailedDevices(
    @Param('id', ParseUUIDPipe) id: string,
    @Param('deploymentId', ParseUUIDPipe) deploymentId: string,
    @Req() req: Request & { user: { sub: string } },
  ): Promise<any> {
    return this.aiModelService.retryFailedDevices(deploymentId, req.user.sub);
  }

  @Post(':id/deployments/:deploymentId/cancel')
  @RequirePermissions('manage', 'AiModel')
  @ApiOperation({ summary: '取消部署任务' })
  @ApiResponse({ status: 200, description: '取消成功' })
  @ApiResponse({ status: 400, description: '部署任务已完成或已取消' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  @ApiResponse({ status: 404, description: '部署任务不存在' })
  async cancelDeployment(
    @Param('id', ParseUUIDPipe) id: string,
    @Param('deploymentId', ParseUUIDPipe) deploymentId: string,
    @Req() req: Request & { user: { sub: string } },
  ): Promise<any> {
    return this.aiModelService.cancelDeployment(deploymentId, req.user.sub);
  }

  /**
   * 提取客户端真实IP地址
   */
  private extractIpAddress(req: Request): string {
    let ip: string | undefined;

    const xForwardedFor = req.headers['x-forwarded-for'] as string | string[] | undefined;
    if (xForwardedFor) {
      ip = Array.isArray(xForwardedFor) ? xForwardedFor[0] : xForwardedFor.split(',')[0];
      ip = ip?.trim();
    }

    if (!ip) {
      const xRealIp = req.headers['x-real-ip'] as string | string[] | undefined;
      if (xRealIp) {
        ip = Array.isArray(xRealIp) ? xRealIp[0] : xRealIp;
      }
    }

    if (!ip) {
      const socket = (req as any).socket;
      if (socket?.remoteAddress) {
        ip = socket.remoteAddress;
      }
    }

    return this.validateIpAddress(ip);
  }

  /**
   * 验证IP地址格式
   */
  private validateIpAddress(ip: string | undefined): string {
    if (!ip || ip === 'unknown') {
      return 'unknown';
    }

    const ipWithoutPort = ip.split(':')[0];
    const ipv4Regex = /^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$/;
    const ipv6Regex = /^(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$|^::(?:[0-9a-fA-F]{1,4}:){0,6}[0-9a-fA-F]{1,4}$|^(?:[0-9a-fA-F]{1,4}:){1,7}:$|^(?:[0-9a-fA-F]{1,4}:){0,6}::(?:[0-9a-fA-F]{1,4}:){0,5}[0-9a-fA-F]{1,4}$/;

    if (ipWithoutPort === 'localhost' || ipWithoutPort === '::1' || ipWithoutPort === '127.0.0.1') {
      return ipWithoutPort;
    }

    if (ipv4Regex.test(ipWithoutPort) || ipv6Regex.test(ipWithoutPort)) {
      return ipWithoutPort;
    }

    return 'unknown';
  }
}
