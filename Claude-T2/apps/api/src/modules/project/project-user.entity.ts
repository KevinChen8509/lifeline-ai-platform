import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  ManyToOne,
  JoinColumn,
  Unique,
  Index,
} from 'typeorm';
import { Project } from './project.entity';
import { User } from '../user/user.entity';

/**
 * 项目成员角色枚举
 */
export enum ProjectRole {
  ADMIN = 'admin',    // 项目管理员 - 可管理成员和配置
  MEMBER = 'member',  // 项目成员 - 可查看和操作设备
}

/**
 * 项目-用户关联实体
 * 用于实现项目成员管理
 */
@Entity('project_users')
@Unique('uq_project_user', ['projectId', 'userId'])
export class ProjectUser {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index('idx_project_users_project')
  @Column({ name: 'project_id', type: 'uuid', comment: '项目ID' })
  projectId: string;

  @Index('idx_project_users_user')
  @Column({ name: 'user_id', type: 'uuid', comment: '用户ID' })
  userId: string;

  @Column({
    type: 'enum',
    enum: ProjectRole,
    default: ProjectRole.MEMBER,
    comment: '项目角色',
  })
  role: ProjectRole;

  @ManyToOne(() => Project, (project) => project.members, {
    onDelete: 'CASCADE',
  })
  @JoinColumn({ name: 'project_id' })
  project: Project;

  @ManyToOne(() => User, {
    onDelete: 'CASCADE',
  })
  @JoinColumn({ name: 'user_id' })
  user: User;

  @CreateDateColumn({ name: 'joined_at', comment: '加入时间' })
  joinedAt: Date;
}
