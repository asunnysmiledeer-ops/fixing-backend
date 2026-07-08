<template>
  <div class="page">
    <el-card>
      <template #header>
        <div class="card-title"><span>人事管理 <span class="muted">员工与客户账号 · 默认密码 123456</span></span>
          <el-button type="primary" round size="small" @click="openForm">新增账号</el-button></div>
      </template>
      <el-table id="platUserTable" :data="users" :row-style="r => r.row.status === '1' ? { opacity: .45 } : {}">
        <el-table-column label="账号" prop="username" width="140" class-name="mono" />
        <el-table-column label="姓名" prop="realName" width="170" />
        <el-table-column label="角色" width="100">
          <template #default="{ row }"><span class="badge" :class="ROLE_BADGE[row.role]">{{ ROLE_CN[row.role] }}</span></template>
        </el-table-column>
        <el-table-column label="归属/驻场">
          <template #default="{ row }">
            {{ row.role === 'CUSTOMER' ? auth.customerName(row.customerId)
               : row.residentCustomerId ? '🏠 驻场·' + auth.customerName(row.residentCustomerId) : '—' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === '1' ? 'info' : 'success'" size="small">{{ row.status === '1' ? '已停用' : '正常' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280">
          <template #default="{ row }">
            <template v-if="row.role !== 'SUPER_ADMIN'">
              <el-button size="small" round @click="toggle(row)">{{ row.status === '1' ? '启用' : '停用' }}</el-button>
              <el-button size="small" round @click="resetPwd(row.id)">重置密码</el-button>
              <el-button v-if="row.role === 'ENGINEER' && auth.featureOn('resident_engineer')"
                         size="small" round @click="openResident(row)">设驻场</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="formOpen" title="新增账号" width="440px">
      <el-form label-width="80px">
        <el-form-item label="用户名"><el-input id="uUsername" v-model="form.username" placeholder="登录名" /></el-form-item>
        <el-form-item label="姓名"><el-input id="uRealName" v-model="form.realName" /></el-form-item>
        <el-form-item label="角色">
          <el-select id="uRole" v-model="form.role" style="width:100%">
            <el-option label="工程师" value="ENGINEER" /><el-option label="运营管理员" value="ADMIN" /><el-option label="客户" value="CUSTOMER" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.role === 'CUSTOMER'" label="客户单位">
          <el-select v-model="form.customerId" style="width:100%">
            <el-option v-for="c in auth.customers" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <div class="muted">默认密码 123456，可在列表中重置</div>
      <template #footer><el-button type="primary" round @click="save">创建</el-button></template>
    </el-dialog>

    <el-dialog v-model="residentOpen" :title="'设置驻场 · ' + (residentUser?.realName ?? '')" width="400px">
      <el-select v-model="residentCid" clearable placeholder="驻场到哪个客户（清空=取消驻场）" style="width:100%">
        <el-option v-for="c in auth.customers" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <template #footer><el-button type="primary" round @click="saveResident">确定</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, ROLE_CN } from '@/api'
import { useAuth } from '@/stores/auth'

const ROLE_BADGE = { SUPER_ADMIN: 'P0', ADMIN: 'P3', ENGINEER: 'P2', CUSTOMER: 'P4' }
const auth = useAuth()
const users = ref([])
const formOpen = ref(false); const form = ref({ role: 'ENGINEER' })
const residentOpen = ref(false); const residentUser = ref(null); const residentCid = ref(null)

async function load() { users.value = await api.platUsers() }
function openForm() { form.value = { role: 'ENGINEER' }; formOpen.value = true }
async function save() {
  await api.platCreateUser(form.value)
  ElMessage.success('账号已创建（默认密码 123456）'); formOpen.value = false; load()
}
async function toggle(row) {
  const r = await api.platToggleUser(row.id)
  ElMessage.success(r.status === '1' ? '已停用（该账号即刻无法访问）' : '已启用'); load()
}
async function resetPwd(id) { await api.platResetPwd(id); ElMessage.success('密码已重置为 123456') }
function openResident(row) { residentUser.value = row; residentCid.value = row.residentCustomerId; residentOpen.value = true }
async function saveResident() {
  await api.platSetResident(residentUser.value.id, { customerId: residentCid.value ?? null })
  ElMessage.success(residentCid.value ? '驻场已设置（该客户派单时置顶推荐）' : '已取消驻场')
  residentOpen.value = false
  if (auth.has('maint:ticket:assign')) auth.users = await api.users()
  load()
}
onMounted(load)
</script>
