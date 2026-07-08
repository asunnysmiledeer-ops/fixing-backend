// 路由（hash 模式：后端零配置，单 jar 直接跑）。
// meta.perm = 该页需要的权限字符串；守卫先登录后鉴权，菜单由 Layout 按同一份表渲染。
import { createRouter, createWebHashHistory } from 'vue-router'

export const menuRoutes = [
  { path: '/report',    name: '我要报修',   icon: 'EditPen',    perm: 'maint:ticket:add',    hideFor: ['ADMIN', 'SUPER_ADMIN'], component: () => import('@/views/Report.vue') },
  { path: '/tickets',   name: '工单',       icon: 'Tickets',    perm: 'maint:ticket:list',   component: () => import('@/views/Tickets.vue') },
  { path: '/dashboard', name: '数据看板',   icon: 'DataAnalysis', perm: 'maint:dashboard:view', component: () => import('@/views/Dashboard.vue') },
  { path: '/inventory', name: '备件库存',   icon: 'Box',        perm: 'maint:part:list',     component: () => import('@/views/Inventory.vue') },
  { path: '/ledger',    name: '客户与设备', icon: 'OfficeBuilding', perm: 'maint:customer:list', component: () => import('@/views/Ledger.vue') },
  { path: '/contracts', name: '合同',       icon: 'Document',   perm: 'maint:contract:list', component: () => import('@/views/Contracts.vue') },
  { path: '/invoices',  name: '发票',       icon: 'Money',      perm: 'maint:invoice:list',  component: () => import('@/views/Invoices.vue') },
  { path: '/orders',    name: '订单派发',   icon: 'Van',        perm: 'maint:order:list',    component: () => import('@/views/Orders.vue') },
  // 平台管理端（超管）
  { path: '/plat/users',  name: '平台·人事', icon: 'UserFilled', perm: 'platform:user:list',   group: '平台', component: () => import('@/views/PlatUsers.vue') },
  { path: '/plat/perms',  name: '平台·权限', icon: 'Key',        perm: 'platform:perm:list',   group: '平台', component: () => import('@/views/PlatPerms.vue') },
  { path: '/plat/config', name: '平台·配置', icon: 'Setting',    perm: 'platform:config:list', group: '平台', component: () => import('@/views/PlatConfig.vue') },
  { path: '/plat/trace',  name: '平台·追踪', icon: 'Monitor',    perm: 'platform:log:list',    group: '平台', component: () => import('@/views/PlatTrace.vue') },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/login', component: () => import('@/views/Login.vue') },
    {
      path: '/',
      component: () => import('@/layout/MainLayout.vue'),
      children: menuRoutes.map(r => ({ ...r, meta: { perm: r.perm } })),
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach((to) => {
  const token = localStorage.getItem('fixing_token')
  if (to.path !== '/login' && !token) return '/login'
  if (to.path === '/login' && token) return '/'
  return true
})

export default router
