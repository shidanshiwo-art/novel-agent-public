<template>
  <div class="chapter-navigation" aria-label="章节导航">
    <el-button
      text
      circle
      :icon="ArrowLeft"
      :disabled="previousDisabled"
      aria-label="上一章"
      @click="emit('previous')"
    />
    <el-button
      text
      circle
      :icon="ArrowRight"
      :disabled="nextDisabled"
      aria-label="下一章"
      @click="emit('next')"
    />
  </div>
</template>

<script setup lang="ts">
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'

withDefaults(defineProps<{
  previousDisabled?: boolean
  nextDisabled?: boolean
}>(), {
  previousDisabled: false,
  nextDisabled: false,
})

const emit = defineEmits<{
  previous: []
  next: []
}>()
</script>

<style scoped>
.chapter-navigation { display: flex; gap: clamp(2px, .6vw, 6px); }
.chapter-navigation .el-button { margin: 0; color: var(--text-muted); }
.chapter-navigation .el-button:hover:not(:disabled) { color: var(--primary); }
</style>
