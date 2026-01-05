<template>
  <div class="sub-page">
    <!-- 动态极光 + 粒子 -->
    <div class="bg-aurora"></div>
    <div class="bg-noise"></div>
    <div class="particles">
      <span v-for="n in 30" :key="n" class="p"></span>
    </div>

    <header class="hero">
      <h1 class="hero-title">在线客服 · 专业订购</h1>
      <p class="hero-sub">更快、更稳、更智能的客服工作台。选择你的成长曲线。</p>

      <!-- 计费周期 -->
      <div class="billing">
        <button
          class="toggle"
          :class="{ active: cycle==='monthly' }"
          @click="cycle='monthly'"
        >月度</button>
        <button
          class="toggle"
          :class="{ active: cycle==='yearly' }"
          @click="cycle='yearly'"
        >
          年度
          <span class="off">-20%</span>
        </button>
      </div>
    </header>

    <!-- 方案卡组 -->
    <section class="tiers">
      <div
        v-for="t in decoratedTiers"
        :key="t.id"
        class="tier-card"
        :data-variant="t.id"
        :style="t.cardStyle"
        @mousemove="onTilt"
        @mouseleave="resetTilt"
      >
        <!-- 顶部徽章 / 丝带 -->
        <div class="ribbon" v-if="t.ribbon">{{ t.ribbon }}</div>

        <div class="gloss"></div>
        <div class="shine"></div>

        <div class="head">
          <div class="title">
            <span class="name">{{ t.name }}</span>
            <span class="tag">{{ t.tag }}</span>
          </div>
          <div class="price">
            <span class="currency">US$</span>
            <span class="amount">{{ t.price }}</span>
            <span class="period">/{{ cycle === 'monthly' ? '月' : '年' }}</span>
          </div>
          <div class="desc">{{ t.desc }}</div>
        </div>

        <!-- 功能清单 -->
        <ul class="features">
          <li v-for="f in t.allFeatures" :key="f.key" :class="{ on: f.on, off: !f.on }">
            <span class="ico" aria-hidden="true">
              <svg v-if="f.on" viewBox="0 0 24 24"><path d="M4 12l5 5 11-11" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round"/></svg>
              <svg v-else viewBox="0 0 24 24"><path d="M6 10a6 6 0 0 1 12 0v3h1a2 2 0 0 1 0 4H5a2 2 0 0 1 0-4h1z" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round"/></svg>
            </span>
            <span class="txt">{{ f.text }}</span>
          </li>
        </ul>

        <!-- CTA -->
        <div class="cta">
          <button class="btn primary" @click="selectPlan(t)">
            立即订购
          </button>
          <button
            v-if="t.contact"
            class="btn ghost"
            @click="contactSales(t)"
          >
            联系销售
          </button>
        </div>
      </div>
    </section>

    <!-- 礼花层 -->
    <transition name="confetti">
      <canvas v-if="boom" ref="canvas" class="confetti"></canvas>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

type Cycle = 'monthly' | 'yearly'
const cycle = ref<Cycle>('monthly')
const router = useRouter()

/** 方案与功能矩阵 */
const BASE = 'basic'
const PRO  = 'pro'
const ENT  = 'enterprise'

const allFeatureCatalog = [
  { key: 'chat_core',        text: '核心客服面板（对话、快捷回复、标签）' },
  { key: 'seats',            text: '坐席：{{n}} 人' },
  { key: 'image_video',      text: '图片 / 视频 / 文件消息' },
  { key: 'realtime_translate',text: '实时翻译（中英/多语）' },
  { key: 'voice_notes',      text: '语音消息（录音 / 播放）' },
  { key: 'ai_copilot',       text: 'AI 智能助理（推荐回复 / 自动摘要）' },
  { key: 'omnichannel',      text: '多渠道聚合（网站 / Telegram / WhatsApp）' },
  { key: 'automation',       text: '自动化工单 / 规则 / 审批' },
  { key: 'sla',              text: 'SLA 协议&监控' },
  { key: 'api',              text: '开放 API & Webhook' },
  { key: 'sso',              text: '企业 SSO / 组织权限' },
  { key: 'analytics',        text: '高级数据分析与报表' },
]

const rawTiers = reactive([
  {
    id: BASE,
    name: '基础版',
    tag: '起步必备',
    desc: '小团队快速启用，轻量高效。',
    monthly: 19, yearly: 19 * 12 * 0.8, // 年付 8 折
    ribbon: '',
    seats: 3,
    enable: new Set([
      'chat_core','seats','image_video'
    ]),
    cardStyle: '--hue: 210; --sat: 85%; --lum: 58%; --depth: 18;'
  },
  {
    id: PRO,
    name: '专业版',
    tag: '增长利器',
    desc: '更强工作流与多语种协作。',
    monthly: 49, yearly: 49 * 12 * 0.8,
    ribbon: '热销',
    seats: 10,
    enable: new Set([
      'chat_core','seats','image_video','realtime_translate','voice_notes','ai_copilot','omnichannel','analytics'
    ]),
    cardStyle: '--hue: 265; --sat: 88%; --lum: 60%; --depth: 26;'
  },
  {
    id: ENT,
    name: '企业尊享版',
    tag: '极致体验',
    desc: '安全、性能、可观测，一次到位。',
    monthly: 129, yearly: 129 * 12 * 0.8,
    ribbon: '旗舰',
    seats: 50,
    enable: new Set([
      'chat_core','seats','image_video','realtime_translate','voice_notes','ai_copilot','omnichannel','automation','sla','api','sso','analytics'
    ]),
    cardStyle: '--hue: 45; --sat: 95%; --lum: 58%; --depth: 34;'
  }
])

const priceFmt = (n:number) => (Math.round(n) === n ? n : n.toFixed(0))

const decoratedTiers = computed(() => {
  return rawTiers.map(t => {
    const price = cycle.value === 'monthly' ? t.monthly : t.yearly
    const allFeatures = allFeatureCatalog.map(f => {
      const on = t.enable.has(f.key)
      const text = f.key === 'seats' ? f.text.replace('{{n}}', String(t.seats)) : f.text
      return { ...f, on, text }
    })
    return {
      ...t,
      price: priceFmt(price),
      allFeatures
    }
  })
})

/** 选择方案 → 跳转结算 */
const checkoutUrl = (import.meta as any).env?.VITE_PUBLIC_CHECKOUT_URL || '/checkout'
function selectPlan(t:any){
  fire()
  const url = typeof checkoutUrl === 'string' ? checkoutUrl : '/checkout'
  // 支持站内 / 外链
  if (url.startsWith('http')) {
    const u = new URL(url)
    u.searchParams.set('plan', t.id)
    u.searchParams.set('cycle', cycle.value)
    window.open(u.toString(), '_blank')
  } else {
    router.push({ path: url, query: { plan: t.id, cycle: cycle.value } })
  }
}

/** 联系销售 */
const contactUrl = (import.meta as any).env?.VITE_PUBLIC_SALES_URL || '/contact/sales'
function contactSales(t:any){
  if (String(contactUrl).startsWith('http')) window.open(contactUrl, '_blank')
  else router.push({ path: contactUrl, query: { plan: t.id } })
}

/** 3D 倾斜 */
function onTilt(e:MouseEvent){
  const el = e.currentTarget as HTMLElement
  const r = el.getBoundingClientRect()
  const px = (e.clientX - r.left) / r.width - 0.5
  const py = (e.clientY - r.top) / r.height - 0.5
  const rotateX = (+py) * -8
  const rotateY = (+px) * 12
  el.style.transform = `rotateX(${rotateX}deg) rotateY(${rotateY}deg) translateZ(0)`
  el.style.setProperty('--tx', `${px*12}px`)
  el.style.setProperty('--ty', `${py*12}px`)
}
function resetTilt(e:MouseEvent){
  const el = e.currentTarget as HTMLElement
  el.style.transform = ''
  el.style.setProperty('--tx', `0px`)
  el.style.setProperty('--ty', `0px`)
}

/** 简易礼花 */
const boom = ref(false)
const canvas = ref<HTMLCanvasElement|null>(null)
let raf = 0
function fire(){
  boom.value = true
  setTimeout(() => {
    drawConfetti()
    setTimeout(() => {
      cancelAnimationFrame(raf)
      boom.value = false
    }, 900)
  })
}
function drawConfetti(){
  const cvs = canvas.value
  if(!cvs) return
  const ctx = cvs.getContext('2d')!
  const W = cvs.width = window.innerWidth
  const H = cvs.height = 320
  const pieces = Array.from({length: 80}).map(() => ({
    x: Math.random()*W, y: Math.random()*H/2, r: 4+Math.random()*6,
    vx: -2+Math.random()*4, vy: 1+Math.random()*3, a: Math.random()*Math.PI*2
  }))
  const step = () => {
    ctx.clearRect(0,0,W,H)
    pieces.forEach(p=>{
      p.x+=p.vx; p.y+=p.vy; p.a+=0.1
      ctx.save()
      ctx.translate(p.x,p.y)
      ctx.rotate(p.a)
      ctx.fillStyle = `hsl(${Math.random()*360},90%,60%)`
      ctx.fillRect(-p.r/2,-p.r/2,p.r,p.r)
      ctx.restore()
    })
    raf = requestAnimationFrame(step)
  }
  step()
}

onMounted(()=> {
  window.addEventListener('resize', onResize)
})
onBeforeUnmount(()=> {
  window.removeEventListener('resize', onResize)
  cancelAnimationFrame(raf)
})
function onResize(){
  if(canvas.value){
    canvas.value.width = window.innerWidth
  }
}
</script>

<style scoped>
/* ================== 全局舞台 ================== */
.sub-page{
  position: relative;
  min-height: 100vh;
  padding: 60px 24px 80px;
  display: grid;
  grid-template-rows: auto 1fr;
  gap: 36px;
  color: #e5ecff;
  overflow: clip;
}
.bg-aurora{
  position: absolute; inset: -20% -10% -10% -10%;
  background:
    radial-gradient(1200px 480px at 15% 0%, rgba(99,102,241,.28), transparent 60%),
    radial-gradient(1000px 520px at 85% 10%, rgba(56,189,248,.25), transparent 60%),
    conic-gradient(from 180deg at 50% 50%, rgba(59,130,246,.12), rgba(168,85,247,.1), rgba(20,184,166,.12), rgba(59,130,246,.12));
  filter: blur(40px) saturate(120%);
  pointer-events: none;
  animation: aurora 16s linear infinite;
}
@keyframes aurora{ 0%{ transform: translateY(0) } 50%{ transform: translateY(-2%) } 100%{ transform: translateY(0) } }
.bg-noise{
  position:absolute; inset:-20px; pointer-events:none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='120' height='120'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.8' numOctaves='2' stitchTiles='stitch'/%3E%3CfeColorMatrix values='0 0 0 0 0  0 0 0 0 0  0 0 0 0 0  0 0 0 -.85 1'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)' opacity='.08'/%3E%3C/svg%3E");
  mix-blend-mode: overlay;
}

/* 小粒子 */
.particles{ position:absolute; inset:0; pointer-events:none; overflow:hidden }
.particles .p{
  position:absolute; width:6px; height:6px; border-radius:50%;
  background: #9cc0ff; opacity:.6;
  animation: float 12s linear infinite;
  left: calc(100% * var(--x, .5));
  top: calc(100% * var(--y, .5));
}
.particles .p:nth-child(odd){ background:#a78bfa }
.particles .p:nth-child(3n){ background:#5eead4 }
.particles .p{ --x: calc((var(--i, 1) * 37) % 100 / 100); --y: calc((var(--i, 1) * 53) % 100 / 100) }
.particles .p:nth-child(n){ --i: 1 }
.particles .p:nth-child(2n){ --i: 2 }
.particles .p:nth-child(3n){ --i: 3 }
.particles .p:nth-child(4n){ --i: 4 }
.particles .p:nth-child(5n){ --i: 5 }
@keyframes float{ 0%{ transform: translateY(0)} 50%{ transform: translateY(-20px)} 100%{ transform: translateY(0)} }

/* ================== 头部 ================== */
.hero{ text-align:center; position:relative; z-index:2 }
.hero-title{
  font-size: clamp(26px, 4vw, 42px);
  font-weight: 900;
  letter-spacing: .4px;
  color: #fff;
  text-shadow: 0 10px 40px rgba(0,0,0,.45);
}
.hero-sub{
  margin-top: 10px;
  color: #c7d4eb;
  opacity: .9;
}
.billing{
  display:inline-flex; margin-top: 18px; gap: 10px; padding: 6px;
  background: rgba(255,255,255,.06); border:1px solid rgba(255,255,255,.12);
  border-radius: 14px; box-shadow: inset 0 1px 0 rgba(255,255,255,.08);
}
.toggle{
  min-width: 96px; padding: 10px 14px; border-radius: 10px; border: 0; cursor:pointer;
  color:#cfe2ff; background: transparent; font-weight: 800; letter-spacing:.3px;
  transition: .18s;
}
.toggle.active{
  color:#071327; background: linear-gradient(135deg, #7dd3fc, #a78bfa);
  box-shadow: 0 10px 30px rgba(2,6,23,.45);
}
.toggle .off{
  margin-left: 8px; font-size: 12px; padding:2px 6px; border-radius: 6px;
  background: rgba(250,204,21,.9); color:#1a1203; font-weight:900;
}

/* ================== 方案卡 ================== */
.tiers{
  display:grid; gap: 18px;
  grid-template-columns: repeat(1, minmax(0, 1fr));
  max-width: 1200px; margin: 0 auto; width: 100%;
}
@media (min-width: 900px){
  .tiers{ grid-template-columns: repeat(3, 1fr); }
}

.tier-card{
  position: relative;
  overflow: hidden;
  border-radius: 20px;
  padding: 22px 18px 20px;
  background:
    linear-gradient(180deg, rgba(255,255,255,.06), rgba(255,255,255,.04));
  border: 1px solid rgba(255,255,255,.14);
  backdrop-filter: blur(6px);
  box-shadow: 0 20px 80px rgba(0,0,0,.45);
  transform-style: preserve-3d;
  transition: transform .18s ease, box-shadow .2s ease, border-color .2s ease;
  --tx: 0px; --ty: 0px;
}
.tier-card:hover{
  box-shadow: 0 28px 110px rgba(0,0,0,.55);
  border-color: rgba(255,255,255,.24);
}
.tier-card::before{
  content:""; position:absolute; inset:-1px; border-radius: inherit;
  background: conic-gradient(from 0deg, hsla(var(--hue),var(--sat),var(--lum),.22), transparent 30% 70%, hsla(var(--hue),var(--sat),var(--lum),.22));
  mask: linear-gradient(#000, transparent 40%);
  pointer-events: none; filter: blur(6px);
}
.tier-card .gloss{
  content:""; position:absolute; inset:0; background: radial-gradient(600px 180px at calc(50% + var(--tx)) calc(0% + var(--ty)), rgba(255,255,255,.22), transparent 60%);
  pointer-events:none; mix-blend-mode: overlay;
}
.tier-card .shine{
  position:absolute; inset:-100% -100% auto -100%; height: 60%; transform: rotate(8deg);
  background: linear-gradient(90deg, transparent, rgba(255,255,255,.18), transparent);
  animation: shine 4s linear infinite;
  pointer-events:none;
}
@keyframes shine{ 0%{ transform: translateX(-20%) rotate(8deg) } 100%{ transform: translateX(20%) rotate(8deg) } }

/* 顶部丝带 */
.ribbon{
  position:absolute; right: -38px; top: 14px;
  transform: rotate(35deg);
  background: linear-gradient(135deg, #f59e0b, #fde68a);
  color:#1c1402; font-weight: 900;
  padding: 6px 50px; border-radius: 10px;
  box-shadow: 0 12px 30px rgba(245,158,11,.3);
}

/* 头部 */
.head{ position: relative; z-index: 2 }
.title{ display:flex; align-items: baseline; gap: 10px }
.name{ font-weight: 900; font-size: 18px; color: #fff }
.tag{
  font-size: 12px; padding: 2px 8px; border-radius: 999px; font-weight: 900;
  background: rgba(255,255,255,.08); border: 1px solid rgba(255,255,255,.14);
}
.price{
  margin-top: 8px; display:flex; align-items: flex-end; gap: 6px;
  color:#fff; text-shadow: 0 8px 30px rgba(0,0,0,.45);
}
.currency{ opacity:.85 }
.amount{ font-size: 42px; line-height: .9; font-weight: 900; letter-spacing: .5px }
.period{ opacity:.8; margin-bottom: 6px }
.desc{ margin-top: 10px; color:#cdd8ee }

/* 功能列表 */
.features{ margin-top: 16px; display:grid; gap: 8px }
.features li{
  display:flex; align-items:center; gap: 10px;
  padding: 8px 10px; border-radius: 12px;
  background: rgba(255,255,255,.04);
  border: 1px solid rgba(255,255,255,.08);
}
.features li .ico{ width: 18px; height: 18px; display:grid; place-items:center }
.features li.on{ color:#d9fff0; border-color: rgba(16,185,129,.26); background: linear-gradient(180deg, rgba(16,185,129,.16), rgba(255,255,255,.04)) }
.features li.off{ color:#9db0cf; opacity:.85 }
.features li.off .ico{ opacity:.7 }

/* CTA */
.cta{ margin-top: 14px; display:flex; gap: 10px }
.btn{
  padding: 12px 14px; border-radius: 12px; border: 1px solid rgba(255,255,255,.14);
  color:#e8efff; background: rgba(255,255,255,.06); font-weight: 900; letter-spacing:.3px;
  cursor:pointer; transition:.16s;
}
.btn:hover{ transform: translateY(-1px); box-shadow: 0 12px 30px rgba(2,6,23,.35) }
.btn.primary{
  background: linear-gradient(135deg, hsla(var(--hue),calc(var(--sat) + 2%),calc(var(--lum) + 3%),.95), hsla(calc(var(--hue) + 45),var(--sat),calc(var(--lum) + 8%),.95));
  border: 0; color:#0b0f1d;
}
.btn.ghost{ background: transparent }

/* 不同级别进一步美化 */
.tier-card[data-variant="basic"] .amount{ background: linear-gradient(90deg,#93c5fd,#22d3ee); -webkit-background-clip: text; color: transparent }
.tier-card[data-variant="pro"] .amount{ background: linear-gradient(90deg,#c084fc,#f472b6); -webkit-background-clip: text; color: transparent }
.tier-card[data-variant="enterprise"] .amount{ background: linear-gradient(90deg,#60a5fa,#f59e0b,#fde68a); -webkit-background-clip: text; color: transparent }

/* 礼花 */
.confetti{
  position: fixed; left:0; right:0; top: 80px; height: 320px; pointer-events:none; z-index: 50;
}
.confetti-enter-from{ opacity:0; transform: translateY(-10px) }
.confetti-enter-active{ transition: all .18s ease }
.confetti-enter-to{ opacity:1; transform:none }
.confetti-leave-active{ transition: all .12s ease }
.confetti-leave-to{ opacity:0; transform: translateY(-10px) }
</style>
