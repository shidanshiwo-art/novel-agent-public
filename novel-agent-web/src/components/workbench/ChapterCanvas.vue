<template>
  <div ref="canvas" class="chapter-canvas" @scroll="emit('scroll', canvas)">
    <article class="chapter-paper">
      <el-input
        v-if="mode === 'edit'"
        type="textarea"
        class="chapter-edit-area"
        :model-value="modelValue"
        :autosize="{ minRows: 3 }"
        resize="none"
        :aria-label="editAriaLabel"
        @update:model-value="emit('update:modelValue', $event)"
      />
      <section v-else class="live-content-panel" aria-label="实时正文">
        <div v-if="content" class="content-preview">{{ content }}</div>
        <div v-else class="content-empty" :class="{ 'live-content-empty': emptyBusy }" role="status">
          <span class="empty-icon">文</span>
          <strong>{{ emptyBusy ? busyTitle : emptyTitle }}</strong>
          <p>{{ emptyBusy ? busyDescription : emptyDescription }}</p>
        </div>
        <slot name="actions" />
      </section>
    </article>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

withDefaults(defineProps<{
  mode?: 'edit' | 'preview'
  modelValue?: string
  content?: string
  editAriaLabel?: string
  emptyTitle?: string
  emptyDescription?: string
  emptyBusy?: boolean
  busyTitle?: string
  busyDescription?: string
}>(), {
  mode: 'preview',
  modelValue: '',
  content: '',
  editAriaLabel: '章节正文',
  emptyTitle: '还没有本次生成内容',
  emptyDescription: '选择“生成本章”开始创作',
  emptyBusy: false,
  busyTitle: '正在等待正文流',
  busyDescription: '正文生成后会显示在这里。',
})

const canvas = ref<HTMLElement | null>(null)
const emit = defineEmits<{
  'update:modelValue': [value: string]
  scroll: [element: HTMLElement | null]
}>()

function getElement() {
  return canvas.value
}

defineExpose({ getElement })
</script>

<style scoped>
.chapter-canvas { flex: 1; min-width: 0; min-height: 0; overflow-y: auto; overflow-x: hidden; padding: 24px clamp(24px, 5vw, 72px) 56px; background: var(--surface); scrollbar-gutter: stable; }
.chapter-paper { width: min(var(--workbench-content-width), 100%); min-height: 100%; display: flex; flex-direction: column; margin: 0 auto; padding: 0 0 48px; }
.chapter-edit-area { width: 100%; flex: 1; }
.chapter-edit-area :deep(.el-textarea__inner),
.chapter-edit-area :deep(.el-textarea__inner:hover),
.chapter-edit-area :deep(.el-textarea__inner:focus) { min-height: 0 !important; resize: none !important; border: 0 !important; border-radius: 0 !important; padding: 0; outline: none; box-sizing: border-box; color: var(--text-secondary); background: transparent !important; box-shadow: none !important; caret-color: var(--primary); font-family: "Microsoft YaHei", "PingFang SC", "Noto Sans CJK SC", "Noto Sans SC", sans-serif; font-size: 16px; line-height: 1.9; letter-spacing: normal; }
.live-content-panel { min-width: 0; padding: 0 0 20px; }
.content-preview { margin-top: 16px; padding: 18px 20px; border-left: 3px solid var(--primary-focus); background: var(--surface-subtle); color: var(--text-secondary); white-space: pre-wrap; font-family: "Microsoft YaHei", "PingFang SC", "Noto Sans CJK SC", "Noto Sans SC", sans-serif; font-size: 16px; line-height: 1.9; letter-spacing: normal; }
.content-empty { min-height: 260px; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 24px 0; color: var(--text-faint); text-align: center; }
.content-empty strong { color: var(--text-muted); font-size: 13px; }
.content-empty p { margin: 4px 0 0; font-size: 11px; }
.live-content-empty { min-height: 220px; }
.empty-icon { width: 48px; height: 48px; display: grid; place-items: center; margin-bottom: 12px; border-radius: 50%; background: var(--primary-dim); color: var(--primary); font: 700 18px serif; }
@media (max-width: 960px) { .chapter-canvas { padding-inline: 4vw; } }
@media (max-width: 720px) { .chapter-canvas { padding: 22px 16px 40px; }.chapter-paper { padding-bottom: 44px; }.chapter-edit-area :deep(.el-textarea__inner) { font-size: 16px; line-height: 1.9; } }
</style>
