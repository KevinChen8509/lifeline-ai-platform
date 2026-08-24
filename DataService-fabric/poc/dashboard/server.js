import express from 'express';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { executeQuery, healthCheck } from './lib/trino-client.js';
import { QUERIES } from './queries/index.js';
import {
  healthAll,
  fetchProfile,
  fetchMetrics,
  fetchCustomers,
  fetchAudit,
  fetchMaskingComparison,
  askAgent,
  fetchTrace,
  fetchUsage,
  fetchToolCalls,
  fetchPools,
  pipeAgentStream,
} from './lib/data-service-client.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PORT = process.env.PORT || 3000;
const app = express();

app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

/** GET /api/health - Trino + 数据源可达性 */
app.get('/api/health', async (req, res) => {
  const trino = await healthCheck();
  res.json({
    trino,
    queriesCount: QUERIES.length,
    serverTime: new Date().toISOString(),
  });
});

/** GET /api/queries - 列出所有验证查询定义（前端渲染卡片用） */
app.get('/api/queries', (req, res) => {
  res.json(QUERIES.map((q) => ({ ...q, sql: undefined })));
});

/** GET /api/query/:id - 执行单个验证查询，返回 columns/rows/stats */
app.get('/api/query/:id', async (req, res) => {
  const q = QUERIES.find((x) => x.id === req.params.id);
  if (!q) return res.status(404).json({ error: `Unknown query id: ${req.params.id}` });
  try {
    const result = await executeQuery(q.sql);
    const verdict = evaluatePassFail(q, result);
    res.json({ ...result, verdict, id: q.id, name: q.name });
  } catch (err) {
    res.status(200).json({
      id: q.id,
      name: q.name,
      error: err.message,
      stats: { state: 'FAILED' },
      verdict: { status: 'fail', reason: err.message },
    });
  }
});

/** POST /api/run-all - 顺序执行所有查询（前端并发会冲击 Trino） */
app.post('/api/run-all', async (req, res) => {
  const results = [];
  for (const q of QUERIES) {
    try {
      const result = await executeQuery(q.sql);
      const verdict = evaluatePassFail(q, result);
      results.push({ ...result, verdict, id: q.id, name: q.name });
    } catch (err) {
      results.push({
        id: q.id,
        name: q.name,
        error: err.message,
        stats: { state: 'FAILED' },
        verdict: { status: 'fail', reason: err.message },
      });
    }
  }
  res.json({ results, totalElapsed: results.reduce((s, r) => s + (r.stats?.elapsedMs || 0), 0) });
});

function evaluatePassFail(query, result) {
  const { rows } = result;
  const { expect } = query;
  if (!expect) return { status: 'pass', reason: '无期望校验' };
  const reasons = [];
  let ok = true;
  if (expect.minRows != null && rows.length < expect.minRows) {
    ok = false;
    reasons.push(`行数 ${rows.length} < 期望最小 ${expect.minRows}`);
  }
  if (expect.maxRows != null && rows.length > expect.maxRows) {
    ok = false;
    reasons.push(`行数 ${rows.length} > 期望最大 ${expect.maxRows}`);
  }
  if (expect.rowCountEquals != null && rows.length !== expect.rowCountEquals) {
    ok = false;
    reasons.push(`行数 ${rows.length} ≠ 期望 ${expect.rowCountEquals}`);
  }
  return {
    status: ok ? 'pass' : 'fail',
    reason: ok ? '通过' : reasons.join('; '),
  };
}

// ============================================================
// Week 2 路由：数据服务 + 治理可视化
// ============================================================

/** GET /api/w2/health — 全栈健康（Trino + Cube + SpringBoot） */
app.get('/api/w2/health', async (req, res) => {
  const result = await healthAll();
  res.json(result);
});

/** GET /api/w2/profile?custId=C0001&role=ADMIN&view=full — 单客户画像 */
app.get('/api/w2/profile', async (req, res) => {
  const { custId = 'C0001', role = '', view = 'full' } = req.query;
  const result = await fetchProfile(custId, { role, view });
  res.status(result.http >= 200 && result.http < 500 ? 200 : 502).json(result);
});

/** GET /api/w2/customers?level=VIP3&page=0&size=10 — 客户分群 */
app.get('/api/w2/customers', async (req, res) => {
  const { level, page = '0', size = '10' } = req.query;
  const result = await fetchCustomers({ level, page: +page, size: +size });
  res.json(result);
});

/** GET /api/w2/metrics — 全局指标快照 */
app.get('/api/w2/metrics', async (req, res) => {
  const result = await fetchMetrics();
  res.json(result);
});

/** GET /api/w2/audit?n=20 — 审计日志 */
app.get('/api/w2/audit', async (req, res) => {
  const { n = '20' } = req.query;
  const result = await fetchAudit(+n);
  res.json(result);
});

/** GET /api/w2/masking-compare?custId=C0001&view=full — 3 角色并排对比 */
app.get('/api/w2/masking-compare', async (req, res) => {
  const { custId = 'C0001', view = 'full' } = req.query;
  const result = await fetchMaskingComparison(custId, view);
  res.json({ custId, view, comparisons: result });
});

// ============================================================
// Week 3 路由：AI Agent 对比（A 路治理 vs B 路直查）
// ============================================================

/** POST /api/w3/ask-fabric { question } → A 路：CustomerInsightAgent（走 /api/v1/* + 治理） */
app.post('/api/w3/ask-fabric', async (req, res) => {
  const question = req.body?.question;
  if (!question || !question.trim()) {
    return res.status(400).json({ error: 'BAD_REQUEST', message: 'question 不能为空' });
  }
  const result = await askAgent('/api/v1/agent/insight', question);
  res.status(result.http >= 200 && result.http < 500 ? 200 : 502).json(result);
});

/** POST /api/w3/ask-raw { question } → B 路：RawDbAgent（直接 JDBC，无治理） */
app.post('/api/w3/ask-raw', async (req, res) => {
  const question = req.body?.question;
  if (!question || !question.trim()) {
    return res.status(400).json({ error: 'BAD_REQUEST', message: 'question 不能为空' });
  }
  const result = await askAgent('/api/v1/agent/raw', question);
  res.status(result.http >= 200 && result.http < 500 ? 200 : 502).json(result);
});

// ============================================================
// Week 4 路由：Agent 可观测台（F2 / F4 / F5 / F6 / F8 / F9）
// ============================================================

/** POST /api/w4/stream/:route { question } — F5 SSE 流式透传（route = insight | raw） */
app.post('/api/w4/stream/:route', async (req, res) => {
  const route = req.params.route === 'raw' ? 'raw' : 'insight';
  const question = req.body?.question;
  if (!question || !question.trim()) {
    return res.status(400).json({ error: 'BAD_REQUEST', message: 'question 不能为空' });
  }
  await pipeAgentStream(route, question, res);
});

/** GET /api/w4/trace/:requestId — F9 治理链路时间线（404 透传给前端显示"未找到"） */
app.get('/api/w4/trace/:requestId', async (req, res) => {
  const result = await fetchTrace(req.params.requestId);
  if (result.error) return res.status(502).json(result);
  res.status(result.http).json(result.data);
});

/** GET /api/w4/usage — F6 token 用量 + 成本估算 */
app.get('/api/w4/usage', async (req, res) => {
  const result = await fetchUsage();
  res.status(result.ok ? 200 : 502).json(result.ok ? result.data : result);
});

/** GET /api/w4/tool-calls?path=fabric|raw&limit=50 — F2 工具调用日志 */
app.get('/api/w4/tool-calls', async (req, res) => {
  const { path, limit } = req.query;
  const result = await fetchToolCalls({ path: path || undefined, limit: +limit || 50 });
  res.status(result.ok ? 200 : 502).json(result.ok ? result.data : result);
});

/** GET /api/w4/pools — F8 三源 HikariCP 连接池状态 */
app.get('/api/w4/pools', async (req, res) => {
  const result = await fetchPools();
  res.status(result.ok ? 200 : 502).json(result.ok ? result.data : result);
});

/**
 * POST /api/w4/redteam { payload } — F4 B 路注入演示：
 * 同步调 raw Agent，再取该 requestId 的工具调用日志作为"rows=0 铁证"。
 */
app.post('/api/w4/redteam', async (req, res) => {
  const payload = req.body?.payload;
  if (!payload || !payload.trim()) {
    return res.status(400).json({ error: 'BAD_REQUEST', message: 'payload 不能为空' });
  }
  const result = await askAgent('/api/v1/agent/raw', payload);
  const requestId = result.data?.requestId;
  let toolCalls = [];
  if (requestId) {
    const logs = await fetchToolCalls({ path: 'raw', limit: 50 });
    toolCalls = (logs.data?.recent || []).filter((c) => c.requestId === requestId);
  }
  res.json({ ...result, toolCalls });
});

app.listen(PORT, () => {
  console.log(`[Dashboard] http://localhost:${PORT}`);
  console.log(`[Trino]       ${process.env.TRINO_URL || 'http://localhost:8080'}`);
  console.log(`[Cube.dev]    ${process.env.CUBE_URL || 'http://localhost:4000'}`);
  console.log(`[DataService] ${process.env.DATA_SERVICE_URL || 'http://localhost:8090'}`);
});
