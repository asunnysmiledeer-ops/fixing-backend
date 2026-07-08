<template>
  <div class="page">
    <el-card>
      <template #header>权限矩阵 <span class="muted">勾选即生效：角色的页签与接口权限实时变化，不发版</span></template>
      <el-table id="permMatrix" :data="rows" size="small">
        <el-table-column label="权限字符串" prop="perm" class-name="mono" min-width="220" />
        <el-table-column v-for="r in roles" :key="r" :label="ROLE_CN[r]" width="110" align="center">
          <template #default="{ row }">
            <el-checkbox :model-value="row.granted[r]" @change="v => toggle(r, row.perm, v)" />
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, ROLE_CN } from '@/api'

const roles = ref([])
const rows = ref([])

async function load() {
  const { perms, byRole } = await api.permMatrix()
  roles.value = Object.keys(byRole)
  rows.value = perms.map(p => ({
    perm: p,
    granted: Object.fromEntries(roles.value.map(r => [r, byRole[r].includes(p)])),
  }))
}
async function toggle(role, perm, granted) {
  await (granted ? api.grantPerm({ role, perm }) : api.revokePerm({ role, perm }))
  ElMessage.success(`${granted ? '已授予' : '已收回'} ${ROLE_CN[role]} · ${perm}（即刻生效）`)
  load()
}
onMounted(load)
</script>
