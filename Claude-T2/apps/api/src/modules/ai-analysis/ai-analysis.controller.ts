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
import { AiAnalysisService } from './ai-analysis.service';
import { AiAnalysisResult, AnalysisType, AnalysisResult } from './ai-analysis-result.entity';
import { PermissionsGuard } from '../../common/guards/permissions.guard';
import { RequirePermissions } from '../../common/decorators/permissions.decorator';

@Controller('devices/:deviceId/ai-results')
@UseGuards(AuthGuard('jwt'), PermissionsGuard)
@ApiTags('AI Analysis')
@ApiBearerAuth()
export class AiAnalysisController {
  constructor(private readonly aiAnalysisService: AiAnalysisService) {}

  @Get('latest')
  @RequirePermissions('read', 'Device')
  @ApiOperation({ summary: '获取设备最新的AI分析结果' })
  @ApiResponse({ status: 200, description: '获取成功' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  @ApiResponse({ status: 404, description: '设备不存在' })
  async getLatestResults(
    @Param('deviceId', ParseUUIDPipe) deviceId: string,
    @Query('type') type?: AnalysisType,
    @Query('limit') limit = 20,
  ): Promise<AiAnalysisResult[]> {
    return this.aiAnalysisService.getLatestResults(deviceId, {
      analysisType: type,
      limit: Math.min(limit, 100),
    });
  }

  @Get('status')
  @RequirePermissions('read', 'Device')
  @ApiOperation({ summary: '获取设备各类型分析的最新状态' })
  @ApiResponse({ status: 200, description: '获取成功' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  async getLatestStatusByType(
    @Param('deviceId', ParseUUIDPipe) deviceId: string,
  ): Promise<Record<AnalysisType, AiAnalysisResult | null>> {
    return this.aiAnalysisService.getLatestStatusByType(deviceId);
  }

  @Get('history')
  @RequirePermissions('read', 'Device')
  @ApiOperation({ summary: '获取设备AI分析历史记录' })
  @ApiResponse({ status: 200, description: '获取成功' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  async getHistory(
    @Param('deviceId', ParseUUIDPipe) deviceId: string,
    @Query('startTime') startTime?: string,
    @Query('endTime') endTime?: string,
    @Query('type') type?: AnalysisType,
    @Query('result') result?: AnalysisResult,
    @Query('page') page = 1,
    @Query('pageSize') pageSize = 50,
  ): Promise<{
    items: AiAnalysisResult[];
    total: number;
    page: number;
    pageSize: number;
  }> {
    return this.aiAnalysisService.getHistory(deviceId, {
      startTime: startTime ? new Date(startTime) : undefined,
      endTime: endTime ? new Date(endTime) : undefined,
      analysisType: type,
      analysisResult: result,
      page,
      pageSize: Math.min(pageSize, 200),
    });
  }

  @Get('statistics')
  @RequirePermissions('read', 'Device')
  @ApiOperation({ summary: '获取设备AI分析统计' })
  @ApiResponse({ status: 200, description: '获取成功' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  async getStatistics(
    @Param('deviceId', ParseUUIDPipe) deviceId: string,
    @Query('startTime') startTime?: string,
    @Query('endTime') endTime?: string,
  ): Promise<{
    total: number;
    byType: Record<AnalysisType, { total: number; normal: number; abnormal: number; warning: number }>;
    avgConfidence: number;
  }> {
    return this.aiAnalysisService.getStatistics(deviceId, {
      startTime: startTime ? new Date(startTime) : undefined,
      endTime: endTime ? new Date(endTime) : undefined,
    });
  }

  @Get(':id')
  @RequirePermissions('read', 'Device')
  @ApiOperation({ summary: '获取AI分析结果详情' })
  @ApiResponse({ status: 200, description: '获取成功' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  @ApiResponse({ status: 404, description: '结果不存在' })
  async findOne(
    @Param('deviceId', ParseUUIDPipe) deviceId: string,
    @Param('id', ParseUUIDPipe) id: string,
  ): Promise<AiAnalysisResult> {
    return this.aiAnalysisService.findOne(id);
  }

  @Post()
  @RequirePermissions('manage', 'Device')
  @ApiOperation({ summary: '上报AI分析结果（边缘设备调用）' })
  @ApiResponse({ status: 201, description: '上报成功' })
  @ApiResponse({ status: 400, description: '请求参数错误' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  async createResult(
    @Param('deviceId', ParseUUIDPipe) deviceId: string,
    @Body() body: {
      modelId: string;
      analysisType: AnalysisType;
      analysisResult: AnalysisResult;
      confidence: number;
      details?: Record<string, any>;
      confidenceFactors?: Record<string, any>;
      rawData?: Record<string, any>;
      timestamp: string;
    },
  ): Promise<AiAnalysisResult> {
    return this.aiAnalysisService.createResult({
      deviceId,
      modelId: body.modelId,
      analysisType: body.analysisType,
      analysisResult: body.analysisResult,
      confidence: body.confidence,
      details: body.details,
      confidenceFactors: body.confidenceFactors,
      rawData: body.rawData,
      timestamp: new Date(body.timestamp),
    });
  }
}
