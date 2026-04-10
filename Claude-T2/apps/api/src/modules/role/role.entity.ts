import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
  Index,
} from 'typeorm';

export enum RoleCode {
  ADMIN = 'admin',
  OPERATOR = 'operator',
  OBSERVER = 'observer',
}

@Entity('roles')
export class Role {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index('idx_roles_name', { unique: true })
  @Column({ length: 50 })
  name: string;

  @Index('idx_roles_code', { unique: true })
  @Column({ length: 50 })
  code: string;

  @Column({ length: 200, nullable: true })
  description: string;

  @Column({ type: 'jsonb', nullable: true })
  permissions: string[];

  @CreateDateColumn({ name: 'created_at' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at' })
  updatedAt: Date;
}
