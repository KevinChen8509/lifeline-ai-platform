// Week 5 · 数据服务市场 —— 数据资源目录 / 业务场景 / 自助发布参数化 API
// 后端：data-service /api/v1/catalog/* + /api/v1/services/*（经 dashboard /api/w5/* 代理）
/* global fetch, document, window */

const state = {
  tables: [],
  degraded: false,
  services: [],
  domainFilter: '全部',
  search: '',
  wiz: { filters: [], busy: false },
};

// ============================================================
// 工具
// ============================================================

const $ = (sel) => document.querySelector(sel);
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({
  '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
}[c]));

function toast(msg, ok = true) {
  const el = $('#toast');
  el.textContent = msg;
  el.style.borderColor = ok ? 'var(--green)' : 'var(--red)';
  el.style.display = 'block';
  setTimeout(() => { el.style.display = 'none'; }, 3200);
}

function switchTab(name) {
  document.querySelectorAll('.tab-btn').forEach((b) => b.classList.toggle('active', b.dataset.tab === name));
  document.querySelectorAll('.tab-panel').forEach((p) => p.classList.toggle('active', p.id === `panel-${name}`));
}

function showResult(el, result, startedAt) {
  const elapsed = Date.now() - startedAt;
  if (result.ok) {
    const d = result.data;
    const summary = d.rows
      ? `${d.rows.length} 行 · ${d.elapsedMs ?? elapsed}ms`
      : `${elapsed}ms`;
    el.innerHTML = `<span class="ok">✓ ${summary}</span>\n${esc(JSON.stringify(d, null, 2))}`;
  } else {
    const detail = result.data ? `${result.data.error ?? ''} ${result.data.message ?? ''}` : (result.error ?? `HTTP ${result.http}`);
    el.innerHTML = `<span class="err">✕ ${esc(detail)}</span>`;
  }
}

async function copyCurl(text) {
  try {
    await navigator.clipboard.writeText(text);
    toast('curl 命令已复制（API Key 请替换为你持有的值）');
  } catch {
    toast('复制失败 —— 请手动复制', false);
  }
}

// ============================================================
// Tab 1：数据资源目录
// ============================================================

async function loadCatalog() {
  const grid = $('#catalog-grid');
  grid.innerHTML = '<div class="empty">目录加载中…</div>';
  try {
    const res = await fetch('/api/w5/catalog');
    const data = await res.json();
    state.tables = data.tables ?? [];
    state.degraded = !!data.degraded;
  } catch (err) {
    state.tables = [];
    state.degraded = true;
  }
  $('#catalog-degraded').style.display = state.degraded ? 'block' : 'none';
  renderDomains();
  renderCatalog();
  renderWizardTables();
}

function renderDomains() {
  const domains = ['全部', ...new Set(state.tables.map((t) => t.domain))];
  $('#catalog-domains').innerHTML = domains
    .map((d) => `<span class="chip ${d === state.domainFilter ? 'active' : ''}" data-domain="${esc(d)}">${esc(d)}</span>`)
    .join('');
  document.querySelectorAll('#catalog-domains .chip').forEach((chip) => {
    chip.onclick = () => { state.domainFilter = chip.dataset.domain; renderDomains(); renderCatalog(); };
  });
}

function renderCatalog() {
  const grid = $('#catalog-grid');
  const q = state.search.trim().toLowerCase();
  const tables = state.tables.filter((t) => {
    if (state.domainFilter !== '全部' && t.domain !== state.domainFilter) return false;
    if (!q) return true;
    const hay = [t.fqn, t.description, ...t.columns.flatMap((c) => [c.name, c.description])].join(' ').toLowerCase();
    return hay.includes(q);
  });
  if (!tables.length) {
    grid.innerHTML = `<div class="empty">${state.tables.length ? '无匹配的目录条目' : '目录为空（OM 离线）'}</div>`;
    return;
  }
  grid.innerHTML = tables.map((t) => `
    <div class="card">
      <h3><span class="badge src-${esc(t.source)}">${esc(t.source)}</span>${esc(t.table)}</h3>
      <div class="fqn">${esc(t.fqn)}</div>
      <div class="desc">${esc(t.description)}</div>
      <span class="badge domain">${esc(t.domain)}</span>
      <span class="badge">${t.columns.length} 列</span>
      <div class="cols">${t.columns.map((c) => `<div><code>${esc(c.name)}</code> — ${esc(c.description)}</div>`).join('')}</div>
      <div class="actions">
        <button class="primary" data-publish="${esc(t.fqn)}">🪄 发布 API</button>
      </div>
    </div>`).join('');
  grid.querySelectorAll('[data-publish]').forEach((btn) => {
    btn.onclick = () => {
      switchTab('market');
      $('#wiz-table').value = btn.dataset.publish;
      onWizardTableChange();
      $('#publish-wizard').scrollIntoView({ behavior: 'smooth' });
      toast('已带入发布向导，勾选返回列与过滤参数');
    };
  });
}

// ============================================================
// Tab 2：业务场景
// ============================================================

const SCENARIOS_CUSTOMER = [
  {
    title: '客户洞察',
    desc: '单客户 360° 画像：基础信息 + 风险标签（跨 MySQL + PostgreSQL 联邦），带脱敏/审计/血缘三切面治理。',
    apis: ['customer-profile', 'agent-insight'],
    action: { kind: 'profile', label: '试调画像（C0001）' },
  },
  {
    title: '客户分群',
    desc: '按等级 / 风险等级筛选客户群，服务端过滤（F7），分页返回 —— 营销与运营的高频入口。',
    apis: ['customer-search', 'customer-overview'],
    action: { kind: 'customers', label: '查 VIP3 客户' },
  },
  {
    title: '风险监测',
    desc: '基于 risk_tags 风险标签实时筛出高风险客户 —— 该场景的专用 API 由业务方从目录自助发布（右侧一键演示）。',
    apis: ['（自助发布）'],
    action: { kind: 'guide-publish', label: '去发布风险查询 API' },
  },
];

const SCENARIOS_LIFELINE = [
  {
    title: '燃气安全监测', domain: '燃气',
    desc: '管网压力/流量异常检测与报警联动；计划资产：gas_pipe_status、gas_alarm_event（接入后从目录发布参数化 API）。',
  },
  {
    title: '供水管网分析', domain: '供水',
    desc: '管网水压水质时序分析与漏损定位；计划资产：water_pressure_ts、water_leak_event。',
  },
  {
    title: '桥梁健康预警', domain: '桥梁',
    desc: '应变/挠度传感器阈值预警；计划资产：bridge_sensor_ts、bridge_assessment。',
  },
];

function renderScenarios() {
  $('#scenario-customer').innerHTML = SCENARIOS_CUSTOMER.map((s, i) => `
    <div class="card">
      <h3>🎯 ${esc(s.title)}</h3>
      <div class="desc">${esc(s.desc)}</div>
      <div class="svc-path">关联服务：${s.apis.map(esc).join(' · ')}</div>
      <div class="actions">
        <button class="primary" data-scenario="${i}">${esc(s.action.label)}</button>
      </div>
      <div class="result-box" id="scenario-result-${i}" style="display:none"></div>
    </div>`).join('');

  $('#scenario-lifeline').innerHTML = SCENARIOS_LIFELINE.map((s) => `
    <div class="card" style="opacity:.85">
      <h3>🏙 ${esc(s.title)} <span class="badge pending">数据待接入</span></h3>
      <div class="desc">${esc(s.desc)}</div>
      <div class="stat-line">服务化路径：数据源注册 → 目录打标 → 场景挂载 → <b>自助发布 API</b>（与客户域同一条链路）</div>
    </div>`).join('');

  document.querySelectorAll('[data-scenario]').forEach((btn) => {
    btn.onclick = async () => {
      const s = SCENARIOS_CUSTOMER[+btn.dataset.scenario];
      const box = $(`#scenario-result-${+btn.dataset.scenario}`);
      box.style.display = 'block';
      box.innerHTML = '<span class="stat-line">调用中…</span>';
      const started = Date.now();
      let result;
      if (s.action.kind === 'profile') {
        const res = await fetch('/api/w2/profile?custId=C0001&role=ADMIN&view=full');
        const data = await res.json();
        result = { ok: res.ok, http: res.status, data: data?.data ?? data };
      } else if (s.action.kind === 'customers') {
        const res = await fetch('/api/w2/customers?level=VIP3&page=0&size=10');
        const data = await res.json();
        result = { ok: res.ok, http: res.status, data: data?.data ?? data };
      } else {
        switchTab('market');
        $('#wiz-table').value = 'postgres.external.risk_tags';
        onWizardTableChange();
        $('#wiz-name').value = '高风险客户标签查询';
        $('#wiz-slug').value = 'high-risk-customers';
        $('#publish-wizard').scrollIntoView({ behavior: 'smooth' });
        toast('已带入向导：勾选列后点「发布服务」，再回到本场景试调');
        return;
      }
      showResult(box, result, started);
    };
  });
}

// ============================================================
// Tab 3：服务市场
// ============================================================

async function loadServices() {
  try {
    const res = await fetch('/api/w5/services');
    const data = await res.json();
    state.services = data.services ?? [];
  } catch {
    state.services = [];
  }
  renderServices();
}

function builtinTryHtml(slug) {
  // 内置 GET 服务给参数试用表单；POST（Agent 双路）引导去 W4
  const forms = {
    'customer-profile': [['custId', 'C0001']],
    'customer-brief': [['custId', 'C0001']],
    'customer-search': [['level', 'VIP3'], ['riskLevel', ''], ['page', '0'], ['size', '5']],
    'customer-overview': [],
  };
  if (!(slug in forms)) {
    return `<div class="actions"><a href="/w4-app.html" target="_blank"><button class="ghost">→ W4 可观测台体验（SSE 流式）</button></a></div>`;
  }
  const inputs = forms[slug].map(([k, v]) =>
    `<label style="font-size:12px;color:var(--text-dim)">${esc(k)}<input data-try-param="${esc(k)}" value="${esc(v)}" style="width:110px;margin-left:6px"></label>`).join('');
  return `
    <div class="actions" style="flex-direction:column;align-items:flex-start;gap:6px">
      <div style="display:flex;gap:10px;flex-wrap:wrap">${inputs}</div>
      <div class="actions"><button class="primary" data-try-builtin="${esc(slug)}">▶ 试调</button></div>
    </div>
    <div class="result-box" id="builtin-result-${esc(slug)}" style="display:none"></div>`;
}

const BUILTIN_TRY_PATH = {
  'customer-profile': (p) => `/api/w2/profile?custId=${encodeURIComponent(p.custId || 'C0001')}&view=full`,
  'customer-brief': (p) => `/api/w2/profile?custId=${encodeURIComponent(p.custId || 'C0001')}&view=brief`,
  'customer-search': (p) => {
    const qs = new URLSearchParams(Object.entries(p).filter(([, v]) => v !== '')).toString();
    return `/api/w2/customers?${qs}`;
  },
  'customer-overview': () => '/api/w2/metrics',
};

function renderServices() {
  const builtin = state.services.filter((s) => s.service.type === 'builtin');
  const published = state.services.filter((s) => s.service.type === 'table-query');

  $('#builtin-grid').innerHTML = builtin.map(({ service: s, callCount }) => `
    <div class="card">
      <h3><span class="badge builtin">内置</span>${esc(s.name)}</h3>
      <div class="svc-path"><span class="method ${esc(s.method)}">${esc(s.method)}</span>${esc(s.pathTemplate)}</div>
      <div class="desc">${esc(s.description)}</div>
      ${builtinTryHtml(s.slug)}
      <div class="callcount">累计调用 <b>${callCount}</b> 次</div>
    </div>`).join('');

  document.querySelectorAll('[data-try-builtin]').forEach((btn) => {
    btn.onclick = async () => {
      const slug = btn.dataset.tryBuiltin;
      const box = $(`#builtin-result-${slug}`);
      box.style.display = 'block';
      box.innerHTML = '<span class="stat-line">调用中…</span>';
      const started = Date.now();
      const params = {};
      btn.closest('.card').querySelectorAll('[data-try-param]').forEach((i) => { params[i.dataset.tryParam] = i.value; });
      const res = await fetch(BUILTIN_TRY_PATH[slug](params));
      const data = await res.json();
      showResult(box, { ok: res.ok, http: res.status, data: data?.data ?? data }, started);
    };
  });

  $('#published-empty').style.display = published.length ? 'none' : 'block';
  $('#published-grid').innerHTML = published.map(({ service: s, callCount }) => {
    const filterInputs = s.filters.map((f) => `
      <label style="font-size:12px;color:var(--text-dim)">${esc(f.column)} (${esc(f.operator)})<input data-svc-param="${esc(f.column)}" style="width:120px;margin-left:6px" placeholder="如 ${esc(defaultHint(s.table, f.column))}"></label>`).join('');
    return `
    <div class="card" data-slug="${esc(s.slug)}">
      <h3><span class="badge published">已发布</span>${esc(s.name)}</h3>
      <div class="svc-path">GET /api/v1/services/${esc(s.slug)}/query</div>
      <div class="desc">${esc(s.description || '（无描述）')}</div>
      <div class="cols">
        <div>返回列：<code>${s.allowedColumns.map(esc).join('</code> <code>')}</code></div>
        <div>过滤参数：${s.filters.length ? s.filters.map((f) => `<code>${esc(f.column)}:${esc(f.operator)}</code>`).join(' ') : '（无）'}</div>
        <div>源表：<span class="fqn">${esc(s.source)}.${esc(s.table)}</span> · 默认 ${s.defaultLimit} 行（上限 500）</div>
      </div>
      <div class="actions" style="flex-direction:column;align-items:flex-start;gap:6px">
        <div style="display:flex;gap:10px;flex-wrap:wrap;align-items:center">
          ${filterInputs}
          <label style="font-size:12px;color:var(--text-dim)">limit<input data-svc-param="__limit" value="20" style="width:70px;margin-left:6px"></label>
        </div>
        <div class="actions">
          <button class="primary" data-svc-call="${esc(s.slug)}">▶ 试调</button>
          <button class="ghost" data-svc-curl="${esc(s.slug)}">📋 复制 curl</button>
          <button class="danger" data-svc-del="${esc(s.slug)}">下线</button>
          <span class="callcount">累计调用 <b>${callCount}</b> 次</span>
        </div>
      </div>
      <div class="result-box" id="svc-result-${esc(s.slug)}" style="display:none"></div>
    </div>`;
  }).join('');

  bindPublishedActions();
}

function defaultHint(table, column) {
  const hints = { cust_level: 'VIP3', risk_level: 'high', region: '北京', cust_id: 'C0001', cust_name: '张' };
  return hints[column] ?? '值';
}

function bindPublishedActions() {
  document.querySelectorAll('[data-svc-call]').forEach((btn) => {
    btn.onclick = async () => {
      const slug = btn.dataset.svcCall;
      const card = btn.closest('.card');
      const box = $(`#svc-result-${slug}`);
      box.style.display = 'block';
      box.innerHTML = '<span class="stat-line">调用中…</span>';
      const params = {};
      card.querySelectorAll('[data-svc-param]').forEach((i) => {
        if (i.value.trim() !== '' && i.dataset.svcParam !== '__limit') params[i.dataset.svcParam] = i.value.trim();
        if (i.dataset.svcParam === '__limit' && i.value.trim() !== '') params.__limit = i.value.trim();
      });
      const limit = params.__limit;
      delete params.__limit;
      const qs = new URLSearchParams(params);
      if (limit) qs.set('limit', limit);
      const started = Date.now();
      const res = await fetch(`/api/w5/services/${encodeURIComponent(slug)}/query?${qs}`);
      const data = await res.json();
      showResult(box, { ok: res.ok, http: res.status, data }, started);
      loadServices();
    };
  });
  document.querySelectorAll('[data-svc-curl]').forEach((btn) => {
    btn.onclick = () => {
      const slug = btn.dataset.svcCurl;
      copyCurl(`curl -H "X-API-Key: <your-api-key>" "http://localhost:8090/api/v1/services/${slug}/query?limit=20"`);
    };
  });
  document.querySelectorAll('[data-svc-del]').forEach((btn) => {
    btn.onclick = async () => {
      const slug = btn.dataset.svcDel;
      if (!window.confirm(`确认下线服务「${slug}」？调用方将立即 404。`)) return;
      const res = await fetch(`/api/w5/services/${encodeURIComponent(slug)}`, { method: 'DELETE' });
      if (res.status === 204) { toast(`服务 ${slug} 已下线`); loadServices(); }
      else toast(`下线失败 HTTP ${res.status}`, false);
    };
  });
}

// ============================================================
// 发布向导
// ============================================================

function renderWizardTables() {
  const sel = $('#wiz-table');
  const prev = sel.value;
  sel.innerHTML = state.tables.length
    ? state.tables.map((t) => `<option value="${esc(t.fqn)}">${esc(t.fqn)}（${esc(t.description)}）</option>`).join('')
    : '<option value="">（目录为空 —— OM 离线）</option>';
  if (prev && state.tables.some((t) => t.fqn === prev)) sel.value = prev;
  onWizardTableChange();
}

function onWizardTableChange() {
  const fqn = $('#wiz-table').value;
  const table = state.tables.find((t) => t.fqn === fqn);
  const box = $('#wiz-columns');
  if (!table) { box.innerHTML = '<span class="stat-line">无可选列</span>'; $('#wiz-filter-col').innerHTML = ''; return; }
  box.innerHTML = table.columns.map((c) =>
    `<label><input type="checkbox" data-wiz-col="${esc(c.name)}" checked> <code>${esc(c.name)}</code></label>`).join('');
  $('#wiz-filter-col').innerHTML = table.columns.map((c) => `<option value="${esc(c.name)}">${esc(c.name)}</option>`).join('');
  state.wiz.filters = [];
  renderWizardFilters();
  if (!$('#wiz-slug').value) {
    $('#wiz-slug').value = `${table.source}-${table.table}-query`.replace(/_/g, '-');
  }
}

function renderWizardFilters() {
  $('#wiz-filters-list').textContent = state.wiz.filters.length
    ? state.wiz.filters.map((f) => `${f.column}:${f.op}`).join(' · ')
    : '（未添加则不支持过滤）';
}

async function publishFromWizard() {
  if (state.wiz.busy) return;
  const fqn = $('#wiz-table').value;
  const name = $('#wiz-name').value.trim();
  const slug = $('#wiz-slug').value.trim();
  const allowedColumns = [...document.querySelectorAll('[data-wiz-col]:checked')].map((i) => i.dataset.wizCol);
  const status = $('#wiz-status');

  if (!fqn) { toast('目录为空：OpenMetadata 离线时无法发布（校验依赖元数据白名单）', false); return; }
  if (!name || !slug) { toast('请填写服务名称与 slug', false); return; }
  if (!allowedColumns.length) { toast('至少勾选一个返回列', false); return; }

  state.wiz.busy = true;
  status.textContent = '发布中…';
  const res = await fetch('/api/w5/services', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      slug, name, description: `自助发布：${fqn}`,
      fqn, allowedColumns,
      filters: state.wiz.filters.map((f) => ({ column: f.column, operator: f.op })),
      defaultLimit: +$('#wiz-limit').value || 20,
    }),
  });
  const data = await res.json().catch(() => ({}));
  state.wiz.busy = false;
  if (res.status === 201) {
    status.innerHTML = `<span class="ok">✓ 已发布：GET /api/v1/services/${esc(slug)}/query</span>`;
    toast(`「${name}」发布成功 —— 分钟级上线，无需后端排期`);
    loadServices();
  } else {
    status.innerHTML = `<span class="err">✕ ${esc(data.message ?? `HTTP ${res.status}`)}</span>`;
  }
}

// ============================================================
// 初始化
// ============================================================

document.querySelectorAll('.tab-btn').forEach((b) => { b.onclick = () => switchTab(b.dataset.tab); });
$('#catalog-search').oninput = (e) => { state.search = e.target.value; renderCatalog(); };
$('#wiz-table').onchange = onWizardTableChange;
$('#wiz-add-filter').onclick = () => {
  const col = $('#wiz-filter-col').value;
  const op = $('#wiz-filter-op').value;
  if (!col) return;
  if (state.wiz.filters.some((f) => f.column === col)) { toast('该列已配置过滤', false); return; }
  state.wiz.filters.push({ column: col, op });
  renderWizardFilters();
};
$('#wiz-publish').onclick = publishFromWizard;

renderScenarios();
loadCatalog();
loadServices();
