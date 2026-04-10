import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  Index,
} from 'typeorm';

/**
 * 审计日志实体
 * 记录系统中所有重要的操作日志
 */
@Entity('audit_logs')
export class AuditLog {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  /**
   * 操作类型
   * 例如: CREATE_USER, UPDATE_USER, ASSIGN_ROLE, DELETE_USER
   */
  @Index()
  @Column({ length: 50 })
  action: string;

  /**
   * 目标资源类型
   * 例如: User, Role, Device, Project
   */
  @Index()
  @Column({ length: 50 })
  targetType: string;

  /**
   * 目标资源ID
   */
  @Index()
  @Column({ length: 36, nullable: true })
  targetId: string | null;

  /**
   * 操作者ID
   */
  @Index()
  @Column({ length: 36 })
  operatorId: string;

  /**
   * 操作者信息（快照）
   */
  @Column({ type: 'jsonb', nullable: true })
  operator: {
    id: string;
    username: string;
    name: string;
  } | null;

  /**
   * 变更前的值
   */
  @Column({ type: 'jsonb', nullable: true })
  oldValue: Record<string, any> | null;

  /**
   * 变更后的值
   */
  @Column({ type: 'jsonb', nullable: true })
  newValue: Record<string, any> | null;

  /**
   * 操作详情/备注
   */
  @Column({ type: 'text', nullable: true })
  description: string | null;

  /**
   * 客户端IP地址
   */
  @Column({ length: 45, nullable: true })
  ipAddress: string | null;

  /**
   * 用户代理
   */
  @Column({ type: 'text', nullable: true })
  userAgent: string | null;

  /**
   * 创建时间
   */
  @Index()
  @CreateDateColumn()
  createdAt: Date;
}

/**
 * 审计操作类型枚举
 */
export enum AuditAction {
  // 用户相关
  CREATE_USER = 'CREATE_USER',
  UPDATE_USER = 'UPDATE_USER',
  DELETE_USER = 'DELETE_USER',
  ASSIGN_ROLE = 'ASSIGN_ROLE',
  UPDATE_STATUS = 'UPDATE_STATUS',

  // 角色相关
  CREATE_ROLE = 'CREATE_ROLE',
  UPDATE_ROLE = 'UPDATE_ROLE',
  DELETE_ROLE = 'DELETE_ROLE',

  // 登录相关
  LOGIN = 'LOGIN',
  LOGOUT = 'LOGOUT',
  LOGIN_FAILED = 'LOGIN_FAILED',

  // 项目相关
  CREATE_PROJECT = 'CREATE_PROJECT',
  UPDATE_PROJECT = 'UPDATE_PROJECT',
  DELETE_PROJECT = 'DELETE_PROJECT',

  // 设备相关
  CREATE_DEVICE = 'CREATE_DEVICE',
  UPDATE_DEVICE = 'UPDATE_DEVICE',
  DELETE_DEVICE = 'DELETE_DEVICE',

  // 其他
  CUSTOM = 'CUSTOM',
}

/**
 * 审计目标类型枚举
 */
export enum AuditTargetType {
  USER = 'User',
  ROLE = 'Role',
  PROJECT = 'Project',
  DEVICE = 'Device',
  MODEL = 'Model',
  ALERT = 'Alert',
  API_KEY = 'ApiKey',
  SYSTEM = 'System',
}
