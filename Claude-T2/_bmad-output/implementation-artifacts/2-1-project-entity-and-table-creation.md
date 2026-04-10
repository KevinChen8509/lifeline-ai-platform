# Story 2.1: 项目实体与数据表创建

Status: done

## Story

**As a** 开发团队,
**I want** 创建项目相关的数据表和实体,
**So that** 后续所有项目相关功能有数据存储基础.

## Acceptance Criteria

1. **AC1: projects 表创建**
   - **Given** 数据库已初始化完成
   - **When** 执行数据库迁移
   - **Then** 系统创建 `projects` 表（id, name, code, description, settings JSON, status, created_at, updated_at）
   - **And** 创建唯一索引 `idx_projects_code`
   - **And** 创建普通索引 `idx_projects_status`

2. **AC2: project_users 关联表创建**
   - **Given** projects 表已创建
   - **When** 执行数据库迁移
   - **Then** 创建 `project_users` 关联表（project_id, user_id, role, joined_at）
   - **And** 创建外键约束

3. **AC3: TypeORM 实体定义**
   - **Given** 数据表已创建
   - **When** 创建 TypeORM Entity
   - **Then** Project 实体包含所有字段映射
   - **And** ProjectUser 实体包含关联关系
   - **And** 实体关系正确定义（Project has many ProjectUser, User has many ProjectUser）

4. **AC4: 单元测试**
   - **Given** 实体定义完成
   - **When** 运行测试
   - **Then** 实体字段验证通过
   - **And** 关系映射验证通过

## Tasks / Subtasks

- [ ] **Task 1: 创建 Project 实体** (AC: 1, 3)
  - [ ] 1.1 创建 project.entity.ts
  - [ ] 1.2 定义字段：id, name, code, description, settings, status
  - [ ] 1.3 添加索引装饰器

- [ ] **Task 2: 创建 ProjectUser 关联实体** (AC: 2, 3)
  - [ ] 2.1 创建 project-user.entity.ts
  - [ ] 2.2 定义关联关系
  - [ ] 2.3 添加外键约束

- [ ] **Task 3: 创建 Project 模块** (AC: 3)
  - [ ] 3.1 创建 project.module.ts
  - [ ] 3.2 注册实体到模块

- [ ] **Task 4: 单元测试** (AC: 4)
  - [ ] 4.1 测试实体字段定义
  - [ ] 4.2 测试关系映射

## Dev Notes

### 数据表设计

**projects 表:**
```sql
CREATE TABLE projects (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name VARCHAR(100) NOT NULL,
  code VARCHAR(20) NOT NULL UNIQUE,
  description TEXT,
  settings JSONB DEFAULT '{}',
  status VARCHAR(20) DEFAULT 'active',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_projects_code ON projects(code);
CREATE INDEX idx_projects_status ON projects(status);
```

**project_users 表:**
```sql
CREATE TABLE project_users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role VARCHAR(20) DEFAULT 'member',
  joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(project_id, user_id)
);

CREATE INDEX idx_project_users_project ON project_users(project_id);
CREATE INDEX idx_project_users_user ON project_users(user_id);
```

### ProjectStatus 枚举

```typescript
export enum ProjectStatus {
  ACTIVE = 'active',      // 活跃
  ARCHIVED = 'archived',  // 归档
}
```

### ProjectRole 枚举

```typescript
export enum ProjectRole {
  ADMIN = 'admin',    // 项目管理员
  MEMBER = 'member',  // 项目成员
}
```

### TypeORM 实体

**project.entity.ts:**
```typescript
@Entity('projects')
export class Project {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ length: 100 })
  name: string;

  @Column({ length: 20, unique: true })
  code: string;

  @Column({ type: 'text', nullable: true })
  description: string;

  @Column({ type: 'jsonb', default: {} })
  settings: Record<string, any>;

  @Column({
    type: 'enum',
    enum: ProjectStatus,
    default: ProjectStatus.ACTIVE,
  })
  status: ProjectStatus;

  @OneToMany(() => ProjectUser, (pu) => pu.project)
  members: ProjectUser[];

  @CreateDateColumn()
  createdAt: Date;

  @UpdateDateColumn()
  updatedAt: Date;
}
```

**project-user.entity.ts:**
```typescript
@Entity('project_users')
export class ProjectUser {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @ManyToOne(() => Project, (project) => project.members)
  project: Project;

  @ManyToOne(() => User)
  user: User;

  @Column({
    type: 'enum',
    enum: ProjectRole,
    default: ProjectRole.MEMBER,
  })
  role: ProjectRole;

  @CreateDateColumn()
  joinedAt: Date;
}
```

### 文件结构

```
apps/api/src/modules/project/
├── project.module.ts
├── project.entity.ts
├── project-user.entity.ts
├── project.service.ts
├── project.controller.ts
├── dto/
│   ├── create-project.dto.ts
│   └── update-project.dto.ts
└── __tests__/
    └── project.entity.spec.ts
```

### References

- [Source: epics.md#Story 2.1] - 原始Story定义
- [Source: FR1-FR5] - 项目与设备组织管理功能需求
- [Source: architecture.md] - 数据库命名约定

---

**Story Context Generated:** 2026-04-03
**Analysis Method:** Ultimate BMad Method Story Context Engine
