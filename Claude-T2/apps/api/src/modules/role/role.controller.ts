import {
  Controller,
  Get,
  Post,
  Put,
  Delete,
  Body,
  Param,
  ParseUUIDPipe,
  HttpCode,
  HttpStatus,
  UseGuards,
} from '@nestjs/common';
import { AuthGuard } from '@nestjs/passport';
import { ApiTags, ApiOperation, ApiBearerAuth, ApiResponse } from '@nestjs/swagger';
import { RoleService } from './role.service';
import { CreateRoleDto } from './dto/create-role.dto';
import { UpdateRoleDto } from './dto/update-role.dto';
import { Role } from './role.entity';
import { PermissionsGuard } from '../../common/guards/permissions.guard';
import { RequirePermissions } from '../../common/decorators/permissions.decorator';

@Controller('roles')
@UseGuards(AuthGuard('jwt'), PermissionsGuard)
@ApiTags('Roles')
@ApiBearerAuth()
@RequirePermissions('manage', 'Role')
export class RoleController {
  constructor(private readonly roleService: RoleService) {}

  @Get()
  @ApiOperation({ summary: '获取角色列表' })
  @ApiResponse({ status: 200, description: '获取成功', type: [Role] })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  async findAll(): Promise<Role[]> {
    return this.roleService.findAll();
  }

  @Get(':id')
  @ApiOperation({ summary: '获取角色详情' })
  @ApiResponse({ status: 200, description: '获取成功', type: Role })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  async findOne(@Param('id', ParseUUIDPipe) id: string): Promise<Role> {
    return this.roleService.findOne(id);
  }

  @Post()
  @ApiOperation({ summary: '创建角色' })
  @ApiResponse({ status: 201, description: '创建成功', type: Role })
  @ApiResponse({ status: 400, description: '请求参数错误' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  async create(@Body() createRoleDto: CreateRoleDto): Promise<Role> {
    return this.roleService.create(createRoleDto);
  }

  @Put(':id')
  @ApiOperation({ summary: '更新角色' })
  @ApiResponse({ status: 200, description: '更新成功', type: Role })
  @ApiResponse({ status: 400, description: '请求参数错误' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  async update(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() updateRoleDto: UpdateRoleDto,
  ): Promise<Role> {
    return this.roleService.update(id, updateRoleDto);
  }

  @Delete(':id')
  @HttpCode(HttpStatus.NO_CONTENT)
  @ApiOperation({ summary: '删除角色' })
  @ApiResponse({ status: 204, description: '删除成功' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  async remove(@Param('id', ParseUUIDPipe) id: string): Promise<void> {
    return this.roleService.remove(id);
  }
}
