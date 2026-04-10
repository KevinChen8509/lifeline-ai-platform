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
import { ApiTags, ApiOperation, ApiBearerAuth, ApiResponse } from '@nestjs/swagger';
import { UserService } from './user.service';
import { CreateUserDto } from './dto/create-user.dto';
import { UpdateUserDto } from './dto/update-user.dto';
import { UpdateUserRoleDto } from './dto/update-user-role.dto';
import { CheckUsernameDto } from './dto/check-username.dto';
import { UserResponseDto } from './dto/user-response.dto';
import { UserStatus } from './user.entity';
import { PermissionsGuard } from '../../common/guards/permissions.guard';
import { RequirePermissions } from '../../common/decorators/permissions.decorator';

@Controller('users')
@UseGuards(AuthGuard('jwt'), PermissionsGuard)
@ApiTags('Users')
@ApiBearerAuth()
@RequirePermissions('manage', 'User')
export class UserController {
  constructor(private readonly userService: UserService) {}

  @Get('check-username')
  @ApiOperation({ summary: '检查用户名是否可用' })
  @ApiResponse({ status: 200, description: '检查成功' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  async checkUsername(
    @Query() query: CheckUsernameDto,
  ): Promise<{ available: boolean }> {
    return this.userService.checkUsername(query.username);
  }

  @Get()
  @ApiOperation({ summary: '获取用户列表' })
  @ApiResponse({ status: 200, description: '获取成功' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  async findAll(
    @Query('page') page = 1,
    @Query('pageSize') pageSize = 20,
    @Query('status') status?: UserStatus,
    @Query('search') search?: string,
    @Query('roleId') roleId?: string,
  ): Promise<{ items: UserResponseDto[]; total: number; page: number; pageSize: number }> {
    const result = await this.userService.findAll({ page, pageSize, status, search, roleId });
    return {
      ...result,
      items: this.userService.toResponseDtoList(result.items),
    };
  }

  @Get(':id')
  @ApiOperation({ summary: '获取用户详情' })
  @ApiResponse({ status: 200, description: '获取成功', type: UserResponseDto })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  async findOne(
    @Param('id', ParseUUIDPipe) id: string,
  ): Promise<UserResponseDto> {
    const user = await this.userService.findOne(id);
    return this.userService.toResponseDto(user);
  }

  @Post()
  @ApiOperation({ summary: '创建用户' })
  @ApiResponse({ status: 201, description: '创建成功', type: UserResponseDto })
  @ApiResponse({ status: 400, description: '请求参数错误' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  async create(@Body() createUserDto: CreateUserDto): Promise<UserResponseDto> {
    const user = await this.userService.create(createUserDto);
    return this.userService.toResponseDto(user);
  }

  @Put(':id')
  @ApiOperation({ summary: '更新用户' })
  @ApiResponse({ status: 200, description: '更新成功', type: UserResponseDto })
  @ApiResponse({ status: 400, description: '请求参数错误' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  async update(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() updateUserDto: UpdateUserDto,
  ): Promise<UserResponseDto> {
    const user = await this.userService.update(id, updateUserDto);
    return this.userService.toResponseDto(user);
  }

  @Put(':id/status')
  @ApiOperation({ summary: '更新用户状态' })
  @ApiResponse({ status: 200, description: '更新成功', type: UserResponseDto })
  @ApiResponse({ status: 400, description: '请求参数错误' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  async updateStatus(
    @Param('id', ParseUUIDPipe) id: string,
    @Body('status') status: UserStatus,
  ): Promise<UserResponseDto> {
    const user = await this.userService.updateStatus(id, status);
    return this.userService.toResponseDto(user);
  }

  @Put(':id/role')
  @ApiOperation({ summary: '分配用户角色' })
  @ApiResponse({ status: 200, description: '分配成功', type: UserResponseDto })
  @ApiResponse({ status: 400, description: '请求参数错误（如修改自己的角色）' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  async updateRole(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() updateUserRoleDto: UpdateUserRoleDto,
    @Req() req: { user: { sub: string } },
  ): Promise<UserResponseDto> {
    const user = await this.userService.updateRole(id, updateUserRoleDto, req.user.sub);
    return this.userService.toResponseDto(user);
  }

  @Delete(':id')
  @HttpCode(HttpStatus.NO_CONTENT)
  @ApiOperation({ summary: '删除用户' })
  @ApiResponse({ status: 204, description: '删除成功' })
  @ApiResponse({ status: 401, description: '未授权' })
  @ApiResponse({ status: 403, description: '没有权限' })
  async remove(@Param('id', ParseUUIDPipe) id: string): Promise<void> {
    return this.userService.remove(id);
  }
}
