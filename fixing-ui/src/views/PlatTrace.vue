<template>
  <div class="page">
    <div class="cards" id="overviewCards" v-if="ov.customerCount !== undefined">
      <el-card><div class="muted">客户 / 工程师</div><div class="num">{{ ov.customerCount }} / {{ ov.engineerCount }}</div></el-card>
      <el-card><div class="muted">工单总量</div><div class="num">{{ ov.ticketTotal }}</div></el-card>
      <el-card><div class="muted">合同回款</div><div class="num">{{ money(ov.paidInvoiceTotal) }}</div></el-card>
      <el-card><div class="muted">按次结算应收</div><div class="num">{{ money(ov.chargeTotal) }}</div></el-card>
    </div>
    <el-card>
      <template #header>操作日志 <span class="muted">全部写操作自动记录：谁 / 何时 / 做了什么</span></template>
      <el-table id="operLogTable" :data="logs" size="small">
        <el-table-column label="时间" width="150"><template #default="{ row }">{{ fmtTime(row.createTime) }}</template></el-table-column>
        <el-table-column label="操作人" prop="userName" width="150" />
        <el-table-column label="动作" width="80"><template #default="{ row }"><el-tag size="small" type="info">{{ row.method }}</el-tag></template></el-table-column>
        <el-table-column label="接口" prop="uri" class-name="mono" show-overflow-tooltip />
        <el-table-column label="结果" width="80"><template #default="{ row }">{{ row.status < 400 ? '✅' : '❌ ' + row.status }}</template></el-table-column>
        <el-table-column label="耗时" width="80"><template #default="{ row }">{{ row.costMs ?? '—' }}ms</template></el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { api, money, fmtTime } from '@/api'

const ov = ref({}); const logs = ref([])
onMounted(async () => { ;[ov.value, logs.value] = await Promise.all([api.overview(), api.operLogs()]) })
</script>

<style scoped>
.cards { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 16px; }
.num { font-size: 26px; font-weight: 800; margin-top: 6px; letter-spacing: -.03em; }
</style>
