# Story 1.4: Token刷新与登出

Status: done

## Story

**As a** 用户,
**I want** 刷新Token或登出系统,
**So that** 保持会话连续性或安全退出系统.

## Acceptance Criteria

1. **AC1: Token自动刷新**
   - **Given** 用户已登录系统，Access Token即将过期
   - **When** 前端检测到Token即将过期（剩余<2分钟）或收到401错误
   - **Then** 系统自动使用Refresh Token获取新的Access Token
   - **And** 刷新成功后无感知继续使用（原请求自动重试）
   - **And** 刷新失败时跳转到登录页面

2. **AC2: 手动Token刷新**
   - **Given** 用户已登录系统
   - **When** 调用 POST /api/v1/auth/refresh 接口
   - **Then** 系统验证Refresh Token有效性
   - **And** 返回新的Access Token和Refresh Token
   - **And** 旧的Refresh Token失效

3. **AC3: 用户登出**
   - **Given** 用户已登录系统
   - **When** 用户点击"退出登录"或调用 POST /api/v1/auth/logout
   - **Then** 系统撤销Refresh Token（从Redis删除）
   - **And** 清除前端存储的Token（localStorage）
   - **And** 跳转到登录页面

4. **AC4: Refresh Token存储（Redis）**
   - **Given** 用户登录成功
   - **When** 系统生成Refresh Token
   - **Then** Token存储到Redis，key格式: `refresh:{userId}:{tokenId}`
   - **And** 设置过期时间7天
   - **And** 登出时从Redis删除对应Token

5. **AC5: 并发请求处理**
   - **Given** 多个请求同时返回401
   - **When** 触发Token刷新
   - **Then** 只发起一次刷新请求
   - **And** 所有等待的请求使用新Token重试

## Tasks / Subtasks

- [x] **Task 1: Redis服务集成** (AC: 4)
  - [x] 1.1 安装 ioredis 依赖
  - [x] 1.2 创建 Redis 配置 (`apps/api/src/config/redis.config.ts`) - 已存在
  - [x] 1.3 创建 RedisModule (`apps/api/src/modules/redis/redis.module.ts`)
  - [x] 1.4 创建 RedisService (`apps/api/src/modules/redis/redis.service.ts`)
  - [x] 1.5 配置环境变量 (REDIS_HOST, REDIS_PORT, REDIS_PASSWORD) - 已存在

- [x] **Task 2: 更新AuthService - Token存储** (AC: 4)
  - [x] 2.1 注入 RedisService 到 AuthService
  - [x] 2.2 在 generateRefreshToken 中存储Token到Redis
  - [x] 2.3 实现 storeRefreshToken 方法（集成在generateRefreshToken中）
  - [x] 2.4 实现 revokeRefreshToken 方法（logout和logoutByUserId）

- [x] **Task 3: Token刷新API** (AC: 2)
  - [x] 3.1 创建 RefreshTokenDto (`apps/api/src/modules/auth/dto/refresh-token.dto.ts`)
  - [x] 3.2 实现 AuthService.refreshToken 方法
  - [x] 3.3 在 AuthController 添加 POST /auth/refresh 端点
  - [x] 3.4 验证Refresh Token有效性
  - [x] 3.5 生成新Token对，使旧Token失效

- [x] **Task 4: 登出API** (AC: 3)
  - [x] 4.1 创建 LogoutDto (从Token中提取userId)
  - [x] 4.2 实现 AuthService.logout 方法
  - [x] 4.3 在 AuthController 添加 POST /auth/logout 端点
  - [x] 4.4 使用 AuthGuard('jwt') 保护登出端点
  - [x] 4.5 从Redis删除Refresh Token（logoutByUserId按模式删除）

- [x] **Task 5: 前端Token刷新逻辑** (AC: 1, 5)
  - [x] 5.1 更新 user store 添加 logoutUser 方法
  - [x] 5.2 更新 request.ts 添加Token刷新逻辑
  - [x] 5.3 实现401拦截和自动刷新
  - [x] 5.4 实现并发请求队列（只刷新一次）
  - [x] 5.5 刷新失败时清除Token并跳转登录页

- [x] **Task 6: 前端登出功能** (AC: 3)
  - [x] 6.1 创建 auth API 模块 (`apps/web/src/api/auth.ts`)
  - [x] 6.2 实现 logout API 调用
  - [x] 6.3 更新 user store 的 logoutUser 方法调用API
  - [x] 6.4 布局组件可使用 logoutUser 进行退出登录

- [x] **Task 7: 单元测试** (AC: All)
  - [x] 7.1 测试 AuthService.refreshToken 正常流程
  - [x] 7.2 测试无效Refresh Token场景
  - [x] 7.3 测试 AuthService.logout
  - [x] 7.4 测试 AuthService.logoutByUserId
  - [x] 7.5 测试前端Token刷新逻辑（通过手动验证）

## Dev Notes

### 前置依赖 [Source: Story 1.3]

**已完成的实现:**
- AuthModule, AuthController, AuthService 已创建
- JWT策略配置完成（Access Token 15min, Refresh Token 7d）
- 登录API POST /auth/login 已实现
- 前端 user store 支持 Token 管理
- Axios 拦截器基础配置完成

**本次实现:**
- RedisModule 和 RedisService 用于存储 Refresh Token
- Token刷新API POST /auth/refresh
- 登出API POST /auth/logout
- 前端401自动刷新逻辑（并发请求队列）
- 前端 logoutUser 方法调用API

### API 端点清单

| 方法 | 路径 | 说明 | 状态 |
|------|------|------|------|
| POST | /api/auth/login | 用户登录 | 已完成 (Story 1.3) |
| POST | /api/auth/refresh | 刷新Token | ✅ 新增 |
| POST | /api/auth/logout | 用户登出 | ✅ 新增 |

### 错误码定义

| HTTP状态 | 业务码 | 说明 |
|----------|--------|------|
| 200 | 0 | 操作成功 |
| 401 | - | 无效的Refresh Token / Token已过期 |
| 400 | - | 参数验证失败 |

### 环境变量

```env
# Redis Configuration
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# JWT Configuration (已有)
JWT_SECRET=your-super-secret-key-at-least-32-characters
JWT_ACCESS_EXPIRATION=15m
JWT_REFRESH_EXPIRATION=7d
```

### References

- [Source: epics.md#Story 1.4] - 原始Story定义
- [Source: architecture.md#Authentication & Security] - JWT + Refresh Token 认证方案
- [Source: architecture.md#Data Architecture] - Redis 缓存配置
- [Source: Story 1.3] - 登录实现（AuthService, AuthController, user store）

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6 (claude-opus-4-6-20250528)

### Debug Log References

无错误日志

### Completion Notes List

1. ✅ **Redis服务集成**
   - 安装 ioredis 依赖
   - 创建 RedisModule（全局模块）
   - 创建 RedisService（支持 set/get/del/exists/delByPattern）
   - 已添加到 AppModule imports

2. ✅ **AuthService 增强**
   - 注入 RedisService
   - generateRefreshToken 存储 Token 到 Redis（TTL 7天）
   - refreshToken 方法：验证、删除旧Token、生成新Token
   - logout 方法：删除单个 Token
   - logoutByUserId 方法：按模式删除所有 Token

3. ✅ **AuthController 更新**
   - POST /auth/refresh 端点
   - POST /auth/logout 端点（使用 AuthGuard('jwt') 保护）

4. ✅ **前端 Token 刷新逻辑**
   - 401 响应自动触发刷新
   - 并发请求队列管理（isRefreshing + refreshSubscribers）
   - 刷新成功后自动重试原请求
   - 刷新失败清除 Token 并跳转登录页

5. ✅ **前端登出功能**
   - 创建 auth.ts API 模块
   - user store 添加 logoutUser 方法（调用 API + 清除本地状态）

6. ✅ **单元测试**
   - auth.service.spec.ts 更新：16 tests 全部通过
   - 新增 refreshToken、logout、logoutByUserId 测试

### Review Findings

**Code Review Completed:** 2026-03-30

**PATCH findings (已修复):**
- [x] [Review][Patch] Redis KEYS 命令性能问题 — 已使用 SCAN 替代 KEYS
- [x] [Review][Patch] Token刷新竞态条件 — 已使用原子性 getDel 方法
- [x] [Review][Patch] delByPattern 模式无验证 — 已添加模式前缀验证
- [x] [Review][Patch] 前端请求队列内存泄漏 — 已在失败时 reject 所有 Promise
- [x] [Review][Patch] AC1 部分实现缺少主动Token刷新 — 已添加过期时间检查逻辑

**DEFER findings (延后处理):**
- [x] [Review][Defer] 登出后 Access Token 仍有效 — 需要架构级黑名单机制，延后处理
- [x] [Review][Defer] 用户状态变更后 Token 未失效 — 需要事件系统，延后处理
- [x] [Review][Defer] 大量 Key 删除参数溢出 — 极端边缘情况，已添加批处理

### File List

**后端 (apps/api/src/):**
- `modules/redis/redis.module.ts` - 新增：Redis模块
- `modules/redis/redis.service.ts` - 新增：Redis服务
- `modules/auth/dto/refresh-token.dto.ts` - 新增：刷新Token DTO
- `modules/auth/auth.service.ts` - 更新：注入RedisService，添加refreshToken/logout/logoutByUserId
- `modules/auth/auth.controller.ts` - 更新：添加refresh/logout端点
- `modules/auth/auth.service.spec.ts` - 更新：添加RedisService mock和刷新/登出测试
- `app.module.ts` - 更新：导入RedisModule

**前端 (apps/web/src/):**
- `api/auth.ts` - 新增：Auth API模块（login/refreshToken/logout）
- `api/request.ts` - 更新：Token刷新拦截器，并发请求队列
- `stores/user.ts` - 更新：添加logoutUser和clearState方法

---

**Story Context Generated:** 2026-03-27
**Analysis Method:** Ultimate BMad Method Story Context Engine
**Implementation Completed:** 2026-03-30
