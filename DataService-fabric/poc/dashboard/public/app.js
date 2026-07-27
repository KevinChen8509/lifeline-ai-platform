// Week 1 Dashboard 前端逻辑
// - 轮询 /api/health 显示 Trino 状态
// - 加载 /api/queries 渲染左侧卡片
// - 点击卡片切换详情 + 执行 SQL + 渲染图表/表格

const SRC_COLORS = {
  mysql: '#00758f',
  clickhouse: '#ffcc01',
  postgres: '#336791',
};

const state = {
  queries: [],       // 查询定义
  results: {},       // {id: result}
  activeId: null,
  chartInstance: null,
};

document.addEventListener('DOMContentLoaded', init);

async function init() {
  startClock();
  bindRunAll();
  await refreshHealth();
  setInterval(refreshHealth, 10_000);
  await loadQueries();
}

function startClock() {
  const el = document.getElementById('clock');
  const tick = () => {
    el.textContent = new Date().toLocaleTimeString('zh-CN', { hour12: false });
  };
  tick();
  setInterval(tick, 1000);
}

// ============ Health ============
async function refreshHealth() {
  const el = document.getElementById('health');
  const text = document.getElementById('health-text');
  const trinoVer = document.getElementById('trino-ver');
  try {
    const res = await fetch('/api/health');
    const data = await res.json();
    if (data.trino.ok) {
      el.classList.add('ok');
      el.classList.remove('down');
      text.textContent = `Trino ${data.trino.version} · ${data.trino.environment}`;
      trinoVer.textContent = data.trino.version;
    } else {
      el.classList.add('down');
      el.classList.remove('ok');
      text.textContent = `Trino 不可达: ${data.trino.error}`;
    }
  } catch (err) {
    el.classList.add('down');
    el.classList.remove('ok');
    text.textContent = `Dashboard 后端不可达: ${err.message}`;
  }
}

// ============ Queries ============
async function loadQueries() {
  const res = await fetch('/api/queries');
  state.queries = await res.json();
  renderCards();
  if (state.queries.length > 0) {
    selectCard(state.queries[0].id);
  }
}

function renderCards() {
  const list = document.getElementById('card-list');
  list.innerHTML = '';
  for (const q of state.queries) {
    const card = document.createElement('div');
    card.className = 'vcard pending';
    card.dataset.id = q.id;
    card.innerHTML = `
      <div class="vcard-head">
        <span class="vcard-name">${escapeHtml(q.name)}</span>
        <span class="vcard-badge">PENDING</span>
      </div>
      <div class="vcard-meta">
        <span>${q.sources.map((s) => `<span class="src-dot" style="background:${SRC_COLORS[s] || '#888'}"></span>${s}`).join(' ')}</span>
      </div>
    `;
    card.addEventListener('click', () => selectCard(q.id));
    list.appendChild(card);
  }
  updatePassCount();
}

async function selectCard(id) {
  state.activeId = id;
  document.querySelectorAll('.vcard').forEach((c) => {
    c.classList.toggle('active', c.dataset.id === id);
  });
  const q = state.queries.find((x) => x.id === id);
  if (!q) return;
  renderDetailSkeleton(q);
  // 还未跑过 → 自动跑一次
  if (!state.results[id] || state.results[id].error) {
    await runQuery(id);
  }
  const result = state.results[id];
  renderDetail(q, result);
}

function renderDetailSkeleton(q) {
  document.getElementById('detail-title').textContent = q.name;
  document.getElementById('detail-desc').textContent = q.description;
  document.getElementById('detail-meta').innerHTML = `<span class="metric"><span class="metric-label">状态</span><span class="metric-value">加载中…</span></span>`;
  document.getElementById('source-chips').innerHTML = q.sources
    .map((s) => `<span class="source-chip ${s}"><span class="src-dot"></span>${s}</span>`)
    .join('');
  document.getElementById('chart-title').textContent = q.visualization?.title || '图表';
  document.getElementById('table-title').textContent = '结果';
  document.getElementById('row-count').textContent = '';
  document.getElementById('data-table').querySelector('thead tr').innerHTML = '';
  document.getElementById('data-table').querySelector('tbody').innerHTML = '';
  document.getElementById('sql-text').textContent = '';
}

// ============ Run ============
async function runQuery(id) {
  const card = document.querySelector(`.vcard[data-id="${id}"]`);
  if (card) {
    card.classList.remove('pass', 'fail', 'pending');
    card.classList.add('running');
    card.querySelector('.vcard-badge').textContent = 'RUNNING';
  }
  try {
    const res = await fetch(`/api/query/${id}`);
    const data = await res.json();
    state.results[id] = data;
    applyVerdictToCard(id, data.verdict);
    return data;
  } catch (err) {
    state.results[id] = { error: err.message, verdict: { status: 'fail', reason: err.message } };
    applyVerdictToCard(id, { status: 'fail', reason: err.message });
  }
}

function applyVerdictToCard(id, verdict) {
  const card = document.querySelector(`.vcard[data-id="${id}"]`);
  if (!card) return;
  card.classList.remove('running', 'pending', 'pass', 'fail');
  card.classList.add(verdict.status);
  const badge = card.querySelector('.vcard-badge');
  badge.textContent = verdict.status.toUpperCase();
  updatePassCount();
}

function updatePassCount() {
  const total = state.queries.length;
  const pass = Object.values(state.results).filter((r) => r.verdict?.status === 'pass').length;
  document.getElementById('pass-count').textContent = `${pass} / ${total}`;
  const totalRows = Object.values(state.results).reduce((s, r) => s + (r.rows?.length || 0), 0);
  document.getElementById('total-rows').textContent = totalRows;
  const totalElapsed = Object.values(state.results).reduce((s, r) => s + (r.stats?.elapsedMs || 0), 0);
  document.getElementById('total-elapsed').textContent = totalElapsed;
}

async function bindRunAll() {
  const btn = document.getElementById('run-all');
  btn.addEventListener('click', async () => {
    btn.disabled = true;
    btn.textContent = '⏳ 执行中…';
    document.getElementById('last-run').textContent = '正在重跑…';
    try {
      const res = await fetch('/api/run-all', { method: 'POST' });
      const data = await res.json();
      for (const r of data.results) {
        state.results[r.id] = r;
        applyVerdictToCard(r.id, r.verdict);
      }
      document.getElementById('last-run').textContent = `完成于 ${new Date().toLocaleTimeString('zh-CN', { hour12: false })}`;
      if (state.activeId) {
        const q = state.queries.find((x) => x.id === state.activeId);
        renderDetail(q, state.results[state.activeId]);
      }
    } catch (err) {
      document.getElementById('last-run').textContent = `错误: ${err.message}`;
    } finally {
      btn.disabled = false;
      btn.textContent = '▶ 重跑全部';
    }
  });
}

// ============ Render detail ============
function renderDetail(q, result) {
  // Meta
  const meta = document.getElementById('detail-meta');
  if (result?.error) {
    meta.innerHTML = `
      <div class="metric"><span class="metric-label">状态</span><span class="metric-value" style="color:var(--fail)">${escapeHtml(result.error)}</span></div>
    `;
  } else if (result?.stats) {
    meta.innerHTML = `
      <div class="metric"><span class="metric-label">耗时</span><span class="metric-value">${result.stats.elapsedMs} ms</span></div>
      <div class="metric"><span class="metric-label">状态</span><span class="metric-value">${result.stats.state}</span></div>
      <div class="metric"><span class="metric-label">行数</span><span class="metric-value">${result.rows.length}</span></div>
      ${result.stats.processedBytes != null ? `<div class="metric"><span class="metric-label">扫描字节</span><span class="metric-value">${formatBytes(result.stats.processedBytes)}</span></div>` : ''}
    `;
  }

  // SQL
  document.getElementById('sql-text').textContent = q.sql || '';

  // Table + Chart
  if (result?.rows && result?.columns) {
    renderTable(result.columns, result.rows);
    renderChart(q, result);
  } else if (result?.error) {
    document.getElementById('data-table').querySelector('tbody').innerHTML =
      `<tr><td style="color:var(--fail);font-family:var(--mono)">${escapeHtml(result.error)}</td></tr>`;
    hideChart();
  }
}

function renderTable(columns, rows) {
  const thead = document.querySelector('#data-table thead tr');
  const tbody = document.querySelector('#data-table tbody');
  thead.innerHTML = columns.map((c) => `<th>${escapeHtml(c)}</th>`).join('');
  tbody.innerHTML = rows.map((row) =>
    `<tr>${row.map((cell) => {
      if (cell === null || cell === undefined) return '<td class="null">NULL</td>';
      const txt = typeof cell === 'object' ? JSON.stringify(cell) : String(cell);
      return `<td title="${escapeHtml(txt)}">${escapeHtml(txt)}</td>`;
    }).join('')}</tr>`,
  ).join('');
  document.getElementById('row-count').textContent = `${rows.length} 行`;
}

// ============ Chart ============
function hideChart() {
  document.getElementById('chart-canvas').hidden = true;
  document.getElementById('text-output').hidden = true;
}

function renderChart(q, result) {
  const canvas = document.getElementById('chart-canvas');
  const textOut = document.getElementById('text-output');
  if (state.chartInstance) {
    state.chartInstance.destroy();
    state.chartInstance = null;
  }

  if (q.visualization.type === 'text') {
    canvas.hidden = true;
    textOut.hidden = false;
    textOut.textContent = result.rows.map((r) => r[0]).join('\n');
    return;
  }

  canvas.hidden = false;
  textOut.hidden = true;
  const ctx = canvas.getContext('2d');
  const renderer = CHART_RENDERERS[q.visualization.type];
  if (renderer) {
    state.chartInstance = renderer(ctx, result);
  } else {
    textOut.hidden = false;
    textOut.textContent = `未支持的可视化类型: ${q.visualization.type}`;
  }
}

const CHART_RENDERERS = {
  // V1 雷达图：客户 6 维画像
  radar: (ctx, result) => {
    if (!result.rows.length) return null;
    const row = result.rows[0];
    const cols = result.columns;
    const metrics = ['total_orders', 'total_amount', 'risk_score'];
    const raw = metrics.map((m) => {
      const idx = cols.indexOf(m);
      return idx >= 0 ? Number(row[idx]) || 0 : 0;
    });
    // 归一化到 0-100
    const maxes = [50, 100000, 100];
    const norm = raw.map((v, i) => (v / maxes[i]) * 100);
    return new Chart(ctx, {
      type: 'radar',
      data: {
        labels: ['订单数', '消费金额', '风险分'],
        datasets: [{
          label: row[cols.indexOf('cust_id')] || '客户',
          data: norm,
          backgroundColor: 'rgba(91, 140, 255, 0.2)',
          borderColor: 'rgba(91, 140, 255, 0.9)',
          borderWidth: 2,
          pointBackgroundColor: '#5b8cff',
          pointRadius: 4,
        }],
      },
      options: chartOpts({ scale: { r: { beginAtZero: true, suggestedMax: 100 } } }),
    });
  },

  // V2 柱状图：Top10 消费金额
  bar: (ctx, result) => {
    const custIdIdx = result.columns.indexOf('cust_id');
    const amountIdx = result.columns.indexOf('total_amount');
    const labels = result.rows.map((r) => r[custIdIdx]);
    const data = result.rows.map((r) => Number(r[amountIdx]) || 0);
    return new Chart(ctx, {
      type: 'bar',
      data: {
        labels,
        datasets: [{
          label: '消费金额 (¥)',
          data,
          backgroundColor: data.map((_, i) =>
            i === 0 ? 'rgba(34, 197, 94, 0.85)' : 'rgba(91, 140, 255, 0.65)',
          ),
          borderColor: 'rgba(91, 140, 255, 1)',
          borderWidth: 1,
          borderRadius: 4,
        }],
      },
      options: chartOpts({
        indexAxis: 'y',
        scales: {
          x: { grid: { color: 'rgba(255,255,255,0.05)' } },
          y: { grid: { display: false } },
        },
      }),
    });
  },

  // V3 饼图：高风险客户区域分布
  pie: (ctx, result) => {
    const regionIdx = result.columns.indexOf('region');
    const countIdx = result.columns.indexOf('high_risk_customers');
    const labels = result.rows.map((r) => r[regionIdx]);
    const data = result.rows.map((r) => Number(r[countIdx]) || 0);
    return new Chart(ctx, {
      type: 'doughnut',
      data: {
        labels,
        datasets: [{
          data,
          backgroundColor: [
            '#5b8cff', '#7c5cff', '#2dd4bf', '#f59e0b', '#ef4444', '#ec4899',
          ],
          borderColor: '#0a0d14',
          borderWidth: 3,
        }],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { position: 'right', labels: { color: '#b8c0d4', font: { family: 'Inter', size: 12 } } },
        },
      },
    });
  },

  // V4 热力图（用柱状图矩阵近似）：渠道×等级 avg_amount
  heatmap: (ctx, result) => {
    const levelIdx = result.columns.indexOf('cust_level');
    const channelIdx = result.columns.indexOf('channel');
    const avgIdx = result.columns.indexOf('avg_amount');
    const levels = [...new Set(result.rows.map((r) => r[levelIdx]))].sort();
    const channels = [...new Set(result.rows.map((r) => r[channelIdx]))].sort();
    const lookup = new Map(result.rows.map((r) => [`${r[levelIdx]}|${r[channelIdx]}`, Number(r[avgIdx]) || 0]));
    const datasets = channels.map((ch, i) => ({
      label: ch,
      data: levels.map((lv) => lookup.get(`${lv}|${ch}`) || 0),
      backgroundColor: ['#5b8cff', '#7c5cff', '#2dd4bf', '#f59e0b'][i % 4],
      borderRadius: 4,
    }));
    return new Chart(ctx, {
      type: 'bar',
      data: { labels: levels, datasets },
      options: chartOpts({
        scales: {
          x: { grid: { display: false } },
          y: { grid: { color: 'rgba(255,255,255,0.05)' }, title: { display: true, text: '客单价 (¥)', color: '#6c7591' } },
        },
      }),
    });
  },
};

function chartOpts(overrides = {}) {
  return {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { labels: { color: '#b8c0d4', font: { family: 'Inter', size: 12 } } },
      tooltip: {
        backgroundColor: '#181d2a',
        borderColor: '#353c54',
        borderWidth: 1,
        titleColor: '#e8ecf4',
        bodyColor: '#b8c0d4',
      },
    },
    ...overrides,
  };
}

function formatBytes(bytes) {
  if (bytes == null) return '—';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`;
}

function escapeHtml(s) {
  return String(s ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}
