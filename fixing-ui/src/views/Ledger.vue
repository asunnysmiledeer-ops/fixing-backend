<template>
  <div class="page">
    <el-card>
      <template #header>客户台账</template>
      <el-table :data="auth.customers">
        <el-table-column label="ID" prop="id" width="60" />
        <el-table-column label="名称" prop="name" />
        <el-table-column label="类型" prop="customerType" width="110" />
        <el-table-column label="联系人" prop="contactName" width="100" />
        <el-table-column label="电话" prop="contactPhone" width="130" />
        <el-table-column label="地址" prop="address" show-overflow-tooltip />
      </el-table>
    </el-card>

    <el-card style="margin-top:16px">
      <template #header>设备查询 <span class="muted">按序列号/运送时间筛选 · 点击行查看设备档案</span></template>
      <div style="display:flex;gap:10px;flex-wrap:wrap;margin-bottom:14px">
        <el-input id="qSerial" v-model="q.serialNo" placeholder="序列号(模糊)" style="width:180px" clearable />
        <el-date-picker id="qRange" v-model="q.range" type="daterange" value-format="YYYY-MM-DD"
                        start-placeholder="运送时间起" end-placeholder="止" style="width:260px" />
        <el-select v-model="q.equipmentType" placeholder="全部类型" clearable style="width:140px">
          <el-option v-for="d in typeDict" :key="d.id" :label="d.dictLabel" :value="d.dictValue" />
        </el-select>
        <el-button type="primary" round @click="search">查 询</el-button>
        <el-button round @click="reset">重置</el-button>
      </div>
      <el-table id="equipmentTable" :data="equipments" @row-click="r => profile.open(r.id)" style="cursor:pointer">
        <el-table-column label="序列号" prop="serialNo" width="170" class-name="mono" />
        <el-table-column label="客户" width="150"><template #default="{ row }">{{ auth.customerName(row.customerId) }}</template></el-table-column>
        <el-table-column label="类型/型号"><template #default="{ row }">{{ row.equipmentType }} {{ row.model }}</template></el-table-column>
        <el-table-column label="位置" prop="location" show-overflow-tooltip />
        <el-table-column label="运送时间" prop="deliveredAt" width="110" />
        <el-table-column label="维修次数" width="90" align="center">
          <template #default="{ row }">
            <b v-if="row.repairCount > 0" style="color:var(--el-color-warning)">{{ row.repairCount }} 次</b>
            <span v-else class="muted">0 次</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card style="margin-top:16px">
      <template #header>软件台账 <span class="muted">"安装软件"完工后在此登记/升版本</span></template>
      <el-table :data="softwares">
        <el-table-column label="ID" prop="id" width="60" />
        <el-table-column label="客户" width="160"><template #default="{ row }">{{ auth.customerName(row.customerId) }}</template></el-table-column>
        <el-table-column label="软件" prop="name" />
        <el-table-column label="版本" prop="version" width="100" />
        <el-table-column label="所在设备" width="110"><template #default="{ row }">{{ row.equipmentId ? '设备#' + row.equipmentId : '—' }}</template></el-table-column>
      </el-table>
    </el-card>

    <EquipProfileDrawer ref="profile" />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { api } from '@/api'
import { useAuth } from '@/stores/auth'
import EquipProfileDrawer from '@/components/EquipProfileDrawer.vue'

const auth = useAuth()
const equipments = ref([]); const softwares = ref([]); const typeDict = ref([])
const q = ref({ serialNo: '', range: null, equipmentType: '' })
const profile = ref()

async function search() {
  const p = new URLSearchParams()
  if (q.value.serialNo) p.set('serialNo', q.value.serialNo)
  if (q.value.range?.[0]) { p.set('deliveredFrom', q.value.range[0]); p.set('deliveredTo', q.value.range[1]) }
  if (q.value.equipmentType) p.set('equipmentType', q.value.equipmentType)
  equipments.value = await api.equipments('?' + p.toString())
}
function reset() { q.value = { serialNo: '', range: null, equipmentType: '' }; search() }

onMounted(async () => {
  search()
  softwares.value = await api.softwares()
  typeDict.value = await api.dicts('equipment_type').catch(() => [])
})
</script>
