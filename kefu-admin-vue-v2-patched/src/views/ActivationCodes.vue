<template>
  <div class="space-y-5 animate-page-in">
    <!-- 顶部工具栏：同后台账号配置 -->
    <div class="toolbar flex flex-wrap items-center gap-3">
      <div class="text-xl font-bold text-white">激活码管理</div>
      <div class="flex-1"></div>

      <div class="flex flex-wrap items-center gap-2 animate-soft-in">
        <input
          v-model.trim="filters.code"
          @keyup.enter="reload(1)"
          placeholder="按 code 搜索"
          class="inp search"
        />

        <div class="select-wrap">
          <select v-model="filters.status" class="sel">
            <option :value="''">全部状态</option>
            <option :value="1">未使用</option>
            <option :value="0">已使用</option>
            <option :value="3">已销毁</option>
          </select>
          <span class="sel-caret"></span>
        </div>

        <div class="select-wrap">
          <select v-model="filters.func" class="sel">
            <option :value="''">全部类型</option>
            <option :value="1">增加可用天数</option>
            <option :value="2">增加座席数</option>
          </select>
          <span class="sel-caret"></span>
        </div>

        <input
          v-model.number="filters.useUserId"
          @keyup.enter="reload(1)"
          placeholder="使用人UID"
          class="inp"
          type="number"
          min="1"
        />

        <button class="btn-ghost" @click="reload(1)">查询</button>
        <button class="btn-primary" @click="openCreate">新建激活码</button>
      </div>
    </div>

    <!-- 列表：同账号配置的 card/table 风格 -->
    <div class="card animate-soft-in overflow-hidden">
      <table class="w-full text-sm table-center">
        <thead class="thead">
          <tr>
            <th class="th">ID</th>
            <th class="th left">Code</th>
            <th class="th">类型</th>
            <th class="th">增加数量</th>
            <th class="th">使用人UID</th>
            <th class="th">创建人UID</th>
            <th class="th">状态</th>
            <th class="th">创建时间</th>
            <th class="th left">备注</th>
            <th class="th">操作</th>
          </tr>
        </thead>

        <tbody>
          <tr v-for="row in list" :key="row.id" class="tr">
            <td class="td mono">{{ row.id }}</td>

            <td class="td left mono code-cell">
              <span class="code-chip" :title="row.code">{{ row.code }}</span>
              <button class="tiny-pill" @click="copy(row.code)">复制</button>
            </td>

            <td class="td">
              <span class="tag" :data-func="row.function">
                {{ row.function === 1 ? '增加可用天数' : row.function === 2 ? '增加座席数' : '-' }}
              </span>
            </td>

            <td class="td">{{ row.value }}</td>
            <td class="td mono">{{ row.useUserId ?? '-' }}</td>
            <td class="td mono">{{ row.createdUserId ?? '-' }}</td>

            <td class="td">
              <!-- 用与“账号管理”一致的 pill strong 徽章体系 -->
              <span class="pill strong" :data-variant="statusVariant(row.status)">
                <i class="dot"></i>{{ statusText(row.status) }}
              </span>
            </td>

            <td class="td text-white/70">{{ fmt(row.createdAt) }}</td>

            <td class="td left text-white/80">
              <span class="note" :title="row.note || '-'">{{ row.note || '-' }}</span>
            </td>

            <td class="td">
              <div class="flex items-center gap-2 justify-center">
                <button
                  class="btn-danger"
                  :disabled="row.status !== 1 || destroyingId===row.id"
                  @click="openDestroy(row)"
                >
                  {{ destroyingId===row.id ? '销毁中…' : '销毁' }}
                </button>
              </div>
            </td>
          </tr>

          <tr v-if="!loading && list.length === 0">
            <td colspan="10" class="empty">暂无数据</td>
          </tr>
          <tr v-if="loading">
            <td colspan="10" class="empty">加载中…</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 分页 -->
    <div class="flex items-center justify-between text-white/70 animate-soft-in">
      <div>共 {{ total }} 条</div>
      <div class="flex items-center gap-2">
        <button class="btn-ghost" :disabled="page===1" @click="reload(page-1)">上一页</button>
        <span>第 {{ page }} / {{ pages }} 页</span>
        <button class="btn-ghost" :disabled="page===pages || pages===0" @click="reload(page+1)">下一页</button>
      </div>
    </div>

    <!-- 新建弹窗（完全沿用“账号管理”的表单网格/按钮/动画） -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="showCreate" class="modal" @wheel.prevent>
          <div class="dialog">
            <div class="title">新建激活码</div>

            <div class="form-grid mt-4">
              <div class="field">
                <label class="lb">产品类型</label>
                <div class="select-wrap control">
                  <select v-model="createForm.function" class="sel">
                    <option :value="1">增加可用天数</option>
                    <option :value="2">增加座席数</option>
                  </select>
                  <span class="sel-caret"></span>
                </div>
              </div>

              <div class="field">
                <label class="lb">增加数</label>
                <input v-model.number="createForm.value" type="number" min="1" class="inp control" />
              </div>
            </div>

            <div v-if="err" class="err mt-3">{{ err }}</div>

            <div class="actions">
              <button @click="showCreate=false" class="btn-ghost">取消</button>
              <button @click="doCreate" :disabled="creating" class="btn-primary">
                {{ creating ? '创建中…' : '创建' }}
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- 销毁确认（统一弹窗样式） -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="showDestroy" class="modal">
          <div class="dialog small">
            <div class="title">销毁确认</div>
            <div class="text-white/80 mt-2">
              确认<span class="text-red-200 font-bold">销毁</span>激活码
              <span class="mono text-white/90">{{ destroyRow?.code }}</span> 吗？（仅对未使用）
            </div>

            <div class="field mt-3">
              <label class="lb">备注（可选）</label>
              <input v-model.trim="destroyNote" class="inp" placeholder="不填将由后端写入“人工销毁 …”" />
            </div>

            <div class="actions">
              <button @click="showDestroy=false" class="btn-ghost">取消</button>
              <button @click="doDestroy" class="btn-danger" :disabled="destroying">
                {{ destroying ? '销毁中…' : '确认销毁' }}
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- 轻量吐司 -->
    <transition name="toast">
      <div v-if="toast" class="toast">{{ toast }}</div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted, watch } from 'vue'
import api from '@/api'

/** 过滤 + 分页（原逻辑保留） */
const page = ref(1)
const size = ref(12)
const total = ref(0)
const pages = ref(0)
const loading = ref(false)

const filters = reactive({
  code: '',
  status: '' as '' | 0 | 1 | 3,
  func: '' as '' | 1 | 2,
  useUserId: undefined as number | undefined
})

const list = ref<any[]>([])

async function reload(p = page.value) {
  loading.value = true
  page.value = p
  try {
    const { data } = await api.get('/activation-codes', {
      params: {
        page: page.value,
        size: size.value,
        code: filters.code || undefined,
        status: filters.status === '' ? undefined : filters.status,
        function: filters.func === '' ? undefined : filters.func,
        use_user_id: filters.useUserId || undefined
      }
    })
    if (data.code === 0) {
      const pg = data.data
      list.value = pg.records || []
      total.value = pg.total || 0
      size.value = pg.size || size.value
      page.value = pg.current || page.value
      pages.value = pg.pages || 0
    } else {
      show('加载失败：' + (data.msg || ''))
    }
  } catch (e:any) {
    show(e?.message || '请求失败')
  } finally {
    loading.value = false
  }
}
onMounted(() => reload(1))

/** 状态文案 */
function statusText(s:number){
  if (s === 1) return '未使用'
  if (s === 0) return '已使用'
  if (s === 2) return '已销毁'
  return String(s ?? '-')
}
/** 与“账号管理”一致的徽章控件，用 data-variant 区分色系 */
function statusVariant(s:number){
  if (s === 1) return 'unused'     // 绿
  if (s === 0) return 'used'       // 紫
  if (s === 3) return 'destroyed'  // 红
  return 'idle'
}

/** 复制 & 吐司 */
const toast = ref('')
function show(msg:string){ toast.value = msg; setTimeout(()=> toast.value='', 1600) }
async function copy(text:string){
  try{
    if (navigator.clipboard?.writeText) await navigator.clipboard.writeText(text)
    else {
      const ta = document.createElement('textarea')
      ta.value = text; ta.style.position='fixed'; ta.style.opacity='0'
      document.body.appendChild(ta); ta.select(); document.execCommand('copy'); document.body.removeChild(ta)
    }
    show('已复制')
  }catch{ show('复制失败') }
}

/** 新建 */
const showCreate = ref(false)
const creating = ref(false)
const err = ref('')
const createForm = reactive({ function: 1 as 1|2, value: 1 })

function openCreate(){
  createForm.function = 1
  createForm.value = 1
  err.value = ''
  showCreate.value = true
}

async function doCreate(){
  if (!createForm.value || createForm.value <= 0) { err.value = '值必须 > 0'; return }
  creating.value = true
  try{
    const { data } = await api.post('/activation-codes', {
      function: createForm.function,
      value: createForm.value
    })
    if (data.code === 0) {
      showCreate.value = false
      show('创建成功')
      reload()
    } else {
      err.value = data.msg || '创建失败'
    }
  } finally {
    creating.value = false
  }
}

/** 销毁 */
const showDestroy = ref(false)
const destroying = ref(false)
const destroyingId = ref<number|undefined>(undefined)
const destroyRow = ref<any>(null)
const destroyNote = ref('')

function openDestroy(row:any){
  destroyRow.value = row
  destroyNote.value = ''
  showDestroy.value = true
}
async function doDestroy(){
  if (!destroyRow.value) return
  destroying.value = true
  destroyingId.value = destroyRow.value.id
  try{
    const { data } = await api.post(`/activation-codes/${destroyRow.value.id}/destroy`, null, {
      params: { note: destroyNote.value || undefined }
    })
    if (data.code === 0) {
      showDestroy.value = false
      show('已销毁')
      reload()
    } else {
      show(data.msg || '销毁失败')
    }
  } finally {
    destroying.value = false
    destroyingId.value = undefined
  }
}

/** 弹窗开关时锁滚动 */
watch([showCreate, showDestroy], ([a, b]) => {
  document.documentElement.style.overflow = (a || b) ? 'hidden' : ''
})

/** 时间格式 */
function fmt(s:any){
  if (!s) return '-'
  try{
    const d = new Date(s)
    const P = (n:number)=> String(n).padStart(2,'0')
    return `${d.getFullYear()}-${P(d.getMonth()+1)}-${P(d.getDate())} ${P(d.getHours())}:${P(d.getMinutes())}`
  }catch{ return s }
}
</script>

<style scoped>
/* 入场动画（同后台账号配置） */
@keyframes pageIn { from {opacity:0; transform: translateY(6px)} to{opacity:1; transform:none} }
@keyframes softIn { from {opacity:0; transform: translateY(4px) scale(.98)} to{opacity:1; transform:none} }
.animate-page-in { animation: pageIn .36s ease-out both; }
.animate-soft-in { animation: softIn .36s ease-out both; }

/* 工具栏输入 */
.search{ width: clamp(260px, 38vw, 520px); }

/* 卡片与表格（照抄后台账号配置的风格） */
.card { border-radius: 16px; background: rgba(255,255,255,.05); border: 1px solid rgba(255,255,255,.1); overflow: hidden; }
.thead { background: rgba(255,255,255,.05); color: rgba(255,255,255,.7); }
.table-center .th, .table-center .td { text-align: center; vertical-align: middle; }
.table-center .th.left, .table-center .td.left { text-align: left; }
.th { padding: 10px 12px; }
.tr { border-top: 1px solid rgba(255,255,255,.08); }
.td { padding: 10px 12px; color: #fff; }
.mono { font-family: ui-monospace, Menlo, Consolas, monospace; }
.empty { padding: 28px; text-align: center; color: rgba(255,255,255,.6) }

/* 基础控件（同一套） */
.inp, .sel {
  padding: 10px 12px; border-radius: 12px;
  background: rgba(14,18,28,.9);
  border: 1px solid rgba(255,255,255,.15);
  color: #e5e7eb; outline: none;
  transition: box-shadow .2s, border-color .2s, background .2s;
}
.inp::placeholder { color: rgba(255,255,255,.4); }
.inp:focus, .sel:focus {
  border-color: rgba(59,130,246,.6);
  box-shadow: 0 0 0 6px rgba(59,130,246,.15);
  background: rgba(14,18,28,.95);
}

/* 下拉外观统一 */
.sel{ appearance: none; -webkit-appearance: none; -moz-appearance: none; border-radius: 12px; }
.sel option{ background: #0d121c; color: #e5e7eb; }
.select-wrap{ position: relative; display:inline-block }
.select-wrap .sel{ padding-right: 28px; }
.sel-caret{
  position:absolute; right:10px; top:50%; width:0; height:0; margin-top:-3px;
  border-left:6px solid transparent; border-right:6px solid transparent; border-top:6px solid rgba(255,255,255,.7);
  pointer-events: none;
}

/* 表单网格 + 统一控件高度 */
.form-grid{
  display:grid;
  grid-template-columns: repeat(2,minmax(0,1fr));
  gap:16px;
}
.field .lb{ color:rgba(255,255,255,.75); font-size:12px; display:block; margin-bottom:6px }
.control{ height:44px }
.inp.control{ padding: 10px 12px; line-height:22px }
.select-wrap.control{ height:44px }
.select-wrap.control .sel{ height:100%; }

/* 按钮（同后台账号配置） */
.btn-primary{
  padding: 10px 14px; border-radius: 12px;
  background: linear-gradient(90deg,#06b6d4,#3b82f6,#8b5cf6);
  color:#fff; font-weight: 700; border:0; cursor: pointer;
  transition: transform .15s, filter .25s, background-position .9s;
  background-size: 220% 100%;
}
.btn-primary:hover{ background-position: 100% 0; filter: brightness(1.06) }
.btn-ghost{
  padding: 10px 14px; border-radius: 12px;
  background: rgba(255,255,255,.06);
  border: 1px solid rgba(255,255,255,.12);
  color:#fff; cursor:pointer;
}
.btn-danger{
  padding: 8px 12px; border-radius: 10px; background: rgba(244,63,94,.2); color:#fecaca;
}

/* pill strong 徽章：比对账号配置的风格做三状态扩展 */
.pill{
  padding: 6px 12px; border-radius: 999px; border: 1px solid rgba(255,255,255,.12);
  background: rgba(255,255,255,.06); color:#fff; cursor:default; font-size: 12px;
  display:inline-flex; align-items:center; gap:8px; font-weight: 800;
}
.pill .dot{ width:8px; height:8px; border-radius:50%; background:#94a3b8; }
.pill.strong[data-variant="unused"]{
  background: linear-gradient(90deg,rgba(16,185,129,.22),rgba(59,130,246,.10));
  border-color: rgba(16,185,129,.35); color:#d1fae5;
}
.pill.strong[data-variant="unused"] .dot{ background:#22c55e }
.pill.strong[data-variant="used"]{
  background: linear-gradient(90deg,rgba(99,102,241,.20),rgba(59,130,246,.08));
  border-color: rgba(99,102,241,.35); color:#c7d2fe;
}
.pill.strong[data-variant="used"] .dot{ background:#8b5cf6 }
.pill.strong[data-variant="destroyed"]{
  background: linear-gradient(90deg,rgba(244,63,94,.22),rgba(59,130,246,.06));
  border-color: rgba(244,63,94,.35); color:#fecaca;
}
.pill.strong[data-variant="destroyed"] .dot{ background:#ef4444 }

/* 类型小标签 */
.tag{
  padding: 4px 8px; border-radius: 999px; font-size: 12px; font-weight: 700;
  background: rgba(255,255,255,.07); color: #e5e7eb;
}
.tag[data-func="1"]{ background: rgba(34,197,94,.18); color:#bbf7d0; }
.tag[data-func="2"]{ background: rgba(59,130,246,.18); color:#bfdbfe; }

/* code 胶囊/备注/吐司（保留你原有的小组件感） */
.code-cell{ display:flex; align-items:center; gap:8px }
.code-chip{
  max-width: 320px; display:inline-block;
  padding: 6px 10px; border-radius: 10px;
  background: rgba(255,255,255,.08); border:1px solid rgba(255,255,255,.14);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.note{ display:inline-block; max-width: 380px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis }
.toast{
  position: fixed; left:50%; bottom:28px; transform: translateX(-50%);
  background: rgba(0,0,0,.7); color:#fff; padding:10px 14px; border-radius:10px;
  border:1px solid rgba(255,255,255,.16); box-shadow: 0 12px 30px rgba(0,0,0,.35);
  z-index: 50; font-weight:800; letter-spacing:.3px; font-size:12px;
}
.toast-enter-from, .toast-leave-to{ opacity:0; transform: translate(-50%, 6px) }
.toast-enter-active, .toast-leave-active{ transition: all .16s ease }

/* 弹窗（同后台账号配置） */
.modal{
  position: fixed; inset: 0; z-index: 9999;
  display: grid; place-items: center;
  background:
    radial-gradient(1200px 600px at 50% -10%, rgba(56,189,248,.10), transparent 60%),
    radial-gradient(1000px 600px at 100% 0, rgba(16,185,129,.10), transparent 55%),
    rgba(2,6,23,.72);
  backdrop-filter: blur(6px) saturate(120%);
}
.dialog{
  width: 620px; max-width: 92vw;
  border-radius: 18px; background: rgba(13,18,28,.96);
  border: 1px solid rgba(255,255,255,.12);
  padding: 20px 20px 16px;
  box-shadow: 0 24px 80px rgba(0,0,0,.45), inset 0 1px 0 rgba(255,255,255,.06);
  transform: scale(.96) translateY(6px); opacity: 0; animation: dlg-in .24s ease forwards;
}
@keyframes dlg-in { to { transform: none; opacity: 1 } }
.dialog.small{ width: 520px }
.title{ color:#fff; font-weight: 800; font-size: 18px }
.actions{ margin-top: 18px; display: flex; justify-content: flex-end; gap: 10px }

/* Toast 过渡 */
.toast-enter-from, .toast-leave-to{ opacity:0; transform: translate(-50%, 6px) }
.toast-enter-active, .toast-leave-active{ transition: all .16s ease }
</style>
