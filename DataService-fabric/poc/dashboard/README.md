# Data Fabric 验证 Dashboard（Week 1 + Week 2）

**目的**: 通过可视化方式逐项验证 Data Fabric PoC 的完成度。

## Tab 1 · Week 1 跨源联邦查询
- 5 个跨源联邦查询（V1-V5）实时跑在 Trino 上
- 每个用例显示：状态徽章 / 耗时 / 数据源 chips / 多种图表 / 数据表 / SQL 原文
- Trino 不可达时优雅降级（红色徽章 + 错误信息）

## Tab 2 · Week 2 数据服务 + 治理（新增）
5 个端点面板：
- **客户画像** `/api/v1/customers/{id}/profile` — 字段 grid，自动标记脱敏/隐藏字段
- **客户分群** `/api/v1/customers?level=VIP3` — 表格 + 等级/风险 chip
- **全局指标** `/api/v1/metrics/customer-overview` — 5 张指标卡（ARPU / VIP3 / 高中低风险）
- **脱敏对比** — 同一客户 × 3 角色（ADMIN/VIEWER/SUPPORT）并排展示，颜色高亮脱敏差异
- **审计日志** — `/api/v1/audit/recent` 实时流，每 5 秒自动刷新，显示 actor / action / resource / risk

服务栈健康卡（左侧）：Trino / Cube.dev / Spring Boot 三状态，绿色 up / 黄 degraded / 红 down

## 架构

```
Browser (localhost:3000)
   │
   ▼
Node.js + Express (host, port 3000)
   │
   ├─ Week 1: POST /v1/statement
   │  └── Trino 435 (port 8080)
   │       └── MySQL / ClickHouse / PostgreSQL
   │
   └─ Week 2: GET /api/w2/*
      └── Spring Boot data-service (port 8090)
          └── POST /cubejs-api/v1/load
              └── Cube.dev (port 4000)
                  └── Trino 435 (port 8080)
                      └── MySQL / ClickHouse / PostgreSQL
```

## 启动

**前置**: Docker 4 个核心容器（dfp-mysql / dfp-clickhouse / dfp-postgres / dfp-trino）已 healthy。
Dashboard 本身跑在 host Node.js 上，**不依赖 Docker**，所以 Docker 重启时 Dashboard 仍可访问（只是显示 Trino 不可达）。

```bash
cd E:/Agentic/DataService-fabric/poc/dashboard
npm install        # 仅 express 一个依赖
npm start          # 默认端口 3000
```

浏览器访问: **http://localhost:3000**

## 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `PORT` | `3000` | Dashboard 端口 |
| `TRINO_URL` | `http://localhost:8080` | Trino 协调器 URL |
| `TRINO_USER` | `dashboard` | Trino 提交用户名（用于审计） |
| `TRINO_SOURCE` | `datafabric-dashboard` | Trino 来源标签 |
| `DATA_SERVICE_URL` | `http://localhost:8090` | Spring Boot 数据服务 URL（Week 2） |
| `CUBE_URL` | `http://localhost:4000` | Cube.dev 语义层 URL（Week 2） |
| `CUBEJS_API_SECRET` | `datafabric-poc-secret-2026` | Cube API 鉴权密钥 |

例：自定义 Trino 地址
```bash
TRINO_URL=http://192.168.1.10:8080 npm start
```

## 界面说明

### 顶部栏
- **品牌**: Data Fabric · Week 1 验证台
- **健康指示**: Trino 版本 + 环境（绿色 = 在线，红色 = 不可达）
- **重跑全部按钮**: 顺序执行 V1-V5，刷新所有结果
- **上次执行时间**: 重跑完成后显示

### 左侧（侧栏）
- 5 个用例卡片，每张显示：用例名 / 状态徽章 / 数据源彩色点
- 卡片左侧色条颜色随状态变化（绿=pass / 红=fail / 灰=pending / 黄=running）
- 底部图例

### 右侧（详情区）
- **顶部**: 用例标题 + 描述 + 元数据（耗时 / 状态 / 行数 / 扫描字节）
- **数据源 chips**: 蓝(mysql) / 黄(clickhouse) / 蓝(postgres)
- **图表**: 按用例可视化类型渲染
  - V1 雷达图: 客户 6 维画像（订单数 / 消费金额 / 风险分）
  - V2 横向柱状图: VIP3 Top10 消费金额
  - V3 环形图: 高风险客户区域分布
  - V4 分组柱状图: 渠道 × 等级 客单价矩阵
  - V5 文本框: EXPLAIN 分布式执行计划
- **数据表**: 全量结果，支持横向滚动 + 鼠标悬停 tooltip
- **SQL 折叠块**: 显示原始 SQL，可折叠

### 底部栏
- Trino 版本
- 累计扫描行数
- 累计查询耗时（ms）
- 实时时钟

## 完成度判定逻辑

每个用例的 `expect` 字段定义期望：
- `rowCountEquals: N` — 行数必须正好等于 N
- `minRows: N` / `maxRows: N` — 行数区间

后端 `evaluatePassFail()` 在 `/api/query/:id` 响应里附加 `verdict`:
```json
{ "verdict": { "status": "pass" | "fail", "reason": "..." } }
```

前端用此 verdict 决定卡片色条和徽章颜色。

## 故障排除

### Q: 所有卡片显示 "FAIL: Trino 不可达"
A: 检查 Trino 容器: `docker ps | grep trino`。若不在线，重启: `docker-compose restart trino`

### Q: V1-V4 通过，V5 (EXPLAIN) 失败
A: 这是 EXPLAIN 输出过大触发 Trino 输出限制的罕见情况。后端 `lib/trino-client.js` 已处理分页轮询。

### Q: 图表加载但没数据
A: 查 Network 面板看 `/api/query/:id` 响应。常见原因：`clickhouse.map-string-as-varchar=true` 未配置导致类型不匹配（已修复，见 `trino-conf/clickhouse.properties`）。

### Q: 中文乱码
A: 浏览器默认 UTF-8 显示正常；只有从 Trino CLI 直查时 Windows Git Bash 才会乱码（数据本身正确）。

## 添加新用例

编辑 `queries/index.js`，追加一个对象：
```js
{
  id: 'v6',
  name: 'V6 新场景',
  description: '...',
  sources: ['mysql', 'clickhouse'],
  visualization: { type: 'bar', title: '...' },  // 支持: radar/bar/pie/heatmap/text
  expect: { minRows: 1 },
  sql: `SELECT ...`,
}
```

无需重启，刷新浏览器即可（注意：用 ES module export，需 `npm run dev` 自动重载后端）。
