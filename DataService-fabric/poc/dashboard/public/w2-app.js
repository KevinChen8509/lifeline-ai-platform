// Week 2 dashboard 前端逻辑
//
// 5 个面板：
//   profile   单客户画像（JSON 展示 + 字段解读）
//   customers 客户分群（表格 + 等级筛选）
//   metrics   全局指标（卡片 grid）
//   masking   3 角色并排对比（脱敏字段高亮）
//   audit     审计日志流（时间轴 + 自动刷新）

const W2 = {
  tabBtns: document.querySelectorAll('.tab'),
  week1View: document.getElementById('week1-view'),
  week2View: document.getElementById('week2-view'),
  services: document.getElementById('w2-services'),
  servicesCount: document.getElementById('w2-services-count'),
  endpoints: document.getElementById('w2-endpoints'),
  panel: document.getElementById('w2-panel'),
  title: document.getElementById('w2-title'),
  desc: document.getElementById('w2-desc'),
  runBtn: document.getElementById('w2-run'),
  custIdInput: document.getElementById('w2-custId'),
  viewSelect: document.getElementById('w2-view'),
  currentEndpoint: 'profile',
  auditTimer: null,
};

const ENDPOINT_META = {
  profile: { title: '客户画像 API', desc: '调 /api/v1/customers/{custId}/profile —— 跨源 JOIN 由 Cube 透明下推' },
  customers: { title: '客户分群 API', desc: '调 /api/v1/customers?level=VIP3 —— 按等级过滤 + 分页' },
  metrics: { title: '全局指标 API', desc: '调 /api/v1/metrics/customer-overview —— ARPU / VIP3 / 风险分布' },
  masking: { title: '脱敏契约对比', desc: '同一 custId 用 3 个角色调用，看 phone/idCard/riskScore 的脱敏差异' },
  audit: { title: '审计日志流', desc: '调 /api/v1/audit/recent —— DataMaskingAspect 切入后的访问记录' },
};

// ============================================================
// Tab 切换
// ============================================================
W2.tabBtns.forEach((btn) => {
  btn.addEventListener('click', () => {
    W2.tabBtns.forEach((b) => b.classList.remove('active'));
    btn.classList.add('active');
    const tab = btn.dataset.tab;
    W2.week1View.hidden = tab !== 'week1';
    W2.week2View.hidden = tab !== 'week2';
    if (tab === 'week2') {
      refreshServices();
    } else {
      stopAuditPolling();
    }
  });
});

// ============================================================
// 端点选择
// ============================================================
W2.endpoints.addEventListener('click', (e) => {
  const btn = e.target.closest('.w2-endpoint-btn');
  if (!btn) return;
  document.querySelectorAll('.w2-endpoint-btn').forEach((b) => b.classList.remove('active'));
  btn.classList.add('active');
  W2.currentEndpoint = btn.dataset.endpoint;
  const meta = ENDPOINT_META[W2.currentEndpoint];
  W2.title.textContent = meta.title;
  W2.desc.textContent = meta.desc;

  // audit 端点进入即开始自动刷新
  if (W2.currentEndpoint === 'audit') {
    runCurrent();
    startAuditPolling();
  } else {
    stopAuditPolling();
  }
});

W2.runBtn.addEventListener('click', runCurrent);

// ============================================================
// 服务栈健康
// ============================================================
async function refreshServices() {
  W2.services.innerHTML = '<div class="w2-svc-loading">检测中…</div>';
  try {
    const res = await fetch('/api/w2/health');
    const data = await res.json();
    renderServices(data);
  } catch (err) {
    W2.services.innerHTML = `<div class="w2-svc-down">${err.message}</div>`;
  }
}

function renderServices(data) {
  const { trino, cube, dataService } = data;
  const all = [trino, cube, dataService];
  const ups = all.filter((s) => s.status === 'up').length;
  W2.servicesCount.textContent = `${ups}/3`;

  W2.services.innerHTML = all.map((svc) => `
    <div class="w2-svc-card status-${svc.status}">
      <div class="w2-svc-name">${svc.name}</div>
      <div class="w2-svc-status">${svc.status.toUpperCase()}</div>
      <div class="w2-svc-meta">${svc.url}</div>
      ${svc.http ? `<div class="w2-svc-meta">HTTP ${svc.http} · ${svc.latencyMs}ms</div>` : ''}
      ${svc.error ? `<div class="w2-svc-meta">${svc.error}</div>` : ''}
    </div>
  `).join('');
}

// ============================================================
// 主调度：根据 currentEndpoint 调不同 API
// ============================================================
async function runCurrent() {
  const ep = W2.currentEndpoint;
  W2.panel.innerHTML = '<div class="w2-loading">加载中…</div>';

  try {
    if (ep === 'profile') await runProfile();
    else if (ep === 'customers') await runCustomers();
    else if (ep === 'metrics') await runMetrics();
    else if (ep === 'masking') await runMasking();
    else if (ep === 'audit') await runAudit();
  } catch (err) {
    W2.panel.innerHTML = `<div class="w2-error">${err.message}</div>`;
  }
}

// ============================================================
// 1. 单客户画像
// ============================================================
async function runProfile() {
  const custId = W2.custIdInput.value || 'C0001';
  const view = W2.viewSelect.value;
  const res = await fetch(`/api/w2/profile?custId=${encodeURIComponent(custId)}&view=${view}`);
  const json = await res.json();

  if (!json.ok) {
    W2.panel.innerHTML = renderApiError(json);
    return;
  }
  const d = json.data;
  W2.panel.innerHTML = `
    <div class="w2-api-meta">URL: <code>${json.url}</code> · HTTP ${json.http} · ${json.elapsedMs}ms</div>
    <div class="w2-profile-grid">
      ${renderField('客户 ID', d.custId)}
      ${renderField('姓名', d.custName)}
      ${renderField('手机号', d.phone, isMasked(d.phone))}
      ${renderField('身份证', d.idCard, isMasked(d.idCard))}
      ${renderField('客户等级', d.customerLevel)}
      ${renderField('地区', d.region)}
      ${renderField('订单数', d.totalOrders)}
      ${renderField('累计金额', d.totalAmount != null ? `¥${Number(d.totalAmount).toFixed(2)}` : null)}
      ${renderField('风险等级', d.riskLevel)}
      ${renderField('风险分', d.riskScore, d.riskScore === null, true)}
      ${renderField('注册时间', d.registerTime)}
      ${renderField('最近订单', d.lastOrderTime)}
    </div>
    <details class="w2-raw">
      <summary>原始 JSON</summary>
      <pre>${escapeHtml(JSON.stringify(d, null, 2))}</pre>
    </details>
  `;
}

// ============================================================
// 2. 客户分群
// ============================================================
async function runCustomers() {
  const res = await fetch('/api/w2/customers?level=VIP3&page=0&size=10');
  const json = await res.json();
  if (!json.ok) {
    W2.panel.innerHTML = renderApiError(json);
    return;
  }
  const rows = Array.isArray(json.data) ? json.data : [];
  W2.panel.innerHTML = `
    <div class="w2-api-meta">URL: <code>${json.url}</code> · ${rows.length} 行</div>
    <div class="table-scroll">
      <table class="w2-table">
        <thead><tr>
          <th>客户 ID</th><th>姓名</th><th>等级</th><th>地区</th>
          <th>订单数</th><th>累计金额</th><th>风险等级</th>
        </tr></thead>
        <tbody>
          ${rows.map((r) => `
            <tr>
              <td><code>${r.custId || ''}</code></td>
              <td>${r.custName || ''}</td>
              <td><span class="chip chip-level">${r.customerLevel || ''}</span></td>
              <td>${r.region || ''}</td>
              <td>${r.totalOrders || ''}</td>
              <td>${r.totalAmount != null ? `¥${Number(r.totalAmount).toFixed(0)}` : ''}</td>
              <td>${riskBadge(r.riskLevel)}</td>
            </tr>
          `).join('')}
        </tbody>
      </table>
    </div>
  `;
}

// ============================================================
// 3. 全局指标
// ============================================================
async function runMetrics() {
  const res = await fetch('/api/w2/metrics');
  const json = await res.json();
  if (!json.ok) {
    W2.panel.innerHTML = renderApiError(json);
    return;
  }
  const d = json.data;
  W2.panel.innerHTML = `
    <div class="w2-api-meta">URL: <code>${json.url}</code></div>
    <div class="w2-metrics-grid">
      ${renderMetric('ARPU 客单价', d.arpu != null ? `¥${Number(d.arpu).toFixed(2)}` : '—', 'primary')}
      ${renderMetric('VIP3 客户', d.vipCustomerCount, 'success')}
      ${renderMetric('高风险', d.highRiskCustomerCount, 'danger')}
      ${renderMetric('中风险', d.mediumRiskCustomerCount, 'warn')}
      ${renderMetric('低风险', d.lowRiskCustomerCount, 'info')}
    </div>
  `;
}

// ============================================================
// 4. 脱敏对比
// ============================================================
async function runMasking() {
  const custId = W2.custIdInput.value || 'C0001';
  const view = W2.viewSelect.value;
  const res = await fetch(`/api/w2/masking-compare?custId=${encodeURIComponent(custId)}&view=${view}`);
  const json = await res.json();

  W2.panel.innerHTML = `
    <div class="w2-api-meta">同一客户 ${escapeHtml(custId)}（view=${view}）× 3 角色</div>
    <div class="w2-masking-grid">
      ${json.comparisons.map((c) => renderMaskColumn(c)).join('')}
    </div>
  `;
}

function renderMaskColumn(c) {
  const roleClass = `role-${(c.role || 'anonymous').toLowerCase()}`;
  if (!c.ok) {
    return `<div class="w2-mask-col ${roleClass}">
      <div class="w2-mask-role">${c.role || 'ANONYMOUS'}</div>
      <div class="w2-mask-err">${c.error || `HTTP ${c.http}`}</div>
    </div>`;
  }
  const d = c.data;
  return `<div class="w2-mask-col ${roleClass}">
    <div class="w2-mask-role">${c.role}</div>
    <div class="w2-mask-row ${isMasked(d.phone) ? 'masked' : 'clear'}">
      <span class="w2-mask-label">phone</span>
      <span class="w2-mask-val">${escapeHtml(String(d.phone))}</span>
    </div>
    <div class="w2-mask-row ${isMasked(d.idCard) ? 'masked' : 'clear'}">
      <span class="w2-mask-label">idCard</span>
      <span class="w2-mask-val">${escapeHtml(String(d.idCard))}</span>
    </div>
    <div class="w2-mask-row ${d.riskScore === null ? 'masked' : 'clear'}">
      <span class="w2-mask-label">riskScore</span>
      <span class="w2-mask-val">${d.riskScore === null ? '<i>null（隐藏）</i>' : escapeHtml(String(d.riskScore))}</span>
    </div>
    <div class="w2-mask-row">
      <span class="w2-mask-label">custName</span>
      <span class="w2-mask-val">${escapeHtml(String(d.custName))}</span>
    </div>
    <div class="w2-mask-row">
      <span class="w2-mask-label">level</span>
      <span class="w2-mask-val">${escapeHtml(String(d.customerLevel))}</span>
    </div>
  </div>`;
}

// ============================================================
// 5. 审计日志流
// ============================================================
async function runAudit() {
  const res = await fetch('/api/w2/audit?n=30');
  const json = await res.json();
  if (!json.ok) {
    W2.panel.innerHTML = renderApiError(json);
    return;
  }
  const events = json.data?.events || [];
  W2.panel.innerHTML = `
    <div class="w2-api-meta">审计 ring buffer 当前 ${json.data?.total || 0} 条 · 显示最近 ${events.length}</div>
    <ul class="w2-audit-list">
      ${events.map((e) => `
        <li class="w2-audit-item role-${(e.actor || 'anonymous').toLowerCase()}">
          <span class="w2-audit-ts">${formatTs(e.timestamp)}</span>
          <span class="w2-audit-actor chip">${e.actor || 'ANONYMOUS'}</span>
          <span class="w2-audit-action">${escapeHtml(e.action)}</span>
          <span class="w2-audit-resource"><code>${escapeHtml(e.resource || '')}</code></span>
          ${e.riskLevel ? `<span class="w2-audit-risk">${riskBadge(e.riskLevel)}</span>` : ''}
          <span class="w2-audit-result chip-${e.result === 'SUCCESS' ? 'success' : 'fail'}">${e.result}</span>
        </li>
      `).join('')}
    </ul>
  `;
}

function startAuditPolling() {
  stopAuditPolling();
  W2.auditTimer = setInterval(runAudit, 5000);
}

function stopAuditPolling() {
  if (W2.auditTimer) {
    clearInterval(W2.auditTimer);
    W2.auditTimer = null;
  }
}

// ============================================================
// 渲染辅助
// ============================================================
function renderField(label, val, masked = false, hidden = false) {
  const cls = masked ? 'field-masked' : (hidden ? 'field-hidden' : '');
  return `<div class="w2-field ${cls}">
    <div class="w2-field-label">${label}</div>
    <div class="w2-field-val">${val === null || val === undefined ? '<i>null</i>' : escapeHtml(String(val))}</div>
  </div>`;
}

function renderMetric(label, val, variant) {
  return `<div class="w2-metric-card metric-${variant}">
    <div class="w2-metric-label">${label}</div>
    <div class="w2-metric-val">${val}</div>
  </div>`;
}

function renderApiError(json) {
  return `<div class="w2-error-block">
    <div class="w2-error-title">调用失败</div>
    <div class="w2-error-detail">${escapeHtml(json.error || JSON.stringify(json.data || {}))}</div>
    ${json.url ? `<div class="w2-api-meta">URL: <code>${json.url}</code></div>` : ''}
  </div>`;
}

function riskBadge(level) {
  if (!level) return '';
  const cls = { high: 'risk-high', medium: 'risk-medium', low: 'risk-low' }[level] || 'risk-unknown';
  return `<span class="chip ${cls}">${level}</span>`;
}

function isMasked(s) {
  if (!s) return false;
  return /\*/.test(String(s));
}

function formatTs(iso) {
  try {
    const d = new Date(iso);
    return d.toLocaleTimeString('zh-CN', { hour12: false });
  } catch {
    return iso;
  }
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  }[c]));
}

// ============================================================
// 启动
// ============================================================
// 进入 Week 2 tab 才拉服务栈健康，避免 Week 1 时多余请求
console.log('[W2 dashboard] loaded · 5 endpoints ready');
