<template>
  <!-- 设备档案抽屉：工程师了解整台机器的一站式视图 -->
  <el-drawer v-model="visible" :title="'设备档案 · ' + (d.equipment?.serialNo ?? '')" size="600px">
    <template v-if="d.equipment">
      <el-descriptions :column="2" size="small" border>
        <el-descriptions-item label="类型/型号">{{ d.equipment.equipmentType }} {{ d.equipment.model }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ d.equipment.status }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ d.customerName ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="位置">{{ d.equipment.location ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="运送时间">{{ d.equipment.deliveredAt ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="维修次数">
          <b :style="{ color: d.repairCount > 2 ? 'var(--el-color-danger)' : 'inherit' }">{{ d.repairCount }} 次</b>
        </el-descriptions-item>
        <el-descriptions-item label="地址" :span="2">{{ d.customerAddress ?? '—' }}</el-descriptions-item>
      </el-descriptions>

      <h4>🔩 适用部件（库存）</h4>
      <template v-if="d.applicableParts.length">
        <el-tag v-for="p in d.applicableParts" :key="p.id" style="margin:0 6px 6px 0" type="info">
          {{ p.name }} · 库存{{ p.stockQty }}</el-tag>
      </template>
      <span v-else class="muted">无专用部件（通用耗材见库存页）</span>

      <h4>💻 已装软件</h4>
      <template v-if="d.softwares.length">
        <el-tag v-for="s in d.softwares" :key="s.id" style="margin-right:6px">{{ s.name }} {{ s.version }}</el-tag>
      </template>
      <span v-else class="muted">无</span>

      <h4>🛠 维修记录与原因（{{ d.repairCount }} 次）</h4>
      <el-table v-if="d.repairTickets.length" :data="d.repairTickets" size="small">
        <el-table-column label="工单号" prop="ticketNo" width="140" class-name="mono" />
        <el-table-column label="原因(标题)" prop="title" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="STATUS_META[row.status]?.type" size="small">{{ STATUS_META[row.status]?.label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="140"><template #default="{ row }">{{ fmtTime(row.createTime) }}</template></el-table-column>
      </el-table>
      <span v-else class="muted">无维修记录 ✅</span>

      <h4>🔧 换件历史</h4>
      <div v-for="(u, i) in d.partUsages" :key="i" style="font-size:12.5px;padding:3px 0">
        {{ fmtTime(u.createTime) }} · {{ u.partName }} ×{{ u.qty }}
        <span :style="{ color: u.billable ? 'var(--el-color-warning)' : 'var(--el-color-success)' }">
          （{{ u.billable ? '计费' : '合同内' }}）</span>
      </div>
      <span v-if="!d.partUsages.length" class="muted">未换过件</span>
    </template>
  </el-drawer>
</template>

<script setup>
import { ref } from 'vue'
import { api, STATUS_META, fmtTime } from '@/api'

const visible = ref(false)
const d = ref({ equipment: null, applicableParts: [], softwares: [], repairTickets: [], partUsages: [] })

async function open(id) {
  d.value = await api.equipProfile(id)
  visible.value = true
}
defineExpose({ open })
</script>

<style scoped>
h4 { font-size: 14px; margin: 16px 0 8px; }
</style>
