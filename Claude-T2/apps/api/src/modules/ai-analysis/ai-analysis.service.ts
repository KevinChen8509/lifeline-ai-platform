import { Injectable, Logger, NotFoundException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository, Between, In } from 'typeorm';
import { AiAnalysisResult, AnalysisType, AnalysisResult } from './ai-analysis-result.entity';

@Injectable()
export class AiAnalysisService {
  private readonly logger = new Logger(AiAnalysisService.name);

  constructor(
    @InjectRepository(AiAnalysisResult)
    private readonly analysisResultRepository: Repository<AiAnalysisResult>,
  ) {}

  /**
   * 创建分析结果（边缘设备上报）
   */
  async createResult(data: {
    deviceId: string;
    modelId: string;
    analysisType: AnalysisType;
    analysisResult: AnalysisResult;
    confidence: number;
    details?: Record<string, any>;
    confidenceFactors?: Record<string, any>;
    rawData?: Record<string, any>;
    timestamp: Date;
  }): Promise<AiAnalysisResult> {
    const result = this.analysisResultRepository.create(data);
    const saved = await this.analysisResultRepository.save(result);

    this.logger.log(
      `AI分析结果: 设备 ${data.deviceId}, 类型 ${data.analysisType}, 结果 ${data.analysisResult}, 置信度 ${data.confidence}%`,
    );

    return saved;
  }

  /**
   * 获取设备最新的分析结果
   */
  async getLatestResults(
    deviceId: string,
    options?: {
      analysisType?: AnalysisType;
      limit?: number;
    },
  ): Promise<AiAnalysisResult[]> {
    const { analysisType, limit = 20 } = options || {};

    const queryBuilder = this.analysisResultRepository
      .createQueryBuilder('result')
      .leftJoinAndSelect('result.model', 'model')
      .where('result.deviceId = :deviceId', { deviceId })
      .orderBy('result.timestamp', 'DESC')
      .take(limit);

    if (analysisType) {
      queryBuilder.andWhere('result.analysisType = :analysisType', {
        analysisType,
      });
    }

    return queryBuilder.getMany();
  }

  /**
   * 获取历史分析记录
   */
  async getHistory(
    deviceId: string,
    options?: {
      startTime?: Date;
      endTime?: Date;
      analysisType?: AnalysisType;
      analysisResult?: AnalysisResult;
      page?: number;
      pageSize?: number;
    },
  ): Promise<{
    items: AiAnalysisResult[];
    total: number;
    page: number;
    pageSize: number;
  }> {
    const {
      startTime,
      endTime,
      analysisType,
      analysisResult,
      page = 1,
      pageSize = 50,
    } = options || {};

    const queryBuilder = this.analysisResultRepository
      .createQueryBuilder('result')
      .leftJoinAndSelect('result.model', 'model')
      .where('result.deviceId = :deviceId', { deviceId });

    if (startTime && endTime) {
      queryBuilder.andWhere('result.timestamp BETWEEN :startTime AND :endTime', {
        startTime,
        endTime,
      });
    }

    if (analysisType) {
      queryBuilder.andWhere('result.analysisType = :analysisType', {
        analysisType,
      });
    }

    if (analysisResult) {
      queryBuilder.andWhere('result.analysisResult = :analysisResult', {
        analysisResult,
      });
    }

    queryBuilder
      .orderBy('result.timestamp', 'DESC')
      .skip((page - 1) * pageSize)
      .take(pageSize);

    const [items, total] = await queryBuilder.getManyAndCount();

    return { items, total, page, pageSize };
  }

  /**
   * 获取单个分析结果详情
   */
  async findOne(id: string): Promise<AiAnalysisResult> {
    const result = await this.analysisResultRepository.findOne({
      where: { id },
      relations: ['model', 'device'],
    });

    if (!result) {
      throw new NotFoundException(`分析结果不存在: ${id}`);
    }

    return result;
  }

  /**
   * 获取设备各类型分析的最新状态
   */
  async getLatestStatusByType(
    deviceId: string,
  ): Promise<Record<AnalysisType, AiAnalysisResult | null>> {
    const results: Partial<Record<AnalysisType, AiAnalysisResult | null>> = {};

    for (const type of Object.values(AnalysisType)) {
      const latest = await this.analysisResultRepository.findOne({
        where: { deviceId, analysisType: type as AnalysisType },
        order: { timestamp: 'DESC' },
        relations: ['model'],
      });
      results[type] = latest;
    }

    return results as Record<AnalysisType, AiAnalysisResult | null>;
  }

  /**
   * 批量获取多设备的最新分析结果
   */
  async getLatestResultsForDevices(
    deviceIds: string[],
  ): Promise<Map<string, AiAnalysisResult[]>> {
    const results = await this.analysisResultRepository
      .createQueryBuilder('result')
      .leftJoinAndSelect('result.model', 'model')
      .where('result.deviceId IN (:...deviceIds)', { deviceIds })
      .orderBy('result.timestamp', 'DESC')
      .getMany();

    const resultMap = new Map<string, AiAnalysisResult[]>();
    for (const result of results) {
      if (!resultMap.has(result.deviceId)) {
        resultMap.set(result.deviceId, []);
      }
      const deviceResults = resultMap.get(result.deviceId)!;
      if (deviceResults.length < 20) {
        deviceResults.push(result);
      }
    }

    return resultMap;
  }

  /**
   * 统计分析结果
   */
  async getStatistics(
    deviceId: string,
    options?: {
      startTime?: Date;
      endTime?: Date;
    },
  ): Promise<{
    total: number;
    byType: Record<AnalysisType, { total: number; normal: number; abnormal: number; warning: number }>;
    avgConfidence: number;
  }> {
    const { startTime, endTime } = options || {};

    const queryBuilder = this.analysisResultRepository
      .createQueryBuilder('result')
      .where('result.deviceId = :deviceId', { deviceId });

    if (startTime && endTime) {
      queryBuilder.andWhere('result.timestamp BETWEEN :startTime AND :endTime', {
        startTime,
        endTime,
      });
    }

    const results = await queryBuilder.getMany();

    const byType: Partial<Record<AnalysisType, { total: number; normal: number; abnormal: number; warning: number }>> = {};
    let totalConfidence = 0;

    for (const type of Object.values(AnalysisType)) {
      byType[type] = { total: 0, normal: 0, abnormal: 0, warning: 0 };
    }

    for (const result of results) {
      const typeStats = byType[result.analysisType];
      if (typeStats) {
        typeStats.total++;
        if (result.analysisResult === AnalysisResult.NORMAL) typeStats.normal++;
        else if (result.analysisResult === AnalysisResult.ABNORMAL) typeStats.abnormal++;
        else if (result.analysisResult === AnalysisResult.WARNING) typeStats.warning++;
      }
      totalConfidence += result.confidence;
    }

    return {
      total: results.length,
      byType: byType as Record<AnalysisType, { total: number; normal: number; abnormal: number; warning: number }>,
      avgConfidence: results.length > 0 ? totalConfidence / results.length : 0,
    };
  }
}
