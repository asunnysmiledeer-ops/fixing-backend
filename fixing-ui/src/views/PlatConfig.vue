<template>
  <div class="page">
    <el-card>
      <template #header>功能开关 <span class="muted">新功能一键启停 —— "要不要上驻场工程师"在这里定</span></template>
      <el-table id="featureTable" :data="features">
        <el-table-column label="功能">
          <template #default="{ row }"><b>{{ row.name }}</b> <span class="muted">{{ row.remark }}</span></template>
        </el-table-column>
        <el-table-column label="" width="130" align="right">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled" @change="() => toggle(row)" />
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card style="margin-top:16px">
      <template #header>业务参数 <span class="muted">收费标准等，改完即刻生效</span></template>
      <el-table id="paramTable" :data="params">
        <el-table-column label="参数">
          <template #default="{ row }">{{ row.name }} <span class="muted mono">{{ row.paramKey }}</span></template>
        </el-table-column>
        <el-table-column label="值" width="240">
          <template #default="{ row }">
            <div style="display:flex;gap:8px">
              <el-input v-model="row.paramValue" size="small" style="width:120px" />
              <el-button size="small" round @click="saveParam(row)">保存</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card style="margin-top:16px">
      <template #header>
        <div class="card-title">
          <span>数据字典 <span class="muted">增一条 = 全平台相关下拉多一项</span></span>
          <span style="display:flex;gap:8px">
            <el-select v-model="dictForm.dictType" size="small" style="width:120px">
              <el-option v-for="(l, k) in DICT_CN" :key="k" :label="l" :value="k" />
            </el-select>
            <el-input id="dictValue" v-model="dictForm.dictValue" size="small" placeholder="值(如 NURSING_HOME)" style="width:170px" />
            <el-input id="dictLabel" v-model="dictForm.dictLabel" size="small" placeholder="标签(如 养老院)" style="width:120px" />
            <el-button type="primary" size="small" round @click="addDict">新增</el-button>
          </span>
        </div>
      </template>
      <el-table id="dictTable" :data="dicts" size="small">
        <el-table-column label="类型" width="110"><template #default="{ row }">{{ DICT_CN[row.dictType] ?? row.dictType }}</template></el-table-column>
        <el-table-column label="项"><template #default="{ row }">{{ row.dictLabel }} <span class="muted mono">{{ row.dictValue }}</span></template></el-table-column>
        <el-table-column label="" width="90" align="right">
          <template #default="{ row }"><el-button type="danger" plain size="small" round @click="delDict(row.id)">删除</el-button></template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '@/api'
import { useAuth } from '@/stores/auth'

const DICT_CN = { customer_type: '客户类型', equipment_type: '设备类型', part_category: '备件分类' }
const auth = useAuth()
const features = ref([]); const params = ref([]); const dicts = ref([])
const dictForm = ref({ dictType: 'customer_type' })

async function load() {
  ;[features.value, params.value, dicts.value] = await Promise.all([api.features(), api.params(), api.allDicts()])
}
async function toggle(row) {
  const f = await api.toggleFeature(row.id)
  ElMessage.success(`${f.name} 已${f.enabled ? '启用' : '关闭'}（全平台入口即刻${f.enabled ? '出现' : '消失'}）`)
  auth.features = await api.enabledFeatures()
  load()
}
async function saveParam(row) { await api.updateParam(row.id, row.paramValue); ElMessage.success('参数已保存，即刻生效') }
async function addDict() {
  await api.addDict(dictForm.value)
  ElMessage.success('字典已新增 —— 全平台相关下拉即刻多了这一项')
  dictForm.value = { dictType: dictForm.value.dictType }; load()
}
async function delDict(id) { await api.delDict(id); ElMessage.success('已删除（软删）'); load() }
onMounted(load)
</script>
