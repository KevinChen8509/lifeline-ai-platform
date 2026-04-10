import {
  Controller,
  Get,
  Put,
  Post,
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
import { DeviceService } from './device.service';
import { AssignProjectDto, BatchAssignProjectDto } from './dto/assign-project.dto';
import { ScanRegisterDto } from './dto/scan-register.dto';
import { CreateDeviceDto } from './dto/create-device.dto';
import { UpdateDeviceConfigDto, BatchUpdateConfigDto } from './dto/update-config.dto';
import { BindModelsDto } from './dto/bind-models.dto';
import { Device, DeviceStatus } from './device.entity';
import { DeviceStatusHistory } from './device-status-history.entity';
import { DeviceModelBinding } from '../ai-model/device-model-binding.entity';
import { AiModel } from '../ai-model/ai-model.entity';
import { PermissionsGuard } from '../../common/guards/permissions.guard';
import { RequirePermissions } from '../../common/decorators/permissions.decorator';

@Controller('devices')
@UseGuards(AuthGuard('jwt'), PermissionsGuard)
@ApiTags('Devices')
@ApiBearerAuth()
export class DeviceController {
  constructor(private readonly deviceService: DeviceService) {}

  @Get()
  @RequirePermissions('read', 'Device')
  @ApiOperation({ summary: '获取设备列表' })
  @ApiResponse({ status: 200, description: '获取成功' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  async findAll(
    @Query('page') page = 1,
    @Query('pageSize') pageSize = 20,
    @Query('projectId') projectId?: string,
    @Query('status') status?: DeviceStatus,
    @Query('search') search?: string,
    @Req() req?: Request & { user: { sub: string } },
  ): Promise<{ items: Device[]; total: number; page: number; pageSize: number }> {
    return this.deviceService.findAll({
      page,
      pageSize,
      projectId,
      status,
      search,
      userId: req?.user?.sub,
    });
  }

  @Post()
  @RequirePermissions('create', 'Device')
  @ApiOperation({ summary: '手动创建设备' })
  @ApiResponse({ status: 201, description: '创建成功' })
  @ApiResponse({ status: 400, description: '请求参数错误' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  @ApiResponse({ status: 409, description: '设备序列号已存在' })
  async create(
    @Body() createDto: CreateDeviceDto,
    @Req() req: Request & { user: { sub: string } },
  ): Promise<Device> {
    const requestInfo = {
      ipAddress: this.extractIpAddress(req),
      userAgent: req.headers['user-agent'],
    };
    return this.deviceService.create(createDto, req.user.sub, requestInfo);
  }

  @Get(':id')
  @RequirePermissions('read', 'Device')
  @ApiOperation({ summary: '获取设备详情' })
  @ApiResponse({ status: 200, description: '获取成功' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  @ApiResponse({ status: 404, description: '设备不存在' })
  async findOne(@Param('id', ParseUUIDPipe) id: string): Promise<Device> {
    return this.deviceService.findOne(id);
  }

  @Get(':id/status-history')
  @RequirePermissions('read', 'Device')
  @ApiOperation({ summary: '获取设备状态变更历史' })
  @ApiResponse({ status: 200, description: '获取成功' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  @ApiResponse({ status: 404, description: '设备不存在' })
  async getStatusHistory(
    @Param('id', ParseUUIDPipe) id: string,
    @Query('startDate') startDate?: string,
    @Query('endDate') endDate?: string,
    @Query('page') page = 1,
    @Query('pageSize') pageSize = 20,
  ): Promise<{ items: DeviceStatusHistory[]; total: number }> {
    return this.deviceService.getStatusHistory(id, {
      startDate,
      endDate,
      page,
      pageSize,
    });
  }

  @Post(':id/activate')
  @RequirePermissions('manage', 'Device')
  @ApiOperation({ summary: '激活设备' })
  @ApiResponse({ status: 200, description: '激活指令已发送' })
  @ApiResponse({ status: 400, description: '设备状态不允许激活' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  @ApiResponse({ status: 404, description: '设备不存在' })
  async activate(
    @Param('id', ParseUUIDPipe) id: string,
    @Req() req: Request & { user: { sub: string } },
  ): Promise<{
    device: Device;
    activationCommand: {
      cmd: string;
      timestamp: string;
      server: string;
      port: number;
    };
  }> {
    const requestInfo = {
      ipAddress: this.extractIpAddress(req),
      userAgent: req.headers['user-agent'],
    };
    return this.deviceService.activate(id, req.user.sub, requestInfo);
  }

  @Post(':id/ota')
  @RequirePermissions('manage', 'Device')
  @ApiOperation({ summary: '触发设备OTA升级' })
  @ApiResponse({ status: 200, description: 'OTA升级指令已发送' })
  @ApiResponse({ status: 400, description: '设备状态不允许升级或版本相同' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  @ApiResponse({ status: 404, description: '设备不存在' })
  async triggerOtaUpgrade(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() body: { targetVersion: string },
    @Req() req: Request & { user: { sub: string } },
  ): Promise<{
    taskId: string;
    status: string;
    progress: number;
    device: Device;
    otaCommand: {
      cmd: string;
      version: string;
      url: string;
      timestamp: string;
    };
  }> {
    const requestInfo = {
      ipAddress: this.extractIpAddress(req),
      userAgent: req.headers['user-agent'],
    };
    return this.deviceService.triggerOtaUpgrade(id, body.targetVersion, req.user.sub, requestInfo);
  }

  @Put(':id/config')
  @RequirePermissions('manage', 'Device')
  @ApiOperation({ summary: '更新设备配置' })
  @ApiResponse({ status: 200, description: '配置更新成功' })
  @ApiResponse({ status: 400, description: '设备状态不允许或参数错误' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  @ApiResponse({ status: 404, description: '设备不存在' })
  async updateConfig(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() configDto: UpdateDeviceConfigDto,
    @Req() req: Request & { user: { sub: string } },
  ): Promise<{
    device: Device;
    configCommand: {
      cmd: string;
      timestamp: string;
      [key: string]: any;
    };
  }> {
    const requestInfo = {
      ipAddress: this.extractIpAddress(req),
      userAgent: req.headers['user-agent'],
    };
    return this.deviceService.updateConfig(id, configDto, req.user.sub, requestInfo);
  }

  @Post('scan-register')
  @RequirePermissions('create', 'Device')
  @ApiOperation({ summary: '扫码注册设备' })
  @ApiResponse({ status: 200, description: '扫码成功，返回设备信息' })
  @ApiResponse({ status: 201, description: '新设备注册成功' })
  @ApiResponse({ status: 400, description: '无效的二维码格式' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  async scanRegister(
    @Body() scanDto: ScanRegisterDto,
    @Req() req: Request & { user: { sub: string } },
  ): Promise<{
    isNew: boolean;
    device: Device;
    preview: {
      deviceType: { code: string; name: string; description: string } | null;
      recommendedProject: string | null;
    };
  }> {
    const requestInfo = {
      ipAddress: this.extractIpAddress(req),
      userAgent: req.headers['user-agent'],
    };
    return this.deviceService.scanRegister(scanDto, req.user.sub, requestInfo);
  }

  @Put(':id/project')
  @RequirePermissions('manage', 'Device')
  @ApiOperation({ summary: '分配设备到项目' })
  @ApiResponse({ status: 200, description: '分配成功' })
  @ApiResponse({ status: 400, description: '请求参数错误' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  @ApiResponse({ status: 404, description: '设备或项目不存在' })
  async assignProject(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() assignDto: AssignProjectDto,
    @Req() req: Request & { user: { sub: string } },
  ): Promise<Device> {
    const requestInfo = {
      ipAddress: this.extractIpAddress(req),
      userAgent: req.headers['user-agent'],
    };
    return this.deviceService.assignProject(id, assignDto, req.user.sub, requestInfo);
  }

  @Post('batch-assign-project')
  @RequirePermissions('manage', 'Device')
  @ApiOperation({ summary: '批量分配设备到项目' })
  @ApiResponse({ status: 200, description: '批量分配成功' })
  @ApiResponse({ status: 400, description: '请求参数错误' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  async batchAssignProject(
    @Body() batchDto: BatchAssignProjectDto,
    @Req() req: Request & { user: { sub: string } },
  ): Promise<{ success: number; failed: string[] }> {
    const requestInfo = {
      ipAddress: this.extractIpAddress(req),
      userAgent: req.headers['user-agent'],
    };
    return this.deviceService.batchAssignProject(batchDto, req.user.sub, requestInfo);
  }

  @Post('batch-config')
  @RequirePermissions('manage', 'Device')
  @ApiOperation({ summary: '批量更新设备配置' })
  @ApiResponse({ status: 200, description: '批量配置成功' })
  @ApiResponse({ status: 400, description: '设备类型不一致或参数错误' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  async batchUpdateConfig(
    @Body() batchDto: BatchUpdateConfigDto,
    @Req() req: Request & { user: { sub: string } },
  ): Promise<{
    taskId: string;
    status: string;
    total: number;
    success: number;
    failed: number;
    errors: Array<{ deviceId: string; reason: string }>;
  }> {
    const requestInfo = {
      ipAddress: this.extractIpAddress(req),
      userAgent: req.headers['user-agent'],
    };
    return this.deviceService.batchUpdateConfig(batchDto, req.user.sub, requestInfo);
  }

  @Post(':id/models')
  @RequirePermissions('manage', 'Device')
  @ApiOperation({ summary: '绑定AI模型到设备' })
  @ApiResponse({ status: 200, description: '绑定成功' })
  @ApiResponse({ status: 400, description: '设备状态不允许或模型不兼容' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  @ApiResponse({ status: 404, description: '设备或模型不存在' })
  async bindModels(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() bindDto: BindModelsDto,
    @Req() req: Request & { user: { sub: string } },
  ): Promise<{
    bindings: DeviceModelBinding[];
    mqttCommand: { cmd: string; models: any[]; timestamp: string };
  }> {
    const requestInfo = {
      ipAddress: this.extractIpAddress(req),
      userAgent: req.headers['user-agent'],
    };
    return this.deviceService.bindModels(id, bindDto, req.user.sub, requestInfo);
  }

  @Delete(':id/models/:modelId')
  @RequirePermissions('manage', 'Device')
  @ApiOperation({ summary: '解除设备与模型的绑定' })
  @ApiResponse({ status: 200, description: '解除成功' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  @ApiResponse({ status: 404, description: '设备或绑定不存在' })
  async unbindModel(
    @Param('id', ParseUUIDPipe) id: string,
    @Param('modelId', ParseUUIDPipe) modelId: string,
    @Req() req: Request & { user: { sub: string } },
  ): Promise<{
    success: boolean;
    mqttCommand: { cmd: string; modelId: string; timestamp: string };
  }> {
    const requestInfo = {
      ipAddress: this.extractIpAddress(req),
      userAgent: req.headers['user-agent'],
    };
    return this.deviceService.unbindModel(id, modelId, req.user.sub, requestInfo);
  }

  @Get(':id/models')
  @RequirePermissions('read', 'Device')
  @ApiOperation({ summary: '获取设备绑定的模型列表' })
  @ApiResponse({ status: 200, description: '获取成功' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  @ApiResponse({ status: 404, description: '设备不存在' })
  async getBoundModels(
    @Param('id', ParseUUIDPipe) id: string,
    @Query('page') page = 1,
    @Query('pageSize') pageSize = 20,
  ): Promise<{
    items: (DeviceModelBinding & { model?: AiModel })[];
    total: number;
    page: number;
    pageSize: number;
  }> {
    return this.deviceService.getBoundModels(id, { page, pageSize });
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
