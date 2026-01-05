// src/api/messages.js
import axios from 'axios'

const API_BASE = import.meta.env.VITE_API_BASE || '/api'
const http = axios.create({
  baseURL: API_BASE,
  withCredentials: true,
  timeout: 20000,
})

// —— 工具：统一解壳 ——
function unwrap(resp) {
  const r = resp?.data
  if (!r || typeof r !== 'object') throw new Error('Bad response')
  if (typeof r.code !== 'number') return r
  if (r.code !== 0) throw new Error(r.msg || 'Request failed')
  return r.data
}

// —— 工具：读取并持久推广码(code) ——
// 1) 先从当前 URL ?code= 取
// 2) 取不到就用 sessionStorage 里上次的
// 3) 取到了就写回 sessionStorage，避免后续路由丢失
function getReferralCode() {
  try {
    const sp = new URLSearchParams(window.location.search)
    const q = (sp.get('code') || '').trim()
    if (q) {
      sessionStorage.setItem('ref_code', q)
      return q
    }
    const saved = (sessionStorage.getItem('ref_code') || '').trim()
    return saved || ''
  } catch {
    return ''
  }
}

/** 0) 统一：每次进入都向后台 ensure（匿名、无需登录）
 *    - 会自动把 ?code= 带给后端（两种方式都带：query + body）
 */
export async function ensureConversation({ visitorToken, name, meta } = {}) {
  const code = getReferralCode()
  const url = code ? `/customers/ensure?code=${encodeURIComponent(code)}` : '/customers/ensure'
  const body = { visitorToken, name, meta }
  if (code) body.code = code // 如果后端采用“方案A: body.code”，也能识别

  const resp = await http.post(url, body)
  const payload = unwrap(resp) // { customerId, conversationId, visitorToken }
  return payload
}

/** 1) 拉取消息列表（匿名端记得带 side=USER） */
export async function listMessages(params) {
  const resp = await http.get('/messages', { params })
  return unwrap(resp)
}

/** 2) 发送消息（1文本 2图片 3文件 4音频 5视频 6富文本） */
export async function sendMessage(payload) {
  const resp = await http.post('/messages', payload)
  return unwrap(resp)
}

/** 3) 上传文件（多文件） -> 返回 FileInfo[]: {url, originalName, size, mime, ct} */
export async function uploadFiles({ conversationId, files, onProgress }) {
  const form = new FormData()
  form.append('conversationId', String(conversationId))
  Array.from(files || []).forEach(f => form.append('files', f))

  const resp = await http.post('/messages/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: e => {
      if (!onProgress || !e.total) return
      onProgress(Math.round((e.loaded * 100) / e.total))
    },
  })
  return unwrap(resp)
}

/** 4) 撤回消息 */
export async function revokeMessage({ messageId, reason }) {
  const resp = await http.post('/messages/revoke', { messageId, reason })
  return unwrap(resp)
}

/** 5) File -> contentType 映射（与现有一致） */
export function detectCtByFile(file) {
  const t = (file?.type || '').toLowerCase()
  const name = (file?.name || '').toLowerCase()
  if (t.startsWith('image/') || /\.(png|jpe?g|gif|webp|bmp|svg)$/.test(name)) return 2
  if (t.startsWith('audio/') || /\.(mp3|m4a|aac|wav|ogg|webm)$/.test(name)) return 4
  if (t.startsWith('video/') || /\.(mp4|mov|avi|mkv|webm|m4v)$/.test(name)) return 5
  return 3
}
