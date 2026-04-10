# 生命线AI感知云平台

Cloud-Edge-Device IoT Platform for Urban Lifeline Infrastructure

## 项目简介

生命线AI感知云平台是一个面向城市生命线基础设施（排水、供水、桥梁、燃气）的智能感知云平台。通过云-边-端三层架构，实现设备快速上线、实时监测、AI预警、数据分析的一体化管理。

### 核心特性

- 🚀 **设备10分钟上线** - 扫码即用，零配置
- 🤖 **边缘AI推理** - ≤100ms响应，离线可用
- 🔔 **实时预警** - WebSocket推送，≤5秒延迟
- 🔐 **等保三级认证** - 信创适配，数据本地化

## 技术栈

### 后端

- **框架**: NestJS 10.x
- **ORM**: TypeORM 0.3.x
- **数据库**: PostgreSQL 15+
- **时序数据库**: ClickHouse 23+
- **缓存**: Redis 7+
- **MQTT Broker**: EMQX 5.x

### 前端

- **框架**: Vue 3.4+
- **构建工具**: Vite 5.x
- **UI组件**: Ant Design Vue 4.x
- **状态管理**: Pinia 2.x
- **图表**: ECharts 5.x
- **地图**: 天地图

## 项目结构

```
Claude-T2/
├── apps/
│   ├── api/                    # 后端 API 服务
│   │   ├── src/
│   │   │   ├── modules/        # 业务模块
│   │   │   ├── common/         # 通用组件
│   │   │   ├── config/         # 配置文件
│   │   │   └── main.ts         # 入口文件
│   │   ├── test/               # 测试文件
│   │   └── package.json
│   │
│   └── web/                    # 前端应用
│       ├── src/
│       │   ├── api/            # API 请求
│       │   ├── components/     # 组件
│       │   ├── router/         # 路由
│       │   ├── stores/         # 状态管理
│       │   ├── views/          # 页面
│       │   └── main.ts         # 入口文件
│       └── package.json
│
├── packages/                   # 共享包
│   └── shared/                 # 共享类型定义
│
├── _bmad-output/               # BMAD 输出文档
│   ├── planning-artifacts/     # 规划文档
│   │   ├── prd.md
│   │   ├── architecture.md
│   │   ├── epics.md
│   │   └── ux-design-specification.md
│   └── implementation-artifacts/  # 实现文档
│       └── sprint-status.yaml
│
├── pnpm-workspace.yaml         # pnpm 工作区配置
└── package.json                # 根 package.json
```

## 快速开始

### 环境要求

- Node.js >= 18.0.0
- pnpm >= 8.0.0
- PostgreSQL >= 15
- Redis >= 7
- (可选) ClickHouse >= 23

### 安装依赖

```bash
# 安装 pnpm（如果未安装）
npm install -g pnpm

# 安装项目依赖
pnpm install
```

### 配置环境变量

```bash
# 复制后端环境变量模板
cp apps/api/.env.example apps/api/.env

# 编辑环境变量
# 修改数据库连接信息等
```

### 创建数据库

```sql
-- PostgreSQL
CREATE DATABASE lifeline_ai;
```

### 启动开发服务器

```bash
# 同时启动前后端
pnpm dev

# 或分别启动
pnpm dev:api    # 后端: http://localhost:3000
pnpm dev:web    # 前端: http://localhost:5173
```

### 访问应用

- 前端: http://localhost:5173
- API: http://localhost:3000/api
- API文档: http://localhost:3000/api/docs
- 健康检查: http://localhost:3000/api/health

## 开发指南

### 代码规范

- 数据库命名: snake_case（表名复数）
- API路径: kebab-case
- 代码: PascalCase（类）/ camelCase（函数）

### Git 提交规范

```
feat: 新功能
fix: 修复 bug
docs: 文档更新
style: 代码格式
refactor: 重构
test: 测试
chore: 构建/工具
```

### 分支策略

- `main`: 生产分支
- `develop`: 开发分支
- `feature/*`: 功能分支
- `hotfix/*`: 紧急修复分支

## 部署

### Docker 部署

```bash
# 构建镜像
docker-compose build

# 启动服务
docker-compose up -d
```

### 生产构建

```bash
# 构建所有
pnpm build

# 分别构建
pnpm build:api
pnpm build:web
```

## 文档

- [PRD 产品需求文档](_bmad-output/planning-artifacts/prd.md)
- [技术架构文档](_bmad-output/planning-artifacts/architecture.md)
- [UX 设计规范](_bmad-output/planning-artifacts/ux-design-specification.md)
- [Epic & Stories](_bmad-output/planning-artifacts/epics.md)

## 许可证

私有项目 - 版权所有 © 2026
