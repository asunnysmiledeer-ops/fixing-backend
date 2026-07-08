<template>
  <!-- 工单详情抽屉（四端共用）：在保状态卡 + 附件 + 结算 + 时间线 + 按"权限×状态"的动作区 -->
  <el-drawer v-model="visible" :title="ticket?.ticketNo" size="620px" @closed="$emit('closed')">
    <template v-if="ticket">
      <!-- 在保状态卡：派单决策中枢 -->
      <div class="coverage-card" :class="coverage.covered ? 'ok' : 'billable'">
        <template v-if="coverage.covered">
          ✅ <b>{{ coverage.contractName }}</b> · {{ coverage.contractEndDate }} 到期
          <span v-if="coverage.daysLeft <= 7">（剩 {{ coverage.daysLeft }} 天 ⚠️）</span><br>{{ coverage.billingNote }}
        </template>
        <template v-else>💰 {{ coverage.billingNote }}</template>
      </div>

      <el-descriptions :column="2" size="small" border>
        <el-descriptions-item label="标题" :span="2">{{ ticket.title }}
          <span class="badge" :class="ticket.priority" style="margin-left:6px">{{ ticket.priority }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="类型">{{ TYPE_META[ticket.type]?.label }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="STATUS_META[ticket.status].type" size="small">{{ STATUS_META[ticket.status].label }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="客户">{{ auth.role === 'CUSTOMER' ? '本单位' : auth.customerName(ticket.customerId) }}</el-descriptions-item>
        <el-descriptions-item label="设备">
          {{ auth.equipmentName(ticket.equipmentId) }}
          <el-button v-if="ticket.equipmentId && auth.has('maint:equipment:list')" link type="primary" size="small"
                     @click="$emit('open-equip', ticket.equipmentId)">📋 设备档案</el-button>
        </el-descriptions-item>
        <el-descriptions-item label="联系人">{{ ticket.contactName ?? '—' }} {{ ticket.contactPhone }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ fmtTime(ticket.createTime) }}</el-descriptions-item>
      </el-descriptions>

      <p v-if="ticket.description" style="font-size:13px"><b class="muted">描述：</b>{{ ticket.description }}</p>

      <div v-if="ticket.photos?.length" class="ticket-photos">
        <template v-for="u in ticket.photos" :key="u">
          <video v-if="/\.(mp4|mov|webm)$/i.test(u)" :src="fileUrl(u)" controls muted />
          <el-image v-else :src="fileUrl(u)" :preview-src-list="[fileUrl(u)]" fit="cover"
                    style="width:100px;height:100px;border-radius:10px" />
        </template>
      </div>

      <!-- 结算明细（完工后出现；在保全免费件时为空） -->
      <div v-if="charges.length" class="charges">
        <b>💰 本单结算</b>
        <div v-for="c in charges" :key="c.id" class="row"><span>{{ c.itemName }}</span><span>{{ money(c.amount) }}</span></div>
        <div class="row total"><span>合计</span><span>{{ money(total) }}</span></div>
      </div>

      <h4 style="font-size:14px;margin:14px 0 4px">流转时间线</h4>
      <el-timeline style="padding-left:4px">
        <el-timeline-item v-for="l in logs" :key="l.id" :timestamp="fmtTime(l.createTime)" placement="top">
          <b>{{ l.operatorName }}</b> {{ ACTION_MAP[l.action] || l.action }}
          <span v-if="l.fromStatus" class="muted">（{{ STATUS_META[l.fromStatus]?.label }} → {{ STATUS_META[l.toStatus]?.label }}）</span>
          <div v-if="l.remark" class="muted">💬 {{ l.remark }}</div>
        </el-timeline-item>
      </el-timeline>

      <!-- ── 动作区：权限×状态双重控制；对象级校验（是不是你的单）在后端 ── -->
      <el-divider style="margin:10px 0" />
      <div id="actionBar" style="display:flex;flex-wrap:wrap;gap:10px;align-items:center">
        <!-- 管理员：派单/改派（驻场工程师置顶🏠，受平台开关控制） -->
        <template v-if="ticket.status === 'PENDING_ASSIGN' && auth.has('maint:ticket:assign')">
          <el-select id="aEngineer" v-model="act.engineerId" placeholder="选择工程师" style="width:200px" size="small">
            <el-option v-for="e in engineerOptions" :key="e.id" :label="e.label" :value="e.id" />
          </el-select>
          <el-button type="primary" size="small" @click="doAssign('assign')">派单</el-button>
        </template>
        <template v-if="['ASSIGNED','IN_PROGRESS'].includes(ticket.status) && auth.has('maint:ticket:assign')">
          <el-select v-model="act.engineerId" placeholder="改派给" style="width:180px" size="small">
            <el-option v-for="e in engineerOptions" :key="e.id" :label="e.label" :value="e.id" />
          </el-select>
          <el-input v-model="act.remark" placeholder="改派原因" size="small" style="width:150px" />
          <el-button size="small" @click="doAssign('reassign')">改派</el-button>
        </template>

        <!-- 工程师：接单 / 换件 / 完工（不在保可报维修费） -->
        <el-button v-if="ticket.status === 'ASSIGNED' && auth.has('maint:ticket:handle')"
                   type="success" size="small" @click="doAct('accept')">接单</el-button>
        <template v-if="ticket.status === 'IN_PROGRESS' && auth.has('maint:ticket:handle')">
          <el-select id="aPart" v-model="act.partId" placeholder="选备件" style="width:220px" size="small">
            <el-option v-for="p in partOptions" :key="p.id" :label="p.label" :value="p.id" />
          </el-select>
          <el-input-number id="aQty" v-model="act.qty" :min="1" size="small" style="width:90px" />
          <el-button size="small" @click="doUsePart">🔩 换件</el-button>
          <el-input v-if="!ticket.covered" id="aLaborFee" v-model="act.laborFee" placeholder="维修费(默认300)" size="small" style="width:130px" />
          <el-input id="aCompleteRemark" v-model="act.remark" placeholder="处理说明" size="small" style="width:150px" />
          <el-button type="success" size="small" @click="doComplete">完工提交</el-button>
        </template>

        <!-- 客户：确认 / 驳回 -->
        <template v-if="ticket.status === 'PENDING_CONFIRM' && auth.has('maint:ticket:confirm')">
          <el-button type="success" size="small" @click="doAct('confirm')">✅ 确认完工</el-button>
          <el-input v-model="act.remark" placeholder="驳回原因" size="small" style="width:150px" />
          <el-button type="danger" plain size="small" @click="doAct('reject')">驳回</el-button>
        </template>

        <el-button v-if="['PENDING_ASSIGN','ASSIGNED'].includes(ticket.status)
                        && (auth.has('maint:ticket:confirm') || auth.has('maint:ticket:assign'))"
                   type="danger" plain size="small" @click="doAct('cancel')">取消工单</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, STATUS_META, TYPE_META, ACTION_MAP, money, fmtTime } from '@/api'
import { fileUrl } from '@/api/request'
import { useAuth } from '@/stores/auth'

const emit = defineEmits(['refresh', 'closed', 'open-equip'])
const auth = useAuth()

const visible = ref(false)
const ticket = ref(null)
const logs = ref([])
const coverage = ref({})
const charges = ref([])
const act = ref({ qty: 1 })

const total = computed(() => charges.value.reduce((a, c) => a + Number(c.amount), 0))

/** 派单下拉：驻场工程师置顶 🏠（受平台功能开关控制） */
const engineerOptions = computed(() => {
  let list = auth.users.filter(u => u.role === 'ENGINEER' && u.status !== '1')
  const on = auth.featureOn('resident_engineer')
  if (on) list = [...list].sort((a, b) =>
    (b.residentCustomerId === ticket.value?.customerId ? 1 : 0) - (a.residentCustomerId === ticket.value?.customerId ? 1 : 0))
  return list.map(e => {
    const resident = on && e.residentCustomerId === ticket.value?.customerId
    return { id: e.id, label: (resident ? '🏠 ' : '') + e.realName + (resident ? '（驻场）' : '') }
  })
})

/** 换件下拉：标注"免费/计费"（按合同免费清单预判） */
const partOptions = computed(() => auth.parts.map(p => {
  const free = coverage.value.covered && coverage.value.freePartNames?.includes(p.name)
  return { id: p.id, label: `${p.name}（库存${p.stockQty}·${free ? '免费' : '计费'}）` }
}))

async function open(id) {
  const [t, lg, cov, ch] = await Promise.all([
    api.ticket(id), api.ticketLogs(id), api.ticketCoverage(id), api.ticketCharges(id),
  ])
  ticket.value = t; logs.value = lg; coverage.value = cov; charges.value = ch
  visible.value = true
}
defineExpose({ open })

async function reload() { await open(ticket.value.id); emit('refresh') }

async function doAct(action) {
  await api.ticketAction(ticket.value.id, action, { remark: act.value.remark || null })
  ElMessage.success(`${ACTION_MAP[action]} 成功`)
  act.value.remark = null
  await reload()
}
async function doAssign(action) {
  if (!act.value.engineerId) return ElMessage.error('请选择工程师')
  await api.ticketAction(ticket.value.id, action, { engineerId: act.value.engineerId, remark: act.value.remark || null })
  ElMessage.success(`${ACTION_MAP[action]} 成功`)
  await reload()
}
async function doUsePart() {
  if (!act.value.partId) return ElMessage.error('请选择备件')
  const r = await api.ticketAction(ticket.value.id, 'use-part', { partId: act.value.partId, qty: act.value.qty })
  ElMessage.success(r.billable ? `换件成功（计费 ${money(r.unitPrice * r.qty)}，完工时并入结算）` : '换件成功（合同内免费）')
  if (auth.has('maint:part:list')) auth.parts = await api.parts()
  await reload()
}
async function doComplete() {
  await api.ticketAction(ticket.value.id, 'complete', {
    remark: act.value.remark || null,
    laborFee: act.value.laborFee ? Number(act.value.laborFee) : null,
  })
  ElMessage.success('完工提交成功，结算单已生成')
  await reload()
}
</script>
