import {
  Controller,
  Get,
  Query,
  Res,
  UseGuards,
  HttpCode,
  HttpStatus,
} from '@nestjs/common';
import { AuthGuard } from '@nestjs/passport';
import { Response } from 'express';
import {
  ApiTags,
  ApiOperation,
  ApiResponse,
  ApiBearerAuth,
  ApiProduces,
} from '@nestjs/swagger';
import { AuditLogService } from './audit-log.service';
import { QueryAuditLogsDto } from './dto/query-audit-logs.dto';
import { AuditLogListResponseDto } from './dto/audit-log-response.dto';
import { PermissionsGuard } from '../../common/guards/permissions.guard';
import { RequirePermissions } from '../../common/decorators/permissions.decorator';

@Controller('audit-logs')
@ApiTags('AuditLogs')
@ApiBearerAuth()
@UseGuards(AuthGuard('jwt'), PermissionsGuard)
export class AuditLogController {
  constructor(private readonly auditLogService: AuditLogService) {}

  @Get()
  @HttpCode(HttpStatus.OK)
  @RequirePermissions('read', 'AuditLog')
  @ApiOperation({ summary: '查询审计日志' })
  @ApiResponse({
    status: 200,
    description: '查询成功',
    type: AuditLogListResponseDto,
  })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '无权限' })
  async findAll(@Query() query: QueryAuditLogsDto): Promise<AuditLogListResponseDto> {
    return this.auditLogService.findAll({
      page: query.page,
      pageSize: query.pageSize,
      action: query.action,
      targetType: query.targetType,
      targetId: query.targetId,
      operatorId: query.userId,
      startDate: query.startTime ? new Date(query.startTime) : undefined,
      endDate: query.endTime ? new Date(query.endTime) : undefined,
    });
  }

  @Get('export')
  @HttpCode(HttpStatus.OK)
  @RequirePermissions('read', 'AuditLog')
  @ApiOperation({ summary: '导出审计日志为CSV' })
  @ApiProduces('text/csv')
  @ApiResponse({
    status: 200,
    description: '导出成功',
    content: { 'text/csv': {} },
  })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '无权限' })
  async export(@Query() query: QueryAuditLogsDto, @Res() res: Response): Promise<void> {
    // 获取数据（限制最大10000条）
    const result = await this.auditLogService.findAll({
      page: 1,
      pageSize: 10000,
      action: query.action,
      targetType: query.targetType,
      targetId: query.targetId,
      operatorId: query.userId,
      startDate: query.startTime ? new Date(query.startTime) : undefined,
      endDate: query.endTime ? new Date(query.endTime) : undefined,
    });

    // 生成CSV
    const csvHeaders = '时间,用户名,操作类型,目标类型,目标ID,IP地址,描述';
    const csvRows = result.items.map((log) => {
      const username = log.operator?.username || log.operatorId || 'system';
      const targetId = log.targetId || '';
      const ipAddress = log.ipAddress || '';
      const description = (log.description || '').replace(/"/g, '""');

      return `${this.formatDate(log.createdAt)},"${username}","${log.action}","${log.targetType}","${targetId}","${ipAddress}","${description}"`;
    });

    const csv = [csvHeaders, ...csvRows].join('\n');

    // 设置响应头
    const filename = `audit-logs-${new Date().toISOString().split('T')[0]}.csv`;
    res.setHeader('Content-Type', 'text/csv; charset=utf-8');
    res.setHeader('Content-Disposition', `attachment; filename="${filename}"`);

    // 添加BOM以支持Excel正确显示中文
    res.send('\uFEFF' + csv);
  }

  /**
   * 格式化日期为可读字符串
   */
  private formatDate(date: Date): string {
    return date.toISOString().replace('T', ' ').substring(0, 19);
  }
}
