import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { Permission } from './permission.entity';

@Injectable()
export class PermissionService {
  constructor(
    @InjectRepository(Permission)
    private readonly permissionRepository: Repository<Permission>,
  ) {}

  async findAll(): Promise<Permission[]> {
    return this.permissionRepository.find({
      order: { module: 'ASC', code: 'ASC' },
    });
  }

  async findByModule(module: string): Promise<Permission[]> {
    return this.permissionRepository.find({
      where: { module },
      order: { code: 'ASC' },
    });
  }

  async findOne(id: string): Promise<Permission | null> {
    return this.permissionRepository.findOne({ where: { id } });
  }

  async create(permissionData: Partial<Permission>): Promise<Permission> {
    const permission = this.permissionRepository.create(permissionData);
    return this.permissionRepository.save(permission);
  }
}
