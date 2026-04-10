import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
  Index,
} from 'typeorm';

@Entity('permissions')
export class Permission {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index('idx_permissions_code', { unique: true })
  @Column({ length: 100 })
  code: string;

  @Column({ length: 100 })
  name: string;

  @Column({ length: 200, nullable: true })
  description: string;

  @Column({ length: 50, nullable: true })
  module: string;

  @Column({ default: true })
  isActive: boolean;

  @CreateDateColumn({ name: 'created_at' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at' })
  updatedAt: Date;
}
