/* ============================================================
 * FIX-ING M1 · 前端逻辑（原生 JS，苹果风轻端）
 *
 * 核心变化：页签/按钮由登录返回的 perms 权限字符串驱动（has() 函数），
 * 不再按角色硬编码 —— 后台改权限，页面自动变，前端零改动。
 * 数据隔离/对象级权限依然全在后端，前端只是"少画入口"。
 * ============================================================ */

'use strict';

let me = null;            // /auth/me：含 perms 权限列表
let customers = [];
let equipments = [];      // 报修下拉（后端按单位隔离）
let softwares = [];
let parts = [];
let users = [];           // 工程师下拉等（管理端）
let uploadedPhotos = [];
let selectedType = 'HARDWARE';

const STATUS_META = {
  PENDING_ASSIGN: { label: '待派单' }, ASSIGNED: { label: '已派单' },
  IN_PROGRESS: { label: '处理中' }, PENDING_CONFIRM: { label: '待确认' },
  COMPLETED: { label: '已完成' }, CANCELLED: { label: '已取消' },
};
const TYPE_META = {
  HARDWARE:         { label: '硬件维修', repair: true,  needEquip: true,  needSoft: false, hint: '设备故障上门维修 —— 需上传故障图片/视频' },
  SOFTWARE:         { label: '软件维修', repair: true,  needEquip: false, needSoft: true,  hint: '软件异常远程处理 —— 需上传异常截图/录屏' },
  INSTALL:          { label: '添加机器', repair: false, needEquip: false, needSoft: false, hint: '申请新装设备 —— 无需传图，描述需求即可' },
  RELOCATE:         { label: '移动机器', repair: false, needEquip: true,  needSoft: false, hint: '现有设备搬迁 —— 选择要移动的设备' },
  SOFTWARE_INSTALL: { label: '安装软件', repair: false, needEquip: false, needSoft: true,  hint: '部署/升级软件 —— 可关联现有软件' },
};
const ACTION_MAP = {
  create: '提交', assign: '派单', reassign: '改派', accept: '接单', complete: '完工提交',
  confirm: '确认完工', reject: '驳回', cancel: '取消', use_part: '领用备件', charge: '生成结算',
};
const CAT_CN = { PART: '配件', COMPONENT: '部件', CONSUMABLE: '耗材' };
const ROLE_CN = { CUSTOMER: '客户', ADMIN: '管理员', ENGINEER: '工程师' };

/** 页签声明：perm 满足才渲染（权限字符串驱动 UI 的落点） */
const TABS = [
  { panel: 'create',    label: '➕ 我要报修', perm: 'maint:ticket:add',    load: () => {}, hideFor: 'ADMIN' },
  { panel: 'tickets',   label: '📋 工单',     perm: 'maint:ticket:list',   load: loadTickets },
  { panel: 'dashboard', label: '📊 数据看板', perm: 'maint:dashboard:view', load: loadDashboard },
  { panel: 'inventory', label: '📦 备件库存', perm: 'maint:part:list',     load: loadInventory },
  { panel: 'ledger',    label: '🏥 客户与设备', perm: 'maint:customer:list', load: loadLedger },
  { panel: 'contracts', label: '📄 合同',     perm: 'maint:contract:list', load: loadContracts },
  { panel: 'invoices',  label: '🧾 发票',     perm: 'maint:invoice:list',  load: loadInvoices },
];

// ════════════ 基础工具 ════════════

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
  try { await api('/auth/logout', 'POST'); } catch (e) { /* 服务端已拉黑，本地照删 */ }
  localStorage.removeItem('fixing_token');
  location.href = '/login.html';
}

/** 权限判断：整个前端的门卫 */
const has = (perm) => me?.perms?.includes(perm);

function esc(s) {
  return String(s ?? '').replace(/[&<>"']/g,
    c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}
function toast(msg, error = false) {
  const el = document.getElementById('toast');
  el.textContent = msg;
  el.className = 'toast' + (error ? ' error' : '');
  clearTimeout(el._timer);
  el._timer = setTimeout(() => el.classList.add('hidden'), 3200);
}
const fmtTime = t => t ? t.replace('T', ' ').slice(0, 16) : '—';
const money = n => '¥' + Number(n).toLocaleString();
const customerName = id => customers.find(c => c.id === id)?.name ?? `客户#${id}`;
const equipmentName = id => {
  if (!id) return '—';
  const e = equipments.find(e => e.id === id);
  return e ? `${e.equipmentType} ${e.model}（${e.location}）` : `设备#${id}`;
};
const partName = id => parts.find(p => p.id === id)?.name ?? `备件#${id}`;
const priorityColor = p => ({ P0: '#ff3b30', P1: '#ff6961', P2: '#ff9500', P3: '#0071e3', P4: '#8e8e93' }[p] || '#d0d7de');

// ════════════ 初始化 ════════════

async function init() {
  if (!localStorage.getItem('fixing_token')) { location.href = '/login.html'; return; }
  me = await api('/auth/me');
  me.id = me.userId;

  document.getElementById('whoami').textContent = `${me.realName} · ${ROLE_CN[me.role]}`;

  // 按权限并行拉基础数据（没权限的接口不去撞 403）
  const jobs = [];
  // 设备/软件下拉只有能报修的角色需要（工程师没有 ticket:add，别去撞 403）
  if (has('maint:ticket:add')) {
    jobs.push(api('/tickets/my-equipments').then(d => equipments = d).catch(() => {}));
    jobs.push(api('/tickets/my-softwares').then(d => softwares = d).catch(() => {}));
  }
  if (has('maint:customer:list')) jobs.push(api('/customers').then(d => customers = d));
  // 名称解析用的设备全量（工程师/管理员有 equipment:list；客户走上面的 my-equipments）
  if (has('maint:equipment:list') && !has('maint:ticket:add'))
    jobs.push(api('/equipments').then(d => equipments = d).catch(() => {}));
  if (has('maint:part:list')) jobs.push(api('/spare-parts').then(d => parts = d));
  if (has('maint:ticket:assign')) jobs.push(api('/users').then(d => users = d));
  await Promise.allSettled(jobs);

  renderTabs();
  loadNotice();
  if (has('maint:ticket:add')) initCreateForm();
  // data-perm 声明式按钮显隐
  document.querySelectorAll('[data-perm]').forEach(el => {
    if (has(el.dataset.perm)) el.classList.remove('hidden');
  });
}

/** 页签 = 权限的投影 */
function renderTabs() {
  const visible = TABS.filter(t => has(t.perm) && t.hideFor !== me.role);
  const nav = document.getElementById('tabsNav');
  nav.innerHTML = visible.map((t, i) =>
    `<button class="tab${i === 0 ? ' active' : ''}" data-panel="${t.panel}">${t.label}</button>`).join('');
  nav.querySelectorAll('.tab').forEach(btn => {
    btn.onclick = () => {
      nav.querySelectorAll('.tab').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      document.querySelectorAll('.panel').forEach(p => p.classList.add('hidden'));
      document.getElementById('panel-' + btn.dataset.panel).classList.remove('hidden');
      visible.find(t => t.panel === btn.dataset.panel).load();
    };
  });
  document.getElementById('panel-' + visible[0].panel).classList.remove('hidden');
  visible[0].load();
}

/** 合同状态横幅：EXPIRING 黄色倒计时；EXPIRED 红色"按次收费"提示（不拦截报修） */
async function loadNotice() {
  try {
    const n = await api('/contracts/service-notice');
    if (n.level === 'OK') return;
    const banner = document.getElementById('serviceBanner');
    banner.textContent = (n.level === 'EXPIRED' ? '⛔ ' : '⏰ ') + n.message;
    banner.className = 'service-banner' + (n.level === 'EXPIRED' ? ' expired' : '');
  } catch (e) { /* 非客户角色恒 OK */ }
}

// ════════════ 报修表单（五类工单） ════════════

function initCreateForm() {
  // 类型分段控件
  const seg = document.getElementById('typeSeg');
  seg.innerHTML = Object.entries(TYPE_META).map(([k, m]) =>
    `<button type="button" data-type="${k}"${k === selectedType ? ' class="on"' : ''}>${m.label}</button>`).join('');
  seg.querySelectorAll('button').forEach(b => b.onclick = () => selectType(b.dataset.type));

  const eSel = document.getElementById('fEquipment');
  const sSel = document.getElementById('fSoftware');
  eSel.innerHTML = '<option value="">— 请选择 —</option>' + equipments.map(e =>
    `<option value="${e.id}">${esc(e.equipmentType)} ${esc(e.model)}（${esc(e.location)}）</option>`).join('');
  sSel.innerHTML = '<option value="">— 请选择 —</option>' + softwares.map(s =>
    `<option value="${s.id}">${esc(s.name)} ${esc(s.version ?? '')}</option>`).join('');

  // 管理员代录单：显示客户下拉（设备/软件下拉是全量的，不做联动了——管理员知道自己在干嘛）
  if (me.role === 'ADMIN') {
    document.getElementById('rowCustomer').classList.remove('hidden');
    document.getElementById('fCustomer').innerHTML =
      customers.map(c => `<option value="${c.id}">${esc(c.name)}</option>`).join('');
  }
  selectType(selectedType);

  document.getElementById('fFiles').onchange = async (ev) => {
    const preview = document.getElementById('filePreview');
    for (const file of ev.target.files) {
      try {
        const url = await uploadFile(file);
        uploadedPhotos.push(url);
        preview.insertAdjacentHTML('beforeend', /\.(mp4|mov|webm)$/i.test(url)
          ? `<video src="${url}" muted></video>` : `<img src="${url}" alt="预览">`);
      } catch (e) { toast(`${file.name}: ${e.message}`, true); }
    }
    ev.target.value = '';
  };

  document.getElementById('createForm').onsubmit = async (ev) => {
    ev.preventDefault();
    const meta = TYPE_META[selectedType];
    if (me.role === 'CUSTOMER' && meta.repair && uploadedPhotos.length === 0) {
      toast('维修报障必须上传机器或软件异常的图片/视频', true);
      return;
    }
    try {
      const t = await api('/tickets', 'POST', {
        customerId: me.role === 'ADMIN' ? Number(document.getElementById('fCustomer').value) : null,
        equipmentId: document.getElementById('fEquipment').value ? Number(document.getElementById('fEquipment').value) : null,
        softwareInstanceId: document.getElementById('fSoftware').value ? Number(document.getElementById('fSoftware').value) : null,
        type: selectedType,
        title: document.getElementById('fTitle').value,
        description: document.getElementById('fDesc').value || null,
        photos: uploadedPhotos,
        contactName: document.getElementById('fContactName').value || null,
        contactPhone: document.getElementById('fContactPhone').value || null,
      });
      // 回显：优先级 + 在保/按次（客户立刻知道这单收不收费）
      toast(`提交成功！${t.ticketNo} · 优先级 ${t.priority} · ${t.covered ? '在保（合同内服务）' : '不在保（按次收费）'}`);
      ev.target.reset();
      uploadedPhotos = [];
      document.getElementById('filePreview').innerHTML = '';
      selectType('HARDWARE');
      document.querySelector('.tab[data-panel="tickets"]')?.click();
    } catch (e) { toast(e.message, true); }
  };
}

/** 切换类型：设备/软件/传图区随之显隐 + 提示语 */
function selectType(type) {
  selectedType = type;
  const meta = TYPE_META[type];
  document.querySelectorAll('#typeSeg button').forEach(b =>
    b.classList.toggle('on', b.dataset.type === type));
  document.getElementById('typeHint').textContent = meta.hint;
  document.getElementById('rowEquipment').classList.toggle('hidden', !meta.needEquip && type !== 'HARDWARE');
  document.getElementById('rowSoftware').classList.toggle('hidden', !meta.needSoft);
  document.getElementById('filesLabel').textContent = meta.repair ? '故障图片/视频 *' : '附件(可选)';
}

// ════════════ 工单：客户列表 / 工程师&管理员看板 ════════════

async function loadTickets() {
  const tickets = await api('/tickets');
  const isCustomer = me.role === 'CUSTOMER';
  document.getElementById('board').classList.toggle('hidden', isCustomer);
  document.getElementById('ticketListWrap').classList.toggle('hidden', !isCustomer);
  document.getElementById('boardSummary').textContent =
    me.role === 'ENGINEER' ? `派给我的工单 ${tickets.length} 张`
    : isCustomer ? `本单位工单 ${tickets.length} 张` : `全平台工单 ${tickets.length} 张`;

  if (isCustomer) {
    document.querySelector('#myTicketsTable tbody').innerHTML = tickets.length ? tickets.map(t => `
      <tr style="cursor:pointer" onclick="openTicket(${t.id})">
        <td>${esc(t.ticketNo)}</td><td>${TYPE_META[t.type]?.label ?? t.type}</td><td>${esc(t.title)}</td>
        <td><span class="badge ${t.priority}">${t.priority}</span></td>
        <td><span class="status-tag ${t.status}">${STATUS_META[t.status].label}</span></td>
        <td>${t.covered ? '✅ 在保' : '💰 按次'}</td>
        <td>${fmtTime(t.createTime)}</td>
      </tr>`).join('') : '<tr><td colspan="7" style="color:#8e8e93">还没有工单</td></tr>';
    return;
  }
  const byStatus = {};
  Object.keys(STATUS_META).forEach(s => byStatus[s] = []);
  tickets.forEach(t => (byStatus[t.status] ?? []).push(t));
  document.getElementById('board').innerHTML = Object.entries(STATUS_META).map(([status, m]) => `
    <div class="board-col">
      <h3>${m.label}<span class="col-count">${byStatus[status].length}</span></h3>
      ${byStatus[status].map(t => `
        <div class="ticket-card" style="border-left-color:${priorityColor(t.priority)}" onclick="openTicket(${t.id})">
          <div class="ticket-no">${esc(t.ticketNo)} <span class="badge ${t.priority}">${t.priority}</span> ${t.covered ? '' : '💰'}</div>
          <div class="ticket-title">${esc(t.title)}</div>
          <div class="ticket-no">${TYPE_META[t.type]?.label ?? ''}</div>
        </div>`).join('')}
    </div>`).join('');
}

// ════════════ 工单详情：在保状态卡 + 时间线 + 结算 + 动作 ════════════

async function openTicket(id) {
  const [t, logs, coverage, charges] = await Promise.all([
    api(`/tickets/${id}`), api(`/tickets/${id}/logs`),
    api(`/tickets/${id}/coverage`), api(`/tickets/${id}/charges`),
  ]);

  document.getElementById('mTitle').innerHTML =
    `${esc(t.ticketNo)} <span class="badge ${t.priority}">${t.priority}</span>`;

  // 在保状态卡：派单前一眼看清"保不保、免费件、计费口径"
  const covHtml = `<div class="coverage-card ${coverage.covered ? 'ok' : 'billable'}">
      ${coverage.covered
        ? `✅ <b>${esc(coverage.contractName)}</b> · ${coverage.contractEndDate} 到期${coverage.daysLeft <= 7 ? `（剩 ${coverage.daysLeft} 天 ⚠️）` : ''}<br>${esc(coverage.billingNote)}`
        : `💰 ${esc(coverage.billingNote)}`}
    </div>`;

  const photosHtml = (t.photos?.length) ? `<div class="ticket-photos">
      ${t.photos.map(url => /\.(mp4|mov|webm)$/i.test(url)
        ? `<video src="${esc(url)}" controls muted></video>`
        : `<img src="${esc(url)}" onclick="window.open('${esc(url)}')" alt="附件">`).join('')}
    </div>` : '';

  // 结算明细（完工后出现）
  const total = charges.reduce((a, c) => a + Number(c.amount), 0);
  const chargesHtml = charges.length ? `<div class="charges">
      <b>💰 本单结算</b>
      ${charges.map(c => `<div class="row"><span>${esc(c.itemName)}</span><span>${money(c.amount)}</span></div>`).join('')}
      <div class="row total"><span>合计</span><span>${money(total)}</span></div>
    </div>` : '';

  document.getElementById('mBody').innerHTML = `
    ${covHtml}
    <div class="detail-grid">
      <div><b>标题</b>${esc(t.title)}</div>
      <div><b>状态</b><span class="status-tag ${t.status}">${STATUS_META[t.status].label}</span></div>
      <div><b>类型</b>${TYPE_META[t.type]?.label ?? t.type}</div>
      <div><b>客户</b>${me.role === 'CUSTOMER' ? '本单位' : esc(customerName(t.customerId))}</div>
      <div><b>设备</b>${esc(equipmentName(t.equipmentId))}</div>
      <div><b>软件</b>${t.softwareInstanceId ? esc(softwares.find(s => s.id === t.softwareInstanceId)?.name ?? '#' + t.softwareInstanceId) : '—'}</div>
      <div><b>联系人</b>${esc(t.contactName ?? '—')} ${esc(t.contactPhone ?? '')}</div>
      <div><b>创建时间</b>${fmtTime(t.createTime)}</div>
    </div>
    ${t.description ? `<div style="margin-bottom:8px;font-size:13px"><b style="color:var(--text-2)">描述：</b>${esc(t.description)}</div>` : ''}
    ${photosHtml}${chargesHtml}
    <h4 style="font-size:14px;margin:14px 0 6px">流转时间线</h4>
    <div class="timeline">
      ${logs.map(l => `<div class="timeline-item">
          <span class="timeline-time">${fmtTime(l.createTime)}</span>
          <b>${esc(l.operatorName ?? '')}</b> ${ACTION_MAP[l.action] || l.action}
          ${l.fromStatus ? `<span style="color:var(--text-2)">（${STATUS_META[l.fromStatus]?.label} → ${STATUS_META[l.toStatus]?.label}）</span>` : ''}
          ${l.remark ? `<span class="timeline-remark">💬 ${esc(l.remark)}</span>` : ''}
        </div>`).join('')}
    </div>
    <div class="action-bar" id="actionBar">${renderActions(t, coverage)}</div>`;

  document.getElementById('modalMask').classList.remove('hidden');
}

function closeModal() { document.getElementById('modalMask').classList.add('hidden'); }

/** 动作按钮 = 权限字符串 × 工单状态；对象级校验（是不是你的单）在后端 */
function renderActions(t, coverage) {
  const html = [];
  const engineers = users.filter(u => u.role === 'ENGINEER');
  const engineerOpts = engineers.map(e => `<option value="${e.id}">${esc(e.realName)}</option>`).join('');
  const partOpts = parts.map(p => {
    const free = coverage.covered && coverage.freePartNames?.includes(p.name);
    return `<option value="${p.id}">${esc(p.name)}（库存${p.stockQty}${free ? '·免费' : '·计费'}）</option>`;
  }).join('');

  if (t.status === 'PENDING_ASSIGN' && has('maint:ticket:assign')) {
    html.push(`<span class="inline-form">
      <select id="aEngineer">${engineerOpts}</select>
      <button class="btn btn-primary" onclick="doAssign(${t.id}, 'assign')">派单</button></span>`);
  }
  if (['ASSIGNED', 'IN_PROGRESS'].includes(t.status) && has('maint:ticket:assign')) {
    html.push(`<span class="inline-form">
      <select id="aEngineer">${engineerOpts}</select>
      <input class="remark" id="aReassignReason" placeholder="改派原因">
      <button class="btn" onclick="doAssign(${t.id}, 'reassign')">改派</button></span>`);
  }
  if (t.status === 'ASSIGNED' && has('maint:ticket:handle')) {
    html.push(`<button class="btn btn-success" onclick="doAct(${t.id}, 'accept')">接单</button>`);
  }
  if (t.status === 'IN_PROGRESS' && has('maint:ticket:handle')) {
    html.push(`<span class="inline-form">
      <select id="aPart">${partOpts}</select>
      <input class="qty" id="aQty" type="number" value="1" min="1">
      <button class="btn" onclick="doUsePart(${t.id})">🔩 换件</button></span>`);
    // 不在保工单：完工时可报维修费（不填按标准 300）
    html.push(`<span class="inline-form">
      ${!t.covered ? '<input class="fee" id="aLaborFee" type="number" placeholder="维修费(默认300)">' : ''}
      <input class="remark" id="aCompleteRemark" placeholder="处理说明">
      <button class="btn btn-success" onclick="doComplete(${t.id})">完工提交</button></span>`);
  }
  if (t.status === 'PENDING_CONFIRM' && has('maint:ticket:confirm')) {
    html.push(`<button class="btn btn-success" onclick="doAct(${t.id}, 'confirm')">✅ 确认完工</button>`);
    html.push(`<span class="inline-form">
      <input class="remark" id="aRejectReason" placeholder="驳回原因">
      <button class="btn btn-danger" onclick="doAct(${t.id}, 'reject', 'aRejectReason')">驳回</button></span>`);
  }
  if (['PENDING_ASSIGN', 'ASSIGNED'].includes(t.status)
      && (has('maint:ticket:confirm') || has('maint:ticket:assign'))) {
    html.push(`<button class="btn btn-danger" onclick="doAct(${t.id}, 'cancel')">取消工单</button>`);
  }
  return html.length ? html.join('') : '<span style="color:#8e8e93">当前状态下无可用操作</span>';
}

async function refresh(ticketId) { await openTicket(ticketId); loadTickets(); }

async function doAct(id, action, remarkInputId = null) {
  try {
    const remark = remarkInputId ? document.getElementById(remarkInputId)?.value : null;
    await api(`/tickets/${id}/${action}`, 'POST', { remark: remark || null });
    toast(`${ACTION_MAP[action]} 成功`);
    await refresh(id);
  } catch (e) { toast(e.message, true); }
}

async function doAssign(id, action) {
  try {
    await api(`/tickets/${id}/${action}`, 'POST', {
      engineerId: Number(document.getElementById('aEngineer').value),
      remark: document.getElementById('aReassignReason')?.value || null,
    });
    toast(`${ACTION_MAP[action]} 成功`);
    await refresh(id);
  } catch (e) { toast(e.message, true); }
}

async function doUsePart(id) {
  try {
    const r = await api(`/tickets/${id}/use-part`, 'POST', {
      partId: Number(document.getElementById('aPart').value),
      qty: Number(document.getElementById('aQty').value),
    });
    toast(r.billable ? `换件成功（计费 ${money(r.unitPrice * r.qty)}，完工时并入结算）` : '换件成功（合同内免费）');
    if (has('maint:part:list')) parts = await api('/spare-parts');
    await openTicket(id);
  } catch (e) { toast(e.message, true); }
}

/** 完工：不在保单可带维修费报价，完工后后端自动生成结算单 */
async function doComplete(id) {
  try {
    const fee = document.getElementById('aLaborFee')?.value;
    await api(`/tickets/${id}/complete`, 'POST', {
      remark: document.getElementById('aCompleteRemark')?.value || null,
      laborFee: fee ? Number(fee) : null,
    });
    toast('完工提交成功，结算单已生成');
    await refresh(id);
  } catch (e) { toast(e.message, true); }
}

// ════════════ 备件库存 ════════════

async function loadInventory() {
  parts = await api('/spare-parts');
  const [lowStock, usages] = await Promise.all([
    api('/spare-parts/low-stock'), api('/part-usages'),
  ]);

  const alertEl = document.getElementById('lowStockAlert');
  if (lowStock.length) {
    alertEl.innerHTML = `🚨 <b>低库存预警：</b>` + lowStock.map(p =>
      `${esc(p.name)}（剩 ${p.stockQty} / 动态阈值 ${p.dynamicThreshold}）`).join('、');
    alertEl.classList.remove('hidden');
  } else alertEl.classList.add('hidden');

  document.getElementById('usageTitle').textContent =
    me.role === 'ENGINEER' ? '我的领料记录' : '领料流水（全部）';

  // 配件申请：工程师=我的申请；管理员=待审批列表（批准自动入库）
  if (has('maint:part:request') || has('maint:part:edit')) loadRequests();

  document.querySelector('#partsTable tbody').innerHTML = parts.map(p => {
    const low = p.stockQty < p.dynamicThreshold;
    return `<tr>
      <td>${esc(p.name)}</td><td>${CAT_CN[p.category] ?? p.category}</td>
      <td>${esc(p.equipmentType ?? '通用')}</td>
      <td>${p.contractedDeviceCount}</td>
      <td${low ? ' style="color:var(--red);font-weight:700"' : ''}>${p.stockQty}${low ? ' ⚠️' : ''}</td>
      <td>${p.dynamicThreshold}</td><td>${p.unitPrice}</td>
    </tr>`;
  }).join('');

  document.querySelector('#usageTable tbody').innerHTML = usages.length ? usages.map(u => `
    <tr>
      <td>${fmtTime(u.createTime)}</td><td>${esc(partName(u.partId))}</td><td>×${u.qty}</td>
      <td>${u.billable ? `<span style="color:var(--orange)">计费 ${money(u.unitPrice * u.qty)}</span>` : '<span style="color:var(--green)">免费</span>'}</td>
      <td>${esc(users.find(x => x.id === u.engineerId)?.realName ?? '#' + u.engineerId)}</td>
      <td>#${u.ticketId}</td>
    </tr>`).join('') : '<tr><td colspan="6" style="color:#8e8e93">暂无领料记录</td></tr>';
}

// ── 配件申请（工程师申领 → 管理员审批入库）──
const REQ_STATUS = {
  PENDING: '<span class="status-tag PENDING_CONFIRM">待审批</span>',
  APPROVED: '<span class="status-tag COMPLETED">已入库</span>',
  REJECTED: '<span class="status-tag CANCELLED">已驳回</span>',
};

async function loadRequests() {
  const reqs = await api('/part-requests');
  const isApprover = has('maint:part:edit');
  document.getElementById('requestTitle').textContent =
    isApprover ? '配件申请审批（批准自动入库）' : '我的配件申请';
  document.getElementById('requestOpTh').textContent = isApprover ? '操作' : '';
  document.querySelector('#requestTable tbody').innerHTML = reqs.length ? reqs.map(r => `
    <tr>
      <td>${fmtTime(r.createTime)}</td><td>${esc(partName(r.partId))}</td><td>×${r.qty}</td>
      <td>${esc(r.reason ?? '—')}${r.approveRemark ? ` <span style="color:var(--text-2)">｜审批：${esc(r.approveRemark)}</span>` : ''}</td>
      <td>${REQ_STATUS[r.status] ?? r.status}</td>
      <td>${isApprover && r.status === 'PENDING'
        ? `<button class="btn btn-success" onclick="approveRequest(${r.id})">批准入库</button>
           <button class="btn btn-danger" onclick="rejectRequest(${r.id})">驳回</button>` : ''}</td>
    </tr>`).join('') : '<tr><td colspan="6" style="color:#8e8e93">暂无申请</td></tr>';
}

function openRequestForm() {
  document.getElementById('rqPart').innerHTML = parts.map(p =>
    `<option value="${p.id}">${esc(p.name)}（现有 ${p.stockQty} / 阈值 ${p.dynamicThreshold}）</option>`).join('');
  document.getElementById('requestMask').classList.remove('hidden');
}

async function saveRequest() {
  try {
    await api('/part-requests', 'POST', {
      partId: Number(document.getElementById('rqPart').value),
      qty: Number(document.getElementById('rqQty').value),
      reason: document.getElementById('rqReason').value || null,
    });
    toast('申请已提交，等待管理员审批');
    document.getElementById('requestMask').classList.add('hidden');
    loadRequests();
  } catch (e) { toast(e.message, true); }
}

async function approveRequest(id) {
  try { await api(`/part-requests/${id}/approve`, 'POST', {}); toast('已批准并入库'); loadInventory(); }
  catch (e) { toast(e.message, true); }
}
async function rejectRequest(id) {
  try { await api(`/part-requests/${id}/reject`, 'POST', {}); toast('已驳回'); loadRequests(); }
  catch (e) { toast(e.message, true); }
}

function openPartForm() { document.getElementById('partMask').classList.remove('hidden'); }
async function savePart() {
  try {
    await api('/spare-parts', 'POST', {
      name: document.getElementById('pName').value,
      category: document.getElementById('pCategory').value,
      equipmentType: document.getElementById('pEquipType').value || null,
      perDeviceQty: Number(document.getElementById('pPerDevice').value),
      stockQty: Number(document.getElementById('pStock').value),
      lowStockThreshold: Number(document.getElementById('pThreshold').value),
      unitPrice: Number(document.getElementById('pPrice').value),
    });
    toast('备件已保存');
    document.getElementById('partMask').classList.add('hidden');
    loadInventory();
  } catch (e) { toast(e.message, true); }
}

// ════════════ 台账（客户/设备/软件） ════════════

async function loadLedger() {
  const [cs, eqs, sws] = await Promise.all([
    api('/customers'), api('/equipments'), api('/softwares'),
  ]);
  customers = cs;
  document.querySelector('#customerTable tbody').innerHTML = cs.map(c => `
    <tr><td>${c.id}</td><td>${esc(c.name)}</td><td>${esc(c.customerType)}</td>
    <td>${esc(c.contactName ?? '')}</td><td>${esc(c.contactPhone ?? '')}</td><td>${esc(c.address ?? '')}</td></tr>`).join('');
  document.querySelector('#equipmentTable tbody').innerHTML = eqs.map(e => `
    <tr><td>${e.id}</td><td>${esc(customerName(e.customerId))}</td><td>${esc(e.equipmentType)}</td>
    <td>${esc(e.model ?? '')}</td><td>${esc(e.serialNo)}</td><td>${esc(e.location ?? '')}</td></tr>`).join('');
  document.querySelector('#softwareTable tbody').innerHTML = sws.map(s => `
    <tr><td>${s.id}</td><td>${esc(customerName(s.customerId))}</td><td>${esc(s.name)}</td>
    <td>${esc(s.version ?? '')}</td><td>${s.equipmentId ? '设备#' + s.equipmentId : '—'}</td></tr>`).join('');
}

// ════════════ 合同（颗粒化绑定） ════════════

async function loadContracts() {
  const contracts = await api('/contracts');
  const cSel = document.getElementById('cCustomer');
  cSel.innerHTML = customers.map(c => `<option value="${c.id}">${esc(c.name)}</option>`).join('');
  cSel.onchange = renderBindingChecks;
  await renderBindingChecks();

  const today = new Date().toISOString().slice(0, 10);
  const soon = new Date(Date.now() + 7 * 86400000).toISOString().slice(0, 10);
  const rows = [];
  for (const c of contracts) {
    const b = await api(`/contracts/${c.id}/bindings`);
    let tag;
    if (c.status === 'TERMINATED') tag = '<span class="status-tag CANCELLED">已终止</span>';
    else if (c.endDate < today) tag = '<span class="status-tag" style="background:rgba(255,59,48,.12);color:var(--red)">已到期</span>';
    else if (c.endDate <= soon) tag = '<span class="status-tag PENDING_CONFIRM">即将到期</span>';
    else tag = '<span class="status-tag COMPLETED">生效中</span>';
    rows.push(`<tr>
      <td>${esc(customerName(c.customerId))}</td><td>${esc(c.name)}</td>
      <td>${c.startDate} ~ ${c.endDate}</td>
      <td style="font-size:12px;color:var(--text-2)">设备×${b.equipmentIds.length} · 免费件×${b.partIds.length} · 软件×${b.softwareInstanceIds.length}</td>
      <td>${money(c.amount)}</td><td>${tag}</td>
      <td>${c.status === 'ACTIVE' ? `<button class="btn btn-danger" onclick="doTerminate(${c.id})">终止</button>` : ''}</td>
    </tr>`);
  }
  document.querySelector('#contractTable tbody').innerHTML = rows.join('');
}

/** 按所选客户渲染三组绑定复选框（设备/软件只列该客户的，备件全量） */
async function renderBindingChecks() {
  const cid = Number(document.getElementById('cCustomer').value);
  const [eqs, sws] = await Promise.all([
    api('/equipments?customerId=' + cid), api('/softwares?customerId=' + cid),
  ]);
  const box = (arr, cls, label) => arr.map(x =>
    `<label style="margin-right:12px;font-size:12.5px"><input type="checkbox" class="${cls}" value="${x.id}"> ${esc(label(x))}</label>`).join('') || '<i style="color:#8e8e93">无</i>';
  document.getElementById('bindEquip').innerHTML = box(eqs, 'ckEquip', e => `${e.equipmentType} ${e.model}`);
  document.getElementById('bindPart').innerHTML = box(parts, 'ckPart', p => p.name);
  document.getElementById('bindSoft').innerHTML = box(sws, 'ckSoft', s => `${s.name} ${s.version ?? ''}`);
}

async function saveContract() {
  const picked = cls => [...document.querySelectorAll('.' + cls + ':checked')].map(i => Number(i.value));
  try {
    await api('/contracts', 'POST', {
      customerId: Number(document.getElementById('cCustomer').value),
      name: document.getElementById('cName').value,
      startDate: document.getElementById('cStart').value,
      endDate: document.getElementById('cEnd').value,
      amount: Number(document.getElementById('cAmount').value || 0),
      billingType: 'YEARLY',
      equipmentIds: picked('ckEquip'),
      partIds: picked('ckPart'),
      softwareInstanceIds: picked('ckSoft'),
    });
    toast('合同已保存（绑定生效：在保判定/动态阈值即刻更新）');
    loadContracts();
  } catch (e) { toast(e.message, true); }
}

async function doTerminate(id) {
  try { await api(`/contracts/${id}/terminate`, 'POST'); toast('已终止'); loadContracts(); }
  catch (e) { toast(e.message, true); }
}

// ════════════ 发票 ════════════

async function loadInvoices() {
  const invoices = await api('/invoices');
  document.getElementById('iCustomer').innerHTML =
    customers.map(c => `<option value="${c.id}">${esc(c.name)}</option>`).join('');
  document.querySelector('#invoiceTable tbody').innerHTML = invoices.length ? invoices.map(i => `
    <tr>
      <td>${esc(i.invoiceNo)}</td><td>${esc(customerName(i.customerId))}</td><td>${esc(i.title)}</td>
      <td>${money(i.amount)}</td>
      <td>${i.status === 'PAID' ? '<span class="status-tag COMPLETED">已回款</span>' : '<span class="status-tag PENDING_CONFIRM">待回款</span>'}</td>
      <td>${i.issuedAt ?? '—'}</td><td>${i.paidAt ?? '—'}</td>
      <td>${i.status !== 'PAID' ? `<button class="btn btn-success" onclick="markPaid(${i.id})">标记回款</button>` : ''}</td>
    </tr>`).join('') : '<tr><td colspan="8" style="color:#8e8e93">暂无发票</td></tr>';
}

async function saveInvoice() {
  try {
    await api('/invoices', 'POST', {
      invoiceNo: document.getElementById('iNo').value,
      customerId: Number(document.getElementById('iCustomer').value),
      title: document.getElementById('iTitle').value,
      amount: Number(document.getElementById('iAmount').value),
      ticketId: document.getElementById('iTicket').value ? Number(document.getElementById('iTicket').value) : null,
    });
    toast('开票成功');
    loadInvoices();
  } catch (e) { toast(e.message, true); }
}

async function markPaid(id) {
  try { await api(`/invoices/${id}/mark-paid`, 'POST'); toast('已标记回款'); loadInvoices(); }
  catch (e) { toast(e.message, true); }
}

// ════════════ Dashboard ════════════

async function loadDashboard() {
  const d = await api('/dashboard/summary');
  const s = d.ticketByStatus || {};
  const totalT = Object.values(s).reduce((a, b) => a + b, 0);
  const open = (s.PENDING_ASSIGN ?? 0) + (s.ASSIGNED ?? 0) + (s.IN_PROGRESS ?? 0) + (s.PENDING_CONFIRM ?? 0);

  document.getElementById('dashCards').innerHTML = `
    <div class="dash-card"><div class="lbl">工单总数 / 进行中</div><div class="num">${totalT} / ${open}</div></div>
    <div class="dash-card ${d.lowStockParts.length ? 'warn' : ''}"><div class="lbl">低库存备件</div><div class="num">${d.lowStockParts.length}</div></div>
    <div class="dash-card ${d.expiringContracts.length ? 'warn' : ''}"><div class="lbl">7天内到期合同</div><div class="num">${d.expiringContracts.length}</div></div>
    <div class="dash-card ${Number(d.unpaidInvoiceAmount) + Number(d.chargeTotal) > 0 ? 'warn' : ''}">
      <div class="lbl">待回款 + 按次应收</div><div class="num">${money(Number(d.unpaidInvoiceAmount) + Number(d.chargeTotal))}</div></div>`;

  const max = Math.max(1, ...Object.values(s));
  document.getElementById('dashStatus').innerHTML = Object.entries(STATUS_META).map(([k, m]) => {
    const n = s[k] ?? 0;
    return `<div class="dist-row"><span class="dist-label">${m.label}</span>
      <div class="dist-bar" style="width:${n / max * 68}%;background:${k === 'COMPLETED' ? 'var(--green)' : k === 'CANCELLED' ? 'var(--gray)' : 'var(--accent)'}"></div>
      <span class="dist-count">${n}</span></div>`;
  }).join('');

  document.querySelector('#dashWorkload tbody').innerHTML = d.engineerWorkload.map(w =>
    `<tr><td>${esc(w.realName)}</td><td><b>${w.openCount}</b> 张在手</td></tr>`).join('');
  document.querySelector('#dashLowStock tbody').innerHTML = d.lowStockParts.length
    ? d.lowStockParts.map(p => `<tr><td>${esc(p.name)}</td>
        <td style="color:var(--red)">剩 ${p.stockQty}（动态阈值 ${p.dynamicThreshold} · 在保 ${p.contractedDeviceCount} 台）</td></tr>`).join('')
    : '<tr><td style="color:#8e8e93">库存健康 ✅</td></tr>';
  document.querySelector('#dashExpiring tbody').innerHTML = d.expiringContracts.length
    ? d.expiringContracts.map(c => `<tr><td>${esc(customerName(c.customerId))}</td><td>${esc(c.name)}</td>
        <td style="color:var(--orange)">${c.endDate} 到期</td></tr>`).join('')
    : '<tr><td style="color:#8e8e93">近期无到期合同 ✅</td></tr>';
}

init().catch(e => toast('初始化失败：' + e.message, true));
