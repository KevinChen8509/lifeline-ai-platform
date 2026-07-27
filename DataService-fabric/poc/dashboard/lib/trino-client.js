// Trino REST 客户端 - 处理 /v1/statement 提交 + nextUri 轮询
// 文档: https://trino.io/docs/current/develop/client-protocol.html

const TRINO_URL = process.env.TRINO_URL || 'http://localhost:8080';
const TRINO_USER = process.env.TRINO_USER || 'dashboard';
const TRINO_SOURCE = process.env.TRINO_SOURCE || 'datafabric-dashboard';

const DEFAULT_TIMEOUT_MS = 30_000;
const POLL_INTERVAL_MS = 200;

/**
 * 执行一条 SQL，返回最终结果对象。
 * - columns: string[]
 * - rows: any[][]
 * - stats: { state, elapsedMs, processedRows, processedBytes, ... }
 */
export async function executeQuery(sql, { timeoutMs = DEFAULT_TIMEOUT_MS } = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const submitRes = await fetch(`${TRINO_URL}/v1/statement`, {
      method: 'POST',
      headers: {
        'X-Trino-User': TRINO_USER,
        'X-Trino-Source': TRINO_SOURCE,
        'Content-Type': 'text/plain',
      },
      body: sql,
      signal: controller.signal,
    });
    if (!submitRes.ok) {
      const text = await submitRes.text();
      throw new Error(`Trino submit HTTP ${submitRes.status}: ${text}`);
    }
    let payload = await submitRes.json();
    const startedAt = Date.now();

    // 关键: Trino 协议把数据分散在多次响应里
    //   - 前几次响应 stats.state=QUEUED/RUNNING 携带部分 data + nextUri
    //   - 最后一次响应 stats.state=FINISHED，nextUri=undefined，data=空
    // 必须累积每一步的 data，否则会丢数据
    const accumulatedRows = [];
    let columns = [];

    if (payload.error) throw new Error(formatTrinoError(payload.error));
    if (payload.columns) columns = payload.columns.map((c) => c.name);
    if (payload.data) {
      for (const row of payload.data) {
        accumulatedRows.push(row.map((cell) => normalizeCell(cell)));
      }
    }

    while (payload.nextUri) {
      if (Date.now() - startedAt > timeoutMs) {
        throw new Error(`Trino query timeout after ${timeoutMs}ms`);
      }
      await sleep(POLL_INTERVAL_MS);
      const pollRes = await fetch(payload.nextUri, {
        headers: { 'X-Trino-User': TRINO_USER },
        signal: controller.signal,
      });
      if (pollRes.status === 503) {
        // Trino 暂时不可用，短暂等待重试
        await sleep(500);
        continue;
      }
      if (!pollRes.ok) {
        const text = await pollRes.text();
        throw new Error(`Trino poll HTTP ${pollRes.status}: ${text}`);
      }
      payload = await pollRes.json();
      if (payload.error) throw new Error(formatTrinoError(payload.error));
      if (payload.columns && columns.length === 0) {
        columns = payload.columns.map((c) => c.name);
      }
      if (payload.data) {
        for (const row of payload.data) {
          accumulatedRows.push(row.map((cell) => normalizeCell(cell)));
        }
      }
    }

    const rows = accumulatedRows;
    const stats = {
      state: payload.stats?.state || 'UNKNOWN',
      elapsedMs: Date.now() - startedAt,
      queued: payload.stats?.queued,
      scheduled: payload.stats?.scheduled,
      processedRows: payload.stats?.processedRows,
      processedBytes: payload.stats?.processedBytes,
      peakMemoryBytes: payload.stats?.peakMemoryBytes,
    };
    return { columns, rows, stats };
  } finally {
    clearTimeout(timer);
  }
}

/** Trino 健康检查，返回 { ok, version, environment } */
export async function healthCheck() {
  try {
    const res = await fetch(`${TRINO_URL}/v1/info`, {
      headers: { 'X-Trino-User': TRINO_USER },
      signal: AbortSignal.timeout(3000),
    });
    if (!res.ok) return { ok: false, error: `HTTP ${res.status}` };
    const info = await res.json();
    return {
      ok: true,
      version: info.version,
      environment: info.environment,
      starting: info.starting,
      uptime: info.uptime,
    };
  } catch (err) {
    return { ok: false, error: err.message };
  }
}

function formatTrinoError(err) {
  const msg = err.message || 'Trino error';
  const loc = err.errorLocation ? ` (line ${err.errorLocation.lineNumber}:${err.errorLocation.columnNumber})` : '';
  return `${msg}${loc}`;
}

function normalizeCell(cell) {
  if (cell === null || cell === undefined) return null;
  // Trino 可能返回 { type: "...", value: "..." } 形式（如 DECIMAL/TIMESTAMP），直接取 value
  if (typeof cell === 'object' && 'value' in cell && Object.keys(cell).length <= 3) {
    return cell.value;
  }
  return cell;
}

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}
