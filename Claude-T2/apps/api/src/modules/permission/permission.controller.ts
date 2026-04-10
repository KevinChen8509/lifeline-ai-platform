import { Controller, Get, Param, ParseUUIDPipe } from '@nestjs/common';
import { PermissionService } from './permission.service';
import { Permission } from './permission.entity';

@Controller('permissions')
export class PermissionController {
  constructor(private readonly permissionService: PermissionService) {}

  @Get()
  async findAll(): Promise<Permission[]> {
    return this.permissionService.findAll();
  }

  @Get('module/:module')
  async findByModule(module: string): Promise<Permission[]> {
    return this.permissionService.findByModule(module);
  }

  @Get(':id')
  async findOne(
    @Param('id', ParseUUIDPipe) id: string,
  ): Promise<Permission | null> {
    return this.permissionService.findOne(id);
  }
}
