# IoT 设备数据订阅功能 — 前端设计文档

> 版本: v3.0 | 日期: 2026-04-23 | 状态: 评审中
> v3.0 变更: 新增 v1.0 前端缺口组件 + v1.1 前端演进规划（健康面板 SSE、模板编辑器、设备组选择器、4 个新 Store、3 阶段迁移方案）
> v2.1 变更: 纳入评审意见 — 配额展示、Header 注入防护提示、端点删除确认
> v2.0 变更: 前端从 WebSocket 告警展示转为 Webhook 配置管理界面

---

## 1. 页面路由规划

| 路由路径 | 页面 | 说明 | MVP |
|----------|------|------|-----|
| `/webhook/endpoints` | Webhook 端点管理 | 端点列表 + 创建/编辑/测试 | Must |
| `/devices` | 设备管理页 | 左树右列表，设备浏览入口 | Must |
| `/devices/:id/datapoints` | 数据点浏览页 | 单设备数据点详情与实时值 | Must |
| `/subscriptions` | 订阅管理页 | 订阅列表 + 创建入口 | Must |
| `/subscriptions/create` | 创建订阅页 | 三步 Stepper 引导 | Must |
| `/webhook/logs` | 推送日志页 | 推送记录查询与重试 | Must |

---

## 2. 核心页面设计

### 2.1 Webhook 端点管理页 (`/webhook/endpoints`)

**端点列表卡片视图**（默认）:

| 字段 | 说明 |
|------|------|
| 端点名称 | 如"企业微信群机器人" |
| URL | 接收地址（截断显示，hover 展开） |
| 状态 | 启用(绿) / 禁用(灰) |
| 最近推送 | 相对时间 |
| 健康度 | 绿/黄/红 指示灯（基于近期推送成功率） |
| 操作 | 「编辑」「测试」「删除」 |

**创建端点弹窗**（需先展示当前配额使用量，如"已使用 12/50 个端点"）：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| 端点名称 | Input | 是 | 自定义名称 |
| 接收 URL | Input | 是 | HTTPS 地址，带格式校验 |
| 自定义 Headers | Key-Value 编辑器 | 否 | 可添加多组 Header，过滤 `\r\n` 防注入 |
| 描述 | Textarea | 否 | 备注说明 |

**密钥展示**: 创建成功后弹出密钥展示对话框：
- 显示生成的 Secret（`whsec_...`），强调"仅展示一次"
- 提供「复制密钥」按钮
- 提供「下载密钥文件」按钮
- 用户确认已保存后关闭弹窗

**测试推送**: 点击「测试」→ 发送测试推送 → 展示结果（成功/失败 + HTTP 状态码 + 响应体）。测试频率限制：每端点每分钟最多 3 次。

**删除端点**: 删除前检查是否有关联活跃订阅，有关联时弹出警告"该端点有 N 个活跃订阅，删除后将停用这些订阅，确认删除？"

### 2.2 设备管理页 (`/devices`)

**布局**: 左树右列表（与 v1.0 相同，仅移除 WebSocket 相关功能）

**左侧 — 设备分组树**:
- 多级嵌套（园区 > 楼栋 > 楼层 > 设备类型）
- 树节点显示：分组名称 + 设备数量 badge
- 支持搜索过滤、懒加载
- 选中分组后右侧列表自动过滤

**右侧 — 设备列表**:

| 列 | 说明 |
|----|------|
| 状态 | 实心圆点（绿/灰/黄/红）+ 呼吸灯动画 |
| 设备名称 | 可点击进入详情 |
| 设备 ID | 唯一标识 |
| 设备类型 | 产品模板名称 |
| 最后上报 | 相对时间（如"3秒前"） |
| 操作 | 「查看数据点」「创建订阅」 |

### 2.3 数据点浏览页 (`/devices/:id/datapoints`)

**顶部 — 设备信息卡片**: 设备名称、类型、在线状态、最后通信时间

**主体 — 数据点展示**:
- 表格视图（默认）/ 卡片视图切换
- 每个数据点：名称、标识符、当前值（带单位）、更新时间
- 当前值实时刷新，值变化时有闪烁渐变效果
- 超范围值变色（红/橙）
- 单行操作：「订阅此数据点」→ 跳转创建页并预填

### 2.4 订阅管理页 (`/subscriptions`)

**订阅列表表格**:

| 列 | 说明 |
|----|------|
| 订阅名称 | 可点击进入详情 |
| 推送端点 | 端点名称，可点击跳转端点管理 |
| 关联目标 | 设备名称 / 设备类型名 / 分组名 |
| 数据点 | 数据点标识列表 |
| 规则摘要 | 如"temperature > 80°C" |
| 告警等级 | Critical(红) / Warning(橙) / Info(蓝) 标签 |
| 状态 | 启用(绿) / 暂停(灰) 开关 |
| 创建时间 | 日期 |
| 操作 | 「编辑」「删除」 |

**工具栏**: 状态筛选 + 等级筛选 + 搜索 + 「新建订阅」按钮

**空状态**: 引导图 + "创建你的第一个订阅" 跳转按钮

### 2.5 创建订阅页 (`/subscriptions/create`)

**三步 Stepper 流程**:

```
[Step1: 选择设备] → [Step2: 选择数据点+设置规则] → [Step3: 确认创建]
```

**Step 1 — 选择设备 + 选择端点**:
- 上半：设备树 + 设备列表（复用 DeviceTree + DeviceTable 组件）
- 下半：Webhook 端点选择（下拉选择已有端点 / 「新建端点」快捷跳转）
- 支持按设备实例选择 / 按设备类型选择（Tab 切换）

**Step 2 — 选择数据点 + 设置规则**:
- 基于选中的设备展示其所有数据点
- Checkbox 列表：数据点名称 + 标识符 + 数据类型 + 当前值
- 每个已选数据点独立设置规则（卡片式布局）：
  - **阈值告警**: 运算符(≥/≤/==/!=) + 阈值输入
  - **离线告警**: 超时分钟数输入
- 告警等级选择：Critical / Warning / Info
- 订阅名称

**Step 3 — 确认创建**:
- 汇总展示：设备信息 + 推送端点 + 已选数据点 + 每条规则摘要
- 「保存为草稿」/「直接启用」

### 2.6 推送日志页 (`/webhook/logs`)

**筛选区**: 订阅选择 + 端点选择 + 状态筛选（全部/成功/失败/重试中/已死信）+ 时间范围

**日志列表表格**:

| 列 | 说明 |
|----|------|
| 状态图标 | 成功(绿✓) / 失败(红✗) / 重试中(黄⟳) / 死信(灰☠) |
| 事件 ID | 可点击查看详情 |
| 订阅名称 | 关联订阅 |
| 推送端点 | 目标 URL（截断） |
| HTTP 状态码 | 200/500/超时 等 |
| 重试次数 | 已重试 / 最大次数 |
| 推送时间 | 日期时间 |
| 耗时 | 毫秒 |
| 操作 | 「查看详情」「重试」（仅失败/死信状态） |

**日志详情弹窗**:
- 请求头（含签名头，密钥打码显示）
- 请求体（JSON 格式化，可复制）
- 响应状态码 + 响应体
- 重试历史时间线（每次重试的时间和结果）

**推送统计卡片**（页面顶部）:
- 近 24h 推送总数 / 成功率 / 平均延迟 / P95 延迟

---

## 3. 状态管理（Pinia Store）

### 3.1 useEndpointStore

```typescript
interface EndpointState {
  endpoints: WebhookEndpoint[]
  currentEndpoint: WebhookEndpoint | null
  secretOnceVisible: string | null   // 创建时一次性展示的密钥
  loading: boolean
  filters: EndpointFilters
}

interface WebhookEndpoint {
  id: number
  name: string
  url: string
  customHeaders: Record<string, string>
  status: 'ACTIVE' | 'DISABLED'
  consecutiveFailures: number
  lastPushAt: string | null
  lastSuccessAt: string | null
  createdAt: string
}

// Actions: fetchEndpoints, createEndpoint, updateEndpoint,
//          deleteEndpoint, testEndpoint, rotateSecret
```

### 3.2 useDeviceStore

```typescript
interface DeviceState {
  deviceTree: TreeNode[]
  deviceList: Device[]
  selectedDeviceId: string | null
  loading: boolean
  filters: DeviceFilters
  pagination: { page: number; pageSize: number; total: number }
}
// Actions: fetchDeviceTree, fetchDeviceList, selectDevice
```

### 3.3 useDataPointStore

```typescript
interface DataPointState {
  dataPoints: DataPoint[]
  loading: boolean
}
// Actions: fetchDataPoints
```

### 3.4 useSubscriptionStore

```typescript
interface SubscriptionState {
  subscriptions: Subscription[]
  subscriptionDetail: Subscription | null
  createFlowState: CreateFlowState  // Stepper 临时状态
  loading: boolean
  filters: SubscriptionFilters
  pagination: { page: number; pageSize: number; total: number }
}
// Actions: fetchSubscriptions, createSubscription, updateSubscription,
//          toggleSubscriptionStatus, deleteSubscription
```

### 3.5 useWebhookLogStore

```typescript
interface WebhookLogState {
  logs: WebhookDeliveryLog[]
  logDetail: WebhookDeliveryLog | null
  stats: PushStats
  loading: boolean
  filters: LogFilters
  pagination: { page: number; pageSize: number; total: number }
}

interface WebhookDeliveryLog {
  id: number
  configId: number
  subscriptionId: number
  event: string
  eventId: string
  deviceId: number
  payload: string
  status: 'PENDING' | 'SUCCESS' | 'FAILED' | 'RETRYING' | 'DEAD'
  attemptCount: number
  maxRetries: number
  responseCode: number | null
  responseBody: string | null
  errorMsg: string | null
  nextRetryAt: string | null
  deliveredAt: string | null
  createdAt: string
}

interface PushStats {
  totalLast24h: number
  successRate: number
  avgLatencyMs: number
  p95LatencyMs: number
}
// Actions: fetchLogs, fetchLogDetail, retryLog, fetchStats
```

---

## 4. 组件架构

```
src/
├── views/
│   ├── webhook/
│   │   ├── EndpointManagePage.vue
│   │   └── WebhookLogPage.vue
│   ├── device/
│   │   ├── DeviceManagePage.vue
│   │   └── DataPointBrowsePage.vue
│   └── subscription/
│       ├── SubscriptionListPage.vue
│       └── SubscriptionCreatePage.vue
│
├── components/
│   ├── webhook/
│   │   ├── EndpointCard.vue              # 端点卡片
│   │   ├── EndpointFormDialog.vue        # 创建/编辑端点弹窗
│   │   ├── EndpointDeleteGuard.vue       # [v1.0缺口] 端点删除守卫（检查关联订阅）
│   │   ├── SecretRevealDialog.vue        # 密钥一次性展示弹窗（复用于密钥轮换）
│   │   ├── TestPushDialog.vue            # 测试推送弹窗（固定/自定义 payload）
│   │   ├── RotateSecretConfirmDialog.vue # 密钥轮换确认弹窗（二次确认）
│   │   ├── EndpointHealthDot.vue         # 端点健康指示灯
│   │   ├── EndpointHealthDialog.vue      # 端点健康详情弹窗
│   │   ├── HealthTrendChart.vue          # 健康指标趋势图
│   │   ├── CircuitBreakerStatus.vue      # 熔断器状态展示
│   │   ├── LogDetailDialog.vue           # 推送日志详情弹窗
│   │   ├── LogRetryTimeline.vue          # 重试时间线
│   │   └── PushStatsCards.vue            # 推送统计卡片组
│   ├── notification/
│   │   ├── NotificationBadge.vue         # 通知徽章（Header 挂载）
│   │   ├── NotificationPopover.vue       # 通知弹出面板
│   │   ├── NotificationListPage.vue      # 通知列表页
│   │   └── NotificationPreferences.vue   # 通知偏好设置
│   ├── device/
│   │   ├── DeviceTree.vue                # 设备分组树（可复用）
│   │   ├── VirtualTree.vue               # [v1.0缺口] 虚拟滚动树（>500节点性能保障）
│   │   ├── DeviceTable.vue               # 设备列表（可复用）
│   │   ├── DeviceStatusDot.vue           # 状态指示点
│   │   ├── DeviceInfoCard.vue            # 设备信息卡片
│   │   └── DeviceToolbar.vue             # 筛选工具栏
│   ├── datapoint/
│   │   ├── DataPointTable.vue            # 数据点表格
│   │   └── DataPointCard.vue             # 数据点卡片
│   ├── subscription/
│   │   ├── StepSelectDevice.vue          # Step1
│   │   ├── StepSetRules.vue              # Step2
│   │   ├── StepConfirm.vue               # Step3
│   │   ├── RuleEditor.vue                # 规则编辑器（可复用）
│   │   ├── RuleConflictWarning.vue       # [v1.0缺口] 多规则同设备冲突检测提示
│   │   ├── SubscriptionCard.vue          # 订阅卡片
│   │   ├── BatchActionBar.vue            # [v1.0缺口] 批量操作栏（启用/禁用/删除）
│   │   └── EmptyState.vue                # [v1.0缺口] 空状态引导组件
│   └── common/
│       ├── StatusStepper.vue             # 步骤条
│       ├── SearchFilter.vue              # 通用搜索筛选
│       ├── PaginatedTable.vue            # 分页表格封装
│       ├── KeyValueEditor.vue            # Key-Value 编辑器（Headers）
│       ├── JsonViewer.vue                # JSON 格式展示器（大 JSON 虚拟滚动）
│       └── ConfirmDialog.vue             # [v1.0缺口] 通用确认对话框
│
├── composables/
│   ├── usePolling.ts                     # 定时轮询 Hook（日志刷新）
│   ├── useCopyToClipboard.ts             # 复制到剪贴板
│   ├── useDraftRecovery.ts               # 表单草稿恢复（sessionStorage）
│   ├── useOffsetPagination.ts            # 偏移分页 composable
│   ├── useCursorPagination.ts            # 游标分页 composable（大数据量）
│   └── useRuleForm.ts                    # 规则表单逻辑 + 校验
│
├── services/
│   ├── endpointService.ts                # Webhook 端点 API
│   ├── deviceService.ts                  # 设备 API
│   ├── dataPointService.ts               # 数据点 API
│   ├── subscriptionService.ts            # 订阅 API
│   ├── webhookLogService.ts              # 推送日志 API
│   └── notificationService.ts            # 通知 API
│
├── stores/
│   ├── endpoint.ts
│   ├── device.ts
│   ├── dataPoint.ts
│   ├── subscription.ts
│   ├── webhookLog.ts
│   └── notification.ts
│
└── types/
    ├── endpoint.ts
    ├── device.ts
    ├── datapoint.ts
    ├── subscription.ts
    ├── webhook-log.ts
    └── notification.ts
```

### 核心可复用组件

| 组件 | 复用场景 |
|------|---------|
| `DeviceTree` / `VirtualTree` | 设备管理页 + 创建订阅 Step1 |
| `DeviceTable` | 设备管理页 + 创建订阅 Step1 |
| `DeviceStatusDot` | 设备列表、数据点页 |
| `RuleEditor` | 创建订阅 Step2 + 编辑订阅 |
| `KeyValueEditor` | 端点 Headers 编辑 + 其他配置场景 |
| `JsonViewer` | 推送日志详情 + 测试推送结果（虚拟滚动） |
| `ConfirmDialog` | 端点删除 + 订阅删除 + 密钥轮换（统一二次确认） |
| `EmptyState` | 订阅列表 + 端点列表 + 日志列表空状态 |
| `BatchActionBar` | 订阅批量启用/禁用/删除 + 端点批量操作 |
| `EndpointDeleteGuard` | 端点删除前检查关联订阅，阻止误删 |
| `RuleConflictWarning` | 创建/编辑订阅时检测同设备多规则冲突 |

---

## 5. API 对接方案

### Service 层原则

- 组件和 Store 不直接调用 axios，通过 Service 层
- Service 负责：参数构造、响应转换、错误统一处理
- Store 调用 Service，管理 loading 状态

### API 列表

```
Webhook 端点:
  POST   /api/v1/webhook-endpoints                    → endpointService.create()
  GET    /api/v1/webhook-endpoints                    → endpointService.getList()
  GET    /api/v1/webhook-endpoints/{id}               → endpointService.getDetail()
  PUT    /api/v1/webhook-endpoints/{id}               → endpointService.update()
  DELETE /api/v1/webhook-endpoints/{id}               → endpointService.delete()
  POST   /api/v1/webhook-endpoints/{id}/test          → endpointService.test()
  GET    /api/v1/webhook-endpoints/{id}/health        → endpointService.getHealth()
  PATCH  /api/v1/webhook-endpoints/{id}/rotate-secret → endpointService.rotateSecret()

设备:
  GET    /api/v1/devices                              → deviceService.getList()
  GET    /api/v1/devices/{id}                         → deviceService.getDetail()
  GET    /api/v1/devices/{id}/datapoints              → dataPointService.getList()
  GET    /api/v1/device-groups/tree                   → deviceService.getGroupTree()

订阅:
  GET    /api/v1/subscriptions                        → subscriptionService.getList()
  POST   /api/v1/subscriptions                        → subscriptionService.create()
  GET    /api/v1/subscriptions/{id}                   → subscriptionService.getDetail()
  PUT    /api/v1/subscriptions/{id}                   → subscriptionService.update()
  PATCH  /api/v1/subscriptions/{id}/status            → subscriptionService.toggleStatus()
  DELETE /api/v1/subscriptions/{id}                   → subscriptionService.delete()

推送日志:
  GET    /api/v1/webhook-logs                         → webhookLogService.getList()
  GET    /api/v1/webhook-logs/{id}                    → webhookLogService.getDetail()
  POST   /api/v1/webhook-logs/{id}/retry              → webhookLogService.retry()
  GET    /api/v1/webhook-logs/stats                   → webhookLogService.getStats()
```

---

## 6. 关键交互细节

### 6.1 密钥安全管理（前端）

- 创建端点后密钥仅在弹窗中展示一次，关闭后不可再查
- 弹窗内有倒计时自动关闭提示（5 分钟）
- 复制到剪贴板使用 `navigator.clipboard.writeText()`
- 提供「下载为 .txt 文件」功能

### 6.2 Stepper 流程状态持久化

创建订阅的三步状态存储在 `useSubscriptionStore.createFlowState`，持久化到 `sessionStorage`。刷新页面不丢失，取消时确认提示。

### 6.3 创建流程的快捷入口

| 入口 | 行为 |
|------|------|
| 设备列表「创建订阅」 | 跳转创建页，Step1 自动选中该设备 |
| 数据点页「订阅此数据点」 | 跳转创建页，Step1 自动选中设备，Step2 自动勾选该数据点 |
| 推送日志「查看订阅」 | 跳转对应订阅详情 |

### 6.4 端点健康指示

- 绿色：近 24h 推送成功率 > 95%
- 黄色：近 24h 推送成功率 80%-95% 或有连续 5+ 次失败
- 红色：近 24h 推送成功率 < 80% 或连续失败超过 20 次
- 灰色：从未推送过

### 6.5 推送日志自动刷新

- 日志页面默认每 10 秒自动刷新列表
- 用户切换筛选条件时停止自动刷新
- 提供「手动刷新」按钮
- 使用 `usePolling` composable 统一管理

### 6.6 SSRF 提示

创建/编辑端点时，URL 输入框下方实时校验：
- 非法协议 → 红色提示"仅支持 HTTP/HTTPS 协议"
- 内网地址 → 红色提示"禁止使用内网地址"
- 开发模式（`VITE_ALLOW_HTTP=true`）→ 允许 HTTP 和内网地址

---

## 7. RuleEditor 组件规格

### 7.1 TypeScript 接口定义

```typescript
// types/subscription.ts — 规则相关类型

type Operator = 'gt' | 'gte' | 'lt' | 'lte' | 'eq' | 'neq'

interface ThresholdRule {
  type: 'THRESHOLD'
  operator: Operator
  threshold: number
  dataPointId: number
  cooldownSeconds: number
  priority: 'INFO' | 'WARNING' | 'CRITICAL'
}

interface OfflineRule {
  type: 'OFFLINE'
  timeout: number  // 分钟
  cooldownSeconds: number
  priority: 'INFO' | 'WARNING' | 'CRITICAL'
}

type RuleForm = ThresholdRule | OfflineRule

// 前端表单 → 后端 API 转换
function toApiRule(form: RuleForm) {
  if (form.type === 'THRESHOLD') {
    return {
      ruleType: 'THRESHOLD',
      condition: { operator: form.operator, threshold: form.threshold },
      cooldownSeconds: form.cooldownSeconds,
      priority: form.priority === 'INFO' ? 0 : form.priority === 'WARNING' ? 1 : 2
    }
  }
  return {
    ruleType: 'OFFLINE',
    condition: { type: 'offline', timeout: form.timeout * 60 },
    cooldownSeconds: form.cooldownSeconds,
    priority: form.priority === 'INFO' ? 0 : form.priority === 'WARNING' ? 1 : 2
  }
}

// 后端 API → 前端表单转换
function toRuleForm(apiRule: any): RuleForm {
  if (apiRule.ruleType === 'THRESHOLD') {
    return {
      type: 'THRESHOLD',
      operator: apiRule.condition.operator,
      threshold: apiRule.condition.threshold,
      dataPointId: apiRule.dataPointId ?? 0,
      cooldownSeconds: apiRule.cooldownSeconds,
      priority: apiRule.priority === 0 ? 'INFO' : apiRule.priority === 1 ? 'WARNING' : 'CRITICAL'
    }
  }
  return {
    type: 'OFFLINE',
    timeout: Math.round((apiRule.condition.timeout ?? 600) / 60),
    cooldownSeconds: apiRule.cooldownSeconds,
    priority: apiRule.priority === 0 ? 'INFO' : apiRule.priority === 1 ? 'WARNING' : 'CRITICAL'
  }
}
```

### 7.2 数据类型驱动的运算符选择

| 数据点类型 | 可用运算符 | 阈值输入类型 |
|-----------|-----------|-------------|
| float / int | gt, gte, lt, lte, eq, neq | 数字输入框（支持小数） |
| string | eq, neq | 文本输入框 |
| bool | eq | 开关（true/false） |
| json | 不可配置阈值 | 仅支持离线检测 |

### 7.3 RuleEditor 子组件

| 子组件 | 职责 |
|--------|------|
| `ThresholdRuleForm.vue` | 阈值规则编辑：运算符下拉 + 阈值输入 + 数据点选择 |
| `OfflineRuleForm.vue` | 离线检测规则编辑：超时分钟数输入 |
| `CooldownInput.vue` | 冷却时间输入（预设快捷项：1分钟/5分钟/10分钟/自定义） |
| `PrioritySelect.vue` | 告警等级选择（Info/Warning/Critical 颜色标签） |

### 7.4 useRuleForm composable

```typescript
// composables/useRuleForm.ts
export function useRuleForm(dataPoint: Ref<DataPoint>) {
  const rules = ref<RuleForm[]>([])
  const errors = ref<Record<string, string>>({})

  // 根据数据点类型提供可用规则类型
  const availableRuleTypes = computed(() => {
    const type = dataPoint.value.dataType
    if (['float', 'int'].includes(type)) return ['THRESHOLD']
    if (type === 'bool') return ['THRESHOLD']  // 仅 eq
    return []  // json 等类型仅支持离线检测
  })

  // 根据数据点类型提供可用运算符
  const availableOperators = computed<Operator[]>(() => {
    const type = dataPoint.value.dataType
    if (['float', 'int'].includes(type)) return ['gt', 'gte', 'lt', 'lte', 'eq', 'neq']
    if (type === 'string') return ['eq', 'neq']
    if (type === 'bool') return ['eq']
    return []
  })

  function addRule(type: 'THRESHOLD' | 'OFFLINE') { ... }
  function removeRule(index: number) { ... }
  function validate(): boolean { ... }
  function toApiPayload() { return rules.value.map(toApiRule) }

  return { rules, errors, availableRuleTypes, availableOperators,
           addRule, removeRule, validate, toApiPayload }
}
```

---

## 8. 核心组件详细规格

### 8.1 DeviceTree 组件

**加载策略**: 两级懒加载
- 第一级：加载园区/楼栋/楼层节点（`GET /api/v1/device-groups/tree`）
- 第二级：点击展开时加载子节点 + 该分组下的设备列表
- 搜索：后端搜索（传入关键词，服务端过滤），前端不做本地过滤

**节点展示**:
```
📁 园区A (128)
  📁 1号楼 (45)
    📁 1楼 (12)
    📁 2楼 (33)
  📁 2号楼 (83)
```

**交互**:
- 点击分组节点 → 右侧列表过滤该分组下的设备
- 搜索框输入 → 300ms 防抖 → 调用后端搜索 API → 高亮匹配节点
- 支持多选（创建订阅时，选择多个分组/设备）

### 8.2 KeyValueEditor 组件

**用于**: 端点自定义 Headers 编辑

**交互细节**:
- 自由输入模式：Key 和 Value 均为 Input 输入框
- 建议列表：Key 输入时展示常用 Header 建议（Content-Type, Authorization, X-Custom-*）
- 大小写去重：输入 `content-type` 时提示已存在 `Content-Type`
- 防注入：过滤 `\r\n` 字符，防止 HTTP Header 注入
- 添加/删除行：每行右侧删除按钮，底部「添加」按钮
- 空值校验：Key 为空时整行标红

### 8.3 JsonViewer 组件

**用于**: 推送日志详情、测试推送结果展示

**技术方案**: 使用 `vue-json-pretty` 库
- 始终只读模式（不可编辑）
- 支持折叠/展开（默认展开第一层）
- 支持复制全文（调用 `useCopyToClipboard`）
- 大 JSON 虚拟滚动（超过 1000 行时启用）

### 8.4 StatusStepper 组件

**双模式**:
1. **向导模式**（创建订阅）：3 步水平 Stepper，当前步高亮，已完步可点击回退
2. **时间线模式**（推送日志重试历史）：垂直时间线，每步展示时间和结果状态

**Props**:
```typescript
interface StepperProps {
  mode: 'wizard' | 'timeline'
  steps: Step[]
  currentStep: number
  linear?: boolean  // 是否严格按顺序（向导模式默认 true）
}
```

### 8.5 TestPushDialog 组件

**Props**:
```typescript
interface TestPushProps {
  endpointId: number
  endpointName: string
}
```

**交互流程**:
1. 弹窗打开 → 展示 payload 编辑区（默认填充固定测试 payload）
2. 可切换「固定 payload」/「自定义 payload」Tab
3. 点击「发送测试」→ 展示 loading → 结果展示
4. 结果区域：HTTP 状态码（颜色标记）+ 响应体（JsonViewer）+ 耗时
5. 频率超限时按钮禁用 + 倒计时提示

### 8.6 RotateSecretConfirmDialog 组件

**二次确认流程**:
1. 弹窗展示警告文字："轮换密钥后，旧密钥将在 5 分钟后失效，请及时更新第三方系统配置"
2. 输入框要求输入端点名称（与当前端点名称匹配才能确认）
3. 确认按钮 → 调用 API → 成功后弹出 SecretRevealDialog 展示新密钥
4. 取消按钮关闭弹窗，无副作用

---

## 9. 前端 Composables

### 9.1 useDraftRecovery

```typescript
// composables/useDraftRecovery.ts
export function useDraftRecovery<T>(key: string, defaultValue: T) {
  const storageKey = `draft:${key}`

  const draft = ref<T>(loadFromSessionStorage(storageKey) ?? defaultValue)

  // 自动保存（300ms 防抖）
  watch(draft, debounce(() => {
    sessionStorage.setItem(storageKey, JSON.stringify(draft.value))
  }, 300), { deep: true })

  function clearDraft() {
    sessionStorage.removeItem(storageKey)
  }

  return { draft, clearDraft }
}
```

### 9.2 useOffsetPagination

```typescript
// composables/useOffsetPagination.ts
export function useOffsetPagination(fetchFn: (page: number, size: number) => Promise<PageResult>) {
  const page = ref(1)
  const pageSize = ref(20)
  const total = ref(0)
  const loading = ref(false)
  const data = ref([])

  async function refresh() {
    loading.value = true
    try {
      const result = await fetchFn(page.value - 1, pageSize.value)
      data.value = result.content
      total.value = result.totalElements
    } finally {
      loading.value = false
    }
  }

  watch([page, pageSize], refresh)

  return { page, pageSize, total, loading, data, refresh }
}
```

### 9.3 useNotificationStore

```typescript
// stores/notification.ts
interface NotificationState {
  notifications: AppNotification[]
  unreadCount: number
  preferences: NotificationPreference
  loading: boolean
}

interface AppNotification {
  id: number
  type: 'ENDPOINT_FAILURE' | 'ENDPOINT_RECOVERED' | 'SUBSCRIPTION_CANCELLED'
  title: string
  content: string
  relatedId: number
  relatedType: 'ENDPOINT' | 'SUBSCRIPTION'
  isRead: boolean
  createdAt: string
}

interface NotificationPreference {
  endpointFailureEnabled: boolean
  endpointRecoveredEnabled: boolean
  failureFrequency: 'EACH' | 'DAILY_SUMMARY'
}

// Actions: fetchNotifications, fetchUnreadCount, markAsRead, markAllAsRead,
//          fetchPreferences, updatePreferences
```

---

## 10. v1.0 前端缺口组件规格

> 以下组件在 v1.0 设计评审中发现缺失，需要在开发前补齐。

### 10.1 EndpointDeleteGuard

**职责**: 端点删除前的安全守卫，检查是否有关联活跃订阅。

```typescript
// 调用方式
const guard = new EndpointDeleteGuard(endpointService)
const result = await guard.check(endpointId)
// result: { canDelete: boolean; activeSubscriptions: number; subscriptionNames: string[] }
```

**交互流程**:
1. 用户点击删除 → 调用 `GET /api/v1/webhook-endpoints/{id}/subscriptions?status=ACTIVE`
2. 无关联 → 直接弹出 `ConfirmDialog` 确认删除
3. 有关联 → 弹出警告弹窗，列出关联订阅名称，需输入端点名称确认

### 10.2 VirtualTree

**职责**: 大数据量设备树的虚拟滚动方案。

```typescript
interface VirtualTreeProps {
  nodes: TreeNode[]
  loadFn: (parentId: string | null) => Promise<TreeNode[]>
  selectable?: boolean
  searchFn: (keyword: string) => Promise<string[]>  // 返回匹配节点 ID
}

// 性能指标:
// - 500+ 节点：首次渲染 < 200ms
// - 滚动帧率：≥ 30fps
// - 搜索响应：< 500ms（后端搜索）
```

**技术方案**: 基于 `vue-virtual-scroller` + 自定义 TreeFlattener（将嵌套树打平为虚拟列表项）。

### 10.3 BatchActionBar

**职责**: 列表页面的批量操作栏，支持选中后浮动显示。

```typescript
interface BatchActionBarProps {
  selectedIds: number[]
  actions: BatchAction[]
}

type BatchAction = {
  label: string
  icon: string
  type: 'primary' | 'warning' | 'danger'
  handler: (ids: number[]) => Promise<void>
  confirmText?: string  // 有值时弹出 ConfirmDialog
}
```

**交互**: 选中 ≥ 1 项时从底部滑入，显示已选数量 + 操作按钮。点击操作后批量执行，显示进度。

### 10.4 EmptyState

**职责**: 各列表页的空状态引导。

```typescript
interface EmptyStateProps {
  title: string
  description: string
  icon?: string
  actionLabel?: string
  actionRoute?: string
}
```

**使用场景**:
- 端点列表: "创建你的第一个推送端点" → 跳转创建端点
- 订阅列表: "创建你的第一个订阅规则" → 跳转创建订阅
- 日志列表: "暂无推送记录" → 无操作按钮

### 10.5 ConfirmDialog

**职责**: 统一的二次确认对话框。

```typescript
interface ConfirmDialogProps {
  visible: boolean
  title: string
  message: string
  type: 'info' | 'warning' | 'danger'
  confirmText?: string
  cancelText?: string
  inputConfirm?: string  // 需要输入确认文字（如端点名）
  loading?: boolean
}
```

**复用场景**: 端点删除、订阅删除、密钥轮换、批量操作确认。

### 10.6 RuleConflictWarning

**职责**: 创建/编辑订阅时检测同一设备的规则冲突。

```typescript
interface RuleConflictWarningProps {
  deviceId: number
  dataPointIds: number[]
  currentRuleId?: number  // 编辑时排除自身
}
```

**冲突检测逻辑**:
1. 查询同一设备 + 相同数据点的已有规则
2. 检查阈值范围是否有重叠（如 rule1: temp>80, rule2: temp>70 → 告警"规则范围重叠"）
3. 检查冷却时间是否合理（重叠规则冷却时间差异 > 5min → 提示）
4. 以 warning 样式展示冲突列表，不阻止提交

---

## 11. v1.1 前端演进规划

### 11.1 新增页面与路由

| 路由路径 | 页面 | 说明 |
|----------|------|------|
| `/health` | 健康监控面板 | SSE 实时推送健康状态 |
| `/templates` | 订阅模板管理 | 模板列表 + 创建/编辑 |
| `/templates/:id/apply` | 应用模板 | 选设备 → 应用模板 → 确认 |

### 11.2 健康监控面板 (`/health`)

**布局**: 顶部概览卡片 + 端点健康网格 + 实时事件流

**顶部概览卡片**:
| 卡片 | 数据源 | 刷新方式 |
|------|--------|---------|
| 总端点数 | REST API | 页面加载 |
| 健康端点数 | SSE `/sse/health` | 实时 |
| 熔断中端点 | SSE `/sse/health` | 实时 |
| 24h 推送成功率 | REST API `/api/v1/webhook-logs/stats` | 30s 轮询 |

**端点健康网格**:
- 每个端点一个卡片：名称 + 健康指示灯 + 成功率趋势 mini 图 + 熔断状态
- 点击卡片展开详情（近期推送历史 + 重试记录 + 响应时间分布）

**实时事件流**:
- 最近的推送事件滚动列表（自动滚动，可暂停）
- 每条：时间 + 端点名 + 状态(成功/失败) + 响应时间

### 11.3 SSE 实时推送方案

> 选择 SSE 而非 WebSocket 的理由：单向推送、Spring Boot SseEmitter 原生支持、EventSource 自动重连、降级到轮询简单。

**后端端点**:
```
GET /api/v1/sse/health          → 全局健康事件流
GET /api/v1/sse/endpoint/{id}   → 单端点详细事件流
```

**SSE 事件格式**:
```typescript
interface SSEHealthEvent {
  event: 'HEALTH_CHANGE' | 'CIRCUIT_BREAKER' | 'PUSH_RESULT'
  data: {
    endpointId: number
    endpointName: string
    type: 'HEALTH_CHANGE'
    health: 'HEALTHY' | 'DEGRADED' | 'UNHEALTHY'
    successRate: number
    timestamp: string
  } | {
    type: 'CIRCUIT_BREAKER'
    state: 'CLOSED' | 'OPEN' | 'HALF_OPEN'
    failureCount: number
    timestamp: string
  } | {
    type: 'PUSH_RESULT'
    status: 'SUCCESS' | 'FAILED'
    responseTime: number
    timestamp: string
  }
}
```

**前端 SSE 连接管理**:
```typescript
// composables/useSSE.ts
export function useSSE(url: string) {
  const events = ref<SSEHealthEvent[]>([])
  const status = ref<'connecting' | 'connected' | 'disconnected'>('disconnected')
  const reconnectCount = ref(0)

  let source: EventSource | null = null
  let pollTimer: number | null = null

  function connect() {
    source = new EventSource(url)
    status.value = 'connecting'

    source.onopen = () => {
      status.value = 'connected'
      reconnectCount.value = 0
    }

    source.addEventListener('HEALTH_CHANGE', (e) => {
      events.value.unshift(JSON.parse(e.data))
      if (events.value.length > 100) events.value.length = 100  // 保留最近 100 条
    })

    source.addEventListener('CIRCUIT_BREAKER', (e) => { ... })
    source.addEventListener('PUSH_RESULT', (e) => { ... })

    source.onerror = () => {
      status.value = 'disconnected'
      source?.close()
      // 指数退避重连：1s → 2s → 4s → 8s → 最多 30s
      const delay = Math.min(1000 * Math.pow(2, reconnectCount.value), 30000)
      reconnectCount.value++
      setTimeout(connect, delay)
      // 超过 5 次重连失败，降级到 10s 轮询
      if (reconnectCount.value > 5) fallbackToPolling()
    }
  }

  function fallbackToPolling() {
    pollTimer = window.setInterval(async () => {
      // 调用 REST API 获取最新健康状态
      const data = await healthService.getLatest()
      events.value.unshift(...data)
    }, 10000)
  }

  function disconnect() {
    source?.close()
    if (pollTimer) clearInterval(pollTimer)
    status.value = 'disconnected'
  }

  onMounted(connect)
  onUnmounted(disconnect)

  return { events, status, reconnectCount, connect, disconnect }
}
```

### 11.4 订阅模板管理 (`/templates`)

**模板列表表格**:

| 列 | 说明 |
|----|------|
| 模板名称 | 如"温度超限告警" |
| 数据点类型 | 关联的数据点类型列表 |
| 规则摘要 | 预览规则配置 |
| 使用次数 | 已被应用次数 |
| 创建时间 | 日期 |
| 操作 | 「编辑」「应用」「删除」 |

**创建模板弹窗**:
- 选择数据点类型（下拉）
- 设置规则（复用 RuleEditor）
- 设置告警等级
- 模板名称 + 描述

**应用模板流程**:
1. 选择模板 → 选择目标设备（支持多选）
2. 预览：每个设备生成的订阅配置
3. 确认 → 批量创建订阅

### 11.5 v1.1 新增 Store

```typescript
// stores/health.ts — useHealthStore
interface HealthState {
  endpointHealthMap: Map<number, EndpointHealth>  // endpointId → health
  sseStatus: 'connecting' | 'connected' | 'disconnected'
  recentEvents: SSEHealthEvent[]                  // 最近 100 条
  loading: boolean
}

interface EndpointHealth {
  endpointId: number
  endpointName: string
  health: 'HEALTHY' | 'DEGRADED' | 'UNHEALTHY'
  successRate: number
  circuitBreakerState: 'CLOSED' | 'OPEN' | 'HALF_OPEN'
  lastPushAt: string | null
}

// Actions: connectSSE, disconnectSSE, fetchAllHealth, getEndpointHealth
```

```typescript
// stores/template.ts — useTemplateStore
interface TemplateState {
  templates: SubscriptionTemplate[]
  currentTemplate: SubscriptionTemplate | null
  loading: boolean
  filters: TemplateFilters
  pagination: { page: number; pageSize: number; total: number }
}

interface SubscriptionTemplate {
  id: number
  name: string
  description: string
  dataPointTypes: string[]
  rules: RuleForm[]
  priority: 'INFO' | 'WARNING' | 'CRITICAL'
  usageCount: number
  createdAt: string
}

// Actions: fetchTemplates, createTemplate, updateTemplate, deleteTemplate, applyTemplate
```

```typescript
// stores/device-group.ts — useDeviceGroupStore
interface DeviceGroupState {
  groupTree: GroupTreeNode[]
  selectedGroupIds: string[]
  expandedKeys: string[]
  loading: boolean
}

interface GroupTreeNode {
  id: string
  name: string
  deviceCount: number
  children: GroupTreeNode[]
}

// Actions: fetchGroupTree, toggleExpand, selectGroups
```

```typescript
// stores/realtime.ts — useRealtimeStore
interface RealtimeState {
  dataPointValues: Map<string, DataPointValue>  // key: `${deviceId}:${dataPointId}`
  connected: boolean
}

interface DataPointValue {
  deviceId: number
  dataPointId: number
  value: number | string | boolean
  timestamp: string
  quality: 'GOOD' | 'UNCERTAIN' | 'BAD'
}

// Actions: subscribeToDevice, unsubscribeFromDevice
// 用于数据点浏览页的实时值展示
```

### 11.6 v1.1 新增组件树

```
src/
├── views/
│   ├── health/
│   │   └── HealthDashboardPage.vue        # [v1.1] 健康监控面板
│   ├── template/
│   │   ├── TemplateListPage.vue           # [v1.1] 模板管理页
│   │   └── TemplateApplyPage.vue          # [v1.1] 应用模板页
│   └── ...（v1.0 页面不变）
│
├── components/
│   ├── health/
│   │   ├── HealthOverviewCards.vue        # [v1.1] 健康概览卡片组
│   │   ├── EndpointHealthGrid.vue         # [v1.1] 端点健康网格
│   │   ├── EndpointHealthDetail.vue       # [v1.1] 端点健康详情面板
│   │   ├── HealthMiniChart.vue            # [v1.1] 健康趋势 mini 图
│   │   ├── RealtimeEventStream.vue        # [v1.1] 实时事件流
│   │   └── SSEConnectionStatus.vue        # [v1.1] SSE 连接状态指示
│   ├── template/
│   │   ├── TemplateFormDialog.vue         # [v1.1] 创建/编辑模板弹窗
│   │   ├── TemplatePreviewCard.vue        # [v1.1] 模板预览卡片
│   │   └── TemplateApplyReview.vue        # [v1.1] 应用模板确认
│   └── ...（v1.0 组件不变）
│
├── composables/
│   ├── useSSE.ts                          # [v1.1] SSE 连接管理 + 自动重连 + 降级
│   └── ...（v1.0 composables 不变）
│
├── stores/
│   ├── health.ts                          # [v1.1] 健康监控 Store
│   ├── template.ts                        # [v1.1] 订阅模板 Store
│   ├── device-group.ts                    # [v1.1] 设备分组 Store
│   ├── realtime.ts                        # [v1.1] 实时数据点 Store
│   └── ...（v1.0 stores 不变）
│
└── services/
    ├── healthService.ts                   # [v1.1] 健康监控 API
    ├── templateService.ts                 # [v1.1] 模板 API
    └── sseService.ts                      # [v1.1] SSE 连接工厂
```

### 11.7 三阶段迁移方案

| 阶段 | 内容 | 预估工作量 | 依赖 |
|------|------|-----------|------|
| **Phase 1: v1.0 补齐** | 补齐 v1.0 缺口组件（VirtualTree、EndpointDeleteGuard、BatchActionBar、EmptyState、ConfirmDialog、RuleConflictWarning） | ~3 天 | 无 |
| **Phase 2: v1.1-A SSE 基础** | useSSE composable + useHealthStore + HealthDashboardPage（概览卡片 + 端点网格 + 事件流） | ~5 天 | 后端 SSE 端点就绪 |
| **Phase 3: v1.1-B 模板系统** | useTemplateStore + useDeviceGroupStore + TemplateListPage + TemplateApplyPage | ~4 天 | 后端模板 API 就绪 |

**Phase 1 与 Phase 2 可并行**（前端补齐缺口组件不影响后端 SSE 开发）。

### 11.8 SSE 降级策略

```
优先连接 SSE
  ↓ 连接失败 5 次
降级到 10s 轮询 REST API
  ↓ 轮询也失败
显示"连接中断"状态，提供手动刷新按钮
```

**降级指标**:
- SSE 首次连接超时：5s
- 重连指数退避：1s → 2s → 4s → 8s → 16s → 30s（上限）
- 降级轮询频率：10s
- 本地事件缓冲：100 条（FIFO）
- 断线期间 UI：显示最后已知状态 + "数据可能不是最新" 提示
