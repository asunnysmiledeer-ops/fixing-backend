/* ============================================================
 * FIX-ING Demo v0 · 前端逻辑（原生 JS，无框架）
 *
 * 全部数据来自后端 REST API，本文件只做三件事：
 *   1. 拉数据、渲染页面（看板/表格/详情弹窗）
 *   2. 按"当前身份 × 工单状态"决定显示哪些动作按钮
 *   3. 把动作转成 POST 请求发给后端
 *
 * 【重要】前端隐藏按钮只是"少画几个入口"，真正的权限与状态机校验
 * 全在后端 Service 层 —— 就算用 curl 绕过页面，非法操作照样被拒。
 * ============================================================ */

'use strict';

// ── 全局状态：页面加载时拉一次的基础数据 ──
let users = [];        // 全部用户（切身份、显示操作人姓名都要用）
let customers = [];    // 客户列表
let equipments = [];   // 设备列表
let parts = [];        // 备件列表
let currentUser = null; // 当前身份（下拉框选中的用户对象）

// 工单状态的展示顺序与中文名（和后端 TicketStatus 枚举一一对应）
const STATUS_META = {
  PENDING_ASSIGN:  '待派单',
  ASSIGNED:        '已派单',
  IN_PROGRESS:     '处理中',
  PENDING_CONFIRM: '待确认',
  COMPLETED:       '已完成',
  CANCELLED:       '已取消',
};

// ════════════════════════ 基础工具 ════════════════════════

/**
 * 统一的 API 调用封装：
 * - 每个请求自动带上 Authorization: Bearer <token>（登录时存进 localStorage）；
 * - 收到 401 = 未登录/令牌过期 → 清掉本地 token，踢回登录页；
 * - 后端约定返回 { code, message, data }，code!=0 时抛出 message。
 */
async function api(path, method = 'GET', body = null) {
  const opts = { method, headers: { 'Content-Type': 'application/json' } };
  const token = localStorage.getItem('fixing_token');
  if (token) opts.headers['Authorization'] = 'Bearer ' + token;
  if (body) opts.body = JSON.stringify(body);
  const res = await fetch(path, opts);
  if (res.status === 401) {
    // 令牌无效/过期：本地登录态已没有意义，统一回登录页重新来
    localStorage.removeItem('fixing_token');
    location.href = '/login.html';
    throw new Error('登录已过期');
  }
  const json = await res.json();
  if (json.code !== 0) throw new Error(json.message);
  return json.data;
}

/** 退出登录：JWT 无状态，服务端没有会话可销毁 —— 删本地 token 即完成登出 */
async function logout() {
  try { await api('/auth/logout', 'POST'); } catch (e) { /* 忽略：反正要清 token */ }
  localStorage.removeItem('fixing_token');
  location.href = '/login.html';
}

/** 防 XSS：所有由用户输入回显的文本都先过这一道再拼 HTML */
function esc(s) {
  return String(s ?? '').replace(/[&<>"']/g,
    c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

/** 右下角轻提示；error=true 时红色 */
function toast(msg, error = false) {
  const el = document.getElementById('toast');
  el.textContent = msg;
  el.className = 'toast' + (error ? ' error' : '');
  clearTimeout(el._timer);
  el._timer = setTimeout(() => el.classList.add('hidden'), 3000);
}

/** 时间显示：去掉 T、砍掉秒以下 */
function fmtTime(t) { return t ? t.replace('T', ' ').slice(0, 16) : '—'; }

/** 按 id 找用户姓名（流转日志里只有 operatorId，展示时翻译成人名） */
function userName(id) {
  const u = users.find(u => u.id === id);
  return u ? u.realName : `用户#${id}`;
}
function customerName(id) { const c = customers.find(c => c.id === id); return c ? c.name : `客户#${id}`; }
function equipmentName(id) {
  if (!id) return '—';
  const e = equipments.find(e => e.id === id);
  return e ? `${e.equipmentType} ${e.model}（${e.location}）` : `设备#${id}`;
}
function partName(id) { const p = parts.find(p => p.id === id); return p ? p.name : `备件#${id}`; }

// ════════════════════════ 初始化 ════════════════════════

async function init() {
  // 没有 token 直接去登录页（有 token 但过期的情况由 api() 的 401 分支兜底）
  if (!localStorage.getItem('fixing_token')) {
    location.href = '/login.html';
    return;
  }

  // 先问后端"我是谁"（/auth/me），再并行拉四类基础数据
  const me = await api('/auth/me');
  // 后端 LoginVO 里叫 userId，前端统一成 id，跟 users 列表里的字段对齐
  currentUser = { id: me.userId, username: me.username, realName: me.realName, role: me.role };

  [users, customers, equipments, parts] = await Promise.all([
    api('/users'), api('/customers'), api('/equipments'), api('/spare-parts'),
  ]);

  // 顶栏显示登录身份
  const roleCn = { CUSTOMER: '客户', ADMIN: '管理员', ENGINEER: '工程师' };
  document.getElementById('whoami').textContent =
    `👤 ${currentUser.realName} · ${roleCn[currentUser.role]}`;

  // 页签切换：纯显示/隐藏，切到哪个面板就刷新哪个的数据
  document.querySelectorAll('.tab').forEach(tab => {
    tab.onclick = () => {
      document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
      tab.classList.add('active');
      document.querySelectorAll('.panel').forEach(p => p.classList.add('hidden'));
      document.getElementById('panel-' + tab.dataset.panel).classList.remove('hidden');
      if (tab.dataset.panel === 'board') loadBoard();
      if (tab.dataset.panel === 'inventory') loadInventory();
      if (tab.dataset.panel === 'ledger') loadLedger();
    };
  });

  initCreateForm();
  loadBoard();
}

// ════════════════════════ 面板1：工单看板 ════════════════════════

async function loadBoard() {
  const tickets = await api('/tickets');
  // 按状态分组：六列看板 = "状态分布"这一 Demo 验收点的可视化
  const byStatus = {};
  Object.keys(STATUS_META).forEach(s => byStatus[s] = []);
  tickets.forEach(t => (byStatus[t.status] ?? []).push(t));

  document.getElementById('boardSummary').textContent =
    `共 ${tickets.length} 张工单 · 当前身份：${currentUser.realName}`;

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

function priorityColor(p) {
  return { P0: '#dc2626', P1: '#ea580c', P2: '#d97706', P3: '#2563eb', P4: '#6b7280' }[p] || '#d0d7de';
}

// ════════════════════════ 工单详情弹窗 ════════════════════════

async function openTicket(id) {
  // 详情 + 时间线两个接口并行拉
  const [t, logs] = await Promise.all([api(`/tickets/${id}`), api(`/tickets/${id}/logs`)]);

  document.getElementById('mTitle').innerHTML =
    `${esc(t.ticketNo)} <span class="badge ${t.priority}">${t.priority}</span>`;

  document.getElementById('mBody').innerHTML = `
    <div class="detail-grid">
      <div><b>标题</b>${esc(t.title)}</div>
      <div><b>状态</b>${STATUS_META[t.status]}</div>
      <div><b>类型</b>${t.type === 'HARDWARE' ? '硬件' : '软件'}</div>
      <div><b>客户</b>${esc(customerName(t.customerId))}</div>
      <div><b>设备</b>${esc(equipmentName(t.equipmentId))}</div>
      <div><b>工程师</b>${t.assignedEngineerId ? esc(userName(t.assignedEngineerId)) : '未派单'}</div>
      <div><b>联系人</b>${esc(t.contactName ?? '—')} ${esc(t.contactPhone ?? '')}</div>
      <div><b>创建时间</b>${fmtTime(t.createdAt)}</div>
    </div>
    ${t.description ? `<div style="margin-bottom:12px"><b style="color:#57606a">故障描述：</b>${esc(t.description)}</div>` : ''}
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

/**
 * 动作按钮矩阵 —— 前端版的"谁在什么状态下能做什么"（与后端规则一致）：
 *
 *   待派单:  管理员→派单/取消    客户→取消
 *   已派单:  被派工程师→接单    管理员→改派/取消
 *   处理中:  责任工程师→换件/完工    管理员→改派
 *   待确认:  客户→确认/驳回
 *   终态:    无操作
 */
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
      // 换件：选备件+数量 → 后端原子扣库存并记领料流水
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
    : `<span style="color:#8b949e">当前身份（${currentUser.realName}）在此状态下无可用操作</span>`;
}

// ── 动作请求：成功后刷新弹窗和看板，失败 toast 后端给的原因 ──

/** 通用动作：accept/complete/confirm/reject/cancel（请求体 = 操作人 + 备注） */
async function doAction(ticketId, action, remarkInputId = null) {
  const remark = remarkInputId ? document.getElementById(remarkInputId)?.value : null;
  try {
    // 注意：请求体里没有"操作人"—— 我是谁由后端从登录令牌里认定
    await api(`/tickets/${ticketId}/${action}`, 'POST', { remark: remark || null });
    toast(`${actionLabel(action)} 成功`);
    await openTicket(ticketId); // 重新拉详情：状态和按钮立即更新
    loadBoard();
  } catch (e) { toast(e.message, true); }
}

/** 派单/改派：多一个 engineerId */
async function doAssign(ticketId, action) {
  const engineerId = Number(document.getElementById('aEngineer').value);
  const remark = document.getElementById('aReassignReason')?.value;
  try {
    await api(`/tickets/${ticketId}/${action}`, 'POST', { engineerId, remark: remark || null });
    toast(`${actionLabel(action)} 成功`);
    await openTicket(ticketId);
    loadBoard();
  } catch (e) { toast(e.message, true); }
}

/** 换件：扣库存成功后同时刷新备件缓存（弹窗里的"库存N"要变） */
async function doUsePart(ticketId) {
  const partId = Number(document.getElementById('aPart').value);
  const qty = Number(document.getElementById('aQty').value);
  try {
    await api(`/tickets/${ticketId}/use-part`, 'POST', { partId, qty });
    toast('换件成功，已扣库存并记录领料流水');
    parts = await api('/spare-parts'); // 库存变了，刷新本地缓存
    await openTicket(ticketId);
  } catch (e) { toast(e.message, true); } // 典型失败：库存不足（后端原子扣减兜底）
}

// ════════════════════════ 面板2：报修表单 ════════════════════════

function initCreateForm() {
  const cSel = document.getElementById('fCustomer');
  cSel.innerHTML = customers.map(c => `<option value="${c.id}">${esc(c.name)}</option>`).join('');

  // 客户 → 设备联动：只列出该客户名下的设备
  const eSel = document.getElementById('fEquipment');
  const refreshEquipments = () => {
    const cid = Number(cSel.value);
    const list = equipments.filter(e => e.customerId === cid);
    eSel.innerHTML = '<option value="">— 不关联设备 —</option>' +
      list.map(e => `<option value="${e.id}">${esc(e.equipmentType)} ${esc(e.model)}（${esc(e.location)}）</option>`).join('');
  };
  cSel.onchange = refreshEquipments;
  refreshEquipments();

  document.getElementById('createForm').onsubmit = async (ev) => {
    ev.preventDefault(); // 阻止浏览器默认提交刷新页面
    try {
      const t = await api('/tickets', 'POST', {
        customerId: Number(cSel.value),
        equipmentId: eSel.value ? Number(eSel.value) : null,
        type: document.getElementById('fType').value,
        title: document.getElementById('fTitle').value,
        description: document.getElementById('fDesc').value || null,
        contactName: document.getElementById('fContactName').value || null,
        contactPhone: document.getElementById('fContactPhone').value || null,
      });
      // 把后端判定的优先级直接反馈给用户 —— 演示"规则引擎在工作"
      toast(`报修成功！工单号 ${t.ticketNo}，系统判定优先级 ${t.priority}`);
      ev.target.reset();
      refreshEquipments();
      document.querySelector('.tab[data-panel="board"]').click(); // 跳回看板看新单
    } catch (e) { toast(e.message, true); }
  };
}

// ════════════════════════ 面板3：库存 ════════════════════════

async function loadInventory() {
  parts = await api('/spare-parts');
  const usages = await api('/part-usages');
  const catCn = { PART: '配件', COMPONENT: '部件', CONSUMABLE: '耗材' };

  document.querySelector('#partsTable tbody').innerHTML = parts.map(p => `
    <tr>
      <td>${p.id}</td><td>${esc(p.name)}</td><td>${catCn[p.category] ?? esc(p.category)}</td>
      <td${p.stockQty <= 2 ? ' style="color:#dc2626;font-weight:700"' : ''}>${p.stockQty}</td>
      <td>${p.unitPrice}</td>
    </tr>`).join('');

  document.querySelector('#usageTable tbody').innerHTML = usages.length
    ? usages.map(u => `
      <tr>
        <td>${fmtTime(u.createdAt)}</td><td>${esc(partName(u.partId))}</td><td>×${u.qty}</td>
        <td>${esc(userName(u.engineerId))}</td><td>工单#${u.ticketId}</td>
      </tr>`).join('')
    : '<tr><td colspan="5" style="color:#8b949e">暂无领料记录</td></tr>';
}

// ════════════════════════ 面板4：台账 ════════════════════════

async function loadLedger() {
  [customers, equipments] = await Promise.all([api('/customers'), api('/equipments')]);

  document.querySelector('#customerTable tbody').innerHTML = customers.map(c => `
    <tr>
      <td>${c.id}</td><td>${esc(c.name)}</td><td>${esc(c.customerType)}</td>
      <td>${esc(c.contactName ?? '')}</td><td>${esc(c.contactPhone ?? '')}</td><td>${esc(c.address ?? '')}</td>
    </tr>`).join('');

  document.querySelector('#equipmentTable tbody').innerHTML = equipments.map(e => `
    <tr style="cursor:pointer" onclick="showEquipmentHistory(${e.id})">
      <td>${e.id}</td><td>${esc(e.equipmentType)}</td><td>${esc(e.model ?? '')}</td>
      <td>${esc(e.serialNo)}</td><td>${esc(e.location ?? '')}</td><td>${esc(e.status)}</td>
    </tr>`).join('');
}

/** 设备维修历史（D 阶段验收点：能按设备查它的历史工单） */
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

// 页面加载完立即初始化
init().catch(e => toast('初始化失败：' + e.message, true));
