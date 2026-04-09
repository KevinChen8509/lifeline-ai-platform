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
} from '@nestjs/swagger';
import { DashboardService } from './dashboard.service';
import { ReportType, ScheduleStatus } from './dashboard.entity';
import { PermissionsGuard } from '../../common/guards/permissions.guard';
import { RequirePermissions } from '../../common/decorators/permissions.decorator';

// ==================== Story 8.1-8.3: 项目概览看板 ====================

@Controller('projects/:projectId/dashboard')
@UseGuards(AuthGuard('jwt'), PermissionsGuard)
@ApiTags('Dashboard')
@ApiBearerAuth()
export class DashboardController {
  constructor(private readonly dashboardService: DashboardService) {}

  @Get('device-stats')
  @RequirePermissions('read', 'Device')
  @ApiOperation({ summary: '设备在线率统计' })
  async getDeviceStats(@Param('projectId', ParseUUIDPipe) projectId: string) {
    return this.dashboardService.getDeviceStats(projectId);
  }

  @Get('alert-stats')
  @RequirePermissions('read', 'Alert')
  @ApiOperation({ summary: '预警统计' })
  async getAlertStats(@Param('projectId', ParseUUIDPipe) projectId: string) {
    return this.dashboardService.getAlertStats(projectId);
  }

  @Get('kpi')
  @RequirePermissions('read', 'Project')
  @ApiOperation({ summary: '关键指标' })
  async getKpi(@Param('projectId', ParseUUIDPipe) projectId: string) {
    return this.dashboardService.getKpi(projectId);
  }
}

// ==================== Story 8.4-8.5: 统计分析 ====================

@Controller('statistics')
@UseGuards(AuthGuard('jwt'), PermissionsGuard)
@ApiTags('Statistics')
@ApiBearerAuth()
export class StatisticsController {
  constructor(private readonly dashboardService: DashboardService) {}

  @Get('alert-type-distribution')
  @RequirePermissions('read', 'Alert')
  @ApiOperation({ summary: '预警类型分布' })
  async getAlertTypeDistribution(
    @Query('projectId') projectId?: string,
    @Query('startDate') startDate?: string,
    @Query('endDate') endDate?: string,
  ) {
    return this.dashboardService.getAlertTypeDistribution({ projectId, startDate, endDate });
  }

  @Get('alert-handling-efficiency')
  @RequirePermissions('read', 'Alert')
  @ApiOperation({ summary: '预警处置效率' })
  async getAlertHandlingEfficiency(
    @Query('projectId') projectId?: string,
    @Query('startDate') startDate?: string,
    @Query('endDate') endDate?: string,
  ) {
    return this.dashboardService.getAlertHandlingEfficiency({ projectId, startDate, endDate });
  }
}

// ==================== Story 8.6-8.8: 报告模板与生成 ====================

@Controller('report-templates')
@UseGuards(AuthGuard('jwt'), PermissionsGuard)
@ApiTags('Report Templates')
@ApiBearerAuth()
export class ReportTemplateController {
  constructor(private readonly dashboardService: DashboardService) {}

  @Post()
  @RequirePermissions('manage', 'Project')
  @ApiOperation({ summary: '创建报告模板' })
  async createTemplate(
    @Body() body: { name: string; type: ReportType; sections: any[]; projectId?: string },
  ) {
    return this.dashboardService.createTemplate(body);
  }

  @Get()
  @RequirePermissions('read', 'Project')
  @ApiOperation({ summary: '获取报告模板列表' })
  async listTemplates(@Query('projectId') projectId?: string) {
    return this.dashboardService.listTemplates(projectId);
  }

  @Get(':id')
  @RequirePermissions('read', 'Project')
  @ApiOperation({ summary: '获取报告模板详情' })
  async getTemplate(@Param('id', ParseUUIDPipe) id: string) {
    return this.dashboardService.getTemplate(id);
  }

  @Put(':id')
  @RequirePermissions('manage', 'Project')
  @ApiOperation({ summary: '更新报告模板' })
  async updateTemplate(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() body: Partial<{ name: string; sections: any[] }>,
  ) {
    return this.dashboardService.updateTemplate(id, body);
  }

  @Delete(':id')
  @RequirePermissions('manage', 'Project')
  @ApiOperation({ summary: '删除报告模板' })
  async deleteTemplate(@Param('id', ParseUUIDPipe) id: string) {
    return this.dashboardService.deleteTemplate(id);
  }
}

@Controller('reports')
@UseGuards(AuthGuard('jwt'), PermissionsGuard)
@ApiTags('Reports')
@ApiBearerAuth()
export class ReportController {
  constructor(private readonly dashboardService: DashboardService) {}

  @Post('generate')
  @RequirePermissions('manage', 'Project')
  @ApiOperation({ summary: '生成运营报告' })
  async generateReport(
    @Req() req: any,
    @Body() body: { type: ReportType; projectId: string; startDate: string; endDate: string; templateId: string },
  ) {
    return this.dashboardService.generateReport({ ...body, userId: req.user.sub });
  }

  @Get()
  @RequirePermissions('read', 'Project')
  @ApiOperation({ summary: '获取报告列表' })
  async listReports(
    @Query('projectId') projectId?: string,
    @Query('type') type?: ReportType,
    @Query('page') page = 1,
    @Query('pageSize') pageSize = 20,
  ) {
    return this.dashboardService.listReports({ projectId, type, page, pageSize });
  }

  @Get(':id')
  @RequirePermissions('read', 'Project')
  @ApiOperation({ summary: '获取报告详情' })
  async getReport(@Param('id', ParseUUIDPipe) id: string) {
    return this.dashboardService.getReport(id);
  }

  @Delete(':id')
  @RequirePermissions('manage', 'Project')
  @ApiOperation({ summary: '删除报告' })
  async deleteReport(@Param('id', ParseUUIDPipe) id: string) {
    return this.dashboardService.deleteReport(id);
  }

  @Post(':id/export-pdf')
  @RequirePermissions('read', 'Project')
  @ApiOperation({ summary: '导出PDF报告' })
  async exportPdf(@Param('id', ParseUUIDPipe) id: string) {
    return this.dashboardService.exportPdf(id);
  }
}

// ==================== Story 8.9-8.10: 定时报告 ====================

@Controller('scheduled-reports')
@UseGuards(AuthGuard('jwt'), PermissionsGuard)
@ApiTags('Scheduled Reports')
@ApiBearerAuth()
export class ScheduledReportController {
  constructor(private readonly dashboardService: DashboardService) {}

  @Post()
  @RequirePermissions('manage', 'Project')
  @ApiOperation({ summary: '创建定时报告' })
  async createScheduledReport(
    @Req() req: any,
    @Body() body: { name: string; type: ReportType; projectIds: string[]; recipients: string[]; templateId: string },
  ) {
    return this.dashboardService.createScheduledReport({
      ...body,
      createdBy: req.user.sub,
    });
  }

  @Get()
  @RequirePermissions('read', 'Project')
  @ApiOperation({ summary: '获取定时报告列表' })
  async listScheduledReports() {
    return this.dashboardService.listScheduledReports();
  }

  @Put(':id')
  @RequirePermissions('manage', 'Project')
  @ApiOperation({ summary: '更新定时报告' })
  async updateScheduledReport(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() body: Partial<{ name: string; projectIds: string[]; recipients: string[]; status: ScheduleStatus }>,
  ) {
    return this.dashboardService.updateScheduledReport(id, body);
  }

  @Delete(':id')
  @RequirePermissions('manage', 'Project')
  @ApiOperation({ summary: '删除定时报告' })
  async deleteScheduledReport(@Param('id', ParseUUIDPipe) id: string) {
    return this.dashboardService.deleteScheduledReport(id);
  }

  @Post(':id/execute')
  @RequirePermissions('manage', 'Project')
  @ApiOperation({ summary: '手动执行定时报告' })
  async executeScheduledReport(@Param('id', ParseUUIDPipe) id: string) {
    return this.dashboardService.executeScheduledReport(id);
  }
}

// ==================== Story 8.11-8.12: 系统监控 ====================

@Controller('system')
@UseGuards(AuthGuard('jwt'), PermissionsGuard)
@ApiTags('System')
@ApiBearerAuth()
export class SystemController {
  constructor(private readonly dashboardService: DashboardService) {}

  @Get('status')
  @RequirePermissions('read', 'System')
  @ApiOperation({ summary: '系统运行状态' })
  async getSystemStatus() {
    return this.dashboardService.getSystemStatus();
  }

  @Get('resources')
  @RequirePermissions('read', 'System')
  @ApiOperation({ summary: '系统资源使用' })
  async getSystemResources() {
    return this.dashboardService.getSystemResources();
  }
}
