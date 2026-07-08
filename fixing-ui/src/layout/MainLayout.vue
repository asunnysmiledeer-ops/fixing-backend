<template>
  <div v-if="auth.me">
    <!-- 毛玻璃顶栏 -->
    <header class="topbar">
      <div class="brand">🔧 FIX-ING <span class="muted">通用设备维保平台</span></div>
      <div class="identity">
        <span id="whoami">{{ auth.me.realName }} · {{ ROLE_CN[auth.me.role] }}</span>
        <el-button class="btn-logout" size="small" round @click="doLogout">退出</el-button>
      </div>
    </header>

    <!-- 合同状态横幅（客户）：EXPIRING 倒计时 / EXPIRED 按次收费提示（不拦截报修） -->
    <div v-if="notice.level === 'EXPIRING'" class="service-banner">⏰ {{ notice.message }}</div>
    <div v-else-if="notice.level === 'EXPIRED'" class="service-banner expired">⛔ {{ notice.message }}</div>

    <!-- 胶囊导航 = 权限的投影：菜单来自 menuRoutes × perms，业务与平台分组 -->
    <nav class="tabs" id="tabsNav">
      <template v-for="(m, i) in menus" :key="m.path">
        <span v-if="m.group === '平台' && (i === 0 || menus[i - 1].group !== '平台')" class="divider" />
        <router-link :to="m.path" custom v-slot="{ navigate, isActive }">
          <button class="tab" :class="{ active: isActive }" @click="navigate">
            <el-icon style="vertical-align: -2px"><component :is="m.icon" /></el-icon> {{ m.name }}
          </button>
        </router-link>
      </template>
    </nav>

    <router-view />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuth } from '@/stores/auth'
import { menuRoutes } from '@/router'
import { api, ROLE_CN } from '@/api'

const router = useRouter()
const auth = useAuth()
const notice = ref({ level: 'OK' })

const menus = computed(() =>
  menuRoutes.filter(m => auth.has(m.perm) && !(m.hideFor ?? []).includes(auth.me.role)))

onMounted(async () => {
  await auth.load()
  api.serviceNotice().then(n => (notice.value = n)).catch(() => {})
  // 默认进第一个有权限的页面
  if (router.currentRoute.value.path === '/') router.replace(menus.value[0]?.path ?? '/login')
})

async function doLogout() {
  try { await api.logout() } catch (e) { /* 服务端已拉黑，本地照删 */ }
  localStorage.removeItem('fixing_token')
  router.push('/login')
}
</script>

<style scoped>
.topbar {
  position: sticky; top: 0; z-index: 50; display: flex; justify-content: space-between; align-items: center;
  padding: 12px 28px; background: rgba(255,255,255,.72);
  backdrop-filter: var(--glass); -webkit-backdrop-filter: var(--glass);
  border-bottom: 1px solid var(--separator);
}
.brand { font-size: 17px; font-weight: 700; letter-spacing: -.02em; }
.identity { display: flex; align-items: center; gap: 14px; font-size: 13px; color: var(--text-2); }
.service-banner { padding: 10px 28px; font-size: 13px; font-weight: 500; background: rgba(255,149,0,.12); color: #c93400; }
.service-banner.expired { background: rgba(255,59,48,.1); color: var(--el-color-danger); }
.tabs { display: flex; flex-wrap: wrap; gap: 2px; padding: 12px 28px 0; align-items: center; }
.tab {
  padding: 8px 16px; border: none; background: none; cursor: pointer; font-size: 14px;
  color: var(--text-2); border-radius: 980px; font-family: inherit; transition: all .25s ease;
}
.tab:hover { color: var(--text); background: rgba(0,0,0,.04); }
.tab.active { color: #fff; background: var(--el-color-primary); font-weight: 600; }
.divider { width: 1px; height: 18px; background: var(--separator); margin: 0 10px; }
</style>
