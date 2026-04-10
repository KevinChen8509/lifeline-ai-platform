import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
  OneToMany,
  Index,
} from 'typeorm';
import { ProjectUser } from './project-user.entity';

/**
 * 项目状态枚举
 */
export enum ProjectStatus {
  ACTIVE = 'active',
  ARCHIVED = 'archived',
}

/**
 * 项目实体
 * 用于组织设备和实现数据隔离
 */
@Entity('projects')
export class Project {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ length: 100, comment: '项目名称' })
  name: string;

  @Index('idx_projects_code', { unique: true })
  @Column({ length: 20, unique: true, comment: '项目编码（全局唯一）' })
  code: string;

  @Column({ type: 'text', nullable: true, comment: '项目描述' })
  description: string;

  @Column({
    type: 'jsonb',
    default: {},
    comment: '项目配置（JSON格式）',
  })
  settings: Record<string, any>;

  @Index('idx_projects_status')
  @Column({
    type: 'enum',
    enum: ProjectStatus,
    default: ProjectStatus.ACTIVE,
    comment: '项目状态',
  })
  status: ProjectStatus;

  @OneToMany(() => ProjectUser, (pu) => pu.project)
  members: ProjectUser[];

  @CreateDateColumn({ name: 'created_at', comment: '创建时间' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at', comment: '更新时间' })
  updatedAt: Date;
}
