<template>
  <div
    ref="rootRef"
    class="login-wrap"
    :class="[{ outro: zooming }]"
  >
    <!-- 背景：极光 + 大网格 + 漂浮光球 -->
    <div class="bg-aurora"></div>
    <div class="bg-grid"></div>
    <div class="orbs">
      <span
        v-for="(o, i) in orbs"
        :key="i"
        class="orb"
        :style="{
          '--x': o.x + '%',
          '--y': o.y + '%',
          '--size': o.size + 'px',
          '--hue': o.hue + 'deg',
          '--dur': o.dur + 's',
          '--delay': o.delay + 's',
          '--sx': o.scale.toString(),
          '--tx': o.tx + 'px',
          '--ty': o.ty + 'px'
        }"
      />
    </div>

    <!-- 登录卡片 -->
    <Transition name="pop">
      <div v-show="mounted" ref="cardRef" class="card glass" :class="{ leaving }">
        <div class="title">登录后台</div>
        <div class="sub">ImChat · 简洁迅速的在线客服系统</div>

        <form @submit.prevent="submit" class="form">
          <div class="field">
            <input v-model.trim="username" autocomplete="username" placeholder="用户名" class="input" />
            <span class="ring"></span>
          </div>

          <div class="field">
            <input
              v-model="password"
              :type="showPwd ? 'text' : 'password'"
              autocomplete="current-password"
              placeholder="密码"
              class="input"
              @keyup.enter="submit"
            />
            <span class="ring"></span>
          </div>

          <div class="field">
            <input
              v-model.trim="googleCode"
              inputmode="numeric"
              maxlength="6"
              placeholder="谷歌验证码（未设置，可为空）"
              class="input"
            />
            <span class="ring"></span>
          </div>

          <div class="row">
            <label class="check">
              <input type="checkbox" v-model="showPwd" />
              显示密码
            </label>
            <span class="err" v-if="error">{{ error }}</span>
          </div>

          <button class="btn" :disabled="loading || !username || !password">
            <span v-if="!loading">登 录</span>
            <span v-else class="spin">登录中…</span>
          </button>
        </form>
      </div>
    </Transition>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import api from '@/api'
import { useAuth } from '@/store/auth'
import { useRoute } from 'vue-router'
const route = useRoute()

const router = useRouter()
const auth = useAuth()

const username = ref('')
const password = ref('')
const googleCode = ref('')

const showPwd = ref(false)
const loading = ref(false)
const error = ref('')
const mounted = ref(false)
const leaving = ref(false)
const zooming = ref(false)

const rootRef = ref(null)
const cardRef = ref(null)

function makeOrbs(n = 14) {
  const arr = []
  for (let i = 0; i < n; i++) {
    arr.push({
      x: Math.random() * 100,
      y: Math.random() * 100,
      size: 160 + Math.random() * 280,
      hue: Math.floor(Math.random() * 360),
      dur: 8 + Math.random() * 10,
      delay: -Math.random() * 8,
      scale: 0.7 + Math.random() * 1.1,
      tx: (Math.random() * 2 - 1) * 160,
      ty: (Math.random() * 2 - 1) * 160
    })
  }
  return arr
}
const orbs = ref(makeOrbs())

onMounted(async () => {
  if (route.query.expired === '1') {
    error.value = '登录过期，或被其他人登录此账号，请重新登录！'
  }
  await nextTick()
  mounted.value = true
})

const wait = (ms) => new Promise(r => setTimeout(r, ms))

async function submit () {
  if (loading.value) return
  error.value = ''
  if (!username.value || !password.value) {
    error.value = '请输入用户名和密码'
    return
  }
  loading.value = true
  try {
    const { data } = await api.post('/auth/login', {
      username: username.value,
      password: password.value,
      totp: googleCode.value || ''
    })
    if (data.code !== 0) {
      error.value = data.msg || '登录失败'
      return
    }
    const payload = data.data || {}
    if (payload.needTotp) {
      await playSuccessExit()
      router.replace({ path: '/totp', query: { u: username.value } })
      return
    }
    auth.setAuth(payload)
    await playSuccessExit()
    router.replace('/')
  } catch (e) {
    error.value = '网络错误或服务器不可用'
  } finally {
    loading.value = false
  }
}

async function playSuccessExit () {
  // 一镜到底：同时开始 → 2s 总时长
  leaving.value = true   // 卡片 0.6s 收起
  zooming.value = true   // 背景 2s 放大并淡出
  await wait(170)       // 等待 2s 后切页
}
</script>

<style scoped>
/* ===== 容器：居中 ===== */
.login-wrap {
  position: relative;
  height: 100vh; width: 100%;
  overflow: hidden; display: grid; place-items: center;
  background:
    radial-gradient(1200px 600px at 12% -10%, #0ea5e9 0%, transparent 60%),
    radial-gradient(1000px 600px at 110% 0%, #10b981 0%, transparent 55%),
    #070b13;
}

/* ===== 极光柔光 ===== */
.bg-aurora {
  position: absolute; inset: 0;
  background:
    conic-gradient(from 180deg at 50% 50%,
      rgba(56,189,248,.16),
      rgba(59,130,246,.16),
      rgba(168,85,247,.14),
      rgba(16,185,129,.16),
      rgba(56,189,248,.16));
  filter: blur(90px);
  mix-blend-mode: screen;
  animation: aurora 18s linear infinite;
  opacity: .65;
  will-change: opacity;
}
@keyframes aurora {
  0%   { transform: translateY(-2%) scale(1); }
  50%  { transform: translateY(2%)  scale(1.05); }
  100% { transform: translateY(-2%) scale(1); }
}

/* ===== 大网格：默认轻微抖动（质感），出场用 background-size 放大避免糊 ===== */
.bg-grid {
  position: absolute; inset: -3%;
  --c: rgba(255,255,255,.08);
  --s: 64px;          /* 初始格子大小 */
  --target: 1600px;   /* 目标背景单元尺寸（放大终点，可调大更夸张） */
  background:
    linear-gradient(var(--c) 1px, transparent 1px) 0 0 / var(--s) var(--s),
    linear-gradient(90deg, var(--c) 1px, transparent 1px) 0 0 / var(--s) var(--s);
  /* 关键：以中心为参考点，否则会从左上角“撑开” */
  background-position: 50% 60%, 50% 60%;
  mask-image: radial-gradient(closest-side at 50% 60%, #000 65%, transparent 100%);
  animation: gridJitter 12s ease-in-out infinite alternate;
  opacity: .75;
  backface-visibility: hidden;
  will-change: background-size, opacity, transform;
}

@keyframes gridJitter {
  0%   { transform: perspective(900px) rotateX(52deg) translateY(-4%); }
  40%  { transform: perspective(900px) rotateX(50deg) translateY(-2%); }
  70%  { transform: perspective(900px) rotateX(54deg) translateY(-3%); }
  100% { transform: perspective(900px) rotateX(52deg) translateY(-1%); }
}

/* ===== 光球（柔和漂浮） ===== */
.orbs { position: absolute; inset: 0; pointer-events: none; overflow: hidden; }
.orb {
  position: absolute; left: calc(var(--x)); top: calc(var(--y));
  width: var(--size); height: var(--size); border-radius: 50%;
  transform: translate(-50%, -50%) scale(var(--sx)) translateZ(0);
  background:
    radial-gradient(circle at 30% 30%, hsla(var(--hue), 90%, 70%, .9), transparent 60%),
    radial-gradient(circle at 70% 70%, hsla(calc(var(--hue) + 40), 90%, 60%, .8), transparent 60%);
  filter: blur(12px) saturate(120%); mix-blend-mode: screen;
  animation: orbFloat var(--dur) ease-in-out var(--delay) infinite alternate;
  will-change: transform, opacity;
}
@keyframes orbFloat {
  0%   { transform: translate(calc(-50% + 0px), calc(-50% + 0px)) scale(calc(var(--sx) * 0.95)) translateZ(0); }
  100% { transform: translate(calc(-50% + var(--tx)), calc(-50% + var(--ty))) scale(calc(var(--sx) * 1.12)) translateZ(0); }
}

/* ===== 成功出场（总 2s）：网格用 background-size 放大，保持线条锐利 ===== */
.login-wrap.outro .bg-grid {
  /* 单独跑 zoom 动画，暂停掉抖动不再复合 */
  animation: grid-zoom 2s cubic-bezier(.22,.82,.17,1) forwards;
}
@keyframes grid-zoom {
  /* 从当前 --s 平滑放大到 --target，期间保持高不透明度便于看清放大 */
  0% {
    background-size: var(--s) var(--s), var(--s) var(--s);
    opacity: .95;
    transform: perspective(900px) rotateX(52deg) translateY(-4%);
  }
  60% {
    /* 中段已经明显变大，但仍然清晰可见 */
    background-size: calc(var(--s) * 6) calc(var(--s) * 6),
                      calc(var(--s) * 6) calc(var(--s) * 6);
    opacity: .95;
    transform: perspective(900px) rotateX(50deg) translateY(-3%);
  }
  100% {
    /* 终点继续放大到巨幅，再一起淡出 */
    background-size: var(--target) var(--target), var(--target) var(--target);
    opacity: 0;
    transform: perspective(900px) rotateX(48deg) translateY(-2%);
  }
}
.login-wrap.outro .bg-aurora {
  animation-play-state: paused;
  opacity: 0;
  transition: opacity 2s ease;
}
.login-wrap.outro .orb {
  animation-play-state: paused;
  opacity: 0;
  transform: translate(-50%,-50%) scale(0.9) translateZ(0);
  transition: opacity 1.6s ease, transform 1.6s ease;
}

/* ===== 登录卡片 ===== */
.card {
  position: relative;
  width: min(92vw, 600px);
  padding: 34px 34px 24px;
  border-radius: 20px;
  box-shadow: 0 26px 80px rgba(2,6,23,.55), inset 0 1px 0 rgba(255,255,255,.06);
  transform-style: preserve-3d;
}
.glass { background: rgba(13,18,28,.62); backdrop-filter: blur(12px) saturate(140%); border: 1px solid rgba(255,255,255,.09); }
.title { font-size: 28px; font-weight: 900; letter-spacing: .3px; color: #fff; }
.sub { color: rgba(255,255,255,.66); font-size: 13px; margin-top: 6px; }
.form { margin-top: 22px; display: grid; gap: 16px; }

.field { position: relative; }
.input {
  width: 100%; padding: 14px 16px; border-radius: 14px;
  background: rgba(255,255,255,.06); border: 1px solid rgba(255,255,255,.12);
  color: #fff; outline: none; transition: border .2s, box-shadow .2s, background .2s; font-size: 14px;
}
.input::placeholder { color: rgba(255,255,255,.38); }
.input:focus { border-color: rgba(56,189,248,.65); box-shadow: 0 0 0 6px rgba(56,189,248,.16); background: rgba(255,255,255,.08); }
.ring { position: absolute; inset: 0; pointer-events: none; border-radius: 14px; box-shadow: 0 0 0 0 rgba(99,102,241,0); transition: box-shadow .25s; }
.input:focus + .ring { box-shadow: 0 0 0 8px rgba(99,102,241,.12) inset; }

.row { display: flex; align-items: center; justify-content: space-between; margin-top: 2px; }
.check { display: inline-flex; align-items: center; gap: 8px; color: rgba(255,255,255,.7); font-size: 12px; cursor: pointer; }
.err { color: #fda4af; font-size: 12px; }

/* 按钮 */
.btn {
  width: 100%; margin-top: 6px; padding: 14px 16px; border-radius: 14px;
  font-weight: 800; letter-spacing: .2px; color: #fff;
  background: linear-gradient(90deg, #06b6d4, #3b82f6, #8b5cf6);
  background-size: 220% 100%;
  border: 0; cursor: pointer;
  transition: transform .16s, filter .22s, background-position .9s;
}
.btn:hover { background-position: 100% 0; filter: brightness(1.06); }
.btn:active { transform: translateY(1px) scale(.995); }
.btn:disabled { opacity: .6; cursor: not-allowed; }

/* 入场 */
.pop-enter-from { opacity: 0; transform: translateY(28px) scale(.95) rotateX(8deg); filter: blur(4px); }
.pop-enter-active { transition: all .6s cubic-bezier(.2,.8,.2,1); }
.pop-enter-to { opacity: 1; transform: translateY(0) scale(1) rotateX(0); filter: blur(0); }

/* 出场：卡片 0.6s 收起，但不阻塞 2s 主过场 */
.leaving { animation: card-leave .6s cubic-bezier(.2,.8,.2,1) forwards; transform-origin: center; }
@keyframes card-leave {
  0% { transform: translateY(0) scale(1); opacity: 1; filter: drop-shadow(0 26px 80px rgba(2,6,23,.55)); }
  60% { transform: translateY(-6px) scale(1.05); }
  100% { transform: translateY(-10px) scale(.88); opacity: 0; filter: blur(8px); }
}

/* 降低动画偏好时更温和 */
@media (prefers-reduced-motion: reduce) {
  .bg-aurora, .bg-grid, .orb { animation: none !important; }
  .login-wrap.outro .bg-grid { transition: opacity 1s ease; opacity: 0; }
}
</style>
