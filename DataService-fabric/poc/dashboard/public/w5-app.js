// Week 5 · 数据服务市场 —— 数据资源目录 / 业务场景 / 自助发布参数化 API
// 后端：data-service /api/v1/catalog/* + /api/v1/services/*（经 dashboard /api/w5/* 代理）
/* global fetch, document, window */

const state = {
  tables: [],
  degraded: false,
  services: [],
  domainFilter: '全部',
  search: '',
  wiz: { filters: [], join: { on: false }, busy: false },
  verify: { slug: '', running: false },
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
  const published = state.services.filter((s) => s.service.type === 'table-query' || s.service.type === 'fusion');
  renderVerifySelect();

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
    const joinInfo = s.type === 'fusion' ? s.joins.map((j) => `
        <div>融合从表：<span class="fqn">${esc(j.fqn)}</span> → 嵌套字段 <code>${esc(j.name)}</code>（${j.columns.length} 列 · 关联 <code>${esc(j.joinColumn)}</code> ⇠ <code>${esc(j.parentColumn)}</code> · 每主键 ≤${j.limitPerParent}）</div>`).join('') : '';
    const keyRow = s.apiKey ? `
      <div class="wiz-row" style="margin:8px 0 0;min-width:100%">
        <label style="min-width:auto;font-size:12px">服务 API Key</label>
        <span class="key-chip">${esc(s.apiKey.slice(0, 10))}…${esc(s.apiKey.slice(-4))}</span>
        <button class="ghost" data-svc-key="${esc(s.slug)}">📋 复制 Key</button>
        <span style="font-size:12px;color:var(--text-dim)">仅可调本服务 /query（恒时校验），对外开放用</span>
      </div>` : '';
    const typeBadge = s.type === 'fusion' ? '<span class="badge fusion">JOIN</span>' : '';
    return `
    <div class="card" data-slug="${esc(s.slug)}">
      <h3><span class="badge published">已发布</span>${typeBadge}${esc(s.name)}</h3>
      <div class="svc-path">GET /api/v1/services/${esc(s.slug)}/query</div>
      <div class="desc">${esc(s.description || '（无描述）')}</div>
      <div class="cols">
        <div>返回列：<code>${s.allowedColumns.map(esc).join('</code> <code>')}</code></div>
        <div>过滤参数：${s.filters.length ? s.filters.map((f) => `<code>${esc(f.column)}:${esc(f.operator)}</code>`).join(' ') : '（无）'}</div>
        <div>源表：<span class="fqn">${esc(s.source)}.${esc(s.table)}</span> · 默认 ${s.defaultLimit} 行（上限 500）</div>
        ${joinInfo}
      </div>
      ${keyRow}
      <div class="actions" style="flex-direction:column;align-items:flex-start;gap:6px">
        <div style="display:flex;gap:10px;flex-wrap:wrap;align-items:center">
          ${filterInputs}
          <label style="font-size:12px;color:var(--text-dim)">limit<input data-svc-param="__limit" value="20" style="width:70px;margin-left:6px"></label>
        </div>
        <div class="actions">
          <button class="primary" data-svc-call="${esc(s.slug)}">▶ 试调</button>
          <button class="ghost" data-svc-verify="${esc(s.slug)}">🧪 验证</button>
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
  document.querySelectorAll('[data-svc-verify]').forEach((btn) => {
    btn.onclick = () => gotoVerify(btn.dataset.svcVerify);
  });
  document.querySelectorAll('[data-svc-curl]').forEach((btn) => {
    btn.onclick = () => {
      const slug = btn.dataset.svcCurl;
      const found = state.services.find((s) => s.service.slug === slug)?.service;
      const keyHint = found?.apiKey ? found.apiKey : '<your-service-key>';
      copyCurl(`# 第三方直调：服务级 Key（仅本服务 /query 有效）\ncurl -H "X-API-Key: ${keyHint}" "http://localhost:8090/api/v1/services/${slug}/query?limit=20"`);
    };
  });
  document.querySelectorAll('[data-svc-key]').forEach((btn) => {
    btn.onclick = async () => {
      const found = state.services.find((s) => s.service.slug === btn.dataset.svcKey)?.service;
      if (!found?.apiKey) { toast('未取到服务 Key', false); return; }
      try {
        await navigator.clipboard.writeText(found.apiKey);
        toast(`「${found.slug}」服务 Key 已复制 —— 仅可调它自己的 /query`);
      } catch {
        toast('复制失败 —— 请手动复制', false);
      }
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
  renderJoinTableOptions(fqn);
}

/** 融合从表候选：目录中除主表外的所有表 */
function renderJoinTableOptions(mainFqn) {
  const sel = $('#wiz-join-table');
  if (!sel) return;
  const candidates = state.tables.filter((t) => t.fqn !== mainFqn);
  sel.innerHTML = candidates.length
    ? candidates.map((t) => `<option value="${esc(t.fqn)}">${esc(t.fqn)}（${esc(t.description)}）</option>`).join('')
    : '<option value="">（目录无其他表可融合）</option>';
  onJoinTableChange();
}

function onJoinTableChange() {
  const fqn = $('#wiz-join-table').value;
  const table = state.tables.find((t) => t.fqn === fqn);
  const box = $('#wiz-join-columns');
  if (!table) {
    box.innerHTML = '<span class="stat-line">无可选从表列</span>';
    $('#wiz-join-col').innerHTML = '';
    return;
  }
  box.innerHTML = table.columns.map((c) =>
    `<label><input type="checkbox" data-wiz-join-col="${esc(c.name)}" checked> <code>${esc(c.name)}</code></label>`).join('');
  $('#wiz-join-col').innerHTML = table.columns.map((c) => `<option value="${esc(c.name)}">${esc(c.name)}</option>`).join('');
  // 默认关联键猜 cust_id（客户域演示），存在即选中
  if (table.columns.some((c) => c.name === 'cust_id')) $('#wiz-join-col').value = 'cust_id';
  if (!$('#wiz-join-name').value) $('#wiz-join-name').value = table.table;
  refreshParentColumnOptions();
}

/** 主表关联列 = 当前勾选的主表返回列（发布校验要求 parentColumn ∈ allowedColumns） */
function refreshParentColumnOptions() {
  const sel = $('#wiz-parent-col');
  if (!sel) return;
  const prev = sel.value;
  const checked = [...document.querySelectorAll('[data-wiz-col]:checked')].map((i) => i.dataset.wizCol);
  sel.innerHTML = checked.length
    ? checked.map((c) => `<option value="${esc(c)}">${esc(c)}</option>`).join('')
    : '<option value="">（先勾选主表返回列）</option>';
  if (checked.includes(prev)) sel.value = prev;
  else if (checked.includes('cust_id')) sel.value = 'cust_id';
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

  // 融合从表（可选）：开关开则组装受控 joins 声明
  let joins = [];
  if (state.wiz.join.on) {
    const joinFqn = $('#wiz-join-table').value;
    const joinName = $('#wiz-join-name').value.trim();
    const joinColumns = [...document.querySelectorAll('[data-wiz-join-col]:checked')].map((i) => i.dataset.wizJoinCol);
    const joinCol = $('#wiz-join-col').value;
    const parentCol = $('#wiz-parent-col').value;
    const perParent = +$('#wiz-join-limit').value || 20;
    if (!joinFqn) { toast('融合从表：目录无其他表可选', false); return; }
    if (!joinName || !/^[A-Za-z][A-Za-z0-9_]*$/.test(joinName)) { toast('输出字段名须为合法标识符（如 orders）', false); return; }
    if (!joinColumns.length) { toast('至少勾选一个从表列', false); return; }
    if (!joinCol || !parentCol) { toast('请选好关联键（从表列 ⇠ 主表列）', false); return; }
    if (!allowedColumns.includes(parentCol)) { toast(`主表关联列 ${parentCol} 须同时勾选进返回列`, false); return; }
    joins = [{ fqn: joinFqn, name: joinName, columns: joinColumns, joinColumn: joinCol, parentColumn: parentCol, limitPerParent: perParent }];
  }

  state.wiz.busy = true;
  status.textContent = '发布中…';
  const res = await fetch('/api/w5/services', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      slug, name, description: `自助发布：${fqn}`,
      fqn, allowedColumns,
      filters: state.wiz.filters.map((f) => ({ column: f.column, operator: f.op })),
      joins,
      defaultLimit: +$('#wiz-limit').value || 20,
    }),
  });
  const data = await res.json().catch(() => ({}));
  state.wiz.busy = false;
  if (res.status === 201) {
    const apiKey = data?.service?.apiKey;
    status.innerHTML = `<span class="ok">✓ 已发布：GET /api/v1/services/${esc(slug)}/query</span>${apiKey ? ' <span class="key-chip">sk-w6-…已生成</span>' : ''} <button class="ghost" id="goto-verify">🧪 去验证</button>`;
    const gv = $('#goto-verify');
    if (gv) gv.onclick = () => gotoVerify(slug);
    toast(`「${name}」发布成功${joins.length ? '（融合服务）' : ''} —— 服务 Key 已生成，卡片可复制，对外开放就绪`);
    loadServices();
  } else {
    status.innerHTML = `<span class="err">✕ ${esc(data.message ?? `HTTP ${res.status}`)}</span>`;
  }
}

// ============================================================
// Tab 4：服务验证台 —— 发布 ≠ 交付，验证通过才可交给消费方
// ============================================================

function gotoVerify(slug) {
  state.verify.slug = slug;
  renderVerifySelect();
  switchTab('verify');
  $('#panel-verify').scrollIntoView({ behavior: 'smooth' });
  toast(`验证对象「${slug}」已就位 —— 点「运行验证套件」`);
}

function renderVerifySelect() {
  const sel = $('#verify-slug');
  if (!sel) return;
  const published = state.services.filter((s) => s.service.type === 'table-query' || s.service.type === 'fusion');
  sel.innerHTML = published.length
    ? published.map(({ service: s }) => `<option value="${esc(s.slug)}">${esc(s.slug)} —— ${esc(s.name)}${s.type === 'fusion' ? '（融合）' : ''}</option>`).join('')
    : '<option value="">（尚无已发布服务 —— 先到服务市场发布）</option>';
  if (state.verify.slug && published.some(({ service: s }) => s.slug === state.verify.slug)) {
    sel.value = state.verify.slug;
  }
  renderVerifyContract();
}

function currentVerifyService() {
  const slug = $('#verify-slug')?.value;
  return state.services.find((s) => s.service.slug === slug)?.service ?? null;
}

function renderVerifyContract() {
  const box = $('#verify-contract');
  const s = currentVerifyService();
  if (!s) { box.innerHTML = ''; return; }
  const joinInfo = s.type === 'fusion' ? s.joins.map((j) => `
    <div>融合从表 <code>${esc(j.name)}</code>：<span class="fqn">${esc(j.fqn)}</span> · 列 [${j.columns.map(esc).join(', ')}] · 关联 <code>${esc(j.joinColumn)}</code> ⇠ <code>${esc(j.parentColumn)}</code> · 每主键 ≤${j.limitPerParent}</div>`).join('') : '';
  const keyRow = s.apiKey ? `<div>服务 Key：<span class="key-chip">${esc(s.apiKey.slice(0, 10))}…${esc(s.apiKey.slice(-4))}</span>（仅本服务 /query 有效 —— 对外分发）</div>` : '';
  box.innerHTML = `
    <div class="card" style="margin-bottom:14px">
      <h3><span class="badge published">契约快照</span>${s.type === 'fusion' ? '<span class="badge fusion">JOIN</span>' : ''}${esc(s.slug)}</h3>
      <div class="cols">
        <div>返回列白名单：<code>${s.allowedColumns.map(esc).join('</code> <code>')}</code></div>
        <div>过滤参数：${s.filters.length ? s.filters.map((f) => `<code>${esc(f.column)}:${esc(f.operator)}</code>`).join(' ') : '（无）'}</div>
        <div>源表：<span class="fqn">${esc(s.source)}.${esc(s.table)}</span> · 默认 ${s.defaultLimit} 行 · LIMIT 上限 500</div>
        ${joinInfo}
        ${keyRow}
      </div>
    </div>`;
  const first = s.filters[0];
  if (first) $('#verify-value').value = defaultHint(s.table, first.column);
}

async function callVerifyQuery(slug, params) {
  const qs = new URLSearchParams(params);
  const started = Date.now();
  const res = await fetch(`/api/w5/services/${encodeURIComponent(slug)}/query?${qs}`);
  let data = null;
  try { data = await res.json(); } catch { /* 空响应体 */ }
  return { http: res.status, ok: res.ok, data, elapsed: Date.now() - started };
}

async function runVerificationSuite() {
  const s = currentVerifyService();
  if (!s) { toast('尚无可验证的已发布服务 —— 先到服务市场发布', false); return; }
  if (state.verify.running) return;
  state.verify.running = true;
  const btn = $('#verify-run');
  btn.disabled = true;
  const resultsBox = $('#verify-results');
  const summaryBox = $('#verify-summary');
  summaryBox.innerHTML = '';
  resultsBox.innerHTML = '<div class="empty">验证套件运行中…</div>';

  const first = s.filters[0];
  const testValue = $('#verify-value').value.trim() || defaultHint(s.table, first?.column);
  const rows = [];
  const addRow = (r) => { rows.push(r); resultsBox.innerHTML = verifyTableHtml(rows); };

  if (s.type === 'fusion') {
    await fusionSuite(s, first, testValue, addRow);
  } else {
    await tableQuerySuite(s, first, testValue, addRow);
  }

  const pass = rows.filter((r) => r.status === 'pass').length;
  const fail = rows.filter((r) => r.status === 'fail').length;
  const skip = rows.filter((r) => r.status === 'skip').length;
  summaryBox.innerHTML = `
    <div class="verdict ${fail ? 'bad' : 'ok'}">
      ${fail
        ? `✕ 验证未通过：${fail} 项 FAIL —— 请检查服务定义后再交付`
        : `✓ 验证通过：${pass} 项 PASS${skip ? ` · ${skip} 项跳过` : ''} —— 「${esc(s.slug)}」可交付消费方（curl 与服务 Key 复制见服务市场页）`}
    </div>`;
  state.verify.running = false;
  btn.disabled = false;
  loadServices(); // 刷新调用计数
}

/** 单表服务 V1-V5 */
async function tableQuerySuite(s, first, testValue, addRow) {
  // V1 正常调用：形状契约（HTTP 200 + 返回列 = 发布白名单 + 行数 ≤ limit）
  {
    const params = first ? { [first.column]: testValue } : {};
    const r = await callVerifyQuery(s.slug, { ...params, limit: 5 });
    const cols = r.data?.columns ?? [];
    const whitelist = new Set(s.allowedColumns);
    const rowCount = r.data?.rows?.length ?? -1;
    const shapeOk = r.http === 200
      && cols.length === s.allowedColumns.length
      && cols.every((c) => whitelist.has(c))
      && rowCount >= 0 && rowCount <= 5;
    addRow({
      id: 'V1', name: '正常调用', expect: 'HTTP 200 · 返回列 = 发布白名单 · 行数 ≤ limit',
      status: shapeOk ? 'pass' : 'fail',
      detail: `HTTP ${r.http} · ${rowCount} 行 · ${r.elapsed}ms · 列 [${cols.join(', ')}]`,
    });
  }

  // V2 SQL 注入防御：payload 参数化绑定 → 200 且 0 行零泄露
  if (first) {
    const payload = `${testValue}' OR '1'='1`;
    const r = await callVerifyQuery(s.slug, { [first.column]: payload, limit: 5 });
    const safe = r.http === 200 && (r.data?.total ?? -1) === 0;
    addRow({
      id: 'V2', name: 'SQL 注入防御', expect: `payload「…' OR '1'='1」参数化绑定 → 200 且 total=0`,
      status: safe ? 'pass' : 'fail',
      detail: `HTTP ${r.http} · total=${r.data?.total ?? '?'} · ${r.elapsed}ms`,
    });
  } else {
    addRow({ id: 'V2', name: 'SQL 注入防御', expect: 'payload 参数化绑定 → 200 且 total=0', status: 'skip', detail: '服务未配置过滤参数 —— 无值入口' });
  }

  // V3 未知参数拒绝：未注册参数 → 400
  {
    const r = await callVerifyQuery(s.slug, { __bogus_param: 'x' });
    const msg = String(r.data?.message ?? r.data?.error ?? '');
    addRow({
      id: 'V3', name: '未知参数拒绝', expect: '未注册参数 __bogus_param → HTTP 400',
      status: r.http === 400 ? 'pass' : 'fail',
      detail: `HTTP ${r.http} · ${msg.slice(0, 80)}`,
    });
  }

  // V4 LIMIT 钳制：limit=99999 → 服务端钳制 ≤500 不失控
  {
    const r = await callVerifyQuery(s.slug, { limit: 99999 });
    const rowCount = r.data?.rows?.length ?? 1e9;
    addRow({
      id: 'V4', name: 'LIMIT 钳制', expect: 'limit=99999 → 钳制 ≤500，不报错不失控',
      status: r.http === 200 && rowCount <= 500 ? 'pass' : 'fail',
      detail: `HTTP ${r.http} · 返回 ${rowCount > 1e8 ? '?' : rowCount} 行（≤500）· ${r.elapsed}ms`,
    });
  }

  // V5 发布白名单闸：携带注入列名重新发布 → 400（列必须逐字命中目录元数据）
  {
    const table = state.tables.find((t) => t.source === s.source && t.table === s.table);
    if (table) {
      const probeSlug = `verify-probe-${Date.now()}`;
      const res = await fetch('/api/w5/services', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          slug: probeSlug, name: '验证探针（应被拒）', description: 'verify probe',
          fqn: table.fqn,
          allowedColumns: [...s.allowedColumns, 'cust_level; DROP TABLE customer'],
          filters: [], joins: [], defaultLimit: 5,
        }),
      });
      let msg = '';
      try { msg = (await res.json()).message ?? ''; } catch { /* ignore */ }
      if (res.status === 201) {
        await fetch(`/api/w5/services/${encodeURIComponent(probeSlug)}`, { method: 'DELETE' }); // 意外通过 → 清理探针
      }
      addRow({
        id: 'V5', name: '发布白名单闸', expect: '携带「cust_level; DROP TABLE customer」列发布 → HTTP 400',
        status: res.status === 400 ? 'pass' : 'fail',
        detail: `HTTP ${res.status} · ${String(msg).slice(0, 90)}`,
      });
    } else {
      addRow({ id: 'V5', name: '发布白名单闸', expect: '携带注入列名发布 → HTTP 400', status: 'skip', detail: '目录中未找到该表 FQN（OM 离线？）—— 无法构造发布探针' });
    }
  }
}

/** 融合服务 FV1-FV7：形状 · 注入 · 未知参数 · LIMIT/每主键钳制 · 发布闸 · 关联一致性 · 金标 */
async function fusionSuite(s, first, testValue, addRow) {
  const nestedCount = (d) => (d?.rows ?? []).reduce(
    (n, row) => n + s.joins.reduce((m, j) => m + (Array.isArray(row[j.name]) ? row[j.name].length : -1), 0), 0);

  // FV1 融合形状：200 + 主列=白名单 + 嵌套字段名=声明 + 每主行都挂数组
  {
    const params = first ? { [first.column]: testValue } : {};
    const r = await callVerifyQuery(s.slug, { ...params, limit: 5 });
    const d = r.data ?? {};
    const cols = d.columns ?? [];
    const whitelist = new Set(s.allowedColumns);
    const joinNames = (d.joins ?? []).map((j) => j.name);
    const shapeOk = r.http === 200
      && cols.length === s.allowedColumns.length && cols.every((c) => whitelist.has(c))
      && joinNames.join(',') === s.joins.map((j) => j.name).join(',')
      && (d.rows ?? []).every((row) => s.joins.every((j) => Array.isArray(row[j.name])));
    addRow({
      id: 'FV1', name: '融合形状', expect: 'HTTP 200 · 主列=白名单 · 嵌套字段名=声明 · 每主行挂从行数组',
      status: shapeOk ? 'pass' : 'fail',
      detail: `HTTP ${r.http} · ${d.rows?.length ?? '?'} 主行 · 嵌套 [${joinNames.join(', ')}] · ${r.elapsed}ms`,
    });
  }

  // FV2 注入防御：关联参数 OR 恒真 → 200 且 0 主行 0 从行
  if (first) {
    const payload = `${testValue}' OR '1'='1`;
    const r = await callVerifyQuery(s.slug, { [first.column]: payload, limit: 5 });
    const d = r.data ?? {};
    const safe = r.http === 200 && (d.total ?? -1) === 0 && nestedCount(d) === 0;
    addRow({
      id: 'FV2', name: 'SQL 注入防御', expect: `payload「…' OR '1'='1」→ 200 · 0 主行 · 0 从行`,
      status: safe ? 'pass' : 'fail',
      detail: `HTTP ${r.http} · total=${d.total ?? '?'} · 从行 ${nestedCount(d)} · ${r.elapsed}ms`,
    });
  } else {
    addRow({ id: 'FV2', name: 'SQL 注入防御', expect: 'payload → 200 · 0 主行 0 从行', status: 'skip', detail: '服务未配置过滤参数 —— 无值入口' });
  }

  // FV3 未知参数拒绝 → 400
  {
    const r = await callVerifyQuery(s.slug, { __bogus_param: 'x' });
    const msg = String(r.data?.message ?? r.data?.error ?? '');
    addRow({
      id: 'FV3', name: '未知参数拒绝', expect: '未注册参数 __bogus_param → HTTP 400',
      status: r.http === 400 ? 'pass' : 'fail',
      detail: `HTTP ${r.http} · ${msg.slice(0, 80)}`,
    });
  }

  // FV4 钳制：主行 ≤500 且每主键从行 ≤ limitPerParent
  {
    const r = await callVerifyQuery(s.slug, { limit: 99999 });
    const d = r.data ?? {};
    const mainRows = d.rows?.length ?? 1e9;
    const cap = Math.max(...s.joins.map((j) => j.limitPerParent));
    const maxNested = (d.rows ?? []).reduce(
      (m, row) => Math.max(m, ...s.joins.map((j) => (Array.isArray(row[j.name]) ? row[j.name].length : 0))), 0);
    const ok = r.http === 200 && mainRows <= 500 && maxNested <= cap;
    addRow({
      id: 'FV4', name: 'LIMIT/每主键钳制', expect: `limit=99999 → 主行 ≤500 · 每主键从行 ≤${cap}`,
      status: ok ? 'pass' : 'fail',
      detail: `HTTP ${r.http} · ${mainRows > 1e8 ? '?' : mainRows} 主行 · 单主键最多 ${maxNested} 从行`,
    });
  }

  // FV5 发布白名单闸（融合）：从表列塞注入 payload → 400
  {
    const mainTable = state.tables.find((t) => t.source === s.source && t.table === s.table);
    const j = s.joins[0];
    if (mainTable && j) {
      const probeSlug = `verify-probe-${Date.now()}`;
      const res = await fetch('/api/w5/services', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          slug: probeSlug, name: '验证探针（应被拒）', description: 'verify probe',
          fqn: mainTable.fqn,
          allowedColumns: s.allowedColumns, filters: [], defaultLimit: 5,
          joins: [{ ...j, columns: [j.columns[0], 'cust_id; DROP TABLE orders'] }],
        }),
      });
      let msg = '';
      try { msg = (await res.json()).message ?? ''; } catch { /* ignore */ }
      if (res.status === 201) {
        await fetch(`/api/w5/services/${encodeURIComponent(probeSlug)}`, { method: 'DELETE' });
      }
      addRow({
        id: 'FV5', name: '发布白名单闸', expect: '融合声明塞「cust_id; DROP TABLE orders」列 → HTTP 400',
        status: res.status === 400 ? 'pass' : 'fail',
        detail: `HTTP ${res.status} · ${String(msg).slice(0, 90)}`,
      });
    } else {
      addRow({ id: 'FV5', name: '发布白名单闸', expect: '融合声明塞注入列 → HTTP 400', status: 'skip', detail: '目录中未找到主/从表 FQN（OM 离线？）' });
    }
  }

  // FV6 关联一致性：每个嵌套从行关联键 == 主行关联值，且 total == 主行数
  {
    const params = first ? { [first.column]: testValue } : {};
    const r = await callVerifyQuery(s.slug, { ...params, limit: 20 });
    const d = r.data ?? {};
    let mismatch = 0;
    for (const j of s.joins) {
      for (const row of d.rows ?? []) {
        const pv = row[j.parentColumn];
        for (const child of row[j.name] ?? []) {
          if (String(child[j.joinColumn] ?? '') !== String(pv ?? '')) mismatch++;
        }
      }
    }
    const consistent = r.http === 200 && mismatch === 0 && (d.total ?? -1) === (d.rows ?? []).length;
    addRow({
      id: 'FV6', name: '关联一致性', expect: `每个从行 ${s.joins.map((j) => `${j.joinColumn}==主行${j.parentColumn}`).join(' · ')} · total=主行数`,
      status: consistent ? 'pass' : 'fail',
      detail: `不一致 ${mismatch} 条 · total=${d.total ?? '?'} / ${d.rows?.length ?? '?'} 主行`,
    });
  }

  // FV7 金标用例：customer×orders 种子已知值 C0001 → 张伟 + 2 单
  {
    const ordersJoin = s.joins.find((j) => j.fqn.endsWith('.orders'));
    if (s.table === 'customer' && ordersJoin) {
      const hasCustIdFilter = s.filters.some((f) => f.column === 'cust_id');
      const r = await callVerifyQuery(s.slug, hasCustIdFilter ? { cust_id: 'C0001' } : { limit: 20 });
      const row = (r.data?.rows ?? []).find((x) => x.cust_id === 'C0001');
      const golden = r.http === 200 && row?.cust_name === '张伟' && row?.[ordersJoin.name]?.length === 2;
      addRow({
        id: 'FV7', name: '金标用例', expect: 'C0001 → 张伟 + 2 笔订单（H2 种子已知值）',
        status: golden ? 'pass' : 'fail',
        detail: row ? `${row.cust_name} · ${row[ordersJoin.name]?.length ?? 0} 单` : '未取到 C0001 主行',
      });
    } else {
      addRow({ id: 'FV7', name: '金标用例', expect: '种子已知值比对', status: 'skip', detail: '仅 customer×orders 融合服务带金标（演示种子）' });
    }
  }
}

function verifyTableHtml(rows) {
  const badge = (st) => `<span class="badge v-${st}">${st === 'pass' ? 'PASS' : st === 'fail' ? 'FAIL' : 'SKIP'}</span>`;
  return `
    <table class="params-table">
      <thead><tr><th style="width:46px">用例</th><th style="width:110px">名称</th><th>预期</th><th style="width:44%">结果</th></tr></thead>
      <tbody>
        ${rows.map((r) => `<tr>
          <td><b>${esc(r.id)}</b></td>
          <td>${esc(r.name)}</td>
          <td style="color:var(--text-dim)">${esc(r.expect)}</td>
          <td>${badge(r.status)} <span style="font-size:12px;color:var(--text-dim)">${esc(r.detail)}</span></td>
        </tr>`).join('')}
      </tbody>
    </table>`;
}

// ============================================================
// 初始化
// ============================================================

document.querySelectorAll('.tab-btn').forEach((b) => { b.onclick = () => switchTab(b.dataset.tab); });
$('#catalog-search').oninput = (e) => { state.search = e.target.value; renderCatalog(); };
$('#wiz-table').onchange = onWizardTableChange;
$('#wiz-columns').onchange = () => refreshParentColumnOptions(); // 主表勾选变化 → 联动关联主表列候选
$('#wiz-join-on').onchange = (e) => {
  state.wiz.join.on = e.target.checked;
  $('#wiz-join-block').style.display = e.target.checked ? 'block' : 'none';
  if (e.target.checked) onJoinTableChange();
};
$('#wiz-join-table').onchange = onJoinTableChange;
$('#wiz-add-filter').onclick = () => {
  const col = $('#wiz-filter-col').value;
  const op = $('#wiz-filter-op').value;
  if (!col) return;
  if (state.wiz.filters.some((f) => f.column === col)) { toast('该列已配置过滤', false); return; }
  state.wiz.filters.push({ column: col, op });
  renderWizardFilters();
};
$('#wiz-publish').onclick = publishFromWizard;
$('#verify-slug').onchange = renderVerifyContract;
$('#verify-run').onclick = runVerificationSuite;

renderScenarios();
loadCatalog();
loadServices();
