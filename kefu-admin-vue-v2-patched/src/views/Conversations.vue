<!-- ChatPanel.vue -->
<template>
  <div class="grid md:grid-cols-3 gap-6">
    <!-- 左侧会话列 -->
    <div class="md:col-span-1 space-y-3">
      <!-- 搜索条 -->
      <div class="flex items-center gap-2 mb-2">
        <input
          v-model.trim="searchQ"
          @keyup.enter="doSearch"
          placeholder="搜索：编号 / 备注 / IP / 地区"
          class="flex-1 px-3 py-2 rounded-xl bg-white/5 border border-white/10 focus:border-brand-400 outline-none"
        />
        <!-- <button
          class="px-3 py-2 rounded-xl bg-brand-600 hover:bg-brand-500 disabled:opacity-60"
          :disabled="searching"
          @click="doSearch"
          title="搜索"
        >搜索</button> -->
        <button
          v-if="searchQ"
          class="px-3 py-2 rounded-xl bg-white/10 hover:bg-white/20"
          @click="clearSearch"
          title="清除搜索"
        >清除</button>
      </div>
      <div
        ref="convList"
        class="space-y-2 max-h-[70vh] overflow-auto pr-2"
        @scroll="onConvScroll"
      >
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
            <span class="truncate max-w-[60%]" v-html="hi(c.customerNote || '无备注', searchQ)"></span>
            <span class="opacity-70">·</span>

            <div class="font-semibold" v-html="hi('编号: ' + c.id, searchQ)"></div>

            <span class="opacity-70">·</span>
            
            <div class="font-semibold">
              <div class="font-semibold" v-html="hi('地区: ' + (c.region || '-'), searchQ)"></div>
            </div>

            <span
              v-if="c.unreadCount>0"
              class="ml-auto inline-flex items-center justify-center text-[10px] min-w-[18px] h-[18px] px-1 rounded-full bg-rose-600 text-white"
            >
              {{ c.unreadCount }}
            </span>
            <!-- <div class="text-xs text-white/60">{{ c.channel }}</div> -->
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
        <div v-if="!hasMore" class="text-center text-white/50 py-2 text-sm">
          没有更多对话了！
        </div>
      </div>
    </div>

    <!-- 右侧聊天区 -->
    <div class="md:col-span-2 card p-0 overflow-hidden" v-if="current" :key="current.id">
      <!-- 顶部栏 -->
      <div class="p-4 bg-white/10 flex items-center justify-between">
        <div class="font-semibold">编号 #{{ current.id }}</div>
        <div class="text-xs text-white/60">客户ID - {{ current.customerId }}</div>
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
        @scroll="onMsgScroll"
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
            <div class="text-sm whitespace-pre-wrap break-words" v-else-if="m.contentType===1">
              {{ m.content }}
            </div>

            <!-- 图片 -->
            <div v-else-if="m.contentType===2" class="space-y-2">
              <img
                :src="fileUrl(m)"
                class="rounded-xl max-h-[280px] object-contain cursor-zoom-in"
                loading="lazy"
                @load="onMediaLoaded"
                @click="preview(fileUrl(m))"
              />
              <div v-if="m.fileName" class="text-xs opacity-70">{{ m.fileName }}</div>
            </div>

            <!-- 音频（后端：3） -->
            <!-- <div v-else-if="m.contentType===3" class="space-y-2">
              <audio
                :src="fileUrl(m)"
                class="w-full"
                controls
                preload="metadata"
              ></audio>
              <div v-if="m.fileName" class="text-xs opacity-70">{{ m.fileName }}</div>
            </div> -->

            <!-- 视频（后端：5） -->
            <div v-else-if="m.contentType===5 || m.contentType===3" class="space-y-2">
              <video
                :src="fileUrl(m)"
                class="rounded-xl max-h-[300px] bg-black"
                controls
                preload="metadata"
                @loadedmetadata="onMediaLoaded"
              />
              <div v-if="m.fileName" class="text-xs opacity-70">{{ m.fileName }}</div>
            </div>

            <!-- 通用文件（后端：4 或其他兜底） -->
            <div v-else class="space-y-1">
              <a :href="fileUrl(m)" target="_blank" class="underline break-all">
                {{ m.fileName || fileUrl(m) }}
              </a>
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

            <!-- <button
              v-if="qrHasMore"
              class="qr-chip more"
              @click="openQrModal"
              title="查看全部并管理"
            >更多…</button> -->

            <!-- 永远可见的设置按钮（解决有数据但不溢出时无法进入设置的问题） -->
            <button
              class="qr-gear"
              @click="openQrModal"
              title="设置快捷回复"
              aria-label="设置快捷回复"
            >
              更多⚙️
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
          >上传文件</button>
        </div>

        <!-- 文本输入 -->
        <textarea
          ref="inputEl"
          v-model="content"
          :rows="1"
          placeholder="输入消息...（Enter 发送，Shift+Enter 换行）"
          class="msg-textarea flex-1 px-4 py-2 rounded-xl bg-white/5 border border-white/10 focus:border-brand-400 outline-none transition resize-none"
          @keydown="onKeydownMessage"
          @input="autoResize"
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
      <div v-if="qrModal" class="qr-modal" @click.self="closeQrModal">
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
              <input
                v-model="item.msg"
                @input="item.dirty = true"
                class="qr-row-inp"
              />
              <div class="qr-row-actions">
                <button class="qr-btn primary" @click="sendFromModal(item.msg)">使用</button>
                <button class="qr-btn danger" @click="delQr(item)">删除</button>
              </div>
            </div>

            <div v-if="qrDraft.length===0" class="qr-empty">暂无数据</div>
          </div>

          <div class="qr-actions">
            <button class="qr-btn ghost" @click="qrModal=false">关闭</button>
            <button class="qr-btn primary" :disabled="qrSaving" @click="saveQrAll">
              {{ qrSaving ? '保存中…' : '保存' }}
            </button>
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

/** 会话搜索 */
const searchQ = ref('')
const searching = ref(false)

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
const stickToBottom = ref(true)

/** 音效 & Toast */
const ding = ref(null)
const showToast = ref(false)
const toastTitle = ref('新消息')
const toastMsg = ref('')

/** 拖拽上传 */
const dragOver = ref(false)
let dragCounter = 0
const fileInput = ref(null)

const qrOriginMap = ref({})

const page = ref(1)
const size = 20
const hasMore = ref(true)
const loadingMore = ref(false)

const isBootstrappingConv = ref(false) // 首批历史加载中
let enteredAtTs = 0                    // 进入此会话的时间戳

/** 统一把各种时间字段转成本地毫秒数（优先用后端的 createdAtTs） */
function normTs(m) {
  if (!m) return 0
  if (typeof m === 'number') return m
  // 记录对象：优先 createdAtTs；否则把 createdAt/createTime/gmtCreate 尝试转毫秒
  const t = m.createdAtTs ?? m.createdAt ?? m.createTime ?? m.gmtCreate
  if (typeof t === 'number') return t
  if (!t) return 0
  const n = Date.parse(String(t))
  return Number.isNaN(n) ? 0 : n
}

function hi (text, q) {
  if (!text) return ''
  if (!q) return String(text)
  const esc = q.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const re = new RegExp(`(${esc})`, 'ig')
  return String(text).replace(re, '<mark class="hi">$1</mark>')
}

/** 表情 */
const showEmoji = ref(false)
const emojis = [
  '😀','😁','😂','🤣','😃','😄','😅','😊','😍','😘',
  '😜','🤩','🤔','🙄','😴','😭','😡','🤝','👍','👎','👏','🙏','🔥'
]

/* ========= 基础加载 ========= */
/**
 * 加载会话列表：
 * - reset=true：重置到第一页并清空，再拉取
 * - reset=false：追加下一页，做去重合并；仅在“顶部或重置时”才全量重排
 */
const nextCursor = ref(null)  // { beforeAtTs, beforeId }

async function load(q = '', reset = true) {
  try {
    if (reset) {
      nextCursor.value = null
      hasMore.value = true
      convs.value = []
    }

   const params = { limit: size }               // 新：游标分页
   if (nextCursor.value) {
     params.beforeAtTs = nextCursor.value.beforeAtTs
     params.beforeId   = nextCursor.value.beforeId
   }
    if (q) params.q = q

    const { data } = await api.get('/conversations', { params })
    if (data?.code !== 0) {
      if (reset) { convs.value = []; hasMore.value = false }
      return
    }

    // 兼容后端两种返回：{ data: { records: [] }} 或 { data: [] }
    const payload = data.data || {}
    const list = payload.records || []
    hasMore.value = !!payload.hasMore
    nextCursor.value = payload.next || null

    // === 去重合并：用 id 作为 key，避免页间重叠/重复 ===
    if (reset) {
      convs.value = list
    } else {
      const map = new Map(convs.value.map(it => [it.id, it]))
      for (const it of list) {
        const old = map.get(it.id)
        map.set(it.id, old ? { ...old, ...it } : it)
      }
      convs.value = Array.from(map.values())
    }

    // === 更新每条的预览缓存 ===
    for (const c of list) {
      lastPreviewMap.value[c.id] = buildPreviewFromConv(c)
    }

    // === 仅在“重置”或“用户在列表顶部”时进行全量排序 ===
    if (reset || isConvListAtTop()) {
      convs.value.sort((a, b) => {
        // 优先 lastMessageAt，其次 startedAt；都无则 0
        const ta = Date.parse(a.lastMessageAt || a.startedAt || 0) || 0
        const tb = Date.parse(b.lastMessageAt || b.startedAt || 0) || 0
        // 二键稳定排序：时间相同再用 id DESC，避免抖动
        if (tb !== ta) return tb - ta
        return (b.id || 0) - (a.id || 0)
      })
    } 
  } catch (e) {
    // 拉取失败时，保留已有数据；首次加载失败则标记没有更多
    if (reset) hasMore.value = false
  }
}

function onKeydownMessage(e) {
  // 处理中文输入法时不要拦截
  if (e.isComposing) return;

  if (e.key === 'Enter') {
    if (e.shiftKey || e.ctrlKey || e.altKey || e.metaKey) {
      // 组合键 + Enter => 保持默认（换行）
      return;
    }
    // 纯 Enter => 发送
    e.preventDefault();
    send();
  }
}

function autoResize() {
  var el = inputEl.value;
  if (!el) return;
  el.style.height = 'auto';
  // 控制最大行数，避免无限增高（可按需调整）
  var lineHeight = 22; // 估算当前行高
  var maxLines = 8;
  var maxH = lineHeight * maxLines;
  el.style.height = Math.min(el.scrollHeight, maxH) + 'px';
}

function resetTextareaHeight() {
  // 等 DOM 更新后再复位
  nextTick(function () {
    var el = inputEl.value;
    if (!el) return;
    el.style.height = 'auto';
    el.rows = 1;
  });
}


async function loadMore() {
  if (!hasMore.value || loadingMore.value) return
  loadingMore.value = true
  try {
    await load(searchQ.value || '', false)    // 不重置，使用 nextCursor 继续拉
  } finally {
    loadingMore.value = false
  }
}

async function doSearch() {
  searching.value = true
  try {
    await load(searchQ.value || '')
    // 若搜索命中单一编号，可选：自动高亮或自动打开
    // const only = convs.value.length === 1 ? convs.value[0] : null
    // if (only) await open(only)
  } finally {
    searching.value = false
  }
}

async function clearSearch() {
  searchQ.value = ''
  // await load('')
}

async function pollConversations() {
  try {
    // 如果当前在分页（page>1）或用户不在列表顶部，则只做“轻量更新”，不要覆盖整段列表
    const atTop = isConvListAtTop()
    const isPaging = page.value > 1

    const params = { page: 1, size: 50 }
    if (searchQ.value) params.q = searchQ.value   // 轮询应尊重搜索条件

    const { data } = await api.get('/conversations', { params })
    if (data?.code !== 0) return
    const fresh = data.data?.records || data.data || []

    // 把 fresh 按 id 合并进现有 convs，绝不整体替换
    // 这样不会清空你已经翻下来的旧分页
    const map = new Map(convs.value.map(it => [it.id, it]))
    for (const it of fresh) {
      const old = map.get(it.id)
      map.set(it.id, old ? { ...old, ...it } : it)
    }

    // 只有当用户在顶部 & 非分页状态时，才进行全量排序到最新置顶
    // 防止用户正在往下翻页时顺序跳动
    const el = convList.value
    const prev = el ? el.scrollTop : 0
    convs.value = Array.from(map.values()).sort((a, b) => {
      const ta = Date.parse(a.lastMessageAt || a.startedAt || 0) || 0
      const tb = Date.parse(b.lastMessageAt || b.startedAt || 0) || 0
      // 二键：时间相同再用 id DESC，避免抖动
      if (tb !== ta) return tb - ta
      return (b.id || 0) - (a.id || 0)
    })
    await nextTick()
    if (el) el.scrollTop = prev

    // 更新列表预览
    for (const c of fresh) {
      lastPreviewMap.value[c.id] = buildPreviewFromConv(c)
    }

    // 仅统计“未读数上升”的会话，且排除当前打开的会话
    const bumps = []
    for (const c of fresh) {
      const prev = lastUnreadMap.value[c.id] ?? 0
      const now  = c.unreadCount ?? 0
      if (now > prev && (!current.value || c.id !== current.value.id)) {
        bumps.push(c)
      }
      lastUnreadMap.value[c.id] = now
    }

    if (bumps.length) {
      const c  = bumps[bumps.length - 1]
      const pv = buildPreviewFromConv(c)
      toastTitle.value = `用户消息，编号：${c.id} | 用户备注：${c.customerNote}`
      toastMsg.value   = pv.text || '有新消息'
      showToast.value  = true
      setTimeout(() => (showToast.value = false), 5000)

      try { if (ding.value) { ding.value.currentTime = 0; await ding.value.play() } } catch {}
    }
  } catch {}
}

// 辅助：判断会话列表是否在顶部（只在顶部才允许轮询重排）
function isConvListAtTop(th = 6) {
  const el = convList.value
  if (!el) return true
  return el.scrollTop <= th
}


/** 从会话构建最新消息预览 */
function buildPreviewFromConv(c) {
  if (!c || !c.lastMessageId) return { text: '暂无消息', time: '' }

  const m = {
    id: c.lastMessageId,
    senderType: c.lastMessageSenderType,
    contentType: c.lastMessageContentType,
    content: c.lastMessageContent,
    fileName: c.lastMessageFileName,
    isRevoked: c.lastMessageIsRevoked,
    createdAt: normTs({
      createdAtTs: c.lastMessageCreatedAtTs,
      createdAt: c.lastMessageCreatedAt
    })
  }
  return formatPreview(m)
}

/** 格式化预览（文本+时间） */
function formatPreview(m) {
  if (!m) return { text: '暂无消息', time: '' }
  if (m.isRevoked === 1) return { text: '（已撤回）', time: fmtTime(m.createdAt) }

  const ct = m.contentType || 1
  let text = ''
  if (ct === 1)      text = (m.content || '').replace(/\s+/g, ' ').trim()
  else if (ct === 2) text = '📷 图片'
  else if (ct === 3) text = '🎧 音频'
  else if (ct === 5) text = '🎬 视频'
  else               text = `📎 ${m.fileName || '文件'}`

  if (!text) text = '（空）'
  if (text.length > 32) text = text.slice(0, 32) + '…'

  return { text, time: fmtTime(m.createdAt) }
}

/** 打开会话 */
async function open(c) {
  isBootstrappingConv.value = true
  enteredAtTs = Date.now()
  current.value = c

  // 先清空，避免从“有消息”切到“无消息”时残留
  msgs.value = []
  content.value = ''
  lastId = 0
  stickToBottom.value = true
  await nextTick(() => { if (msgBox.value) msgBox.value.scrollTop = 0 })
  resetTextareaHeight();
  isBootstrappingConv.value = false

  // 拉取消息
  try {
    const { data } = await api.get('/messages', {
      params: {
        conversationId: c.id,
        page: 1,
        size: 200,
        side: 'CS',
        // 增量撤回同步：首次可不传（或传0），之后用上次 serverTime
        sinceRevokedAt: revokedSince.value || undefined
      }
    })

    if (data?.code === 0) {
      const payload = data.data ?? {}
      const recs = Array.isArray(payload.records)
        ? payload.records
        : (Array.isArray(payload) ? payload : [])

      // ✨ 统一转成本地毫秒时间
      msgs.value = recs.map(r => ({ ...r, createdAt: normTs(r) })).reverse()
      lastId = msgs.value.length ? msgs.value[msgs.value.length - 1].id : 0

      // 使用 waterline 把我方历史“已发送”改成“已读”
      applyCustomerReadWaterline(payload.customerReadMaxId || 0)

      // 应用本轮撤回
      applyRevoked(payload.revokedIds || [])

      // 记录 serverTime 作为下次 sinceRevokedAt
      revokedSince.value = payload.serverTime || Date.now()

      await scrollToBottom(false)
    } else {
      msgs.value = []
      lastId = 0
    }
  } catch (e) {
    msgs.value = []
    lastId = 0
  }

  // 加载客户资料（备注、IP等保持不变）
  try {
    const cu = await api.get(`/customers/${c.customerId}`)
    customer.value = cu.data?.data || null
    if (customer.value) {
      noteContent.value = customer.value.note || ''
      hasNote.value = !!noteContent.value
      isEditing.value = false
    }
  } catch (e) {
    customer.value   = null
    noteContent.value = ''
    hasNote.value     = false
    isEditing.value   = false
  }

  // 刷新右侧顶部预览
  lastPreviewMap.value[c.id] = msgs.value.length
    ? formatPreview(msgs.value[msgs.value.length - 1])
    : { text: '暂无消息', time: '' }
}

const qrSaving = ref(false)

async function saveQrAll () {
  // 1) 计算“当前可见顺序”的 sort（基于整个 qrDraft，而非脏子集）
  const idToNewSort = new Map(qrDraft.value.map((x, idx) => [x.id, idx + 1]))

  // 2) 逐条对比原始值与当前值，挑出真的变化
  const updates = []
  for (const item of qrDraft.value) {
    const id = item.id
    const nowMsg = (item.msg || '').trim()
    const nowSort = idToNewSort.get(id) ?? (item.sort ?? 0)

    const origin = qrOriginMap.value[id] || { msg: '', sort: item.sort ?? 0 }
    const msgChanged   = nowMsg !== (origin.msg || '')
    const orderChanged = nowSort !== (origin.sort ?? nowSort)

    if (msgChanged || orderChanged) {
      updates.push({ id, msg: nowMsg, sort: nowSort })
    }
  }

  if (updates.length === 0) {
    toastTitle.value = '提示'
    toastMsg.value   = '没有修改，不需要保存'
    showToast.value  = true
    setTimeout(() => (showToast.value = false), 1200)
    return
  }

  // 3) 只提交变化项（包含正确的 sort：基于“全列表”的序号）
  qrSaving.value = true
  try {
    await Promise.all(
      updates.map(u => api.put(`/quick-replies/${u.id}`, { msg: u.msg, sort: u.sort }))
    )

    await loadQuickReplies()
    // 更新快照，防止二次保存被误判
    const fresh = [...quickReplies.value].sort(
      (a,b)=> (a.sort??0)-(b.sort??0) || (a.id??0)-(b.id??0)
    )
    qrOriginMap.value = Object.fromEntries(
      fresh.map(x => [x.id, { msg: x.msg || '', sort: x.sort ?? 0 }])
    )
    qrDraft.value = fresh.map(x => ({ ...x }))

    toastTitle.value = '成功'
    toastMsg.value   = `已保存 ${updates.length} 条修改`
    showToast.value  = true
    setTimeout(() => (showToast.value = false), 1500)
    qrModal.value = false
  } catch (e) {
    toastTitle.value = '失败'
    toastMsg.value   = '保存失败，请重试'
    showToast.value  = true
    setTimeout(() => (showToast.value = false), 1800)
  } finally {
    qrSaving.value = false
  }
}
const convList = ref(null)

let convScrollTimer = null
function onConvScroll() {
  if (convScrollTimer) clearTimeout(convScrollTimer)
  convScrollTimer = setTimeout(() => {
    const el = convList.value
    if (!el) return
    const nearBottom = el.scrollHeight - el.scrollTop - el.clientHeight <= 60
    if (nearBottom) loadMore()
  }, 80)
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

/** 轮询新消息 */
async function poll() {
  if (isBootstrappingConv.value) return
  if (!current.value) return

  const { data } = await api.get('/messages', {
    params: {
      conversationId: current.value.id,
      afterId: lastId,
      side: 'CS',
      sinceRevokedAt: revokedSince.value || undefined
    }
  })
  if (data.code !== 0) return

  const payload = data.data || {}
  const arr = (payload.records || []).map(r => ({ ...r, createdAt: normTs(r) }))
  const waterline = payload.customerReadMaxId || 0

  if (waterline > 0) applyCustomerReadWaterline(waterline)

  // 先应用撤回（以防这批里有覆盖项）
  applyRevoked(payload.revokedIds || [])

  if (!arr.length) {
    // 也要推进游标（否则撤回会落后）
    revokedSince.value = payload.serverTime || revokedSince.value || Date.now()
    return
  }

  // REVERSE arr because backend returns DESC now
  arr.reverse()

  const wasAtBottom = stickToBottom.value || isNearBottom()
  arr.forEach(m => {
    const i = msgs.value.findIndex(x => x.id === m.id)
    if (i >= 0) {
      msgs.value[i] = { ...msgs.value[i], ...m }
    } else {
      msgs.value.push(m)
    }
  })
  lastId = msgs.value[msgs.value.length - 1].id

  if (wasAtBottom) {
    await scrollToBottom(false)
    stickToBottom.value = true
  }

  // 新入站消息的提示逻辑
  const inboundNew = arr.filter(m =>
    (m.senderType ?? 0) === 1 &&
    (m.isRevoked ?? 0) !== 1 &&
    normTs(m) > enteredAtTs           // ✅ 只提示进入会话之后产生的
  )
  if (inboundNew.length > 0) {
    const lastInbound = inboundNew[inboundNew.length - 1]
    toastTitle.value = `用户消息，会话编号：${current.value?.id} | 用户备注：${current.value?.customerNote || '—'}`
    toastMsg.value = previewText(lastInbound)
    showToast.value = true
    setTimeout(() => (showToast.value = false), 5000)
    try { await ding.value.play() } catch {}
  }

  lastPreviewMap.value[current.value.id] = formatPreview(msgs.value[msgs.value.length - 1])

  // 推进撤回游标
  revokedSince.value = payload.serverTime || revokedSince.value || Date.now()
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
  if (ct === 3) return '🎧 音频'
  if (ct === 5) return '🎬 视频'
  return `📎 ${m.fileName || '文件'}`
}

async function sendFromModal(text) {
  if (!current.value) {
    toastTitle.value = '提示'; toastMsg.value = '请先选择一个会话'; showToast.value = true;
    setTimeout(()=> showToast.value=false, 1500);
    return;
  }
  // 把内容放进输入框并立即发送
  content.value = text || '';
  await nextTick();
  // await send();
  // 也可以不关闭弹窗：按需注释
  qrModal.value = false;
}

/* ========= 发送文本 ========= */
async function send(){
  if (!content.value || !current.value || isSending.value) return;
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
  await keepBottom(false)
  resetTextareaHeight();

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
      await keepBottom(false)
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

function onMsgScroll() {
  stickToBottom.value = isNearBottom()
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

async function keepBottom(smooth = false) {
  if (!stickToBottom.value) return
  await scrollToBottom(smooth)
}

function onMediaLoaded() {
  if (!stickToBottom.value) return
  scrollToBottom(false)
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
    const kind = detectContentType(f)   // 2图 3音频 5视频 4文件
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
  await keepBottom(false)

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

      const fname = it.fileName || it.original || it.name || ''
      const ct    = it.ct ?? contentTypeMap(it.mime) ?? 4

      const req = {
        conversationId: current.value.id,
        senderType: 2,
        content: it.url,
        contentType: ct,
        fileName: fname,
        fileSize: it.size
      }
      const sent = await api.post('/messages', req)

      const idx = msgs.value.findIndex(x => x.id === t.tempId)
      if (idx >= 0) {
        if (sent?.data?.code === 0) {
          const real = (sent.data.data && (sent.data.data.record || sent.data.data)) || {}
          msgs.value[idx] = {
            ...t.tempMsg,
            ...real,
            url: it.url,
            fileName: fname,
            fileSize: it.size,
            contentType: ct
          }
        } else {
          msgs.value.splice(idx, 1)
        }
      }
    }

    await keepBottom(false)
  } catch (e) {
    for (const t of temps) {
      const i = msgs.value.findIndex(x => x.id === t.tempId)
      if (i >= 0) msgs.value.splice(i, 1)
    }
  }
}

function closeQrModal () {
  qrModal.value = false
}

// 撤回增量同步游标：传给 sinceRevokedAt，用后端返回的 serverTime 回写
const revokedSince = ref(0)

// 根据后端返回的 revokedIds，将当前列表中的对应消息标记为已撤回
function applyRevoked(ids) {
  if (!ids || !ids.length) return
  const set = new Set(ids)
  for (let i = 0; i < msgs.value.length; i++) {
    const m = msgs.value[i]
    if (m && m.id && set.has(m.id)) {
      msgs.value[i] = { ...m, isRevoked: 1 }
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
  const type = (file?.type || '').toLowerCase()
  if (/^image\//.test(type)) return 2
  if (/^audio\//.test(type)) return 3
  if (/^video\//.test(type)) return 5
  return 4
}
function contentTypeMap(mime){
  const m = (mime || '').toLowerCase()
  if (/^image\//.test(m)) return 2
  if (/^audio\//.test(m)) return 3
  if (/^video\//.test(m)) return 5
  return 4
}
function preview(url){
  window.open(url, '_blank')
}

/* ========= 时间 & 状态 ========= */
function fmtTime(ts) {
  const n = normTs(ts)
  if (!n) return ''
  const d  = new Date(n)
  const y  = d.getFullYear()
  const mo = String(d.getMonth() + 1).padStart(2, '0')
  const da = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${y}-${mo}-${da} ${hh}:${mm}`
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

watch(qrModal, (open) => {
  const body = document.body
  if (open) {
    // 锁定背景滚动
    body.style.overflow = 'hidden'
    // 兼容 iOS 橡皮筋，可按需加：
    body.style.position = 'relative'
  } else {
    // 恢复
    body.style.overflow = ''
    body.style.position = ''
  }
})

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
  // 以当前 quickReplies 的展示顺序作为“原始顺序”
  const list = [...quickReplies.value].sort(
    (a,b)=> (a.sort??0)-(b.sort??0) || (a.id??0)-(b.id??0)
  )

  // 记录原始值快照（用于保存时对比）
  qrOriginMap.value = Object.fromEntries(
    list.map(x => [x.id, { msg: x.msg || '', sort: x.sort ?? 0 }])
  )

  // 编辑副本
  qrDraft.value = list.map(x => ({ ...x }))

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

  // 标记排序发生过变化
  qrDraft.value.forEach(x => x.dirty = true)
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

// let debounceTimer = null
// watch(searchQ, () => {
//   if (debounceTimer) clearTimeout(debounceTimer)
//   debounceTimer = setTimeout(() => { doSearch() }, 300)
// })


// 初始与监听
onMounted(async () => {
  await loadQuickReplies()
  recalcVisibleCount()
  window.addEventListener('resize', recalcVisibleCount)
})
onUnmounted(() => {
  window.removeEventListener('resize', recalcVisibleCount)
  const body = document.body
  body.style.overflow = ''
  body.style.position = ''
})
</script>

<style scoped>
.hi { background: rgba(250, 204, 21, .35); padding: 0 2px; border-radius: 4px }

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
  margin-bottom: 10px;
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

  white-space: nowrap;
  overflow: hidden;          /* 只裁切掉超出部分，不显示省略号 */
  text-overflow: ellipsis;
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
  width: 82px; height: 32px;
  display:grid; place-items:center;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.1);
  border: 1px solid rgba(255,255,255,.18);
  color:#e5e7eb; cursor:pointer;
  flex: 0 0 auto;
}

/* ===== 快捷回复弹窗 ===== */
.qr-modal{
  position: fixed; inset: 0; z-index: 1;
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
/* 让遮罩自身可滚，避免内容很高时卡住 */
.qr-modal{
  overflow: auto;               /* 关键：遮罩允许滚动 */
}

/* 让对话框内部也能滚动（尤其是移动端） */
.qr-dialog{
  max-height: 80vh;             /* 关键：限定对话框高度 */
  overflow: auto;               /* 关键：内容超出时滚动 */
  -webkit-overflow-scrolling: touch; /* iOS 惯性滚动 */
}

/* 列表区已有 max-height，可保留 */
.qr-list{
  -webkit-overflow-scrolling: touch;
}

.msg-textarea {
  line-height: 1.4;
  overflow: hidden;            /* 配合 autoResize 防滚动条 */
  scrollbar-width: none;       /* Firefox 隐藏滚动条 */
}
.msg-textarea::-webkit-scrollbar { display: none; } /* WebKit 隐藏滚动条 */


</style>
