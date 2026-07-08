// 登录态 store：me(含 perms) + 启用功能 + 基础数据缓存。
// has(perm) 是整个前端的门卫 —— 页签、按钮、路由守卫都问它。
import { defineStore } from 'pinia'
import { api } from '@/api'

export const useAuth = defineStore('auth', {
  state: () => ({
    me: null,          // { userId, username, realName, role, customerId, perms }
    features: [],      // 启用中的功能键（平台开关）
    users: [], customers: [], equipments: [], softwares: [], parts: [],
  }),
  getters: {
    has: (s) => (perm) => s.me?.perms?.includes(perm) ?? false,
    featureOn: (s) => (key) => s.features.includes(key),
    role: (s) => s.me?.role,
  },
  actions: {
    async load() {
      this.me = await api.me()
      // 按权限并行拉基础数据（没权限的不去撞 403）
      const jobs = [api.enabledFeatures().then(d => (this.features = d)).catch(() => {})]
      if (this.has('maint:ticket:add')) {
        jobs.push(api.myEquipments().then(d => (this.equipments = d)).catch(() => {}))
        jobs.push(api.mySoftwares().then(d => (this.softwares = d)).catch(() => {}))
      } else if (this.has('maint:equipment:list')) {
        jobs.push(api.equipments().then(d => (this.equipments = d)).catch(() => {}))
      }
      if (this.has('maint:customer:list')) jobs.push(api.customers().then(d => (this.customers = d)))
      if (this.has('maint:part:list')) jobs.push(api.parts().then(d => (this.parts = d)))
      if (this.has('maint:ticket:assign')) jobs.push(api.users().then(d => (this.users = d)))
      await Promise.allSettled(jobs)
    },
    customerName(id) { return this.customers.find(c => c.id === id)?.name ?? `客户#${id}` },
    equipmentName(id) {
      if (!id) return '—'
      const e = this.equipments.find(e => e.id === id)
      return e ? `${e.equipmentType} ${e.model}（${e.location ?? ''}）` : `设备#${id}`
    },
    partName(id) { return this.parts.find(p => p.id === id)?.name ?? `备件#${id}` },
    userName(id) { return this.users.find(u => u.id === id)?.realName ?? `用户#${id}` },
  },
})
