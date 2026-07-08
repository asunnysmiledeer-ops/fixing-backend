<template>
  <div class="page">
    <el-card>
      <template #header>
        <div class="card-title"><span>发票管理 <span class="muted">应收跟踪：开票 → 回款</span></span>
          <el-button type="primary" round size="small" @click="open = true">开 票</el-button></div>
      </template>
      <el-table id="invoiceTable" :data="invoices">
        <el-table-column label="发票号" prop="invoiceNo" width="150" class-name="mono" />
        <el-table-column label="客户" width="150"><template #default="{ row }">{{ auth.customerName(row.customerId) }}</template></el-table-column>
        <el-table-column label="项目" prop="title" show-overflow-tooltip />
        <el-table-column label="金额" width="110" align="right"><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'PAID' ? 'success' : 'warning'" size="small">{{ row.status === 'PAID' ? '已回款' : '待回款' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="开票日" prop="issuedAt" width="110" />
        <el-table-column label="回款日" prop="paidAt" width="110" />
        <el-table-column label="" width="110">
          <template #default="{ row }">
            <el-button v-if="row.status !== 'PAID'" type="success" round size="small" @click="paid(row.id)">标记回款</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="open" title="开票" width="440px">
      <el-form label-width="80px">
        <el-form-item label="发票号"><el-input v-model="form.invoiceNo" placeholder="INV-2026-xxxx" /></el-form-item>
        <el-form-item label="客户">
          <el-select v-model="form.customerId" style="width:100%">
            <el-option v-for="c in auth.customers" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="金额"><el-input-number v-model="form.amount" :min="0.01" :precision="2" /></el-form-item>
        <el-form-item label="关联工单"><el-input v-model="form.ticketId" placeholder="按次结算转开票时填工单id(选填)" /></el-form-item>
      </el-form>
      <template #footer><el-button type="primary" round @click="save">开票</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, money } from '@/api'
import { useAuth } from '@/stores/auth'

const auth = useAuth()
const invoices = ref([]); const open = ref(false); const form = ref({})

async function load() { invoices.value = await api.invoices() }
async function save() {
  await api.createInvoice({ ...form.value, ticketId: form.value.ticketId ? Number(form.value.ticketId) : null })
  ElMessage.success('开票成功'); open.value = false; form.value = {}; load()
}
async function paid(id) { await api.markInvoicePaid(id); ElMessage.success('已标记回款'); load() }
onMounted(load)
</script>
