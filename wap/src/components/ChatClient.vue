<template> 
  <div class="chat-shell">
    <!-- Header -->
    <header class="chat-header">
      <div class="left">
        <!-- Brand Logo -->
        <img
          v-if="logo"
          :src="logo"
          alt="logo"
          class="brand-logo"
        />
        <div class="title">
          <!-- Brand Title -->
          <div class="name">{{ title }}</div>
          <div class="subtitle" :class="{ online: isOnline }">
            <span>Online</span> · {{ displayAgentName }}
          </div>
        </div>
      </div>
      <div class="right">
        <!-- Settings menu only -->
        <button class="icon-btn" @click="toggleSettings" title="Settings">
          <SvgIcon name="dots" />
        </button>
      </div>
      <transition name="fade">
        <div v-if="showSettings" class="dropdown">
          <div class="item" @click="toggleSound">
            <SvgIcon :name="soundOn ? 'bell' : 'bell-off'" />
            <span>{{ soundOn ? 'Disable sound' : 'Enable sound' }}</span>
          </div>
          <div class="item" @click="toggleCompact">
            <SvgIcon name="rows" />
            <span>{{ compact ? 'Comfortable density' : 'Compact density' }}</span>
          </div>
          <div class="item" @click="clearChat">
            <SvgIcon name="trash" />
            <span>Clear chat</span>
          </div>
        </div>
      </transition>
    </header>

    <!-- Messages -->
    <main class="chat-body" ref="scrollRef" @dragover.prevent @drop.prevent="onDrop">
      <div class="welcome" v-if="messages.length === 0">
        <img v-if="logo" :src="logo" alt="logo" class="welcome-logo" />
        <div class="welcome-text">
          <div class="brand">{{ brand }}</div>
          <div class="hint" v-if="brandIntro">{{ brandIntro }}</div>
          <div class="hint" v-else>Hi, I'm&nbsp;{{ displayAgentName }} — how can I help?</div>
        </div>
      </div>

      <div v-else class="msg-list" :class="{ compact }">
        <template v-for="(msg, idx) in messages" :key="msg.id">
          <div v-if="shouldShowTime(idx)" class="time-tag">
            {{ ts(messages[idx].createdAt) }}
          </div>

          <!-- System message -->
          <div v-if="msg.type === 'system'" class="sys">
            <SvgIcon name="spark" />
            <span>{{ msg.text }}</span>
          </div>

          <!-- Regular bubble -->
          <div
            v-else
            class="bubble"
            :class="[msg.from === 'me' ? 'me' : 'other', { sending: msg.status==='sending' || msg.status==='uploading' }]"
          >
            <img v-if="msg.from === 'other'" class="avatar" :src="displayAgentAvatar" alt="agent" />
            <div class="content">
              <div class="name" v-if="msg.from==='other'">{{ displayAgentName }}</div>

              <!-- Text / Rich -->
              <div v-if="msg.type==='text'" class="card text" v-html="linkify(msg.text)"></div>
              <div v-else-if="msg.type==='rich'" class="card text" v-html="msg.html"></div>

              <!-- Media / Location -->
              <div v-else-if="msg.type==='image'" class="card media">
                <img :src="msg.url" @click="previewMedia(msg.url, 'image')" />
              </div>
              <div v-else-if="msg.type==='video'" class="card media">
                <video
                  :src="msg.url"
                  controls
                  playsinline
                  preload="metadata"
                  :poster="msg.meta?.poster"
                  @click="previewMedia(msg.url, 'video')"
                />
              </div>
              <!-- File -->
              <div v-else-if="msg.type==='file'" class="card file">
                <a :href="msg.url" target="_blank" rel="noopener" class="file-row">
                  <SvgIcon name="paperclip" />
                  <div class="file-meta">
                    <div class="name">{{ msg.meta?.name || (msg.url?.split('/').pop() || 'file') }}</div>
                    <div class="size" v-if="msg.meta?.size">{{ prettySize(msg.meta.size) }}</div>
                  </div>
                </a>
              </div>
              <div v-else-if="msg.type==='audio'" class="card media audio">
                <audio :src="msg.url" controls preload="metadata"></audio>
                <div class="duration">{{ msg.meta?.duration ? formatDur(msg.meta.duration) : '' }}</div>
              </div>
              <div v-else-if="msg.type==='location'" class="card location" @click="openMap(msg.meta)">
                <SvgIcon name="location" />
                <div class="loc">
                  <div class="addr">{{ msg.meta?.label || 'Shared a location' }}</div>
                  <div class="coord">{{ msg.meta?.lat?.toFixed(5) }}, {{ msg.meta?.lng?.toFixed(5) }}</div>
                </div>
                <SvgIcon name="arrow-right" />
              </div>

              <!-- Meta line -->
              <div class="meta">
                <span class="time">{{ timeOnly(msg.createdAt) }}</span>
                <span v-if="msg.from==='me'" class="status">
                  <template v-if="msg.status==='uploading'">Uploading {{ msg.progress || 100 }}%</template>
                  <template v-else-if="msg.status==='sending'">Sending</template>
                  <template v-else-if="msg.status==='sent'">Sent</template>
                  <template v-else>Failed</template>
                </span>
              </div>
            </div>
          </div>
        </template>
      </div>

      <!-- Preview -->
      <transition name="fade">
        <div v-if="preview" class="preview" @click="preview=null">
          <img v-if="preview.kind==='image'" :src="preview.url" />
          <video v-else :src="preview.url" controls autoplay playsinline></video>
        </div>
      </transition>
    </main>

    <!-- Composer -->
    <footer class="chat-input" :class="{ recording: isRecording }">
      <div class="toolbar">
        <!-- Emoji -->
        <button class="icon-btn" @click="toggleEmoji" :class="{ active: showEmoji }" title="Emoji">
          <SvgIcon name="emoji" />
        </button>
        <!-- Location -->
        <button class="icon-btn" @click="shareLocation" title="Location">
          <SvgIcon name="location" />
        </button>
        <!-- Hold to record -->
        <button class="icon-btn"
                @mousedown.prevent="holdToRecordStart"
                @mouseup.prevent="holdToRecordStop"
                @touchstart.prevent="holdToRecordStart"
                @touchend.prevent="holdToRecordStop"
                :class="{ active: isRecording }"
                title="Hold to record">
          <SvgIcon name="mic" />
        </button>

        <div class="flex-spacer"></div>

        <!-- Send file -->
        <button class="pill outline" @click="onUploadFile" title="Send file">
          <SvgIcon name="paperclip" />
          <span>File</span>
        </button>
      </div>

      <div class="composer">
        <textarea
          ref="inputRef"
          v-model="draft"
          :placeholder="placeholder"
          rows="1"
          @keydown.enter.exact.prevent="sendText()"
          @input="autoResize"
          @paste="onPaste"
        />
        <button class="send-btn" :disabled="!canSend" @click="sendText" title="Send">
          <SvgIcon name="send" />
        </button>
      </div>

      <!-- Emoji panel -->
      <transition name="slide-up">
        <div v-if="showEmoji" ref="emojiPanelRef" class="emoji-panel">
          <div class="emoji-grid">
            <button v-for="e in emojis" :key="e" class="emoji" @click="appendEmoji(e)">
              {{ e }}
            </button>
          </div>
          <div class="emoji-actions">
            <button class="pill" @click="showEmoji=false">Done</button>
          </div>
        </div>
      </transition>

      <!-- Recording bar -->
      <transition name="fade">
        <div v-if="isRecording" class="recording-bar">
          <div class="dot"></div>
          <span>Recording… release to send</span>
          <span class="dur">{{ formatDur(recordingSec) }}</span>
        </div>
      </transition>
    </footer>

    <!-- Hidden file input -->
    <input ref="fileInput" type="file" class="hidden" @change="onPickFile" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, onBeforeUnmount, reactive, ref, nextTick, computed, h } from 'vue'
import { ensureConversation, listMessages, sendMessage, uploadFiles } from '@/api/messages'
import { ensureVisitorToken, persistVisitorToken, readVisitorToken, listenVTokenChange, buildShareUrl } from './visitorToken'


/** ======= props ======= */
const props = defineProps<{
  conversationId?: number | string
  title?: string
  brand?: string
  agentName?: string
  agentAvatar?: string
  isOnline?: boolean
}>()

/** ======= conversation & brand from ensure ======= */
const convId = ref<number | null>(null)
const title = ref(props.title ?? 'Live Support')
const brand = ref(props.brand ?? 'Concierge')
const logo = ref<string>('')                         
const brandIntro = ref<string>('')        
const csName = ref<string>('')   // boot.csName
const csLogo = ref<string>('')   // boot.csLogo           

async function initConversation() {
  // 先确保拿到本设备稳定 token
  const vtoken = ensureVisitorToken()

  // 把 token 传给后端，后端若返回标准化 token（可能与你生成的不一样），再以服务端为准更新存储
  const boot: any = await ensureConversation({ visitorToken: vtoken })

  // 如果后端回传了 token，就持久化成“后端口径”的
  if (boot?.visitorToken && boot.visitorToken !== vtoken) {
    persistVisitorToken(boot.visitorToken)
  }

  convId.value = Number(props.conversationId ?? boot.conversationId)

  // prefer backend; fallback to props/defaults
  title.value      = boot.brandName || props.title || 'Live Support'
  brand.value      = boot.brandName || props.brand || 'Concierge'
  logo.value       = boot.brandLogo  || ''
  brandIntro.value = boot.brandIntro || ''
  csName.value     = (boot.csName ?? '').trim()
  csLogo.value     = (boot.csLogo ?? '').trim()
}


/** ======= constants ======= */
const agentNameDefault   = 'Mei'
const agentAvatarDefault = 'https://unavatar.io/github/meiqia?fallback=true'

// ✅ 显示用（模板统一用这两个）
const displayAgentName = computed(() =>
  csName.value || props.agentName || agentNameDefault
)
const displayAgentAvatar = computed(() =>
  csLogo.value || props.agentAvatar || agentAvatarDefault
)
const isOnline = props.isOnline ?? true

const SENDER_TYPE_ME = 1
const CT = { TEXT:1, IMAGE:2, AUDIO:3, FILE:4, VIDEO:5, RICH:6 } as const

/** ======= state ======= */
type Msg =
  | { id: string, type: 'system', text: string, createdAt: number }
  | { id: string, type: 'text'|'rich', text?: string, html?: string, from: 'me'|'other', status?: 'uploading'|'sending'|'sent'|'failed', createdAt: number, meta?: Record<string, any>, progress?: number, srvId?: number }
  | { id: string, type: 'image'|'video'|'audio'|'file', url: string, from: 'me'|'other', status?: 'uploading'|'sending'|'sent'|'failed', createdAt: number, meta?: Record<string, any>, progress?: number, srvId?: number }
  | { id: string, type: 'location', from: 'me'|'other', status?: 'sending'|'sent'|'failed', createdAt: number, meta: { lat:number, lng:number, label?:string }, srvId?: number }

const messages = reactive<Msg[]>([])
const draft = ref('')
const inputRef = ref<HTMLTextAreaElement|null>(null)
const scrollRef = ref<HTMLElement|null>(null)
const preview = ref<{ url: string, kind: 'image'|'video' } | null>(null)

const showEmoji = ref(false)
const soundOn = ref(true)
const compact = ref(false)
const showSettings = ref(false)

const placeholder = 'Type a message…'
const emojis = Array.from('😀😁😂🤣😃😄😅😊😍😘😜🤩🤔🙄😴😭😡🤝👍👎👏🙏🔥')
const emojiPanelRef = ref<HTMLElement|null>(null)

/** ======= sounds ======= */
const msgSoundUrl = new URL('@/assets/msg.mp3', import.meta.url).href
let recvAudio: HTMLAudioElement | null = null

/** ======= polling control ======= */
let pollTimer: number | null = null
let lastId = 0
let revokedCursor = 0

/** ======= utils ======= */
const canSend = computed(()=> draft.value.trim().length>0)
const shouldShowTime = (idx: number) => idx === 0 || (messages[idx].createdAt - messages[idx-1].createdAt) > 1000*60*6
const ts = (t:number) => {
  const d=new Date(t)   // 本地时区自动渲染
  return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
const timeOnly = (t:number) => { const d=new Date(t); return `${pad(d.getHours())}:${pad(d.getMinutes())}` }
function pad(n:number){ return n<10 ? `0${n}` : `${n}` }
function linkify(text?: string){ if(!text) return ''; const s=escapeHtml(text); return s.replace(/(https?:\/\/[^\s]+)/g,'<a href="$1" target="_blank" rel="noopener">$1</a>') }
function appendEmoji(e: string){ draft.value += e }
function autoResize(e: Event){ const ta=e.target as HTMLTextAreaElement; ta.style.height='auto'; ta.style.height=Math.min(160, ta.scrollHeight)+'px' }
function escapeHtml(s:string){ return s.replace(/[&<>"']/g, c=>({ '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;' }[c] as string)) }
function safeParseJson(s:any){ try{ return s ? JSON.parse(s) : undefined }catch{ return undefined } }
function formatDur(total:number){ total = Math.max(0, total|0); const m = Math.floor(total/60); const s = total%60; return `${pad(m)}:${pad(s)}` }
function prettySize(n?: number){
  if (!n && n!==0) return ''
  const u = ['B','KB','MB','GB','TB']; let i=0, x=n
  while (x>=1024 && i<u.length-1){ x/=1024; i++ }
  return `${x.toFixed(x<10 && i>0 ? 1 : 0)}${u[i]}`
}

/** ======= adapt backend record ======= */
function adaptIncoming(rec: any): Msg | null {
  if ((rec.isRevoked ?? rec.revoked ?? 0) === 1) return null
  const createdAt = rec.createdAtTs ? Number(rec.createdAtTs) : Date.now()
  const from: 'me'|'other' = rec.senderType === SENDER_TYPE_ME ? 'me' : 'other'
  const ct = Number(rec.contentType || CT.TEXT)
  const meta = safeParseJson(rec.metaJson)
  const status: 'sent' = 'sent'

  if (ct === CT.TEXT)  return { id:String(rec.id), srvId:rec.id, type:'text', text:rec.content||'', from, status, createdAt, meta }
  if (ct === CT.RICH)  return { id:String(rec.id), srvId:rec.id, type:'rich', html:rec.content||'', from, status, createdAt, meta }
  if (ct === CT.IMAGE) return { id:String(rec.id), srvId:rec.id, type:'image', url:rec.content, from, status, createdAt, meta }
  if (ct === CT.VIDEO) return { id:String(rec.id), srvId:rec.id, type:'video', url:rec.content, from, status, createdAt, meta }
  if (ct === CT.AUDIO) return { id:String(rec.id), srvId:rec.id, type:'audio', url:rec.content, from, status, createdAt, meta }
  if (ct === CT.FILE) {
    return {
      id: String(rec.id),
      srvId: rec.id,
      type: 'file',
      url: rec.content,
      from,
      status,
      createdAt,
      meta
    }
  }
  return null
}

async function scrollToBottom(){ await nextTick(); const el=scrollRef.value; if(el) el.scrollTo({ top: el.scrollHeight, behavior:'smooth' }) }
async function smoothToBottom(){ await nextTick(); const el=scrollRef.value; if(el) el.scrollTo({ top: el.scrollHeight+9999, behavior:'smooth' }) }

/** ======= polling (with revoke sync) ======= */
async function fetchNew() {
  try {
    if (!convId.value) return
    const payload:any = await listMessages({
      conversationId: convId.value,
      size: 100,
      afterId: lastId || undefined,
      side: 'USER',
      sinceRevokedAt: revokedCursor || undefined,
    })

    const recs = Array.isArray(payload?.records) ? payload.records : []
    // Reverse because backend now returns DESC
    recs.reverse()

    const revokedIdsArr = Array.isArray(payload?.revokedIds) ? payload.revokedIds : []
    const nextCursor = Number(payload?.serverTime || Date.now())

    if (revokedIdsArr.length) {
      const set = new Set(revokedIdsArr.map((n:any)=>Number(n)))
      for (let i=messages.length-1;i>=0;i--) {
        const sid = Number((messages[i] as any).srvId)
        if (sid && set.has(sid)) messages.splice(i,1)
      }
    }

    let hasOther = false, appended = false
    const adapted: Msg[] = []
    for (const r of recs) {
      const m = adaptIncoming(r)
      if (!m) continue
      adapted.push(m)
      if (m.from==='other') hasOther = true
      const idn = Number(r.id); if (!Number.isNaN(idn)) lastId = Math.max(lastId, idn)
    }

    for (const m of adapted) {
      const exist = messages.find(x => (x as any).srvId === (m as any).srvId)
      if (exist) { exist.status = m.status; continue }

      if (m.from === 'me') {
        const pending = messages.find(x =>
          x.from === 'me' && !(x as any).srvId && x.type === m.type &&
          ( (x.type==='text' && (x as any).text===(m as any).text) ||
            (('url' in x) && (x as any).url && (m as any).url && (x as any).url===(m as any).url))
        )
        if (pending) {
          (pending as any).srvId = (m as any).srvId
          pending.status = m.status
          if ('url' in m) (pending as any).url = (m as any).url
          continue
        }
      }
      messages.push(m); appended = true
    }

    if (appended) await scrollToBottom()
    revokedCursor = nextCursor
    if (hasOther) playRecv()
  } catch {}
}

/** ======= send text ======= */
async function sendText(){
  const text = draft.value.trim()
  if(!text) return

  const raw: Msg = {
    id: nid(),
    type: 'text',
    from: 'me',
    text,
    status: 'sending',
    createdAt: Date.now()
  }

  // push 后立刻拿到“代理对象”再做后续修改
  const idx = messages.push(raw) - 1
  const local = messages[idx] as Msg

  draft.value = ''
  showEmoji.value = false
  await smoothToBottom()

  try{
    if (!convId.value) await initConversation()
    const saved = await sendMessage({
      conversationId: convId.value!,
      senderType: SENDER_TYPE_ME,
      contentType: CT.TEXT,
      content: text
    })
    ;(local as any).srvId = saved?.id
    local.status = 'sent'          // ✅ 改“代理对象”，立即触发渲染
    await nextTick()
  }catch{
    local.status = 'failed'
    await nextTick()
  }
}



/** ======= file / paste / drop ======= */
const fileInput = ref<HTMLInputElement|null>(null)
function onUploadFile(){ fileInput.value?.click() }
function onPickFile(e: Event){
  const fs = (e.target as HTMLInputElement).files
  if (!fs || fs.length === 0) return
  void commonUploadAndSend(fs, 'file')
  ;(e.target as HTMLInputElement).value = ''
}
function onPaste(e: ClipboardEvent){
  const item = e.clipboardData?.items && Array.from(e.clipboardData.items).find(i=> i.type.startsWith('image/'))
  if(item){
    const file = item.getAsFile()
    if(file){ void commonUploadAndSend([file], 'image'); e.preventDefault() }
  }
}
function onDrop(e: DragEvent){
  const file = e.dataTransfer?.files?.[0]
  if(!file) return
  const t = (file.type || '').toLowerCase()
  if (t.startsWith('image/'))      void commonUploadAndSend([file], 'image')
  else if (t.startsWith('video/')) void commonUploadAndSend([file], 'video')
  else if (t.startsWith('audio/')) void commonUploadAndSend([file], 'audio')
  else                             void commonUploadAndSend([file], 'file')
}

async function commonUploadAndSend(files: FileList | File[], preferType?: 'image'|'video'|'file'|'audio') {
  const list = Array.from(files || []).filter(Boolean)
  if (list.length === 0) return

  const locals: Msg[] = list.map((f) => {
    const mime = (f.type || '').toLowerCase()
    const url  = URL.createObjectURL(f)

    // 显示类型：优先使用 preferType，其次依据 MIME
    type DisplayType = 'image' | 'video' | 'audio' | 'file'
    let displayType: DisplayType = 'file'
    if (preferType === 'image' || mime.startsWith('image/')) displayType = 'image'
    else if (preferType === 'video' || mime.startsWith('video/')) displayType = 'video'
    else if (preferType === 'audio' || mime.startsWith('audio/')) displayType = 'audio'

    // 公共字段
    const base = {
      id: nid(),
      from: 'me' as const,
      status: 'uploading' as const,
      createdAt: Date.now(),
      progress: 1,
      meta: { name: f.name, size: f.size, mime: f.type }
    }

    if (displayType === 'image')  return { ...base, type: 'image', url } as Msg
    if (displayType === 'video')  return { ...base, type: 'video', url } as Msg
    if (displayType === 'audio')  return { ...base, type: 'audio', url } as Msg
    return { ...base, type: 'file', url } as Msg  // 兜底：文件气泡
  })

  messages.push(...locals)
  // ✅ 取出刚 push 进去的“代理对象数组”
  const base = messages.length - locals.length
  const proxies: Msg[] = locals.map((_, i) => messages[base + i] as Msg)

await smoothToBottom()
  try{
    if (!convId.value) { await initConversation() }
    const infos = await uploadFiles({
      conversationId: convId.value, 
      files: list,
      onProgress: (p:number) => { proxies.forEach(m => (m.progress = p)) } // ✅ 改代理对象
    })

    for (let i=0;i<infos.length;i++){
      const info:any = infos[i]
      const local = proxies[i]                                  // ✅ 用代理对象
      const sendCt =
      (local.type === 'image') ? CT.IMAGE :
      (local.type === 'video') ? CT.VIDEO :
      (local.type === 'audio') ? CT.AUDIO :
      mapUploadInfoToCt(info)
      const content = info.url

      try{
        const saved = await sendMessage({
          conversationId: convId.value,
          senderType: SENDER_TYPE_ME,
          contentType: sendCt,
          content,
          metaJson: JSON.stringify({ name: info.originalName, size: info.size, mime: info.mime, ...local.meta }),
        })
        if ('url' in local) (local as any).url = content
        if (local.meta) {
          local.meta.name = info.originalName || local.meta.name
          local.meta.size = info.size ?? local.meta.size
          local.meta.mime = info.mime ?? local.meta.mime
        }
        ;(local as any).srvId = saved.id
        local.status = 'sent'                                     // ✅ 改代理对象
        await nextTick()
      } catch {
        local.status = 'failed'
        await nextTick()
      }
    }
  } catch {
    proxies.forEach(m => m.status = 'failed')
  }
}

function genTextForFile(info:any){
  const name = info?.originalName || info?.url?.split('/').pop() || 'file'
  return `Sent file: <a href="${info.url}" target="_blank" rel="noopener">${escapeHtml(name)}</a>`
}
function mapUploadInfoToCt(info:any){
  const mime = (info?.mime || '').toLowerCase()

  if (mime.startsWith('image/')) return CT.IMAGE
  if (mime.startsWith('video/')) return CT.VIDEO
  if (mime.startsWith('audio/')) return CT.AUDIO

  // 再兜底检查 info.ct
  if (Number(info?.ct) === CT.IMAGE) return CT.IMAGE
  if (Number(info?.ct) === CT.VIDEO) return CT.VIDEO
  if (Number(info?.ct) === CT.AUDIO) return CT.AUDIO
  if (Number(info?.ct) === CT.FILE)  return CT.FILE

  return CT.FILE  // 最后兜底
}


/** ======= preview / location / recording ======= */
function previewMedia(url:string, kind:'image'|'video'){ preview.value = { url, kind } }
function shareLocation(){
  if(!('geolocation' in navigator)) { toast('Geolocation is not supported'); return }
  navigator.geolocation.getCurrentPosition(async (pos)=>{
    const { latitude:lat, longitude:lng } = pos.coords as any
    const txt = `My location: ${lat.toFixed(5)}, ${lng.toFixed(5)}`
    const raw: Msg = { id: nid(), type:'text', from:'me', text: txt, status:'sending', createdAt: Date.now() }
    const idx = messages.push(raw) - 1
    const local = messages[idx] as Msg
    await smoothToBottom()
    try{
      const saved = await sendMessage({
        conversationId: convId.value,
        senderType: SENDER_TYPE_ME,
        contentType: CT.TEXT,
        content: txt,
        metaJson: JSON.stringify({ lat, lng, label: 'My location' }),
      })
      ;(local as any).srvId = saved.id
      local.status = 'sent'
      await nextTick()
    }catch{ local.status = 'failed' }
  }, ()=>{ toast('Location failed. Please check permissions.') }, { enableHighAccuracy:true, timeout: 8000 })
}
function openMap(meta:any){ window.open(`https://maps.google.com/?q=${meta.lat},${meta.lng}`, '_blank') }

let mediaRecorder: MediaRecorder | null = null
let chunks: BlobPart[] = []
let chosenMime: string | undefined
const isRecording = ref(false)
const recordingSec = ref(0)
let timer: number | null = null
let recStartedAt = 0
let stopping = false

function pickSupportedAudioMime(): string | undefined {
  const c = ['audio/mp4','audio/webm;codecs=opus','audio/webm','audio/mpeg']
  return c.find(t => (window as any).MediaRecorder?.isTypeSupported?.(t))
}
async function holdToRecordStart() {
  if (isRecording.value || stopping) return
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    chosenMime = pickSupportedAudioMime()
    mediaRecorder = chosenMime ? new MediaRecorder(stream, { mimeType: chosenMime }) : new MediaRecorder(stream)
    chunks = []
    mediaRecorder.ondataavailable = (evt) => { if (evt.data && evt.data.size) chunks.push(evt.data) }
    mediaRecorder.onstop = async () => {
      try{
        const type = mediaRecorder?.mimeType || chosenMime || 'audio/mp4'
        const blob = new Blob(chunks, { type })
        const ext =
          type.includes('webm') ? 'webm' :
          type.includes('mpeg') ? 'mp3'  :
          type.includes('ogg')  ? 'ogg'  :
          type.includes('wav')  ? 'wav'  :
          'm4a' // mp4/aac
        const file = new File([blob], `voice_${Date.now()}.${ext}`, { type })
        await commonUploadAndSend([file], 'audio')
      } finally {
        try { (mediaRecorder as any)?.stream?.getTracks?.().forEach((t: MediaStreamTrack) => t.stop()) } catch {}
        mediaRecorder = null; stopping = false
        if (timer) { clearInterval(timer as any); timer = null }
        isRecording.value = false
      }
    }
    recStartedAt = performance.now()
    mediaRecorder.start()
    isRecording.value = true
    recordingSec.value = 0
    if (timer) { clearInterval(timer as any) }
    timer = window.setInterval(() => {
      recordingSec.value = Math.floor((performance.now() - recStartedAt) / 1000)
    }, 200)
  } catch (err:any) {
    if (err?.name === 'NotAllowedError') toast('Microphone permission denied. Please enable it in system settings.')
    else if (err?.name === 'NotReadableError') toast('Microphone is busy. Try again later.')
    else if (location.protocol !== 'https:' && location.hostname !== 'localhost') toast('Recording requires HTTPS or localhost.')
    else toast('Unable to access microphone.')
  }
}
function holdToRecordStop(){
  if (!isRecording.value || stopping) return
  stopping = true
  isRecording.value = false
  if (timer){ clearInterval(timer); timer = null }
  try{ mediaRecorder?.stop() }catch{ stopping = false }
}

/** ======= misc ======= */
function toggleEmoji(){ showEmoji.value = !showEmoji.value }
function toggleSettings(){ showSettings.value = !showSettings.value }
function toggleSound(){ soundOn.value = !soundOn.value }
function toggleCompact(){ compact.value = !compact.value }
function clearChat(){ messages.splice(0, messages.length) }

function tsNow(){ return Date.now() }
function nid(){ return Math.random().toString(36).slice(2) + tsNow().toString(36) }

/** Removed send sound: keep only receive sound */
function playSend(){ /* no-op: send click sound removed as requested */ }
function playRecv(){
  if(!soundOn.value) return
  try{ recvAudio = recvAudio || new Audio(msgSoundUrl); recvAudio.currentTime = 0; void recvAudio.play() }catch{}
}
function toast(msg:string){ alert(msg) }

let offListen: (()=>void) | null = null

onMounted(async () => {
  try {
    // 监听其它 tab 更新 token（比如用户打开了带 vt 的链接）
    offListen = listenVTokenChange(async (vt) => {
      // 收到新 token 后，重新 init 即可
      await initConversation()
      messages.splice(0, messages.length)
      lastId = 0; revokedCursor = 0
      await fetchNew()
      await smoothToBottom()
    })

    await initConversation()
    await fetchNew()
    await smoothToBottom()
    pollTimer = window.setInterval(fetchNew, 1200)
  } catch (e:any) {
    alert(e?.message || 'Failed to initialize conversation.')
  }
})
onBeforeUnmount(()=>{ if (pollTimer){ clearInterval(pollTimer); pollTimer = null }; try{ recvAudio?.pause?.() }catch{}; offListen?.() })

/** ======= inline icon ======= */
/** ======= inline icon (iOS 旧版兼容) ======= */
const SvgIcon = (props:{name:string}) => {
  const paths:Record<string,string> = {
    'dots': 'M5 12a2 2 0 1 0 0-4 2 2 0 0 0 0 4zm7 0a2 2 0 1 0 0-4 2 2 0 0 0 0 4zm7 0a2 2 0 1 0 0-4 2 2 0 0 0 0 4z',
    'bell': 'M18 8a6 6 0 10-12 0c0 7-3 8-3 8h18s-3-1-3-8m-7 13a2 2 0 004 0',
    'bell-off': 'M2 2l20 20M18 8a6 6 0 10-12 0c0 7-3 8-3 8h12M13 21a2 2 0 01-2 0',
    'rows': 'M4 6h16M4 12h16M4 18h16',
    'trash': 'M3 6h18M8 6V4h8v2m-1 0v14H9V6h6z',
    'spark': 'M12 2l2 6h6l-4.5 3.5L17 18l-5-3-5 3 1.5-6.5L4 8h6z',
    'emoji': 'M12 2a10 10 0 100 20 10 10 0 000-20zm-4 9a1.5 1.5 0 110-3 1.5 1.5 0 010 3zm8 0a1.5 1.5 0 110-3 1.5 1.5 0 010 3zM7 15s2 2 5 2 5-2 5-2',
    'location': 'M12 2C8 2 6 5 6 8c0 5 6 14 6 14s6-9 6-14c0-3-2-6-6-6zm0 8a2 2 0 110-4 2 2 0 010 4z',
    'arrow-right': 'M9 6l6 6-6 6',
    'mic': 'M12 14a3 3 0 003-3V6a3 3 0 10-6 0v5a3 3 0 003 3zm7-3a7 7 0 01-14 0m7 7v3',
    'paperclip': 'M21.44 11.05L12 20.49a6 6 0 01-8.49-8.49l9.19-9.2a4 4 0 015.66 5.66L9.76 17.47a2 2 0 01-2.83-2.83l8.48-8.49',
    'send': 'M22 2L11 13M22 2l-7 20-4-9-9-4 20-7',
  }
  const path = paths[props.name] || ''
  return h(
    'svg',
    {
      xmlns: 'http://www.w3.org/2000/svg',    // ← 旧 iOS 对 inline SVG 更稳
      viewBox: '0 0 24 24',
      width: 20,                               // ← 明确尺寸
      height: 20,
      fill: 'none',
      stroke: 'currentColor',                  // ← 用当前文字色绘制
      'stroke-width': 2,
      'stroke-linecap': 'round',
      'stroke-linejoin': 'round',
      class: 'icon',
      'aria-hidden': 'true',
      focusable: 'false'
    },
    [h('path', { d: path })]
  )
}
</script>

<style scoped src="./ChatClientCss.css"></style>
<!-- Minimal override to ensure composer text uses the same normal font as the rest -->
<style scoped>
.chat-input textarea {
  font-family: inherit;
  font-weight: 400;
}
.card.file .file-row { display:flex; gap:8px; align-items:center; }
.card.file .file-meta .name { font-weight:600; }
.card.file .file-meta .size { font-size:12px; opacity:.7; }
/* 确保文本消息保留换行/多空格，并且长链接可断行 */
.card.text {
  white-space: pre-wrap;   /* 关键：\n -> 视觉上的换行 */
  word-break: break-word;  /* 长链接/长词可断行 */
}

</style>
