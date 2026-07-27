import express from 'express';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { executeQuery, healthCheck } from './lib/trino-client.js';
import { QUERIES } from './queries/index.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PORT = process.env.PORT || 3000;
const app = express();

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

app.listen(PORT, () => {
  console.log(`[Week1 Dashboard] http://localhost:${PORT}`);
  console.log(`[Trino] ${process.env.TRINO_URL || 'http://localhost:8080'}`);
});
