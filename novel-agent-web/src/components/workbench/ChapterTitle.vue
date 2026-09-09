<template>
  <h1 class="chapter-title">
    <span v-if="chapterNumber !== null" class="chapter-title-number">第 {{ chapterNumber }} 章</span>
    <el-input
      v-if="editable"
      class="chapter-title-input"
      :model-value="modelValue ?? title"
      aria-label="章节标题"
      @update:model-value="emit('update:modelValue', $event)"
    />
    <span v-else class="chapter-title-name">{{ title || '未命名章节' }}</span>
  </h1>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  chapterNumber: number | null
  title?: string
  modelValue?: string
  editable?: boolean
}>(), {
  title: '',
  modelValue: undefined,
  editable: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()
</script>

<style scoped>
.chapter-title { min-width: 0; display: flex; align-items: baseline; gap: 8px; overflow: hidden; margin: 0; color: var(--text); font-size: 20px; font-weight: 600; line-height: 1.3; white-space: nowrap; }
.chapter-title-number,
.chapter-title-name,
.chapter-title-input { font-size: inherit; font-weight: inherit; }
.chapter-title-name { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.chapter-title-input { min-width: 0; flex: 1; }
.chapter-title-input :deep(.el-input__wrapper),
.chapter-title-input :deep(.el-input__wrapper.is-focus) { padding: 0; border: 0 !important; border-radius: 0; background: transparent !important; box-shadow: none !important; }
.chapter-title-input :deep(.el-input__inner) { height: auto; padding: 0; color: inherit; font: inherit; line-height: inherit; }
</style>
