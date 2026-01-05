<!-- ChatPanel.vue -->
<template>
  <div class="grid md:grid-cols-3 gap-6">
    <!-- 左侧会话列 -->
    <div class="md:col-span-1 space-y-3">
      <div class="flex items-center gap-2">
        <button @click="load" class="px-3 py-2 rounded-lg bg-white/10 hover:bg-white/20">刷新</button>
      </div>

      <div class="space-y-2 max-h-[70vh] overflow-auto pr-2">
        <div
          v-for="c in convs"
          :key="c.id"
          @click="open(c)"
          :class="[
            'p-3 rounded-xl cursor-pointer transition',
            current?.id === c.id
              ? 'border-2 border-brand-500 bg-white/10'
              : 'border border-white/10 bg-white/5 hover:border-brand-400/50'
          ]"
        >
          <!-- 顶部：备注 + IP （白色加粗）-->
          <div class="text-sm font-semibold text-white flex items-center gap-2 mb-1">
            <span class="truncate max-w-[60%]">
              {{ c.customerNote || '无备注' }}
            </span>
            <span class="opacity-70">·</span>
            <span class="truncate">{{ c.customerIp || '-' }}</span>

            <div class="font-semibold">
              会话编号 #{{ c.id }}
              <span
                v-if="c.unreadCount>0"
                class="ml-2 inline-flex items-center justify-center text-[10px] min-w-[18px] h-[18px] px-1 rounded-full bg-rose-600 text-white"
              >{{ c.unreadCount }}</span>
            </div>
            <div class="text-xs text-white/60">{{ c.channel }}</div>
          </div>
          <!-- 底部：最新消息预览 + 时间 -->
          <div class="mt-1 text-[12px] flex items-center justify-between">
            <div class="text-white/60 truncate max-w-[80%]">
              {{ lastPreviewMap[c.id]?.text || '暂无消息' }}
            </div>
            <div class="text-white/40 shrink-0 ml-2">
              {{ lastPreviewMap[c.id]?.time || '' }}
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 右侧聊天区 -->
    <div class="md:col-span-2 card p-0 overflow-hidden" v-if="current">
      <!-- 顶部栏 -->
      <div class="p-4 bg-white/10 flex items-center justify-between">
        <div class="font-semibold">会话 #{{ current.id }}</div>
        <div class="text-xs text-white/60">客户 {{ current.customerId }}</div>
      </div>

      <!-- 顶部信息 + 单行备注（展示/编辑） -->
      <div v-if="current" class="p-3 mb-2 rounded-xl bg-white/5 border border-white/10">
        <div class="text-sm text-white/60 flex flex-wrap gap-4 items-center">
          <div>IP：{{ customer?.lastIp || customer?.firstIp || '-' }}</div>
          <div>地区：{{ customer?.region || '-' }}</div>
          <div>用户名：{{ customer?.name || '-' }}</div>

          <!-- 单行备注 -->
          <div class="flex items-center gap-2 p-2 bg-white/5 border border-white/10 rounded-xl flex-1 min-w-[240px]">
            <span class="shrink-0">备注：</span>

            <!-- 展示态 -->
            <div v-if="!isEditing" class="flex-1 min-w-0">
              <span class="block whitespace-nowrap overflow-hidden text-ellipsis">
                {{ hasNote ? noteContent : '无备注' }}
              </span>
            </div>

            <!-- 编辑态 -->
            <div v-else class="flex-1 min-w-0">
              <input
                v-model="editBuffer"
                placeholder="填写备注..."
                class="w-full px-3 py-2 rounded bg-white/10 border border-white/10 outline-none"
              />
            </div>

            <!-- 右侧按钮 -->
            <button
              v-if="!isEditing"
              @click="onEditClick"
              class="px-3 py-2 rounded bg-white/10 border border-white/10"
            >
              {{ hasNote ? '编辑' : '添加备注' }}
            </button>
            <button
              v-else
              @click="onSaveClick"
              class="px-3 py-2 rounded bg-brand-600/80 hover:bg-brand-600"
            >
              保存
            </button>
          </div>
        </div>
      </div>

      <!-- 消息列表 -->
      <div
        class="h-[60vh] overflow-auto p-4 space-y-3"
        ref="msgBox"
        @dragenter.prevent="onDragEnter"
        @dragover.prevent="onDragOver"
        @dragleave.prevent="onDragLeave"
        @drop.prevent="onDrop"
      >
        <!-- 拖拽覆盖层 -->
        <div
          v-if="dragOver"
          class="absolute inset-0 z-10 flex items-center justify-center pointer-events-none"
        >
          <div class="mx-6 my-4 w-full h-[60vh] rounded-2xl border-2 border-dashed border-brand-400/70 bg-brand-400/10 backdrop-blur-sm flex items-center justify-center">
            <div class="px-4 py-2 rounded-xl bg-black/60 text-white text-sm">
              松开以上传图片 / 视频 / 文件
            </div>
          </div>
        </div>

        <div
          v-for="m in msgs"
          :key="m.id"
          :class="m.senderType===2 ? 'text-right' : 'text-left'"
        >
          <!-- 气泡 -->
          <div
            class="inline-block px-3 py-2 rounded-2xl animate-fade-up max-w-[82%] text-left align-top"
            :class="m.senderType===2 ? 'bg-brand-600/60' : 'bg-white/10'"
          >
            <!-- 撤回 -->
            <div class="text-[12px] text-white/60" v-if="m.isRevoked===1">已撤回</div>

            <!-- 文本 -->
            <div class="text-sm whitespace-pre-wrap break-words" v-else-if="(m.contentType||1)===1">
              {{ m.content }}
            </div>

            <!-- 图片 -->
            <div v-else-if="m.contentType===2" class="space-y-2">
              <img
                :src="fileUrl(m)"
                class="rounded-xl max-h-[280px] object-contain cursor-zoom-in"
                loading="lazy"
                @click="preview(fileUrl(m))"
              />
              <div v-if="m.fileName" class="text-xs opacity-70">{{ m.fileName }}</div>
            </div>

            <!-- 视频 -->
            <div v-else-if="m.contentType===3" class="space-y-2">
              <video
                :src="fileUrl(m)"
                class="rounded-xl max-h-[300px] bg-black"
                controls
                preload="metadata"
              />
              <div v-if="m.fileName" class="text-xs opacity-70">{{ m.fileName }}</div>
            </div>

            <!-- 通用文件 -->
            <div v-else class="space-y-1">
              <a :href="fileUrl(m)" target="_blank" class="underline break-all">{{ m.fileName || fileUrl(m) }}</a>
              <div v-if="m.fileSize" class="text-xs opacity-70">{{ prettySize(m.fileSize) }}</div>
            </div>
          </div>

          <!-- 时间 + 状态行 -->
          <div
            class="mt-1 text-[11px] flex items-center gap-2"
            :class="m.senderType===2 ? 'justify-end text-white/60' : 'justify-start text-white/50'"
          >
            <span class="opacity-70">{{ fmtTime(m.createdAt || m.createTime || m.gmtCreate) }}</span>

            <template v-if="m.senderType===2">
              <span v-if="msgStatus(m).code==='sending'">发送中</span>
              <span v-else-if="msgStatus(m).code==='sent'">已发送</span>
              <span v-else-if="msgStatus(m).code==='read'" class="flex items-center gap-1">
                <span>已读</span><span aria-hidden="true" class="inline-block -ml-1">✓✓</span>
              </span>
              <span v-else-if="m.isRevoked===1">已撤回</span>
            </template>
          </div>
        </div>
      </div>

      <!-- ✅ 快捷回复条（在输入区上方） -->
      <div class="px-4 pt-3">
        <div ref="qrBar" class="qr-bar">
          <!-- 有数据：显示若干条 + 更多 + 永久“设置”按钮 -->
          <template v-if="quickReplies.length">
            <button
              v-for="(qr,i) in visibleQrs"
              :key="qr.id || i"
              class="qr-chip"
              :title="qr.msg"
              @click="applyQuick(qr.msg)"
            >{{ qr.msg }}</button>

            <button
              v-if="qrHasMore"
              class="qr-chip more"
              @click="openQrModal"
              title="查看全部并管理"
            >更多…</button>

            <!-- 永远可见的设置按钮（解决有数据但不溢出时无法进入设置的问题） -->
            <button
              class="qr-gear"
              @click="openQrModal"
              title="设置快捷回复"
              aria-label="设置快捷回复"
            >
              ⚙️
            </button>
          </template>

          <!-- 无数据：显示“设置快捷回复” -->
          <template v-else>
            <button class="qr-setup" @click="openQrModal">设置快捷回复</button>
          </template>
        </div>
      </div>

      <!-- 输入区 -->
      <div
        class="relative p-4 flex items-end gap-2 border-t border-white/10"
        @paste="onPaste"
      >
        <!-- 表情按钮 -->
        <div class="relative">
          <button
            class="px-3 py-2 rounded-xl bg-white/10 hover:bg-white/20"
            @click="toggleEmoji"
          >😀</button>

          <!-- 表情面板 -->
          <div
            v-if="showEmoji"
            class="absolute bottom-[120%] left-0 z-20 w-[280px] max-w-[90vw] p-2 rounded-xl border border-white/10 bg-black/70 backdrop-blur-md shadow-lg"
            @mousedown.prevent
          >
            <div class="grid grid-cols-8 gap-1 text-lg leading-[34px]">
              <button
                v-for="e in emojis"
                :key="e"
                class="hover:bg-white/10 rounded"
                @click="appendEmoji(e)"
              >{{ e }}</button>
            </div>
          </div>
        </div>

        <!-- 上传按钮（可多选） -->
        <div>
          <input
            ref="fileInput"
            type="file"
            class="hidden"
            multiple
            @change="onFileSelect"
          />
          <button
            class="px-3 py-2 rounded-xl bg-white/10 hover:bg-white/20"
            @click="fileInput?.click()"
            title="上传图片/视频/文件"
          >上传</button>
        </div>

        <!-- 文本输入 -->
        <input
          ref="inputEl"
          v-model="content"
          @keyup.enter="send"
          placeholder="输入消息..."
          class="flex-1 px-4 py-2 rounded-xl bg-white/5 border border-white/10 focus:border-brand-400 outline-none transition"
        />

        <!-- 发送按钮（发送中禁用 + 文案切换） -->
        <button
          :disabled="isSending || !canSend"
          @click="send"
          class="px-4 py-2 rounded-xl disabled:opacity-60 disabled:cursor-not-allowed"
          :class="isSending ? 'bg-white/20' : 'bg-brand-600 hover:bg-brand-500'"
        >{{ isSending ? '发送中…' : '发送' }}</button>

        <button @click="revokeLast" class="px-4 py-2 rounded-xl bg-white/10 hover:bg-white/20">撤回上一条</button>
      </div>
    </div>
  </div>

  <!-- 快捷回复弹窗：新增/编辑/删除/拖拽排序 -->
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="qrModal" class="qr-modal" @wheel.prevent>
        <div class="qr-dialog">
          <div class="qr-title">快捷回复管理</div>

          <!-- 新增 -->
          <div class="qr-add">
            <input v-model.trim="qrNew" class="qr-inp" placeholder="新增一条快捷回复…" @keyup.enter="createQr" />
            <button class="qr-btn primary" @click="createQr">添加</button>
          </div>

          <!-- 列表（可拖拽排序） -->
          <div class="qr-list" @dragover.prevent>
            <div
              v-for="(item, idx) in qrDraft"
              :key="item.id ?? idx"
              class="qr-row"
              draggable="true"
              @dragstart="onQrDragStart(idx)"
              @dragenter.prevent="onQrDragEnter(idx)"
              @dragend="onQrDragEnd"
            >
              <span class="drag" title="拖拽排序">☰</span>
              <input v-model="item.msg" class="qr-row-inp" />
              <div class="qr-row-actions">
                <button class="qr-btn ghost" @click="saveQr(item)">保存</button>
                <button class="qr-btn danger" @click="delQr(item)">删除</button>
              </div>
            </div>

            <div v-if="qrDraft.length===0" class="qr-empty">暂无数据</div>
          </div>

          <div class="qr-actions">
            <button class="qr-btn ghost" @click="qrModal=false">关闭</button>
            <button class="qr-btn primary" @click="saveQrOrder">保存排序</button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>

  <audio ref="ding" preload="auto">
    <source src="/src/public/mp3/msg-1.mp3" type="audio/mpeg" />
  </audio>

  <Toast
    :show="showToast"
    :title="toastTitle"
    :message="toastMsg"
    @close="showToast = false"
  />
</template>

<script setup>
import { useRoute } from 'vue-router'
import { ref, onMounted, onUnmounted, nextTick, computed, watch } from 'vue'
import Toast from '@/components/Toast.vue'
import api from '@/api'

// 放在 <script setup> 顶部变量区域
const route = useRoute()
let openedOnce = false  // 只自动打开一次，避免轮询覆盖用户手动选择

async function openFromQuery() {
  if (openedOnce) return
  const q = route.query || {}
  const convId = q.conversationId ? Number(q.conversationId) : null
  const custId = q.customerId ? Number(q.customerId) : null

  let target = null

  // ① 优先按 conversationId
  if (convId) {
    target = convs.value.find(c => Number(c.id) === convId) || null
  }

  // ② 其次按 customerId（若有多条取最新）
  if (!target && custId) {
    const list = convs.value.filter(c => Number(c.customerId) === custId)
    if (list.length) {
      list.sort((a, b) =>
        (new Date(b.startedAt || b.createdAt || 0).getTime() || b.id) -
        (new Date(a.startedAt || a.createdAt || 0).getTime() || a.id)
      )
      target = list[0]
    }
  }

  if (target) {
    openedOnce = true
    await open(target)
  }
}

/** 备注相关 */
const noteContent = ref('')     // 当前已保存的备注内容
const editBuffer  = ref('')     // 编辑中的临时值
const hasNote     = ref(false)  // 是否已有备注
const isEditing   = ref(false)  // 是否处于编辑态

/** 会话/消息/用户 */
const convs = ref([])
const current = ref(null)
const msgs = ref([])
const customer = ref(null)
// 会话列表增强：备注/IP/最新消息
const noteMap = ref({})
const ipMap = ref({})
const lastPreviewMap = ref({})

/** 输入与发送 */
const content = ref('')
const isSending = ref(false)
const canSend = ref(true)
const inputEl = ref(null)

// ===== 会话列表轮询 =====
let convsPoller = null
const lastUnreadMap = ref({})

/** 轮询/滚动 */
let lastId = 0
let poller = null
const msgBox = ref(null)

/** 音效 & Toast */
const ding = ref(null)
const showToast = ref(false)
const toastTitle = ref('新消息')
const toastMsg = ref('')

/** 拖拽上传 */
const dragOver = ref(false)
let dragCounter = 0
const fileInput = ref(null)

/** 表情 */
const showEmoji = ref(false)
const emojis = [
  '😀','😁','😂','🤣','😃','😄','😅','😊','😍','😘',
  '😜','🤩','🤔','🙄','😴','😭','😡','🤝','👍','👎','👏','🙏','🔥'
]

/* ========= 基础加载 ========= */
async function load(){
  const { data } = await api.get('/conversations', { params: { page:1, size:50 } })
  if (data.code !== 0) return
  const list = data.data.records || data.data || []
  convs.value = list

  // 直接用接口里的数据构建预览
  list.forEach(c => {
    if (c.lastMessageId) {
      const last = {
        id: c.lastMessageId,
        senderType: c.lastMessageSenderType,
        contentType: c.lastMessageContentType,
        content: c.lastMessageContent,
        fileName: c.lastMessageFileName,
        isRevoked: c.lastMessageIsRevoked,
        createdAt: c.lastMessageCreatedAt
      }
      lastPreviewMap.value[c.id] = formatPreview(last)
    } else {
      lastPreviewMap.value[c.id] = { text: '暂无消息', time: '' }
    }
  })

  lastUnreadMap.value = Object.fromEntries(list.map(c => [c.id, c.unreadCount ?? 0]))
}

async function pollConversations() {
  try {
    const { data } = await api.get('/conversations', { params: { page: 1, size: 50 } })
    if (data?.code !== 0) return
    const list = data.data?.records || data.data || []

    // 更新列表与预览
    convs.value = list
    list.forEach(c => {
      lastPreviewMap.value[c.id] = buildPreviewFromConv(c)
    })

    // 仅统计“未读数上升”的会话，且排除当前打开的会话
    const bumps = []
    list.forEach(c => {
      const prev = lastUnreadMap.value[c.id] ?? 0
      const now  = c.unreadCount ?? 0
      if (now > prev && (!current.value || c.id !== current.value.id)) {
        bumps.push(c)
      }
      lastUnreadMap.value[c.id] = now
    })

    if (bumps.length) {
      const c  = bumps[bumps.length - 1]
      const pv = buildPreviewFromConv(c)
      toastTitle.value = `用户消息，编号：${c.id} | 用户备注：${c.customerNote}`
      toastMsg.value   = pv.text || '有新消息'
      showToast.value  = true
      setTimeout(() => (showToast.value = false), 5000)

      try {
        if (ding.value) {
          ding.value.currentTime = 0
          await ding.value.play()
        }
      } catch {}
    }
  } catch {}
}

function buildPreviewFromConv(c) {
  if (!c || !c.lastMessageId) return { text: '暂无消息', time: '' }
  const m = {
    id: c.lastMessageId,
    senderType: c.lastMessageSenderType,
    contentType: c.lastMessageContentType,
    content: c.lastMessageContent,
    fileName: c.lastMessageFileName,
    isRevoked: c.lastMessageIsRevoked,
    createdAt: c.lastMessageCreatedAt
  }
  return formatPreview(m)
}

function formatPreview(m) {
  if (!m) return { text: '暂无消息', time: '' }
  if (m.isRevoked === 1) return { text: '（已撤回）', time: fmtTime(m.createdAt || m.createTime || m.gmtCreate) }

  const ct = m.contentType || 1
  let text = ''
  if (ct === 1) {
    text = (m.content || '').replace(/\s+/g, ' ').trim()
  } else if (ct === 2) {
    text = '📷 图片'
  } else if (ct === 3) {
    text = '🎬 视频'
  } else {
    text = `📎 ${m.fileName || '文件'}`
  }
  if (!text) text = '（空）'
  if (text.length > 32) text = text.slice(0, 32) + '…'
  return { text, time: fmtTime(m.createdAt || m.createTime || m.gmtCreate) }
}

async function open(c){
  current.value = c

  const { data } = await api.get('/messages', { params: { conversationId: c.id, page:1, size:200, side:'CS' } })
  if (data.code === 0) {
    const payload = data.data || {}
    const recs = payload.records || payload || []
    msgs.value = recs
    lastId = msgs.value.length ? msgs.value[msgs.value.length-1].id : 0

    // 使用 waterline 把我方历史“已发送”改成“已读”
    applyCustomerReadWaterline(payload.customerReadMaxId || 0)

    await nextTick()
    if (msgBox.value) msgBox.value.scrollTop = msgBox.value.scrollHeight
  }

  // 加载客户资料（直接读 note 字段）
  const cu = await api.get(`/customers/${c.customerId}`)
  customer.value = cu.data?.data || null
  if (customer.value) {
    noteContent.value = customer.value.note || ''
    hasNote.value = !!noteContent.value
    isEditing.value = false

    ipMap.value[c.customerId] = customer.value.lastIp || customer.value.firstIp || '-'
    noteMap.value[c.customerId] = { content: noteContent.value || '' }
  }

  if (Array.isArray(msgs.value) && msgs.value.length) {
    lastPreviewMap.value[c.id] = formatPreview(msgs.value[msgs.value.length - 1])
  }
}

onMounted(async () => {
  await load()
  await openFromQuery()

  poller = setInterval(async () => { if (current.value) await poll() }, 2000)
  convsPoller = setInterval(pollConversations, 3000)
})

onUnmounted(() => {
  if (poller) { clearInterval(poller); poller = null }
  if (convsPoller) { clearInterval(convsPoller); convsPoller = null }
})

/* ========= 轮询：叠加 + 合并状态 ========= */
async function poll() {
  if (!current.value) return

  const { data } = await api.get('/messages', {
    params: { conversationId: current.value.id, afterId: lastId, side: 'CS' }
  })
  if (data.code !== 0) return

  const payload = data.data || {}
  const arr = payload.records || []
  const waterline = payload.customerReadMaxId || 0

  if (waterline > 0) applyCustomerReadWaterline(waterline)
  if (!arr.length) return

  const wasAtBottom = isNearBottom()
  arr.forEach(m => {
    const i = msgs.value.findIndex(x => x.id === m.id)
    if (i >= 0) {
      msgs.value[i] = { ...msgs.value[i], ...m }
    } else {
      msgs.value.push(m)
    }
  })
  lastId = msgs.value[msgs.value.length - 1].id

  await nextTick()
  if (wasAtBottom && msgBox.value) {
    msgBox.value.scrollTop = msgBox.value.scrollHeight
  }

  const inboundNew = arr.filter(m => (m.senderType ?? 0) === 1 && (m.isRevoked ?? 0) !== 1)
  if (inboundNew.length > 0) {
    const lastInbound = inboundNew[inboundNew.length - 1]
    toastTitle.value = `用户消息，编号：${customer.value?.id} | 用户备注：${current.value?.customerNote}`
    toastMsg.value = previewText(lastInbound)
    showToast.value = true
    setTimeout(() => (showToast.value = false), 5000)
    try { await ding.value.play() } catch {}
  }

  lastPreviewMap.value[current.value.id] = formatPreview(msgs.value[msgs.value.length - 1])
}

function applyCustomerReadWaterline(waterline) {
  if (!waterline || !Array.isArray(msgs.value)) return
  for (let i = 0; i < msgs.value.length; i++) {
    const m = msgs.value[i]
    if (!m || !m.id || String(m.id).startsWith('tmp_')) continue
    if ((m.senderType ?? 0) !== 2) continue
    if ((m.isRevoked ?? 0) === 1) continue
    if ((m.isRead ?? 0) === 1) continue
    if (m.id <= waterline) m.isRead = 1
  }
}

function previewText(m) {
  if (!m) return ''
  const ct = m.contentType || 1
  if (ct === 1) {
    const t = (m.content || '').replace(/\s+/g, ' ').trim()
    return t.length > 40 ? t.slice(0, 40) + '…' : t
  }
  if (ct === 2) return '📷 图片'
  if (ct === 3) return '🎬 视频'
  return `📎 ${m.fileName || '文件'}`
}

/* ========= 发送文本 ========= */
async function send(){
  if (!content.value || !current.value || isSending.value) return
  isSending.value = true
  const text = content.value
  const tempId = `tmp_${Date.now()}`
  const temp = {
    id: tempId,
    conversationId: current.value.id,
    senderType: 2,
    contentType: 1,
    content: text,
    createdAt: Date.now(),
    isRead: 0
  }
  msgs.value.push(temp)
  lastPreviewMap.value[current.value.id] = formatPreview(temp)
  await nextTick(()=> { if (msgBox.value) msgBox.value.scrollTop = msgBox.value.scrollHeight })

  try {
    const req = { conversationId: current.value.id, contentType: 1, senderType: 2, content: text }
    const { data } = await api.post('/messages', req)
    if(data.code===0){
      const i = msgs.value.findIndex(x => x.id === tempId)
      if (i >= 0) {
        const real = (data.data && (data.data.record || data.data)) || {}
        msgs.value[i] = { ...temp, ...real }
      }
      content.value = ''
      await nextTick(()=> { if (msgBox.value) msgBox.value.scrollTop = msgBox.value.scrollHeight })
    } else {
      const i = msgs.value.findIndex(x => x.id === tempId)
      if (i >= 0) msgs.value.splice(i, 1)
    }
  } catch (e) {
    const i = msgs.value.findIndex(x => x.id === tempId)
    if (i >= 0) msgs.value.splice(i, 1)
  } finally {
    isSending.value = false
  }
}

/* ========= 撤回 ========= */
async function revokeLast(){
  if(!current.value) return
  const last = [...msgs.value].reverse().find(x => x.senderType===2 && x.isRevoked!==1)
  if(!last) return
  await api.post('/messages/revoke', { messageId: last.id, reason: '后台撤回' })
  open(current.value)
}

/* ========= 标记已读 ========= */
async function markRead(){
  if(!current.value) return
  try {
    await api.post(`/conversations/${current.value.id}/mark-read`, null, { params: { side: 'CS' } })
  } catch (e) {
    // ignore
  } finally {
    const now = Date.now()
    msgs.value.forEach(m => { if (m.senderType !== 2) m.csReadAt = m.csReadAt || now })
  }
}

function onEditClick() {
  editBuffer.value = noteContent.value || ''
  isEditing.value = true
  nextTick(()=> inputEl.value?.focus())
}

async function onSaveClick() {
  if (!current.value) return
  const cid = current.value.customerId
  const val = (editBuffer.value || '').trim()
  if (!val) {
    toastTitle.value = '提示'; toastMsg.value = '备注不能为空'; showToast.value = true
    setTimeout(() => (showToast.value = false), 2000)
    return
  }
  try {
    const { data } = await api.put(`/customers/${cid}/note`, { content: val })
    if (data?.code === 0) {
      noteContent.value = val
      hasNote.value = true
      isEditing.value = false

      if (current.value) current.value.customerNote = val
      const idx = convs.value.findIndex(x => x.id === (current.value?.id))
      if (idx >= 0) convs.value[idx] = { ...convs.value[idx], customerNote: val }

      toastTitle.value = '成功'; toastMsg.value = '备注已保存'; showToast.value = true
      setTimeout(() => (showToast.value = false), 2000)
    } else {
      throw new Error('save fail')
    }
  } catch (e) {
    toastTitle.value = '失败'; toastMsg.value = '保存备注失败'; showToast.value = true
    setTimeout(() => (showToast.value = false), 2000)
  }
}

function isNearBottom(threshold = 80) {
  const el = msgBox.value
  if (!el) return true
  return el.scrollHeight - el.scrollTop - el.clientHeight <= threshold
}

async function scrollToBottom(smooth = true) {
  await nextTick()
  const el = msgBox.value
  if (!el) return
  const to = el.scrollHeight - el.clientHeight
  if (smooth && 'scrollTo' in el) {
    el.scrollTo({ top: to, behavior: 'smooth' })
  } else {
    el.scrollTop = el.scrollHeight
  }
}

/* ========= 表情 ========= */
function toggleEmoji(){ showEmoji.value = !showEmoji.value }
function appendEmoji(e){
  const el = inputEl.value
  const add = e || ''
  if (!el) { content.value += add; return }
  const start = el.selectionStart ?? content.value.length
  const end   = el.selectionEnd ?? content.value.length
  content.value = content.value.slice(0, start) + add + content.value.slice(end)
  nextTick(() => {
    el.focus()
    const pos = start + add.length
    el.setSelectionRange(pos, pos)
  })
}

/* ========= 拖拽 / 选择 / 粘贴 上传 ========= */
function onDragEnter(){ dragCounter++; dragOver.value = true }
function onDragOver(){ dragOver.value = true }
function onDragLeave(){ dragCounter--; if (dragCounter<=0){ dragOver.value = false; dragCounter=0 } }
async function onDrop(e){
  dragCounter = 0; dragOver.value = false
  const files = [...(e.dataTransfer?.files || [])]
  if (!files.length) return
  await uploadAndSend(files)
}
async function onFileSelect(e){
  const files = [...(e.target?.files || [])]
  if (!files.length) return
  e.target.value = ''
  await uploadAndSend(files)
}
async function onPaste(e){
  const items = [...(e.clipboardData?.items || [])]
  const files = []
  for (const it of items) {
    if (it.kind === 'file') {
      const f = it.getAsFile()
      if (f) files.push(f)
    }
  }
  if (files.length) {
    e.preventDefault()
    await uploadAndSend(files)
  }
}

/** 实际上传 + 发送文件消息 */
async function uploadAndSend(files){
  if (!current.value || !files.length) return

  const temps = files.map(f => {
    const kind = detectContentType(f)
    const tempId = `tmp_${Date.now()}_${Math.random().toString(36).slice(2,7)}`
    const tempMsg = {
      id: tempId,
      conversationId: current.value.id,
      senderType: 2,
      contentType: kind,
      fileName: f.name,
      fileSize: f.size,
      url: '',
      createdAt: Date.now()
    }
    msgs.value.push(tempMsg)
    return { tempId, tempMsg, file: f }
  })
  await nextTick(()=> { if (msgBox.value) msgBox.value.scrollTop = msgBox.value.scrollHeight })

  try {
    const form = new FormData()
    for (const t of temps) form.append('files', t.file)
    const up = await api.post(`/messages/upload`, form, {
      params: { conversationId: current.value.id },
      headers: { 'Content-Type': 'multipart/form-data' }
    })

    if (up?.data?.code !== 0) throw new Error('upload fail')

    const list = up.data.data || []
    for (let i = 0; i < list.length; i++) {
      const it = list[i]
      const t  = temps[i]
      if (!it || !t) continue

      const req = {
        conversationId: current.value.id,
        senderType: 2,
        content: it.url,
        contentType: it.ct,
        fileName: it.fileName,
        fileSize: it.size
      }
      const sent = await api.post('/messages', req)

      const idx = msgs.value.findIndex(x => x.id === t.tempId)
      if (idx >= 0) {
        if (sent?.data?.code === 0) {
          const real = (sent.data.data && (sent.data.data.record || sent.data.data)) || {}
          msgs.value[idx] = { ...t.tempMsg, ...real, url: it.url, fileName: it.fileName, fileSize: it.size }
        } else {
          msgs.value.splice(idx, 1)
        }
      }
    }

    await nextTick(()=> { if (msgBox.value) msgBox.value.scrollTop = msgBox.value.scrollHeight })
  } catch (e) {
    for (const t of temps) {
      const i = msgs.value.findIndex(x => x.id === t.tempId)
      if (i >= 0) msgs.value.splice(i, 1)
    }
  }
}

/* ========= 渲染辅助 ========= */
function fileUrl(m){ return m.url || m.content || '' }
function prettySize(n){
  if (!n && n!==0) return ''
  const units = ['B','KB','MB','GB']
  let i=0, x=n
  while (x>=1024 && i<units.length-1){ x/=1024; i++ }
  return `${x.toFixed(x<10 && i>0 ? 1 : 0)}${units[i]}`
}
function detectContentType(file){
  if (/^image\//i.test(file.type)) return 2
  if (/^video\//i.test(file.type)) return 3
  return 4
}
function contentTypeMap(mime){
  if (!mime) return null
  if (/^image\//i.test(mime)) return 2
  if (/^video\//i.test(mime)) return 3
  return 4
}
function preview(url){
  window.open(url, '_blank')
}

/* ========= 时间 & 状态 ========= */
function fmtTime(ts) {
  if (!ts) return ''
  const d = typeof ts === 'number' ? new Date(ts) : new Date(String(ts))
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${hh}:${mm}`
}
function msgStatus(m) {
  if (m.isRevoked === 1) return { code: 'revoked' }
  if (m.senderType === 2) {
    if (!m.id || String(m.id).startsWith('tmp_')) return { code: 'sending' }
    return m.isRead === 1 ? { code:'read' } : { code:'sent' }
  }
  return { code: 'inbound' }
}

/* ================= 快捷回复（新增） ================= */
const quickReplies = ref([])        // 完整列表（含 id/msg/sort）
const qrModal = ref(false)          // 弹窗显隐
const qrDraft = ref([])             // 弹窗内的可编辑副本
const qrNew = ref('')               // 新增输入框
const visibleCount = ref(0)

/* 可见条目（给 chips） */
const visibleQrs = computed(() => quickReplies.value.slice(0, Math.max(0, visibleCount.value)))
const qrHasMore  = computed(() => quickReplies.value.length > visibleCount.value)

/* 根据窗口宽度 + 预留按钮位（齿轮 + 可能的“更多…”）动态计算展示数量 */
function baseSlots(w) {
  if (w >= 1440) return 8
  if (w >= 1280) return 7
  if (w >= 1024) return 6
  if (w >= 768)  return 5
  return 3
}
function recalcVisibleCount () {
  const w = window.innerWidth || 1200
  const base = baseSlots(w)
  if (!quickReplies.value.length) {
    visibleCount.value = 0
    return
  }
  // 预留：1 个齿轮 + 如果有更多再预留 1 个
  const needMore = quickReplies.value.length > (base - 1)
  const reserved = 1 + (needMore ? 1 : 0)
  visibleCount.value = Math.max(0, base - reserved)
}
watch(quickReplies, recalcVisibleCount, { deep: true })

function applyQuick (text) {
  const el = inputEl.value
  const add = text || ''
  if (!el) { content.value = (content.value || '') + add; return }
  const start = el.selectionStart ?? content.value.length
  const end   = el.selectionEnd ?? content.value.length
  content.value = content.value.slice(0, start) + add + content.value.slice(end)
  nextTick(() => {
    el.focus()
    const pos = start + add.length
    el.setSelectionRange(pos, pos)
  })
}

function openQrModal () {
  qrDraft.value = quickReplies.value.map(x => ({ ...x }))
  qrModal.value = true
}

async function loadQuickReplies () {
  try {
    const { data } = await api.get('/quick-replies', { params: { page:1, size:200 } })
    if (data?.code === 0) {
      const list = data.data?.records || data.data || []
      quickReplies.value = [...list].sort((a,b)=> (a.sort??0)-(b.sort??0) || (a.id??0)-(b.id??0))
    }
  } catch {}
}

async function createQr () {
  const msg = (qrNew.value || '').trim()
  if (!msg) return
  try {
    const nextSort = (quickReplies.value[quickReplies.value.length-1]?.sort || 0) + 1
    const { data } = await api.post('/quick-replies', { msg, sort: nextSort })
    if (data?.code === 0) {
      qrNew.value = ''
      await loadQuickReplies()
      qrDraft.value = quickReplies.value.map(x => ({ ...x }))
      toastTitle.value = '成功'; toastMsg.value = '已添加'; showToast.value = true
      setTimeout(()=> showToast.value=false, 1500)
    }
  } catch {}
}

async function saveQr (item) {
  if (!item?.id) return
  try {
    const { data } = await api.put(`/quick-replies/${item.id}`, { msg: item.msg, sort: item.sort ?? 0 })
    if (data?.code === 0) {
      await loadQuickReplies()
      toastTitle.value = '成功'; toastMsg.value = '已保存'; showToast.value = true
      setTimeout(()=> showToast.value=false, 1200)
    }
  } catch {}
}

async function delQr (item) {
  if (!item?.id) return
  if (!confirm('确认删除这条快捷回复？')) return
  try {
    const { data } = await api.delete(`/quick-replies/${item.id}`)
    if (data?.code === 0) {
      await loadQuickReplies()
      qrDraft.value = qrDraft.value.filter(x => x.id !== item.id)
      toastTitle.value = '成功'; toastMsg.value = '已删除'; showToast.value = true
      setTimeout(()=> showToast.value=false, 1200)
    }
  } catch {}
}

// 拖拽排序（在 qrDraft 中即时交换，然后统一保存）
let dragFrom = -1
function onQrDragStart (idx) { dragFrom = idx }
function onQrDragEnter (to) {
  if (dragFrom === -1 || dragFrom === to) return
  const arr = [...qrDraft.value]
  const [m] = arr.splice(dragFrom, 1)
  arr.splice(to, 0, m)
  qrDraft.value = arr
  dragFrom = to
}
function onQrDragEnd () { dragFrom = -1 }

/* 与后端对齐：没有批量排序接口，用逐条 PUT 更新 sort */
async function saveQrOrder () {
  const jobs = qrDraft.value.map((x, i) =>
    api.put(`/quick-replies/${x.id}`, { msg: x.msg, sort: i + 1 })
  )
  try {
    await Promise.all(jobs)
    await loadQuickReplies()
    toastTitle.value = '成功'; toastMsg.value = '排序已保存'; showToast.value = true
    setTimeout(()=> showToast.value=false, 1200)
    qrModal.value = false
  } catch {
    toastTitle.value = '失败'; toastMsg.value = '保存排序失败'; showToast.value = true
    setTimeout(()=> showToast.value=false, 1500)
  }
}

// 初始与监听
onMounted(async () => {
  await loadQuickReplies()
  recalcVisibleCount()
  window.addEventListener('resize', recalcVisibleCount)
})
onUnmounted(() => {
  window.removeEventListener('resize', recalcVisibleCount)
})
</script>

<style scoped>
/* 简易进入动画（如项目已全局有，可去掉） */
@keyframes fadeUp {
  from { opacity: 0; transform: translateY(6px); }
  to   { opacity: 1; transform: translateY(0); }
}
.animate-fade-up { animation: fadeUp .2s ease both; }

/* 让右侧聊天区成为拖拽容器的定位上下文 */
.md\:col-span-2.card.p-0.overflow-hidden { position: relative; }

/* Firefox */
.overflow-auto {
  scrollbar-width: thin;
  scrollbar-color: rgba(255,255,255,.25) transparent;
}

/* ===== 快捷回复条 ===== */
.qr-bar{
  display:flex; gap:8px; align-items:center;
  overflow:hidden; padding:6px 0 0;
}
.qr-chip{
  max-width: 240px;
  padding: 6px 10px;
  border-radius: 999px;
  font-size: 12px; font-weight: 800;
  background: rgba(255,255,255,.08);
  border: 1px solid rgba(255,255,255,.16);
  color:#e5e7eb; cursor:pointer;
  white-space: nowrap; text-overflow: ellipsis; overflow: hidden;
}
.qr-chip.more{
  background: rgba(59,130,246,.18);
  border-color: rgba(59,130,246,.35)
}
.qr-setup{
  padding: 6px 10px; border-radius: 999px;
  font-size: 12px; font-weight: 800;
  background: rgba(59,130,246,.18);
  border: 1px solid rgba(59,130,246,.35);
  color: #e5edff;
}
/* 永远可见的齿轮按钮 */
.qr-gear{
  width: 32px; height: 32px;
  display:grid; place-items:center;
  border-radius: 999px;
  background: rgba(255,255,255,.10);
  border: 1px solid rgba(255,255,255,.18);
  color:#e5e7eb; cursor:pointer;
  flex: 0 0 auto;
}

/* ===== 快捷回复弹窗 ===== */
.qr-modal{
  position: fixed; inset: 0; z-index: 9999;
  display:grid; place-items:center;
  background:
    radial-gradient(1200px 600px at 50% -10%, rgba(56,189,248,.10), transparent 60%),
    radial-gradient(1000px 600px at 100% 0, rgba(16,185,129,.10), transparent 55%),
    rgba(2,6,23,.72);
  backdrop-filter: blur(6px) saturate(120%);
}
.qr-dialog{
  width: 720px; max-width: 92vw;
  border-radius: 18px; background: rgba(13,18,28,.96);
  border: 1px solid rgba(255,255,255,.12);
  padding: 18px; box-shadow: 0 24px 80px rgba(0,0,0,.45);
}
.qr-title{ color:#fff; font-weight: 800; font-size: 16px; margin-bottom: 10px }

.qr-add{ display:flex; gap:8px; margin-bottom: 12px }
.qr-inp{
  flex:1; padding:10px 12px; border-radius: 12px;
  background: rgba(255,255,255,.06); border:1px solid rgba(255,255,255,.12);
  color:#fff; outline:none;
}
.qr-btn{
  padding: 8px 12px; border-radius: 10px; color:#fff;
  background: rgba(255,255,255,.08); border:1px solid rgba(255,255,255,.16); cursor:pointer;
}
.qr-btn.primary{ background: linear-gradient(135deg,#60a5fa,#22d3ee); border:0; color:#0b1020 }
.qr-btn.ghost{ background: rgba(255,255,255,.06) }
.qr-btn.danger{ background: rgba(244,63,94,.22); color:#fecaca; border:1px solid rgba(244,63,94,.35) }

.qr-list{ max-height: 52vh; overflow:auto; padding-right: 4px; }
.qr-row{
  display:flex; align-items:center; gap:10px;
  padding:8px; border-radius:12px; background: rgba(255,255,255,.04);
  border:1px solid rgba(255,255,255,.10); margin-bottom:8px;
}
.qr-row-inp{
  flex:1; padding:8px 10px; border-radius:10px;
  background: rgba(255,255,255,.06); border:1px solid rgba(255,255,255,.12);
  color:#fff; outline:none;
}
.qr-row-actions{ display:flex; gap:8px }
.drag{ cursor:grab; user-select:none; opacity:.8 }
.qr-empty{ padding:16px; text-align:center; color: rgba(255,255,255,.6) }

.qr-actions{ display:flex; justify-content:flex-end; gap:10px; margin-top: 10px }

/* 弹窗过渡（复用 modal 名称） */
.modal-enter-from{ opacity: 0 }
.modal-enter-active{ transition: opacity .18s ease }
.modal-enter-to{ opacity: 1 }
.modal-leave-from{ opacity: 1 }
.modal-leave-active{ transition: opacity .18s ease }
.modal-leave-to{ opacity: 0 }
</style>
