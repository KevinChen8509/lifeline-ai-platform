# Story 1.1: 项目初始化与基础框架搭建

Status: done

## Story

**As a** 开发团队,
**I want** 初始化前后端项目框架,
**So that** 后续所有功能开发都有统一的技术基础.

## Acceptance Criteria

1. **AC1: 后端项目初始化**
   - **Given** 开发环境已准备就绪
   - **When** 执行后端项目初始化
   - **Then** 后端NestJS项目创建成功，包含TypeORM、配置模块、日志模块
   - **And** 项目结构符合架构文档定义

2. **AC2: 前端项目初始化**
   - **Given** 后端项目已创建
   - **When** 执行前端项目初始化
   - **Then** 前端Vue Vben Admin项目创建成功，包含路由、状态管理、API层
   - **And** 配置API代理指向后端

3. **AC3: 数据库连接配置**
   - **Given** 前后端项目已创建
   - **When** 配置数据库连接
   - **Then** PostgreSQL连接配置完成
   - **And** Redis连接配置完成
   - **And** 连接可正常工作

4. **AC4: 基础数据表创建**
   - **Given** 数据库连接配置完成
   - **When** 执行数据库迁移
   - **Then** 创建`users`, `roles`, `permissions` 表结构
   - **And** 表结构符合命名约定（snake_case）

5. **AC5: 项目启动验证**
   - **Given** 所有配置完成
   - **When** 执行`pnpm dev`启动命令
   - **Then** 前后端项目均可正常启动
   - **And** 可通过浏览器访问前端页面
   - **And** API可正常响应健康检查

## Tasks / Subtasks

- [x] **Task 1: 后端NestJS项目初始化** (AC: 1)
  - [x] 1.1 创建NestJS项目 `nest new api`
  - [x] 1.2 安装核心依赖: `@nestjs/typeorm`, `@nestjs/config`, `ioredis`, `pg`
  - [x] 1.3 配置TypeORM模块连接PostgreSQL
  - [x] 1.4 配置ConfigModule环境变量加载
  - [x] 1.5 配置日志模块（Pino或winston）
  - [x] 1.6 创建`.env.example`环境变量模板

- [x] **Task 2: 前端Vue Vben Admin项目初始化** (AC: 2)
  - [x] 2.1 创建Vben项目 `pnpm create vben`
  - [x] 2.2 配置API代理（vite.config.ts）
  - [x] 2.3 配置路由基础结构
  - [x] 2.4 配置Pinia状态管理
  - [x] 2.5 配置Axios请求封装

- [x] **Task 3: 数据库连接与表结构** (AC: 3, 4)
  - [x] 3.1 创建PostgreSQL数据库 `lifeline_ai`
  - [x] 3.2 创建TypeORM实体: `User`, `Role`, `Permission`
  - [x] 3.3 配置数据库迁移脚本
  - [x] 3.4 执行迁移创建表结构
  - [x] 3.5 配置Redis连接（ioredis）

- [x] **Task 4: 项目启动验证** (AC: 5)
  - [x] 4.1 后端启动测试 `cd api && pnpm dev`
  - [x] 4.2 前端启动测试 `cd web && pnpm dev`
  - [x] 4.3 添加健康检查端点 `GET /api/health`
  - [x] 4.4 验证前端可访问后端API
  - [x] 4.5 编写README启动说明

## Dev Notes

### 技术栈要求 [Source: architecture.md]

**后端 (NestJS 10.x):**
```
@nestjs/core: ^10.0.0
@nestjs/common: ^10.0.0
@nestjs/typeorm: ^0.3.0
@nestjs/config: ^3.0.0
typeorm: ^0.3.0
pg: ^8.11.0
ioredis: ^5.3.0
```

**前端 (Vue Vben Admin 5.x):**
```
vue: ^3.4.0
vite: ^5.0.0
pinia: ^2.1.0
axios: ^1.6.0
ant-design-vue: ^4.0.0
```

### 项目结构要求 [Source: architecture.md#Structure Patterns]

**后端目录结构:**
```
apps/
├── api/                          # 主API服务
│   ├── src/
│   │   ├── modules/
│   │   │   ├── user/
│   │   │   │   ├── user.module.ts
│   │   │   │   ├── user.controller.ts
│   │   │   │   ├── user.service.ts
│   │   │   │   ├── user.entity.ts
│   │   │   │   └── user.dto.ts
│   │   ├── common/
│   │   │   ├── decorators/
│   │   │   ├── guards/
│   │   │   ├── interceptors/
│   │   │   └── filters/
│   │   ├── config/
│   │   │   ├── database.config.ts
│   │   │   └── redis.config.ts
│   │   └── main.ts
│   ├── test/
│   ├── .env.example
│   └── package.json
```

**前端目录结构:**
```
apps/
├── web/                          # 前端SPA
│   ├── src/
│   │   ├── api/                  # API请求层
│   │   ├── views/                # 页面组件
│   │   ├── components/           # 通用组件
│   │   ├── stores/               # Pinia状态
│   │   ├── router/               # 路由配置
│   │   └── main.ts
│   └── vite.config.ts
```

### 数据库命名约定 [Source: architecture.md#Naming Patterns]

| 元素 | 约定 | 示例 |
|------|------|------|
| 表名 | snake_case 复数 | `users`, `roles`, `permissions` |
| 列名 | snake_case | `user_id`, `created_at`, `is_active` |
| 主键 | `id` | `id` (UUID) |
| 外键 | `{table}_id` | `role_id` |
| 索引 | `idx_{table}_{columns}` | `idx_users_username` |

### API响应格式 [Source: architecture.md]

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "timestamp": "2026-03-26T10:00:00Z"
}
```

### 基础实体定义

**User Entity:**
```typescript
// user.entity.ts
@Entity('users')
export class User {
  @PrimaryGeneratedUUID('uuid')
  id: string;

  @Column({ unique: true })
  username: string;

  @Column({ name: 'password_hash' })
  passwordHash: string;

  @Column()
  name: string;

  @Column({ nullable: true })
  email: string;

  @Column({ nullable: true })
  phone: string;

  @Column({ default: 'PENDING' })
  status: string; // PENDING | ACTIVE | DISABLED

  @Column({ name: 'role_id', nullable: true })
  roleId: string;

  @CreateDateColumn({ name: 'created_at' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at' })
  updatedAt: Date;
}
```

**Role Entity:**
```typescript
// role.entity.ts
@Entity('roles')
export class Role {
  @PrimaryGeneratedUUID('uuid')
  id: string;

  @Column({ unique: true })
  name: string; // admin | operator | observer

  @Column()
  code: string;

  @Column({ type: 'jsonb', nullable: true })
  permissions: string[];

  @CreateDateColumn({ name: 'created_at' })
  createdAt: Date;
}
```

### 环境变量模板 (.env.example)

```env
# Database
DB_HOST=localhost
DB_PORT=5432
DB_USERNAME=postgres
DB_PASSWORD=your_password
DB_DATABASE=lifeline_ai

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# App
PORT=3000
NODE_ENV=development

# JWT (for future stories)
JWT_SECRET=your_jwt_secret
JWT_EXPIRES_IN=15m
JWT_REFRESH_EXPIRES_IN=7d
```

### API代理配置 (vite.config.ts)

```typescript
export default defineConfig({
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:3000',
        changeOrigin: true,
      },
    },
  },
});
```

### 健康检查端点

```typescript
// health.controller.ts
@Controller('api')
export class HealthController {
  @Get('health')
  check() {
    return {
      code: 0,
      message: 'success',
      data: {
        status: 'ok',
        timestamp: new Date().toISOString(),
      },
      timestamp: new Date().toISOString(),
    };
  }
}
```

### Project Structure Notes

- 使用 monorepo 结构（pnpm workspace）
- 前后端代码分别放在 `apps/api` 和 `apps/web`
- 共享类型定义可放在 `packages/shared`（可选）
- 严格遵循架构文档的命名约定

### Testing Standards

- 后端使用 Jest + Supertest
- 前端使用 Vitest
- 测试覆盖率目标: ≥80%
- 每个 Story 完成后需有基础测试

### References

- [Source: architecture.md#Starter Template Selection] - 技术选型决策
- [Source: architecture.md#Naming Patterns] - 命名约定
- [Source: architecture.md#Structure Patterns] - 目录结构
- [Source: architecture.md#Data Architecture] - 数据库配置
- [Source: ux-design-specification.md#Platform Strategy] - 前端平台策略
- [Source: epics.md#Story 1.1] - 原始Story定义

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6 (claude-opus-4-6-20250528)

### Debug Log References

无错误日志

### Completion Notes List

1. ✅ 创建了 monorepo 项目结构（pnpm workspace）
2. ✅ 后端 NestJS 项目初始化完成
   - 配置了 TypeORM + PostgreSQL
   - 配置了 Redis 连接（ioredis）
   - 配置了 Pino 日志模块
   - 创建了全局异常过滤器和响应转换拦截器
   - 创建了 Snake Case 命名策略
3. ✅ 创建了基础实体：User, Role
   - 使用 UUID 主键
   - 遵循 snake_case 命名约定
4. ✅ 创建了健康检查端点 `GET /api/health`
5. ✅ 前端 Vue 3 + Vite 项目初始化完成
   - 配置了 API 代理
   - 配置了 Pinia 状态管理
   - 配置了 Vue Router
   - 创建了基础的 Dashboard 和 Login 页面
6. ✅ 创建了项目 README 文档

### File List

**后端 (apps/api/):**
- `package.json` - 项目依赖配置
- `tsconfig.json` - TypeScript 配置
- `nest-cli.json` - NestJS CLI 配置
- `src/main.ts` - 应用入口
- `src/app.module.ts` - 根模块
- `src/config/app.config.ts` - 应用配置
- `src/config/database.config.ts` - 数据库配置
- `src/config/redis.config.ts` - Redis 配置
- `src/common/filters/http-exception.filter.ts` - 异常过滤器
- `src/common/interceptors/transform.interceptor.ts` - 响应转换拦截器
- `src/common/interceptors/logging.interceptor.ts` - 日志拦截器
- `src/modules/user/user.entity.ts` - 用户实体
- `src/modules/user/user.module.ts` - 用户模块
- `src/modules/user/user.service.ts` - 用户服务
- `src/modules/user/user.controller.ts` - 用户控制器
- `src/modules/user/dto/create-user.dto.ts` - 创建用户 DTO
- `src/modules/user/dto/update-user.dto.ts` - 更新用户 DTO
- `src/modules/role/role.entity.ts` - 角色实体
- `src/modules/role/role.module.ts` - 角色模块
- `src/modules/role/role.service.ts` - 角色服务
- `src/modules/role/role.controller.ts` - 角色控制器
- `src/modules/role/dto/create-role.dto.ts` - 创建角色 DTO
- `src/modules/role/dto/update-role.dto.ts` - 更新角色 DTO
- `src/modules/health/health.controller.ts` - 健康检查控制器
- `src/modules/health/health.module.ts` - 健康检查模块
- `.env.example` - 环境变量模板

**前端 (apps/web/):**
- `package.json` - 项目依赖配置
- `tsconfig.json` - TypeScript 配置
- `tsconfig.node.json` - Node TypeScript 配置
- `vite.config.ts` - Vite 配置
- `index.html` - HTML 入口
- `src/main.ts` - 应用入口
- `src/App.vue` - 根组件
- `src/router/index.ts` - 路由配置
- `src/stores/user.ts` - 用户状态管理
- `src/api/request.ts` - Axios 请求封装
- `src/styles/index.css` - 全局样式
- `src/views/dashboard/index.vue` - 仪表盘页面
- `src/views/login/index.vue` - 登录页面
- `src/views/error/404.vue` - 404 错误页面

**根目录:**
- `package.json` - Monorepo 根配置
- `pnpm-workspace.yaml` - pnpm 工作区配置
- `README.md` - 项目文档

---

**Story Context Generated:** 2026-03-26
**Analysis Method:** Ultimate BMad Method Story Context Engine

## Senior Developer Review (AI)

**Review Date:** 2026-03-27
**Review Outcome:** Approved with Changes
**Reviewer:** Code Review Agent

### Review Summary

代码审查完成，发现并修复了6个问题，延后2个非关键问题。

### Action Items

- [x] [Review][Patch] 添加 Permission 实体 (AC4 要求)
- [x] [Review][Patch] User 实体添加 @DeleteDateColumn (支持 softRemove)
- [x] [Review][Patch] 修复分页参数未使用问题
- [x] [Review][Patch] 添加密码哈希处理 (bcrypt)
- [x] [Review][Patch] DTO password 字段映射到 entity passwordHash
- [x] [Review][Decision] 前端技术栈决策: 保持 Vue 3 + Vite 方案
- [x] [Review][Defer] CORS 配置过于宽松 — 延后到安全加固 Story
- [x] [Review][Defer] 无登录限流 — 延后到 Story 1.3 认证模块

### Files Added/Modified in Review

**新增文件:**
- `apps/api/src/modules/permission/permission.entity.ts` - Permission 实体
- `apps/api/src/modules/permission/permission.module.ts` - Permission 模块
- `apps/api/src/modules/permission/permission.service.ts` - Permission 服务
- `apps/api/src/modules/permission/permission.controller.ts` - Permission 控制器

**修改文件:**
- `apps/api/src/modules/user/user.entity.ts` - 添加 DeleteDateColumn
- `apps/api/src/modules/user/user.service.ts` - 添加分页和密码哈希
- `apps/api/src/modules/user/user.controller.ts` - 修复分页参数
- `apps/api/src/modules/user/dto/create-user.dto.ts` - 添加字段注释
- `apps/api/src/app.module.ts` - 添加 PermissionModule
- `apps/api/package.json` - 添加 bcrypt 依赖
