// Week 2 数据服务客户端
// 调用：
//   - Spring Boot data-service :8090  （主要 API）
//   - Cube.dev :4000              （可选，做 schema 可视化）
//   - Trino :8080                  （Week 1 已有，这里复用 health）
//
// 所有调用都容错：服务未起时返回 { status: 'down', error: '...' }，不抛异常。

const DATA_SERVICE_URL = process.env.DATA_SERVICE_URL || 'http://localhost:8090';
const CUBE_URL = process.env.CUBE_URL || 'http://localhost:4000';
const CUBE_API_SECRET = process.env.CUBEJS_API_SECRET || 'datafabric-poc-secret-2026';
const TRINO_URL = process.env.TRINO_URL || 'http://localhost:8080';
// B2 鉴权：data-service 要求 X-API-Key；dashboard 作为后端代理持同一共享 key
const DATA_SERVICE_API_KEY =
  process.env.DATAFABRIC_API_KEY || 'datafabric-poc-api-key-2026-please-rotate';

async function ping(name, url, { expect = 200, timeoutMs = 3000 } = {}) {
  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort(), timeoutMs);
  const started = Date.now();
  try {
    const res = await fetch(url, { signal: ctrl.signal });
    clearTimeout(t);
    return {
      name,
      url,
      status: res.ok ? 'up' : 'degraded',
      http: res.status,
      latencyMs: Date.now() - started,
    };
  } catch (err) {
    clearTimeout(t);
    return {
      name,
      url,
      status: 'down',
      error: err.name === 'AbortError' ? `timeout ${timeoutMs}ms` : err.message,
      latencyMs: Date.now() - started,
    };
  }
}

/** 一次性 ping 全部 Week 2 服务栈 */
export async function healthAll() {
  const [trino, cube, dataService] = await Promise.all([
    ping('Trino 435', `${TRINO_URL}/v1/info`),
    ping('Cube.dev 0.36', `${CUBE_URL}/`),
    ping('Spring Boot 3.5', `${DATA_SERVICE_URL}/actuator/health`),
  ]);
  return { trino, cube, dataService, checkedAt: new Date().toISOString() };
}

/** B2 之后 /api/v1/** 全部要求 X-API-Key —— 所有出站请求统一带 */
function authHeaders(extra = {}) {
  return { 'X-API-Key': DATA_SERVICE_API_KEY, ...extra };
}

/** 调 data-service 的客户画像端点（含 role / view 透传） */
export async function fetchProfile(custId, { role = '', view = 'full' } = {}) {
  const path = view === 'brief'
    ? `/api/v1/customers/${encodeURIComponent(custId)}/brief`
    : `/api/v1/customers/${encodeURIComponent(custId)}/profile`;
  const url = `${DATA_SERVICE_URL}${path}`;
  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort(), 8000);
  const started = Date.now();
  try {
    const res = await fetch(url, {
      headers: role ? authHeaders({ 'X-User-Role': role }) : authHeaders(),
      signal: ctrl.signal,
    });
    clearTimeout(t);
    const body = await res.json();
    return {
      url,
      http: res.status,
      ok: res.ok,
      elapsedMs: Date.now() - started,
      data: body,
    };
  } catch (err) {
    clearTimeout(t);
    return {
      url,
      http: 0,
      ok: false,
      elapsedMs: Date.now() - started,
      error: err.message,
    };
  }
}

/** 调 metrics overview */
export async function fetchMetrics() {
  const url = `${DATA_SERVICE_URL}/api/v1/metrics/customer-overview`;
  try {
    const res = await fetch(url, { headers: authHeaders() });
    return { url, http: res.status, ok: res.ok, data: await res.json() };
  } catch (err) {
    return { url, http: 0, ok: false, error: err.message };
  }
}

/** 调 客户分群 */
export async function fetchCustomers({ level, page = 0, size = 10 } = {}) {
  const params = new URLSearchParams({ page, size });
  if (level) params.set('level', level);
  const url = `${DATA_SERVICE_URL}/api/v1/customers?${params}`;
  try {
    const res = await fetch(url, { headers: authHeaders() });
    return { url, http: res.status, ok: res.ok, data: await res.json() };
  } catch (err) {
    return { url, http: 0, ok: false, error: err.message };
  }
}

/** 调 审计日志 */
export async function fetchAudit(n = 20) {
  const url = `${DATA_SERVICE_URL}/api/v1/audit/recent?n=${n}`;
  try {
    const res = await fetch(url, { headers: authHeaders() });
    return { url, http: res.status, ok: res.ok, data: await res.json() };
  } catch (err) {
    return { url, http: 0, ok: false, error: err.message };
  }
}

/** 一次拉取 3 个角色的脱敏对比（同一 custId） */
export async function fetchMaskingComparison(custId, view = 'full') {
  const roles = ['ADMIN', 'CUSTOMER_VIEWER', 'SUPPORT'];
  const results = await Promise.all(
    roles.map((role) => fetchProfile(custId, { role, view }))
  );
  return roles.map((role, i) => ({ role, ...results[i] }));
}

/**
 * Week 3: 调用 data-service 的 Agent 端点（A 路 CustomerInsightAgent / B 路 RawDbAgent）。
 * 携带 X-API-Key（B2 鉴权）。LLM 调用可能较慢，超时放宽到 60s。
 */
export async function askAgent(apiPath, question) {
  const url = `${DATA_SERVICE_URL}${apiPath}`;
  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort(), 60000);
  const started = Date.now();
  try {
    const res = await fetch(url, {
      method: 'POST',
      headers: authHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify({ question }),
      signal: ctrl.signal,
    });
    clearTimeout(t);
    const body = await res.json();
    return {
      url,
      http: res.status,
      ok: res.ok,
      elapsedMs: Date.now() - started,
      data: body,
    };
  } catch (err) {
    clearTimeout(t);
    return {
      url,
      http: 0,
      ok: false,
      elapsedMs: Date.now() - started,
      error: err.name === 'AbortError' ? 'timeout 60s' : err.message,
    };
  }
}

// ============================================================
// Week 4: Agent 可观测台（F2 / F5 / F6 / F8 / F9 / F4）
// ============================================================

/** F9 trace 时间线（404 由调用方透传给前端显示"未找到"） */
export async function fetchTrace(requestId) {
  const url = `${DATA_SERVICE_URL}/api/v1/agent/trace/${encodeURIComponent(requestId)}`;
  try {
    const res = await fetch(url, { headers: authHeaders() });
    return { url, http: res.status, ok: res.ok, data: await res.json() };
  } catch (err) {
    return { url, http: 0, ok: false, error: err.message };
  }
}

/** F6 token 用量 + 成本快照 */
export async function fetchUsage() {
  const url = `${DATA_SERVICE_URL}/api/v1/agent/usage`;
  try {
    const res = await fetch(url, { headers: authHeaders() });
    return { url, http: res.status, ok: res.ok, data: await res.json() };
  } catch (err) {
    return { url, http: 0, ok: false, error: err.message };
  }
}

/** F2 工具调用日志（path 可选 fabric | raw） */
export async function fetchToolCalls({ path, limit = 50 } = {}) {
  const params = new URLSearchParams({ limit: String(limit) });
  if (path) params.set('path', path);
  const url = `${DATA_SERVICE_URL}/api/v1/agent/tool-calls?${params}`;
  try {
    const res = await fetch(url, { headers: authHeaders() });
    return { url, http: res.status, ok: res.ok, data: await res.json() };
  } catch (err) {
    return { url, http: 0, ok: false, error: err.message };
  }
}

/** F8 三源 HikariCP 池状态 */
export async function fetchPools() {
  const url = `${DATA_SERVICE_URL}/api/v1/raw-db/pools`;
  try {
    const res = await fetch(url, { headers: authHeaders() });
    return { url, http: res.status, ok: res.ok, data: await res.json() };
  } catch (err) {
    return { url, http: 0, ok: false, error: err.message };
  }
}

/**
 * F5 SSE 流式透传：把 data-service 的 SseEmitter 输出原样 pipe 到 express 响应。
 * 上游帧格式 `data:{"type":"token"|"done"|"error",...}`，浏览器端自行解析。
 * 上游非 200 时补发一条 error 事件再结束，保证前端总能收到终止信号。
 */
export async function pipeAgentStream(route, question, res) {
  const url = `${DATA_SERVICE_URL}/api/v1/agent/${route}/stream`;
  res.writeHead(200, {
    'Content-Type': 'text/event-stream; charset=utf-8',
    'Cache-Control': 'no-cache, no-transform',
    Connection: 'keep-alive',
    'X-Accel-Buffering': 'no',
  });
  // 记录已透传内容的尾部：undici 对服务端正常收尾的 SSE 流会额外抛 terminated
  // （done/error 帧其实已完整送达）—— 只有上游未发过终止帧时才向前端补 error
  let tail = '';
  const decoder = new TextDecoder();
  try {
    const upstream = await fetch(url, {
      method: 'POST',
      headers: authHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify({ question }),
    });
    if (!upstream.ok || !upstream.body) {
      res.write(`data: ${JSON.stringify({ type: 'error', message: `上游 HTTP ${upstream.status}（A 路需 Cube 在线；B 路需 LLM key）` })}\n\n`);
      res.end();
      return;
    }
    for await (const chunk of upstream.body) {
      res.write(chunk);
      tail = (tail + decoder.decode(chunk, { stream: true })).slice(-256);
    }
    res.end();
  } catch (err) {
    const ended = tail.includes('"type":"done"') || tail.includes('"type":"error"');
    if (!ended) {
      res.write(`data: ${JSON.stringify({ type: 'error', message: err.message })}\n\n`);
    }
    res.end();
  }
}
