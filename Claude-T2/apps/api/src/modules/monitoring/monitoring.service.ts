import { Injectable, Logger, NotFoundException, BadRequestException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository, In } from 'typeorm';
import { Device } from '../device/device.entity';
import { DeviceModelBinding, BindingStatus } from '../ai-model/device-model-binding.entity';
import { ModelDeployment, DeviceDeployment, DeploymentStatus, DeviceDeploymentStatus } from '../ai-model/model-deployment.entity';
import { AiModel, AiModelStatus } from '../ai-model/ai-model.entity';

@Injectable()
export class MonitoringService {
  private readonly logger = new Logger(MonitoringService.name);

  constructor(
    @InjectRepository(Device)
    private readonly deviceRepository: Repository<Device>,
    @InjectRepository(DeviceModelBinding)
    private readonly bindingRepository: Repository<DeviceModelBinding>,
    @InjectRepository(ModelDeployment)
    private readonly deploymentRepository: Repository<ModelDeployment>,
    @InjectRepository(DeviceDeployment)
    private readonly deviceDeploymentRepository: Repository<DeviceDeployment>,
    @InjectRepository(AiModel)
    private readonly aiModelRepository: Repository<AiModel>,
  ) {}

  // ==================== Story 4.13: 模型运行状态监控看板 ====================

  async getModelStatus() {
    // 获取所有绑定
    const bindings = await this.bindingRepository.find({
      relations: ['device'],
    });

    // 统计
    let running = 0;
    let error = 0;
    const errorDevices: Array<{
      id: string;
      name: string;
      errorType: string;
      duration: string;
    }> = [];

    for (const binding of bindings) {
      if (binding.status === BindingStatus.RUNNING) {
        running++;
      } else if (binding.status === BindingStatus.ERROR) {
        error++;
        const device = binding.device;
        const errorDuration = this.formatDuration(binding.lastSyncAt || binding.createdAt);
        errorDevices.push({
          id: device.id,
          name: device.name,
          errorType: binding.error || 'MODEL_ERROR',
          duration: errorDuration,
        });
      }
    }

    // 统计未绑定设备数
    const boundDeviceIds = bindings.map((b) => b.deviceId);
    const totalDevices = await this.deviceRepository.count();
    const unbound = totalDevices - boundDeviceIds.length;

    return {
      stats: { running, error, unbound: Math.max(0, unbound) },
      errorDevices,
    };
  }

  private formatDuration(since: Date): string {
    const diffMs = Date.now() - since.getTime();
    const hours = Math.floor(diffMs / 3600000);
    const minutes = Math.floor((diffMs % 3600000) / 60000);
    if (hours > 0) {
      return `${hours}小时${minutes}分`;
    }
    return `${minutes}分钟`;
  }

  // ==================== Story 4.12: 模型下发进度监控 ====================

  async getDeploymentProgress(deploymentId: string) {
    const deployment = await this.deploymentRepository.findOne({
      where: { id: deploymentId },
    });

    if (!deployment) {
      throw new NotFoundException(`部署任务不存在: ${deploymentId}`);
    }

    const deviceDeployments = await this.deviceDeploymentRepository.find({
      where: { deploymentId },
    });

    const progress = {
      total: deviceDeployments.length,
      success: deviceDeployments.filter((d) => d.status === DeviceDeploymentStatus.SUCCESS).length,
      failed: deviceDeployments.filter((d) => d.status === DeviceDeploymentStatus.FAILED).length,
      pending: deviceDeployments.filter((d) => d.status === DeviceDeploymentStatus.PENDING).length,
      downloading: deviceDeployments.filter((d) => d.status === DeviceDeploymentStatus.DOWNLOADING).length,
      installing: deviceDeployments.filter((d) => d.status === DeviceDeploymentStatus.INSTALLING).length,
    };

    return { deployment, deviceDeployments, progress };
  }

  // ==================== Story 4.14: 批量模型绑定 ====================

  async batchBindModel(deviceIds: string[], modelId: string) {
    if (!deviceIds || deviceIds.length === 0) {
      throw new BadRequestException('设备列表不能为空');
    }
    if (deviceIds.length > 50) {
      throw new BadRequestException('每批最多50台设备');
    }

    // 检查模型
    const model = await this.aiModelRepository.findOne({
      where: { id: modelId },
    });
    if (!model) {
      throw new NotFoundException(`模型不存在: ${modelId}`);
    }
    if (model.status !== AiModelStatus.PUBLISHED) {
      throw new BadRequestException('模型未发布，无法绑定');
    }

    // 检查兼容性
    const applicableTypes = model.applicableDeviceTypes || [];
    const devices = await this.deviceRepository.find({
      where: { id: In(deviceIds) },
    });

    const compatibleDevices = devices.filter(
      (d) => applicableTypes.length === 0 || applicableTypes.includes(d.deviceType),
    );
    const incompatibleDevices = devices.filter(
      (d) => applicableTypes.length > 0 && !applicableTypes.includes(d.deviceType),
    );

    // 创建绑定
    const bindings: DeviceModelBinding[] = [];
    for (const device of compatibleDevices) {
      // 检查是否已绑定
      const existing = await this.bindingRepository.findOne({
        where: { deviceId: device.id, modelId },
      });
      if (!existing) {
        const binding = this.bindingRepository.create({
          deviceId: device.id,
          modelId,
          status: BindingStatus.PENDING,
          boundVersion: model.version,
        });
        bindings.push(binding);
      }
    }

    if (bindings.length > 0) {
      await this.bindingRepository.save(bindings);
    }

    this.logger.log(
      `批量绑定: 模型 ${model.code}, 成功 ${bindings.length} 台, 不兼容 ${incompatibleDevices.length} 台`,
    );

    return {
      success: true,
      totalDevices: deviceIds.length,
      compatibleDevices: compatibleDevices.length,
      incompatibleDevices: incompatibleDevices.length,
      boundCount: bindings.length,
    };
  }

  // ==================== Story 4.15: AI分析结果聚合统计 ====================

  async getAiResultsSummary(options: {
    projectId?: string;
    deviceType?: string;
    startTime?: string;
    endTime?: string;
  }) {
    // TODO: 接入 ClickHouse 物化视图查询
    // 当前返回基础聚合查询结构
    const now = new Date();
    const oneHourAgo = new Date(now.getTime() - 3600000);

    this.logger.log('AI分析结果聚合统计查询');

    return {
      summary: {
        totalAnalysisCount: 0,
        abnormalCount: 0,
        avgConfidence: 0,
      },
      filters: {
        projectId: options.projectId || null,
        deviceType: options.deviceType || null,
        startTime: options.startTime || oneHourAgo.toISOString(),
        endTime: options.endTime || now.toISOString(),
      },
    };
  }
}
