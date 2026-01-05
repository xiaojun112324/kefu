<template>
  <div class="min-h-screen app-bg">
    <router-view v-slot="{ Component }">
      <transition name="route" mode="out-in">
        <component :is="Component" />
      </transition>
    </router-view>
  </div>
</template>
<script setup></script>
