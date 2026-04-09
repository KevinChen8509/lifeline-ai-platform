import {
  Controller,
  Get,
  Post,
  Body,
  Query,
  Param,
  UseGuards,
} from '@nestjs/common';
import { AuthGuard } from '@nestjs/passport';
import {
  ApiTags,
  ApiOperation,
  ApiBearerAuth,
  ApiResponse,
} from '@nestjs/swagger';
import { MonitoringService } from './monitoring.service';
import { PermissionsGuard } from '../../common/guards/permissions.guard';
import { RequirePermissions } from '../../common/decorators/permissions.decorator';

@Controller('monitoring')
@UseGuards(AuthGuard('jwt'), PermissionsGuard)
@ApiTags('Monitoring')
@ApiBearerAuth()
export class MonitoringController {
  constructor(private readonly monitoringService: MonitoringService) {}

  // ==================== 模型状态监控 (Story 4.13) ====================

  @Get('model-status')
  @RequirePermissions('read', 'Device')
  @ApiOperation({ summary: '获取模型运行状态统计' })
  @ApiResponse({ status: 200, description: '获取成功' })
  async getModelStatus() {
    return this.monitoringService.getModelStatus();
  }

  // ==================== 模型部署进度 (Story 4.12) ====================

  @Get('deployments/:deploymentId/progress')
  @RequirePermissions('read', 'AiModel')
  @ApiOperation({ summary: '获取部署任务进度' })
  @ApiResponse({ status: 200, description: '获取成功' })
  @ApiResponse({ status: 404, description: '部署任务不存在' })
  async getDeploymentProgress(
    @Param('deploymentId') deploymentId: string,
  ) {
    return this.monitoringService.getDeploymentProgress(deploymentId);
  }

  // ==================== 批量模型绑定 (Story 4.14) ====================

  @Post('devices/batch-bind-model')
  @RequirePermissions('manage', 'Device')
  @ApiOperation({ summary: '批量绑定模型到设备' })
  @ApiResponse({ status: 201, description: '绑定成功' })
  @ApiResponse({ status: 400, description: '请求参数错误' })
  async batchBindModel(
    @Body() body: { deviceIds: string[]; modelId: string },
  ) {
    return this.monitoringService.batchBindModel(body.deviceIds, body.modelId);
  }

  // ==================== AI分析结果聚合统计 (Story 4.15) ====================

  @Get('ai-results/summary')
  @RequirePermissions('read', 'Device')
  @ApiOperation({ summary: '获取AI分析结果聚合统计' })
  @ApiResponse({ status: 200, description: '获取成功' })
  async getAiResultsSummary(
    @Query('projectId') projectId?: string,
    @Query('deviceType') deviceType?: string,
    @Query('startTime') startTime?: string,
    @Query('endTime') endTime?: string,
  ) {
    return this.monitoringService.getAiResultsSummary({
      projectId,
      deviceType,
      startTime,
      endTime,
    });
  }
}
