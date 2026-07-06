/* ============================================================
 * FIX-ING Demo v0.3 · 前端逻辑（原生 JS，无框架）
 *
 * v0.3 核心：按登录角色渲染完全不同的工作台（TABS_BY_ROLE）——
 * 无关模块直接不渲染。但真正的数据隔离/权限在后端：
 * 工程师调 /tickets 后端只回自己的单，前端只是"照着画"。
 * ============================================================ */

'use strict';

// ── 全局状态 ──
let currentUser = null;   // /auth/me 的结果（含 role、customerId、serviceNotice）
let users = [];           // 人名解析用（时间线/派单下拉）
let customers = [];       // 管理端/工程师端需要
let equipments = [];      // 报修选设备、名称解析
let parts = [];           // 备件（工程师/管理员）
let uploadedPhotos = [];  // 报修表单已上传的附件 URL

const STATUS_META = {
  PENDING_ASSIGN:  '待派单',
  ASSIGNED:        '已派单',
  IN_PROGRESS:     '处理中',
  PENDING_CONFIRM: '待确认',
  COMPLETED:       '已完成',
  CANCELLED:       '已取消',
};
const ROLE_CN = { CUSTOMER: '客户', ADMIN: '管理员', ENGINEER: '工程师' };

/**
 * 角色 → 页签配置：每个角色只看到自己的工作台（v0.3 的核心需求）。
 * panel 对应 index.html 里的 section id，load 是切进来时的刷新函数。
 */
const TABS_BY_ROLE = {
  CUSTOMER: [
    { panel: 'create',     label: '➕ 我要报修',  load: () => {} },
    { panel: 'my-tickets', label: '📋 我的工单',  load: loadMyTickets },
  ],
  ENGINEER: [
    { panel: 'board',      label: '🔧 我的工单',  load: loadBoard },
    { panel: 'inventory',  label: '📦 备件库存',  load: loadInventory },
  ],
  ADMIN: [
    { panel: 'dashboard',  label: '📊 数据看板',  load: loadDashboard },
    { panel: 'board',      label: '📋 工单管理',  load: loadBoard },
    { panel: 'inventory',  label: '📦 库存管理',  load: loadInventory },
    { panel: 'ledger',     label: '🏥 客户与设备', load: loadLedger },
    { panel: 'contracts',  label: '📄 合同管理',  load: loadContracts },
    { panel: 'invoices',   label: '🧾 发票管理',  load: loadInvoices },
  ],
};

// ════════════════════════ 基础工具 ════════════════════════

/** 统一 API 封装：自动带 JWT；401 → 踢回登录页；code!=0 → 抛 message */
async function api(path, method = 'GET', body = null) {
  const opts = { method, headers: { 'Content-Type': 'application/json' } };
  const token = localStorage.getItem('fixing_token');
  if (token) opts.headers['Authorization'] = 'Bearer ' + token;
  if (body) opts.body = JSON.stringify(body);
  const res = await fetch(path, opts);
  if (res.status === 401) {
    localStorage.removeItem('fixing_token');
    location.href = '/login.html';
    throw new Error('登录已过期');
  }
  const json = await res.json();
  if (json.code !== 0) throw new Error(json.message);
  return json.data;
}

/** 文件上传：multipart 表单，注意不能手动设 Content-Type（浏览器要自己拼 boundary） */
async function uploadFile(file) {
  const fd = new FormData();
  fd.append('file', file);
  const res = await fetch('/files', {
    method: 'POST',
    headers: { 'Authorization': 'Bearer ' + localStorage.getItem('fixing_token') },
    body: fd,
  });
  const json = await res.json();
  if (json.code !== 0) throw new Error(json.message);
  return json.data.url;
}

async function logout() {
  try { await api('/auth/logout', 'POST'); } catch (e) { /* 忽略 */ }
  localStorage.removeItem('fixing_token');
  location.href = '/login.html';
}

function esc(s) {
  return String(s ?? '').replace(/[&<>"']/g,
    c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}
function toast(msg, error = false) {
  const el = document.getElementById('toast');
  el.textContent = msg;
  el.className = 'toast' + (error ? ' error' : '');
  clearTimeout(el._timer);
  el._timer = setTimeout(() => el.classList.add('hidden'), 3000);
}
function fmtTime(t) { return t ? t.replace('T', ' ').slice(0, 16) : '—'; }
function userName(id) { const u = users.find(u => u.id === id); return u ? u.realName : `用户#${id}`; }
function customerName(id) { const c = customers.find(c => c.id === id); return c ? c.name : `客户#${id}`; }
function equipmentName(id) {
  if (!id) return '—';
  const e = equipments.find(e => e.id === id);
  return e ? `${e.equipmentType} ${e.model}（${e.location}）` : `设备#${id}`;
}
function partName(id) { const p = parts.find(p => p.id === id); return p ? p.name : `备件#${id}`; }
function priorityColor(p) {
  return { P0: '#dc2626', P1: '#ea580c', P2: '#d97706', P3: '#2563eb', P4: '#6b7280' }[p] || '#d0d7de';
}

// ════════════════════════ 初始化 ════════════════════════

async function init() {
  if (!localStorage.getItem('fixing_token')) {
    location.href = '/login.html';
    return;
  }

  // 先问"我是谁"，其余数据按角色决定拉哪些（客户没权限看备件，别去撞 403）
  const me = await api('/auth/me');
  currentUser = { ...me, id: me.userId };

  const fetches = [api('/users'), api('/equipments')]; // 人名/设备名全角色都要
  if (currentUser.role !== 'CUSTOMER') {
    fetches.push(api('/customers'), api('/spare-parts'));
  }
  const results = await Promise.all(fetches);
  [users, equipments] = results;
  if (currentUser.role !== 'CUSTOMER') { customers = results[2]; parts = results[3]; }

  document.getElementById('whoami').textContent =
    `👤 ${currentUser.realName} · ${ROLE_CN[currentUser.role]}`;

  renderTabs();
  handleServiceNotice(); // 客户：合同到期弹窗/横幅
  initCreateForm();
}

/** 按角色生成页签，并激活第一个 */
function renderTabs() {
  const tabs = TABS_BY_ROLE[currentUser.role];
  const nav = document.getElementById('tabsNav');
  nav.innerHTML = tabs.map((t, i) =>
    `<button class="tab${i === 0 ? ' active' : ''}" data-panel="${t.panel}">${t.label}</button>`).join('');

  nav.querySelectorAll('.tab').forEach(btn => {
    btn.onclick = () => {
      nav.querySelectorAll('.tab').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      document.querySelectorAll('.panel').forEach(p => p.classList.add('hidden'));
      document.getElementById('panel-' + btn.dataset.panel).classList.remove('hidden');
      tabs.find(t => t.panel === btn.dataset.panel).load();
    };
  });

  // 默认进第一个页签
  document.getElementById('panel-' + tabs[0].panel).classList.remove('hidden');
  tabs[0].load();
}

/**
 * 客户服务状态提示（登录响应里带的 serviceNotice）：
 * EXPIRED  → 全屏弹窗 + 禁用报修提交按钮（后端也会硬拦，这里是体验）
 * EXPIRING → 顶部黄色横幅倒计时
 */
function handleServiceNotice() {
  const n = currentUser.serviceNotice;
  if (!n || n.level === 'OK') return;
  if (n.level === 'EXPIRED') {
    document.getElementById('expiredMsg').textContent = n.message;
    document.getElementById('expiredMask').classList.remove('hidden');
    const btn = document.getElementById('createSubmit');
    btn.disabled = true;
    btn.textContent = '服务已到期，无法报修';
  } else if (n.level === 'EXPIRING') {
    const banner = document.getElementById('serviceBanner');
    banner.textContent = '⏰ ' + n.message;
    banner.classList.remove('hidden');
  }
}

// ════════════════════════ 看板（管理员全量 / 工程师只有自己的单，后端圈定） ════════════════════════

async function loadBoard() {
  const tickets = await api('/tickets');
  const byStatus = {};
  Object.keys(STATUS_META).forEach(s => byStatus[s] = []);
  tickets.forEach(t => (byStatus[t.status] ?? []).push(t));

  document.getElementById('boardSummary').textContent = currentUser.role === 'ENGINEER'
    ? `派给我的工单共 ${tickets.length} 张`
    : `全平台工单共 ${tickets.length} 张`;

  document.getElementById('board').innerHTML = Object.entries(STATUS_META).map(([status, label]) => `
    <div class="board-col">
      <h3>${label}<span class="col-count">${byStatus[status].length}</span></h3>
      ${byStatus[status].map(t => `
        <div class="ticket-card" style="border-left-color:${priorityColor(t.priority)}"
             onclick="openTicket(${t.id})">
          <div class="ticket-no">${esc(t.ticketNo)} <span class="badge ${t.priority}">${t.priority}</span></div>
          <div class="ticket-title">${esc(t.title)}</div>
          <div class="ticket-no">${t.assignedEngineerId ? '👤 ' + esc(userName(t.assignedEngineerId)) : ''}</div>
        </div>`).join('')}
    </div>`).join('');
}

// ════════════════════════ 工单详情弹窗（三端共用，按钮按角色渲染） ════════════════════════

async function openTicket(id) {
  const [t, logs] = await Promise.all([api(`/tickets/${id}`), api(`/tickets/${id}/logs`)]);

  document.getElementById('mTitle').innerHTML =
    `${esc(t.ticketNo)} <span class="badge ${t.priority}">${t.priority}</span>`;

  // 附件：图片直接缩略，视频给 <video> 控件（报修必传的故障证据）
  const photosHtml = (t.photos && t.photos.length) ? `
    <div class="ticket-photos">
      ${t.photos.map(url => /\.(mp4|mov|webm)$/i.test(url)
        ? `<video src="${esc(url)}" controls muted></video>`
        : `<img src="${esc(url)}" onclick="window.open('${esc(url)}')" alt="故障图片">`).join('')}
    </div>` : '';

  document.getElementById('mBody').innerHTML = `
    <div class="detail-grid">
      <div><b>标题</b>${esc(t.title)}</div>
      <div><b>状态</b>${STATUS_META[t.status]}</div>
      <div><b>类型</b>${t.type === 'HARDWARE' ? '硬件' : '软件'}</div>
      <div><b>客户</b>${currentUser.role === 'CUSTOMER' ? '本单位' : esc(customerName(t.customerId))}</div>
      <div><b>设备</b>${esc(equipmentName(t.equipmentId))}</div>
      <div><b>工程师</b>${t.assignedEngineerId ? esc(userName(t.assignedEngineerId)) : '未派单'}</div>
      <div><b>联系人</b>${esc(t.contactName ?? '—')} ${esc(t.contactPhone ?? '')}</div>
      <div><b>创建时间</b>${fmtTime(t.createdAt)}</div>
    </div>
    ${t.description ? `<div style="margin-bottom:8px"><b style="color:#57606a">故障描述：</b>${esc(t.description)}</div>` : ''}
    ${photosHtml}
    <h3 style="font-size:14px">流转时间线</h3>
    <div class="timeline">
      ${logs.map(l => `
        <div class="timeline-item">
          <span class="timeline-time">${fmtTime(l.createdAt)}</span>
          <b>${esc(userName(l.operatorId))}</b> ${actionLabel(l.action)}
          ${l.fromStatus ? `（${STATUS_META[l.fromStatus]} → ${STATUS_META[l.toStatus]}）` : ''}
          ${l.remark ? `<span class="timeline-remark">💬 ${esc(l.remark)}</span>` : ''}
        </div>`).join('')}
    </div>
    <div class="action-bar" id="actionBar">${renderActions(t)}</div>`;

  document.getElementById('modalMask').classList.remove('hidden');
}

function actionLabel(a) {
  return { create: '提交报修', assign: '派单', reassign: '改派', accept: '接单',
           complete: '完工提交', confirm: '确认完工', reject: '驳回',
           cancel: '取消', use_part: '领用备件' }[a] || a;
}
function closeModal() { document.getElementById('modalMask').classList.add('hidden'); }

/** 动作按钮矩阵："身份 × 状态"（与后端规则一致，后端才是真校验） */
function renderActions(t) {
  const role = currentUser.role;
  const isAssignedEngineer = role === 'ENGINEER' && t.assignedEngineerId === currentUser.id;
  const engineers = users.filter(u => u.role === 'ENGINEER');
  const engineerOpts = engineers.map(e => `<option value="${e.id}">${esc(e.realName)}</option>`).join('');
  const partOpts = parts.map(p =>
    `<option value="${p.id}">${esc(p.name)}（库存${p.stockQty}）</option>`).join('');
  const html = [];

  if (t.status === 'PENDING_ASSIGN') {
    if (role === 'ADMIN') {
      html.push(`<span class="inline-form">
        <select id="aEngineer">${engineerOpts}</select>
        <button class="btn btn-primary" onclick="doAssign(${t.id}, 'assign')">派单</button></span>`);
    }
    if (role === 'ADMIN' || role === 'CUSTOMER') {
      html.push(`<span class="inline-form">
        <input class="remark" id="aCancelReason" placeholder="取消原因(可选)">
        <button class="btn btn-danger" onclick="doAction(${t.id}, 'cancel', 'aCancelReason')">取消工单</button></span>`);
    }
  }

  if (t.status === 'ASSIGNED') {
    if (isAssignedEngineer) {
      html.push(`<button class="btn btn-success" onclick="doAction(${t.id}, 'accept')">接单</button>`);
    }
    if (role === 'ADMIN') {
      html.push(`<span class="inline-form">
        <select id="aEngineer">${engineerOpts}</select>
        <input class="remark" id="aReassignReason" placeholder="改派原因">
        <button class="btn" onclick="doAssign(${t.id}, 'reassign')">改派</button></span>`);
      html.push(`<span class="inline-form">
        <input class="remark" id="aCancelReason" placeholder="取消原因(可选)">
        <button class="btn btn-danger" onclick="doAction(${t.id}, 'cancel', 'aCancelReason')">取消工单</button></span>`);
    }
  }

  if (t.status === 'IN_PROGRESS') {
    if (isAssignedEngineer) {
      html.push(`<span class="inline-form">
        <select id="aPart">${partOpts}</select>
        <input class="qty" id="aQty" type="number" value="1" min="1">
        <button class="btn" onclick="doUsePart(${t.id})">🔩 换件</button></span>`);
      html.push(`<span class="inline-form">
        <input class="remark" id="aCompleteRemark" placeholder="处理说明">
        <button class="btn btn-success" onclick="doAction(${t.id}, 'complete', 'aCompleteRemark')">完工提交</button></span>`);
    }
    if (role === 'ADMIN') {
      html.push(`<span class="inline-form">
        <select id="aEngineer">${engineerOpts}</select>
        <input class="remark" id="aReassignReason" placeholder="改派原因">
        <button class="btn" onclick="doAssign(${t.id}, 'reassign')">改派</button></span>`);
    }
  }

  if (t.status === 'PENDING_CONFIRM' && role === 'CUSTOMER') {
    html.push(`<button class="btn btn-success" onclick="doAction(${t.id}, 'confirm')">✅ 确认完工</button>`);
    html.push(`<span class="inline-form">
      <input class="remark" id="aRejectReason" placeholder="驳回原因">
      <button class="btn btn-danger" onclick="doAction(${t.id}, 'reject', 'aRejectReason')">驳回（没修好）</button></span>`);
  }

  return html.length ? html.join('')
    : `<span style="color:#8b949e">当前身份在此状态下无可用操作</span>`;
}

/** 动作后的统一刷新：详情 + 当前角色的列表视图 */
async function refreshAfterAction(ticketId) {
  await openTicket(ticketId);
  if (currentUser.role === 'CUSTOMER') loadMyTickets(); else loadBoard();
}

async function doAction(ticketId, action, remarkInputId = null) {
  const remark = remarkInputId ? document.getElementById(remarkInputId)?.value : null;
  try {
    await api(`/tickets/${ticketId}/${action}`, 'POST', { remark: remark || null });
    toast(`${actionLabel(action)} 成功`);
    await refreshAfterAction(ticketId);
  } catch (e) { toast(e.message, true); }
}

async function doAssign(ticketId, action) {
  const engineerId = Number(document.getElementById('aEngineer').value);
  const remark = document.getElementById('aReassignReason')?.value;
  try {
    await api(`/tickets/${ticketId}/${action}`, 'POST', { engineerId, remark: remark || null });
    toast(`${actionLabel(action)} 成功`);
    await refreshAfterAction(ticketId);
  } catch (e) { toast(e.message, true); }
}

async function doUsePart(ticketId) {
  const partId = Number(document.getElementById('aPart').value);
  const qty = Number(document.getElementById('aQty').value);
  try {
    await api(`/tickets/${ticketId}/use-part`, 'POST', { partId, qty });
    toast('换件成功，已扣库存并记录领料流水');
    parts = await api('/spare-parts');
    await openTicket(ticketId);
  } catch (e) { toast(e.message, true); }
}

// ════════════════════════ 客户端：报修（必传附件） ════════════════════════

function initCreateForm() {
  const isAdmin = currentUser.role === 'ADMIN';
  const cSel = document.getElementById('fCustomer');
  const eSel = document.getElementById('fEquipment');

  // 管理员代录单：显示客户选择，设备随客户联动；客户报修：设备就是自己单位的（后端已过滤）
  if (isAdmin) {
    document.getElementById('rowCustomer').classList.remove('hidden');
    cSel.innerHTML = customers.map(c => `<option value="${c.id}">${esc(c.name)}</option>`).join('');
    const refresh = () => {
      const cid = Number(cSel.value);
      const list = equipments.filter(e => e.customerId === cid);
      eSel.innerHTML = '<option value="">— 不关联设备 —</option>' +
        list.map(e => `<option value="${e.id}">${esc(e.equipmentType)} ${esc(e.model)}（${esc(e.location)}）</option>`).join('');
    };
    cSel.onchange = refresh;
    refresh();
  } else {
    eSel.innerHTML = '<option value="">— 不关联设备 —</option>' +
      equipments.map(e => `<option value="${e.id}">${esc(e.equipmentType)} ${esc(e.model)}（${esc(e.location)}）</option>`).join('');
  }

  // 选了文件立即上传并出预览（提交时只传 URL，避免大文件拖慢表单提交）
  document.getElementById('fFiles').onchange = async (ev) => {
    const preview = document.getElementById('filePreview');
    for (const file of ev.target.files) {
      try {
        const url = await uploadFile(file);
        uploadedPhotos.push(url);
        preview.insertAdjacentHTML('beforeend', /\.(mp4|mov|webm)$/i.test(url)
          ? `<video src="${url}" muted></video>`
          : `<img src="${url}" alt="预览">`);
      } catch (e) { toast(`${file.name}: ${e.message}`, true); }
    }
    ev.target.value = ''; // 清空以便再次选同一文件
  };

  document.getElementById('createForm').onsubmit = async (ev) => {
    ev.preventDefault();
    // 客户报修必传故障图/视频（后端还有一道硬校验）
    if (currentUser.role === 'CUSTOMER' && uploadedPhotos.length === 0) {
      toast('请先上传机器或软件异常的图片/视频', true);
      return;
    }
    try {
      const t = await api('/tickets', 'POST', {
        customerId: isAdmin ? Number(cSel.value) : null, // 客户角色后端强制用登录单位
        equipmentId: eSel.value ? Number(eSel.value) : null,
        type: document.getElementById('fType').value,
        title: document.getElementById('fTitle').value,
        description: document.getElementById('fDesc').value || null,
        photos: uploadedPhotos,
        contactName: document.getElementById('fContactName').value || null,
        contactPhone: document.getElementById('fContactPhone').value || null,
      });
      toast(`报修成功！工单号 ${t.ticketNo}，系统判定优先级 ${t.priority}`);
      ev.target.reset();
      uploadedPhotos = [];
      document.getElementById('filePreview').innerHTML = '';
      // 跳到"我的工单/工单管理"看新单
      const target = currentUser.role === 'CUSTOMER' ? 'my-tickets' : 'board';
      document.querySelector(`.tab[data-panel="${target}"]`)?.click();
    } catch (e) { toast(e.message, true); }
  };
}

// ════════════════════════ 客户端：我的工单 ════════════════════════

async function loadMyTickets() {
  const tickets = await api('/tickets'); // 后端已按登录客户过滤
  document.querySelector('#myTicketsTable tbody').innerHTML = tickets.length
    ? tickets.map(t => `
      <tr style="cursor:pointer" onclick="openTicket(${t.id})">
        <td>${esc(t.ticketNo)}</td><td>${esc(t.title)}</td>
        <td><span class="badge ${t.priority}">${t.priority}</span></td>
        <td><span class="status-tag ${t.status}">${STATUS_META[t.status]}</span></td>
        <td>${fmtTime(t.createdAt)}</td>
      </tr>`).join('')
    : '<tr><td colspan="5" style="color:#8b949e">还没有报修记录</td></tr>';
}

// ════════════════════════ 工程师/管理端：库存 ════════════════════════

async function loadInventory() {
  parts = await api('/spare-parts');
  const [lowStock, usages] = await Promise.all([
    api('/spare-parts/low-stock'),
    api('/part-usages'), // 工程师后端强制只回自己的领料
  ]);
  const catCn = { PART: '配件', COMPONENT: '部件', CONSUMABLE: '耗材' };

  // 低库存预警条（工程师出门前一眼看到缺什么）
  const alertEl = document.getElementById('lowStockAlert');
  if (lowStock.length) {
    alertEl.innerHTML = `🚨 <b>低库存预警：</b>` + lowStock.map(p =>
      `${esc(p.name)}（剩 ${p.stockQty}，阈值 ${p.lowStockThreshold}）`).join('、') +
      (currentUser.role === 'ADMIN' ? ' —— 请安排采购补货' : ' —— 领用前请确认余量');
    alertEl.classList.remove('hidden');
  } else {
    alertEl.classList.add('hidden');
  }

  document.getElementById('usageTitle').textContent =
    currentUser.role === 'ENGINEER' ? '我的领料记录' : '领料流水（全部工程师）';

  document.querySelector('#partsTable tbody').innerHTML = parts.map(p => {
    const low = p.stockQty < p.lowStockThreshold;
    return `<tr>
      <td>${p.id}</td><td>${esc(p.name)}</td><td>${catCn[p.category] ?? esc(p.category)}</td>
      <td${low ? ' style="color:#dc2626;font-weight:700"' : ''}>${p.stockQty}${low ? ' ⚠️' : ''}</td>
      <td>${p.lowStockThreshold}</td><td>${p.unitPrice}</td>
    </tr>`;
  }).join('');

  document.querySelector('#usageTable tbody').innerHTML = usages.length
    ? usages.map(u => `
      <tr>
        <td>${fmtTime(u.createdAt)}</td><td>${esc(partName(u.partId))}</td><td>×${u.qty}</td>
        <td>${esc(userName(u.engineerId))}</td><td>工单#${u.ticketId}</td>
      </tr>`).join('')
    : '<tr><td colspan="5" style="color:#8b949e">暂无领料记录</td></tr>';
}

// ════════════════════════ 管理端：Dashboard ════════════════════════

async function loadDashboard() {
  const d = await api('/dashboard/summary');
  const totalTickets = Object.values(d.ticketByStatus).reduce((a, b) => a + b, 0);
  const open = (d.ticketByStatus.PENDING_ASSIGN ?? 0) + (d.ticketByStatus.ASSIGNED ?? 0)
             + (d.ticketByStatus.IN_PROGRESS ?? 0) + (d.ticketByStatus.PENDING_CONFIRM ?? 0);

  // 顶部指标卡
  document.getElementById('dashCards').innerHTML = `
    <div class="dash-card"><div class="lbl">工单总数 / 进行中</div><div class="num">${totalTickets} / ${open}</div></div>
    <div class="dash-card ${d.lowStockParts.length ? 'warn' : ''}"><div class="lbl">低库存备件</div><div class="num">${d.lowStockParts.length}</div></div>
    <div class="dash-card ${d.expiringContracts.length ? 'warn' : ''}"><div class="lbl">7天内到期合同</div><div class="num">${d.expiringContracts.length}</div></div>
    <div class="dash-card ${d.unpaidInvoiceCount ? 'warn' : ''}"><div class="lbl">待回款</div><div class="num">¥${Number(d.unpaidInvoiceAmount).toLocaleString()}</div></div>`;

  // 状态分布横条图（纯 CSS，宽度按占比）
  const max = Math.max(1, ...Object.values(d.ticketByStatus));
  document.getElementById('dashStatus').innerHTML = Object.entries(STATUS_META).map(([s, label]) => {
    const n = d.ticketByStatus[s] ?? 0;
    return `<div class="dist-row">
      <span class="dist-label">${label}</span>
      <div class="dist-bar" style="width:${n / max * 70}%;background:${s === 'COMPLETED' ? '#16a34a' : s === 'CANCELLED' ? '#9ca3af' : '#2563eb'}"></div>
      <span class="dist-count">${n}</span>
    </div>`;
  }).join('');

  document.querySelector('#dashWorkload tbody').innerHTML = d.engineerWorkload.map(w =>
    `<tr><td>${esc(w.realName)}</td><td><b>${w.openCount}</b> 张在手</td></tr>`).join('');

  document.querySelector('#dashLowStock tbody').innerHTML = d.lowStockParts.length
    ? d.lowStockParts.map(p =>
      `<tr><td>${esc(p.name)}</td><td style="color:#dc2626">剩 ${p.stockQty}（阈值 ${p.lowStockThreshold}）</td></tr>`).join('')
    : '<tr><td style="color:#8b949e">库存健康 ✅</td></tr>';

  document.querySelector('#dashExpiring tbody').innerHTML = d.expiringContracts.length
    ? d.expiringContracts.map(c =>
      `<tr><td>${esc(customerName(c.customerId))}</td><td>${esc(c.name)}</td><td style="color:#d97706">${c.endDate} 到期</td></tr>`).join('')
    : '<tr><td style="color:#8b949e">近期无到期合同 ✅</td></tr>';
}

// ════════════════════════ 管理端：台账 ════════════════════════

async function loadLedger() {
  [customers, equipments] = await Promise.all([api('/customers'), api('/equipments')]);

  document.querySelector('#customerTable tbody').innerHTML = customers.map(c => `
    <tr>
      <td>${c.id}</td><td>${esc(c.name)}</td><td>${esc(c.customerType)}</td>
      <td>${esc(c.contactName ?? '')}</td><td>${esc(c.contactPhone ?? '')}</td><td>${esc(c.address ?? '')}</td>
    </tr>`).join('');

  document.querySelector('#equipmentTable tbody').innerHTML = equipments.map(e => `
    <tr style="cursor:pointer" onclick="showEquipmentHistory(${e.id})">
      <td>${e.id}</td><td>${esc(customerName(e.customerId))}</td><td>${esc(e.equipmentType)}</td>
      <td>${esc(e.model ?? '')}</td><td>${esc(e.serialNo)}</td><td>${esc(e.location ?? '')}</td><td>${esc(e.status)}</td>
    </tr>`).join('');
}

async function showEquipmentHistory(equipmentId) {
  const tickets = await api(`/equipments/${equipmentId}/tickets`);
  document.getElementById('equipmentHistory').innerHTML = `
    <h2 style="font-size:14px;margin:14px 0 8px">🔍 ${esc(equipmentName(equipmentId))} 的维修历史（${tickets.length} 条）</h2>
    ${tickets.length ? `<table>
      <thead><tr><th>工单号</th><th>标题</th><th>优先级</th><th>状态</th><th>创建时间</th></tr></thead>
      <tbody>${tickets.map(t => `
        <tr style="cursor:pointer" onclick="openTicket(${t.id})">
          <td>${esc(t.ticketNo)}</td><td>${esc(t.title)}</td>
          <td><span class="badge ${t.priority}">${t.priority}</span></td>
          <td>${STATUS_META[t.status]}</td><td>${fmtTime(t.createdAt)}</td>
        </tr>`).join('')}</tbody>
    </table>` : '<p style="color:#8b949e">该设备暂无维修记录</p>'}`;
}

// ════════════════════════ 管理端：合同 ════════════════════════

async function loadContracts() {
  const contracts = await api('/contracts');
  const billingCn = { YEARLY: '年费', PER_CASE: '按次', MIXED: '年费+按次' };
  const today = new Date().toISOString().slice(0, 10);
  const soon = new Date(Date.now() + 7 * 86400_000).toISOString().slice(0, 10);

  // 新建表单的客户下拉
  document.getElementById('cCustomer').innerHTML =
    customers.map(c => `<option value="${c.id}">${esc(c.name)}</option>`).join('');

  document.querySelector('#contractTable tbody').innerHTML = contracts.map(c => {
    // 到期状态标注：已过期红 / 7 天内黄 / 正常绿
    let tag;
    if (c.status === 'TERMINATED') tag = '<span class="status-tag CANCELLED">已终止</span>';
    else if (c.endDate < today) tag = '<span class="status-tag" style="background:#fee2e2;color:#991b1b">已到期</span>';
    else if (c.endDate <= soon) tag = '<span class="status-tag PENDING_CONFIRM">即将到期</span>';
    else tag = '<span class="status-tag COMPLETED">生效中</span>';
    return `<tr>
      <td>${esc(customerName(c.customerId))}</td><td>${esc(c.name)}</td><td>${esc(c.scope ?? '')}</td>
      <td>${c.startDate} ~ ${c.endDate}</td><td>${billingCn[c.billingType] ?? c.billingType}</td>
      <td>¥${Number(c.amount).toLocaleString()}</td><td>${tag}</td>
      <td>${c.status === 'ACTIVE' ? `<button class="btn btn-danger" onclick="terminateContract(${c.id})">终止</button>` : ''}</td>
    </tr>`;
  }).join('');

  document.getElementById('contractForm').onsubmit = async (ev) => {
    ev.preventDefault();
    try {
      await api('/contracts', 'POST', {
        customerId: Number(document.getElementById('cCustomer').value),
        name: document.getElementById('cName').value,
        scope: document.getElementById('cScope').value || null,
        startDate: document.getElementById('cStart').value,
        endDate: document.getElementById('cEnd').value,
        billingType: document.getElementById('cBilling').value,
        amount: Number(document.getElementById('cAmount').value || 0),
      });
      toast('合同已保存');
      ev.target.reset();
      loadContracts();
    } catch (e) { toast(e.message, true); }
  };
}

async function terminateContract(id) {
  try {
    await api(`/contracts/${id}/terminate`, 'POST');
    toast('合同已终止');
    loadContracts();
  } catch (e) { toast(e.message, true); }
}

// ════════════════════════ 管理端：发票 ════════════════════════

async function loadInvoices() {
  const invoices = await api('/invoices');

  document.getElementById('iCustomer').innerHTML =
    customers.map(c => `<option value="${c.id}">${esc(c.name)}</option>`).join('');

  document.querySelector('#invoiceTable tbody').innerHTML = invoices.length
    ? invoices.map(i => `
      <tr>
        <td>${esc(i.invoiceNo)}</td><td>${esc(customerName(i.customerId))}</td><td>${esc(i.title)}</td>
        <td>¥${Number(i.amount).toLocaleString()}</td>
        <td>${i.status === 'PAID'
            ? '<span class="status-tag COMPLETED">已回款</span>'
            : '<span class="status-tag PENDING_CONFIRM">待回款</span>'}</td>
        <td>${i.issuedAt ?? '—'}</td><td>${i.paidAt ?? '—'}</td>
        <td>${i.status !== 'PAID' ? `<button class="btn btn-success" onclick="markPaid(${i.id})">标记回款</button>` : ''}</td>
      </tr>`).join('')
    : '<tr><td colspan="8" style="color:#8b949e">暂无发票</td></tr>';

  document.getElementById('invoiceForm').onsubmit = async (ev) => {
    ev.preventDefault();
    try {
      await api('/invoices', 'POST', {
        invoiceNo: document.getElementById('iNo').value,
        customerId: Number(document.getElementById('iCustomer').value),
        title: document.getElementById('iTitle').value,
        amount: Number(document.getElementById('iAmount').value),
      });
      toast('开票成功');
      ev.target.reset();
      loadInvoices();
    } catch (e) { toast(e.message, true); }
  };
}

async function markPaid(id) {
  try {
    await api(`/invoices/${id}/mark-paid`, 'POST');
    toast('已标记回款');
    loadInvoices();
  } catch (e) { toast(e.message, true); }
}

// 页面加载完立即初始化
init().catch(e => toast('初始化失败：' + e.message, true));
