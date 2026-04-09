import {
  Injectable,
  NotFoundException,
  ConflictException,
  BadRequestException,
  Logger,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository, In, DataSource } from 'typeorm';
import { AiModel, AiModelStatus, AiModelType } from './ai-model.entity';
import { DeviceModelBinding, BindingStatus } from './device-model-binding.entity';
import { AiModelVersion, ModelVersionStatus } from './ai-model-version.entity';
import {
  ModelDeployment,
  DeploymentStatus,
  DeviceDeployment,
  DeviceDeploymentStatus,
  DeploymentFailureReason,
} from './model-deployment.entity';
import { CreateAiModelDto } from './dto/create-ai-model.dto';
import { UpdateAiModelDto } from './dto/update-ai-model.dto';
import { CreateModelVersionDto, UpdateModelVersionDto, DeployModelDto } from './dto/model-version.dto';
import { AuditLogService } from '../audit/audit-log.service';

@Injectable()
export class AiModelService {
  private readonly logger = new Logger(AiModelService.name);

  constructor(
    @InjectRepository(AiModel)
    private readonly aiModelRepository: Repository<AiModel>,
    @InjectRepository(DeviceModelBinding)
    private readonly bindingRepository: Repository<DeviceModelBinding>,
    @InjectRepository(AiModelVersion)
    private readonly versionRepository: Repository<AiModelVersion>,
    @InjectRepository(ModelDeployment)
    private readonly deploymentRepository: Repository<ModelDeployment>,
    @InjectRepository(DeviceDeployment)
    private readonly deviceDeploymentRepository: Repository<DeviceDeployment>,
    private readonly dataSource: DataSource,
    private readonly auditLogService: AuditLogService,
  ) {}

  /**
   * 创建AI模型
   */
  async create(
    createDto: CreateAiModelDto,
    operatorId: string,
    requestInfo?: { ipAddress?: string; userAgent?: string },
  ): Promise<AiModel> {
    // 检查模型编码是否已存在
    const existingModel = await this.aiModelRepository.findOne({
      where: { code: createDto.code },
    });

    if (existingModel) {
      throw new ConflictException(`模型编码 "${createDto.code}" 已存在`);
    }

    const model = this.aiModelRepository.create({
      ...createDto,
      status: createDto.status || AiModelStatus.DRAFT,
    });

    const savedModel = await this.aiModelRepository.save(model);

    // 记录审计日志
    await this.auditLogService.createLog({
      action: 'create',
      targetType: 'AiModel',
      targetId: savedModel.id,
      operatorId,
      description: `创建AI模型: ${savedModel.name} (${savedModel.code})`,
      newValue: createDto,
      ipAddress: requestInfo?.ipAddress,
      userAgent: requestInfo?.userAgent,
    });

    this.logger.log(`AI模型创建成功: ${savedModel.code} by ${operatorId}`);

    return savedModel;
  }

  /**
   * 获取模型列表
   */
  async findAll(options?: {
    page?: number;
    pageSize?: number;
    type?: AiModelType;
    status?: AiModelStatus;
    search?: string;
  }): Promise<{ items: (AiModel & { deviceCount: number })[]; total: number; page: number; pageSize: number }> {
    const { page = 1, pageSize = 20, type, status, search } = options || {};

    const queryBuilder = this.aiModelRepository.createQueryBuilder('model');

    // 类型筛选
    if (type) {
      queryBuilder.andWhere('model.type = :type', { type });
    }

    // 状态筛选
    if (status) {
      queryBuilder.andWhere('model.status = :status', { status });
    }

    // 搜索
    if (search) {
      queryBuilder.andWhere(
        '(model.name ILIKE :search OR model.code ILIKE :search)',
        { search: `%${search}%` },
      );
    }

    // 排序和分页
    queryBuilder
      .orderBy('model.createdAt', 'DESC')
      .skip((page - 1) * pageSize)
      .take(pageSize);

    const [items, total] = await queryBuilder.getManyAndCount();

    // 批量获取设备绑定数量
    const modelIds = items.map((m) => m.id);
    const deviceCounts = await this.getDeviceCounts(modelIds);

    // 添加设备数量到每个模型
    const itemsWithCount = items.map((model) => ({
      ...model,
      deviceCount: deviceCounts[model.id] || 0,
    }));

    return { items: itemsWithCount, total, page, pageSize };
  }

  /**
   * 获取模型详情
   */
  async findOne(id: string): Promise<AiModel> {
    const model = await this.aiModelRepository.findOne({
      where: { id },
      relations: ['bindings', 'bindings.device'],
    });

    if (!model) {
      throw new NotFoundException(`AI模型不存在: ${id}`);
    }

    return model;
  }

  /**
   * 获取模型详细信息（含分页设备绑定列表）
   */
  async findOneDetail(id: string, options?: { page?: number; pageSize?: number }): Promise<{
    model: AiModel;
    specs: Record<string, any>;
    applicableDeviceTypes: string[];
    bindings: { items: DeviceModelBinding[]; total: number; page: number; pageSize: number };
  }> {
    const { page = 1, pageSize = 20 } = options || {};

    const model = await this.aiModelRepository.findOne({
      where: { id },
    });

    if (!model) {
      throw new NotFoundException(`AI模型不存在: ${id}`);
    }

    // 获取绑定的设备（分页）
    const [items, total] = await this.bindingRepository.findAndCount({
      where: { modelId: id },
      relations: ['device'],
      order: { boundAt: 'DESC' },
      skip: (page - 1) * pageSize,
      take: pageSize,
    });

    return {
      model,
      specs: model.specs || {},
      applicableDeviceTypes: model.applicableDeviceTypes || [],
      bindings: {
        items,
        total,
        page,
        pageSize,
      },
    };
  }

  /**
   * 更新模型
   */
  async update(
    id: string,
    updateDto: UpdateAiModelDto,
    operatorId: string,
    requestInfo?: { ipAddress?: string; userAgent?: string },
  ): Promise<AiModel> {
    const model = await this.findOne(id);

    // 如果更新编码，检查唯一性
    if (updateDto.code && updateDto.code !== model.code) {
      const existingModel = await this.aiModelRepository.findOne({
        where: { code: updateDto.code },
      });
      if (existingModel) {
        throw new ConflictException(`模型编码 "${updateDto.code}" 已存在`);
      }
    }

    Object.assign(model, updateDto);
    const updatedModel = await this.aiModelRepository.save(model);

    // 记录审计日志
    await this.auditLogService.createLog({
      action: 'update',
      targetType: 'AiModel',
      targetId: id,
      operatorId,
      description: `更新AI模型: ${model.name}`,
      ipAddress: requestInfo?.ipAddress,
      userAgent: requestInfo?.userAgent,
    });

    return updatedModel;
  }

  /**
   * 发布模型
   */
  async publish(
    id: string,
    operatorId: string,
    requestInfo?: { ipAddress?: string; userAgent?: string },
  ): Promise<AiModel> {
    const model = await this.findOne(id);

    if (model.status === AiModelStatus.PUBLISHED) {
      throw new BadRequestException('模型已处于发布状态');
    }

    model.status = AiModelStatus.PUBLISHED;
    const updatedModel = await this.aiModelRepository.save(model);

    // 记录审计日志
    await this.auditLogService.createLog({
      action: 'publish',
      targetType: 'AiModel',
      targetId: id,
      operatorId,
      description: `发布AI模型: ${model.name} (${model.version})`,
      ipAddress: requestInfo?.ipAddress,
      userAgent: requestInfo?.userAgent,
    });

    this.logger.log(`AI模型发布成功: ${model.code} by ${operatorId}`);

    return updatedModel;
  }

  /**
   * 下线模型
   */
  async deprecate(
    id: string,
    operatorId: string,
    requestInfo?: { ipAddress?: string; userAgent?: string },
  ): Promise<AiModel> {
    const model = await this.findOne(id);

    if (model.status !== AiModelStatus.PUBLISHED) {
      throw new BadRequestException('只有已发布的模型可以下线');
    }

    model.status = AiModelStatus.DEPRECATED;
    const updatedModel = await this.aiModelRepository.save(model);

    // 记录审计日志
    await this.auditLogService.createLog({
      action: 'deprecate',
      targetType: 'AiModel',
      targetId: id,
      operatorId,
      description: `下线AI模型: ${model.name}`,
      ipAddress: requestInfo?.ipAddress,
      userAgent: requestInfo?.userAgent,
    });

    this.logger.log(`AI模型已下线: ${model.code} by ${operatorId}`);

    return updatedModel;
  }

  /**
   * 删除模型
   */
  async remove(
    id: string,
    operatorId: string,
    requestInfo?: { ipAddress?: string; userAgent?: string },
  ): Promise<void> {
    const model = await this.findOne(id);

    // 检查是否有设备绑定
    const bindingCount = await this.bindingRepository.count({
      where: { modelId: id },
    });

    if (bindingCount > 0) {
      throw new BadRequestException(
        `该模型已绑定 ${bindingCount} 台设备，请先解除绑定后再删除`,
      );
    }

    await this.aiModelRepository.remove(model);

    // 记录审计日志
    await this.auditLogService.createLog({
      action: 'delete',
      targetType: 'AiModel',
      targetId: id,
      operatorId,
      description: `删除AI模型: ${model.name} (${model.code})`,
      ipAddress: requestInfo?.ipAddress,
      userAgent: requestInfo?.userAgent,
    });

    this.logger.log(`AI模型已删除: ${model.code} by ${operatorId}`);
  }

  /**
   * 获取模型绑定的设备数量
   */
  async getDeviceCount(modelId: string): Promise<number> {
    return this.bindingRepository.count({
      where: { modelId },
    });
  }

  /**
   * 批量获取模型的设备绑定数量
   */
  async getDeviceCounts(modelIds: string[]): Promise<Record<string, number>> {
    const counts = await this.bindingRepository
      .createQueryBuilder('binding')
      .select('binding.modelId', 'modelId')
      .addSelect('COUNT(*)', 'count')
      .where('binding.modelId IN (:...modelIds)', { modelIds })
      .groupBy('binding.modelId')
      .getRawMany();

    const result: Record<string, number> = {};
    modelIds.forEach((id) => {
      result[id] = 0;
    });
    counts.forEach((item) => {
      result[item.modelId] = parseInt(item.count, 10);
    });

    return result;
  }

  // ==================== 版本管理方法 ====================

  /**
   * 获取模型的所有版本
   */
  async getVersions(
    modelId: string,
    options?: { status?: ModelVersionStatus },
  ): Promise<AiModelVersion[]> {
    const queryBuilder = this.versionRepository
      .createQueryBuilder('version')
      .where('version.modelId = :modelId', { modelId })
      .orderBy('version.createdAt', 'DESC');

    if (options?.status) {
      queryBuilder.andWhere('version.status = :status', { status: options.status });
    }

    return queryBuilder.getMany();
  }

  /**
   * 获取单个版本详情
   */
  async getVersion(modelId: string, versionId: string): Promise<AiModelVersion> {
    const version = await this.versionRepository.findOne({
      where: { id: versionId, modelId },
      relations: ['model'],
    });

    if (!version) {
      throw new NotFoundException(`版本不存在: ${versionId}`);
    }

    return version;
  }

  /**
   * 创建新版本
   */
  async createVersion(
    modelId: string,
    createDto: CreateModelVersionDto,
    operatorId: string,
    requestInfo?: { ipAddress?: string; userAgent?: string },
  ): Promise<AiModelVersion> {
    // 检查模型是否存在
    const model = await this.findOne(modelId);

    // 检查版本号是否已存在
    const existingVersion = await this.versionRepository.findOne({
      where: { modelId, version: createDto.version },
    });

    if (existingVersion) {
      throw new ConflictException(`版本 "${createDto.version}" 已存在`);
    }

    const version = this.versionRepository.create({
      ...createDto,
      modelId,
      status: ModelVersionStatus.DRAFT,
      fileUrl: createDto.fileUrl,
      fileSize: createDto.fileSize,
      checksum: createDto.checksum,
      signature: createDto.signature,
    });

    const savedVersion = await this.versionRepository.save(version);

    // 记录审计日志
    await this.auditLogService.createLog({
      action: 'create_version',
      targetType: 'AiModel',
      targetId: modelId,
      operatorId,
      description: `创建模型版本: ${model.name} ${createDto.version}`,
      newValue: createDto,
      ipAddress: requestInfo?.ipAddress,
      userAgent: requestInfo?.userAgent,
    });

    this.logger.log(`模型版本创建: ${model.code} ${createDto.version} by ${operatorId}`);

    return savedVersion;
  }

  /**
   * 更新版本信息
   */
  async updateVersion(
    modelId: string,
    versionId: string,
    updateDto: UpdateModelVersionDto,
    operatorId: string,
    requestInfo?: { ipAddress?: string; userAgent?: string },
  ): Promise<AiModelVersion> {
    const version = await this.getVersion(modelId, versionId);

    // 已发布的版本不允许修改
    if (version.status === ModelVersionStatus.PUBLISHED) {
      throw new BadRequestException('已发布的版本不允许修改');
    }

    Object.assign(version, updateDto);
    const updatedVersion = await this.versionRepository.save(version);

    // 记录审计日志
    await this.auditLogService.createLog({
      action: 'update_version',
      targetType: 'AiModel',
      targetId: modelId,
      operatorId,
      description: `更新模型版本: ${version.version}`,
      ipAddress: requestInfo?.ipAddress,
      userAgent: requestInfo?.userAgent,
    });

    return updatedVersion;
  }

  /**
   * 发布版本
   */
  async publishVersion(
    modelId: string,
    versionId: string,
    operatorId: string,
    requestInfo?: { ipAddress?: string; userAgent?: string },
  ): Promise<AiModelVersion> {
    const model = await this.findOne(modelId);
    const version = await this.getVersion(modelId, versionId);

    if (version.status === ModelVersionStatus.PUBLISHED) {
      throw new BadRequestException('版本已处于发布状态');
    }

    if (!version.fileUrl || !version.checksum) {
      throw new BadRequestException('请先上传模型文件');
    }

    // 使用事务：将当前发布版本设为非当前，新版本设为当前
    await this.dataSource.transaction(async (manager) => {
      // 取消当前发布版本
      await manager.update(
        AiModelVersion,
        { modelId, isCurrent: true },
        { isCurrent: false },
      );

      // 发布新版本
      version.status = ModelVersionStatus.PUBLISHED;
      version.isCurrent = true;
      version.publishedAt = new Date();
      version.publishedBy = operatorId;

      await manager.save(version);

      // 更新模型主记录的版本和文件信息
      model.version = version.version;
      model.fileUrl = version.fileUrl;
      model.fileSize = version.fileSize;
      model.checksum = version.checksum;
      model.status = AiModelStatus.PUBLISHED;

      await manager.save(model);
    });

    // 记录审计日志
    await this.auditLogService.createLog({
      action: 'publish_version',
      targetType: 'AiModel',
      targetId: modelId,
      operatorId,
      description: `发布模型版本: ${model.name} ${version.version}`,
      ipAddress: requestInfo?.ipAddress,
      userAgent: requestInfo?.userAgent,
    });

    this.logger.log(`模型版本发布: ${model.code} ${version.version} by ${operatorId}`);

    return version;
  }

  /**
   * 下线版本
   */
  async deprecateVersion(
    modelId: string,
    versionId: string,
    operatorId: string,
    requestInfo?: { ipAddress?: string; userAgent?: string },
  ): Promise<AiModelVersion> {
    const version = await this.getVersion(modelId, versionId);

    if (version.status !== ModelVersionStatus.PUBLISHED) {
      throw new BadRequestException('只有已发布的版本可以下线');
    }

    if (version.isCurrent) {
      throw new BadRequestException('当前发布版本不能下线，请先发布其他版本');
    }

    version.status = ModelVersionStatus.DEPRECATED;
    const updatedVersion = await this.versionRepository.save(version);

    // 记录审计日志
    await this.auditLogService.createLog({
      action: 'deprecate_version',
      targetType: 'AiModel',
      targetId: modelId,
      operatorId,
      description: `下线模型版本: ${version.version}`,
      ipAddress: requestInfo?.ipAddress,
      userAgent: requestInfo?.userAgent,
    });

    this.logger.log(`模型版本下线: ${version.version} by ${operatorId}`);

    return updatedVersion;
  }

  /**
   * 获取当前发布版本
   */
  async getCurrentVersion(modelId: string): Promise<AiModelVersion | null> {
    return this.versionRepository.findOne({
      where: { modelId, isCurrent: true },
    });
  }

  // ==================== 模型部署方法 ====================

  /**
   * 创建部署任务
   */
  async createDeployment(
    modelId: string,
    deployDto: DeployModelDto,
    operatorId: string,
    requestInfo?: { ipAddress?: string; userAgent?: string },
  ): Promise<ModelDeployment> {
    const model = await this.findOne(modelId);
    const currentVersion = await this.getCurrentVersion(modelId);

    if (!currentVersion) {
      throw new BadRequestException('模型没有已发布的版本');
    }

    const targetVersion = deployDto.version
      ? await this.versionRepository.findOne({
          where: { modelId, version: deployDto.version },
        })
      : currentVersion;

    if (!targetVersion) {
      throw new NotFoundException(`版本不存在: ${deployDto.version}`);
    }

    // 创建部署任务
    const deployment = this.deploymentRepository.create({
      modelId,
      targetVersion: targetVersion.version,
      totalDevices: deployDto.deviceIds.length,
      successCount: 0,
      failedCount: 0,
      inProgressCount: 0,
      pendingCount: deployDto.deviceIds.length,
      status: DeploymentStatus.PENDING,
      createdBy: operatorId,
    });

    const savedDeployment = await this.deploymentRepository.save(deployment);

    // 创建设备部署记录
    const deviceDeployments = deployDto.deviceIds.map((deviceId) => ({
      deploymentId: savedDeployment.id,
      deviceId,
      status: DeviceDeploymentStatus.PENDING,
      progress: 0,
      retryCount: 0,
    }));

    await this.deviceDeploymentRepository.insert(deviceDeployments);

    // 记录审计日志
    await this.auditLogService.createLog({
      action: 'create_deployment',
      targetType: 'AiModel',
      targetId: modelId,
      operatorId,
      description: `创建模型部署任务: ${model.name} ${targetVersion.version}, 目标设备 ${deployDto.deviceIds.length} 台`,
      newValue: { deviceIds: deployDto.deviceIds, version: targetVersion.version },
      ipAddress: requestInfo?.ipAddress,
      userAgent: requestInfo?.userAgent,
    });

    this.logger.log(
      `部署任务创建: ${model.code} ${targetVersion.version}, 设备数: ${deployDto.deviceIds.length} by ${operatorId}`,
    );

    return savedDeployment;
  }

  /**
   * 获取部署任务详情
   */
  async getDeployment(deploymentId: string): Promise<{
    deployment: ModelDeployment;
    deviceDeployments: DeviceDeployment[];
    progress: {
      total: number;
      success: number;
      failed: number;
      pending: number;
      downloading: number;
      installing: number;
    };
  }> {
    const deployment = await this.deploymentRepository.findOne({
      where: { id: deploymentId },
    });

    if (!deployment) {
      throw new NotFoundException(`部署任务不存在: ${deploymentId}`);
    }

    const deviceDeployments = await this.deviceDeploymentRepository.find({
      where: { deploymentId },
    });

    // 计算进度统计
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

  /**
   * 获取模型的所有部署任务
   */
  async getDeployments(
    modelId: string,
    options?: { status?: DeploymentStatus; page?: number; pageSize?: number },
  ): Promise<{ items: ModelDeployment[]; total: number }> {
    const { status, page = 1, pageSize = 20 } = options || {};

    const queryBuilder = this.deploymentRepository
      .createQueryBuilder('deployment')
      .where('deployment.modelId = :modelId', { modelId })
      .orderBy('deployment.createdAt', 'DESC')
      .skip((page - 1) * pageSize)
      .take(pageSize);

    if (status) {
      queryBuilder.andWhere('deployment.status = :status', { status });
    }

    const [items, total] = await queryBuilder.getManyAndCount();
    return { items, total };
  }

  /**
   * 更新设备部署状态
   */
  async updateDeviceDeploymentStatus(
    deploymentId: string,
    deviceId: string,
    status: DeviceDeploymentStatus,
    progress?: number,
    error?: string,
    failureReason?: DeploymentFailureReason,
  ): Promise<DeviceDeployment> {
    const deviceDeployment = await this.deviceDeploymentRepository.findOne({
      where: { deploymentId, deviceId },
    });

    if (!deviceDeployment) {
      throw new NotFoundException(`设备部署记录不存在`);
    }

    deviceDeployment.status = status;
    if (progress !== undefined) {
      deviceDeployment.progress = progress;
    }
    deviceDeployment.error = error || null;
    deviceDeployment.failureReason = failureReason || null;

    if (status === DeviceDeploymentStatus.SUCCESS || status === DeviceDeploymentStatus.FAILED) {
      deviceDeployment.completedAt = new Date();
    } else if (status === DeviceDeploymentStatus.DOWNLOADING || status === DeviceDeploymentStatus.INSTALLING) {
      if (!deviceDeployment.startedAt) {
        deviceDeployment.startedAt = new Date();
      }
    }

    const updated = await this.deviceDeploymentRepository.save(deviceDeployment);

    // 更新部署任务统计
    await this.updateDeploymentProgress(deploymentId);

    return updated;
  }

  /**
   * 更新部署任务进度
   */
  private async updateDeploymentProgress(deploymentId: string): Promise<void> {
    const deviceDeployments = await this.deviceDeploymentRepository.find({
      where: { deploymentId },
    });

    const successCount = deviceDeployments.filter(
      (d) => d.status === DeviceDeploymentStatus.SUCCESS,
    ).length;
    const failedCount = deviceDeployments.filter(
      (d) => d.status === DeviceDeploymentStatus.FAILED,
    ).length;
    const pendingCount = deviceDeployments.filter(
      (d) => d.status === DeviceDeploymentStatus.PENDING,
    ).length;
    const inProgressCount = deviceDeployments.filter(
      (d) =>
        d.status === DeviceDeploymentStatus.DOWNLOADING ||
        d.status === DeviceDeploymentStatus.INSTALLING,
    ).length;

    const deployment = await this.deploymentRepository.findOne({
      where: { id: deploymentId },
    });

    if (deployment) {
      deployment.successCount = successCount;
      deployment.failedCount = failedCount;
      deployment.pendingCount = pendingCount;
      deployment.inProgressCount = inProgressCount;

      // 检查是否完成
      if (pendingCount === 0 && inProgressCount === 0) {
        deployment.status = DeploymentStatus.COMPLETED;
        deployment.completedAt = new Date();
      } else if (inProgressCount > 0) {
        deployment.status = DeploymentStatus.IN_PROGRESS;
        if (!deployment.startedAt) {
          deployment.startedAt = new Date();
        }
      }

      await this.deploymentRepository.save(deployment);
    }
  }

  /**
   * 重试失败的设备
   */
  async retryFailedDevices(
    deploymentId: string,
    operatorId: string,
  ): Promise<{ success: boolean; retryCount: number }> {
    const deployment = await this.deploymentRepository.findOne({
      where: { id: deploymentId },
    });

    if (!deployment) {
      throw new NotFoundException(`部署任务不存在: ${deploymentId}`);
    }

    const failedDevices = await this.deviceDeploymentRepository.find({
      where: { deploymentId, status: DeviceDeploymentStatus.FAILED },
    });

    if (failedDevices.length === 0) {
      return { success: true, retryCount: 0 };
    }

    for (const device of failedDevices) {
      device.status = DeviceDeploymentStatus.PENDING;
      device.progress = 0;
      device.error = null;
      device.failureReason = null;
      device.retryCount += 1;
    }

    await this.deviceDeploymentRepository.save(failedDevices);

    // 更新部署任务状态
    deployment.status = DeploymentStatus.IN_PROGRESS;
    deployment.pendingCount += failedDevices.length;
    deployment.failedCount -= failedDevices.length;
    await this.deploymentRepository.save(deployment);

    this.logger.log(
      `重试部署任务: ${deploymentId}, 设备数: ${failedDevices.length} by ${operatorId}`,
    );

    return { success: true, retryCount: failedDevices.length };
  }

  /**
   * 取消部署任务
   */
  async cancelDeployment(
    deploymentId: string,
    operatorId: string,
  ): Promise<ModelDeployment> {
    const deployment = await this.deploymentRepository.findOne({
      where: { id: deploymentId },
    });

    if (!deployment) {
      throw new NotFoundException(`部署任务不存在: ${deploymentId}`);
    }

    if (
      deployment.status === DeploymentStatus.COMPLETED ||
      deployment.status === DeploymentStatus.CANCELLED
    ) {
      throw new BadRequestException('部署任务已完成或已取消，无法取消');
    }

    // 取消所有待处理的设备部署
    await this.deviceDeploymentRepository.update(
      { deploymentId, status: DeviceDeploymentStatus.PENDING },
      { status: DeviceDeploymentStatus.SKIPPED },
    );

    deployment.status = DeploymentStatus.CANCELLED;
    deployment.completedAt = new Date();

    const updated = await this.deploymentRepository.save(deployment);

    this.logger.log(`取消部署任务: ${deploymentId} by ${operatorId}`);

    return updated;
  }

  /**
   * 获取MQTT部署指令
   */
  async getDeploymentCommand(deploymentId: string, deviceId: string): Promise<{
    cmd: string;
    modelId: string;
    version: string;
    url: string;
    checksum: string;
    signature: string;
    timestamp: string;
  }> {
    const deployment = await this.deploymentRepository.findOne({
      where: { id: deploymentId },
    });

    if (!deployment) {
      throw new NotFoundException(`部署任务不存在: ${deploymentId}`);
    }

    const deviceDeployment = await this.deviceDeploymentRepository.findOne({
      where: { deploymentId, deviceId },
    });

    if (!deviceDeployment) {
      throw new NotFoundException(`设备部署记录不存在: ${deviceId}`);
    }

    // 查找版本信息
    const version = await this.versionRepository.findOne({
      where: { modelId: deployment.modelId, version: deployment.targetVersion },
    });

    if (!version) {
      throw new NotFoundException(`版本不存在: ${deployment.targetVersion}`);
    }

    return {
      cmd: 'update_model',
      modelId: deployment.modelId,
      version: version.version,
      url: version.fileUrl || '',
      checksum: version.checksum || '',
      signature: version.signature || '',
      timestamp: new Date().toISOString(),
    };
  }
}
