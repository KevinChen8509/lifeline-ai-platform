# Story 4.10: 模型版本管理

Status: completed

## Story

**As a** 管理员,
**I want** 管理AI模型的版本,
**So that** 跟踪模型迭代和发布历史.

## Acceptance Criteria

1. **AC1: 版本列表展示**
   - **Given** 管理员进入模型管理页面
   - **When** 管理员点击某个模型的"版本管理"
   - **Then** 系统显示该模型的所有版本列表（版本号、发布时间、变更说明、状态）

2. **AC2: 上传新版本**
   - **Given** 管理员在版本管理页面
   - **When** 上传新版本模型文件
   - **Then** 系统创建新版本记录，状态为"草稿"

3. **AC3: 设置当前发布版本**
   - **Given** 存在已发布的版本
   - **When** 管理员点击"发布"
   - **Then** 该版本成为当前发布版本
   旧版本自动变为非当前

4. **AC4: 下线旧版本**
   - **Given** 存在已发布的版本
   - **When** 管理员点击"下线"
   - **Then** 版本状态变更为DEPRECATED

5. **AC5: Checksum验证**
   - **Given** 上传模型文件
   - **When** 生成文件校验和
   - **Then** 每个版本有唯一的checksum（SHA256）

## Tasks / Subtasks

- [x] **Task 1: 创建版本实体** (AC: 1-5)
  - [x] 1.1 创建 ai-model-version.entity.ts
  - [x] 1.2 定义版本状态枚举
  - [x] 1.3 添加 checksum 和 signature 字段

- [x] **Task 2: 实现版本管理服务** (AC: 1-5)
  - [x] 2.1 getVersions 方法
  - [x] 2.2 getVersion 方法
  - [x] 2.3 createVersion 方法
  - [x] 2.4 publishVersion 方法
  - [x] 2.5 deprecateVersion 方法
  - [x] 2.6 getCurrentVersion 方法

- [x] **Task 3: 添加控制器端点** (AC: 1-5)
  - [x] 3.1 GET /ai-models/:id/versions
  - [x] 3.2 GET /ai-models/:id/versions/:versionId
  - [x] 3.3 POST /ai-models/:id/versions
  - [x] 3.4 PUT /ai-models/:id/versions/:versionId/publish
  - [x] 3.5 PUT /ai-models/:id/versions/:versionId/deprecate
  - [x] 3.6 GET /ai-models/:id/versions/current

- [x] **Task 4: 创建DTO** (AC: 1-5)
  - [x] 4.1 CreateModelVersionDto
  - [x] 4.2 UpdateModelVersionDto
  - [x] 4.3 DeployModelDto

## Dev Notes

### API 端点

- `GET /api/v1/ai-models/{modelId}/versions`
- `GET /api/v1/ai-models/{modelId}/versions/{versionId}`
- `POST /api/v1/ai-models/{modelId}/versions`
- `PUT /api/v1/ai-models/{modelId}/versions/{versionId}`
- `PUT /api/v1/ai-models/{modelId}/versions/{versionId}/publish`
- `PUT /api/v1/ai-models/{modelId}/versions/{versionId}/deprecate`
- `GET /api/v1/ai-models/{modelId}/versions/current`

### 版本状态枚举

| 状态 | 代码 | 说明 |
|------|------|------|
| 草稿 | DRAFT | 版本开发中 |
| 已发布 | PUBLISHED | 版本可用 |
| 已下线 | DEPRECATED | 版本已废弃 |

### 版本号格式

语义化版本: `v{major}.{minor}.{patch}` 或 `v{major}.{minor}.{patch}-{suffix}`

示例: `v2.1.0`, `v2.1.0-beta`, `v2.1.0-rc1`

### References

- [Source: epics.md#Story 4.10] - 原始Story定义
- [Source: FR16] - 模型版本管理功能需求

---

**Story Implemented:** 2026-04-08
