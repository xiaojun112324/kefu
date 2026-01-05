<template>
  <div class="space-y-5 animate-page-in">
    <!-- 顶部工具栏 -->
    <div class="toolbar flex flex-wrap items-center gap-3">
      <div class="text-xl font-bold text-white">客户管理</div>
      <div class="flex-1"></div>

      <div class="flex flex-wrap items-center gap-2 animate-soft-in">
        <input
          v-model.trim="keyword"
          @keyup.enter="load(1)"
          placeholder="搜索：用户备注 / 用户名"
          class="inp search"
        />
        <button class="btn-ghost" @click="load(1)">查询</button>
      </div>
    </div>

    <!-- 表格 -->
    <div class="card animate-soft-in">
      <table class="w-full text-sm table-center">
        <thead class="thead">
          <tr>
            <th class="th">UID</th>
            <th class="th">代理UID</th>
            <th class="th left">用户备注</th>
            <th class="th left">用户名</th>
            <th class="th">用户IP</th>
            <th class="th">用户地区</th>
            <th class="th">创建时间</th>
            <th class="th">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.id" class="tr">
            <td class="td mono">{{ row.id }}</td>
            <td class="td mono">{{ row.agentId ?? '—' }}</td>
            <td class="td left">{{ row.note ?? '—' }}</td>
            <td class="td left">{{ row.name ?? '—' }}</td>
            <td class="td">{{ row.firstIp ?? '—' }}</td>
            <td class="td">{{ row.region ?? '—' }}</td>
            <td class="td text-white/60">{{ fmt(row.createdAt) }}</td>
            <td class="td">
              <div class="flex items-center justify-center">
                <button class="btn-primary btn-sm" @click="goChat(row)">去聊天</button>
              </div>
            </td>
          </tr>

          <tr v-if="!loading && rows.length === 0">
            <td colspan="7" class="empty">暂无数据</td>
          </tr>
          <tr v-if="loading">
            <td colspan="7" class="empty">加载中…</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 分页 -->
    <div class="flex items-center justify-between text-white/70 animate-soft-in">
      <div>共 {{ total }} 条</div>
      <div class="flex items-center gap-2">
        <button class="btn-ghost" :disabled="page===1" @click="load(page-1)">上一页</button>
        <span>第 {{ page }} / {{ pages }} 页</span>
        <button class="btn-ghost" :disabled="page===pages || pages===0" @click="load(page+1)">下一页</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import api from '@/api'

const router = useRouter()

const rows = ref([])
const keyword = ref('')

const page   = ref(1)
const size   = ref(15)
const total  = ref(0)
const pages  = ref(0)
const loading = ref(false)

async function load(p = page.value){
  loading.value = true
  page.value = p
  try{
    const { data } = await api.get('/customers', {
      params: { page: page.value, size: size.value, keyword: keyword.value || undefined }
    })
    if (data.code === 0) {
      const pg = data.data
      rows.value  = pg.records || []
      total.value = pg.total   || 0
      size.value  = pg.size    || size.value
      page.value  = pg.current || page.value
      pages.value = pg.pages   || 0
    } else {
      alert(data.msg || '加载失败')
    }
  } finally {
    loading.value = false
  }
}
onMounted(()=> load(1))

function goChat(row){
  router.push({ path: '/conversations', query: { customerId: row.id } })
}

function fmt(s){
  if(!s) return '—'
  try{
    const d = new Date(s)
    const P = n => String(n).padStart(2,'0')
    return `${d.getFullYear()}-${P(d.getMonth()+1)}-${P(d.getDate())} ${P(d.getHours())}:${P(d.getMinutes())}`
  }catch{ return s }
}
</script>

<style scoped>
@keyframes pageIn { from {opacity:0; transform: translateY(6px)} to{opacity:1; transform:none} }
@keyframes softIn { from {opacity:0; transform: translateY(4px) scale(.98)} to{opacity:1; transform:none} }
.animate-page-in { animation: pageIn .36s ease-out both; }
.animate-soft-in { animation: softIn .36s ease-out both; }

.search{ width: clamp(320px, 38vw, 560px); }

.card { border-radius: 16px; background: rgba(255,255,255,.05); border: 1px solid rgba(255,255,255,.1); overflow: hidden; }
.thead { background: rgba(255,255,255,.05); color: rgba(255,255,255,.7); }
.table-center .th, .table-center .td { text-align: center; vertical-align: middle; }
.table-center .th.left, .table-center .td.left { text-align: left; }
.th { padding: 10px 12px; }
.tr { border-top: 1px solid rgba(255,255,255,.08); }
.td { padding: 10px 12px; color: #fff; }
.mono { font-family: ui-monospace, Menlo, Consolas, monospace; }
.empty { padding: 28px; text-align: center; color: rgba(255,255,255,.6) }

.inp{
  padding: 10px 12px; border-radius: 12px;
  background: rgba(14,18,28,.9);
  border: 1px solid rgba(255,255,255,.15);
  color: #e5e7eb; outline: none;
  transition: box-shadow .2s, border-color .2s, background .2s;
}
.inp::placeholder{ color: rgba(255,255,255,.4) }
.inp:focus{
  border-color: rgba(59,130,246,.6);
  box-shadow: 0 0 0 6px rgba(59,130,246,.15);
  background: rgba(14,18,28,.95);
}

.btn-primary{
  padding: 10px 14px; border-radius: 12px;
  background: linear-gradient(90deg,#06b6d4,#3b82f6,#8b5cf6);
  color:#fff; font-weight: 700; border:0; cursor: pointer;
  transition: transform .15s, filter .25s, background-position .9s;
  background-size: 220% 100%;
}
.btn-primary:hover{ background-position: 100% 0; filter: brightness(1.06) }
.btn-primary.btn-sm{ padding: 6px 10px; border-radius: 10px; font-size: 12px }

.btn-ghost{
  padding: 10px 14px; border-radius: 12px;
  background: rgba(255,255,255,.06);
  border: 1px solid rgba(255,255,255,.12);
  color:#fff; cursor:pointer;
}
</style>
