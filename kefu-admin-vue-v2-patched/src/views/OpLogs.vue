<template>
  <div class="space-y-4">
    <!-- 工具栏 -->
    <div class="flex items-center gap-3">
      <input v-model="keyword"
             type="text"
             placeholder="搜索动作/操作详情"
             class="px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-sm w-60 focus:outline-none focus:ring-2 focus:ring-brand-500" />
      <input v-model="userId"
             type="number"
             placeholder="用户ID"
             class="px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-sm w-40 focus:outline-none focus:ring-2 focus:ring-brand-500" />
      <button @click="load(1)"
              class="px-4 py-2 rounded-lg bg-brand-500 hover:bg-brand-600 transition text-white text-sm">
        查询
      </button>
      <button @click="reset"
              class="px-4 py-2 rounded-lg bg-white/10 hover:bg-white/20 transition text-sm">
        重置
      </button>
    </div>

    <!-- 表格 -->
    <div class="overflow-x-auto rounded-2xl border border-white/10">
      <table class="min-w-full text-sm">
        <thead class="bg-white/10 text-white/70">
          <tr>
            <th class="text-left p-3">时间</th>
            <th class="text-left p-3">用户</th>
            <th class="text-left p-3">动作</th>
            <!-- <th class="text-left p-3">目标</th> -->
            <th class="text-left p-3">操作详情</th>
            <th class="text-left p-3">IP</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.id"
              class="border-t border-white/10 hover:bg-white/5 transition">
            <td class="p-3">{{ row.createdAt }}</td>
            <td class="p-3">{{ row.userId }}</td>
            <td class="p-3 font-medium text-brand-400">{{ row.op }}</td>
            <!-- <td class="p-3">{{ row.targetTable }}#{{ row.targetId }}</td> -->
            <td class="p-3">{{ row.opResult }}</td>
            <td class="p-3">{{ row.ip }}</td>
          </tr>
          <tr v-if="rows.length === 0">
            <td colspan="6" class="p-6 text-center text-white/40">暂无数据</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 分页器 -->
    <div class="flex justify-end items-center gap-2 text-sm text-white/70">
      <button :disabled="page<=1"
              @click="load(page-1)"
              class="px-3 py-1 rounded-lg bg-white/10 hover:bg-white/20 disabled:opacity-40">上一页</button>
      <span>第 {{ page }} 页 / 共 {{ totalPages }} 页</span>
      <button :disabled="page>=totalPages"
              @click="load(page+1)"
              class="px-3 py-1 rounded-lg bg-white/10 hover:bg-white/20 disabled:opacity-40">下一页</button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import api from '@/api'

const rows = ref([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const userId = ref('')
const keyword = ref('')

async function load(p = 1) {
  page.value = p
  const { data } = await api.get('/op-logs', {
    params: {
      page: page.value,
      size: size.value,
      userId: userId.value || undefined,
      q: keyword.value || undefined
    }
  })
  if (data?.code === 0) {
    rows.value = data.data.records || []
    total.value = data.data.total || 0
  } else {
    rows.value = []
    total.value = 0
  }
}

function reset() {
  userId.value = ''
  keyword.value = ''
  load(1)
}

const totalPages = computed(() => {
  const t = Number(total.value) || 0
  const s = Number(size.value) || 1
  return Math.max(1, Math.ceil(t / s))
})

onMounted(() => load(1))
</script>

<style>
/* Chrome / Edge / Safari */
input[type=number]::-webkit-inner-spin-button,
input[type=number]::-webkit-outer-spin-button {
  -webkit-appearance: none;
  margin: 0;
}
</style>