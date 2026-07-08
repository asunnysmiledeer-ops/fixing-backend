<template>
  <div class="page">
    <el-card style="max-width: 640px">
      <template #header>提交报修 / 服务申请</template>
      <el-form label-width="96px">
        <el-form-item v-if="auth.role === 'ADMIN'" label="客户">
          <el-select id="fCustomer" v-model="form.customerId" style="width: 100%">
            <el-option v-for="c in auth.customers" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="类型">
          <!-- 五类工单分段控件：维修类必传图，服务申请类不强制 -->
          <div>
            <el-radio-group v-model="form.type" id="typeSeg">
              <el-radio-button v-for="(m, k) in TYPE_META" :key="k" :value="k" :data-type="k">{{ m.label }}</el-radio-button>
            </el-radio-group>
            <div class="muted" style="margin-top: 6px">{{ TYPE_META[form.type].hint }}</div>
          </div>
        </el-form-item>

        <el-form-item v-if="TYPE_META[form.type].needEquip || form.type === 'HARDWARE'" label="设备">
          <el-select id="fEquipment" v-model="form.equipmentId" clearable placeholder="请选择设备" style="width: 100%">
            <el-option v-for="e in auth.equipments" :key="e.id"
                       :label="`${e.equipmentType} ${e.model}（${e.location ?? ''}）`" :value="e.id" />
          </el-select>
        </el-form-item>

        <el-form-item v-if="TYPE_META[form.type].needSoft" label="软件">
          <el-select id="fSoftware" v-model="form.softwareInstanceId" clearable placeholder="请选择软件" style="width: 100%">
            <el-option v-for="s in auth.softwares" :key="s.id" :label="`${s.name} ${s.version ?? ''}`" :value="s.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="标题">
          <el-input id="fTitle" v-model="form.title" maxlength="128" placeholder="如：门诊大厅叫号主机黑屏" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input id="fDesc" v-model="form.description" type="textarea" :rows="3"
                    placeholder="描述影响范围（全院停摆/单台故障…），系统自动判定优先级" />
        </el-form-item>

        <el-form-item :label="TYPE_META[form.type].repair ? '故障图/视频 *' : '附件(可选)'">
          <!-- 选中即上传拿 URL，提交时只传 URL 数组（大文件不拖慢表单） -->
          <div style="width: 100%">
            <input id="fFiles" type="file" multiple accept="image/*,video/*" @change="onFiles" />
            <div class="ticket-photos" v-if="photos.length">
              <template v-for="u in photos" :key="u">
                <video v-if="/\.(mp4|mov|webm)$/i.test(u)" :src="fileUrl(u)" muted />
                <img v-else :src="fileUrl(u)" alt="预览" />
              </template>
            </div>
          </div>
        </el-form-item>

        <el-form-item label="联系人"><el-input id="fContactName" v-model="form.contactName" style="width: 220px" /></el-form-item>
        <el-form-item label="电话"><el-input id="fContactPhone" v-model="form.contactPhone" style="width: 220px" /></el-form-item>

        <el-button id="createSubmit" type="primary" size="large" @click="submit">提 交</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api, TYPE_META } from '@/api'
import { upload, fileUrl } from '@/api/request'
import { useAuth } from '@/stores/auth'

const auth = useAuth()
const router = useRouter()
const form = ref({ type: 'HARDWARE' })
const photos = ref([])

async function onFiles(ev) {
  for (const file of ev.target.files) {
    try { photos.value.push((await upload(file)).url) }
    catch (e) { /* request 已弹错 */ }
  }
  ev.target.value = ''
}

async function submit() {
  const meta = TYPE_META[form.value.type]
  // 前端先拦"维修无图"（后端还有硬校验 —— 前端拦截只是体验）
  if (auth.role === 'CUSTOMER' && meta.repair && photos.value.length === 0) {
    return ElMessage.error('维修报障必须上传机器或软件异常的图片/视频')
  }
  const t = await api.createTicket({ ...form.value, photos: photos.value })
  ElMessage.success(`提交成功！${t.ticketNo} · 优先级 ${t.priority} · ${t.covered ? '在保' : '不在保（按次收费）'}`)
  form.value = { type: 'HARDWARE' }
  photos.value = []
  router.push('/tickets')
}
</script>
