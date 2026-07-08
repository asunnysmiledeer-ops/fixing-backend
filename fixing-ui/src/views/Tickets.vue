<template>
  <div class="page">
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:14px">
      <span id="boardSummary" class="muted" style="font-size:13px">{{ summary }}</span>
      <el-button size="small" round @click="load">刷新</el-button>
    </div>

    <!-- 客户 = 列表；工程师/管理员 = 六列看板（数据范围后端已按角色隔离） -->
    <el-card v-if="auth.role === 'CUSTOMER'">
      <el-table id="myTicketsTable" :data="tickets" @row-click="r => openDetail(r.id)" style="cursor:pointer">
        <el-table-column label="工单号" prop="ticketNo" width="150" class-name="mono" />
        <el-table-column label="类型" width="100"><template #default="{ row }">{{ TYPE_META[row.type]?.label }}</template></el-table-column>
        <el-table-column label="标题" prop="title" show-overflow-tooltip />
        <el-table-column label="优先级" width="80" align="center">
          <template #default="{ row }"><span class="badge" :class="row.priority">{{ row.priority }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="STATUS_META[row.status].type" size="small">{{ STATUS_META[row.status].label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="在保" width="90"><template #default="{ row }">{{ row.covered ? '✅ 在保' : '💰 按次' }}</template></el-table-column>
        <el-table-column label="提交时间" width="150"><template #default="{ row }">{{ fmtTime(row.createTime) }}</template></el-table-column>
      </el-table>
    </el-card>

    <div v-else id="board" class="board">
      <div v-for="(m, s) in STATUS_META" :key="s" class="board-col">
        <h3>{{ m.label }}<span class="col-count">{{ byStatus[s]?.length ?? 0 }}</span></h3>
        <div v-for="t in byStatus[s]" :key="t.id" class="ticket-card"
             :style="{ borderLeftColor: PRI_COLOR[t.priority] }" @click="openDetail(t.id)">
          <div class="ticket-no">{{ t.ticketNo }} <span class="badge" :class="t.priority">{{ t.priority }}</span> {{ t.covered ? '' : '💰' }}</div>
          <div class="ticket-title">{{ t.title }}</div>
          <div class="ticket-no">{{ TYPE_META[t.type]?.label }}</div>
        </div>
      </div>
    </div>

    <TicketDetailDrawer ref="drawer" @refresh="load" @open-equip="id => equipDrawer.open(id)" />
    <EquipProfileDrawer ref="equipDrawer" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { api, STATUS_META, TYPE_META, fmtTime } from '@/api'
import { useAuth } from '@/stores/auth'
import TicketDetailDrawer from '@/components/TicketDetailDrawer.vue'
import EquipProfileDrawer from '@/components/EquipProfileDrawer.vue'

const PRI_COLOR = { P0: '#ff3b30', P1: '#ff6961', P2: '#ff9500', P3: '#0071e3', P4: '#8e8e93' }
const auth = useAuth()
const tickets = ref([])
const drawer = ref()
const equipDrawer = ref()

const byStatus = computed(() => {
  const g = {}
  Object.keys(STATUS_META).forEach(s => (g[s] = []))
  tickets.value.forEach(t => g[t.status]?.push(t))
  return g
})
const summary = computed(() =>
  auth.role === 'ENGINEER' ? `派给我的工单 ${tickets.value.length} 张`
  : auth.role === 'CUSTOMER' ? `本单位工单 ${tickets.value.length} 张`
  : `全平台工单 ${tickets.value.length} 张`)

async function load() { tickets.value = await api.tickets() }
function openDetail(id) { drawer.value.open(id) }
onMounted(load)
</script>
