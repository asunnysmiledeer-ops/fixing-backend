// 全部业务 API + 前端共用枚举映射（与后端枚举一一对应）
import { get, post } from './request'

export const api = {
  // 认证
  login: (d) => post('/auth/login', d),
  me: () => get('/auth/me'),
  logout: () => post('/auth/logout'),
  // 工单
  tickets: (q = '') => get('/tickets' + q),
  ticket: (id) => get(`/tickets/${id}`),
  ticketLogs: (id) => get(`/tickets/${id}/logs`),
  ticketCoverage: (id) => get(`/tickets/${id}/coverage`),
  ticketCharges: (id) => get(`/tickets/${id}/charges`),
  createTicket: (d) => post('/tickets', d),
  ticketAction: (id, action, d = {}) => post(`/tickets/${id}/${action}`, d),
  myEquipments: () => get('/tickets/my-equipments'),
  mySoftwares: () => get('/tickets/my-softwares'),
  // 库存
  parts: () => get('/spare-parts'),
  lowStock: () => get('/spare-parts/low-stock'),
  addPart: (d) => post('/spare-parts', d),
  usages: (q = '') => get('/part-usages' + q),
  partRequests: () => get('/part-requests'),
  createPartRequest: (d) => post('/part-requests', d),
  approvePartRequest: (id) => post(`/part-requests/${id}/approve`),
  rejectPartRequest: (id) => post(`/part-requests/${id}/reject`),
  // 台账
  customers: () => get('/customers'),
  equipments: (q = '') => get('/equipments' + q),
  equipProfile: (id) => get(`/equipments/${id}/profile`),
  softwares: (q = '') => get('/softwares' + q),
  // 合同/发票
  contracts: () => get('/contracts'),
  contractBindings: (id) => get(`/contracts/${id}/bindings`),
  createContract: (d) => post('/contracts', d),
  terminateContract: (id) => post(`/contracts/${id}/terminate`),
  serviceNotice: () => get('/contracts/service-notice'),
  invoices: () => get('/invoices'),
  createInvoice: (d) => post('/invoices', d),
  markInvoicePaid: (id) => post(`/invoices/${id}/mark-paid`),
  // 看板 / 用户
  dashboard: () => get('/dashboard/summary'),
  users: () => get('/users'),
  // 订单/整机
  orders: () => get('/orders'),
  createOrder: (d) => post('/orders', d),
  dispatchMachine: (id, d) => post(`/orders/${id}/dispatch-machine`, d),
  dispatchSoftware: (id, d) => post(`/orders/${id}/dispatch-software`, d),
  machines: () => get('/machines'),
  assemble: (d) => post('/machines/assemble', d),
  // 平台
  platUsers: () => get('/platform/users'),
  platCreateUser: (d) => post('/platform/users', d),
  platToggleUser: (id) => post(`/platform/users/${id}/toggle-status`),
  platResetPwd: (id) => post(`/platform/users/${id}/reset-password`),
  platSetResident: (id, d) => post(`/platform/users/${id}/resident`, d),
  permMatrix: () => get('/platform/perms/matrix'),
  grantPerm: (d) => post('/platform/perms/grant', d),
  revokePerm: (d) => post('/platform/perms/revoke', d),
  features: () => get('/platform/features'),
  toggleFeature: (id) => post(`/platform/features/${id}/toggle`),
  enabledFeatures: () => get('/platform/features/enabled'),
  params: () => get('/platform/params'),
  updateParam: (id, v) => post(`/platform/params/${id}`, { value: v }),
  dicts: (type) => get('/platform/dicts?type=' + type),
  allDicts: () => get('/platform/dicts/all'),
  addDict: (d) => post('/platform/dicts', d),
  delDict: (id) => post(`/platform/dicts/${id}/delete`),
  operLogs: () => get('/platform/oper-logs'),
  overview: () => get('/platform/overview'),
}

export const STATUS_META = {
  PENDING_ASSIGN: { label: '待派单', type: 'info' },
  ASSIGNED: { label: '已派单', type: 'warning' },
  IN_PROGRESS: { label: '处理中', type: 'primary' },
  PENDING_CONFIRM: { label: '待确认', type: 'warning' },
  COMPLETED: { label: '已完成', type: 'success' },
  CANCELLED: { label: '已取消', type: 'info' },
}
export const TYPE_META = {
  HARDWARE: { label: '硬件维修', repair: true, needEquip: true, needSoft: false, hint: '设备故障上门维修 —— 需上传故障图片/视频' },
  SOFTWARE: { label: '软件维修', repair: true, needEquip: false, needSoft: true, hint: '软件异常远程处理 —— 需上传异常截图/录屏' },
  INSTALL: { label: '添加机器', repair: false, needEquip: false, needSoft: false, hint: '申请新装设备 —— 无需传图' },
  RELOCATE: { label: '移动机器', repair: false, needEquip: true, needSoft: false, hint: '现有设备搬迁 —— 选择要移动的设备' },
  SOFTWARE_INSTALL: { label: '安装软件', repair: false, needEquip: false, needSoft: true, hint: '部署/升级软件' },
}
export const ACTION_MAP = {
  create: '提交', assign: '派单', reassign: '改派', accept: '接单', complete: '完工提交',
  confirm: '确认完工', reject: '驳回', cancel: '取消', use_part: '领用备件', charge: '生成结算',
}
export const ROLE_CN = { SUPER_ADMIN: '平台超管', ADMIN: '管理员', ENGINEER: '工程师', CUSTOMER: '客户' }
export const CAT_CN = { PART: '配件', COMPONENT: '部件', CONSUMABLE: '耗材' }
export const money = (n) => '¥' + Number(n ?? 0).toLocaleString()
export const fmtTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '—')
