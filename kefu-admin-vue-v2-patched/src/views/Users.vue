<template>
  <div class="space-y-5 animate-page-in">
    <!-- 顶部工具栏 -->
    <div class="toolbar flex flex-wrap items-center gap-3">
      <div class="text-xl font-bold text-white">账号管理</div>
      <div class="flex-1"></div>

      <div class="flex flex-wrap items-center gap-2 animate-soft-in">
        <!-- 搜索框更长且自适应，不会挡字 -->
        <input
          v-model.trim="keyword"
          @keyup.enter="reload(1)"
          placeholder="搜索：用户名 / 昵称 / 代码"
          class="inp search"
        />
        <!-- 统一圆角下拉（角色） -->
        <div class="select-wrap">
          <select v-model="role" class="sel">
            <option value="">全部角色</option>
            <option value="SUPER_AGENT">SUPER_AGENT</option>
            <option value="AGENT">AGENT</option>
            <option value="CS">CS</option>
          </select>
          <span class="sel-caret"></span>
        </div>
        <!-- 统一圆角下拉（状态） -->
        <div class="select-wrap">
          <select v-model="status" class="sel">
            <option :value="undefined">全部状态</option>
            <option :value="1">启用</option>
            <option :value="0">禁用</option>
          </select>
          <span class="sel-caret"></span>
        </div>

        <button class="btn-ghost" @click="reload(1)">查询</button>
        <button v-if="canShowCreateBtn" class="btn-primary" @click="openCreate">新建用户</button>
      </div>
    </div>

    <!-- 表格（默认居中对齐） -->
    <div class="card animate-soft-in">
      <table class="w-full text-sm table-center">
        <thead class="thead">
          <tr>
            <th class="th">UID</th>
            <th class="th left">用户名</th>
            <th class="th left">昵称</th>
            <th class="th">角色</th>
            <th class="th">上级代理UID</th>
            <th class="th">状态</th>
            <th class="th">创建时间</th>
            <th class="th">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="u in list" :key="u.id" class="tr">
            <td class="td mono">{{ u.id }}</td>
            <td class="td left">{{ u.username }}</td>
            <td class="td left">{{ u.displayName ?? '-' }}</td>
            <td class="td">
              <span class="tag" :data-variant="u.role">{{ u.role }}</span>
            </td>
            <td class="td mono">{{ u.agentId ?? '-' }}</td>
            <td class="td">
              <button
                class="pill strong"
                :data-on="u.status === 1"
                @click="confirmToggleStatus(u)"
              >
                <i class="dot"></i>{{ u.status === 1 ? '启用' : '禁用' }}
              </button>
            </td>
            <td class="td text-white/60">{{ fmt(u.createdAt) }}</td>
            <td class="td">
              <div class="flex items-center gap-2 justify-center">
                <button @click="openEdit(u)" class="btn-ghost">编辑</button>
                <button v-if="canDeleteRow(u)" @click="confirmDelete(u)" class="btn-danger">删除</button>
              </div>
            </td>
          </tr>
          <tr v-if="!loading && list.length === 0">
            <td colspan="8" class="empty">暂无数据</td>
          </tr>
          <tr v-if="loading">
            <td colspan="8" class="empty">加载中…</td>
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

    <!-- 新建/编辑弹窗（Teleport 到 body） -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="showModal" class="modal" @wheel.prevent>
          <div class="dialog">
            <div class="title">{{ form.id ? '编辑用户' : '新建用户' }}</div>

            <!-- 表单两列严格对齐 + 统一高度 -->
            <div class="form-grid mt-4">
              <div class="field">
                <label class="lb">用户名</label>
                <input v-model.trim="form.username" class="inp control" />
              </div>

              <div class="field">
                <label class="lb">角色</label>
                <div class="select-wrap">
                  <select v-model="form.role" class="sel w-full" :disabled="!canEditRole">
                    <option v-for="r in roleOptions" :key="r" :value="r">{{ r }}</option>
                  </select>
                  <span class="sel-caret"></span>
                </div>
              </div>

              <div>
                <label class="lb">代理UID</label>
                <input
                  v-model.number="form.agentId"
                  type="number"
                  class="inp w-full"
                  :disabled="!canEditAgent"
                  :placeholder="canEditAgent ? '例如：1' : ''"
                />
              </div>

              <div class="field">
                <label class="lb">{{ form.id ? '新密码（留空不改）' : '密码（必填）' }}</label>
                <input v-model="form.password" type="text" :placeholder="form.id ? '留空表示不修改' : ''" class="inp control" />
              </div>

              <div class="field">
                <label class="lb">昵称</label>
                <input v-model.trim="form.displayName" class="inp control" />
              </div>

              <!-- 推广代码：仅 ROOT 且“编辑”时显示；新增不显示 -->
              <div class="field" v-if="isRoot && form.id">
                <label class="lb">推广代码</label>
                <input v-model.trim="form.code" class="inp control" />
              </div>

              <div class="field">
                <label class="lb">状态</label>
                <div class="select-wrap control">
                  <select v-model.number="form.status" class="sel">
                    <option :value="1">启用</option>
                    <option :value="0">禁用</option>
                  </select>
                  <span class="sel-caret"></span>
                </div>
              </div>

              <div class="field flex items-end">
                <label class="switch">
                  <input type="checkbox" v-model="form.totpEnabled" />
                  <span></span>
                  <i>谷歌验证码</i>
                </label>
              </div>
            </div>

            <div v-if="err" class="err mt-3">{{ err }}</div>

            <div class="actions">
              <button @click="closeModal" class="btn-ghost">取消</button>
              <button @click="save" :disabled="saving" class="btn-primary">
                {{ saving ? '保存中…' : '保存' }}
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- 状态确认 -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="showStatusConfirm" class="modal">
          <div class="dialog small">
            <div class="title">状态确认</div>
            <div class="text-white/80 mt-1">
              确认将用户「{{ statusRow?.username }}」状态修改为
              <b class="text-white">{{ statusTo === 1 ? '启用' : '禁用' }}</b> 吗？
            </div>
            <div class="actions">
              <button @click="showStatusConfirm=false" class="btn-ghost">取消</button>
              <button @click="doToggleStatus" class="btn-primary">确定</button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- 删除确认（仅 ROOT） -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="showDel" class="modal">
          <div class="dialog small">
            <div class="title">删除确认</div>
            <div class="text-white/80 mt-1">确认删除用户「{{ delRow?.username }}」吗？该操作不可恢复。</div>
            <div class="actions">
              <button @click="showDel=false" class="btn-ghost">取消</button>
              <button @click="doDelete" class="btn-danger">删除</button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted, watch, computed } from 'vue'
import { fetchUsers, createUser, updateUser, deleteUser, setUserStatus } from '@/api/users'
import { useAuth } from '@/store/auth'

/* Auth & 权限 */
const auth = useAuth()
const roleUp = computed(() => (auth.role || '').toUpperCase())
const isRoot = computed(() => roleUp.value === 'ROOT')
const isSuper = computed(() => roleUp.value === 'SUPER_AGENT')
const isAgent = computed(() => roleUp.value === 'AGENT')
const isCS    = computed(() => roleUp.value === 'CS')

// 顶部“新建用户”按钮：除了 CS 都可见
const canShowCreateBtn = computed(() => !isCS.value)
// 行内“删除”按钮：除了 CS 都可见（若你要更严可再加级别判断）
const canShowDeleteBtn = computed(() => !isCS.value)

const ROLE_ALL = ['ROOT', 'SUPER_AGENT', 'AGENT', 'CS']
const ROLE_CREATE_FOR_SUPER = ['AGENT', 'CS']

const isCreate = computed(() => !form.id)
const canEditRole = computed(() => isCreate.value ? !isCS.value : isRoot.value)

const roleOptions = computed(() => {
  const creating = !form.id
  if (!creating) {
    // 编辑时保持原有口径（也可按需收紧）
    return ROLE_ALL
  }
  // 创建时：
  if (isRoot.value)      return ['SUPER_AGENT', 'AGENT', 'CS'] // root 不能创建 root
  if (isSuper.value)     return ['AGENT', 'CS']                // 超级代理：代理/客服
  if (isAgent.value)     return ['CS']                         // 代理：仅客服
  return []                                                   // CS 不可创建
})

// ⭐️ 新规则：创建时 ROOT / SUPER_AGENT 可改 agentId；编辑时只有 ROOT 可改
const canEditAgent = computed(() => isRoot.value)

// 只有 ROOT 能改“推广代码”
const canEditCode = computed(() => isRoot.value)
/* 列表分页检索 */
const page = ref(1)
const size = ref(12)
const total = ref(0)
const pages = ref(0)
const keyword = ref('')
const role = ref<string | undefined>('')
const status = ref<number | undefined>(undefined)

const list = ref<any[]>([])
const loading = ref(false)

async function reload(p = page.value) {
  loading.value = true
  page.value = p
  try {
    const { data } = await fetchUsers({
      page: page.value, size: size.value,
      keyword: keyword.value || undefined,
      role: role.value || undefined,
      status: status.value
    })
    if (data.code === 0) {
      const pg = data.data
      list.value = pg.records || []
      total.value = pg.total || 0
      size.value = pg.size || size.value
      page.value = pg.current || page.value
      pages.value = pg.pages || 0
    } else {
      alert(data.msg || '加载失败')
    }
  } finally {
    loading.value = false
  }
}
onMounted(() => reload(1))

/* 新建 / 编辑 */
const showModal = ref(false)
const saving = ref(false)
const err = ref('')

const form = reactive<any>({
  id: null,
  agentId: null,
  role: 'CS',
  username: '',
  password: '',
  displayName: '',
  code: '',
  status: 1,
  totpEnabled: false
})

function canDeleteRow(row:any){
  if (isCS.value) return false
  if (isRoot.value) return true
  // 超管/代理不能删更高或同级
  const rank = { ROOT:3, SUPER_AGENT:2, AGENT:1, CS:0 }
  return (rank[roleUp.value] || 0) > (rank[(row.role||'').toUpperCase()] || 0)
}


function openCreate() {
  if (isCS.value) { alert('无权限'); return }

  Object.assign(form, {
    id: null,
    // 只有 ROOT 可手填；其他角色固定为“自己”的 UID
    agentId: isRoot.value ? null : auth.userId,
    role: 'CS',
    username: '',
    password: '',
    displayName: '',
    code: '',
    status: 1,
    totpEnabled: false
  })
  if (!roleOptions.value.includes(form.role)) {
    form.role = roleOptions.value[0] || 'CS'
  }
  err.value = ''
  showModal.value = true
}



function openEdit(row: any) {
  Object.assign(form, {
    id: row.id,
    agentId: row.agentId,          // 编辑时展示原值；非 ROOT 会被禁用，不能改
    role: row.role || 'AGENT',
    username: row.username,
    password: '',
    displayName: row.displayName || '',
    code: row.code || '',
    status: row.status ?? 1,
    totpEnabled: !!row.totpEnabled
  })
  err.value = ''
  showModal.value = true
}
function closeModal() { showModal.value = false }

async function save() {
  if (!form.username) { err.value = '用户名不能为空'; return }
  if (!form.agentId && form.agentId !== 0) { err.value = '代理ID为必填'; return }
  if (!form.role) { err.value = '角色为必选'; return }
  if (!form.id && !form.password) { err.value = '新建时必须填写密码'; return }

  err.value = ''
  saving.value = true
  try {
    let resp
    if (form.id) {
      // 编辑：只有 ROOT 才能改 agentId；其它人不传该字段
      const payload:any = {
        role: form.role,
        username: form.username,
        displayName: form.displayName,
        status: Number(form.status),
        totpEnabled: form.totpEnabled ? 1 : 0
      }
      if (isRoot.value) payload.agentId = form.agentId
      if (form.password) payload.password = form.password
      if (canEditCode.value) payload.code = form.code
      resp = await updateUser(form.id, payload)
    } else {
      // 新建：ROOT / SUPER_AGENT 用用户填写的 agentId；其它角色强制为自己
      const agentIdToUse = (isRoot.value || isSuper.value) ? form.agentId : auth.userId
      resp = await createUser({
        agentId: agentIdToUse,
        role: form.role,
        username: form.username,
        password: form.password, // 明文
        displayName: form.displayName,
        code: canEditCode.value ? form.code : undefined,
        status: Number(form.status),
        totpEnabled: form.totpEnabled ? 1 : 0
      })
    }
    const { data } = resp
    if (data.code === 0) {
      closeModal()
      reload()
    } else {
      err.value = data.msg || '保存失败'
    }
  } finally {
    saving.value = false
  }
}


/* 状态切换（带确认） */
const showStatusConfirm = ref(false)
const statusRow = ref<any>(null)
const statusTo = ref<number>(1)
function confirmToggleStatus(u:any) {
  statusRow.value = u
  statusTo.value = u.status === 1 ? 0 : 1
  showStatusConfirm.value = true
}
async function doToggleStatus() {
  const u = statusRow.value
  const to = statusTo.value
  showStatusConfirm.value = false
  const { data } = await setUserStatus(u.id, to)
  if (data.code === 0) u.status = to
  else alert(data.msg || '状态修改失败')
}

/* 删除（仅 ROOT） */
const showDel = ref(false)
const delRow = ref<any>(null)
function confirmDelete(row:any) {
  // if (!isRoot.value) { alert('只有 ROOT 账户可删除'); return }
  delRow.value = row
  showDel.value = true
}
async function doDelete() {
  if (!isRoot.value) { alert('只有 ROOT 账户可删除'); showDel.value=false; return }
  const { data } = await deleteUser(delRow.value.id)
  if (data.code === 0) { showDel.value = false; reload() }
  else alert(data.msg || '删除失败')
}

/* 弹窗打开时锁滚动 */
watch([showModal, showDel, showStatusConfirm], ([a, b, c]) => {
  document.documentElement.style.overflow = (a || b || c) ? 'hidden' : ''
})

/* 小工具 */
function fmt(s: any) {
  if (!s) return '-'
  try {
    const d = new Date(s)
    const P = (n:number)=> String(n).padStart(2,'0')
    return `${d.getFullYear()}-${P(d.getMonth()+1)}-${P(d.getDate())} ${P(d.getHours())}:${P(d.getMinutes())}`
  } catch { return s }
}
</script>

<style scoped>
/* 入场动画 */
@keyframes pageIn { from {opacity:0; transform: translateY(6px)} to{opacity:1; transform:none} }
@keyframes softIn { from {opacity:0; transform: translateY(4px) scale(.98)} to{opacity:1; transform:none} }
.animate-page-in { animation: pageIn .36s ease-out both; }
.animate-soft-in { animation: softIn .36s ease-out both; }

/* 工具栏输入 */
.search{
  width: clamp(260px, 38vw, 520px);
}

/* 卡片与表格 */
.card { border-radius: 16px; background: rgba(255,255,255,.05); border: 1px solid rgba(255,255,255,.1); overflow: hidden; }
.thead { background: rgba(255,255,255,.05); color: rgba(255,255,255,.7); }
.table-center .th, .table-center .td { text-align: center; vertical-align: middle; }
.table-center .th.left, .table-center .td.left { text-align: left; }
.th { padding: 10px 12px; }
.tr { border-top: 1px solid rgba(255,255,255,.08); }
.td { padding: 10px 12px; color: #fff; }
.mono { font-family: ui-monospace, Menlo, Consolas, monospace; }
.empty { padding: 28px; text-align: center; color: rgba(255,255,255,.6) }

/* 基础控件 */
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
.sel{
  appearance: none; -webkit-appearance: none; -moz-appearance: none;
  border-radius: 12px;
}
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

/* 按钮 */
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

/* 状态更明显 */
.pill{
  padding: 6px 12px; border-radius: 999px; border: 1px solid rgba(255,255,255,.12);
  background: rgba(255,255,255,.06); color:#fff; cursor:pointer; font-size: 12px;
  display:inline-flex; align-items:center; gap:8px;
}
.pill .dot{ width:8px; height:8px; border-radius:50%; background:#94a3b8; }
.pill.strong[data-on="true"]{
  background: linear-gradient(90deg,rgba(16,185,129,.22),rgba(59,130,246,.10));
  border-color: rgba(16,185,129,.35);
  color:#d1fae5;
}
.pill.strong[data-on="true"] .dot{ background:#22c55e }
.pill.strong[data-on="false"]{
  background: linear-gradient(90deg,rgba(244,63,94,.22),rgba(59,130,246,.06));
  border-color: rgba(244,63,94,.35);
  color:#fecaca;
}
.pill.strong[data-on="false"] .dot{ background:#ef4444 }

/* 角色小标签 */
.tag{
  padding: 4px 8px; border-radius: 999px; font-size: 12px; font-weight: 700;
  background: rgba(255,255,255,.07); color: #e5e7eb;
}
.tag[data-variant="SUPER_AGENT"]{ background: rgba(234,179,8,.2); color:#fde68a; }
.tag[data-variant="AGENT"]{ background: rgba(59,130,246,.2); color:#bfdbfe; }
.tag[data-variant="CS"]{ background: rgba(16,185,129,.2); color:#bbf7d0; }

/* 开关 */
.switch{ display:inline-flex; align-items:center; gap:10px; color:#fff }
.switch input{ display:none }
.switch span{
  width:44px;height:24px; border-radius:999px; background:rgba(255,255,255,.15); position:relative; transition: background .2s;
}
.switch span::after{
  content:''; position:absolute; left:4px; top:4px; width:16px; height:16px;
  border-radius:50%; background:#fff; transition: transform .2s;
}
.switch input:checked + span{ background:rgba(16,185,129,.4) }
.switch input:checked + span::after{ transform: translateX(20px) }
.switch i{ font-style:normal; color:rgba(255,255,255,.7) }

/* 弹窗与遮罩（全屏柔光） */
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
.dialog.small{ width: 480px }
.title{ color:#fff; font-weight: 800; font-size: 18px }
.actions{ margin-top: 18px; display: flex; justify-content: flex-end; gap: 10px }

/* Vue 过渡 */
.modal-enter-from{ opacity: 0 }
.modal-enter-active{ transition: opacity .18s ease }
.modal-enter-to{ opacity: 1 }
.modal-leave-from{ opacity: 1 }
.modal-leave-active{ transition: opacity .18s ease }
.modal-leave-to{ opacity: 0 }
</style>
