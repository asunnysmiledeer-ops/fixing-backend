<template>
  <div class="page">
    <el-card>
      <template #header>新建合同 <span class="muted">颗粒化绑定：保哪几台设备、哪些备件免费换、保哪些软件</span></template>
      <el-form label-width="90px" style="max-width:720px">
        <el-form-item label="客户">
          <el-select v-model="form.customerId" style="width:260px" @change="loadBindOptions">
            <el-option v-for="c in auth.customers" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="合同名称"><el-input v-model="form.name" style="width:400px" /></el-form-item>
        <el-form-item label="起止日期">
          <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" style="width:280px" />
          <el-input-number v-model="form.amount" :min="0" :precision="2" style="margin-left:12px" /> 元
        </el-form-item>
        <el-form-item label="绑定设备">
          <el-checkbox-group v-model="form.equipmentIds">
            <el-checkbox v-for="e in bindEquips" :key="e.id" :value="e.id">{{ e.equipmentType }} {{ e.model }}（{{ e.serialNo }}）</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="免费备件">
          <el-checkbox-group v-model="form.partIds">
            <el-checkbox v-for="p in auth.parts" :key="p.id" :value="p.id">{{ p.name }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="绑定软件">
          <el-checkbox-group v-model="form.softwareInstanceIds">
            <el-checkbox v-for="s in bindSofts" :key="s.id" :value="s.id">{{ s.name }} {{ s.version }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-button type="primary" round @click="save">保存合同</el-button>
      </el-form>
    </el-card>

    <el-card style="margin-top:16px">
      <template #header>合同列表</template>
      <el-table id="contractTable" :data="contracts">
        <el-table-column label="客户" width="150"><template #default="{ row }">{{ auth.customerName(row.customerId) }}</template></el-table-column>
        <el-table-column label="合同" prop="name" show-overflow-tooltip />
        <el-table-column label="起止" width="200"><template #default="{ row }">{{ row.startDate }} ~ {{ row.endDate }}</template></el-table-column>
        <el-table-column label="绑定" width="200">
          <template #default="{ row }">
            <span class="muted">设备×{{ bindings[row.id]?.equipmentIds.length ?? '…' }} · 免费件×{{ bindings[row.id]?.partIds.length ?? '…' }} · 软件×{{ bindings[row.id]?.softwareInstanceIds.length ?? '…' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="110" align="right"><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'TERMINATED'" type="info" size="small">已终止</el-tag>
            <el-tag v-else-if="row.endDate < today" type="danger" size="small">已到期</el-tag>
            <el-tag v-else-if="row.endDate <= soon" type="warning" size="small">即将到期</el-tag>
            <el-tag v-else type="success" size="small">生效中</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="" width="80">
          <template #default="{ row }">
            <el-button v-if="row.status === 'ACTIVE'" link type="danger" size="small" @click="terminate(row.id)">终止</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, money } from '@/api'
import { useAuth } from '@/stores/auth'

const auth = useAuth()
const contracts = ref([]); const bindings = ref({})
const bindEquips = ref([]); const bindSofts = ref([])
const form = ref({ equipmentIds: [], partIds: [], softwareInstanceIds: [], amount: 0 })
const range = ref(null)
const today = new Date().toISOString().slice(0, 10)
const soon = new Date(Date.now() + 7 * 86400000).toISOString().slice(0, 10)

async function load() {
  contracts.value = await api.contracts()
  for (const c of contracts.value) bindings.value[c.id] = await api.contractBindings(c.id)
}
async function loadBindOptions() {
  const cid = form.value.customerId
  ;[bindEquips.value, bindSofts.value] = await Promise.all([
    api.equipments('?customerId=' + cid), api.softwares('?customerId=' + cid),
  ])
  form.value.equipmentIds = []; form.value.softwareInstanceIds = []
}
async function save() {
  await api.createContract({ ...form.value, startDate: range.value?.[0], endDate: range.value?.[1], billingType: 'YEARLY' })
  ElMessage.success('合同已保存（在保判定/动态阈值即刻更新）')
  form.value = { equipmentIds: [], partIds: [], softwareInstanceIds: [], amount: 0 }; range.value = null
  load()
}
async function terminate(id) {
  await ElMessageBox.confirm('终止后该合同覆盖的设备将变为"不在保·按次收费"', '确认终止？')
  await api.terminateContract(id); ElMessage.success('已终止'); load()
}
onMounted(load)
</script>
