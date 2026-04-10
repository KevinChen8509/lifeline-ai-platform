import {
  Controller,
  Get,
  Post,
  Put,
  Delete,
  Body,
  Param,
  Query,
  ParseUUIDPipe,
  HttpCode,
  HttpStatus,
  UseGuards,
  Req,
} from '@nestjs/common';
import { AuthGuard } from '@nestjs/passport';
import {
  ApiTags,
  ApiOperation,
  ApiBearerAuth,
  ApiResponse,
} from '@nestjs/swagger';
import { Request } from 'express';
import { ProjectService } from './project.service';
import { CreateProjectDto } from './dto/create-project.dto';
import { UpdateProjectDto } from './dto/update-project.dto';
import { ProjectResponseDto } from './dto/project-response.dto';
import { ProjectOverviewDto } from './dto/project-overview.dto';
import { AddMemberDto, UpdateMemberRoleDto } from './dto/add-member.dto';
import { Project, ProjectStatus } from './project.entity';
import { ProjectRole } from './project-user.entity';
import { PermissionsGuard } from '../../common/guards/permissions.guard';
import { RequirePermissions } from '../../common/decorators/permissions.decorator';

@Controller('projects')
@UseGuards(AuthGuard('jwt'), PermissionsGuard)
@ApiTags('Projects')
@ApiBearerAuth()
@RequirePermissions('manage', 'Project')
export class ProjectController {
  constructor(private readonly projectService: ProjectService) {}

  @Post()
  @ApiOperation({ summary: '创建项目' })
  @ApiResponse({ status: 201, description: '创建成功', type: ProjectResponseDto })
  @ApiResponse({ status: 400, description: '请求参数错误' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  @ApiResponse({ status: 409, description: '项目编码已存在' })
  async create(
    @Body() createProjectDto: CreateProjectDto,
    @Req() req: Request & { user: { sub: string } },
  ): Promise<Project> {
    const requestInfo = {
      ipAddress: this.extractIpAddress(req),
      userAgent: req.headers['user-agent'],
    };
    return this.projectService.create(createProjectDto, req.user.sub, requestInfo);
  }

  @Get()
  @ApiOperation({ summary: '获取项目列表' })
  @ApiResponse({ status: 200, description: '获取成功' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  async findAll(
    @Query('page') page = 1,
    @Query('pageSize') pageSize = 20,
    @Query('search') search?: string,
    @Query('status') status?: ProjectStatus,
  ): Promise<{ items: Project[]; total: number; page: number; pageSize: number }> {
    return this.projectService.findAll({ page, pageSize, search, status });
  }

  @Get(':id')
  @ApiOperation({ summary: '获取项目详情' })
  @ApiResponse({ status: 200, description: '获取成功', type: ProjectResponseDto })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  @ApiResponse({ status: 404, description: '项目不存在' })
  async findOne(
    @Param('id', ParseUUIDPipe) id: string,
  ): Promise<Project> {
    return this.projectService.findOne(id);
  }

  @Get(':id/overview')
  @ApiOperation({ summary: '获取项目概览' })
  @ApiResponse({ status: 200, description: '获取成功', type: ProjectOverviewDto })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  @ApiResponse({ status: 404, description: '项目不存在' })
  async getOverview(
    @Param('id', ParseUUIDPipe) id: string,
  ): Promise<ProjectOverviewDto> {
    return this.projectService.getOverview(id);
  }

  @Put(':id')
  @ApiOperation({ summary: '更新项目' })
  @ApiResponse({ status: 200, description: '更新成功', type: ProjectResponseDto })
  @ApiResponse({ status: 400, description: '请求参数错误' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  @ApiResponse({ status: 404, description: '项目不存在' })
  @ApiResponse({ status: 409, description: '项目编码已存在' })
  async update(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() updateProjectDto: UpdateProjectDto,
    @Req() req: Request & { user: { sub: string } },
  ): Promise<Project> {
    const requestInfo = {
      ipAddress: this.extractIpAddress(req),
      userAgent: req.headers['user-agent'],
    };
    return this.projectService.update(id, updateProjectDto, req.user.sub, requestInfo);
  }

  @Delete(':id')
  @HttpCode(HttpStatus.NO_CONTENT)
  @ApiOperation({ summary: '归档项目' })
  @ApiResponse({ status: 204, description: '归档成功' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  @ApiResponse({ status: 404, description: '项目不存在' })
  async remove(
    @Param('id', ParseUUIDPipe) id: string,
    @Req() req: Request & { user: { sub: string } },
  ): Promise<void> {
    const requestInfo = {
      ipAddress: this.extractIpAddress(req),
      userAgent: req.headers['user-agent'],
    };
    return this.projectService.remove(id, req.user.sub, requestInfo);
  }

  @Put(':id/archive')
  @ApiOperation({ summary: '归档项目' })
  @ApiResponse({ status: 200, description: '归档成功', type: ProjectResponseDto })
  @ApiResponse({ status: 400, description: '项目已处于归档状态' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  @ApiResponse({ status: 404, description: '项目不存在' })
  async archive(
    @Param('id', ParseUUIDPipe) id: string,
    @Req() req: Request & { user: { sub: string } },
  ): Promise<Project> {
    const requestInfo = {
      ipAddress: this.extractIpAddress(req),
      userAgent: req.headers['user-agent'],
    };
    return this.projectService.archive(id, req.user.sub, requestInfo);
  }

  @Put(':id/restore')
  @ApiOperation({ summary: '恢复项目' })
  @ApiResponse({ status: 200, description: '恢复成功', type: ProjectResponseDto })
  @ApiResponse({ status: 400, description: '项目未处于归档状态' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  @ApiResponse({ status: 404, description: '项目不存在' })
  async restore(
    @Param('id', ParseUUIDPipe) id: string,
    @Req() req: Request & { user: { sub: string } },
  ): Promise<Project> {
    const requestInfo = {
      ipAddress: this.extractIpAddress(req),
      userAgent: req.headers['user-agent'],
    };
    return this.projectService.restore(id, req.user.sub, requestInfo);
  }

  // ==================== 项目成员管理 ====================

  @Get(':id/members')
  @ApiOperation({ summary: '获取项目成员列表' })
  @ApiResponse({ status: 200, description: '获取成功' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  @ApiResponse({ status: 404, description: '项目不存在' })
  async getMembers(@Param('id', ParseUUIDPipe) id: string) {
    return this.projectService.getMembers(id);
  }

  @Post(':id/members')
  @ApiOperation({ summary: '添加项目成员' })
  @ApiResponse({ status: 201, description: '添加成功' })
  @ApiResponse({ status: 400, description: '请求参数错误' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  @ApiResponse({ status: 404, description: '项目不存在' })
  @ApiResponse({ status: 409, description: '用户已是项目成员' })
  async addMember(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() addMemberDto: AddMemberDto,
    @Req() req: Request & { user: { sub: string } },
  ) {
    const requestInfo = {
      ipAddress: this.extractIpAddress(req),
      userAgent: req.headers['user-agent'],
    };
    return this.projectService.addMember(id, addMemberDto, req.user.sub, requestInfo);
  }

  @Put(':id/members/:userId')
  @ApiOperation({ summary: '更新成员角色' })
  @ApiResponse({ status: 200, description: '更新成功' })
  @ApiResponse({ status: 400, description: '请求参数错误' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  @ApiResponse({ status: 404, description: '成员不存在' })
  async updateMemberRole(
    @Param('id', ParseUUIDPipe) id: string,
    @Param('userId', ParseUUIDPipe) userId: string,
    @Body() updateRoleDto: UpdateMemberRoleDto,
    @Req() req: Request & { user: { sub: string } },
  ) {
    const requestInfo = {
      ipAddress: this.extractIpAddress(req),
      userAgent: req.headers['user-agent'],
    };
    return this.projectService.updateMemberRole(id, userId, updateRoleDto.role, req.user.sub, requestInfo);
  }

  @Delete(':id/members/:userId')
  @HttpCode(HttpStatus.NO_CONTENT)
  @ApiOperation({ summary: '移除项目成员' })
  @ApiResponse({ status: 204, description: '移除成功' })
  @ApiResponse({ status: 400, description: '不能移除自己或最后一个管理员' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  @ApiResponse({ status: 404, description: '成员不存在' })
  async removeMember(
    @Param('id', ParseUUIDPipe) id: string,
    @Param('userId', ParseUUIDPipe) userId: string,
    @Req() req: Request & { user: { sub: string } },
  ): Promise<void> {
    const requestInfo = {
      ipAddress: this.extractIpAddress(req),
      userAgent: req.headers['user-agent'],
    };
    return this.projectService.removeMember(id, userId, req.user.sub, requestInfo);
  }

  /**
   * 提取客户端真实IP地址
   */
  private extractIpAddress(req: Request): string {
    let ip: string | undefined;

    const xForwardedFor = req.headers['x-forwarded-for'] as string | string[] | undefined;
    if (xForwardedFor) {
      ip = Array.isArray(xForwardedFor) ? xForwardedFor[0] : xForwardedFor.split(',')[0];
      ip = ip?.trim();
    }

    if (!ip) {
      const xRealIp = req.headers['x-real-ip'] as string | string[] | undefined;
      if (xRealIp) {
        ip = Array.isArray(xRealIp) ? xRealIp[0] : xRealIp;
      }
    }

    if (!ip) {
      const socket = (req as any).socket;
      if (socket?.remoteAddress) {
        ip = socket.remoteAddress;
      }
    }

    return this.validateIpAddress(ip);
  }

  /**
   * 验证IP地址格式
   */
  private validateIpAddress(ip: string | undefined): string {
    if (!ip || ip === 'unknown') {
      return 'unknown';
    }

    const ipWithoutPort = ip.split(':')[0];
    const ipv4Regex = /^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$/;
    const ipv6Regex = /^(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$|^::(?:[0-9a-fA-F]{1,4}:){0,6}[0-9a-fA-F]{1,4}$|^(?:[0-9a-fA-F]{1,4}:){1,7}:$|^(?:[0-9a-fA-F]{1,4}:){0,6}::(?:[0-9a-fA-F]{1,4}:){0,5}[0-9a-fA-F]{1,4}$/;

    if (ipWithoutPort === 'localhost' || ipWithoutPort === '::1' || ipWithoutPort === '127.0.0.1') {
      return ipWithoutPort;
    }

    if (ipv4Regex.test(ipWithoutPort) || ipv6Regex.test(ipWithoutPort)) {
      return ipWithoutPort;
    }

    return 'unknown';
  }
}
