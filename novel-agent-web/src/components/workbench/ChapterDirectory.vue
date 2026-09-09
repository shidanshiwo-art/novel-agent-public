<template>
  <aside class="chapter-directory" aria-label="章节目录">
    <div class="directory-head">
      <h2>章节目录</h2>
      <span class="chapter-count">{{ chapterCount }} 章</span>
    </div>

    <div v-if="showSearch" class="directory-search">
      <el-input
        :model-value="keyword"
        clearable
        :prefix-icon="Search"
        placeholder="搜索章节"
        aria-label="搜索章节"
        @update:model-value="emit('update:keyword', $event)"
      />
    </div>

    <div class="directory-tabs">
      <span class="active">{{ primaryTabLabel }}</span>
      <span>{{ secondaryTabLabel }}</span>
    </div>

    <div class="chapter-list">
      <el-skeleton v-if="loading" :rows="6" animated />
      <div v-else-if="volumes.length" class="directory-tree" aria-label="章节层级目录">
        <el-button
          v-if="showBook"
          text
          class="directory-node level-book"
          :aria-expanded="bookExpanded"
          @click="emit('toggle-book')"
        >
          <span class="node-toggle" aria-hidden="true">{{ bookExpanded ? '▾' : '▸' }}</span>
          <span class="node-prefix">故事总纲 ·</span>
          <strong class="node-title">{{ bookTitle || '未设置书名' }}</strong>
        </el-button>

        <div v-if="!showBook || bookExpanded" class="directory-children">
          <div v-for="volume in volumes" :key="volume.key" class="volume-group">
            <el-button
              v-if="showVolumeToggles && !flattenSingleVolume"
              text
              class="directory-node volume-row"
              :aria-expanded="isVolumeExpanded(volume.key)"
              @click="emit('toggle-volume', volume.key)"
            >
              <span class="node-toggle" aria-hidden="true">{{ isVolumeExpanded(volume.key) ? '▾' : '▸' }}</span>
              <span class="node-prefix">{{ formatVolumeLabel(volume.sequenceNo) }} ·</span>
              <strong class="node-title">{{ volume.title || formatVolumeLabel(volume.sequenceNo) }}</strong>
              <small class="volume-count" aria-label="章节数量">{{ volume.chapters.length }} 章</small>
            </el-button>
            <div v-else class="directory-node volume-row">
              <span class="node-prefix">{{ formatVolumeLabel(volume.sequenceNo) }} ·</span>
              <strong class="node-title">{{ volume.title || formatVolumeLabel(volume.sequenceNo) }}</strong>
              <small class="volume-count" aria-label="章节数量">{{ volume.chapters.length }} 章</small>
            </div>

            <div
              v-if="flattenSingleVolume || !showVolumeToggles || isVolumeExpanded(volume.key)"
              class="directory-children"
              :class="{ 'is-flat': flattenSingleVolume }"
            >
              <el-button
                v-for="chapter in volume.chapters"
                :key="chapter.key"
                text
                class="directory-node chapter-row"
                :class="{ 'chapter-row--active': chapter.chapterNumber === selectedChapterNumber }"
                :disabled="navigationBusy"
                @click="chapter.chapterNumber !== null && emit('select-chapter', chapter.chapterNumber)"
                @contextmenu.prevent.stop="chapter.chapterNumber !== null && emit('chapter-contextmenu', $event, chapter.chapterNumber)"
              >
                <div class="chapter-node-copy">
                  <strong>{{ chapterLabel(chapter) }}</strong>
                  <small v-if="chapter.metaLabel" class="chapter-row-status">{{ chapter.metaLabel }}</small>
                </div>
              </el-button>
            </div>
          </div>
        </div>
      </div>
      <div v-else class="directory-empty">
        <span class="empty-book">卷</span>
        <strong>{{ emptyTitle }}</strong>
        <p>{{ emptyDescription }}</p>
      </div>
    </div>

    <footer class="directory-foot">
      <span>{{ footerLabel }}</span>
      <slot name="footer-action" />
    </footer>
  </aside>
</template>

<script setup lang="ts">
import { Search } from '@element-plus/icons-vue'

export type ChapterDirectoryChapter = {
  key: string
  chapterNumber: number | null
  title: string
  metaLabel?: string
}

export type ChapterDirectoryVolume = {
  key: string
  sequenceNo: number
  title: string
  chapters: ChapterDirectoryChapter[]
}

const props = withDefaults(defineProps<{
  volumes: ChapterDirectoryVolume[]
  chapterCount: number
  secondaryTabLabel: string
  footerLabel: string
  primaryTabLabel?: string
  bookTitle?: string
  showBook?: boolean
  bookExpanded?: boolean
  showSearch?: boolean
  showVolumeToggles?: boolean
  flattenSingleVolume?: boolean
  expandedVolumeKeys?: Set<string>
  selectedChapterNumber?: number | null
  keyword?: string
  loading?: boolean
  navigationBusy?: boolean
  emptyTitle?: string
  emptyDescription?: string
}>(), {
  primaryTabLabel: '全部章节',
  bookTitle: '',
  showBook: false,
  bookExpanded: true,
  showSearch: false,
  showVolumeToggles: false,
  flattenSingleVolume: false,
  expandedVolumeKeys: () => new Set<string>(),
  selectedChapterNumber: null,
  keyword: '',
  loading: false,
  navigationBusy: false,
  emptyTitle: '还没有章节',
  emptyDescription: '完成章节准备后，内容会显示在这里。',
})

const emit = defineEmits<{
  'update:keyword': [value: string]
  'toggle-book': []
  'toggle-volume': [key: string]
  'select-chapter': [chapterNumber: number]
  'chapter-contextmenu': [event: MouseEvent, chapterNumber: number]
}>()

function isVolumeExpanded(key: string) {
  return props.expandedVolumeKeys.has(key)
}

function chapterLabel(chapter: ChapterDirectoryChapter) {
  const number = chapter.chapterNumber ?? '—'
  return chapter.title ? `第 ${number} 章 ${chapter.title}` : `第 ${number} 章`
}

function formatVolumeLabel(sequenceNo: number) {
  const digits = '零一二三四五六七八九'
  if (!Number.isInteger(sequenceNo) || sequenceNo < 1 || sequenceNo > 99) return `卷${sequenceNo}`
  if (sequenceNo < 10) return `卷${digits[sequenceNo]}`
  if (sequenceNo < 20) return `卷十${sequenceNo === 10 ? '' : digits[sequenceNo - 10]}`
  const tens = Math.floor(sequenceNo / 10)
  const ones = sequenceNo % 10
  return `卷${digits[tens]}十${ones ? digits[ones] : ''}`
}
</script>

<style scoped>
.chapter-directory { min-width: 0; min-height: 0; display: flex; flex-direction: column; overflow: hidden; background: var(--surface); border-right: 1px solid var(--border); }
.directory-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 20px 18px 16px; }
.directory-head h2 { min-width: 0; margin: 0; overflow: hidden; color: var(--text); font-size: 16px; line-height: 1.35; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.chapter-count { flex-shrink: 0; color: var(--text-faint); font-size: 11px; }
.directory-search { margin: 0 14px 14px; }
.directory-search :deep(.el-input__wrapper) { min-height: 38px; padding-inline: 11px; border-radius: 8px; background: var(--surface-muted); box-shadow: 0 0 0 1px var(--border) inset !important; }
.directory-search :deep(.el-input__wrapper.is-focus) { background: var(--surface); box-shadow: 0 0 0 1px var(--primary) inset, 0 0 0 3px var(--primary-dim) !important; }
.directory-search :deep(.el-input__prefix) { color: var(--text-faint); }
.directory-tabs { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 0 18px 10px; border-bottom: 1px solid var(--border); color: var(--text-faint); font-size: 11px; }
.directory-tabs .active { position: relative; color: var(--text); font-weight: 700; }
.directory-tabs .active::after { position: absolute; right: 0; bottom: -11px; left: 0; height: 2px; background: var(--primary); content: ''; }
.chapter-list { flex: 1; min-width: 0; min-height: 0; overflow-y: auto; padding: 10px 8px 12px; scrollbar-gutter: stable; }
.directory-tree, .directory-children, .volume-group { min-width: 0; }
.directory-node { width: 100%; box-sizing: border-box; display: flex; align-items: baseline; justify-content: flex-start; gap: 6px; margin: 0; padding: 8px 10px; border: 0; border-radius: 7px; color: inherit; text-align: left; font-size: 13px; line-height: 1.45; }
.volume-row { padding-left: 8px; color: var(--text-muted); }
.volume-row:hover, .chapter-row:hover { background: var(--surface-muted); }
.level-book { padding-left: 12px; }
.node-toggle { flex-shrink: 0; width: 12px; color: var(--text-faint); }
.node-prefix { flex-shrink: 0; color: var(--text-faint); font-size: 11px; white-space: nowrap; }
.node-title { min-width: 0; overflow: hidden; color: var(--text); text-overflow: ellipsis; white-space: nowrap; font-size: 13px; font-weight: 600; line-height: 1.45; }
.volume-count { flex-shrink: 0; margin-left: auto; color: var(--text-faint); font-size: 10px; white-space: nowrap; }
.directory-tree > .directory-children { padding-left: 12px; }
.volume-group > .directory-children,
.directory-children.is-flat { padding-left: 16px; }
.chapter-row { padding-left: 8px; }
.chapter-row--active { background: var(--workbench-selection-bg); color: var(--workbench-selection-text); box-shadow: inset 3px 0 var(--primary); }
.chapter-node-copy { min-width: 0; display: flex; flex: 1; align-items: baseline; gap: 8px; }
.chapter-node-copy strong { min-width: 0; flex: 1; overflow: hidden; color: var(--text); font-size: 13px; font-weight: 500; line-height: 1.4; text-overflow: ellipsis; white-space: nowrap; }
.chapter-row--active .chapter-node-copy strong { color: var(--primary); }
.chapter-row-status { flex: 0 0 auto; margin-left: auto; color: var(--text-faint); font-size: 10px; line-height: 1.4; white-space: nowrap; }
.directory-empty { display: flex; min-height: 180px; flex-direction: column; align-items: center; justify-content: center; padding: 20px 12px; color: var(--text-faint); text-align: center; }
.directory-empty strong { color: var(--text-muted); font-size: 12px; }
.directory-empty p { margin: 5px 0 0; font-size: 11px; line-height: 1.5; }
.empty-book { display: grid; width: 38px; height: 38px; place-items: center; margin-bottom: 10px; border-radius: 50%; background: var(--primary-dim); color: var(--primary); font: 700 16px serif; }
.directory-foot { display: flex; height: 42px; flex-shrink: 0; align-items: center; justify-content: space-between; gap: 8px; padding: 0 14px 0 18px; border-top: 1px solid var(--border); color: var(--text-faint); font-size: 10px; }
@media (max-width: 720px) {
  .chapter-directory { flex: 0 0 310px; height: auto; border-right: 0; border-bottom: 1px solid var(--border); }
}
</style>
