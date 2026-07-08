<template>
  <div class="page" v-if="d.ticketByStatus">
    <div class="cards" id="dashCards">
      <el-card v-for="c in cards" :key="c.label" shadow="hover">
        <div class="muted">{{ c.label }}</div>
        <div class="num" :style="{ color: c.warn ? 'var(--el-color-danger)' : 'inherit' }">{{ c.value }}</div>
      </el-card>
    </div>
    <div class="grid">
      <el-card><template #header>工单状态分布</template>
        <div v-for="(m, s) in STATUS_META" :key="s" class="dist-row">
          <span class="dist-label">{{ m.label }}</span>
          <el-progress :percentage="pct(d.ticketByStatus[s])" :stroke-width="14" :show-text="false" style="flex:1"
                       :color="s === 'COMPLETED' ? '#34c759' : s === 'CANCELLED' ? '#8e8e93' : '#0071e3'" />
          <b style="width:26px;text-align:right">{{ d.ticketByStatus[s] ?? 0 }}</b>
        </div>
        <el-divider />
        <el-tag v-for="(n, t) in d.ticketByType" :key="t" size="large" style="margin:0 8px 6px 0">
          {{ TYPE_META[t]?.label }}：{{ n }}</el-tag>
      </el-card>
      <el-card><template #header>工程师工作量（未完结）</template>
        <el-table :data="d.engineerWorkload" size="small">
          <el-table-column prop="realName" label="工程师" />
          <el-table-column label="在手" width="100"><template #default="{ row }"><b>{{ row.openCount }}</b> 张</template></el-table-column>
        </el-table>
      </el-card>
      <el-card><template #header>⚠️ 低库存备件（动态阈值判定）</template>
        <el-table :data="d.lowStockParts" size="small" empty-text="库存健康 ✅">
          <el-table-column prop="name" label="备件" />
          <el-table-column label="库存/阈值" width="160">
            <template #default="{ row }">
              <span style="color:var(--el-color-danger)">剩 {{ row.stockQty }}</span>（阈值 {{ row.dynamicThreshold }} · 在保 {{ row.contractedDeviceCount }} 台）
            </template>
          </el-table-column>
        </el-table>
      </el-card>
      <el-card><template #header>⏰ 7天内到期合同</template>
        <el-table :data="d.expiringContracts" size="small" empty-text="近期无到期合同 ✅">
          <el-table-column label="客户" width="140"><template #default="{ row }">{{ auth.customerName(row.customerId) }}</template></el-table-column>
          <el-table-column prop="name" label="合同" show-overflow-tooltip />
          <el-table-column prop="endDate" label="到期日" width="110" />
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { api, STATUS_META, TYPE_META, money } from '@/api'
import { useAuth } from '@/stores/auth'

const auth = useAuth()
const d = ref({})
const cards = computed(() => {
  const s = d.value.ticketByStatus ?? {}
  const total = Object.values(s).reduce((a, b) => a + b, 0)
  const open = (s.PENDING_ASSIGN ?? 0) + (s.ASSIGNED ?? 0) + (s.IN_PROGRESS ?? 0) + (s.PENDING_CONFIRM ?? 0)
  return [
    { label: '工单总数 / 进行中', value: `${total} / ${open}` },
    { label: '低库存备件', value: d.value.lowStockParts?.length ?? 0, warn: d.value.lowStockParts?.length > 0 },
    { label: '7天内到期合同', value: d.value.expiringContracts?.length ?? 0, warn: d.value.expiringContracts?.length > 0 },
    { label: '待回款 + 按次应收', value: money(Number(d.value.unpaidInvoiceAmount ?? 0) + Number(d.value.chargeTotal ?? 0)),
      warn: d.value.unpaidInvoiceCount > 0 },
  ]
})
const max = computed(() => Math.max(1, ...Object.values(d.value.ticketByStatus ?? { x: 1 })))
const pct = (n) => Math.round(((n ?? 0) / max.value) * 100)
onMounted(async () => { d.value = await api.dashboard() })
</script>

<style scoped>
.cards { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 16px; }
.num { font-size: 26px; font-weight: 800; margin-top: 6px; letter-spacing: -.03em; }
.grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.dist-row { display: flex; align-items: center; gap: 10px; margin-bottom: 9px; font-size: 12.5px; }
.dist-label { width: 52px; color: var(--text-2); flex-shrink: 0; }
@media (max-width: 900px) { .cards { grid-template-columns: 1fr 1fr; } .grid { grid-template-columns: 1fr; } }
</style>
