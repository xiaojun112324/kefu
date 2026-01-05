<template>
  <div class="ps-page">
    <!-- 顶部横幅 -->
    <section class="hero card animate-fade-in">
      <div class="hero-bg"></div>

      <div class="hero-content">
        <!-- LOGO（有才显示，无就只留容器，不会出现裂图） -->
        <div class="logo-wrap animate-pop">
          <img
            v-if="showLogo"
            :src="logoSrc"
            alt="brand"
            class="logo-img"
            @error="onLogoError"
            @load="onLogoLoad"
          />
          <div class="scan-lines"></div>
        </div>

        <h1 class="title">
          {{ form.brandName || displayName || '你的平台' }}
        </h1>
        <p class="subtitle">
          {{ form.brandIntro || '欢迎来到代理平台设置中心' }}
        </p>
      </div>
    </section>

    <!-- 信息 + 表单 -->
    <section class="grid grid-cols-1 xl:grid-cols-[380px_1fr] gap-6 mt-6">
      <!-- 左侧信息栏 -->
      <aside class="card p-5 space-y-4 animate-fade-up">
        <h2 class="section-title">基础信息</h2>

        <Info label="UID" :value="user?.id" mono />
        <Info label="当前角色" :value="user?.role" />
        <Info label="上级代理UID" :value="user?.agentId ?? '-'" mono />
        <Info label="创建时间" :value="fmtTime(user?.createdAt)" />
        <Info label="到期时间" :value="fmtTime(user?.expireAt)" variant="accent" />
        <Info label="总坐席数" :value="user?.seatCount ?? '-'" />
       

        <!-- ↓↓↓ 新增：产品激活码兑换 ↓↓↓ -->
        <div class="sep"></div>
        <h3 class="subsection-title">产品激活码</h3>
        <div class="redeem-wrap">
          <input
            v-model.trim="redeemCode"
            @keyup.enter="doRedeem"
            class="ipt redeem-input"
            placeholder="输入产品激活码"
          />
          <button
            class="pill primary"
            :disabled="redeeming || !redeemCode"
            @click="doRedeem"
          >
            <span v-if="redeeming" class="loader"></span>
            兑换
          </button>
        </div>
        <p v-if="redeemErr" class="redeem-err">{{ redeemErr }}</p>

        <!-- 兑换结果：仅显示 “兑换的东西 与 数量” -->
        <div v-if="redeemOk" class="redeem-result">
          <div class="rr-title">
            <span class="dot ok"></span>兑换成功
          </div>
          <div class="rr-grid">
            <div class="rr-item">
              <div class="k">兑换项目</div>
              <div class="v">{{ redeemItemText }}</div>
            </div>
            <div class="rr-item">
              <div class="k">增加数量</div>
              <div class="v">{{ redeemQtyText }}</div>
            </div>
          </div>
        </div>
        <!-- ↑↑↑ 新增：产品激活码兑换 ↑↑↑ -->

        <div class="sep"></div>
        <div class="flex items-center gap-2">
          <span class="text-sm text-white/70">推广链接</span>
          <button class="tiny-pill" @click="copyInvite" :disabled="!form.code">复制</button>
        </div>
        <div class="invite-url" :title="inviteUrl">{{ inviteUrl }}</div>
        <p v-if="!form.code" class="text-[12px] text-amber-300/80">
          * 当前没有配置推广码（code），请联系管理员设置后再复制链接
        </p>
      </aside>

      <!-- 右侧：可编辑表单 -->
      <main class="card p-6 animate-fade-up-delayed">
        <h2 class="section-title">平台设置</h2>

        <form class="form-grid" @submit.prevent="save">
          <!-- 账号与展示 -->
          <FormItem label="用户名">
            <input v-model.trim="form.username" type="text" class="ipt" placeholder="用户名" />
          </FormItem>

          <FormItem label="显示名 / 昵称">
            <input v-model.trim="form.displayName" type="text" class="ipt" placeholder="给客服端展示的名字" />
          </FormItem>

          <FormItem label="邮箱">
            <input v-model.trim="form.email" type="email" class="ipt" placeholder="name@example.com" />
          </FormItem>

          <FormItem label="手机号">
            <input v-model.trim="form.phone" type="tel" class="ipt" placeholder="手机号" />
          </FormItem>

          <div class="sep mt-1 mb-2 col-span-2"></div>

          <FormItem label="客服名称">
            <input v-model.trim="form.csName" type="text" class="ipt" placeholder="对话框/客服的名称" />
          </FormItem>
          <FormItem label="客服头像">
            <div class="uploader">
              <input ref="csLogoInput" type="file" accept="image/*" class="hidden"
                    @change="e => onPickLogo(e, 'cs')" />
              <button type="button" class="pill" @click="csLogoInput?.click()" :disabled="uploadingLogo">
                <span v-if="uploadingLogo" class="loader"></span>
                {{ uploadingLogo ? '上传中…' : '选择图片' }}
              </button>
              <span class="hint">建议正方形 ≥ 256×256，PNG / JPG</span>
                <img
                  v-if="csLogoPreview && !csLogoError"
                  :src="csLogoPreview"
                  class="logo-preview"
                  @error="onCsLogoError"
                  @load="onCsLogoLoad"
                />
            </div>
          </FormItem>
          <!-- 品牌 -->
          <FormItem label="平台名称">
            <input v-model.trim="form.brandName" type="text" class="ipt" placeholder="品牌/平台名" />
          </FormItem>

          <!-- 上传 LOGO + 预览 -->
          <FormItem label="平台 LOGO">
            <div class="uploader">
              <input ref="logoInput" type="file" accept="image/*" class="hidden" @change="onPickLogo" />
              <button type="button" class="pill" @click="logoInput?.click()" :disabled="uploadingLogo">
                <span v-if="uploadingLogo" class="loader"></span>
                {{ uploadingLogo ? '上传中…' : '选择图片' }}
              </button>
              <span class="hint">建议正方形 ≥ 256×256，PNG / JPG</span>
              <img
                v-if="logoPreview && !logoError"
                :src="logoPreview"
                class="logo-preview"
                @error="onLogoError"
                @load="onLogoLoad"
              />
            </div>
          </FormItem>

          <FormItem class="col-span-2" label="平台介绍">
            <textarea v-model.trim="form.brandIntro" rows="4" class="ipt" placeholder="一句话/一段介绍"></textarea>
          </FormItem>

          <FormItem label="平台域名">
            <div class="flex flex-col gap-1 w-full">
              <input
                v-model.trim="form.domain"
                type="url"
                class="ipt"
                placeholder="例如：https://aaa.com/"
              />
              <span class="text-xs text-white/60">请输入完整网址，建议包含 https:// 前缀, 同时/ 结尾</span>
            </div>
          </FormItem>


          <FormItem class="col-span-2" label="自动回复（最多 3 条）">
            <div class="ar-wrap">
              <div
                v-for="(r, i) in autoReplies"
                :key="i"
                class="ar-row"
              >
                <input
                  class="ipt ar-input"
                  :placeholder="`第 ${i + 1} 条`"
                  :value="r"
                  @input="onReplyInput(i, ($event.target as HTMLInputElement).value)"
                />
                <button type="button" class="tiny-pill danger" @click="removeReply(i)">删除</button>
              </div>
            </div>

            <!-- 按钮放到外层，就不会和横向滚动的内容冲突 -->
            <button
              v-if="autoReplies.length < 3"
              type="button"
              class="pill ar-add"
              @click="addReply"
              title="最多 3 条"
            >
              + 新增一条
            </button>

            <div class="ar-hint">保存时会按顺序以英文逗号`,`拼接提交到后端</div>
          </FormItem>

          <div class="col-span-2 flex items-center justify-end gap-3 mt-4">
            <button type="button" class="pill outline" @click="reset">重置</button>
            <button type="submit" class="pill primary" :disabled="saving">
              <span v-if="saving" class="loader"></span>
              保存
            </button>
          </div>
        </form>
      </main>
    </section>

    <!-- 轻量吐司 -->
    <transition name="toast">
      <div v-if="toastMsg" class="toast">{{ toastMsg }}</div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, onMounted, reactive, ref } from 'vue'
import api from '@/api'
import { useAuth } from '@/store/auth'
import { useRoute, useRouter } from 'vue-router'

/** ---------------- 小组件：Info ---------------- */
const Info = defineComponent({
  name: 'Info',
  props: {
    label: { type: String, required: true },
    value: { type: [String, Number, Boolean, Object, Array] as any, default: '-' },
    mono:  { type: Boolean, default: false },
    badge: { type: Boolean, default: false },
    variant:{ type: String, default: '' } // '', 'accent'
  },
  setup (props, { slots }) {
    return () =>
      h('div', { class: 'info-block' }, [
        h('div', { class: 'label' }, props.label),
        h('div', { class: ['value', props.mono && 'mono', props.badge && 'badge', props.variant && `v-${props.variant}`] }, String(props.value ?? '-')),
        slots.default?.()
      ])
  }
})

/** ---------------- 小组件：FormItem ---------------- */
const FormItem = defineComponent({
  name: 'FormItem',
  props: { label: { type: String, required: true } },
  setup (props, { slots }) {
    return () =>
      h('div', { class: 'fi' }, [
        h('div', { class: 'fi-label' }, props.label),
        h('div', { class: 'fi-body' }, slots.default?.() ?? [])
      ])
  }
})

/** ---------------- 状态 ---------------- */
const route = useRoute()
const router = useRouter()
const auth = useAuth()

// 优先 ?id=，否则用当前登录用户
const uid = computed(() => Number(route.query.id ?? auth.userId))
const displayName = computed(() => auth.username)

// LOGO 源与错误控制：失败则不显示图像
const logoError = ref(false)
const tempLogoPreview = ref<string>('')
const logoPreview = computed(() => tempLogoPreview.value || form.brandLogo || '')
const logoSrc = computed(() => logoPreview.value)
const showLogo = computed(() => !!logoPreview.value && !logoError.value)

// 客服头像预览
const tempCsLogoPreview = ref<string>('')  
const csLogoPreview = computed(() => tempCsLogoPreview.value || form.csLogo || '')

type UserDto = {
  id: number
  agentId: number | null
  role: string
  username: string
  displayName?: string | null
  email?: string | null
  phone?: string | null
  code?: string | null
  brandName?: string | null
  brandTitle?: string | null
  brandLogo?: string | null
  brandIntro?: string | null
  createdAt?: string
  expireAt?: string | null
  seatCount?: number | null
  csName?: string | null
  csLogo?: string | null
  autoReply?: string | null
}

type RedeemResp = {
  codeId: number
  code: string
  function: 1 | 2
  value: number
  userId: number
  newExpireAt?: string | null
  newSeatCount?: number | null
}

const user = ref<UserDto | null>(null)
const form = reactive({
  username: '',
  displayName: '',
  email: '',
  phone: '',
  code: '',
  brandName: '',
  brandLogo: '',
  brandIntro: '',
  csName: '',
  csLogo: '',
  autoReply: '',
  domain: '',
})
let originalSnapshot: any = null
const saving = ref(false)
const toastMsg = ref('')

/** 上传相关 */
const logoInput = ref<HTMLInputElement|null>(null)
const uploadingLogo = ref(false)

const csLogoInput = ref<HTMLInputElement|null>(null)

/** 推广链接 */
const inviteUrl = computed(() => {
  const code = form.code?.trim()
  const domain = form.domain?.trim()
  if (!domain) return code ? `https://kefu.ise-app.xyz?code=${encodeURIComponent(code)}` : `https://kefu.ise-app.xyz?code=`
  const base = domain.endsWith('/') ? domain.slice(0, -1) : domain
  return code ? `${base}?code=${encodeURIComponent(code)}` : `${base}?code=`
})


const csLogoError = ref(false)
function onCsLogoError () { csLogoError.value = true }
function onCsLogoLoad () { csLogoError.value = false }

/** ---------------- 兑换逻辑 ---------------- */
const redeemCode = ref('')
const redeeming = ref(false)
const redeemErr = ref('')
const redeemResult = ref<RedeemResp | null>(null)

const redeemOk = computed(() => !!redeemResult.value)
const redeemItemText = computed(() => {
  const fn = redeemResult.value?.function
  if (fn === 1) return '可用天数'
  if (fn === 2) return '座席数'
  return '-'
})
const redeemQtyText = computed(() => {
  const fn = redeemResult.value?.function
  const val = redeemResult.value?.value ?? 0
  if (fn === 1) return `${val} 天`
  if (fn === 2) return `${val} 个`
  return '-'
})

/** ---------------- 自动回复（最多 5 条） ---------------- */
// 将后端的逗号串 -> 数组（过滤空白）
function splitAutoReply(s: string): string[] {
  if (!s) return []
  return s.split(',').map(v => v.trim()).filter(v => v.length > 0)
}
// 将数组 -> 逗号串（发给后端）
function joinAutoReply(arr: string[]): string {
  return arr.map(v => v.trim()).filter(v => v.length > 0).join(',')
}

// 可编辑数组（用于 UI 展示与单条编辑/删除）
const autoReplies = ref<string[]>([])

// 输入时：不允许英文逗号，统一替换成全角逗号，避免和分隔符冲突
function sanitizeInput(s: string) {
  return s.replace(/,/g, '，')  // 如果你允许英文逗号就删掉这行
}

// 新增一条
function addReply() {
  if (autoReplies.value.length >= 3) { showToast('最多只能添加 3 条'); return }
  autoReplies.value.push('')
}

// 删除指定下标
function removeReply(index: number) {
  autoReplies.value.splice(index, 1)
}

// 单条输入变更
function onReplyInput(index: number, val: string) {
  autoReplies.value[index] = sanitizeInput(val)
}

// 保存前同步到 form.autoReply（由数组拼成逗号串）
function syncAutoReplyToForm() {
  form.autoReply = joinAutoReply(autoReplies.value.slice(0, 3))
}

async function doRedeem () {
  redeemErr.value = ''
  redeemResult.value = null
  const code = redeemCode.value.trim()
  if (!code) { redeemErr.value = '请输入激活码'; return }

  redeeming.value = true
  try {
    // 后端 @RequestParam String code
    const { data } = await api.post('/activation-codes/redeem', null, { params: { code } })
    if (data?.code !== 0) throw new Error(data?.msg || '兑换失败')

    const resp: RedeemResp = data.data
    redeemResult.value = resp
    // 同步更新本地用户信息（到期时间/座席数）
    if (!user.value) user.value = { id: resp.userId, agentId: null, role: '', username: '' }
    if (typeof resp.newExpireAt !== 'undefined') {
      user.value!.expireAt = resp.newExpireAt
    }
    if (typeof resp.newSeatCount !== 'undefined') {
      (user.value as any).seatCount = resp.newSeatCount
    }
    // 也可以重新拉取一次用户信息，确保一致
    await fetchUser()

    // 成功后清空输入
    redeemCode.value = ''
    showToast('兑换成功')
  } catch (e:any) {
    redeemErr.value = e?.message || '兑换失败'
  } finally {
    redeeming.value = false
  }
}

/** ---------------- 方法 ---------------- */
function fmtTime (s?: string | null) {
  if (!s) return '-'
  try{
    const d = new Date(s)
    const pad = (n:number)=> n<10?`0${n}`:n
    return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
  }catch{ return String(s) }
}

function showToast (msg: string) {
  toastMsg.value = msg
  setTimeout(() => (toastMsg.value = ''), 1600)
}

async function fetchUser () {
  if (!uid.value) return
  const { data } = await api.get(`/users/${uid.value}`)
  if (data?.code !== 0) throw new Error(data?.msg || '加载失败')

  const u: UserDto = data.data
  user.value = u

  // 兼容 brandTitle -> brandName
  const bn = (u.brandName ?? u.brandTitle) || ''
  Object.assign(form, {
    username: u.username || '',
    displayName: u.displayName || '',
    email: u.email || '',
    phone: u.phone || '',
    code: u.code || '',
    brandName: bn,
    brandLogo: u.brandLogo || '',
    brandIntro: u.brandIntro || '',
    csName: u.csName || '',
    csLogo: u.csLogo || '' ,
    autoReply: u.autoReply || '',
    domain: (u as any).domain || '',
  })
  autoReplies.value = splitAutoReply(form.autoReply).slice(0, 3)
  tempLogoPreview.value = '' // 用服务器地址作为预览
  logoError.value = false
  originalSnapshot = JSON.stringify(form)
}

function reset () {
  if (!originalSnapshot) return
  Object.assign(form, JSON.parse(originalSnapshot))
  tempLogoPreview.value = ''
  logoError.value = false
  showToast('已重置')
}

async function save () {
  if (!uid.value) return
  saving.value = true
  syncAutoReplyToForm()
  try {
    const payload = {
      username: form.username,
      displayName: form.displayName,
      email: form.email,
      phone: form.phone,
      brandName: form.brandName,
      brandTitle: form.brandName, // 兼容旧字段
      brandLogo: form.brandLogo,
      brandIntro: form.brandIntro,
      csName: form.csName,
      csLogo: form.csLogo,
      autoReply: form.autoReply,
      domain: form.domain,
    }
    const { data } = await api.put(`/users/${uid.value}`, payload)
    if (data?.code !== 0) throw new Error(data?.msg || '保存失败')
    showToast('已保存')
    await fetchUser()
  } catch (e:any) {
    showToast(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function copyInvite () {
  const text = inviteUrl.value
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
    } else {
      const ta = document.createElement('textarea')
      ta.value = text
      ta.style.position = 'fixed'
      ta.style.opacity = '0'
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      document.body.removeChild(ta)
    }
    showToast('已复制')
  } catch {
    showToast('复制失败')
  }
}

function onPickLogo (e: Event, type: 'brand' | 'cs' = 'brand') {
  const f = (e.target as HTMLInputElement).files?.[0]
  if (!f) return

  if (type === 'brand') {
    tempLogoPreview.value = URL.createObjectURL(f)
    logoError.value = false
  } else {
    tempCsLogoPreview.value = URL.createObjectURL(f)
    csLogoError.value = false
  }

  void uploadLogo(f, type).finally(() => {
    (e.target as HTMLInputElement).value = ''
  })
}

async function uploadLogo (file: File, type: 'brand' | 'cs' = 'brand') {
  uploadingLogo.value = true
  try {
    const fd = new FormData()
    fd.append('file', file)
    const { data } = await api.post('/files/brand-logo', fd, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    const url = data?.data?.url || data?.url
    if (!url) throw new Error(data?.msg || '上传失败')

    if (type === 'brand') {
      form.brandLogo = url
    } else {
      form.csLogo = url
    }
    showToast('上传成功')
  } catch (err:any) {
    showToast(err?.message || '上传失败')
  } finally {
    uploadingLogo.value = false
  }
}

/** LOGO 事件：失败则隐藏图片 */
function onLogoError() { logoError.value = true }
function onLogoLoad() { logoError.value = false }

/** 生命周期 */
onMounted(async () => {
  try { await fetchUser() } catch (e:any) { showToast(e?.message || '加载失败') }
})
</script>

<style scoped>
/* 通用卡片 */
.card{
  position: relative;
  border-radius: 18px;
  background: rgba(10,15,25,.7);
  border:1px solid rgba(255,255,255,.09);
  box-shadow: 0 20px 60px rgba(0,0,0,.45), inset 0 1px 0 rgba(255,255,255,.06);
  overflow: hidden;
}
/* 页面容器 */
.ps-page{ padding: 8px }

/* 顶部英雄区 */
.hero{ position: relative; min-height: 340px }
.hero-bg{
  position:absolute; inset:0;
  background:
    radial-gradient(1200px 360px at 60% -20%, rgba(59,130,246,.35), transparent 60%),
    radial-gradient(900px 260px at 10% 10%, rgba(34,211,238,.25), transparent 55%),
    radial-gradient(900px 260px at 90% 10%, rgba(168,85,247,.24), transparent 55%),
    linear-gradient(180deg, rgba(14,18,28,.85), rgba(9,12,20,.85));
}
.hero-content{
  position:relative; z-index:1;
  min-height: 340px;
  display:flex; flex-direction:column; align-items:center; justify-content:center;
  gap: 14px; padding: 24px 0 28px;
}
.logo-wrap{
  width: 128px; height: 128px; border-radius: 28px;
  background: rgba(255,255,255,.06);
  border: 1px solid rgba(255,255,255,.14);
  display:grid; place-items:center;
  box-shadow: inset 0 1px 0 rgba(255,255,255,.08), 0 30px 80px rgba(0,0,0,.45);
}
.logo-img{
  width: 110px; height: 110px; border-radius: 20px;
  object-fit: contain; background: rgba(0,0,0,.12);
}
.scan-lines{ position:absolute; inset:0; border-radius:inherit; overflow:hidden; pointer-events:none }
.scan-lines::before{
  content:""; position:absolute; left:0; top:-120%;
  width:100%; height:120%;
  background: linear-gradient(180deg, transparent, rgba(255,255,255,.16), transparent);
  animation: scan 3.5s linear infinite;
}
@keyframes scan{ to{ transform: translateY(220%) } }

.title{
  font-weight:900; font-size: 26px; letter-spacing:.5px; color:#fff;
  text-shadow: 0 8px 30px rgba(2,6,23,.6);
}
.subtitle{ color:#cbd5e1; font-size:12px; opacity:.9; text-align:center; max-width: 86% }

/* 信息块 */
.info-block{ display:grid; grid-template-columns: 116px 1fr; gap:10px; align-items:center }
.info-block + .info-block{ margin-top:10px }
.info-block .label{ color:#a7b4c7; font-size:12px }
.info-block .value{ color:#e7eefc; font-weight:800 }
.info-block .value.mono{ font-family: ui-monospace, Menlo, Monaco, Consolas, "Courier New", monospace }
.info-block .value.badge{
  display:inline-flex; align-items:center; justify-content:flex-start;
  gap:8px; padding:6px 10px; border-radius:10px; width:fit-content;
  background: rgba(255,255,255,.06); border:1px solid rgba(255,255,255,.12);
  box-shadow: inset 0 1px 0 rgba(255,255,255,.06);
}

/* 到期时间：渐变文案 */
.info-block .value.v-accent{
  background: linear-gradient(90deg,#93c5fd,#22d3ee,#a5f3fc);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  font-weight: 900;
  animation: shimmer 3s ease-in-out infinite;
}
@keyframes shimmer{
  0%{ filter: drop-shadow(0 0 0 rgba(147,197,253,.0)); }
  50%{ filter: drop-shadow(0 0 8px rgba(147,197,253,.25)); }
  100%{ filter: drop-shadow(0 0 0 rgba(147,197,253,.0)); }
}

.subsection-title{ font-weight:800; color:#fff; margin-top:6px }

/* 兑换 */
.redeem-wrap{
  display:flex; gap:10px; align-items:center; flex-wrap:wrap;
}
.redeem-input{ flex: 1 1 220px }
.redeem-err{ color:#fecaca; font-size:12px; margin-top:4px }
.redeem-result{
  margin-top:10px; padding:10px; border-radius:12px;
  background: rgba(255,255,255,.05); border:1px solid rgba(255,255,255,.12);
}
.rr-title{
  display:flex; align-items:center; gap:8px; font-weight:900; color:#e5f8ef;
}
.dot{ width:10px; height:10px; border-radius:50% }
.dot.ok{ background:#22c55e }
.rr-grid{
  display:grid; grid-template-columns: 1fr 1fr; gap:10px; margin-top:8px;
}
.rr-item .k{ color:#a7b4c7; font-size:12px }
.rr-item .v{
  color:#e7eefc; font-weight:900;
  padding:6px 10px; border-radius:10px;
  background: rgba(255,255,255,.04); border:1px solid rgba(255,255,255,.10);
}

/* 分隔线 */
.sep{ height:1px; background:linear-gradient(90deg,transparent,rgba(255,255,255,.14),transparent); margin:10px 0 }

/* 推广链接展示 */
.invite-url{
  user-select: all;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  padding:8px 10px; border-radius:10px;
  background: rgba(255,255,255,.04);
  border:1px solid rgba(255,255,255,.08);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas;
  font-size:12px; color:#e5e7eb;
}

/* 表单 */
.section-title{ font-weight:800; color:#fff; margin-bottom:10px }
.form-grid{ display:grid; grid-template-columns: 1fr 1fr; gap:14px 18px }
.fi{ display:grid; grid-template-columns: 180px 1fr; align-items:center; gap:10px }
.fi-label{ color:#a7b4c7; font-size:12px }
.ipt{
  width:100%; border-radius:12px; padding:10px 12px; color:#fff;
  background: rgba(255,255,255,.06); border:1px solid rgba(255,255,255,.12);
  outline: none;
}
.ipt:focus{ border-color: rgba(59,130,246,.6); box-shadow: 0 0 0 3px rgba(59,130,246,.2) }

/* 上传器 */
.uploader{ display:flex; align-items:center; gap:12px; flex-wrap:wrap }
.uploader .hint{ font-size:12px; color:#9fb2c7 }
.logo-preview{
  width:56px; height:56px; border-radius:14px; object-fit:contain;
  background: rgba(255,255,255,.05); border:1px solid rgba(255,255,255,.12); padding:6px
}

/* 按钮 */
.pill{
  padding:10px 14px; border-radius:12px; font-weight:800; letter-spacing:.3px;
  background: rgba(59,130,246,.18); color:#e5edff; border:1px solid rgba(59,130,246,.35);
  transition:.15s;
}
.pill:hover{ transform: translateY(-1px); box-shadow:0 12px 30px rgba(2,6,23,.35) }
.pill.primary{ background: linear-gradient(135deg,#60a5fa,#22d3ee); border:0; color:#0b1020 }
.pill.outline{ background: transparent; border:1px solid rgba(255,255,255,.16) }
.tiny-pill{
  padding:4px 8px; border-radius:8px; font-size:12px; font-weight:800;
  background: rgba(255,255,255,.08); border:1px solid rgba(255,255,255,.16);
  color:#e5e7eb;
}

/* 加载动画 */
.loader{
  display:inline-block; width:14px; height:14px; margin-right:6px; vertical-align:-2px;
  border:2px solid rgba(255,255,255,.55); border-top-color: transparent; border-radius:50%;
  animation: spin .9s linear infinite;
}
@keyframes spin{ to{ transform: rotate(360deg) } }

/* 过渡动画 */
.animate-fade-in{ animation: fadeIn .38s ease both }
.animate-fade-up{ animation: fadeUp .38s ease both }
.animate-fade-up-delayed{ animation: fadeUp .46s .06s ease both }
.animate-pop{ animation: popIn .36s .06s ease both }
@keyframes fadeIn{ from{ opacity:0 } to{ opacity:1 } }
@keyframes fadeUp{ from{ opacity:0; transform: translateY(8px) } to{ opacity:1; transform:none } }
@keyframes popIn{ from{ opacity:0; transform: scale(.96) } to{ opacity:1; transform: scale(1) } }

/* 吐司 */
.toast{
  position: fixed; left:50%; bottom:28px; transform: translateX(-50%);
  background: rgba(0,0,0,.7); color:#fff; padding:10px 14px; border-radius:10px;
  border:1px solid rgba(255,255,255,.16); box-shadow: 0 12px 30px rgba(0,0,0,.35);
  z-index: 50; font-weight:800; letter-spacing:.3px; font-size:12px;
}
.toast-enter-from, .toast-leave-to{ opacity:0; transform: translate(-50%, 6px) }
.toast-enter-active, .toast-leave-active{ transition: all .16s ease }
/* 自动回复编辑区（横向、单行、不换行，满宽可横向滚动） */
/* 横向滚动容器 */
.ar-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: nowrap;
  overflow-x: auto;
  padding-bottom: 2px;
}

/* 单条回复输入 */
.ar-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
}

.ar-input {
  width: 220px;
}

/* 新增按钮单独一行，避免被横向滚动遮挡 */
.ar-add {
  margin-top: 8px;
}

/* 提示文字 */
.ar-hint {
  font-size: 12px;
  color: #9fb2c7;
  margin-top: 6px;
}

/* “删除”危险色 */
.tiny-pill.danger {
  background: rgba(239,68,68,.12);
  border-color: rgba(239,68,68,.35);
  color: #fecaca;
}

</style>
