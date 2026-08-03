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
      headers: role ? { 'X-User-Role': role } : {},
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
    const res = await fetch(url);
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
    const res = await fetch(url);
    return { url, http: res.status, ok: res.ok, data: await res.json() };
  } catch (err) {
    return { url, http: 0, ok: false, error: err.message };
  }
}

/** 调 审计日志 */
export async function fetchAudit(n = 20) {
  const url = `${DATA_SERVICE_URL}/api/v1/audit/recent?n=${n}`;
  try {
    const res = await fetch(url);
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
