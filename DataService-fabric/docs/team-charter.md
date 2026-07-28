# Data Fabric 数据服务团队章程

> **版本**：v1.0 · **创建**：2026-07-20 · **基线**：DataGovePlatform-N 4-Phase 演进路线
> **关联文档**：[Data Fabric 演进路线](../../DataGovePlatform-N/docs/data-fabric-roadmap.md) · [DataGovePlatform-N 团队组织](../../DataGovePlatform-N/docs/team-organization.md)

---

## 0. 文档目的

为 **Data Fabric 数据服务能力建设**组建 5-7 人专项团队。本文档定义：
- 团队定位、组织架构、岗位设置
- 责任矩阵（RACI）与 4-Phase 演进配置
- 人机协作模式（真人 + AI Agent）
- KPI / OKR / 招聘建议 / 风险登记

**适用范围**：DataGovePlatform-N 数据服务能力（FR-21~24）扩展，覆盖 Gartner Data Fabric 六大能力的 25% → 80% 演进。

---

## 1. 团队定位与目标

### 1.1 使命陈述

> **"用 18 个月把数据从'可查'升级为'可订阅、可推理、可联邦'，使 Data Fabric 覆盖度从 25% 提升到 80%。"**

### 1.2 核心交付目标（四大支柱）

| # | 支柱 | 关键技术 | 对应 ADR | Phase | Data Fabric 覆盖度 |
|---|------|---------|---------|-------|-------------------|
| ① | **主动元数据 + 血缘** | OpenMetadata v1.12+ + ClickHouse 连接器 + Flink OpenLineage | ADR-021（待立） | Phase 2 | 25% → 50% |
| ② | **语义层 + 指标 API** | Cube.dev + 10-20 业务指标 + REST/GraphQL | ADR-022（待立） | Phase 3 | 50% → 65% |
| ③ | **联邦查询层** | StarRocks v3.3+ External Catalog + 跨源 JOIN | ADR-023（待立） | Phase 4 | 65% → 80% |
| ④ | **数据服务网关** | `/api/v1/*` 双轨 + Pact + Bearer + 脱敏 + Marketplace 雏形 | ADR-008（已有）扩展 | 全 Phase | 贯穿增强 |

### 1.3 团队边界

**本团队负责**：
- 元数据 / 血缘 / 语义层 / 联邦查询 / 数据服务 API 的设计、开发、运维
- 与 Dinky / Flink / ClickHouse / SeaTunnel 的集成（消费方）
- 数据服务相关 ADR 的提案与执行

**本团队不负责**（由 DataGovePlatform-N 主团队承担）：
- 数据管道建设（Flink SQL / MV / SeaTunnel 作业）
- 平台部署与基础设施（Docker / K8s / 监控）
- 业务应用层（行业内核 / D-005 三件套）

**协作接口**：本团队对外提供 4 类 API 契约，主团队消费。

---

## 2. 组织架构

### 2.1 架构图

```
                 ┌─────────────────────────────────────┐
                 │   ① Data Fabric 架构师 / Tech Lead  │
                 │   架构决策 · ADR · 跨团队协调        │
                 └─────────────────┬───────────────────┘
                                   │
       ┌──────────────┬────────────┼────────────┬──────────────┐
       │              │            │            │              │
┌──────▼──────┐ ┌─────▼─────┐ ┌────▼────┐ ┌─────▼─────┐ ┌──────▼──────┐
│ ② 元数据 &  │ │ ③ 语义层  │ │ ④ 数据  │ │ ⑤ 联邦    │ │ ⑥ 治理 &    │
│   血缘工程  │ │   & 指标  │ │  服务 & │ │  查询 &   │ │   DevOps   │
│   师       │ │   工程师  │ │  API    │ │  OLAP     │ │   工程师   │
│ OpenMeta   │ │ Cube.dev  │ │ 工程师  │ │ 工程师    │ │ (可选 0-1) │
│ Data       │ │ Headless  │ │ Gateway │ │ StarRocks │ │ 质量/安全  │
│ ─────────  │ │ ───────── │ │ ─────── │ │ ───────── │ │ ─────────  │
│   1 人     │ │   1 人    │ │ 1-2 人  │ │   1 人    │ │   0-1 人   │
└──────┬─────┘ └─────┬─────┘ └────┬────┘ └─────┬─────┘ └──────┬──────┘
       │             │            │            │              │
       └─────────────┴────────────┼────────────┴──────────────┘
                                   │
                    ┌──────────────▼──────────────┐
                    │   AI Agent 增强（虚拟成员）   │
                    │  BMAD 6 + 通用 5，始终在线   │
                    └─────────────────────────────┘
```

### 2.2 配置规模与启动建议

| 阶段 | 真人数 | 必配岗位 | 可选岗位 | 启动周期 |
|------|--------|---------|---------|---------|
| **Phase 1**（v1 接口预留） | **5 人** | ①②③④ | ⑤⑥ | 0-30 天 |
| **Phase 2**（v1.1 OpenMetadata） | **6 人** | ①②③④⑤ | ⑥ | 30-90 天 |
| **Phase 3**（v1.2 Cube.dev） | **6-7 人** | ①②③④⑤ | ⑥ | 90-180 天 |
| **Phase 4**（v2 StarRocks） | **7 人** | ①②③④⑤⑥ | — | 180-360 天 |
| **Phase 5**（远期 KG + Marketplace） | **7 人**（角色轮转） | ①②③④⑤⑥ | — | 360+ 天 |

**最小可启动配置 = 5 人**（Phase 1）：①Tech Lead + ②元数据 + ③语义层 + ④数据服务（2 人）。

---

## 3. 核心岗位 JD

### 3.1 ① Data Fabric 架构师 / Tech Lead（1 人，必配）

**核心职责**：
- Data Fabric 4-Phase 整体架构演进规划与 ADR 撰写（ADR-021 ~ ADR-024）
- 跨模块技术决策（OpenMetadata vs 国产 DataOps、Cube vs dbt MetricFlow、StarRocks vs Trino）
- 关键代码审查（架构契约、SPI 边界、契约测试）
- 与 DataGovePlatform-N 主团队 Tech Lead 协调接口与版本
- 团队技术风险预警与 PoC 立项决策

**技能要求**：
- 8+ 年后端经验，3+ 年数据领域（数据中台 / 数据治理 / Data Fabric 任意一项）
- 精通 Java 17 + Spring Boot 3.x，熟悉 Node.js（Cube.dev 需要）
- 至少一项元数据平台实战（OpenMetadata / DataHub / Apache Atlas）
- 至少一项 OLAP 实战（ClickHouse / StarRocks / Doris / Presto）
- 熟悉 Gartner Data Fabric 六大能力定义与业界参考架构
- 优秀的架构文档撰写能力（中文 + 英文 ADR）

**KPI / OKR**：
- 季度产出 4+ 篇高质量 ADR（含决策矩阵 + PoC 数据 + 反对意见处理）
- 4-Phase 路线按期推进偏差 ≤ 15%
- 团队代码 Review 通过率 ≥ 85%
- 关键技术决策无重大回退（≤ 1 次/半年）

**招聘建议**：内部转岗优先（DataGovePlatform-N Tech Lead 暂不可抽调，可从主团队架构师中分化）；外部招聘需求数据中台 / 治理平台背景，薪酬段 P8 / T8 / 阿里 P8 同等。

---

### 3.2 ② 元数据 & 血缘工程师（1 人，必配）

**核心职责**：
- OpenMetadata v1.12+ 部署、配置、运维、升级
- ClickHouse / Dinky / Flink / Kafka 元数据自动采集连接器配置
- 血缘解析（ClickHouse View 依赖 + Flink OpenLineage + Dinky Catalogue 双源融合）
- 元数据 API 对外暴露（`/api/v1/catalog/*`）
- ADR-014（Schema Registry）升级到 ADR-021（OpenMetadata）的迁移执行
- 已知 bug 跟踪（Issue #17574 ClickHouse View schema prefix）

**技能要求**：
- 3+ 年后端经验，至少 1 年元数据 / 数据治理相关
- Java 或 Python 熟练（OpenMetadata 主要 Python + Java）
- 熟悉 OpenMetadata / DataHub / Amundsen 至少一项（OpenMetadata 优先）
- 熟悉 ClickHouse 系统表（`system.tables` / `system.parts` / `system.dictionaries`）
- 熟悉 OpenLineage 协议与 Marquez 集成
- 图数据库基础（Neo4j / JanusGraph，OpenMetadata 内部用 Neo4j）

**KPI / OKR**：
- 元数据采集覆盖率 Phase 2 末 ≥ 95%（按表 / 列 / 作业）
- 字段级血缘准确率 ≥ 90%
- 采集任务稳定性 ≥ 99.5%（月度 SLA）
- 元数据查询 P95 ≤ 500ms

**招聘建议**：外部招聘为主，DataOps / 数据治理背景优先；内部可由 DataGovePlatform-N 数据治理工程师兼任过渡。

---

### 3.3 ③ 语义层 & 指标工程师（1 人，必配）

**核心职责**：
- Cube.dev 部署、数据源连接（ClickHouse 主，StarRocks 辅）
- 10-20 个核心业务指标建模（FR-15~20 行业内核相关）
- Cube REST / GraphQL API 对外开放（`/api/v1/metrics/*`）
- 预聚合（Pre-aggregations）策略设计与刷新窗口优化
- 与 ADR-008 双轨 API 融合，复用 Bearer Token 鉴权链
- Headless BI 理念推广，对接前端 BI 工具（Superset / Metabase）

**技能要求**：
- 3+ 年数据 / BI 工程经验
- Node.js + TypeScript 熟练（Cube.dev 技术栈）
- 熟悉至少一项语义层（Cube.dev / dbt MetricFlow / LookML / AtScale）
- ClickHouse SQL 优化能力（预聚合命中率、Materialized View 配合）
- 指标定义方法论熟悉（Kimball / One-Metric-One-Definition）
- GraphQL schema 设计能力

**KPI / OKR**：
- 指标 API P95 延迟 ≤ 800ms（预聚合命中）/ ≤ 3s（实时计算）
- 预聚合命中率 ≥ 85%
- 指标定义一次正确率 ≥ 90%（业务方签字确认）
- 业务方接入数 ≥ 5 个（季度）

**招聘建议**：BI Engineer 转型最佳；具备 Headless BI 实践经验者薪酬可上浮 15%。

---

### 3.4 ④ 数据服务 & API 工程师（1-2 人，必配）

**核心职责**：
- `/api/v1/*` 数据服务网关开发与运维（基于 Spring Boot 3.x）
- 双轨路由（新建 + Dinky 透传）+ 白名单管理
- Bearer Token 认证 + 数据范围授权（绑分级标签）
- Pact 契约测试编写与 CI 集成
- 数据脱敏（动态 / 静态）+ 限流（Redis 令牌桶 60/min）
- 数据服务前端管控台（Vue 3 + TypeScript）

**技能要求**：
- 3+ 年 Java 后端，精通 Spring Boot 3.x + Spring Security 6
- 熟悉 API Gateway 模式（Spring Cloud Gateway / Kong / Apisix）
- Vue 3 + TypeScript + Vite 前端能力（管控台）
- 熟悉 Pact / Spring Cloud Contract 契约测试
- 熟悉 Redis 限流 / 缓存 / Pub-Sub
- OpenAPI 3.x 规范熟练，会生成多语言 SDK

**KPI / OKR**：
- API 可用性 ≥ 99.9%（月度 SLA）
- API P95 延迟：直查 ≤ 1s / 指标 ≤ 800ms / 联邦 ≤ 5s
- 契约测试覆盖率 ≥ 80%（关键端点）
- 安全漏洞 0 个（OWASP Top 10 自动扫描）

**配置策略**：
- **1 人版**（Phase 1）：后端为主，管控台复用 DataGovePlatform-N 主前端
- **2 人版**（Phase 3+）：1 后端 + 1 前端，前端负责数据服务管控台与 Marketplace UI

**招聘建议**：全栈优先；后端 + 前端各 1 的组合更稳，但需注意前后端协作默契。

---

### 3.5 ⑤ 联邦查询 & OLAP 工程师（1 人，Phase 2+ 必配）

**核心职责**：
- StarRocks v3.3+ 部署、External Catalog 配置（ClickHouse / MySQL / Hive / Iceberg）
- 跨源 JOIN 性能调优（谓词下推、本地物化、Colocate Join）
- ClickHouse 性能监控与慢查询分析（Phase 1 起即承担）
- 即席查询路由策略（直查 CH vs 联邦 StarRocks）
- ADR-023（联邦查询层）撰写与 PoC 执行

**技能要求**：
- 3+ 年 OLAP / 数据库内核或 DBA 经验
- 精通 ClickHouse（MergeTree 引擎 / 分区分桶 / 物化视图 / TTL）
- 至少一项 MPP 数据库实战（StarRocks / Doris / Greenplum）
- 熟悉 External Catalog 模式（StarRocks / Trino / Presto）
- SQL 调优能力（执行计划解读、索引、Colocate Join、Runtime Filter）
- Linux + 监控栈（Prometheus + Grafana）

**KPI / OKR**：
- ClickHouse 查询 P95 ≤ 1s（Phase 1 SLA）
- StarRocks 联邦查询 P95 ≤ 5s（Phase 4 SLA）
- 谓词下推命中率 ≥ 90%
- 慢查询工单响应 ≤ 4 小时

**招聘建议**：DBA 转型最佳；ClickHouse 中文社区活跃成员优先。

---

### 3.6 ⑥ 数据治理 & DevOps 工程师（0-1 人，可选）

**核心职责**：
- 数据质量规则设计与执行（基于 DataGovePlatform-N Phase 3 DQC 扩展）
- 数据分级分类标签同步（ADR-013 ↔ OpenMetadata）
- 审计日志（哈希链，ADR-016）运维
- CI/CD 流水线维护（Jenkins + GitLab CI，ADR-019）
- 信创合规检查（ADR-017 SPI 边界）

**技能要求**：
- 3+ 年 DevOps 或数据治理经验
- Jenkins + GitLab CI + Docker + K8s 熟练
- 熟悉 DAMA-DMBOK 数据治理理论
- 至少一项数据质量工具实战（Great Expectations / Soda / dbt tests）
- 信创合规知识（等保 / 国密 / 鲲鹏 / 飞腾）

**KPI / OKR**：
- CI/CD 流水线可用性 ≥ 99%
- 数据质量规则覆盖率 ≥ 80%
- 信创合规扫描无 P0/P1 问题
- 审计哈希链完整性 100%

**配置策略**：
- **Phase 1-2 不配**：由 ①Tech Lead 兼任 + DataGovePlatform-N 主团队支撑
- **Phase 3+ 必配 0.5 FTE**：与主团队治理工程师共同承担
- **Phase 4+ 1 FTE**：独立配置

---

## 4. AI Agent 增强（虚拟成员）

### 4.1 BMAD 6 个核心 agents（已配置）

| 代号 | 名称 | 角色 | 在本团队的典型场景 |
|------|------|------|---------------------|
| 📋 John | `bmad-agent-pm` | 产品经理 | 数据服务需求挖掘、API 优先级排序、用户访谈 |
| 📊 Mary | `bmad-agent-analyst` | 业务分析师 | Data Fabric 竞品调研、行业指标对标、证据梳理 |
| 🏗️ Winston | `bmad-agent-architect` | 系统架构师 | ADR-021~024 撰写、4-Phase 路线评审 |
| 🎨 Sally | `bmad-agent-ux-designer` | UX 设计师 | 数据服务管控台、Marketplace UI、开发者门户 |
| 💻 Amelia | `bmad-agent-dev` | 高级工程师 | Story 实现、TDD、Cube/OpenMetadata 集成代码 |
| 📚 Paige | `bmad-agent-tech-writer` | 技术写作 | API 文档、指标字典、数据服务开发者手册 |

### 4.2 通用增强 agents

| Agent | 触发场景 | 在本团队的输出 |
|-------|----------|---------------|
| `planner` | OpenMetadata / Cube.dev 引入前 | 迁移计划、回滚预案 |
| `architect` | ADR 决策点 | 多方案对比（OpenMetadata vs DataHub vs Atlas） |
| `code-reviewer` | 每次代码合并前 | 质量评分 + Spring Boot/Node.js 改进清单 |
| `security-reviewer` | API 端点、Bearer 鉴权、脱敏代码 | OWASP Top 10 + 数据泄露扫描 |
| `tdd-guide` | 新指标、新端点、新 Adapter | 测试先行（Pact 契约 + JUnit + Vitest） |

### 4.3 人机协作模式

**真人主导（决策 / 创造 / 沟通）**：
- 架构决策（A 类工作）：撰写 ADR、客户对接、跨团队协调
- 业务建模（B 类工作）：指标定义、元数据模型设计
- 代码审查最终决定权

**AI 主导（执行 / 检索 / 验证）**：
- 文档草稿（C 类工作）：PRD / ADR / API 文档 / 用户手册
- 测试生成：Pact 契约、单元测试、E2E 用例
- 代码审查：第一轮扫描，CRITICAL/HIGH 标注
- 知识检索：业界 Data Fabric 实践、组件 issue 跟踪

**协作准则**：
- 每个真人岗每天至少与 1 个 AI agent 协作（输入 / 输出可追溯）
- AI 输出必须真人 Review 后才能进入主干
- ADR / 关键设计必须由 `bmad-agent-architect` Winston 评审
- 安全敏感代码必须由 `security-reviewer` 扫描
- 用户文档必须由 Paige (`bmad-agent-tech-writer`) 终审

---

## 5. RACI 责任矩阵

> **R** = Responsible 执行 / **A** = Accountable 最终负责 / **C** = Consulted 咨询 / **I** = Informed 知会

### 5.1 按交付目标

| 交付目标 / 任务 | ① Tech Lead | ② 元数据 | ③ 语义层 | ④ 数据服务 | ⑤ 联邦查询 | ⑥ 治理 |
|---|---|---|---|---|---|---|
| **Phase 1：v1 接口预留** | | | | | | |
| ADR-008 扩展（接口预留） | **A** | C | C | **R** | I | I |
| `/api/v1/catalog/external` stub | I | **R** | I | **A** | I | I |
| `/api/v1/metrics/*` stub | I | I | **R** | **A** | I | I |
| Adapter 层抽象扩展 | **A** | C | C | **R** | C | I |
| **Phase 2：OpenMetadata** | | | | | | |
| ADR-021 撰写 | **A/R** | C | I | C | C | C |
| OpenMetadata 部署 | I | **A/R** | I | C | C | C |
| ClickHouse 连接器 | I | **A/R** | I | I | C | I |
| Flink OpenLineage 集成 | C | **A/R** | I | I | I | C |
| Dinky Catalogue 双源融合 | C | **A/R** | I | C | I | I |
| `/api/v1/catalog/*` 对外 | C | **R** | I | **A** | I | I |
| **Phase 3：Cube.dev** | | | | | | |
| ADR-022 撰写 | **A/R** | I | C | C | C | I |
| Cube.dev 部署 | I | I | **A/R** | C | C | I |
| 10-20 指标建模 | C | C | **A/R** | I | I | I |
| `/api/v1/metrics/*` 对外 | I | I | **R** | **A** | I | I |
| 预聚合策略 | C | I | **A/R** | I | C | I |
| **Phase 4：StarRocks** | | | | | | |
| ADR-023 撰写 | **A/R** | I | C | C | C | I |
| StarRocks 部署 | I | I | I | I | **A/R** | C |
| External Catalog 配置 | I | C | I | I | **A/R** | I |
| 跨源 JOIN 调优 | C | I | C | I | **A/R** | I |
| ClickHouse 慢查询优化 | I | I | I | I | **A/R** | I |
| **跨 Phase：数据服务网关** | | | | | | |
| Pact 契约测试 | I | C | C | **A/R** | C | I |
| Bearer + 数据范围授权 | C | I | I | **A/R** | I | C |
| 动态脱敏 | C | C | I | **A/R** | I | C |
| 管控台 Vue 前端 | I | C | C | **A/R** | I | I |
| Marketplace 雏形 | **A** | **R** | **R** | **R** | I | C |
| **横切：治理 / 安全** | | | | | | |
| 数据分级标签同步 | I | C | I | I | I | **A/R** |
| 审计哈希链 | I | I | I | C | I | **A/R** |
| 信创合规扫描 | **A** | I | I | C | I | **R** |
| CI/CD 流水线 | I | I | I | C | I | **A/R** |

### 5.2 关键决策授权

| 决策类型 | 授权人 | 升级路径 |
|---------|--------|---------|
| 单 Story 内技术选型 | 各岗位自己 | → Tech Lead |
| 模块级技术选型 | ① Tech Lead | → DataGovePlatform-N 架构委员会 |
| 跨 ADR 决策 | ① Tech Lead + Winston agent | → 客户 / 项目发起人 |
| 引入新依赖（>1MB / 商业组件） | ① Tech Lead 批准 | → 安全审查 |
| 紧急回滚 / Hotfix | ④ 数据服务工程师 | → Tech Lead 复核 |

---

## 6. 4-Phase 人员配置演进

### 6.1 Phase 1：v1 接口预留（Day 0-30，5 人）

**目标**：完成 Epic 7 五个 Story + Fabric 接入点预留。

| 岗位 | 人数 | 主任务 | 副任务 |
|------|------|--------|--------|
| ① Tech Lead | 1 | ADR-008 扩展、4-Phase 路线对齐 | — |
| ② 元数据 | 1 | OpenMetadata PoC 调研、`/api/v1/catalog/external` stub | ClickHouse 元数据盘点 |
| ③ 语义层 | 1 | Cube.dev PoC 调研、`/api/v1/metrics/*` stub | 业务指标清单整理 |
| ④ 数据服务 | 2 | Epic 7 主力（API + 前端） | Pact 契约骨架 |
| ⑤ 联邦查询 | 0（兼任） | — | ClickHouse 现状监控 |
| ⑥ 治理 | 0（兼任） | — | — |

**关键产出**：4-Phase 路线确认、ADR-008 扩展完成、3 个接口 stub 上线、PoC 立项文档 ×3。

---

### 6.2 Phase 2：v1.1 OpenMetadata（Day 30-120，6 人）

**目标**：Data Fabric 覆盖度 25% → 50%。

**新增岗位**：⑤ 联邦查询工程师（提前到岗，先承担 ClickHouse 调优 + StarRocks PoC 准备）

| 岗位 | 人数 | 主任务 |
|------|------|--------|
| ① Tech Lead | 1 | ADR-021 撰写、跨团队协调 |
| ② 元数据 | 1 | **OpenMetadata 主力**（部署 + 连接器 + 血缘 + API） |
| ③ 语义层 | 1 | 业务指标建模准备、Cube.dev 单机 PoC |
| ④ 数据服务 | 2 | `/api/v1/catalog/*` 实现 + 管控台集成 |
| ⑤ 联邦查询 | 1 | ClickHouse 性能基线、StarRocks PoC 立项 |
| ⑥ 治理 | 0（仍兼任） | — |

**关键产出**：OpenMetadata 上线、字段级血缘准确率 ≥ 90%、ADR-021 通过、StarRocks PoC 立项。

---

### 6.3 Phase 3：v1.2 Cube.dev（Day 120-240，6-7 人）

**目标**：Data Fabric 覆盖度 50% → 65%。

**新增 / 转型**：
- ③ 语义层工程师全力投入 Cube.dev
- ④ 数据服务工程师 2 人版（1 后端 + 1 前端）
- ⑥ 治理工程师 0.5 FTE 启动（与主团队共担）

| 岗位 | 人数 | 主任务 |
|------|------|--------|
| ① Tech Lead | 1 | ADR-022 撰写、指标治理流程建立 |
| ② 元数据 | 1 | OpenMetadata 与 Cube.dev 元数据互通 |
| ③ 语义层 | 1 | **Cube.dev 主力**（部署 + 10-20 指标 + API） |
| ④ 数据服务 | 2 | `/api/v1/metrics/*` + 前端管控台 |
| ⑤ 联邦查询 | 1 | ClickHouse 持续优化、StarRocks PoC 执行 |
| ⑥ 治理 | 0.5 | 数据质量规则 + 标签同步 |

**关键产出**：Cube.dev 上线、首批 15 个业务指标、ADR-022 通过、Marketplace PoC。

---

### 6.4 Phase 4：v2 StarRocks（Day 240-360，7 人）

**目标**：Data Fabric 覆盖度 65% → 80%。

**满配**：6 个岗位全部到齐。

| 岗位 | 人数 | 主任务 |
|------|------|--------|
| ① Tech Lead | 1 | ADR-023 撰写、整体路线收口 |
| ② 元数据 | 1 | OpenMetadata 治理 StarRocks Catalog |
| ③ 语义层 | 1 | Cube.dev 接 StarRocks 数据源 |
| ④ 数据服务 | 2 | 联邦查询对外 API + Marketplace 上线 |
| ⑤ 联邦查询 | 1 | **StarRocks 主力**（部署 + External Catalog + 调优） |
| ⑥ 治理 | 1 | 数据服务 SLA 监控 + 信创合规扫描 |

**关键产出**：StarRocks 上线、跨源 JOIN P95 ≤ 5s、ADR-023 通过、Marketplace GA。

---

## 7. 人机协作工作流（举例）

### 7.1 引入 OpenMetadata 的协作流（Phase 2 起步）

```
① Tech Lead（人）
  ├─ [输入] 4-Phase 路线 + 风险登记
  ├─ [AI] Winston (bmad-agent-architect) → ADR-021 草稿
  ├─ [AI] planner agent → 迁移计划（Dinky Catalogue 双源）
  ├─ [人] Review + 决策会议
  └─ [输出] ADR-021 v1
        │
        ▼
② 元数据工程师（人）
  ├─ [输入] ADR-021
  ├─ [AI] architect agent → 连接器配置方案（3 选 1）
  ├─ [AI] Amelia (bmad-agent-dev) → 连接器代码骨架
  ├─ [AI] tdd-guide → 连接器单元测试
  ├─ [人] 实施 + 集成测试
  ├─ [AI] code-reviewer → 代码评审
  ├─ [AI] security-reviewer → 权限扫描
  └─ [输出] OpenMetadata ClickHouse 连接器 GA
        │
        ▼
④ 数据服务工程师（人）
  ├─ [输入] OpenMetadata API
  ├─ [AI] Amelia → `/api/v1/catalog/*` Adapter 骨架
  ├─ [AI] tdd-guide → Pact 契约测试
  ├─ [人] 实施 + 前端集成
  └─ [输出] 数据服务网关 `/api/v1/catalog/*` 上线
        │
        ▼
⑥ 治理工程师 / ① Tech Lead
  ├─ [AI] Paige (bmad-agent-tech-writer) → API 文档 + 用户手册
  └─ [人] 发布
```

### 7.2 关键时刻强制 AI 介入点

| 场景 | 强制 agent | 理由 |
|------|-----------|------|
| 任何 ADR 草稿 | Winston (architect) | 多视角架构评审 |
| API 端点开发 | security-reviewer | OWASP 扫描 |
| 上线前代码合并 | code-reviewer | 质量门禁 |
| 客户文档 | Paige (tech-writer) | 一致性与可读性 |
| 指标定义 | Mary (analyst) | 业务对齐 |

---

## 8. KPI / OKR 体系

### 8.1 团队级 OKR（季度）

**O1：Phase N 按期交付（按当前 Phase）**
- KR1：路线图关键节点按期达成 ≥ 90%
- KR2：ADR 评审通过率 ≥ 80%
- KR3：PoC 数据支撑决策 ≥ 3 项/季度

**O2：数据服务质量**
- KR1：API 月度 SLA ≥ 99.9%
- KR2：契约测试覆盖率 ≥ 80%
- KR3：安全 P0/P1 漏洞 = 0

**O3：Data Fabric 能力演进**
- KR1：当前 Phase 覆盖度目标达成（如 Phase 2 末 50%）
- KR2：关键组件（OpenMetadata / Cube / StarRocks）生产可用
- KR3：业务方接入数 ≥ 季度目标

**O4：团队效能**
- KR1：AI Agent 协作任务占比 ≥ 40%
- KR2：人均 Story 产出 ≥ 6 / 季度
- KR3：团队代码 Review 通过率 ≥ 85%

### 8.2 个人级 KPI（参考第 3 节各岗位 KPI）

每季度由 ① Tech Lead 与个人共同制定 3-5 个 KR，对齐团队 OKR。

---

## 9. 招聘与培养建议

### 9.1 招聘优先级与时序

| 优先级 | 岗位 | Phase | 来源 | 时效 |
|--------|------|-------|------|------|
| P0 | ① Tech Lead | Phase 1 | 内部转岗 / 外部 P8 | Day -15 ~ 0 |
| P0 | ④ 数据服务工程师 ×2 | Phase 1 | 外部招聘 | Day 0 ~ 30 |
| P1 | ② 元数据工程师 | Phase 1 | 外部招聘 | Day 0 ~ 30 |
| P1 | ③ 语义层工程师 | Phase 1 | 内部 BI 转型 / 外部 | Day 0 ~ 30 |
| P2 | ⑤ 联邦查询工程师 | Phase 2 | 外部 DBA 转型 | Day 30 ~ 60 |
| P3 | ⑥ 治理工程师 | Phase 3 | 内部转岗 0.5 → 外部 1.0 | Day 120 ~ 180 |

### 9.2 技能培养路径

| 岗位 | 必修课程 / 认证 | 推荐资源 |
|------|----------------|---------|
| ① Tech Lead | Gartner Data Fabric Reference Architecture、TOGAF | Gartner 报告 + The Open Group |
| ② 元数据 | OpenMetadata Official Course、Apache Atlas 文档 | OpenMetadata Academy、Linux Foundation |
| ③ 语义层 | Cube.dev Certification、Kimball 指标方法论 | Cube.dev 官方、Kimball Group |
| ④ 数据服务 | Spring Certified Professional、OWASP API Security | VMware、OWASP |
| ⑤ 联邦查询 | StarRocks Certified Engineer、ClickHouse 初级 + 中级 | 镜舟科技、ClickHouse 官方 |
| ⑥ 治理 | CDMP（DAMA）、CKA（K8s） | DAMA International、CNCF |

### 9.3 内部转岗路径（与 DataGovePlatform-N 主团队）

| 主团队岗位 | 可转本团队岗位 | 迁移成本 | 推荐时机 |
|-----------|---------------|---------|---------|
| 全栈工程师 | ④ 数据服务工程师 | 低 | Phase 1 |
| 数据治理工程师 | ② 元数据 / ⑥ 治理 | 低 | Phase 1-2 |
| Flink/CH 工程师 | ⑤ 联邦查询 | 中（补 StarRocks） | Phase 2-3 |
| Tech Lead | ① Tech Lead | 低 | 立即（兼任过渡） |

---

## 10. 风险登记与缓解

| # | 风险 | 等级 | 影响 | 缓解措施 | 责任人 |
|---|------|------|------|---------|--------|
| R-TEAM-01 | ① Tech Lead 招聘周期长（市场稀缺） | 🔴 高 | Phase 1 延期 | 内部转岗优先 + 顾问过渡 | 项目发起人 |
| R-TEAM-02 | ② 元数据工程师 OpenMetadata 经验稀缺 | 🟡 中 | Phase 2 延期 | 接受 DataHub/Atlas 经验 + 3 周学习曲线 | ① Tech Lead |
| R-TEAM-03 | ③ 语义层 + Cube.dev 国内案例少 | 🟡 中 | Phase 3 指标建模慢 | 与 Cube.dev 官方技术支持合作 | ① Tech Lead |
| R-TEAM-04 | ⑤ 联邦查询 ClickHouse JOIN 性能 | 🟡 中 | Phase 4 SLA 风险 | 谓词下推 + 本地物化；监控 P95（R-FAB-01） | ⑤ 联邦查询 |
| R-TEAM-05 | 跨团队接口冲突（主团队 vs 本团队） | 🟡 中 | 协作摩擦 | 双 Tech Lead 双周会 + ADR 联签机制 | ① Tech Lead |
| R-TEAM-06 | AI Agent 输出误用（未 Review 进主干） | 🟡 中 | 代码质量回退 | CI 强制门禁：agent 输出必须有真人 Approve | ① Tech Lead |
| R-TEAM-07 | 信创合规组件替代（如 OpenMetadata 替换为国产） | 🟢 低 | Phase 2+ 重构 | SPI 抽象 + 国产平台双轨 PoC（ADR-017） | ① Tech Lead + ⑥ 治理 |
| R-TEAM-08 | 关键人员离职（单点知识） | 🔴 高 | 知识断层 | ADR 强制文档化 + 结对编程 + AI 知识沉淀 | ① Tech Lead |

---

## 11. 启动检查清单（Day 0-7）

### 11.1 团队启动会（Day 0）

- [ ] 4-Phase 路线对齐（继承 DataGovePlatform-N data-fabric-roadmap.md）
- [ ] 本章程签字确认（项目发起人 + ① Tech Lead）
- [ ] RACI 矩阵全员共识
- [ ] AI Agent 工具链就绪（BMAD + 通用 agents 在 `_bmad/config.toml` 配置）
- [ ] 协作工具：Jira / Confluence / GitLab / Figma / 飞书

### 11.2 第一周任务（Day 1-7）

- [ ] ① Tech Lead 撰写 ADR-008 扩展草案
- [ ] ② 元数据完成 OpenMetadata v1.12 单机 PoC
- [ ] ③ 语义层完成 Cube.dev 单机 PoC + 业务指标清单 v0
- [ ] ④ 数据服务完成 Epic 7 五个 Story 拆分 + Pact 契约骨架
- [ ] 全员完成 DataGovePlatform-N architecture.md / epics.md 通读
- [ ] 启动会议纪要、Phase 1 Sprint 1 Backlog

### 11.3 第一月里程碑（Day 30）

- [ ] Phase 1 五人团队就位
- [ ] ADR-008 扩展完成、3 个接口 stub 上线
- [ ] OpenMetadata + Cube.dev PoC 报告（含性能基线）
- [ ] Phase 2 启动决策（Go / No-Go）

---

## 12. 附录

### 12.1 与 DataGovePlatform-N 主团队的关系

```
┌─────────────────────────────────────────────────────┐
│            DataGovePlatform-N 项目级治理             │
│   架构委员会 · 联合 Tech Lead 会 · 联合 ADR 联签     │
└──────────────┬──────────────────┬───────────────────┘
               │                  │
   ┌───────────▼──────┐   ┌───────▼───────────────┐
   │   主团队（5人）   │   │  本团队（5-7人）       │
   │  数据管道 + 平台  │←→│  Data Fabric 数据服务  │
   │  + 业务内核       │   │  元数据/语义/联邦/API  │
   └──────────────────┘   └───────────────────────┘
```

**接口契约**：
- 主团队 → 本团队：数据管道元数据（Flink OpenLineage）、CH 表结构、Dinky Catalogue
- 本团队 → 主团队：数据服务 API（FR-21~24）、指标 API、联邦查询能力

### 12.2 关键术语表

| 术语 | 定义 |
|------|------|
| **Data Fabric** | Gartner 定义的数据集成架构，六大能力：主动元数据、语义层、虚拟化、自动编目、知识图谱、AI 增强 |
| **Headless BI** | 语义层独立于可视化工具，API 优先，Cube.dev 是典型代表 |
| **OpenLineage** | 开放血缘标准，Flink / Airflow / Spark 原生支持 |
| **Pact 契约测试** | 消费者驱动的契约测试（CDC），保证 API 兼容性 |
| **External Catalog** | StarRocks 跨源查询机制，通过元数据映射访问外部数据源 |
| **预聚合** | Cube.dev 提前计算并物化指标，运行时直接命中加速 |

### 12.3 参考文档

- [Data Fabric 演进路线](../../DataGovePlatform-N/docs/data-fabric-roadmap.md)
- [DataGovePlatform-N 团队组织](../../DataGovePlatform-N/docs/team-organization.md)
- [DataGovePlatform-N 架构文档](../../DataGovePlatform-N/_bmad-output/planning-artifacts/architecture/architecture-DataGovePlatform-N-2026-06-18/architecture.md)
- [DataGovePlatform-N Epic/Story](../../DataGovePlatform-N/_bmad-output/planning-artifacts/epics.md)
- Gartner: *A Data Fabric Is the Future of Data Management* (2024)
- OpenMetadata 官方文档 https://docs.open-metadata.org/
- Cube.dev 官方文档 https://cube.dev/docs
- StarRocks 官方文档 https://docs.starrocks.io/

---

**文档维护**：① Tech Lead（主） · ⑥ 治理工程师（协）
**评审周期**：每 Phase 末 + 季度 OKR 评审同步更新
**下次评审**：Phase 2 启动前（Day 30）
