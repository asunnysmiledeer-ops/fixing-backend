<template>
  <!-- Apple ID 式登录：留白 + 居中卡片 + 胶囊按钮 -->
  <div class="login-wrap">
    <div class="logo">🔧</div>
    <h1>FIX-ING</h1>
    <div class="slogan">通用设备维保平台</div>
    <div class="login-card">
      <el-form @submit.prevent="doLogin">
        <el-input id="username" v-model="form.username" placeholder="用户名" size="large" style="margin-bottom: 14px" />
        <el-input id="password" v-model="form.password" type="password" placeholder="密码" size="large" show-password @keyup.enter="doLogin" />
        <el-button class="btn-login" type="primary" size="large" :loading="loading" @click="doLogin">登 录</el-button>
      </el-form>
      <div class="demo-hint">
        <p>演示账号一键登录（密码均 123456）</p>
        <div class="demo-accounts">
          <button v-for="a in demo" :key="a.u" @click="fill(a.u)">{{ a.label }}</button>
        </div>
      </div>
    </div>
    <div class="foot">FIX-ING · 让每一次维保都有据可查</div>
  </div>
</template>

<script setup>
// 登录成功 → token 入 localStorage → 主布局的 auth.load() 拉取身份与权限
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '@/api'

const router = useRouter()
const loading = ref(false)
const form = ref({ username: '', password: '' })
const demo = [
  { u: 'hospital_it', label: '🏥 客户' }, { u: 'engineer_zh', label: '🔧 工程师' },
  { u: 'admin', label: '🗂 管理员' }, { u: 'super', label: '🛰 超管' },
]

function fill(u) { form.value = { username: u, password: '123456' }; doLogin() }

async function doLogin() {
  if (!form.value.username) return
  loading.value = true
  try {
    const d = await api.login(form.value)
    localStorage.setItem('fixing_token', d.token)
    router.push('/')
  } finally { loading.value = false }
}
</script>

<style scoped>
.login-wrap { min-height: 100vh; display: flex; flex-direction: column; align-items: center; justify-content: center; background: var(--bg); }
.logo { font-size: 44px; }
h1 { font-size: 26px; font-weight: 700; letter-spacing: -.02em; margin: 6px 0 0; }
.slogan { color: var(--text-2); margin: 6px 0 30px; }
.login-card { background: #fff; border-radius: 20px; padding: 34px 38px; width: 380px; box-shadow: var(--shadow-float); }
.btn-login { width: 100%; margin-top: 22px; font-weight: 600; }
.demo-hint { margin-top: 24px; padding-top: 18px; border-top: 1px solid var(--separator); }
.demo-hint p { font-size: 12px; color: var(--text-2); text-align: center; margin: 0 0 10px; }
.demo-accounts { display: flex; gap: 8px; }
.demo-accounts button {
  flex: 1; padding: 9px 2px; font-size: 12px; border: 1px solid var(--separator); border-radius: 980px;
  background: #fff; cursor: pointer; font-family: inherit; transition: background .2s ease;
}
.demo-accounts button:hover { background: rgba(0,0,0,.04); }
.foot { margin-top: 26px; font-size: 12px; color: #86868b; }
</style>
