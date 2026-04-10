# Story 1.2: 用户注册与账号创建

Status: done

## Story

**As a** 管理员,
**I want** 创建新的用户账号,
**So that** 新员工可以访问系统完成工作任务.

## Acceptance Criteria

1. **AC1: 用户名唯一性验证**
   - **Given** 管理员已登录系统
   - **When** 管理员填写用户信息并提交
   - **Then** 系统验证用户名唯一性
   - **And** 若用户名已存在，返回409错误并提示"用户名已存在"

2. **AC2: 密码安全存储**
   - **Given** 用户名验证通过
   - **When** 系统创建用户记录
   - **Then** 密码使用bcrypt加密（cost factor: 10）
   - **And** 明文密码不被存储或记录

3. **AC3: 默认状态设置**
   - **Given** 用户记录创建成功
   - **Then** 用户状态默认为"PENDING"（待激活）
   - **And** 用户无默认角色（roleId为null）

4. **AC4: 激活邮件发送（可选）**
   - **Given** 用户记录创建成功且配置了邮件服务
   - **When** 系统检测到邮件配置可用
   - **Then** 发送激活邮件到用户邮箱
   - **And** 若邮件服务不可用，记录警告日志但不影响用户创建

5. **AC5: API响应格式**
   - **Given** 用户创建成功
   - **When** 返回响应
   - **Then** 响应符合标准API格式
   - **And** 不返回passwordHash字段

## Tasks / Subtasks

- [x] **Task 1: 用户名唯一性验证** (AC: 1)
  - [x] 1.1 在 UserService.create() 中添加用户名检查逻辑
  - [x] 1.2 用户名已存在时抛出 ConflictException
  - [x] 1.3 添加 GET /api/users/check-username 端点
  - [x] 1.4 创建 CheckUsernameDto 验证器

- [x] **Task 2: 密码验证增强** (AC: 2)
  - [x] 2.1 在 CreateUserDto 中添加密码强度验证（正则表达式）
  - [x] 2.2 密码要求：至少6位，包含字母和数字
  - [x] 2.3 确认 bcrypt cost factor 为 10

- [x] **Task 3: 响应数据脱敏** (AC: 5)
  - [x] 3.1 创建 UserResponseDto 排除 passwordHash
  - [x] 3.2 修改 UserController.create() 返回 UserResponseDto
  - [x] 3.3 更新 TransformInterceptor 处理敏感字段

- [x] **Task 4: 邮件服务基础（可选功能）** (AC: 4)
  - [x] 4.1 创建 EmailModule 和 EmailService
  - [x] 4.2 配置 nodemailer（使用环境变量）
  - [x] 4.3 创建激活邮件模板
  - [x] 4.4 在 UserService.create() 中调用邮件服务（try-catch包裹）
  - [x] 4.5 添加邮件发送失败的日志记录

- [x] **Task 5: 前端注册表单** (AC: 1, 2, 5)
  - [x] 5.1 创建用户管理页面路由 /system/users
  - [x] 5.2 创建用户列表组件 UserList.vue
  - [x] 5.3 创建新增用户弹窗/表单 UserForm.vue
  - [x] 5.4 实现表单验证（用户名、密码强度）
  - [x] 5.5 调用 POST /api/users 创建用户
  - [x] 5.6 处理用户名重复错误提示

- [x] **Task 6: 单元测试** (AC: All)
  - [x] 6.1 测试 UserService.create() 正常流程
  - [x] 6.2 测试用户名重复场景
  - [x] 6.3 测试密码哈希验证
  - [x] 6.4 测试响应不含 passwordHash

## Dev Notes

### 前置依赖 [Source: Story 1.1]

**已完成的实现:**
- User 实体已创建 (`apps/api/src/modules/user/user.entity.ts`)
- UserStatus 枚举: PENDING, ACTIVE, DISABLED
- CreateUserDto 已创建，包含基本验证
- UserService.create() 已实现 bcrypt 哈希
- UserController 已有基础 CRUD 端点

**需要增强的部分:**
- 用户名唯一性检查（当前未检查）
- 密码强度验证
- 响应数据脱敏（排除 passwordHash）

### 技术实现指南

**1. 用户名唯一性检查:**

```typescript
// user.service.ts
async create(createUserDto: CreateUserDto): Promise<User> {
  // 检查用户名唯一性
  const existingUser = await this.findByUsername(createUserDto.username);
  if (existingUser) {
    throw new ConflictException('用户名已存在');
  }

  const { password, ...rest } = createUserDto;
  const passwordHash = await bcrypt.hash(password, 10);

  const user = this.userRepository.create({
    ...rest,
    passwordHash,
    status: UserStatus.PENDING,
  });
  return this.userRepository.save(user);
}

// 新增检查端点
async checkUsername(username: string): Promise<{ available: boolean }> {
  const user = await this.findByUsername(username);
  return { available: !user };
}
```

**2. 密码强度验证:**

```typescript
// create-user.dto.ts
import { Matches } from 'class-validator';

export class CreateUserDto {
  // ... 其他字段

  @IsString()
  @MinLength(6)
  @MaxLength(100)
  @Matches(/^(?=.*[A-Za-z])(?=.*\d).+$/, {
    message: '密码必须包含字母和数字',
  })
  password: string;
}
```

**3. 响应数据脱敏:**

```typescript
// user-response.dto.ts
export class UserResponseDto {
  id: string;
  username: string;
  name: string;
  email?: string;
  phone?: string;
  status: UserStatus;
  roleId?: string;
  role?: { id: string; name: string; code: string };
  createdAt: Date;
  updatedAt: Date;
}

// user.service.ts
toResponseDto(user: User): UserResponseDto {
  const { passwordHash, deletedAt, ...rest } = user;
  return rest;
}
```

**4. 邮件服务（可选）:**

```typescript
// email.service.ts
@Injectable()
export class EmailService {
  constructor(private configService: ConfigService) {}

  async sendActivationEmail(email: string, username: string): Promise<void> {
    const smtpConfig = this.configService.get('smtp');
    if (!smtpConfig?.host) {
      this.logger.warn('SMTP未配置，跳过发送激活邮件');
      return;
    }
    // nodemailer 发送逻辑
  }
}
```

### 前端实现指南

**用户管理页面路由:**

```typescript
// router/index.ts
{
  path: '/system/users',
  name: 'UserManagement',
  component: () => import('@/views/system/users/index.vue'),
  meta: { title: '用户管理' },
}
```

**用户表单验证:**

```typescript
// 用户名验证规则
const usernameRules = [
  { required: true, message: '请输入用户名' },
  { min: 3, max: 50, message: '用户名长度为3-50个字符' },
  { pattern: /^[a-zA-Z0-9_]+$/, message: '用户名只能包含字母、数字和下划线' },
];

// 密码验证规则
const passwordRules = [
  { required: true, message: '请输入密码' },
  { min: 6, max: 100, message: '密码长度为6-100个字符' },
  { pattern: /^(?=.*[A-Za-z])(?=.*\d).+$/, message: '密码必须包含字母和数字' },
];
```

### API 端点清单

| 方法 | 路径 | 说明 | 状态 |
|------|------|------|------|
| POST | /api/users | 创建用户 | 已存在，需增强 |
| GET | /api/users/check-username | 检查用户名可用性 | 新增 |
| GET | /api/users | 用户列表（分页） | 已存在 |
| GET | /api/users/:id | 获取用户详情 | 已存在 |

### 错误码定义

| 错误码 | HTTP状态 | 说明 |
|--------|----------|------|
| 0 | 200 | 成功 |
| 409 | 409 | 用户名已存在 |
| 400 | 400 | 参数验证失败 |
| 500 | 500 | 服务器内部错误 |

### 数据库注意事项

- users 表已创建，包含 username 唯一索引
- status 字段默认值为 'PENDING'
- 密码存储在 password_hash 列（bcrypt哈希）

### 测试要点

1. **正向测试:**
   - 创建用户成功，状态为 PENDING
   - 密码正确哈希（可验证登录）
   - 响应不包含 passwordHash

2. **异常测试:**
   - 用户名重复返回 409
   - 密码不符合强度要求返回 400
   - 必填字段缺失返回 400

3. **边界测试:**
   - 用户名最小/最大长度
   - 密码最小/最大长度
   - 特殊字符处理

### References

- [Source: epics.md#Story 1.2] - 原始Story定义
- [Source: architecture.md#Security] - 安全要求
- [Source: architecture.md#Naming Patterns] - 命名约定
- [Source: Story 1.1] - 已实现的 User 模块基础

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6 (claude-opus-4-6-20250528)

### Debug Log References

无错误日志

### Completion Notes List

1. ✅ 用户名唯一性验证
   - UserService.create() 添加了 findByUsername 检查
   - ConflictException 处理用户名重复场景
   - 新增 GET /api/users/check-username 端点
   - CheckUsernameDto 验证器

2. ✅ 密码验证增强
   - CreateUserDto 添加 Matches 装饰器（正则验证）
   - 密码要求：至少6位，包含字母和数字
   - bcrypt cost factor 保持为 10

3. ✅ 响应数据脱敏
   - 创建 UserResponseDto 排除 passwordHash
   - UserService.toResponseDto() 方法
   - UserController 所有方法返回 UserResponseDto

4. ✅ 邮件服务基础（可选功能）
   - EmailModule 全局模块
   - EmailService 使用 nodemailer
   - 激活邮件模板（HTML格式）
   - 配置检测：未配置时跳过发送

5. ✅ 前端注册表单
   - 用户管理页面 /system/users
   - 用户列表组件（表格+搜索+分页）
   - 用户表单弹窗（新建/编辑）
   - 表单验证（用户名、密码强度）
   - 用户名实时可用性检查

6. ✅ 单元测试
   - user.service.spec.ts 测试文件
   - 测试创建用户正常流程
   - 测试用户名重复场景
   - 测试密码哈希验证
   - 测试响应不含 passwordHash

### File List

**后端 (apps/api/src/modules/):**
- `user/user.service.ts` - 添加用户名检查、toResponseDto
- `user/user.controller.ts` - 添加 check-username 端点，使用 UserResponseDto
- `user/user.module.ts` - 导入 EmailModule
- `user/dto/create-user.dto.ts` - 添加密码强度验证
- `user/dto/check-username.dto.ts` - 用户名检查 DTO
- `user/dto/user-response.dto.ts` - 响应 DTO（排除敏感字段）
- `user/user.service.spec.ts` - 单元测试
- `email/email.module.ts` - 邮件模块
- `email/email.service.ts` - 邮件服务

**前端 (apps/web/src/):**
- `views/system/users/index.vue` - 用户管理页面
- `views/system/users/components/UserFormModal.vue` - 用户表单弹窗
- `router/index.ts` - 添加用户管理路由

**配置文件:**
- `apps/api/package.json` - 添加 nodemailer 依赖

---

**Story Context Generated:** 2026-03-27
**Analysis Method:** Ultimate BMad Method Story Context Engine
