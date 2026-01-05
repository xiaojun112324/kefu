<template>
  <div class="grid md:grid-cols-3 gap-6">
    <div v-for="c in cards" :key="c.title" class="p-6 rounded-2xl bg-gradient-to-b from-white/10 to-white/5 border border-white/10 animate-fade-up">
      <div class="text-white/60 text-xs">{{ c.subtitle }}</div>
      <div class="text-3xl font-bold my-2">{{ c.value }}</div>
      <div class="text-sm text-white/60">{{ c.title }}</div>
    </div>
    <div class="md:col-span-3 rounded-2xl border border-white/10 p-6 bg-white/5 animate-fade-up">
      <div class="font-semibold mb-2">快速入口</div>
      <div class="flex flex-wrap gap-3">
        <RouterLink v-for="i in links" :key="i.path" :to="i.path"
          class="px-4 py-2 rounded-xl bg-white/10 hover:bg-white/20 transition shadow-glow/0 hover:shadow-glow">
          {{ i.title }}
        </RouterLink>
      </div>
    </div>
  </div>
</template>
<script setup>
const cards = [
  { title: '今日新客户', value: 12, subtitle: 'New Customers' },
  { title: '在线会话', value: 5, subtitle: 'Live Conversations' },
  { title: '未处理消息', value: 19, subtitle: 'Pending Messages' }
]
const links = [
  { path: '/customers', title: '客户' },
  { path: '/conversations', title: '会话' },
  { path: '/op-logs', title: '操作日志' }
]
</script>
