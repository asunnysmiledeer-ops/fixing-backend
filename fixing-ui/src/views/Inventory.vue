<template>
  <div class="page">
    <el-alert v-if="lowStock.length" type="error" :closable="false" show-icon style="margin-bottom:14px;border-radius:10px"
              :title="'低库存预警：' + lowStock.map(p => `${p.name}（剩${p.stockQty}/动态阈值${p.dynamicThreshold}）`).join('、')" />

    <el-card>
      <template #header>
        <div class="card-title">
          <span>备件库存 <span class="muted">动态阈值 = max(人工阈值, 每台备货 × 在保签约设备数)</span></span>
          <span>
            <el-button v-if="auth.has('maint:part:request')" round size="small" @click="reqOpen = true">申请配件</el-button>
            <el-button v-if="auth.has('maint:part:edit')" type="primary" round size="small" @click="partOpen = true">新增备件</el-button>
          </span>
        </div>
      </template>
      <el-table id="partsTable" :data="parts">
        <el-table-column label="名称" prop="name" />
        <el-table-column label="分类" width="80"><template #default="{ row }">{{ CAT_CN[row.category] }}</template></el-table-column>
        <el-table-column label="适用设备" width="110"><template #default="{ row }">{{ row.equipmentType ?? '通用' }}</template></el-table-column>
        <el-table-column label="在保台数" prop="contractedDeviceCount" width="90" align="center" />
        <el-table-column label="库存" width="100" align="center">
          <template #default="{ row }">
            <b v-if="row.stockQty < row.dynamicThreshold" style="color:var(--el-color-danger)">{{ row.stockQty }} ⚠️</b>
            <span v-else>{{ row.stockQty }}</span>
          </template>
        </el-table-column>
        <el-table-column label="动态阈值" prop="dynamicThreshold" width="90" align="center" />
        <el-table-column label="单价(元)" prop="unitPrice" width="100" align="right" />
      </el-table>
    </el-card>

    <el-card style="margin-top:16px">
      <template #header>{{ auth.has('maint:part:edit') ? '配件申请审批（批准自动入库）' : '我的配件申请' }}</template>
      <el-table id="requestTable" :data="requests">
        <el-table-column label="时间" width="150"><template #default="{ row }">{{ fmtTime(row.createTime) }}</template></el-table-column>
        <el-table-column label="备件" width="160"><template #default="{ row }">{{ auth.partName(row.partId) }}</template></el-table-column>
        <el-table-column label="数量" width="70"><template #default="{ row }">×{{ row.qty }}</template></el-table-column>
        <el-table-column label="理由" show-overflow-tooltip>
          <template #default="{ row }">{{ row.reason ?? '—' }}<span v-if="row.approveRemark" class="muted">｜审批：{{ row.approveRemark }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="{ PENDING: 'warning', APPROVED: 'success', REJECTED: 'info' }[row.status]" size="small">
              {{ { PENDING: '待审批', APPROVED: '已入库', REJECTED: '已驳回' }[row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="auth.has('maint:part:edit')" label="操作" width="180">
          <template #default="{ row }">
            <template v-if="row.status === 'PENDING'">
              <el-button type="success" size="small" round @click="approve(row.id)">批准入库</el-button>
              <el-button type="danger" plain size="small" round @click="reject(row.id)">驳回</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card style="margin-top:16px">
      <template #header>{{ auth.role === 'ENGINEER' ? '我的领料记录' : '领料流水（全部）' }}</template>
      <el-table id="usageTable" :data="usages">
        <el-table-column label="时间" width="150"><template #default="{ row }">{{ fmtTime(row.createTime) }}</template></el-table-column>
        <el-table-column label="备件"><template #default="{ row }">{{ auth.partName(row.partId) }}</template></el-table-column>
        <el-table-column label="数量" width="70"><template #default="{ row }">×{{ row.qty }}</template></el-table-column>
        <el-table-column label="计费" width="140">
          <template #default="{ row }">
            <span v-if="row.billable" style="color:var(--el-color-warning)">计费 {{ money(row.unitPrice * row.qty) }}</span>
            <span v-else style="color:var(--el-color-success)">免费</span>
          </template>
        </el-table-column>
        <el-table-column label="工单" width="90"><template #default="{ row }">#{{ row.ticketId }}</template></el-table-column>
      </el-table>
    </el-card>

    <!-- 申请配件（工程师） -->
    <el-dialog v-model="reqOpen" title="申请配件" width="440px">
      <el-form label-width="70px">
        <el-form-item label="备件">
          <el-select id="rqPart" v-model="reqForm.partId" style="width:100%">
            <el-option v-for="p in parts" :key="p.id" :label="`${p.name}（现有${p.stockQty}/阈值${p.dynamicThreshold}）`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="数量"><el-input-number id="rqQty" v-model="reqForm.qty" :min="1" /></el-form-item>
        <el-form-item label="理由"><el-input id="rqReason" v-model="reqForm.reason" placeholder="如：库存不足，为下周巡检备货" /></el-form-item>
      </el-form>
      <template #footer><el-button type="primary" round @click="saveRequest">提交申请</el-button></template>
    </el-dialog>

    <!-- 新增备件（管理员） -->
    <el-dialog v-model="partOpen" title="新增备件" width="460px">
      <el-form label-width="90px">
        <el-form-item label="名称"><el-input v-model="partForm.name" /></el-form-item>
        <el-form-item label="分类">
          <el-select v-model="partForm.category" style="width:100%">
            <el-option v-for="(l, k) in CAT_CN" :key="k" :label="l" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="适用设备"><el-input v-model="partForm.equipmentType" placeholder="设备类型，留空=通用" /></el-form-item>
        <el-form-item label="每台备货"><el-input-number v-model="partForm.perDeviceQty" :min="0" /></el-form-item>
        <el-form-item label="初始库存"><el-input-number v-model="partForm.stockQty" :min="0" /></el-form-item>
        <el-form-item label="人工阈值"><el-input-number v-model="partForm.lowStockThreshold" :min="0" /></el-form-item>
        <el-form-item label="单价"><el-input-number v-model="partForm.unitPrice" :min="0" :precision="2" /></el-form-item>
      </el-form>
      <template #footer><el-button type="primary" round @click="savePart">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, CAT_CN, money, fmtTime } from '@/api'
import { useAuth } from '@/stores/auth'

const auth = useAuth()
const parts = ref([]); const lowStock = ref([]); const usages = ref([]); const requests = ref([])
const reqOpen = ref(false); const reqForm = ref({ qty: 1 })
const partOpen = ref(false); const partForm = ref({ category: 'PART', perDeviceQty: 1, stockQty: 0, lowStockThreshold: 5, unitPrice: 0 })

async function load() {
  ;[parts.value, lowStock.value, usages.value] = await Promise.all([api.parts(), api.lowStock(), api.usages()])
  auth.parts = parts.value
  if (auth.has('maint:part:request') || auth.has('maint:part:edit')) requests.value = await api.partRequests()
}
async function saveRequest() {
  await api.createPartRequest(reqForm.value)
  ElMessage.success('申请已提交，等待管理员审批')
  reqOpen.value = false; reqForm.value = { qty: 1 }; load()
}
async function approve(id) { await api.approvePartRequest(id); ElMessage.success('已批准并入库'); load() }
async function reject(id) { await api.rejectPartRequest(id); ElMessage.success('已驳回'); load() }
async function savePart() { await api.addPart(partForm.value); ElMessage.success('已保存'); partOpen.value = false; load() }
onMounted(load)
</script>
