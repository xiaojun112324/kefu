<!-- src/components/Toast.vue -->
<template>
  <transition name="toast-pop">
    <div
      v-if="show"
      class="fixed top-6 left-1/2 -translate-x-1/2 z-[9999] w-full flex justify-center pointer-events-none"
      aria-live="polite"
      role="status"
    >
      <div class="pointer-events-auto relative">
        <!-- 柔光描边 -->
        <div
          class="absolute -inset-[1.5px] rounded-2xl opacity-0 toast-glow"
          aria-hidden="true"
        ></div>

        <!-- 主卡片 -->
        <div
          class="relative rounded-2xl bg-white/10 border border-white/15 backdrop-blur-md 
                 shadow-[0_12px_50px_rgba(0,0,0,.35)]
                 px-6 py-4 pb-7 md:px-7 md:py-5 md:pb-8 w-[min(92vw,700px)] text-white"
        >
          <!-- 右上角关闭 -->
          <button
            class="absolute top-2.5 right-2.5 p-1.5 rounded-lg hover:bg-white/15 active:scale-95 transition"
            @click="$emit('close')"
            aria-label="关闭"
          >
            <svg class="w-5 h-5 md:w-6 md:h-6 opacity-90" viewBox="0 0 24 24" fill="currentColor">
              <path d="M18.3 5.7 12 12l6.3 6.3-1.4 1.4L10.6 13.4 4.3 19.7 2.9 18.3 9.2 12 2.9 5.7 4.3 4.3l6.3 6.3 6.3-6.3z"/>
            </svg>
          </button>

          <!-- 内容 -->
          <div class="flex items-start gap-3 pr-8">
            <div class="mt-0.5 w-1.5 h-7 rounded-full bg-gradient-to-b from-brand-400/90 to-brand-500/90"></div>

            <div class="min-w-0">
              <div class="text-base md:text-lg font-semibold leading-snug truncate">
                {{ title || '提示' }}
              </div>
              <div class="mt-1 text-sm md:text-[15px] text-white/90 whitespace-pre-wrap break-words">
                {{ message || '' }}
              </div>
            </div>
          </div>

          <!-- 底部进度条 -->
          <div v-if="showBar" class="absolute left-0 right-0 bottom-0 h-[3px] overflow-hidden rounded-b-2xl">
            <div
              class="h-full toast-bar"
              :style="{ animationDuration: duration + 'ms' }"
            ></div>
          </div>
        </div>
      </div>
    </div>
  </transition>
</template>

<script setup>
import { watch, onUnmounted } from 'vue'

const props = defineProps({
  show: { type: Boolean, default: false },
  title: { type: String, default: '' },
  message: { type: String, default: '' },
  showBar: { type: Boolean, default: true },
  /** 自动关闭时长 (ms)，默认 3000 */
  duration: { type: Number, default: 3000 }
})
const emit = defineEmits(['close'])

let timer = null

watch(() => props.show, (val) => {
  if (val && props.duration > 0) {
    clearTimeout(timer)
    timer = setTimeout(() => {
      emit('close')
    }, props.duration)
  }
})

onUnmounted(() => {
  clearTimeout(timer)
})
</script>

<style scoped>
.toast-pop-enter-active,
.toast-pop-leave-active {
  transition: opacity .22s cubic-bezier(.2,.7,.2,1),
              transform .22s cubic-bezier(.2,.7,.2,1);
}
.toast-pop-enter-from,
.toast-pop-leave-to {
  opacity: 0;
  transform: translate(-50%, -8px) scale(.98);
}

/* 柔光描边 */
.toast-glow {
  border-radius: 1rem;
  background:
    radial-gradient(1200px 1200px at 50% -10%,
      rgba(120,180,255,.14), transparent 60%),
    linear-gradient(180deg, rgba(255,255,255,.18), rgba(255,255,255,.06));
  filter: blur(8px);
  animation: glow-in .28s ease forwards;
}
@keyframes glow-in {
  from { opacity: 0; }
  to   { opacity: .9; }
}

/* 进度条：随 duration 动画 */
.toast-bar {
  width: 100%;
  background: linear-gradient(90deg,
    rgba(255,255,255,.35),
    rgba(255,255,255,.85),
    rgba(255,255,255,.35)
  );
  transform-origin: left center;
  animation-name: toast-timer, shimmer;
  animation-timing-function: linear, linear;
  animation-iteration-count: 1, infinite;
}
@keyframes toast-timer {
  from { transform: scaleX(1); }
  to   { transform: scaleX(0); }
}
@keyframes shimmer {
  0%   { filter: brightness(1); }
  50%  { filter: brightness(1.25); }
  100% { filter: brightness(1); }
}
</style>
