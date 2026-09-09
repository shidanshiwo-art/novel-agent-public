<template>
  <header class="chapter-toolbar">
    <ChapterNavigation
      :previous-disabled="previousDisabled"
      :next-disabled="nextDisabled"
      @previous="emit('previous')"
      @next="emit('next')"
    />
    <ChapterTitle
      class="chapter-toolbar-title"
      :chapter-number="chapterNumber"
      :title="title"
      :model-value="modelValue"
      :editable="editableTitle"
      @update:model-value="emit('update:modelValue', $event)"
    />
    <div class="toolbar-meta">
      <slot />
    </div>
  </header>
</template>

<script setup lang="ts">
import ChapterNavigation from './ChapterNavigation.vue'
import ChapterTitle from './ChapterTitle.vue'

withDefaults(defineProps<{
  chapterNumber: number | null
  title?: string
  modelValue?: string
  editableTitle?: boolean
  previousDisabled?: boolean
  nextDisabled?: boolean
}>(), {
  title: '',
  modelValue: undefined,
  editableTitle: false,
  previousDisabled: false,
  nextDisabled: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  previous: []
  next: []
}>()
</script>

<style scoped>
.chapter-toolbar { position: sticky; top: 0; z-index: 2; display: flex; align-items: center; gap: clamp(6px, 1vw, 12px); height: var(--toolbar-height); flex-shrink: 0; min-width: 0; padding: 0 clamp(12px, 2vw, 24px); overflow: hidden; border-bottom: 1px solid var(--border); background: var(--surface-overlay); white-space: nowrap; }
.chapter-toolbar :deep(.chapter-toolbar-title) { min-width: 0; flex: 1 1 auto; }
.chapter-navigation { flex: 0 0 auto; }
.toolbar-meta { display: flex; flex: 0 0 auto; min-width: 0; align-items: center; justify-content: flex-end; gap: clamp(4px, .8vw, 8px); white-space: nowrap; }
.toolbar-meta :deep(.status-pill) { display: inline-flex; align-items: center; flex: 0 0 auto; padding: 3px 8px; border: 1px solid var(--success-border); border-radius: var(--radius-md); background: var(--success-surface); color: var(--success-strong); font-size: 11px; font-weight: 600; line-height: 1.4; white-space: nowrap; }
.toolbar-meta :deep(.el-button) { flex: 0 0 auto; white-space: nowrap; }
@media (max-width: 720px) {
  .chapter-toolbar { gap: 6px; padding-inline: 12px; }
  .toolbar-meta { gap: 4px; }
}
</style>
