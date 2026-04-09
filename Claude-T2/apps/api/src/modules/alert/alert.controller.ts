import {
  Controller,
  Get,
  Post,
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
import { AlertService } from './alert.service';
import { AlertType, AlertLevel, AlertStatus } from './alert.entity';
import { PermissionsGuard } from '../../common/guards/permissions.guard';
import { RequirePermissions } from '../../common/decorators/permissions.decorator';

@Controller('alerts')
@UseGuards(AuthGuard('jwt'), PermissionsGuard)
@ApiTags('Alerts')
@ApiBearerAuth()
export class AlertController {
  constructor(private readonly alertService: AlertService) {}

  // ==================== Story 5.2: 预警列表 ====================

  @Get()
  @RequirePermissions('read', 'Device')
  @ApiOperation({ summary: '获取预警列表' })
  @ApiResponse({ status: 200, description: '获取成功' })
  async findAll(
    @Query('page') page = 1,
    @Query('pageSize') pageSize = 20,
    @Query('type') type?: AlertType,
    @Query('level') level?: AlertLevel,
    @Query('status') status?: AlertStatus,
    @Query('deviceId') deviceId?: string,
    @Query('projectId') projectId?: string,
    @Query('startTime') startTime?: string,
    @Query('endTime') endTime?: string,
    @Query('search') search?: string,
  ) {
    return this.alertService.findAll({
      page,
      pageSize,
      type,
      level,
      status,
      deviceId,
      projectId,
      startTime,
      endTime,
      search,
    });
  }

  // ==================== Story 5.3: 预警详情 ====================

  @Get(':id')
  @RequirePermissions('read', 'Device')
  @ApiOperation({ summary: '获取预警详情' })
  @ApiResponse({ status: 200, description: '获取成功' })
  @ApiResponse({ status: 404, description: '预警不存在' })
  async findOne(@Param('id', ParseUUIDPipe) id: string) {
    return this.alertService.findOne(id);
  }

  // ==================== Story 5.12: 处置闭环时间线 ====================

  @Get(':id/timeline')
  @RequirePermissions('read', 'Device')
  @ApiOperation({ summary: '获取预警处置时间线' })
  @ApiResponse({ status: 200, description: '获取成功' })
  async getTimeline(@Param('id', ParseUUIDPipe) id: string) {
    return this.alertService.getTimeline(id);
  }

  // ==================== Story 5.14: 预警统计 ====================

  @Get('stats/summary')
  @RequirePermissions('read', 'Device')
  @ApiOperation({ summary: '获取预警统计' })
  @ApiResponse({ status: 200, description: '获取成功' })
  async getStats(@Query('projectId') projectId?: string) {
    return this.alertService.getStats(projectId);
  }

  // ==================== Story 5.5: 预警确认 ====================

  @Post(':id/acknowledge')
  @RequirePermissions('manage', 'Device')
  @ApiOperation({ summary: '确认预警' })
  @ApiResponse({ status: 200, description: '确认成功' })
  @ApiResponse({ status: 400, description: '状态不允许确认' })
  async acknowledge(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() body: { note?: string },
    @Req() req: Request & { user: { sub: string } },
  ) {
    return this.alertService.acknowledge(id, req.user.sub, body.note);
  }

  // ==================== Story 5.6: 预警处置 ====================

  @Post(':id/process')
  @RequirePermissions('manage', 'Device')
  @ApiOperation({ summary: '开始处置预警' })
  @ApiResponse({ status: 200, description: '处置成功' })
  @ApiResponse({ status: 400, description: '状态不允许处置' })
  async process(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() body: { description: string },
    @Req() req: Request & { user: { sub: string } },
  ) {
    return this.alertService.process(id, req.user.sub, body.description);
  }

  // ==================== Story 5.7: 预警关闭 ====================

  @Post(':id/close')
  @RequirePermissions('manage', 'Device')
  @ApiOperation({ summary: '关闭预警' })
  @ApiResponse({ status: 200, description: '关闭成功' })
  @ApiResponse({ status: 400, description: '状态不允许关闭' })
  async close(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() body: { resolution: string; rootCause?: string },
    @Req() req: Request & { user: { sub: string } },
  ) {
    return this.alertService.close(id, req.user.sub, body.resolution, body.rootCause);
  }

  // ==================== Story 5.8: 工单生成 ====================

  @Post(':id/work-order')
  @RequirePermissions('manage', 'Device')
  @ApiOperation({ summary: '生成处置工单' })
  @ApiResponse({ status: 201, description: '创建成功' })
  async createWorkOrder(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() body: {
      title: string;
      description?: string;
      assigneeId?: string;
      priority?: AlertLevel;
      dueDate?: string;
    },
    @Req() req: Request & { user: { sub: string } },
  ) {
    return this.alertService.createWorkOrder(id, body, req.user.sub);
  }
}
