---
stepsCompleted: ["step-01-init", "step-02-context", "step-03-starter", "step-04-decisions", "step-05-patterns", "step-06-structure", "step-07-validation", "step-08-complete"]
workflowType: 'architecture'
lastStep: 8
status: 'complete'
completedAt: '2026-03-26'
inputDocuments:
  - "prd.md"
  - "product-brief-生命线AI感知云平台.md"
  - "product-brief-生命线AI感知云平台-distillate.md"
workflowType: 'architecture'
project_name: '生命线AI感知云平台'
user_name: 'rd_agents'
date: '2026-03-25'
---

# Architecture Decision Document

_This document builds collaboratively through step-by-step discovery. Sections are appended as we work through each architectural decision together._

---

## Project Context Analysis

### Requirements Overview

**Functional Requirements:**
- **FR Count:** 57个功能需求， 涵盖8个能力领域
- **Key Areas:** 项目管理、 设备管理、 模型管理, 预警管理, 接口管理, 数据存储与查询, 用户与权限管理, 系统监控与报告
- **Coverage:** 所有FR映射到4个用户旅程

**Non-Functional Requirements:**
- **NFR Count:** 37个非功能需求, 分布在6个类别
- **Key Categories:** 性能(7), 安全(9), 可靠性(7), 可扩展性(5), 集成(5), 数据管理(4)
- **All NFRs are measurable** 有具体目标值和 验证方法

### Scale & Complexity Assessment

**Primary Technical Domain:**
- **Web Application** (Cloud Dashboard) - Vue.js/React SPA
- **IoT Platform** - Device connectivity, edge computing
- **Edge Computing** - AI inference on device firmware
- **Backend Services** - APIs, Data processing, Background jobs

**Complexity Level:** Medium-High

**Reasoning:**
- Real-time AI inference at edge
- Multi-protocol device communication
- Government compliance requirements
- Time-series data at scale

**Estimated Architectural Components:** 15-20 core components

**Cross-cutting Concerns:**

1. **Authentication & Authorization** - P0
   - 3-level RBAC (管理员/运维员/观察员)
   - Session management
   - Audit logging
2. **Real-time Data Pipeline** - P0
   - Device data ingestion
   - Edge processing
   - Cloud storage
   - Alert generation
   - Push notifications
3. **Edge AI Inference** - P0
   - Model loading/runtime
   - Inference execution
   - Result aggregation
   - Model updates
4. **API Gateway & Data Access** - P0
   - RESTful API design
   - Authentication (密钥)
   - Rate limiting
   - Webhook support
5. **Time-Series Data Store** - P0
   - ClickHouse (or similar)
   - 2+ months hot data
   - Historical query
   - Data retention policies
6. **Device Registry & Management** - P0
   - QR code device registration
   - Device metadata
   - Status tracking
   - Configuration management
   - OTA firmware management
7. **Alert & Notification System** - P0
   - Alert rule engine
   - Notification channels
   - Alert lifecycle management
8. **Reporting & Analytics** - P1
   - Dashboard widgets
   - Chart libraries
   - PDF generation
9. **Cloud-Edge Communication** - P0
   - MQTT protocol
   - 4G/5G connectivity
   - Offline queue management
10. **Security Infrastructure** - P0
    - TLS termination
    - Certificate management
    - Encryption at rest/transit
11. **Multi-tenancy Support** - P1 (Post-MVP)
    - Project isolation
    - Data segregation
12. **Audit & Compliance Logging** - P0
    - Audit event capture
    - Log storage
    - Compliance reporting

### Technical Constraints & Dependencies

**Performance:**
- Edge AI: ≤100ms (必须支持离线运行)
- Device onboarding: ≤10 min (核心差异点)
- API: ≤500ms P95
- Query: ≤3s
- Concurrent: ≥1000 devices/min
- Push: ≤5s

**Security:**
- TLS 1.3 (等保三级强制)
- AES-256 encryption
- Audit logs ≥6 months
- 等保三级认证 Year 1
- Data localization (data stays in province)
- 信创适配

**Scalability:**
- MVP: 389 devices
- Growth: 5000+ devices
- Offline capability required
- Multi-protocol support
- Extensible architecture needed

### Domain-Specific Considerations

**Government/Municipal Domain:**
- 等保三级认证 required
- Data localization mandatory
- Audit logging for compliance
- Integration with government systems (大数据平台, 应急指挥)

**IoT Domain:**
- 5 communication protocols
- Edge AI deployment
- Device management complexity
- Offline operation support

---

## Starter Template Evaluation

### Primary Technology Domain

**IoT + Web + AI 混合平台**

本项目是典型的云-边-端三层IoT平台，需要同时处理：
- **Web应用层**：管理后台、数据可视化、配置界面
- **IoT设备层**：设备接入、协议转换、边缘计算
- **AI能力层**：边缘推理、模型管理、预警分析

### Frontend Starter Selection

**选定方案：Vue Vben Admin 5.x**

| 评估维度 | Vue Vben Admin 5.x | Soybean Admin | Ant Design Pro |
|----------|-------------------|---------------|----------------|
| 技术栈 | Vue3 + Vite + TS | Vue3 + Vite + TS | React + Umi |
| UI组件库 | Ant Design Vue | Naive UI | Ant Design |
| 企业级特性 | ✅ 完整 | ✅ 完整 | ✅ 完整 |
| 开箱即用 | ✅ 高 | ✅ 高 | ✅ 高 |
| 中文生态 | ✅ 优秀 | ✅ 优秀 | ✅ 优秀 |
| IoT仪表盘 | ✅ 支持 | ✅ 支持 | ✅ 支持 |
| 学习成本 | 低（团队熟悉Vue） | 低 | 中 |
| 长期维护 | ✅ 活跃社区 | ✅ 活跃社区 | ✅ 活跃社区 |

**选择理由：**
- 团队已有Vue技术栈经验
- Vue Vben Admin 5.x提供完整的企业级解决方案
- 内置权限管理、多标签页、主题切换等开箱即用功能
- 与Ant Design Vue深度集成，UI组件丰富
- 支持微前端架构扩展

### Backend Starter Selection

**选定方案：NestJS**

| 评估维度 | NestJS | Spring Boot | FastAPI |
|----------|--------|-------------|---------|
| 语言 | TypeScript | Java | Python |
| 架构模式 | ✅ 模块化DI | ✅ IoC | ✅ 依赖注入 |
| TypeScript支持 | ✅ 原生 | ❌ 需编译 | ❌ 需类型存根 |
| IoT生态 | ✅ MQTT/Socket | ✅ 成熟 | ✅ 支持 |
| 学习曲线 | 中等 | 陡峭 | 平缓 |
| 性能 | 良好 | 优秀 | 优秀 |
| 企业级特性 | ✅ 完整 | ✅ 完整 | ✅ 基础 |

**选择理由：**
- 前后端TypeScript统一，代码可共享类型定义
- 模块化架构适合IoT平台的多业务域
- 内置依赖注入、装饰器、Guard等企业级特性
- 优秀的MQTT集成能力（与EMQX配合）
- 支持微服务架构演进

### Database Selection

**主数据存储：PostgreSQL**
- 关系型数据（用户、项目、设备元数据、配置）
- 成熟稳定，信创适配友好（可迁移至国产数据库）
- JSONB支持灵活Schema

**时序数据存储：ClickHouse**
- 设备遥测数据、AI推理结果、预警记录
- 高性能时序查询（10亿级数据秒级响应）
- 列式存储，压缩比高
- 支持TTL数据生命周期管理

**缓存层：Redis**
- 会话管理、实时状态缓存
- 消息队列（设备状态推送）
- 分布式锁

### IoT Infrastructure Selection

**消息中间件：EMQX**
- 企业级MQTT Broker
- 支持100万+并发连接
- 内置规则引擎，支持数据流转
- 支持多种协议（MQTT、CoAP、LwM2M）

**边缘通信协议：MQTT 5.0**
- 轻量级，适合4G/5G网络
- 支持QoS等级，消息可靠性高
- 支持遗嘱消息（设备离线检测）
- 与EMQX深度集成

### Starter Template Summary

| 层级 | 技术选型 | 版本 | 用途 |
|------|----------|------|------|
| 前端框架 | Vue Vben Admin | 5.x | 管理后台SPA |
| 后端框架 | NestJS | 10.x | API服务、业务逻辑 |
| 关系数据库 | PostgreSQL | 15+ | 主数据存储 |
| 时序数据库 | ClickHouse | 23+ | 遥测数据存储 |
| 缓存 | Redis | 7+ | 缓存、会话、消息队列 |
| MQTT Broker | EMQX | 5.x | 设备消息接入 |
| 容器化 | Docker + K8s | - | 部署运维 |

### 信创适配策略

| 组件 | 当前选型 | 信创替代方案 | 适配优先级 |
|------|----------|--------------|------------|
| 操作系统 | CentOS/Ubuntu | 麒麟/统信UOS | P0 |
| 数据库 | PostgreSQL | 达梦/人大金仓 | P1 |
| 中间件 | Redis | 飞腾Redis | P1 |
| 容器平台 | Docker/K8s | KubeSphere | P2 |

**信创适配原则：**
- 代码层保持数据库无关（使用ORM抽象）
- 信创环境测试纳入CI/CD流程
- 分阶段完成信创认证

---

## Core Architectural Decisions

### Decision Priority Analysis

**Critical Decisions (Block Implementation):**
- ORM选型：TypeORM
- 认证方案：JWT + Refresh Token
- API风格：REST + Swagger
- 云服务商：华为云
- 容器编排：Docker + Kubernetes

**Important Decisions (Shape Architecture):**
- 权限实现：CASL
- 图表库：ECharts
- 地图组件：天地图
- 监控方案：Prometheus + Grafana
- CI/CD：GitLab CI

**Deferred Decisions (Post-MVP):**
- 服务网格：Istio
- 多租户支持
- GraphQL API层

---

### Data Architecture

| 组件 | 选型 | 版本 | 用途 |
|------|------|------|------|
| ORM | TypeORM | 0.3.x | 业务数据访问 |
| 关系数据库 | PostgreSQL | 15+ | 主数据存储 |
| 时序数据库 | ClickHouse | 23+ | 遥测数据 |
| 时序客户端 | @clickhouse/client | latest | ClickHouse访问 |
| 缓存 | Redis | 7+ | 会话、状态缓存 |
| Redis客户端 | ioredis | 5.x | Redis访问 |

**ClickHouse设计：**
- 分区键：`toYYYYMMDD(timestamp)` 按天分区
- TTL：60天热数据自动清理
- 物化视图：每小时设备状态聚合

**信创迁移路径：**
- PostgreSQL → 达梦/人大金仓（TypeORM Driver层）
- Redis → 飞腾Redis（API兼容）
- ClickHouse → TDengine（备选）

---

### Authentication & Security

| 组件 | 选型 | 说明 |
|------|------|------|
| 认证方案 | JWT + Refresh Token | 无状态、设备端支持 |
| 权限框架 | CASL | 声明式、前后端共享 |
| API认证 | HMAC-SHA256签名 | 外部接口调用 |
| API Key格式 | `lk_live_xxx` | 前缀 + UUID |
| 请求有效期 | 5分钟 | 防重放攻击 |
| 存储加密 | AES-256 | 敏感字段加密 |
| 密钥管理 | K8s Secrets | MVP阶段方案 |

**3级RBAC模型：**
- 管理员：全部权限
- 运维员：设备管理、数据查看
- 观察员：只读权限

---

### API & Communication Patterns

| 组件 | 选型 | 说明 |
|------|------|------|
| API风格 | REST | IoT兼容性好 |
| API文档 | Swagger/OpenAPI | @nestjs/swagger |
| 实时推送 | WebSocket Gateway | NestJS原生 |
| 设备通信 | MQTT 5.0 | EMQX Broker |

**MQTT Topic设计：**
- 设备上行：`device/{deviceId}/telemetry`
- 设备下行：`device/{deviceId}/command`
- 预警推送：`alert/{projectId}/{level}`
- 状态广播：`status/{deviceId}`

**协议优先级：**

| 协议 | 优先级 | 用途 |
|------|--------|------|
| MQTT 5.0 | P0 | 主通信协议 |
| HTTPS | P0 | 设备注册/OTA |
| CoAP | P1 | NB-IoT设备 |
| LoRaWAN | P1 | 远距离场景 |
| Modbus | P2 | 边缘网关 |

---

### Frontend Architecture

| 组件 | 选型 | 说明 |
|------|------|------|
| 框架 | Vue Vben Admin 5.x | 企业级SPA |
| 状态管理 | Pinia | Vue3官方推荐 |
| 图表库 | ECharts | IoT数据可视化 |
| 地图组件 | 天地图 | 政府项目合规 |
| 表格组件 | vxe-table | 虚拟滚动 |
| 主题色 | #0050b3 | 政府蓝 |

**定制组件：**
- 设备状态徽章
- 预警卡片
- 实时数据面板
- 管网地图组件

**性能优化：**
- 路由懒加载
- 组件按需加载
- ECharts tree-shaking
- 虚拟滚动

---

### Infrastructure & Deployment

| 组件 | 选型 | 说明 |
|------|------|------|
| 云服务商 | 华为云 | 信创友好 |
| CI/CD | GitLab CI | 国内稳定 |
| 容器运行时 | Docker | 标准化部署 |
| 编排平台 | Kubernetes | 高可用 |
| 镜像仓库 | Harbor | 私有安全 |
| 日志方案 | ELK Stack | 集中日志 |
| 监控方案 | Prometheus + Grafana | 指标监控 |
| 链路追踪 | Jaeger | 分布式追踪 |
| 告警 | Alertmanager | 多渠道通知 |

**环境划分：**

| 环境 | 用途 | 配置 |
|------|------|------|
| dev | 开发测试 | 最小配置 |
| staging | 预发布 | 生产缩小版 |
| prod | 生产 | 高可用 |

**备份策略：**
- 每日全量 + 每小时增量
- 保留30天
- RTO ≤ 4小时, RPO ≤ 1小时

---

### Decision Impact Analysis

**Implementation Sequence:**
1. 基础设施搭建（K8s集群、GitLab CI、Harbor）
2. 数据库部署（PostgreSQL、ClickHouse、Redis）
3. 后端框架搭建（NestJS + TypeORM + EMQX）
4. 前端框架搭建（Vue Vben Admin + ECharts）
5. 认证授权实现（JWT + CASL）
6. 核心业务开发（设备管理、预警管理）

**Cross-Component Dependencies:**
- TypeORM → PostgreSQL连接池配置
- CASL → 前后端权限规则同步
- EMQX → MQTT与WebSocket桥接
- ClickHouse → 物化视图依赖PostgreSQL设备元数据

---

## Implementation Patterns & Consistency Rules

### Pattern Categories Defined

**Critical Conflict Points Identified:** 5大类别，共25+潜在冲突点

---

### Naming Patterns

#### Database Naming Conventions

| 元素 | 约定 | 示例 |
|------|------|------|
| 表名 | snake_case 复数 | `devices`, `alert_rules`, `device_models` |
| 列名 | snake_case | `device_id`, `created_at`, `is_active` |
| 主键 | `id` | `id` (UUID) |
| 外键 | `{table}_id` | `project_id`, `model_id` |
| 索引 | `idx_{table}_{columns}` | `idx_devices_status`, `idx_alerts_created_at` |
| 连接表 | `{table1}_{table2}` | `device_alert_rules` |

#### API Naming Conventions

| 元素 | 约定 | 示例 |
|------|------|------|
| 端点路径 | kebab-case 复数 | `/api/v1/devices`, `/api/v1/alert-rules` |
| 路由参数 | camelCase | `:deviceId`, `:projectId` |
| 查询参数 | camelCase | `?pageSize=10&deviceId=xxx` |
| 请求头 | X-Prefix | `X-Api-Key`, `X-Request-Id` |
| 响应字段 | camelCase | `{ deviceId, createdAt }` |

#### Code Naming Conventions

| 元素 | 约定 | 示例 |
|------|------|------|
| 类/组件 | PascalCase | `DeviceService`, `DeviceCard`, `AlertRule` |
| 接口 | PascalCase + I前缀(可选) | `DeviceDto`, `ICreateDeviceInput` |
| 函数/方法 | camelCase | `getDeviceById()`, `createAlertRule()` |
| 变量 | camelCase | `deviceList`, `isLoading` |
| 常量 | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT`, `DEFAULT_PAGE_SIZE` |
| 枚举 | PascalCase | `DeviceStatus`, `AlertLevel` |
| 文件(组件) | PascalCase | `DeviceCard.vue`, `AlertList.vue` |
| 文件(服务) | kebab-case | `device.service.ts`, `alert-rule.dto.ts` |
| 文件(工具) | kebab-case | `date-utils.ts`, `http-utils.ts` |

---

### Structure Patterns

#### Backend Directory Structure (NestJS)

```
apps/
├── api/                          # 主API服务
│   ├── src/
│   │   ├── modules/
│   │   │   ├── device/
│   │   │   │   ├── device.module.ts
│   │   │   │   ├── device.controller.ts
│   │   │   │   ├── device.service.ts
│   │   │   │   ├── device.entity.ts
│   │   │   │   ├── device.dto.ts
│   │   │   │   └── device.spec.ts
│   │   │   ├── alert/
│   │   │   ├── model/
│   │   │   └── project/
│   │   ├── common/
│   │   │   ├── decorators/
│   │   │   ├── guards/
│   │   │   ├── interceptors/
│   │   │   ├── filters/
│   │   │   ├── pipes/
│   │   │   └── dto/
│   │   ├── config/
│   │   │   ├── database.config.ts
│   │   │   ├── redis.config.ts
│   │   │   └── emqx.config.ts
│   │   └── main.ts
│   └── test/
│       └── e2e/
├── mqtt-worker/                  # MQTT消息处理Worker
└── shared/                       # 共享代码
    └── types/
```

#### Frontend Directory Structure (Vue Vben Admin)

```
src/
├── views/                        # 页面视图
│   ├── dashboard/
│   ├── device/
│   │   ├── list/
│   │   │   └── index.vue
│   │   ├── detail/
│   │   │   └── [id].vue
│   │   └── components/
│   │       ├── DeviceCard.vue
│   │       └── DeviceStatus.vue
│   ├── alert/
│   ├── model/
│   └── project/
├── components/                   # 通用组件
│   ├── Table/
│   ├── Form/
│   └── Modal/
├── api/                          # API请求
│   ├── device.ts
│   ├── alert.ts
│   └── model.ts
├── store/                        # Pinia状态
│   ├── modules/
│   │   ├── device.ts
│   │   └── user.ts
│   └── index.ts
├── hooks/                        # 组合式函数
├── utils/                        # 工具函数
│   ├── date-utils.ts
│   └── http-utils.ts
└── types/                        # TypeScript类型
    └── api/
```

#### Test File Location

| 测试类型 | 位置 | 命名 |
|----------|------|------|
| 单元测试 | 与源文件同目录 | `*.spec.ts` |
| E2E测试 | `test/e2e/` | `*.e2e-spec.ts` |
| 前端测试 | 与组件同目录 | `*.test.ts` |

---

### Format Patterns

#### API Response Format

**成功响应：**
```typescript
interface ApiResponse<T> {
  code: 0;                        // 0 表示成功
  message: 'success';
  data: T;
  timestamp: string;              // ISO 8601
}
```

**错误响应：**
```typescript
interface ErrorResponse {
  code: string;                   // 业务错误码
  message: string;                // 用户友好消息
  details?: Record<string, any>;  // 额外详情
  timestamp: string;
  traceId: string;                // 链路追踪ID
}
```

**分页响应：**
```typescript
interface PagedResponse<T> {
  code: 0;
  data: {
    items: T[];
    total: number;
    page: number;
    pageSize: number;
  };
}
```

#### Date/Time Formats

| 场景 | 格式 | 示例 |
|------|------|------|
| API传输 | ISO 8601 | `2026-03-25T10:00:00.000Z` |
| 数据库存储 | TIMESTAMP WITH TZ | PostgreSQL原生 |
| 前端显示 | `YYYY-MM-DD HH:mm:ss` | `2026-03-25 18:00:00` |
| 文件名 | `YYYYMMDD_HHmmss` | `20260325_180000` |

#### JSON Field Naming

- **API响应：** camelCase
- **数据库JSON字段：** snake_case（与PostgreSQL JSONB一致）
- **MQTT消息：** camelCase（与前端一致）

---

### Communication Patterns

#### WebSocket Events

| 事件类型 | 命名约定 | 示例 |
|----------|----------|------|
| 服务端推送 | `resource.action` | `device.status_changed` |
| 客户端订阅 | `subscribe:resource` | `subscribe:device` |
| 房间命名 | `resource:id` | `device:abc123` |

**消息格式：**
```typescript
interface WsMessage<T> {
  event: string;
  data: T;
  timestamp: string;
}
```

#### MQTT Topics

| 方向 | Topic格式 | 示例 |
|------|-----------|------|
| 设备上行遥测 | `device/{deviceId}/telemetry` | `device/abc123/telemetry` |
| 设备下行命令 | `device/{deviceId}/command` | `device/abc123/command` |
| 预警广播 | `alert/{projectId}/{level}` | `alert/proj001/critical` |
| 状态广播 | `status/{deviceId}` | `status/abc123` |

**遥测消息格式：**
```typescript
interface TelemetryMessage {
  deviceId: string;
  timestamp: number;              // Unix毫秒
  data: Record<string, any>;
}
```

---

### Process Patterns

#### Error Handling

**后端异常处理：**
```typescript
// 业务异常类
class BusinessException extends Error {
  constructor(
    public code: string,
    message: string,
    public details?: any
  ) {}
}

// 全局异常过滤器
@Catch()
class GlobalExceptionFilter implements ExceptionFilter {
  catch(exception: unknown, host: ArgumentsHost) {
    // 统一错误响应格式
  }
}
```

**前端错误处理：**
- HTTP拦截器统一处理4xx/5xx错误
- Toast显示用户友好消息
- 控制台输出详细错误用于调试

**日志规范：**
```typescript
interface LogEntry {
  level: 'debug' | 'info' | 'warn' | 'error';
  message: string;
  context?: Record<string, any>;
  traceId?: string;
  timestamp: string;
}
```

#### Loading States

| 状态类型 | 命名约定 | 示例 |
|----------|----------|------|
| 全局加载 | `isGlobalLoading` | 顶部进度条 |
| 资源加载 | `is{Resource}Loading` | `isDeviceListLoading` |
| 操作加载 | `is{Action}ing` | `isCreating`, `isUpdating` |

#### Retry Strategy

| 场景 | 策略 |
|------|------|
| API请求 | 3次指数退避（1s, 2s, 4s） |
| MQTT重连 | 自动重连，最大间隔30s |
| WebSocket断开 | 3次重试后提示用户 |

---

### Enforcement Guidelines

**All AI Agents MUST:**

1. **命名一致性**：严格遵循上述命名约定，不得混用风格
2. **文件位置**：新文件必须放置在规定目录结构中
3. **API格式**：所有API响应必须使用统一包装格式
4. **错误处理**：使用全局异常处理机制，不自行捕获后静默
5. **日志规范**：使用结构化JSON日志，包含traceId

**Pattern Enforcement:**

- **代码审查**：PR中检查命名和格式一致性
- **ESLint/Prettier**：自动化格式检查
- **TypeScript**：接口定义强制类型检查
- **Swagger**：API文档自动生成，验证响应格式

---

### Pattern Examples

#### Good Examples

**✅ 正确的API端点：**
```
GET  /api/v1/devices
GET  /api/v1/devices/:deviceId
POST /api/v1/devices
PUT  /api/v1/devices/:deviceId
```

**✅ 正确的实体定义：**
```typescript
@Entity('devices')
export class Device {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ name: 'project_id' })
  projectId: string;

  @CreateDateColumn({ name: 'created_at' })
  createdAt: Date;
}
```

**✅ 正确的API响应：**
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "deviceId": "abc123",
    "deviceName": "传感器001",
    "status": "online"
  },
  "timestamp": "2026-03-25T10:00:00.000Z"
}
```

#### Anti-Patterns

**❌ 错误：混用命名风格**
```typescript
// 错误：表名用PascalCase
@Entity('Devices')
// 错误：列名用camelCase
@Column() deviceId: string;
```

**❌ 错误：不一致的API响应**
```json
{
  "success": true,
  "result": { ... }
}
```

**❌ 错误：文件放错位置**
```
src/services/device.service.ts  // 应该在 modules/device/
```

---

## Project Structure & Boundaries

### Requirements to Structure Mapping

| FR类别 | 模块 | 后端位置 | 前端位置 |
|--------|------|----------|----------|
| FR1-5: 项目管理 | project | `modules/project/` | `views/project/` |
| FR6-15: 设备管理 | device | `modules/device/` | `views/device/` |
| FR16-23: 模型管理 | model | `modules/model/` | `views/model/` |
| FR24-31: 预警管理 | alert | `modules/alert/` | `views/alert/` |
| FR32-40: 接口管理 | api-key | `modules/api-key/` | `views/api/` |
| FR41-45: 数据存储 | telemetry | `modules/telemetry/` | `views/data/` |
| FR46-51: 用户权限 | auth, user | `modules/auth/`, `modules/user/` | `views/system/` |
| FR52-57: 监控报告 | monitoring | `modules/monitoring/` | `views/dashboard/` |

---

### Complete Project Directory Structure

#### Monorepo Root

```
lifeline-ai-platform/
├── README.md
├── docker-compose.yml
├── docker-compose.prod.yml
├── .gitignore
├── .gitlab-ci.yml
├── .env.example
├── .prettierrc
├── commitlint.config.js
│
├── apps/
│   ├── api/                          # NestJS 主API服务
│   ├── mqtt-worker/                  # MQTT消息处理Worker
│   ├── web/                          # Vue Vben Admin 前端
│   └── docs/                         # API文档站点
│
├── packages/
│   ├── shared-types/                 # 共享TypeScript类型
│   ├── shared-utils/                 # 共享工具函数
│   └── eslint-config/                # 共享ESLint配置
│
├── infrastructure/
│   ├── k8s/                          # Kubernetes配置
│   ├── terraform/                    # 基础设施即代码
│   └── docker/                       # Dockerfile
│
└── scripts/
    ├── deploy.sh
    └── backup.sh
```

#### Backend API (apps/api/)

```
apps/api/
├── package.json
├── nest-cli.json
├── tsconfig.json
├── tsconfig.build.json
├── .env
├── .env.example
│
├── src/
│   ├── main.ts                       # 应用入口
│   ├── app.module.ts                 # 根模块
│   │
│   ├── config/
│   │   ├── database.config.ts
│   │   ├── redis.config.ts
│   │   ├── emqx.config.ts
│   │   ├── clickhouse.config.ts
│   │   ├── jwt.config.ts
│   │   └── app.config.ts
│   │
│   ├── common/
│   │   ├── decorators/
│   │   │   ├── current-user.decorator.ts
│   │   │   ├── require-permissions.decorator.ts
│   │   │   └── api-response.decorator.ts
│   │   ├── guards/
│   │   │   ├── jwt-auth.guard.ts
│   │   │   ├── permissions.guard.ts
│   │   │   └── api-key.guard.ts
│   │   ├── interceptors/
│   │   │   ├── response.interceptor.ts
│   │   │   ├── logging.interceptor.ts
│   │   │   └── timeout.interceptor.ts
│   │   ├── filters/
│   │   │   ├── http-exception.filter.ts
│   │   │   └── business-exception.filter.ts
│   │   ├── pipes/
│   │   │   ├── validation.pipe.ts
│   │   │   └── parse-uuid.pipe.ts
│   │   ├── dto/
│   │   │   ├── pagination.dto.ts
│   │   │   └── response.dto.ts
│   │   └── utils/
│   │       ├── crypto.util.ts
│   │       ├── date.util.ts
│   │       └── trace.util.ts
│   │
│   ├── modules/
│   │   ├── auth/
│   │   │   ├── auth.module.ts
│   │   │   ├── auth.controller.ts
│   │   │   ├── auth.service.ts
│   │   │   ├── dto/
│   │   │   │   ├── login.dto.ts
│   │   │   │   ├── refresh-token.dto.ts
│   │   │   │   └── auth-response.dto.ts
│   │   │   ├── strategies/
│   │   │   │   └── jwt.strategy.ts
│   │   │   └── auth.spec.ts
│   │   │
│   │   ├── user/
│   │   │   ├── user.module.ts
│   │   │   ├── user.controller.ts
│   │   │   ├── user.service.ts
│   │   │   ├── user.entity.ts
│   │   │   ├── dto/
│   │   │   │   ├── create-user.dto.ts
│   │   │   │   ├── update-user.dto.ts
│   │   │   │   └── user-response.dto.ts
│   │   │   └── user.spec.ts
│   │   │
│   │   ├── project/
│   │   │   ├── project.module.ts
│   │   │   ├── project.controller.ts
│   │   │   ├── project.service.ts
│   │   │   ├── project.entity.ts
│   │   │   ├── dto/
│   │   │   │   ├── create-project.dto.ts
│   │   │   │   └── project-response.dto.ts
│   │   │   └── project.spec.ts
│   │   │
│   │   ├── device/
│   │   │   ├── device.module.ts
│   │   │   ├── device.controller.ts
│   │   │   ├── device.service.ts
│   │   │   ├── device.entity.ts
│   │   │   ├── device-type.entity.ts
│   │   │   ├── dto/
│   │   │   │   ├── create-device.dto.ts
│   │   │   │   ├── update-device.dto.ts
│   │   │   │   ├── device-query.dto.ts
│   │   │   │   └── device-response.dto.ts
│   │   │   ├── enums/
│   │   │   │   ├── device-status.enum.ts
│   │   │   │   └── device-type.enum.ts
│   │   │   └── device.spec.ts
│   │   │
│   │   ├── model/
│   │   │   ├── model.module.ts
│   │   │   ├── model.controller.ts
│   │   │   ├── model.service.ts
│   │   │   ├── ai-model.entity.ts
│   │   │   ├── model-binding.entity.ts
│   │   │   ├── dto/
│   │   │   │   ├── create-model.dto.ts
│   │   │   │   ├── bind-model.dto.ts
│   │   │   │   └── model-response.dto.ts
│   │   │   └── model.spec.ts
│   │   │
│   │   ├── alert/
│   │   │   ├── alert.module.ts
│   │   │   ├── alert.controller.ts
│   │   │   ├── alert.service.ts
│   │   │   ├── alert-rule.entity.ts
│   │   │   ├── alert-record.entity.ts
│   │   │   ├── dto/
│   │   │   │   ├── create-alert-rule.dto.ts
│   │   │   │   ├── alert-query.dto.ts
│   │   │   │   └── alert-response.dto.ts
│   │   │   ├── enums/
│   │   │   │   ├── alert-level.enum.ts
│   │   │   │   └── alert-status.enum.ts
│   │   │   └── alert.spec.ts
│   │   │
│   │   ├── api-key/
│   │   │   ├── api-key.module.ts
│   │   │   ├── api-key.controller.ts
│   │   │   ├── api-key.service.ts
│   │   │   ├── api-key.entity.ts
│   │   │   ├── dto/
│   │   │   │   ├── create-api-key.dto.ts
│   │   │   │   └── api-key-response.dto.ts
│   │   │   └── api-key.spec.ts
│   │   │
│   │   ├── telemetry/
│   │   │   ├── telemetry.module.ts
│   │   │   ├── telemetry.controller.ts
│   │   │   ├── telemetry.service.ts
│   │   │   ├── dto/
│   │   │   │   ├── telemetry-query.dto.ts
│   │   │   │   └── telemetry-response.dto.ts
│   │   │   └── telemetry.spec.ts
│   │   │
│   │   ├── monitoring/
│   │   │   ├── monitoring.module.ts
│   │   │   ├── monitoring.controller.ts
│   │   │   ├── monitoring.service.ts
│   │   │   ├── dto/
│   │   │   │   ├── dashboard-stats.dto.ts
│   │   │   │   └── report-query.dto.ts
│   │   │   └── monitoring.spec.ts
│   │   │
│   │   └── webhook/
│   │       ├── webhook.module.ts
│   │       ├── webhook.service.ts
│   │       ├── webhook.entity.ts
│   │       └── dto/
│   │
│   ├── database/
│   │   ├── migrations/
│   │   └── seeds/
│   │
│   └── clickhouse/
│       ├── client.ts
│       └── queries/
│           ├── telemetry.query.ts
│           └── aggregation.query.ts
│
├── test/
│   ├── app.e2e-spec.ts
│   ├── jest-e2e.json
│   └── fixtures/
│
└── docker/
    └── Dockerfile
```

#### Frontend (apps/web/)

```
apps/web/
├── package.json
├── vite.config.ts
├── tsconfig.json
├── .env
├── .env.production
├── index.html
│
├── public/
│   └── favicon.ico
│
├── src/
│   ├── main.ts
│   ├── App.vue
│   │
│   ├── api/
│   │   ├── index.ts                 # Axios实例配置
│   │   ├── auth.ts
│   │   ├── user.ts
│   │   ├── project.ts
│   │   ├── device.ts
│   │   ├── model.ts
│   │   ├── alert.ts
│   │   ├── api-key.ts
│   │   ├── telemetry.ts
│   │   └── monitoring.ts
│   │
│   ├── views/
│   │   ├── dashboard/
│   │   │   ├── index.vue
│   │   │   └── components/
│   │   │       ├── DeviceStatusCard.vue
│   │   │       ├── AlertSummaryCard.vue
│   │   │       └── TelemetryChart.vue
│   │   │
│   │   ├── project/
│   │   │   ├── list/
│   │   │   │   └── index.vue
│   │   │   ├── detail/
│   │   │   │   └── [id].vue
│   │   │   └── components/
│   │   │       └── ProjectCard.vue
│   │   │
│   │   ├── device/
│   │   │   ├── list/
│   │   │   │   └── index.vue
│   │   │   ├── detail/
│   │   │   │   └── [id].vue
│   │   │   ├── register/
│   │   │   │   └── index.vue
│   │   │   └── components/
│   │   │       ├── DeviceCard.vue
│   │   │       ├── DeviceStatusBadge.vue
│   │   │       ├── DeviceTelemetryPanel.vue
│   │   │       └── DeviceLocationMap.vue
│   │   │
│   │   ├── model/
│   │   │   ├── list/
│   │   │   │   └── index.vue
│   │   │   ├── detail/
│   │   │   │   └── [id].vue
│   │   │   └── components/
│   │   │       ├── ModelCard.vue
│   │   │       └── ModelBindingForm.vue
│   │   │
│   │   ├── alert/
│   │   │   ├── list/
│   │   │   │   └── index.vue
│   │   │   ├── rules/
│   │   │   │   └── index.vue
│   │   │   ├── detail/
│   │   │   │   └── [id].vue
│   │   │   └── components/
│   │   │       ├── AlertCard.vue
│   │   │       ├── AlertLevelTag.vue
│   │   │       └── AlertRuleForm.vue
│   │   │
│   │   ├── api/
│   │   │   ├── keys/
│   │   │   │   └── index.vue
│   │   │   ├── docs/
│   │   │   │   └── index.vue
│   │   │   └── components/
│   │   │       └── ApiKeyCard.vue
│   │   │
│   │   ├── data/
│   │   │   ├── telemetry/
│   │   │   │   └── index.vue
│   │   │   └── components/
│   │   │       ├── TelemetryTable.vue
│   │   │       └── TelemetryChart.vue
│   │   │
│   │   └── system/
│   │       ├── user/
│   │       │   └── index.vue
│   │       ├── role/
│   │       │   └── index.vue
│   │       └── log/
│   │           └── index.vue
│   │
│   ├── components/
│   │   ├── Table/
│   │   │   └── ProTable.vue
│   │   ├── Form/
│   │   │   └── ProForm.vue
│   │   ├── Modal/
│   │   │   └── ProModal.vue
│   │   └── Charts/
│   │       ├── LineChart.vue
│   │       ├── PieChart.vue
│   │       └── GaugeChart.vue
│   │
│   ├── store/
│   │   ├── index.ts
│   │   └── modules/
│   │       ├── user.ts
│   │       ├── permission.ts
│   │       ├── device.ts
│   │       └── alert.ts
│   │
│   ├── hooks/
│   │   ├── useTable.ts
│   │   ├── useForm.ts
│   │   ├── useWebSocket.ts
│   │   └── usePermission.ts
│   │
│   ├── router/
│   │   ├── index.ts
│   │   └── routes/
│   │       ├── dashboard.ts
│   │       ├── device.ts
│   │       ├── alert.ts
│   │       └── system.ts
│   │
│   ├── utils/
│   │   ├── http.ts
│   │   ├── date.ts
│   │   ├── crypto.ts
│   │   └── storage.ts
│   │
│   ├── types/
│   │   ├── api/
│   │   │   ├── device.ts
│   │   │   ├── alert.ts
│   │   │   └── common.ts
│   │   └── global.d.ts
│   │
│   └── styles/
│       ├── variables.less
│       └── global.less
│
└── docker/
    └── Dockerfile
```

#### MQTT Worker (apps/mqtt-worker/)

```
apps/mqtt-worker/
├── package.json
├── tsconfig.json
├── .env
│
├── src/
│   ├── main.ts
│   ├── worker.module.ts
│   │
│   ├── handlers/
│   │   ├── telemetry.handler.ts
│   │   ├── status.handler.ts
│   │   └── command.handler.ts
│   │
│   ├── services/
│   │   ├── mqtt.service.ts
│   │   ├── clickhouse.service.ts
│   │   └── alert.service.ts
│   │
│   └── config/
│       └── mqtt.config.ts
│
└── docker/
    └── Dockerfile
```

#### Infrastructure

```
infrastructure/
├── k8s/
│   ├── base/
│   │   ├── deployment-api.yaml
│   │   ├── deployment-web.yaml
│   │   ├── deployment-worker.yaml
│   │   ├── service-api.yaml
│   │   ├── configmap.yaml
│   │   └── secret.yaml
│   ├── overlays/
│   │   ├── dev/
│   │   ├── staging/
│   │   └── prod/
│   └── namespace.yaml
│
├── terraform/
│   ├── main.tf
│   ├── variables.tf
│   ├── outputs.tf
│   └── modules/
│       ├── vpc/
│       ├── rds/
│       ├── redis/
│       └── k8s/
│
└── docker/
    ├── api.Dockerfile
    ├── web.Dockerfile
    └── worker.Dockerfile
```

---

### Architectural Boundaries

#### API Boundaries

| 边界 | 端点前缀 | 认证方式 | 说明 |
|------|----------|----------|------|
| 公开API | `/api/v1/auth/*` | 无 | 登录、刷新Token |
| 用户API | `/api/v1/*` | JWT | 所有业务API |
| 设备API | `/device/v1/*` | API Key | 设备上报数据 |
| 外部API | `/external/v1/*` | API Key + 签名 | 第三方集成 |

#### Service Boundaries

```
┌─────────────────────────────────────────────────────────────┐
│                      API Gateway (NestJS)                    │
├──────────┬──────────┬──────────┬──────────┬────────────────┤
│  Auth    │  Device  │  Alert   │ Telemetry│   Monitoring   │
│  Module  │  Module  │  Module  │  Module  │    Module      │
└────┬─────┴────┬─────┴────┬─────┴────┬─────┴───────┬────────┘
     │          │          │          │             │
     ▼          ▼          ▼          ▼             ▼
┌─────────┐┌─────────┐┌─────────┐┌─────────┐┌─────────────┐
│PostgreSQL││PostgreSQL││PostgreSQL││ClickHouse││  PostgreSQL │
│  (Auth) ││ (Device)││ (Alert) ││(Telemetry)││(Monitoring)│
└─────────┘└─────────┘└─────────┘└─────────┘└─────────────┘
                           │
                           ▼
                    ┌─────────────┐
                    │    Redis    │
                    │   (Cache)   │
                    └─────────────┘
```

#### Data Boundaries

| 数据库 | 数据类型 | 访问方式 | 保留策略 |
|--------|----------|----------|----------|
| PostgreSQL | 业务数据 | TypeORM | 永久 |
| ClickHouse | 时序数据 | @clickhouse/client | 60天TTL |
| Redis | 缓存/会话 | ioredis | 按需过期 |

---

### FR to File Mapping

| FR编号 | 功能 | 后端文件 | 前端文件 |
|--------|------|----------|----------|
| FR1-5 | 项目管理 | `modules/project/` | `views/project/` |
| FR6 | 扫码注册 | `modules/device/device.service.ts` | `views/device/register/` |
| FR7 | 设备激活 | `modules/device/device.service.ts` | - |
| FR8-9 | 设备状态 | `modules/device/device.controller.ts` | `views/device/list/` |
| FR10-15 | 设备配置 | `modules/device/` | `views/device/detail/` |
| FR16-23 | 模型管理 | `modules/model/` | `views/model/` |
| FR24-31 | 预警管理 | `modules/alert/` | `views/alert/` |
| FR32-40 | 接口管理 | `modules/api-key/` | `views/api/` |
| FR41-45 | 数据查询 | `modules/telemetry/` | `views/data/` |
| FR46-51 | 用户权限 | `modules/auth/`, `modules/user/` | `views/system/` |
| FR52-57 | 监控报告 | `modules/monitoring/` | `views/dashboard/` |

---

### Integration Points

#### Internal Communication

| 通信方式 | 场景 | 实现 |
|----------|------|------|
| HTTP REST | API服务间调用 | NestJS HttpModule |
| Redis Pub/Sub | 实时事件广播 | ioredis |
| WebSocket | 前端实时推送 | NestJS Gateway |
| MQTT | 设备通信 | EMQX |

#### External Integrations

| 集成点 | 协议 | 用途 |
|--------|------|------|
| 设备接入 | MQTT 5.0 | 遥测数据上报、命令下发 |
| 第三方API | HTTPS + 签名 | 数据共享、系统对接 |
| 政务系统 | HTTPS | 大数据平台、应急指挥 |

#### Data Flow

```
设备 ──MQTT──▶ EMQX ──▶ MQTT Worker ──▶ ClickHouse
                              │
                              ├──▶ PostgreSQL (元数据)
                              │
                              └──▶ Redis (实时状态)
                                      │
                                      └──▶ WebSocket ──▶ 前端
```

---

## Architecture Validation Results

### Coherence Validation ✅

#### Decision Compatibility

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 前后端TypeScript统一 | ✅ 通过 | NestJS + Vue 共享类型定义 |
| 数据库访问层 | ✅ 通过 | TypeORM (PostgreSQL) + @clickhouse/client (ClickHouse) |
| 认证方案 | ✅ 通过 | JWT + CASL 前后端共享权限规则 |
| 实时通信 | ✅ 通过 | EMQX + WebSocket Gateway 协同 |
| 缓存策略 | ✅ 通过 | Redis 统一会话、状态缓存 |

#### Pattern Consistency

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 命名约定 | ✅ 通过 | 数据库snake_case，API/代码camelCase |
| 目录结构 | ✅ 通过 | NestJS模块化 + Vue功能模块 |
| 通信格式 | ✅ 通过 | 统一API响应格式、MQTT消息格式 |

#### Structure Alignment

| 检查项 | 状态 | 说明 |
|--------|------|------|
| FR到模块映射 | ✅ 通过 | 57个FR全部映射到modules/ |
| 边界定义 | ✅ 通过 | API边界、服务边界、数据边界明确 |
| 集成点 | ✅ 通过 | MQTT/HTTP/WebSocket集成点已定义 |

---

### Requirements Coverage Validation ✅

#### Functional Requirements Coverage (57 FRs)

| FR类别 | 数量 | 架构支持 | 状态 |
|--------|------|----------|------|
| FR1-5: 项目管理 | 5 | `modules/project/` | ✅ 完整 |
| FR6-15: 设备管理 | 10 | `modules/device/` | ✅ 完整 |
| FR16-23: 模型管理 | 8 | `modules/model/` | ✅ 完整 |
| FR24-31: 预警管理 | 8 | `modules/alert/` | ✅ 完整 |
| FR32-40: 接口管理 | 9 | `modules/api-key/` | ✅ 完整 |
| FR41-45: 数据存储 | 5 | `modules/telemetry/` + ClickHouse | ✅ 完整 |
| FR46-51: 用户权限 | 6 | `modules/auth/` + `modules/user/` | ✅ 完整 |
| FR52-57: 监控报告 | 6 | `modules/monitoring/` | ✅ 完整 |

**覆盖率：57/57 = 100%**

#### Non-Functional Requirements Coverage (37 NFRs)

| NFR类别 | 数量 | 架构支持 | 状态 |
|--------|------|----------|------|
| 性能 (P01-P07) | 7 | ClickHouse + Redis + K8s弹性伸缩 | ✅ 完整 |
| 安全 (S01-S09) | 9 | JWT + CASL + TLS1.3 + AES-256 | ✅ 完整 |
| 可靠性 (R01-R07) | 7 | K8s HA + 备份策略 | ✅ 完整 |
| 可扩展性 (SC01-SC05) | 5 | 微服务架构演进路径 | ✅ 完整 |
| 集成 (I01-I05) | 5 | MQTT + REST + WebSocket | ✅ 完整 |
| 数据管理 (D01-D04) | 4 | PostgreSQL + ClickHouse TTL | ✅ 完整 |

**覆盖率：37/37 = 100%**

---

### Implementation Readiness Validation ✅

#### Decision Completeness

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 技术栈版本 | ✅ 完成 | 所有组件版本已指定 |
| 信创适配路径 | ✅ 完成 | 迁移方案已规划 |
| 安全方案 | ✅ 完成 | JWT + CASL + 加密策略 |
| 部署方案 | ✅ 完成 | 华为云 + K8s + GitLab CI |

#### Structure Completeness

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 后端目录结构 | ✅ 完成 | 完整到文件级别 |
| 前端目录结构 | ✅ 完成 | 完整到组件级别 |
| 基础设施结构 | ✅ 完成 | K8s + Terraform |
| 共享包结构 | ✅ 完成 | shared-types, shared-utils |

#### Pattern Completeness

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 命名模式 | ✅ 完成 | 数据库/API/代码全覆盖 |
| 通信模式 | ✅ 完成 | HTTP/WebSocket/MQTT |
| 错误处理模式 | ✅ 完成 | 全局异常过滤器 |
| 加载状态模式 | ✅ 完成 | Pinia + 组件级 |

---

### Gap Analysis

#### Critical Gaps: None ✅

所有阻塞实现的决策已完成。

#### Important Gaps: None ✅

所有重要模式已定义。

#### Nice-to-Have Improvements

| 项目 | 优先级 | 建议 |
|------|--------|------|
| API限流策略 | P2 | 可在实现阶段细化 |
| 日志采集规范 | P2 | 可在部署阶段细化 |
| 监控告警规则 | P2 | 可在运维阶段细化 |

---

### Architecture Completeness Checklist

**✅ Requirements Analysis**
- [x] 项目上下文分析完成
- [x] 规模与复杂度评估完成
- [x] 技术约束识别完成
- [x] 横切关注点映射完成

**✅ Architectural Decisions**
- [x] 关键决策已记录版本
- [x] 技术栈完整指定
- [x] 集成模式已定义
- [x] 性能考量已处理

**✅ Implementation Patterns**
- [x] 命名约定已建立
- [x] 结构模式已定义
- [x] 通信模式已指定
- [x] 流程模式已文档化

**✅ Project Structure**
- [x] 完整目录结构已定义
- [x] 组件边界已建立
- [x] 集成点已映射
- [x] 需求到结构映射完成

---

### Architecture Readiness Assessment

**整体状态：** ✅ **准备就绪**

**置信度：** 高

**关键优势：**
1. 前后端TypeScript统一，类型安全有保障
2. 信创适配路径清晰，降低未来风险
3. 时序数据架构（ClickHouse）支撑高性能查询
4. 模块化设计支持团队并行开发
5. 完整的57个FR映射确保需求可追溯

**未来增强方向：**
1. Post-MVP阶段可引入Istio服务网格
2. 规模扩展时可增加GraphQL API层
3. 多租户支持可在Growth阶段实现

---

### Implementation Handoff

**AI Agent 指南：**
1. 严格遵循架构文档中的所有决策
2. 使用实现模式保持代码一致性
3. 遵守项目结构和边界定义
4. 遇到架构问题时参考本文档

**优先实现顺序：**
1. 基础设施搭建（K8s、数据库、Redis）
2. 后端框架初始化（NestJS + TypeORM）
3. 认证授权模块（JWT + CASL）
4. 设备管理模块（核心业务）
5. 前端框架搭建（Vue Vben Admin）