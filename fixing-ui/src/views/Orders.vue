<template>
  <div class="page">
    <el-card>
      <template #header>
        <div class="card-title">
          <span>销售订单 <span class="muted">超管录入 · 管理员派发（扣整机 → 建设备档案 → 自动生成安装工单）</span></span>
          <el-button v-if="auth.has('maint:order:edit')" type="primary" round size="small" @click="openOrder">新建订单</el-button>
        </div>
      </template>
      <el-table id="orderTable" :data="orders">
        <el-table-column label="订单号" prop="orderNo" width="150" class-name="mono" />
        <el-table-column label="客户" width="150"><template #default="{ row }">{{ auth.customerName(row.customerId) }}</template></el-table-column>
        <el-table-column label="内容">
          <template #default="{ row }">
            {{ row.orderType === 'MACHINE' ? '🖥 ' + row.model : '💿 ' + row.softwareName + ' ' + (row.softwareVersion ?? '') }}
          </template>
        </el-table-column>
        <el-table-column label="数量" width="70"><template #default="{ row }">×{{ row.qty }}</template></el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'PENDING' ? 'warning' : 'success'" size="small">
              {{ row.status === 'PENDING' ? '待派发' : '已派发' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="" width="100">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING' && auth.has('maint:order:dispatch')"
                       type="primary" round size="small" @click="openDispatch(row)">派 发</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card style="margin-top:16px">
      <template #header>
        <div class="card-title">
          <span>整机库存 <span class="muted">入=组装消耗配件 · 出=订单派发</span></span>
          <el-button v-if="auth.has('maint:machine:edit')" round size="small" @click="openAssemble">🔧 组装整机</el-button>
        </div>
      </template>
      <el-table id="machineTable" :data="machines">
        <el-table-column label="机型" prop="model" width="160"><template #default="{ row }"><b>{{ row.model }}</b></template></el-table-column>
        <el-table-column label="设备类型" prop="equipmentType" width="140" />
        <el-table-column label="可派发数量">
          <template #default="{ row }">
            <b v-if="row.qty === 0" style="color:var(--el-color-danger)">0 台（需组装）</b>
            <span v-else>{{ row.qty }} 台</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新建订单（超管） -->
    <el-dialog v-model="orderOpen" title="新建订单" width="460px">
      <el-form label-width="70px">
        <el-form-item label="客户">
          <el-select v-model="orderForm.customerId" style="width:100%">
            <el-option v-for="c in auth.customers" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="orderForm.orderType">
            <el-radio-button value="MACHINE">整机</el-radio-button>
            <el-radio-button value="SOFTWARE">软件</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <template v-if="orderForm.orderType === 'MACHINE'">
          <el-form-item label="机型">
            <el-select v-model="orderForm.model" style="width:100%">
              <el-option v-for="m in machines" :key="m.id" :label="`${m.model}（现货${m.qty}台）`" :value="m.model" />
            </el-select>
          </el-form-item>
          <el-form-item label="台数"><el-input-number id="oQty" v-model="orderForm.qty" :min="1" /></el-form-item>
        </template>
        <template v-else>
          <el-form-item label="软件"><el-input v-model="orderForm.softwareName" placeholder="软件名" /></el-form-item>
          <el-form-item label="版本"><el-input v-model="orderForm.softwareVersion" style="width:140px" /></el-form-item>
        </template>
        <el-form-item label="备注"><el-input id="oRemark" v-model="orderForm.remark" /></el-form-item>
      </el-form>
      <template #footer><el-button type="primary" round @click="saveOrder">创建订单</el-button></template>
    </el-dialog>

    <!-- 派发（管理员）：整机=逐台序列号；软件=可选安装设备 -->
    <el-dialog v-model="dispatchOpen" :title="'派发 ' + (dispatching?.orderNo ?? '')" width="460px">
      <template v-if="dispatching?.orderType === 'MACHINE'">
        <p class="muted" style="margin-top:0">{{ dispatching.model }} ×{{ dispatching.qty }} → {{ auth.customerName(dispatching.customerId) }}（每台录入序列号）</p>
        <el-input v-for="(s, i) in serials" :key="i" v-model="serials[i]" class="dispatchSerial"
                  :placeholder="`序列号${i + 1}，如 SN-...`" style="margin-bottom:10px" />
        <el-input id="dispatchLocation" v-model="location" placeholder="安装位置，如 门诊三楼" />
      </template>
      <template v-else-if="dispatching">
        <p class="muted" style="margin-top:0">{{ dispatching.softwareName }} → {{ auth.customerName(dispatching.customerId) }}</p>
        <el-select v-model="softEquip" placeholder="装到哪台设备（可不选）" clearable style="width:100%">
          <el-option v-for="e in custEquips" :key="e.id" :label="`${e.equipmentType} ${e.serialNo}`" :value="e.id" />
        </el-select>
      </template>
      <template #footer>
        <el-button type="primary" round @click="confirmDispatch">确认派发</el-button>
        <div class="muted" style="margin-top:10px;text-align:left">派发后自动生成安装工单（待派单），随后到"工单"页指派工程师</div>
      </template>
    </el-dialog>

    <!-- 组装（管理员）：勾选消耗配件 -->
    <el-dialog v-model="assembleOpen" title="组装整机（配件 → 整机）" width="520px">
      <el-form label-width="86px">
        <el-form-item label="机型">
          <el-select id="aMachine" v-model="asmForm.machineStockId" style="width:100%">
            <el-option v-for="m in machines" :key="m.id" :label="`${m.model}（现有${m.qty}台）`" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="组装台数"><el-input-number v-model="asmForm.qty" :min="1" /></el-form-item>
        <el-form-item label="消耗配件">
          <div style="width:100%">
            <div v-for="p in auth.parts" :key="p.id" style="display:flex;gap:10px;align-items:center;margin-bottom:8px">
              <el-checkbox v-model="asmChecks[p.id]" class="asmCk">{{ p.name }}（库存{{ p.stockQty }}）</el-checkbox>
              <el-input-number v-if="asmChecks[p.id]" v-model="asmQty[p.id]" :min="1" size="small" style="width:100px" />
            </div>
          </div>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="asmForm.remark" /></el-form-item>
      </el-form>
      <template #footer><el-button type="primary" round @click="confirmAssemble">完成组装入库</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '@/api'
import { useAuth } from '@/stores/auth'

const auth = useAuth()
const orders = ref([]); const machines = ref([])
const orderOpen = ref(false); const orderForm = ref({ orderType: 'MACHINE', qty: 1 })
const dispatchOpen = ref(false); const dispatching = ref(null)
const serials = ref([]); const location = ref(''); const softEquip = ref(null); const custEquips = ref([])
const assembleOpen = ref(false); const asmForm = ref({ qty: 1 }); const asmChecks = ref({}); const asmQty = ref({})

async function load() { ;[orders.value, machines.value] = await Promise.all([api.orders(), api.machines()]) }

function openOrder() { orderForm.value = { orderType: 'MACHINE', qty: 1 }; orderOpen.value = true }
async function saveOrder() {
  await api.createOrder(orderForm.value)
  ElMessage.success('订单已创建，等待管理员派发'); orderOpen.value = false; load()
}

async function openDispatch(order) {
  dispatching.value = order
  serials.value = Array(order.qty).fill(''); location.value = ''; softEquip.value = null
  if (order.orderType === 'SOFTWARE') custEquips.value = await api.equipments('?customerId=' + order.customerId)
  dispatchOpen.value = true
}
async function confirmDispatch() {
  const o = dispatching.value
  if (o.orderType === 'MACHINE') {
    if (serials.value.some(s => !s.trim())) return ElMessage.error('请为每台机器填写序列号')
    await api.dispatchMachine(o.id, { serialNos: serials.value.map(s => s.trim()), location: location.value || null })
  } else {
    await api.dispatchSoftware(o.id, { equipmentId: softEquip.value })
  }
  ElMessage.success('派发成功：设备/软件已入客户档案，安装工单已生成（待派单）')
  dispatchOpen.value = false; load()
}

function openAssemble() { asmForm.value = { qty: 1 }; asmChecks.value = {}; asmQty.value = {}; assembleOpen.value = true }
async function confirmAssemble() {
  const parts = Object.entries(asmChecks.value).filter(([, on]) => on)
    .map(([id]) => ({ partId: Number(id), qty: asmQty.value[id] ?? 1 }))
  await api.assemble({ ...asmForm.value, parts })
  ElMessage.success('组装完成：整机入库，消耗配件已扣减')
  assembleOpen.value = false
  auth.parts = await api.parts(); load()
}
onMounted(load)
</script>
