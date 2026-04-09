import {
  Controller,
  Get,
  Post,
  Body,
  Param,
  Query,
  ParseUUIDPipe,
  UseGuards,
} from '@nestjs/common';
import { AuthGuard } from '@nestjs/passport';
import {
  ApiTags,
  ApiOperation,
  ApiBearerAuth,
  ApiResponse,
} from '@nestjs/swagger';
import { TelemetryService } from './telemetry.service';
import { BackupType, BackupStatus } from './telemetry.entity';
import { PermissionsGuard } from '../../common/guards/permissions.guard';
import { RequirePermissions } from '../../common/decorators/permissions.decorator';

@Controller()
@UseGuards(AuthGuard('jwt'), PermissionsGuard)
@ApiTags('Telemetry')
@ApiBearerAuth()
export class TelemetryController {
  constructor(private readonly telemetryService: TelemetryService) {}

  // ==================== Story 6.3/6.4: 遥测数据查询 ====================

  @Get('devices/:deviceId/telemetry')
  @RequirePermissions('read', 'Device')
  @ApiOperation({ summary: '查询设备历史遥测数据' })
  @ApiResponse({ status: 200, description: '查询成功' })
  async getTelemetry(
    @Param('deviceId', ParseUUIDPipe) deviceId: string,
    @Query('page') page = 1,
    @Query('pageSize') pageSize = 50,
    @Query('startTime') startTime?: string,
    @Query('endTime') endTime?: string,
  ) {
    return this.telemetryService.findTelemetry({
      deviceId,
      page,
      pageSize,
      startTime,
      endTime,
    });
  }

  // ==================== Story 6.5: 图表数据 ====================

  @Get('devices/:deviceId/telemetry/chart')
  @RequirePermissions('read', 'Device')
  @ApiOperation({ summary: '获取遥测图表数据' })
  @ApiResponse({ status: 200, description: '获取成功' })
  async getChart(
    @Param('deviceId', ParseUUIDPipe) deviceId: string,
    @Query('metrics') metricsStr = 'level,flow',
    @Query('startTime') startTime?: string,
    @Query('endTime') endTime?: string,
    @Query('interval') interval?: 'raw' | 'hour' | 'day',
  ) {
    const metrics = metricsStr.split(',').map((m) => m.trim());
    return this.telemetryService.getChartData({
      deviceId,
      metrics,
      startTime,
      endTime,
      interval,
    });
  }

  // ==================== Story 6.2: 数据写入 ====================

  @Post('devices/:deviceId/telemetry')
  @RequirePermissions('manage', 'Device')
  @ApiOperation({ summary: '写入设备遥测数据' })
  @ApiResponse({ status: 201, description: '写入成功' })
  async writeTelemetry(
    @Param('deviceId', ParseUUIDPipe) deviceId: string,
    @Body() body: { timestamp?: string; metrics: Record<string, any> },
  ) {
    return this.telemetryService.writeTelemetry({
      deviceId,
      timestamp: body.timestamp ? new Date(body.timestamp) : new Date(),
      metrics: body.metrics,
    });
  }

  // ==================== Story 6.6: 备份配置 ====================

  @Post('backup/configs')
  @RequirePermissions('manage', 'Device')
  @ApiOperation({ summary: '创建备份配置' })
  @ApiResponse({ status: 201, description: '创建成功' })
  async createBackupConfig(
    @Body() body: { type: BackupType; schedule: string; retentionDays?: number; storagePath?: string },
  ) {
    return this.telemetryService.createBackupConfig(body);
  }

  @Get('backup/configs')
  @RequirePermissions('read', 'Device')
  @ApiOperation({ summary: '获取备份配置列表' })
  async getBackupConfigs() {
    return this.telemetryService.getBackupConfigs();
  }

  // ==================== Story 6.7: 备份执行 ====================

  @Post('backup/configs/:configId/execute')
  @RequirePermissions('manage', 'Device')
  @ApiOperation({ summary: '执行备份' })
  async executeBackup(@Param('configId', ParseUUIDPipe) configId: string) {
    return this.telemetryService.executeBackup(configId);
  }

  @Get('backup/logs')
  @RequirePermissions('read', 'Device')
  @ApiOperation({ summary: '获取备份日志' })
  async getBackupLogs(
    @Query('type') type?: BackupType,
    @Query('status') status?: BackupStatus,
  ) {
    return this.telemetryService.getBackupLogs({ type, status });
  }

  // ==================== Story 6.8: 备份恢复 ====================

  @Post('backup/:backupId/restore')
  @RequirePermissions('manage', 'Device')
  @ApiOperation({ summary: '恢复备份' })
  async restoreBackup(@Param('backupId', ParseUUIDPipe) backupId: string) {
    return this.telemetryService.restoreBackup(backupId);
  }
}
