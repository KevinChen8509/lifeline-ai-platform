import { Injectable, NotFoundException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { Role, RoleCode } from './role.entity';

@Injectable()
export class RoleService {
  constructor(
    @InjectRepository(Role)
    private readonly roleRepository: Repository<Role>,
  ) {}

  async findAll(): Promise<Role[]> {
    return this.roleRepository.find({
      order: { createdAt: 'ASC' },
    });
  }

  async findOne(id: string): Promise<Role> {
    const role = await this.roleRepository.findOne({ where: { id } });
    if (!role) {
      throw new NotFoundException(`Role with ID "${id}" not found`);
    }
    return role;
  }

  async findByCode(code: RoleCode): Promise<Role | null> {
    return this.roleRepository.findOne({ where: { code } });
  }

  async create(roleData: Partial<Role>): Promise<Role> {
    const role = this.roleRepository.create(roleData);
    return this.roleRepository.save(role);
  }

  async update(id: string, roleData: Partial<Role>): Promise<Role> {
    const role = await this.findOne(id);
    Object.assign(role, roleData);
    return this.roleRepository.save(role);
  }

  async remove(id: string): Promise<void> {
    const role = await this.findOne(id);
    await this.roleRepository.remove(role);
  }

  async seedDefaultRoles(): Promise<void> {
    const defaultRoles = [
      {
        name: '管理员',
        code: RoleCode.ADMIN,
        description: '系统管理员，拥有所有权限',
        permissions: ['*'],
      },
      {
        name: '运维员',
        code: RoleCode.OPERATOR,
        description: '运维人员，可操作设备和处理预警',
        permissions: ['device:read', 'device:write', 'alert:read', 'alert:write', 'telemetry:read'],
      },
      {
        name: '观察员',
        code: RoleCode.OBSERVER,
        description: '观察员，只读权限',
        permissions: ['device:read', 'alert:read', 'telemetry:read'],
      },
    ];

    for (const roleData of defaultRoles) {
      const existing = await this.findByCode(roleData.code);
      if (!existing) {
        await this.create(roleData);
      }
    }
  }
}
