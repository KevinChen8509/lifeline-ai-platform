import {
  Injectable,
  NotFoundException,
  BadRequestException,
  ConflictException,
  Logger,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository, DataSource, In } from 'typeorm';
import { Device, DeviceStatus, DeviceSource, DeviceProtocol } from './device.entity';
import { DeviceStatusHistory } from './device-status-history.entity';
import { DeviceModelBinding, BindingStatus } from '../ai-model/device-model-binding.entity';
import { AiModel, AiModelStatus } from '../ai-model/ai-model.entity';
import { AssignProjectDto, BatchAssignProjectDto } from './dto/assign-project.dto';
import {
  ScanRegisterDto,
  getDeviceInfoByType,
  DeviceTypeInfo,
} from './dto/scan-register.dto';
import { CreateDeviceDto } from './dto/create-device.dto';
import { UpdateDeviceConfigDto, BatchUpdateConfigDto } from './dto/update-config.dto';
import { BindModelsDto } from './dto/bind-models.dto';
import { ProjectService } from '../project/project.service';
import { AuditLogService } from '../audit/audit-log.service';

@Injectable()
export class DeviceService {
  private readonly logger = new Logger(DeviceService.name);

  constructor(
    @InjectRepository(Device)
    private readonly deviceRepository: Repository<Device>,
    @InjectRepository(DeviceStatusHistory)
    private readonly statusHistoryRepository: Repository<DeviceStatusHistory>,
    @InjectRepository(DeviceModelBinding)
    private readonly bindingRepository: Repository<DeviceModelBinding>,
    @InjectRepository(AiModel)
    private readonly aiModelRepository: Repository<AiModel>,
    private readonly projectService: ProjectService,
    private readonly dataSource: DataSource,
    private readonly auditLogService: AuditLogService,
  ) {}

  /**
   * 创建设备（手动录入）
   */
  async create(
    createDto: CreateDeviceDto,
    operatorId: string,
    requestInfo?: { ipAddress?: string; userAgent?: string },
  ): Promise<Device> {
    // 检查序列号是否已存在
    const existingDevice = await this.deviceRepository.findOne({
      where: { serialNumber: createDto.serialNumber },
    });

    if (existingDevice) {
      throw new ConflictException(`设备序列号 "${createDto.serialNumber}" 已存在`);
    }

    // 如果指定了项目，检查用户是否是项目成员
    if (createDto.projectId) {
      const isMember = await this.projectService.isProjectMember(
        createDto.projectId,
        operatorId,
      );
      if (!isMember) {
        throw new BadRequestException('您不是目标项目的成员，无法添加设备到此项目');
      }

      // 验证项目存在
      await this.projectService.findOne(createDto.projectId);
    }

    // 创建设备
    const device = this.deviceRepository.create({
      name: createDto.name,
      serialNumber: createDto.serialNumber,
      deviceType: createDto.deviceType,
      model: createDto.model,
      manufacturer: createDto.manufacturer,
      source: createDto.source || DeviceSource.THIRD_PARTY,
      protocol: createDto.protocol,
      description: createDto.description,
      projectId: createDto.projectId || null,
      status: DeviceStatus.PENDING,
      config: {},
    });

    const savedDevice = await this.deviceRepository.save(device);

    // 记录审计日志
    await this.auditLogService.createLog({
      action: 'create',
      targetType: 'Device',
      targetId: savedDevice.id,
      operatorId,
      description: `手动录入设备: ${createDto.serialNumber}, 来源: ${savedDevice.source}`,
      newValue: createDto,
      ipAddress: requestInfo?.ipAddress,
      userAgent: requestInfo?.userAgent,
    });

    this.logger.log(`手动录入设备成功: ${createDto.serialNumber} by ${operatorId}`);

    return savedDevice;
  }

  /**
   * 分配设备到项目
   */
  async assignProject(
    deviceId: string,
    assignDto: AssignProjectDto,
    operatorId: string,
    requestInfo?: { ipAddress?: string; userAgent?: string },
  ): Promise<Device> {
    // 查找设备
    const device = await this.deviceRepository.findOne({
      where: { id: deviceId },
    });

    if (!device) {
      throw new NotFoundException(`设备不存在: ${deviceId}`);
    }

    const oldProjectId = device.projectId;

    // 如果分配到新项目，检查用户是否是项目成员
    if (assignDto.projectId) {
      const isMember = await this.projectService.isProjectMember(
        assignDto.projectId,
        operatorId,
      );
      if (!isMember) {
        throw new BadRequestException('您不是目标项目的成员，无法分配设备');
      }

      // 验证项目存在
      await this.projectService.findOne(assignDto.projectId);
    }

    // 更新项目
    device.projectId = assignDto.projectId;
    const updatedDevice = await this.deviceRepository.save(device);

    // 记录审计日志
    await this.auditLogService.createLog({
      action: oldProjectId ? 'reassign_project' : 'assign_project',
      targetType: 'Device',
      targetId: deviceId,
      operatorId,
      description: `设备${oldProjectId ? '重新分配' : '分配'}项目: ${assignDto.projectId || '无项目'}`,
      oldValue: { projectId: oldProjectId },
      newValue: { projectId: assignDto.projectId },
      ipAddress: requestInfo?.ipAddress,
      userAgent: requestInfo?.userAgent,
    });

    this.logger.log(
      `设备 ${device.serialNumber} 分配到项目 ${assignDto.projectId || '无项目'} by ${operatorId}`,
    );

    return updatedDevice;
  }

  /**
   * 批量分配设备到项目
   */
  async batchAssignProject(
    batchDto: BatchAssignProjectDto,
    operatorId: string,
    requestInfo?: { ipAddress?: string; userAgent?: string },
  ): Promise<{ success: number; failed: string[] }> {
    // 如果分配到新项目，检查用户是否是项目成员
    if (batchDto.projectId) {
      const isMember = await this.projectService.isProjectMember(
        batchDto.projectId,
        operatorId,
      );
      if (!isMember) {
        throw new BadRequestException('您不是目标项目的成员，无法分配设备');
      }

      // 验证项目存在
      await this.projectService.findOne(batchDto.projectId);
    }

    // 查找所有设备
    const devices = await this.deviceRepository.find({
      where: { id: In(batchDto.deviceIds) },
    });

    const foundIds = devices.map((d) => d.id);
    const notFoundIds = batchDto.deviceIds.filter((id) => !foundIds.includes(id));

    if (notFoundIds.length > 0) {
      this.logger.warn(`未找到设备: ${notFoundIds.join(', ')}`);
    }

    // 批量更新
    const updatePromises = devices.map(async (device) => {
      device.projectId = batchDto.projectId;
      return this.deviceRepository.save(device);
    });

    await Promise.all(updatePromises);

    // 记录审计日志
    await this.auditLogService.createLog({
      action: 'batch_assign_project',
      targetType: 'Device',
      operatorId,
      description: `批量分配 ${devices.length} 个设备到项目: ${batchDto.projectId || '无项目'}`,
      newValue: { deviceIds: batchDto.deviceIds, projectId: batchDto.projectId },
      ipAddress: requestInfo?.ipAddress,
      userAgent: requestInfo?.userAgent,
    });

    this.logger.log(
      `批量分配 ${devices.length} 个设备到项目 ${batchDto.projectId || '无项目'} by ${operatorId}`,
    );

    return {
      success: devices.length,
      failed: notFoundIds,
    };
  }

  /**
   * 查找项目下的所有设备
   */
  async findByProject(
    projectId: string,
    options?: { page?: number; pageSize?: number; status?: DeviceStatus },
  ): Promise<{ items: Device[]; total: number }> {
    const { page = 1, pageSize = 20, status } = options || {};

    const queryBuilder = this.deviceRepository
      .createQueryBuilder('device')
      .where('device.projectId = :projectId', { projectId });

    if (status) {
      queryBuilder.andWhere('device.status = :status', { status });
    }

    queryBuilder
      .orderBy('device.createdAt', 'DESC')
      .skip((page - 1) * pageSize)
      .take(pageSize);

    const [items, total] = await queryBuilder.getManyAndCount();

    return { items, total };
  }

  /**
   * 查找所有设备（支持分页、筛选、搜索）
   */
  async findAll(options?: {
    page?: number;
    pageSize?: number;
    status?: DeviceStatus;
    search?: string;
    projectId?: string;
    userId?: string;
  }): Promise<{ items: Device[]; total: number; page: number; pageSize: number }> {
    const { page = 1, pageSize = 20, status, search, projectId, userId } = options || {};

    const queryBuilder = this.deviceRepository
      .createQueryBuilder('device')
      .leftJoinAndSelect('device.project', 'project');

    // 如果指定了项目ID，筛选项目
    if (projectId) {
      queryBuilder.andWhere('device.projectId = :projectId', { projectId });
    }

    // 如果指定了用户ID，只返回用户有权限的项目设备
    if (userId) {
      queryBuilder.andWhere(
        'device.projectId IN (SELECT pu.project_id FROM project_users pu WHERE pu.user_id = :userId)',
        { userId },
      );
    }

    // 状态筛选
    if (status) {
      queryBuilder.andWhere('device.status = :status', { status });
    }

    // 搜索条件（设备名称或序列号）
    if (search) {
      queryBuilder.andWhere(
        '(device.name ILIKE :search OR device.serialNumber ILIKE :search)',
        { search: `%${search}%` },
      );
    }

    // 排序和分页
    queryBuilder
      .orderBy('device.createdAt', 'DESC')
      .skip((page - 1) * pageSize)
      .take(pageSize);

    const [items, total] = await queryBuilder.getManyAndCount();

    return { items, total, page, pageSize };
  }

  /**
   * 查找设备详情
   */
  async findOne(id: string): Promise<Device> {
    const device = await this.deviceRepository.findOne({
      where: { id },
      relations: ['project'],
    });

    if (!device) {
      throw new NotFoundException(`设备不存在: ${id}`);
    }

    return device;
  }

  /**
   * 扫码注册设备
   * 解析二维码数据，创建或返回已存在的设备
   */
  async scanRegister(
    scanDto: ScanRegisterDto,
    operatorId: string,
    requestInfo?: { ipAddress?: string; userAgent?: string },
  ): Promise<{
    isNew: boolean;
    device: Device;
    preview: {
      deviceType: DeviceTypeInfo | null;
      recommendedProject: string | null;
    };
  }> {
    const { qrData } = scanDto;

    // 解析二维码数据: LK://{device_sn}:{device_type}:{factory_id}
    const parsedData = this.parseQRData(qrData);
    if (!parsedData) {
      throw new BadRequestException('无效的二维码格式');
    }

    const { serialNumber, deviceType, factoryId } = parsedData;

    // 检查设备是否已存在
    const existingDevice = await this.deviceRepository.findOne({
      where: { serialNumber },
    });

    if (existingDevice) {
      this.logger.log(`扫描到已注册设备: ${serialNumber}`);
      return {
        isNew: false,
        device: existingDevice,
        preview: {
          deviceType: getDeviceInfoByType(existingDevice.deviceType || ''),
          recommendedProject: existingDevice.projectId,
        },
      };
    }

    // 获取设备类型信息
    const deviceTypeInfo = getDeviceInfoByType(deviceType);

    // 创建新设备
    const device = this.deviceRepository.create({
      name: deviceTypeInfo ? deviceTypeInfo.name : `设备-${serialNumber}`,
      serialNumber,
      deviceType,
      model: deviceType,
      manufacturer: deviceTypeInfo?.manufacturer || factoryId,
      source: DeviceSource.SELF_DEVELOPED,
      status: DeviceStatus.PENDING,
      config: {},
    });

    const savedDevice = await this.deviceRepository.save(device);

    // 记录审计日志
    await this.auditLogService.createLog({
      action: 'scan_register',
      targetType: 'Device',
      targetId: savedDevice.id,
      operatorId,
      description: `扫码注册设备: ${serialNumber}, 类型: ${deviceType}`,
      newValue: { serialNumber, deviceType, factoryId },
      ipAddress: requestInfo?.ipAddress,
      userAgent: requestInfo?.userAgent,
    });

    this.logger.log(`扫码注册设备成功: ${serialNumber} by ${operatorId}`);

    return {
      isNew: true,
      device: savedDevice,
      preview: {
        deviceType: deviceTypeInfo,
        recommendedProject: null,
      },
    };
  }

  /**
   * 解析二维码数据
   * 格式: LK://{device_sn}:{device_type}:{factory_id}
   */
  private parseQRData(
    qrData: string,
  ): { serialNumber: string; deviceType: string; factoryId: string } | null {
    try {
      // 检查前缀
      if (!qrData.startsWith('LK://')) {
        return null;
      }

      // 移除前缀
      const data = qrData.substring(5);

      // 分割数据
      const parts = data.split(':');
      if (parts.length !== 3) {
        return null;
      }

      const [serialNumber, deviceType, factoryId] = parts;

      // 验证数据
      if (!serialNumber || !deviceType || !factoryId) {
        return null;
      }

      return { serialNumber, deviceType, factoryId };
    } catch {
      return null;
    }
  }

  /**
   * 激活设备
   * 发送激活指令并更新设备状态
   */
  async activate(
    deviceId: string,
    operatorId: string,
    requestInfo?: { ipAddress?: string; userAgent?: string },
  ): Promise<{
    device: Device;
    activationCommand: {
      cmd: string;
      timestamp: string;
      server: string;
      port: number;
    };
  }> {
    // 查找设备
    const device = await this.deviceRepository.findOne({
      where: { id: deviceId },
    });

    if (!device) {
      throw new NotFoundException(`设备不存在: ${deviceId}`);
    }

    // 验证设备状态
    if (device.status !== DeviceStatus.PENDING && device.status !== DeviceStatus.FAILED) {
      throw new BadRequestException(`设备当前状态为 ${device.status}，无法激活`);
    }

    // 更新设备状态为激活中
    device.status = DeviceStatus.ACTIVATING;
    await this.deviceRepository.save(device);

    // 生成激活指令
    const activationCommand = {
      cmd: 'activate',
      timestamp: new Date().toISOString(),
      server: process.env.MQTT_HOST || 'mqtt.example.com',
      port: parseInt(process.env.MQTT_PORT || '1883', 10),
    };

    // TODO: 通过MQTT发送激活指令
    // await this.mqttService.publish(`device/${deviceId}/command`, activationCommand);

    this.logger.log(`设备激活指令已发送: ${device.serialNumber} by ${operatorId}`);

    // 记录审计日志
    await this.auditLogService.createLog({
      action: 'activate',
      targetType: 'Device',
      targetId: deviceId,
      operatorId,
      description: `发送设备激活指令: ${device.serialNumber}`,
      newValue: activationCommand,
      ipAddress: requestInfo?.ipAddress,
      userAgent: requestInfo?.userAgent,
    });

    return {
      device,
      activationCommand,
    };
  }

  /**
   * 确认设备上线
   * 设备首次上报数据时调用
   */
  async confirmOnline(
    deviceId: string,
    requestInfo?: { ipAddress?: string; userAgent?: string },
  ): Promise<Device> {
    const device = await this.deviceRepository.findOne({
      where: { id: deviceId },
    });

    if (!device) {
      throw new NotFoundException(`设备不存在: ${deviceId}`);
    }

    // 更新设备状态为在线
    const oldStatus = device.status;
    device.status = DeviceStatus.ONLINE;
    device.lastOnlineAt = new Date();
    const updatedDevice = await this.deviceRepository.save(device);

    this.logger.log(`设备已上线: ${device.serialNumber}`);

    // 记录审计日志
    await this.auditLogService.createLog({
      action: 'online',
      targetType: 'Device',
      targetId: deviceId,
      operatorId: 'system',
      description: `设备上线: ${device.serialNumber}`,
      oldValue: { status: oldStatus },
      newValue: { status: DeviceStatus.ONLINE },
      ipAddress: requestInfo?.ipAddress,
      userAgent: requestInfo?.userAgent,
    });

    return updatedDevice;
  }

  /**
   * 标记激活失败
   */
  async markActivationFailed(
    deviceId: string,
    reason: string,
    requestInfo?: { ipAddress?: string; userAgent?: string },
  ): Promise<Device> {
    const device = await this.deviceRepository.findOne({
      where: { id: deviceId },
    });

    if (!device) {
      throw new NotFoundException(`设备不存在: ${deviceId}`);
    }

    // 更新设备状态为失败
    const oldStatus = device.status;
    device.status = DeviceStatus.FAILED;
    const updatedDevice = await this.deviceRepository.save(device);

    this.logger.warn(`设备激活失败: ${device.serialNumber}, 原因: ${reason}`);

    // 记录审计日志
    await this.auditLogService.createLog({
      action: 'activation_failed',
      targetType: 'Device',
      targetId: deviceId,
      operatorId: 'system',
      description: `设备激活失败: ${device.serialNumber}, 原因: ${reason}`,
      oldValue: { status: oldStatus },
      newValue: { status: DeviceStatus.FAILED, reason },
      ipAddress: requestInfo?.ipAddress,
      userAgent: requestInfo?.userAgent,
    });

    return updatedDevice;
  }

  /**
   * 更新设备配置
   * 远程修改设备采集频次和上传频次
   */
  async updateConfig(
    deviceId: string,
    configDto: UpdateDeviceConfigDto,
    operatorId: string,
    requestInfo?: { ipAddress?: string; userAgent?: string },
  ): Promise<{
    device: Device;
    configCommand: {
      cmd: string;
      timestamp: string;
      [key: string]: any;
    };
  }> {
    // 查找设备
    const device = await this.deviceRepository.findOne({
      where: { id: deviceId },
    });

    if (!device) {
      throw new NotFoundException(`设备不存在: ${deviceId}`);
    }

    // 检查设备状态
    if (device.status !== DeviceStatus.ONLINE) {
      throw new BadRequestException(`设备当前状态为 ${device.status}，只有在线设备可以更新配置`);
    }

    // 保存旧配置
    const oldConfig = { ...device.config };

    // 更新配置
    device.config = {
      ...device.config,
      ...(configDto.collectInterval !== undefined && { collectInterval: configDto.collectInterval }),
      ...(configDto.uploadInterval !== undefined && { uploadInterval: configDto.uploadInterval }),
      ...(configDto.alertThresholds !== undefined && { alertThresholds: configDto.alertThresholds }),
    };

    const updatedDevice = await this.deviceRepository.save(device);

    // 生成配置下发指令
    const configCommand = {
      cmd: 'update_config',
      timestamp: new Date().toISOString(),
      ...configDto,
    };

    // TODO: 通过MQTT下发配置指令
    // await this.mqttService.publish(`device/${deviceId}/command`, configCommand);

    this.logger.log(`设备配置已更新: ${device.serialNumber} by ${operatorId}`);

    // 记录审计日志
    await this.auditLogService.createLog({
      action: 'update_config',
      targetType: 'Device',
      targetId: deviceId,
      operatorId,
      description: `更新设备配置: ${device.serialNumber}`,
      oldValue: oldConfig,
      newValue: device.config,
      ipAddress: requestInfo?.ipAddress,
      userAgent: requestInfo?.userAgent,
    });

    return {
      device: updatedDevice,
      configCommand,
    };
  }

  /**
   * 批量更新设备配置
   * 对多台设备执行相同的配置操作
   */
  async batchUpdateConfig(
    batchDto: BatchUpdateConfigDto,
    operatorId: string,
    requestInfo?: { ipAddress?: string; userAgent?: string },
  ): Promise<{
    taskId: string;
    status: string;
    total: number;
    success: number;
    failed: number;
    errors: Array<{ deviceId: string; reason: string }>;
  }> {
    const { deviceIds, config } = batchDto;

    // 查找所有设备
    const devices = await this.deviceRepository.find({
      where: { id: In(deviceIds) },
    });

    const foundIds = devices.map((d) => d.id);
    const notFoundIds = deviceIds.filter((id) => !foundIds.includes(id));

    // 检查设备类型是否一致
    const deviceTypes = [...new Set(devices.map((d) => d.deviceType))];
    if (deviceTypes.length > 1) {
      throw new BadRequestException(
        `批量配置的设备类型不一致: ${deviceTypes.join(', ')}。只能对同类型设备进行批量配置`,
      );
    }

    // 生成任务ID
    const taskId = `batch-config-${Date.now()}-${Math.random().toString(36).substring(2, 8)}`;

    const results = {
      taskId,
      status: 'processing',
      total: deviceIds.length,
      success: 0,
      failed: notFoundIds.length,
      errors: [] as Array<{ deviceId: string; reason: string }>,
    };

    // 记录未找到的设备
    notFoundIds.forEach((id) => {
      results.errors.push({ deviceId: id, reason: '设备不存在' });
    });

    // 批量更新配置
    for (const device of devices) {
      try {
        // 检查设备状态
        if (device.status !== DeviceStatus.ONLINE) {
          results.failed++;
          results.errors.push({
            deviceId: device.id,
            reason: `设备状态为 ${device.status}，只有在线设备可以更新配置`,
          });
          continue;
        }

        // 更新配置
        device.config = {
          ...device.config,
          ...(config.collectInterval !== undefined && { collectInterval: config.collectInterval }),
          ...(config.uploadInterval !== undefined && { uploadInterval: config.uploadInterval }),
          ...(config.alertThresholds !== undefined && { alertThresholds: config.alertThresholds }),
        };

        await this.deviceRepository.save(device);
        results.success++;

        // TODO: 通过MQTT下发配置指令
        // await this.mqttService.publish(`device/${device.id}/command`, {
        //   cmd: 'update_config',
        //   ...config,
        // });
      } catch (error) {
        results.failed++;
        results.errors.push({
          deviceId: device.id,
          reason: error.message || '配置更新失败',
        });
      }
    }

    // 更新任务状态
    results.status = results.failed === 0 ? 'completed' : 'partial_success';

    // 记录审计日志
    await this.auditLogService.createLog({
      action: 'batch_update_config',
      targetType: 'Device',
      operatorId,
      description: `批量更新 ${results.success} 台设备配置`,
      newValue: {
        taskId,
        deviceIds,
        config,
        results: { success: results.success, failed: results.failed },
      },
      ipAddress: requestInfo?.ipAddress,
      userAgent: requestInfo?.userAgent,
    });

    this.logger.log(
      `批量配置更新完成: 成功 ${results.success}, 失败 ${results.failed} by ${operatorId}`,
    );

    return results;
  }

  /**
   * 获取设备状态变更历史
   */
  async getStatusHistory(
    deviceId: string,
    options?: {
      startDate?: string;
      endDate?: string;
      page?: number;
      pageSize?: number;
    },
  ): Promise<{ items: DeviceStatusHistory[]; total: number }> {
    // 验证设备存在
    const device = await this.deviceRepository.findOne({
      where: { id: deviceId },
    });

    if (!device) {
      throw new NotFoundException(`设备不存在: ${deviceId}`);
    }

    const { startDate, endDate, page = 1, pageSize = 20 } = options || {};

    const queryBuilder = this.statusHistoryRepository
      .createQueryBuilder('history')
      .where('history.deviceId = :deviceId', { deviceId });

    // 时间范围筛选
    if (startDate) {
      queryBuilder.andWhere('history.timestamp >= :startDate', {
        startDate: new Date(startDate),
      });
    }

    if (endDate) {
      queryBuilder.andWhere('history.timestamp <= :endDate', {
        endDate: new Date(endDate),
      });
    }

    // 排序和分页
    queryBuilder
      .orderBy('history.timestamp', 'DESC')
      .skip((page - 1) * pageSize)
      .take(pageSize);

    const [items, total] = await queryBuilder.getManyAndCount();

    return { items, total };
  }

  /**
   * 记录设备状态变更
   */
  async recordStatusChange(
    deviceId: string,
    fromStatus: DeviceStatus | null,
    toStatus: DeviceStatus,
    reason?: string,
    operatorId?: string,
    metadata?: Record<string, any>,
  ): Promise<DeviceStatusHistory> {
    const historyEntry = this.statusHistoryRepository.create({
      deviceId,
      fromStatus,
      toStatus,
      reason: reason || null,
      operatorId: operatorId || null,
      metadata: metadata || null,
      timestamp: new Date(),
    });

    return this.statusHistoryRepository.save(historyEntry);
  }

  /**
   * 触发设备OTA升级
   */
  async triggerOtaUpgrade(
    deviceId: string,
    targetVersion: string,
    operatorId: string,
    requestInfo?: { ipAddress?: string; userAgent?: string },
  ): Promise<{
    taskId: string;
    status: string;
    progress: number;
    device: Device;
    otaCommand: {
      cmd: string;
      version: string;
      url: string;
      timestamp: string;
    };
  }> {
    // 查找设备
    const device = await this.deviceRepository.findOne({
      where: { id: deviceId },
    });

    if (!device) {
      throw new NotFoundException(`设备不存在: ${deviceId}`);
    }

    // 检查设备状态
    if (device.status !== DeviceStatus.ONLINE) {
      throw new BadRequestException(`设备当前状态为 ${device.status}，只有在线设备可以执行OTA升级`);
    }

    // 检查版本是否相同
    if (device.firmwareVersion === targetVersion) {
      throw new BadRequestException(`设备当前已是 ${targetVersion} 版本`);
    }

    // TODO: 验证目标版本是否存在
    // const firmwareRelease = await this.firmwareReleaseRepository.findOne({
    //   where: { version: targetVersion, deviceType: device.deviceType },
    // });

    // 生成任务ID
    const taskId = `ota-${Date.now()}-${Math.random().toString(36).substring(2, 8)}`;

    // 生成OTA升级指令
    const otaCommand = {
      cmd: 'ota_upgrade',
      version: targetVersion,
      url: `${process.env.FIRMWARE_BASE_URL || 'https://firmware.example.com'}/${targetVersion}.bin`,
      timestamp: new Date().toISOString(),
    };

    // TODO: 通过MQTT下发OTA升级指令
    // await this.mqttService.publish(`device/${deviceId}/command`, otaCommand);

    this.logger.log(`OTA升级指令已发送: ${device.serialNumber} -> ${targetVersion} by ${operatorId}`);

    // 记录审计日志
    await this.auditLogService.createLog({
      action: 'ota_upgrade',
      targetType: 'Device',
      targetId: deviceId,
      operatorId,
      description: `触发OTA升级: ${device.serialNumber}, ${device.firmwareVersion || 'unknown'} -> ${targetVersion}`,
      oldValue: { firmwareVersion: device.firmwareVersion },
      newValue: { firmwareVersion: targetVersion, taskId },
      ipAddress: requestInfo?.ipAddress,
      userAgent: requestInfo?.userAgent,
    });

    return {
      taskId,
      status: 'downloading',
      progress: 0,
      device,
      otaCommand,
    };
  }

  /**
   * 更新OTA升级进度
   */
  async updateOtaProgress(
    deviceId: string,
    status: string,
    progress: number,
    firmwareVersion?: string,
  ): Promise<Device> {
    const device = await this.deviceRepository.findOne({
      where: { id: deviceId },
    });

    if (!device) {
      throw new NotFoundException(`设备不存在: ${deviceId}`);
    }

    // 如果升级完成，更新固件版本
    if (status === 'completed' && firmwareVersion) {
      device.firmwareVersion = firmwareVersion;
      await this.deviceRepository.save(device);
      this.logger.log(`OTA升级完成: ${device.serialNumber} -> ${firmwareVersion}`);
    }

    // 如果升级失败，记录日志
    if (status === 'failed') {
      this.logger.warn(`OTA升级失败: ${device.serialNumber}`);
    }

    return device;
  }

  /**
   * 绑定AI模型到设备
   */
  async bindModels(
    deviceId: string,
    bindDto: BindModelsDto,
    operatorId: string,
    requestInfo?: { ipAddress?: string; userAgent?: string },
  ): Promise<{
    bindings: DeviceModelBinding[];
    mqttCommand: { cmd: string; models: any[]; timestamp: string };
  }> {
    const device = await this.deviceRepository.findOne({
      where: { id: deviceId },
    });

    if (!device) {
      throw new NotFoundException(`设备不存在: ${deviceId}`);
    }

    // 验证设备状态
    if (device.status !== DeviceStatus.ONLINE) {
      throw new BadRequestException('只有在线设备才能绑定模型');
    }

    // 获取模型信息
    const models = await this.aiModelRepository.find({
      where: {
        id: In(bindDto.modelIds),
        status: AiModelStatus.PUBLISHED,
      },
    });

    if (models.length !== bindDto.modelIds.length) {
      const foundIds = models.map((m) => m.id);
      const missingIds = bindDto.modelIds.filter((id) => !foundIds.includes(id));
      throw new BadRequestException(
        `部分模型不存在或未发布: ${missingIds.join(', ')}`,
      );
    }

    // 验证模型与设备类型兼容性
    const incompatibleModels = models.filter(
      (model) =>
        model.applicableDeviceTypes &&
        model.applicableDeviceTypes.length > 0 &&
        !model.applicableDeviceTypes.includes(device.deviceType),
    );

    if (incompatibleModels.length > 0) {
      throw new BadRequestException(
        `以下模型不兼容设备类型 "${device.deviceType}": ${incompatibleModels.map((m) => m.name).join(', ')}`,
      );
    }

    // 检查是否已绑定
    const existingBindings = await this.bindingRepository.find({
      where: {
        deviceId,
        modelId: In(bindDto.modelIds),
      },
    });

    const existingModelIds = existingBindings.map((b) => b.modelId);
    const newModelIds = bindDto.modelIds.filter((id) => !existingModelIds.includes(id));

    if (newModelIds.length === 0) {
      throw new BadRequestException('所选模型已全部绑定到此设备');
    }

    // 创建绑定记录
    const bindings = this.bindingRepository.create(
      newModelIds.map((modelId) => ({
        deviceId,
        modelId,
        status: BindingStatus.PENDING,
        boundVersion: models.find((m) => m.id === modelId)?.version,
      })),
    );

    await this.bindingRepository.save(bindings);

    // 准备MQTT指令
    const mqttCommand = {
      cmd: 'load_model',
      models: newModelIds.map((id) => {
        const model = models.find((m) => m.id === id)!;
        return {
          id: model.id,
          code: model.code,
          version: model.version,
          url: model.fileUrl,
          checksum: model.checksum,
        };
      }),
      timestamp: new Date().toISOString(),
    };

    // 记录审计日志
    await this.auditLogService.createLog({
      action: 'bind_models',
      targetType: 'Device',
      targetId: deviceId,
      operatorId,
      description: `绑定AI模型到设备: ${device.name}, 模型: ${models.map((m) => m.name).join(', ')}`,
      newValue: { modelIds: newModelIds },
      ipAddress: requestInfo?.ipAddress,
      userAgent: requestInfo?.userAgent,
    });

    this.logger.log(
      `设备绑定模型: ${device.serialNumber} -> ${models.map((m) => m.code).join(', ')}`,
    );

    return {
      bindings,
      mqttCommand,
    };
  }

  /**
   * 解除设备与模型的绑定
   */
  async unbindModel(
    deviceId: string,
    modelId: string,
    operatorId: string,
    requestInfo?: { ipAddress?: string; userAgent?: string },
  ): Promise<{
    success: boolean;
    mqttCommand: { cmd: string; modelId: string; timestamp: string };
  }> {
    const device = await this.deviceRepository.findOne({
      where: { id: deviceId },
    });

    if (!device) {
      throw new NotFoundException(`设备不存在: ${deviceId}`);
    }

    const binding = await this.bindingRepository.findOne({
      where: { deviceId, modelId },
    });

    if (!binding) {
      throw new NotFoundException(`绑定关系不存在: 设备 ${deviceId}, 模型 ${modelId}`);
    }

    // 准备MQTT指令
    const mqttCommand = {
      cmd: 'unload_model',
      modelId,
      timestamp: new Date().toISOString(),
    };

    // 删除绑定记录
    await this.bindingRepository.remove(binding);

    // 记录审计日志
    await this.auditLogService.createLog({
      action: 'unbind_model',
      targetType: 'Device',
      targetId: deviceId,
      operatorId,
      description: `解除AI模型绑定: ${device.name}, 模型ID: ${modelId}`,
      oldValue: { modelId, status: binding.status },
      ipAddress: requestInfo?.ipAddress,
      userAgent: requestInfo?.userAgent,
    });

    this.logger.log(`设备解除模型绑定: ${device.serialNumber} <- ${modelId}`);

    return {
      success: true,
      mqttCommand,
    };
  }

  /**
   * 获取设备绑定的模型列表
   */
  async getBoundModels(
    deviceId: string,
    options?: { page?: number; pageSize?: number },
  ): Promise<{
    items: (DeviceModelBinding & { model?: AiModel })[];
    total: number;
    page: number;
    pageSize: number;
  }> {
    const { page = 1, pageSize = 20 } = options || {};

    const device = await this.deviceRepository.findOne({
      where: { id: deviceId },
    });

    if (!device) {
      throw new NotFoundException(`设备不存在: ${deviceId}`);
    }

    const [items, total] = await this.bindingRepository.findAndCount({
      where: { deviceId },
      relations: ['model'],
      order: { boundAt: 'DESC' },
      skip: (page - 1) * pageSize,
      take: pageSize,
    });

    return {
      items: items.map((item) => ({
        ...item,
        model: item.model,
      })),
      total,
      page,
      pageSize,
    };
  }

  /**
   * 确认模型加载成功（边缘设备回调）
   */
  async confirmModelLoaded(
    deviceId: string,
    modelId: string,
  ): Promise<DeviceModelBinding> {
    const binding = await this.bindingRepository.findOne({
      where: { deviceId, modelId },
    });

    if (!binding) {
      throw new NotFoundException(`绑定关系不存在`);
    }

    binding.status = BindingStatus.RUNNING;
    binding.lastSyncAt = new Date();

    return this.bindingRepository.save(binding);
  }

  /**
   * 确认模型加载失败（边缘设备回调）
   */
  async confirmModelLoadFailed(
    deviceId: string,
    modelId: string,
    error: string,
  ): Promise<DeviceModelBinding> {
    const binding = await this.bindingRepository.findOne({
      where: { deviceId, modelId },
    });

    if (!binding) {
      throw new NotFoundException(`绑定关系不存在`);
    }

    binding.status = BindingStatus.ERROR;
    binding.error = error;

    return this.bindingRepository.save(binding);
  }
}
