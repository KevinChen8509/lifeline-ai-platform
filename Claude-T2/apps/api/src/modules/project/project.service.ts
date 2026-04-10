import {
  Injectable,
  ConflictException,
  NotFoundException,
  BadRequestException,
  Logger,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository, DataSource } from 'typeorm';
import { Project, ProjectStatus } from './project.entity';
import { ProjectUser, ProjectRole } from './project-user.entity';
import { CreateProjectDto } from './dto/create-project.dto';
import { UpdateProjectDto } from './dto/update-project.dto';
import { AddMemberDto, UpdateMemberRoleDto } from './dto/add-member.dto';
import { ProjectOverviewDto, DeviceStatsDto, RecentActivityDto } from './dto/project-overview.dto';
import { AuditLogService } from '../audit/audit-log.service';

@Injectable()
export class ProjectService {
  private readonly logger = new Logger(ProjectService.name);

  constructor(
    @InjectRepository(Project)
    private readonly projectRepository: Repository<Project>,
    @InjectRepository(ProjectUser)
    private readonly projectUserRepository: Repository<ProjectUser>,
    private readonly dataSource: DataSource,
    private readonly auditLogService: AuditLogService,
  ) {}

  /**
   * 创建项目
   * @param createProjectDto 创建项目DTO
   * @param creatorId 创建者ID
   * @param requestInfo 请求信息（用于审计日志）
   */
  async create(
    createProjectDto: CreateProjectDto,
    creatorId: string,
    requestInfo?: { ipAddress?: string; userAgent?: string },
  ): Promise<Project> {
    const { name, code, description } = createProjectDto;

    // 检查项目编码是否已存在
    const existingProject = await this.projectRepository.findOne({
      where: { code },
    });

    if (existingProject) {
      throw new ConflictException(`项目编码 "${code}" 已存在`);
    }

    // 使用事务创建项目和关联记录
    const project = await this.dataSource.transaction(async (manager) => {
      // 创建项目
      const newProject = manager.create(Project, {
        name,
        code,
        description,
        status: ProjectStatus.ACTIVE,
      });
      const savedProject = await manager.save(newProject);

      // 将创建者添加为项目管理员
      const projectUser = manager.create(ProjectUser, {
        projectId: savedProject.id,
        userId: creatorId,
        role: ProjectRole.ADMIN,
      });
      await manager.save(projectUser);

      return savedProject;
    });

    // 记录审计日志
    await this.auditLogService.createLog({
      action: 'create',
      targetType: 'Project',
      targetId: project.id,
      operatorId: creatorId,
      description: `创建项目: ${name} (${code})`,
      ipAddress: requestInfo?.ipAddress,
      userAgent: requestInfo?.userAgent,
    });

    this.logger.log(`项目创建成功: ${code} by user ${creatorId}`);

    return project;
  }

  /**
   * 查找所有项目（分页）
   */
  async findAll(options: {
    page?: number;
    pageSize?: number;
    search?: string;
    status?: ProjectStatus;
    userId?: string;
  }): Promise<{ items: Project[]; total: number; page: number; pageSize: number }> {
    const { page = 1, pageSize = 20, search, status, userId } = options;

    const queryBuilder = this.projectRepository
      .createQueryBuilder('project')
      .leftJoinAndSelect('project.members', 'member');

    // 如果指定了用户ID，只返回该用户参与的项目
    if (userId) {
      queryBuilder.andWhere(
        'project.id IN (SELECT pu.project_id FROM project_users pu WHERE pu.user_id = :userId)',
        { userId },
      );
    }

    // 搜索条件
    if (search) {
      queryBuilder.andWhere(
        '(project.name ILIKE :search OR project.code ILIKE :search)',
        { search: `%${search}%` },
      );
    }

    // 状态筛选
    if (status) {
      queryBuilder.andWhere('project.status = :status', { status });
    }

    // 排序和分页
    queryBuilder
      .orderBy('project.createdAt', 'DESC')
      .skip((page - 1) * pageSize)
      .take(pageSize);

    const [items, total] = await queryBuilder.getManyAndCount();

    return { items, total, page, pageSize };
  }

  /**
   * 根据ID查找项目
   */
  async findOne(id: string): Promise<Project> {
    const project = await this.projectRepository.findOne({
      where: { id },
      relations: ['members', 'members.user'],
    });

    if (!project) {
      throw new NotFoundException(`项目不存在: ${id}`);
    }

    return project;
  }

  /**
   * 更新项目
   */
  async update(
    id: string,
    updateProjectDto: UpdateProjectDto,
    operatorId: string,
    requestInfo?: { ipAddress?: string; userAgent?: string },
  ): Promise<Project> {
    const project = await this.findOne(id);

    // 如果更新编码，检查唯一性
    if (updateProjectDto.code && updateProjectDto.code !== project.code) {
      const existingProject = await this.projectRepository.findOne({
        where: { code: updateProjectDto.code },
      });
      if (existingProject) {
        throw new ConflictException(`项目编码 "${updateProjectDto.code}" 已存在`);
      }
    }

    Object.assign(project, updateProjectDto);
    const updatedProject = await this.projectRepository.save(project);

    // 记录审计日志
    await this.auditLogService.createLog({
      action: 'update',
      targetType: 'Project',
      targetId: id,
      operatorId,
      description: `更新项目: ${project.name} (${project.code})`,
      ipAddress: requestInfo?.ipAddress,
      userAgent: requestInfo?.userAgent,
    });

    return updatedProject;
  }

  /**
   * 归档项目
   */
  async archive(
    id: string,
    operatorId: string,
    requestInfo?: { ipAddress?: string; userAgent?: string },
  ): Promise<Project> {
    const project = await this.findOne(id);

    if (project.status === ProjectStatus.ARCHIVED) {
      throw new BadRequestException('项目已处于归档状态');
    }

    project.status = ProjectStatus.ARCHIVED;
    const archivedProject = await this.projectRepository.save(project);

    // 记录审计日志
    await this.auditLogService.createLog({
      action: 'archive',
      targetType: 'Project',
      targetId: id,
      operatorId,
      description: `归档项目: ${project.name} (${project.code})`,
      ipAddress: requestInfo?.ipAddress,
      userAgent: requestInfo?.userAgent,
    });

    this.logger.log(`项目归档成功: ${project.code} by user ${operatorId}`);
    return archivedProject;
  }

  /**
   * 恢复项目
   */
  async restore(
    id: string,
    operatorId: string,
    requestInfo?: { ipAddress?: string; userAgent?: string },
  ): Promise<Project> {
    const project = await this.findOne(id);

    if (project.status !== ProjectStatus.ARCHIVED) {
      throw new BadRequestException('项目未处于归档状态');
    }

    project.status = ProjectStatus.ACTIVE;
    const restoredProject = await this.projectRepository.save(project);

    // 记录审计日志
    await this.auditLogService.createLog({
      action: 'restore',
      targetType: 'Project',
      targetId: id,
      operatorId,
      description: `恢复项目: ${project.name} (${project.code})`,
      ipAddress: requestInfo?.ipAddress,
      userAgent: requestInfo?.userAgent,
    });

    this.logger.log(`项目恢复成功: ${project.code} by user ${operatorId}`);
    return restoredProject;
  }

  /**
   * 删除项目（归档）
   * @deprecated 使用 archive 方法代替
   */
  async remove(
    id: string,
    operatorId: string,
    requestInfo?: { ipAddress?: string; userAgent?: string },
  ): Promise<void> {
    await this.archive(id, operatorId, requestInfo);
  }

  /**
   * 检查用户是否是项目成员
   */
  async isProjectMember(projectId: string, userId: string): Promise<boolean> {
    const projectUser = await this.projectUserRepository.findOne({
      where: { projectId, userId },
    });
    return !!projectUser;
  }

  /**
   * 检查用户是否是项目管理员
   */
  async isProjectAdmin(projectId: string, userId: string): Promise<boolean> {
    const projectUser = await this.projectUserRepository.findOne({
      where: { projectId, userId, role: ProjectRole.ADMIN },
    });
    return !!projectUser;
  }

  /**
   * 获取项目概览（设备统计和最近活动）
   * 注意：设备统计目前返回占位数据，待 Epic 3 设备管理实现后更新
   */
  async getOverview(id: string): Promise<ProjectOverviewDto> {
    // 确保项目存在
    await this.findOne(id);

    // TODO: 当 Epic 3 设备管理实现后，从设备表查询真实数据
    // 目前返回占位数据
    const stats: DeviceStatsDto = {
      totalDevices: 0,
      onlineDevices: 0,
      offlineDevices: 0,
      alertDevices: 0,
    };

    // TODO: 当设备状态历史记录实现后，查询最近活动
    const recentActivities: RecentActivityDto[] = [];

    return {
      stats,
      recentActivities,
    };
  }

  /**
   * 添加项目成员
   */
  async addMember(
    projectId: string,
    addMemberDto: AddMemberDto,
    operatorId: string,
    requestInfo?: { ipAddress?: string; userAgent?: string },
  ): Promise<ProjectUser> {
    // 确保项目存在
    await this.findOne(projectId);

    // 检查用户是否已是成员
    const existingMember = await this.projectUserRepository.findOne({
      where: { projectId, userId: addMemberDto.userId },
    });

    if (existingMember) {
      throw new ConflictException('该用户已是项目成员');
    }

    // 添加成员
    const projectUser = this.projectUserRepository.create({
      projectId,
      userId: addMemberDto.userId,
      role: addMemberDto.role,
    });
    const savedMember = await this.projectUserRepository.save(projectUser);

    // 记录审计日志
    await this.auditLogService.createLog({
      action: 'add_member',
      targetType: 'Project',
      targetId: projectId,
      operatorId,
      description: `添加项目成员: ${addMemberDto.userId}, 角色: ${addMemberDto.role}`,
      ipAddress: requestInfo?.ipAddress,
      userAgent: requestInfo?.userAgent,
    });

    return savedMember;
  }

  /**
   * 更新成员角色
   */
  async updateMemberRole(
    projectId: string,
    userId: string,
    role: ProjectRole,
    operatorId: string,
    requestInfo?: { ipAddress?: string; userAgent?: string },
  ): Promise<ProjectUser> {
    // 确保项目存在
    await this.findOne(projectId);

    // 查找成员
    const member = await this.projectUserRepository.findOne({
      where: { projectId, userId },
    });

    if (!member) {
      throw new NotFoundException('该用户不是项目成员');
    }

    // 不能修改自己的角色
    if (userId === operatorId) {
      throw new BadRequestException('不能修改自己的角色');
    }

    // 更新角色
    member.role = role;
    const updatedMember = await this.projectUserRepository.save(member);

    // 记录审计日志
    await this.auditLogService.createLog({
      action: 'update_member_role',
      targetType: 'Project',
      targetId: projectId,
      operatorId,
      description: `更新成员角色: ${userId}, 新角色: ${role}`,
      ipAddress: requestInfo?.ipAddress,
      userAgent: requestInfo?.userAgent,
    });

    return updatedMember;
  }

  /**
   * 移除项目成员
   */
  async removeMember(
    projectId: string,
    userId: string,
    operatorId: string,
    requestInfo?: { ipAddress?: string; userAgent?: string },
  ): Promise<void> {
    // 确保项目存在
    await this.findOne(projectId);

    // 查找成员
    const member = await this.projectUserRepository.findOne({
      where: { projectId, userId },
    });

    if (!member) {
      throw new NotFoundException('该用户不是项目成员');
    }

    // 不能移除自己
    if (userId === operatorId) {
      throw new BadRequestException('不能移除自己');
    }

    // 检查是否是最后一个管理员
    if (member.role === ProjectRole.ADMIN) {
      const adminCount = await this.projectUserRepository.count({
        where: { projectId, role: ProjectRole.ADMIN },
      });
      if (adminCount <= 1) {
        throw new BadRequestException('不能移除最后一个项目管理员');
      }
    }

    // 移除成员
    await this.projectUserRepository.remove(member);

    // 记录审计日志
    await this.auditLogService.createLog({
      action: 'remove_member',
      targetType: 'Project',
      targetId: projectId,
      operatorId,
      description: `移除项目成员: ${userId}`,
      ipAddress: requestInfo?.ipAddress,
      userAgent: requestInfo?.userAgent,
    });
  }

  /**
   * 获取项目成员列表
   */
  async getMembers(projectId: string): Promise<ProjectUser[]> {
    // 确保项目存在
    await this.findOne(projectId);

    return this.projectUserRepository.find({
      where: { projectId },
      relations: ['user'],
      order: { joinedAt: 'ASC' },
    });
  }
}
