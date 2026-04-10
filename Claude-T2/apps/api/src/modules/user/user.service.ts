import { Injectable, NotFoundException, ConflictException, Logger, BadRequestException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import * as bcrypt from 'bcrypt';
import { User, UserStatus } from './user.entity';
import { CreateUserDto } from './dto/create-user.dto';
import { UserResponseDto } from './dto/user-response.dto';
import { UpdateUserRoleDto } from './dto/update-user-role.dto';
import { EmailService } from '../email/email.service';
import { RoleService } from '../role/role.service';
import { AuditLogService } from '../audit/audit-log.service';
import { AuditAction, AuditTargetType } from '../audit/audit-log.entity';
import { RedisService } from '../redis/redis.service';

export interface FindAllOptions {
  page: number;
  pageSize: number;
  status?: UserStatus;
  search?: string;
  roleId?: string;
}

@Injectable()
export class UserService {
  private readonly logger = new Logger(UserService.name);

  constructor(
    @InjectRepository(User)
    private readonly userRepository: Repository<User>,
    private readonly emailService: EmailService,
    private readonly roleService: RoleService,
    private readonly auditLogService: AuditLogService,
    private readonly redisService: RedisService,
  ) {}

  async findAll(
    options: FindAllOptions = { page: 1, pageSize: 20 },
  ): Promise<{ items: User[]; total: number; page: number; pageSize: number }> {
    const { page, pageSize, status, search, roleId } = options;
    const skip = (page - 1) * pageSize;

    const queryBuilder = this.userRepository
      .createQueryBuilder('user')
      .leftJoinAndSelect('user.role', 'role');

    if (status) {
      queryBuilder.andWhere('user.status = :status', { status });
    }

    if (roleId) {
      queryBuilder.andWhere('user.roleId = :roleId', { roleId });
    }

    if (search) {
      queryBuilder.andWhere(
        '(user.username LIKE :search OR user.name LIKE :search OR user.email LIKE :search)',
        { search: `%${search}%` },
      );
    }

    const [items, total] = await queryBuilder
      .skip(skip)
      .take(pageSize)
      .orderBy('user.createdAt', 'DESC')
      .getManyAndCount();

    return { items, total, page, pageSize };
  }

  async findOne(id: string): Promise<User> {
    const user = await this.userRepository.findOne({
      where: { id },
      relations: ['role'],
    });
    if (!user) {
      throw new NotFoundException(`User with ID "${id}" not found`);
    }
    return user;
  }

  async findByUsername(username: string): Promise<User | null> {
    return this.userRepository.findOne({
      where: { username },
      relations: ['role'],
    });
  }

  async create(createUserDto: CreateUserDto): Promise<User> {
    // 检查用户名唯一性
    const existingUser = await this.findByUsername(createUserDto.username);
    if (existingUser) {
      throw new ConflictException('用户名已存在');
    }

    const { password, ...rest } = createUserDto;

    // 哈希密码
    const passwordHash = await bcrypt.hash(password, 10);

    const user = this.userRepository.create({
      ...rest,
      passwordHash,
      status: UserStatus.PENDING,
    });
    const savedUser = await this.userRepository.save(user);

    // 发送激活邮件（可选功能，失败不影响用户创建）
    if (savedUser.email) {
      try {
        await this.emailService.sendActivationEmail(savedUser.email, savedUser.username);
      } catch (error) {
        this.logger.warn(`发送激活邮件失败: ${error.message}`);
      }
    }

    return savedUser;
  }

  async checkUsername(username: string): Promise<{ available: boolean }> {
    const user = await this.findByUsername(username);
    return { available: !user };
  }

  async update(id: string, userData: Partial<User>): Promise<User> {
    const user = await this.findOne(id);
    Object.assign(user, userData);
    return this.userRepository.save(user);
  }

  async updateStatus(id: string, status: UserStatus, operatorId?: string): Promise<User> {
    const user = await this.findOne(id);
    const oldStatus = user.status;
    user.status = status;
    const updatedUser = await this.userRepository.save(user);

    // 如果是禁用操作，撤销所有Token
    if (status === UserStatus.DISABLED) {
      await this.revokeAllUserTokens(id);
    }

    // 记录审计日志
    if (operatorId) {
      await this.auditLogService.createLog({
        action: AuditAction.UPDATE_STATUS,
        targetType: AuditTargetType.USER,
        targetId: id,
        operatorId,
        oldValue: { status: oldStatus },
        newValue: { status },
        description: `用户状态从 ${oldStatus} 变更为 ${status}`,
      });
    }

    return updatedUser;
  }

  /**
   * 更新用户角色
   * @param userId 目标用户ID
   * @param dto 角色更新DTO
   * @param operatorId 操作者ID
   */
  async updateRole(
    userId: string,
    dto: UpdateUserRoleDto,
    operatorId: string,
  ): Promise<User> {
    // 禁止修改自己的角色
    if (userId === operatorId) {
      throw new BadRequestException('不能修改自己的角色');
    }

    // 验证角色是否存在
    const role = await this.roleService.findOne(dto.roleId);
    if (!role) {
      throw new BadRequestException('指定的角色不存在');
    }

    // 获取用户信息（包含旧角色用于审计）
    const user = await this.findOne(userId);
    const oldRole = user.role;

    // 更新用户角色
    user.roleId = dto.roleId;
    user.role = role;
    const updatedUser = await this.userRepository.save(user);

    // 记录审计日志
    await this.auditLogService.createLog({
      action: AuditAction.ASSIGN_ROLE,
      targetType: AuditTargetType.USER,
      targetId: userId,
      operatorId,
      oldValue: {
        roleId: oldRole?.id || null,
        roleName: oldRole?.name || null,
        roleCode: oldRole?.code || null,
      },
      newValue: {
        roleId: role.id,
        roleName: role.name,
        roleCode: role.code,
      },
      description: `用户角色从 ${oldRole?.name || '无'} 变更为 ${role.name}`,
    });

    this.logger.log(
      `用户角色已更新: userId=${userId}, oldRole=${oldRole?.code || 'none'}, newRole=${role.code}, operator=${operatorId}`,
    );

    return updatedUser;
  }

  async remove(id: string, operatorId?: string): Promise<void> {
    const user = await this.findOne(id);

    // 撤销所有Token
    await this.revokeAllUserTokens(id);

    // 记录审计日志
    if (operatorId) {
      await this.auditLogService.createLog({
        action: AuditAction.DELETE_USER,
        targetType: AuditTargetType.USER,
        targetId: id,
        operatorId,
        oldValue: {
          username: user.username,
          name: user.name,
          email: user.email,
        },
        description: `删除用户 ${user.username}`,
      });
    }

    await this.userRepository.softRemove(user);
  }

  /**
   * 撤销用户所有Token
   */
  private async revokeAllUserTokens(userId: string): Promise<void> {
    try {
      const pattern = `refresh:${userId}:*`;
      const count = await this.redisService.delByPattern(pattern);
      this.logger.log(`已撤销用户 ${userId} 的 ${count} 个Token`);
    } catch (error) {
      this.logger.error(`撤销用户 ${userId} Token失败: ${error.message}`);
      // Token撤销失败不应影响主流程
    }
  }

  /**
   * 将 User 实体转换为响应 DTO（排除敏感字段）
   */
  toResponseDto(user: User): UserResponseDto {
    const { passwordHash, deletedAt, ...rest } = user;
    return rest as UserResponseDto;
  }

  /**
   * 批量转换 User 实体列表
   */
  toResponseDtoList(users: User[]): UserResponseDto[] {
    return users.map((user) => this.toResponseDto(user));
  }
}
