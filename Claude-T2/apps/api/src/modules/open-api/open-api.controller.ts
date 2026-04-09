import {
  Controller,
  Get,
  Post,
  Put,
  Delete,
  Body,
  Param,
  Query,
  UseGuards,
  Req,
  ParseUUIDPipe,
} from '@nestjs/common';
import { AuthGuard } from '@nestjs/passport';
import {
  ApiTags,
  ApiOperation,
  ApiBearerAuth,
  ApiResponse,
} from '@nestjs/swagger';
import { OpenApiService } from './open-api.service';
import { WebhookEventType } from './open-api.entity';
import { PermissionsGuard } from '../../common/guards/permissions.guard';
import { RequirePermissions } from '../../common/decorators/permissions.decorator';

// ==================== Story 7.1-7.3: API Key 管理 ====================

@Controller('api-keys')
@UseGuards(AuthGuard('jwt'), PermissionsGuard)
@ApiTags('API Keys')
@ApiBearerAuth()
export class ApiKeyController {
  constructor(private readonly openApiService: OpenApiService) {}

  @Post()
  @RequirePermissions('manage', 'Project')
  @ApiOperation({ summary: '创建 API Key' })
  async createApiKey(
    @Req() req: any,
    @Body() body: {
      name: string;
      description?: string;
      projectId?: string;
      permissions?: string[];
      expiresInDays?: number;
    },
  ) {
    return this.openApiService.createApiKey({
      ...body,
      userId: req.user.sub,
    });
  }

  @Get()
  @RequirePermissions('read', 'Project')
  @ApiOperation({ summary: '获取 API Key 列表' })
  async listApiKeys(@Req() req: any) {
    return this.openApiService.listApiKeys(req.user.sub);
  }

  @Get(':id')
  @RequirePermissions('read', 'Project')
  @ApiOperation({ summary: '获取 API Key 详情' })
  async getApiKey(@Param('id', ParseUUIDPipe) id: string) {
    return this.openApiService.getApiKey(id);
  }

  @Post(':id/revoke')
  @RequirePermissions('manage', 'Project')
  @ApiOperation({ summary: '吊销 API Key' })
  async revokeApiKey(
    @Param('id', ParseUUIDPipe) id: string,
    @Req() req: any,
  ) {
    return this.openApiService.revokeApiKey(id, req.user.sub);
  }
}

// ==================== Story 7.6-7.8: Webhook 管理 ====================

@Controller('webhooks')
@UseGuards(AuthGuard('jwt'), PermissionsGuard)
@ApiTags('Webhooks')
@ApiBearerAuth()
export class WebhookController {
  constructor(private readonly openApiService: OpenApiService) {}

  @Post()
  @RequirePermissions('manage', 'Project')
  @ApiOperation({ summary: '创建 Webhook' })
  async createWebhook(
    @Req() req: any,
    @Body() body: {
      name: string;
      url: string;
      events: WebhookEventType[];
      projectId?: string;
      headers?: Record<string, string>;
    },
  ) {
    return this.openApiService.createWebhook({
      ...body,
      userId: req.user.sub,
    });
  }

  @Get()
  @RequirePermissions('read', 'Project')
  @ApiOperation({ summary: '获取 Webhook 列表' })
  async listWebhooks(@Req() req: any) {
    return this.openApiService.listWebhooks(req.user.sub);
  }

  @Get(':id')
  @RequirePermissions('read', 'Project')
  @ApiOperation({ summary: '获取 Webhook 详情' })
  async getWebhook(@Param('id', ParseUUIDPipe) id: string) {
    return this.openApiService.getWebhook(id);
  }

  @Put(':id')
  @RequirePermissions('manage', 'Project')
  @ApiOperation({ summary: '更新 Webhook' })
  async updateWebhook(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() body: Partial<{ name: string; url: string; events: WebhookEventType[]; enabled: boolean }>,
  ) {
    return this.openApiService.updateWebhook(id, body);
  }

  @Delete(':id')
  @RequirePermissions('manage', 'Project')
  @ApiOperation({ summary: '删除 Webhook' })
  async deleteWebhook(@Param('id', ParseUUIDPipe) id: string) {
    return this.openApiService.deleteWebhook(id);
  }

  @Get(':id/deliveries')
  @RequirePermissions('read', 'Project')
  @ApiOperation({ summary: '获取 Webhook 投递记录' })
  async getDeliveries(
    @Param('id', ParseUUIDPipe) id: string,
    @Query('limit') limit?: number,
  ) {
    return this.openApiService.getWebhookDeliveries(id, { limit });
  }

  @Post('deliveries/:deliveryId/retry')
  @RequirePermissions('manage', 'Project')
  @ApiOperation({ summary: '重试 Webhook 投递' })
  async retryDelivery(@Param('deliveryId', ParseUUIDPipe) deliveryId: string) {
    return this.openApiService.retryDelivery(deliveryId);
  }
}

// ==================== Story 7.9-7.12: Open API 端点 ====================

@Controller('open-api/v1')
@ApiTags('Open API')
export class OpenApiController {
  constructor(private readonly openApiService: OpenApiService) {}

  @Get('devices')
  @ApiOperation({ summary: '获取设备列表（Open API）' })
  async getDevices(
    @Req() req: any,
    @Query('page') page = 1,
    @Query('pageSize') pageSize = 50,
    @Query('status') status?: string,
  ) {
    return this.openApiService.getOpenDevices(req.apiKey, { page, pageSize, status });
  }

  @Get('alerts')
  @ApiOperation({ summary: '获取告警列表（Open API）' })
  async getAlerts(
    @Req() req: any,
    @Query('page') page = 1,
    @Query('pageSize') pageSize = 50,
    @Query('level') level?: string,
    @Query('status') status?: string,
  ) {
    return this.openApiService.getOpenAlerts(req.apiKey, { page, pageSize, level, status });
  }

  @Get('devices/:deviceId/telemetry')
  @ApiOperation({ summary: '获取遥测数据（Open API）' })
  async getTelemetry(
    @Req() req: any,
    @Param('deviceId') deviceId: string,
    @Query('startTime') startTime?: string,
    @Query('endTime') endTime?: string,
    @Query('metrics') metricsStr?: string,
  ) {
    const metrics = metricsStr ? metricsStr.split(',').map((m) => m.trim()) : undefined;
    return this.openApiService.getOpenTelemetry(req.apiKey, deviceId, { startTime, endTime, metrics });
  }
}

// ==================== Story 7.13-7.15: API 调用日志 ====================

@Controller('api-logs')
@UseGuards(AuthGuard('jwt'), PermissionsGuard)
@ApiTags('API Logs')
@ApiBearerAuth()
export class ApiLogController {
  constructor(private readonly openApiService: OpenApiService) {}

  @Get('stats')
  @RequirePermissions('read', 'Project')
  @ApiOperation({ summary: '获取 API 调用统计' })
  async getCallStats(
    @Query('apiKeyId') apiKeyId?: string,
    @Query('startDate') startDate?: string,
    @Query('endDate') endDate?: string,
  ) {
    return this.openApiService.getCallStats({ apiKeyId, startDate, endDate });
  }

  @Get()
  @RequirePermissions('read', 'Project')
  @ApiOperation({ summary: '获取 API 调用日志' })
  async getCallLogs(
    @Query('page') page = 1,
    @Query('pageSize') pageSize = 50,
    @Query('apiKeyId') apiKeyId?: string,
    @Query('path') path?: string,
  ) {
    return this.openApiService.getCallLogs({ page, pageSize, apiKeyId, path });
  }
}
