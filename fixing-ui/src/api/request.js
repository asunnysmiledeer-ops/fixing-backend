// 统一请求封装（M2）：自动带 JWT、401 踢回登录、code!=0 弹错并抛出。
// dev 走 vite 代理(/api→8080)；生产构建后同源直连，也是 /api 前缀？——
// 生产是同一个 jar 同源，无前缀；用 BASE 区分环境。
import { ElMessage } from 'element-plus'
import router from '@/router'

const BASE = import.meta.env.DEV ? '/api' : ''

export async function request(path, { method = 'GET', body, raw } = {}) {
  const headers = {}
  const token = localStorage.getItem('fixing_token')
  if (token) headers['Authorization'] = 'Bearer ' + token
  if (body && !raw) headers['Content-Type'] = 'application/json'

  const res = await fetch(BASE + path, {
    method,
    headers,
    body: raw ? body : body ? JSON.stringify(body) : undefined,
  })
  if (res.status === 401) {
    localStorage.removeItem('fixing_token')
    router.push('/login')
    throw new Error('登录已过期')
  }
  const json = await res.json()
  if (json.code !== 0) {
    ElMessage.error(json.message)
    throw new Error(json.message)
  }
  return json.data
}

export const get = (p) => request(p)
export const post = (p, body = {}) => request(p, { method: 'POST', body })

/** 文件上传（multipart 不能手动设 Content-Type，浏览器要自己拼 boundary） */
export async function upload(file) {
  const fd = new FormData()
  fd.append('file', file)
  return request('/files', { method: 'POST', body: fd, raw: true })
}

/** 附件 URL 前缀（dev 环境走代理） */
export const fileUrl = (u) => (import.meta.env.DEV ? '/api' : '') + u
