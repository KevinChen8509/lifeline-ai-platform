# Story 1.12: API 权限守卫

Status: done

## Story

**As a** 后端系统,
**I want** 所有 API 端点都有权限控制,
**So that** 只有授权用户才能访问相应功能.

## Acceptance Criteria

1. **AC1: 权限守卫实现**
   - **Given** 用户访问需要权限的 API
   - **When** 请求到达后端
   - **Then** 系统检查用户权限
   - **And** 无权限时返回 403 错误

2. **AC2: 控制器权限配置**
   - **Given** 控制器定义
   - **When** 添加 @RequirePermissions 装饰器
   - **Then** 该控制器所有方法都需要指定权限

3. **AC3: 公开端点保护**
   - **Given** 公共 API（登录、健康检查）
   - **When** 访问这些端点
   - **Then** 不需要权限检查

4. **AC4: 权限一致性**
   - **Given** 前端菜单权限
   - **When** 调用后端 API
   - **Then** API 权限与菜单权限一致

## Tasks / Subtasks

- [x] **Task 1: 权限守卫实现** (AC: 1)
  - [x] 1.1 PermissionsGuard 已实现
  - [x] 1.2 使用 CASL Ability 进行权限检查
  - [x] 1.3 无权限时抛出 ForbiddenException

- [x] **Task 2: 权限装饰器** (AC: 2)
  - [x] 2.1 @RequirePermissions 装饰器已实现
  - [x] 2.2 支持类级别和方法级别装饰

- [x] **Task 3: 控制器权限配置** (AC: 2, 3, 4)
  - [x] 3.1 UserController: @RequirePermissions('manage', 'User')
  - [x] 3.2 RoleController: @RequirePermissions('manage', 'Role')
  - [x] 3.3 AuditLogController: @RequirePermissions('read', 'AuditLog')
  - [x] 3.4 AuthController: 公共端点（登录/刷新）+ 认证端点（登出/权限）
  - [x] 3.5 HealthController: 公共健康检查端点
  - [x] 3.6 PermissionController: 公共权限查询端点

- [x] **Task 4: 单元测试**
  - [x] 4.1 PermissionsGuard 测试已通过
  - [x] 4.2 所有 76 个测试通过

## Dev Notes

### 已存在的实现

**权限守卫 (common/guards/permissions.guard.ts):**
```typescript
@Injectable()
export class PermissionsGuard implements CanActivate {
  constructor(
    private readonly reflector: Reflector,
    private readonly abilityFactory: AbilityFactory,
  ) {}

  canActivate(context: ExecutionContext): boolean {
    const requiredPermission = this.reflector.getAllAndOverride<{
      action: string;
      subject: string;
    }>(PERMISSIONS_KEY, [context.getHandler(), context.getClass()]);

    if (!requiredPermission) {
      return true;
    }

    const user = request.user as User;
    const ability = this.abilityFactory.createForUser(user);
    const hasPermission = ability.can(action, subject);

    if (!hasPermission) {
      throw new ForbiddenException(`没有权限执行此操作: ${action} ${subject}`);
    }
    return true;
  }
}
```

**权限装饰器 (common/decorators/permissions.decorator.ts):**
```typescript
export const RequirePermissions = (action: string, subject: string) =>
  SetMetadata(PERMISSIONS_KEY, { action, subject });
```

**控制器权限配置示例:**
```typescript
@Controller('users')
@UseGuards(AuthGuard('jwt'), PermissionsGuard)
@RequirePermissions('manage', 'User')
export class UserController { ... }
```

### API 权限配置表

| 路由 | 权限 | 角色要求 |
|------|------|---------|
| POST /api/auth/login | 公开 | 无 |
| POST /api/auth/refresh | 公开 | 无 |
| POST /api/auth/logout | 需认证 | 登录即可 |
| GET /api/auth/permissions | 需认证 | 登录即可 |
| GET /api/health | 公开 | 无 |
| GET /api/permissions | 公开 | 无 |
| /api/users/* | manage:User | admin |
| /api/roles/* | manage:Role | admin |
| /api/audit-logs/* | read:AuditLog | admin |

### References

- [Source: Story 1.5] - 角色定义和权限规则
- [Source: FR51] - 系统可以根据用户角色限制功能访问权限

---

**Story Context Generated:** 2026-04-03
**Analysis Method:** Ultimate BMad Method Story Context Engine
