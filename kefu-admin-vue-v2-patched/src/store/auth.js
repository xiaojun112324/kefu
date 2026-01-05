// src/store/auth.js
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

const LS_KEY = 'auth_v1'

const toNum = (v) => (v === null || v === undefined || v === '' ? null : Number(v))

export const useAuth = defineStore('auth', () => {
  const token = ref('')
  const userId = ref(null)
  const username = ref('')
  const role = ref('')
  const agentId = ref(null)

  const isAuthed = computed(() => !!token.value)

  function hydrate () {
    try {
      const raw = localStorage.getItem(LS_KEY)
      if (!raw) return
      const obj = JSON.parse(raw)
      token.value = obj.token || ''
      userId.value = toNum(obj.userId)
      username.value = obj.username || ''
      role.value = obj.role || ''
      agentId.value = toNum(obj.agentId)
    } catch {}
  }

  function persist () {
    localStorage.setItem(LS_KEY, JSON.stringify({
      token: token.value,
      userId: userId.value,
      username: username.value,
      role: role.value,
      agentId: agentId.value
    }))
  }

  function setAuth (p = {}) {
    token.value = p.token || ''
    userId.value = toNum(p.userId)
    username.value = p.username || ''
    role.value = p.role || ''
    agentId.value = toNum(p.agentId)
    persist()
  }

  function setAccessToken (t) {
    token.value = t || ''
    persist()
  }

  function clear () {
    token.value = ''
    userId.value = null
    username.value = ''
    role.value = ''
    agentId.value = null
    localStorage.removeItem(LS_KEY)
  }

  // 初始化一次
  hydrate()

  // 多标签页登录状态同步（可选，但很有用）
  if (typeof window !== 'undefined') {
    window.addEventListener('storage', (e) => {
      if (e.key === LS_KEY) hydrate()
    })
  }

  return { token, userId, username, role, agentId, isAuthed, setAuth, setAccessToken, clear, hydrate }
})
