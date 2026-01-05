<template>
  <div class="min-h-screen grid grid-cols-[260px_1fr] gap-6 p-6">
    <!-- 侧栏 -->
    <aside class="card p-4 animate-fade-up">
      <div
        class="flex items-center gap-3 p-3 rounded-xl bg-gradient-to-r from-brand-600/20 to-brand-400/10 border border-brand-400/30 mb-4 shadow-neon"
      >
        <!-- LOGO -->
        <img
          src="../public/img/logo.png"
          alt="KeFu Admin"
          class="w-10 h-10 rounded-xl bg-white/5 border border-white/10 p-1.5 object-contain shadow-inner animate-float"
        />
        <div>
          <div class="text-lg font-semibold tracking-wide">M-IMChat</div>
          <div class="text-white/60 text-xs">高端 · 迅速 · 易用</div>
        </div>
      </div>

      <nav class="space-y-2">
        <RouterLink
          v-for="i in menus"
          :key="i.path"
          :to="i.path"
          class="flex items-center gap-3 px-3 py-2 rounded-lg hover:bg-white/10 transition group"
          :class="{'bg-white/10 shadow-glow': isActive(i.path)}"
        >
          <span class="size-2 rounded-full bg-brand-400 group-hover:scale-125 transition"></span>
          <span class="text-sm">{{ i.title }}</span>
        </RouterLink>
      </nav>
    </aside>

    <!-- 主区 -->
    <main class="space-y-6">
      <header class="card p-4 flex items-center justify-between">
        <div class="font-semibold">控制台</div>

        <!-- 右上角个人菜单 -->
        <div class="relative" ref="wrap">
          <!-- 触发按钮 -->
          <button class="chip" @click="toggle" :aria-expanded="open">
            <span class="avatar" :data-variant="roleUpper">
              {{ initials }}
            </span>
            <span class="meta">
              <b class="name">{{ username || '—' }}</b>
              <i class="role">{{ roleUpper || '-' }}</i>
            </span>
            <svg class="caret" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
              <path d="M7 10l5 5 5-5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            </svg>
          </button>

          <!-- 下拉菜单 -->
          <Transition name="menu">
            <div v-if="open" class="menu" role="menu">
              <div class="info">
                <div class="row"><span>UID</span><b>{{ userId ?? '-' }}</b></div>
                <div class="row"><span>上级代理UID</span><b>{{ agentId ?? '-' }}</b></div>
              </div>
              <div class="sep"></div>

              <!-- 只有非 CS 才显示 -->
              <RouterLink
                v-if="!isCS"
                to="/platform"
                class="item"
                role="menuitem"
                @click="close"
              >
                <span class="ico">
                  <svg viewBox="0 0 24 24">
                    <path d="M12 3l9 6-9 6-9-6 9-6zm0 12l9-6v9a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V9l9 6z"
                          fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                  </svg>
                </span>
                <span class="txt">代理平台设置</span>
              </RouterLink>

              <RouterLink to="/" class="item" role="menuitem" @click="close">
                <span class="ico">
                  <svg viewBox="0 0 24 24"><path d="M3 12l9-8 9 8M5 10v10h14V10" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
                </span>
                <span class="txt">返回首页</span>
              </RouterLink>

              <button class="item danger" role="menuitem" @click="logout">
                <span class="ico">
                  <svg viewBox="0 0 24 24"><path d="M16 17l5-5-5-5M21 12H9M12 21H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
                </span>
                <span class="txt">退出登录</span>
              </button>
            </div>
          </Transition>
        </div>
      </header>

      <section class="card p-6 overflow-hidden">
        <router-view />
      </section>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api'
import { useAuth } from '@/store/auth'

/* 左侧菜单 */
const route = useRoute()
const auth = useAuth()
const router = useRouter()

const roleUpper = computed(() => (auth.role || '').toUpperCase())
const isCS = computed(() => roleUpper.value === 'CS')
const canSeeAC = computed(() => ['ROOT', 'SUPER_AGENT'].includes(roleUpper.value))

// 左侧菜单：不包含「代理平台设置」
const BASE_MENUS = [
  { path: '/',              title: '仪表盘' },
  { path: '/conversations', title: '消息列表' },
  { path: '/customers',     title: '客户列表' },
  { path: '/users',         title: '后台账户管理' },
]

// CS 只保留这三项，其余角色直接使用 ALL_MENUS 并过滤 hideForCS
const menus = computed(() => {
  const list = [...BASE_MENUS]
  if (canSeeAC.value) {
    list.push({ path: '/ActivationCodes', title: '产品激活码管理' })
    list.push({ path: '/op-logs', title: '操作日志' })
  }
  // CS 只保留三项
  if (isCS.value) {
    const allow = new Set(['/', '/conversations', '/customers'])
    return list.filter(m => allow.has(m.path))
  }
  return list
})

const isActive = (p) => route.path === p || (route.path === '/' && p === '/')

// 个人菜单所需
const open = ref(false)
const wrap = ref(null)

const userId = computed(() => auth.userId)
const username = computed(() => auth.username)
const agentId = computed(() => auth.agentId)
const initials = computed(() => String(username.value || 'RO').slice(0,2).toUpperCase())

function toggle(){ open.value = !open.value }
function close(){ open.value = false }
function onDoc(e){ if(wrap.value && !wrap.value.contains(e.target)) close() }
function onKey(e){ if(e.key==='Escape') close() }

onMounted(() => {
  document.addEventListener('click', onDoc)
  document.addEventListener('keyup', onKey)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onDoc)
  document.removeEventListener('keyup', onKey)
})

async function logout () {
  try { await api.post('/auth/logout') } catch {}
  auth.clear()
  close()
  router.replace('/login')
}
</script>

<style scoped>
/* ---------- 右上角 Chip 统一样式 ---------- */
.chip{
  display:flex; align-items:center; gap:10px;
  padding: 12px 16px;
  height: 64px;
  border-radius: 16px;
  color:#e5e7eb;
  background: rgba(13,18,28,.7);
  border: 1px solid rgba(255,255,255,.12);
  box-shadow: inset 0 1px 0 rgba(255,255,255,.06), 0 8px 30px rgba(0,0,0,.35);
  transition: transform .12s ease, box-shadow .2s ease, border-color .2s ease;
}
.chip:hover{ border-color: rgba(59,130,246,.45); box-shadow: 0 10px 30px rgba(2,6,23,.45), inset 0 1px 0 rgba(255,255,255,.08) }
.chip:active{ transform: translateY(1px) }
.avatar{
  width:40px; height:40px; border-radius:12px;
  display:grid; place-items:center; font-weight:900; font-size:14px; letter-spacing:.3px;
  color:#0b1020; text-shadow: 0 1px 0 rgba(255,255,255,.35);
  background: linear-gradient(135deg,#60a5fa,#22d3ee);
  box-shadow: inset 0 -2px 10px rgba(255,255,255,.35);
}
.avatar[data-variant="ROOT"]{ background: linear-gradient(135deg,#f59e0b,#f43f5e) }
.avatar[data-variant="SUPER_AGENT"]{ background: linear-gradient(135deg,#f59e0b,#fde68a) }
.avatar[data-variant="AGENT"]{ background: linear-gradient(135deg,#60a5fa,#8b5cf6) }
.avatar[data-variant="CS"]{ background: linear-gradient(135deg,#34d399,#22d3ee) }
.meta{ display:flex; flex-direction:column; line-height:1.2; max-width:200px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis }
.name{ font-weight:800; font-size:14px; color:#fff }
.role{ font-style:normal; font-size:12px; letter-spacing:.4px; opacity:.8; margin-top:5px }
.caret{ opacity:.8 }

/* ---------- 菜单面板 ---------- */
.menu{
  position:absolute; right:0; top:48px; z-index:40;
  width:260px; border-radius:16px; overflow:hidden;
  background: rgba(13,18,28,.96);
  border:1px solid rgba(255,255,255,.12);
  box-shadow: 0 24px 80px rgba(0,0,0,.45);
  backdrop-filter: blur(8px);
}
.info{ padding:10px 12px 6px; color:#cbd5e1; font-size:12px }
.info .row{ display:flex; align-items:center; justify-content:space-between; padding:6px 6px }
.info .row span{ opacity:.75 }
.info .row b{ color:#fff; font-weight:800 }
.sep{ height:1px; background:linear-gradient(90deg,transparent,rgba(255,255,255,.14),transparent) }

.item{
  width:100%; display:flex; align-items:center; gap:10px;
  padding:10px 12px; color:#e5e7eb; text-align:left; background:transparent; border:0; cursor:pointer;
  transition: background .15s ease, color .15s ease, transform .1s ease;
}
.item:hover{ background: rgba(255,255,255,.06) }
.item:active{ transform: translateY(1px) }
.item .ico{
  width:28px; height:28px; border-radius:8px; display:grid; place-items:center;
  background: rgba(255,255,255,.06);
}
.item .ico svg{ width:16px; height:16px }
.item .txt{ font-weight:700; font-size:13px }

.item.danger{ color:#fecaca }
.item.danger .ico{ background: rgba(244,63,94,.18) }
.item.danger:hover{ background: rgba(244,63,94,.18); color:#fff }

/* ---------- Vue 过渡 ---------- */
.menu-enter-from{ transform: translateY(-6px) scale(.98); opacity:0 }
.menu-enter-active{ transition: all .16s ease }
.menu-enter-to{ transform:none; opacity:1 }
.menu-leave-from{ transform:none; opacity:1 }
.menu-leave-active{ transition: all .12s ease }
.menu-leave-to{ transform: translateY(-6px) scale(.98); opacity:0 }
</style>
