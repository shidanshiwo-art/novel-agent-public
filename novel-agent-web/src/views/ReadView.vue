<template>
  <div class="read-workbench workbench-shell">
    <ChapterDirectory
      v-model:keyword="keyword"
      :volumes="chapterDirectoryVolumes"
      :chapter-count="chapters.length"
      :secondary-tab-label="`已生成 ${chapters.length}`"
      :footer-label="`共 ${totalWords.toLocaleString()} 字`"
      :book-title="bookTitle"
      :show-book="true"
      :book-expanded="bookExpanded"
      :show-search="true"
      :show-volume-toggles="true"
      :flatten-single-volume="showSingleDefaultVolumeFlat"
      :expanded-volume-keys="expandedVolumeCodes"
      :selected-chapter-number="selectedNumber"
      :loading="loading"
      :empty-title="chapters.length === 0 ? '还没有生成章节' : `没有匹配“${keyword}”的章节`"
      :empty-description="chapters.length === 0 ? '完成章节生成后，全部内容会显示在这里。' : '换一个关键词试试。'"
      @toggle-book="toggleBook"
      @toggle-volume="toggleVolume"
      @select-chapter="selectChapter"
      @chapter-contextmenu="openChapterMenu"
    >
      <template #footer-action>
        <el-button
          text
          :loading="refreshing"
          :disabled="loading || refreshing || !projectStore.active"
          @click="refreshChapterList"
        >刷新目录</el-button>
      </template>
    </ChapterDirectory>
    <div v-if="chapterMenu" class="chapter-menu" :style="{ left: `${chapterMenu.x}px`, top: `${chapterMenu.y}px` }" @click.stop>
      <el-popconfirm
        :title="`确定删除第 ${chapterMenu.chapter.chapterNumber} 章吗？\n删除后无法恢复。`"
        confirm-button-text="删除"
        cancel-button-text="取消"
        confirm-button-type="danger"
        width="240"
        @confirm="removeChapter"
      >
        <template #reference>
          <el-button text type="danger" class="local-delete-button">删除本章</el-button>
        </template>
      </el-popconfirm>
    </div>

    <section class="reading-stage">
      <ChapterToolbar
        :chapter-number="selectedChapter?.chapterNumber ?? null"
        :title="selectedChapter?.title ?? ''"
        v-model="editTitle"
        :editable-title="Boolean(selectedChapter)"
        :previous-disabled="!previousChapter"
        :next-disabled="!nextChapter"
        @previous="goAdjacent(-1)"
        @next="goAdjacent(1)"
      >
        <template v-if="selectedChapter">
          <span>{{ selectedChapter.wordCount?.toLocaleString() || 0 }} 字</span>
          <span class="status-pill">{{ statusLabel(selectedChapter.status) }}</span>
          <div v-if="selectedChapter.status === 'DIRTY'" class="derived-sync-control">
            <el-button
              type="warning"
              plain
              :loading="resyncing"
              :disabled="saving"
              @click="resyncSelectedChapter"
            >
              更新章节记忆
            </el-button>
            <small>正文已修改，更新章节记忆后后续章节才能使用最新内容。</small>
          </div>
          <el-button type="primary" :disabled="saving" @click="saveEdit">{{ saving ? '保存中…' : '保存' }}</el-button>
        </template>
      </ChapterToolbar>

      <ChapterCanvas v-if="selectedChapter" v-model="editContent" mode="edit" />
      <div v-else class="stage-empty">
        <template v-if="!projectStore.active">
          <div class="stage-empty-icon">稿</div>
          <h2>先加载一个小说项目</h2>
          <p>加载项目后，这里会显示完整章节目录和正文。</p>
          <router-link to="/project">前往项目管理</router-link>
        </template>
        <template v-else-if="!loading">
          <div class="stage-empty-icon">卷</div>
          <h2>章节尚未生成</h2>
          <p>生成第一章后，就可以在这里连续阅读整部作品。</p>
          <router-link to="/generate">前往生成章节</router-link>
        </template>
      </div>
    </section>

  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { listChapters, listChaptersByVolume, overwriteChapterContent, resyncChapterDerivedData, deleteChapter } from '../api/project'
import { listOutlineNodes } from '../api/planning'
import { ApiBusinessError } from '../api/http'
import { useProjectStore } from '../stores/project'
import type { GeneratedChapterResponse, OutlineNode, VolumeChapterGroupResponse } from '../types'
import { statusLabel } from '../utils/uiSemantics'
import { ElMessage, ElMessageBox } from 'element-plus'
import ChapterCanvas from '../components/workbench/ChapterCanvas.vue'
import ChapterDirectory, { type ChapterDirectoryVolume } from '../components/workbench/ChapterDirectory.vue'
import ChapterToolbar from '../components/workbench/ChapterToolbar.vue'

const route = useRoute()
const requested = Number(route.query.chapter)
const projectStore = useProjectStore()
const chapters = ref<GeneratedChapterResponse[]>([])
const selectedNumber = ref<number | null>(null)
const keyword = ref('')
const loading = ref(false)
const refreshing = ref(false)
const editTitle = ref('')
const editContent = ref('')
const saving = ref(false)
const resyncing = ref(false)
const derivedSyncFailed = ref(false)
const volumeGroups = ref<VolumeChapterGroupResponse[]>([])
const outlineNodes = ref<OutlineNode[]>([])
const bookExpanded = ref(true)
const expandedVolumeCodes = ref(new Set<string>())
let directoryExpansionProjectCode: string | null = null
let chapterInitialized = false
const chapterMenu = ref<{ x: number; y: number; chapter: GeneratedChapterResponse } | null>(null)

const filteredChapters = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  if (!query) return chapters.value
  return chapters.value.filter((chapter) =>
    String(chapter.chapterNumber).includes(query)
    || (chapter.title || '').toLowerCase().includes(query)
  )
})
const filteredVolumeGroups = computed(() => {
  const visibleChapterNumbers = new Set(filteredChapters.value.map((chapter) => chapter.chapterNumber))
  return volumeGroups.value
    .map((group) => ({
      ...group,
      chapters: group.chapters.filter((chapter) => visibleChapterNumbers.has(chapter.chapterNumber)),
    }))
    .filter((group) => group.chapters.length || !keyword.value.trim())
})
const showSingleDefaultVolumeFlat = computed(() => {
  if (volumeGroups.value.length !== 1) return false
  const group = volumeGroups.value[0]
  const volume = outlineNodes.value.find((node) =>
    node.nodeKind === 'VOLUME' && node.nodeCode === group.volumeCode,
  )
  return volume?.sequenceNo === 1
    && ['卷一', '第一卷'].includes(volume.title?.trim() ?? '')
    && !volume.summary?.trim()
})
const bookTitle = computed(() => outlineNodes.value.find((node) => node.nodeKind === 'BOOK')?.title?.trim() || '')
const selectedChapter = computed(() => chapters.value.find((chapter) => chapter.chapterNumber === selectedNumber.value) ?? null)
const chapterDirectoryVolumes = computed<ChapterDirectoryVolume[]>(() => filteredVolumeGroups.value.map((group, index) => ({
  key: group.volumeCode,
  sequenceNo: group.sequenceNo ?? index + 1,
  title: group.title,
  chapters: group.chapters.map((chapter) => ({
    key: String(chapter.chapterNumber),
    chapterNumber: chapter.chapterNumber,
    title: chapter.title,
    metaLabel: statusLabel(chapter.status),
  })),
})))
const selectedIndex = computed(() => chapters.value.findIndex((chapter) => chapter.chapterNumber === selectedNumber.value))
const previousChapter = computed(() => selectedIndex.value > 0 ? chapters.value[selectedIndex.value - 1] : null)
const nextChapter = computed(() => selectedIndex.value >= 0 && selectedIndex.value < chapters.value.length - 1 ? chapters.value[selectedIndex.value + 1] : null)
const hasLocalEdits = computed(() => {
  const chapter = selectedChapter.value
  return !!chapter && (
    editTitle.value !== chapter.title
    || editContent.value !== chapter.content
  )
})
const totalWords = computed(() => chapters.value.reduce((total, chapter) => total + (chapter.wordCount || 0), 0))

async function refresh(initialLoad = false) {
  derivedSyncFailed.value = false
  if (!projectStore.active) {
    chapters.value = []
    volumeGroups.value = []
    outlineNodes.value = []
    selectedNumber.value = null
    return
  }
  if (initialLoad) loading.value = true
  else refreshing.value = true
  try {
    const [nextChapters, nextVolumeGroups, nextOutlineNodes] = await Promise.all([
      listChapters(projectStore.active.projectCode),
      listChaptersByVolume(projectStore.active.projectCode),
      listOutlineNodes(projectStore.active.projectCode),
    ])
    chapters.value = nextChapters
    volumeGroups.value = nextVolumeGroups
    outlineNodes.value = nextOutlineNodes
    const target = !chapterInitialized
      ? chapters.value.find((chapter) => chapter.chapterNumber === requested) ?? chapters.value[0] ?? null
      : chapters.value.find((chapter) => chapter.chapterNumber === selectedNumber.value) ?? chapters.value[0] ?? null
    const isNewProject = directoryExpansionProjectCode !== projectStore.active.projectCode
    if (isNewProject) {
      bookExpanded.value = true
      const selectedVolume = nextVolumeGroups.find((group) =>
        group.chapters.some((chapter) => chapter.chapterNumber === target?.chapterNumber),
      )
      expandedVolumeCodes.value = selectedVolume ? new Set([selectedVolume.volumeCode]) : new Set()
      directoryExpansionProjectCode = projectStore.active.projectCode
    } else {
      const availableVolumeCodes = new Set(nextVolumeGroups.map((group) => group.volumeCode))
      expandedVolumeCodes.value = new Set(
        [...expandedVolumeCodes.value].filter((volumeCode) => availableVolumeCodes.has(volumeCode)),
      )
    }
    selectedNumber.value = target?.chapterNumber ?? null
    chapterInitialized = true
  } finally {
    loading.value = false
    refreshing.value = false
  }
}
async function refreshChapterList() {
  try {
    await refresh()
  } catch (error) {
    if (error instanceof ApiBusinessError) {
      ElMessage.error(error.message)
    } else {
      ElMessage.error('章节目录加载失败，请稍后重试')
    }
  }
}
function toggleBook() { bookExpanded.value = !bookExpanded.value }
function toggleVolume(volumeCode: string) {
  const next = new Set(expandedVolumeCodes.value)
  if (next.has(volumeCode)) next.delete(volumeCode)
  else next.add(volumeCode)
  expandedVolumeCodes.value = next
}
function openChapterMenu(event: MouseEvent, chapterNumber: number) {
  const chapter = chapters.value.find((item) => item.chapterNumber === chapterNumber)
  if (chapter) chapterMenu.value = { x: event.clientX, y: event.clientY, chapter }
}
function closeChapterMenu() { chapterMenu.value = null }
async function removeChapter() {
  const projectCode = projectStore.active?.projectCode
  const deleted = chapterMenu.value?.chapter.chapterNumber
  if (!projectCode || !deleted) return
  chapterMenu.value = null
  try {
    await deleteChapter(projectCode, deleted)
    await refresh()
    ElMessage.success('本章已删除')
  } catch (error) {
    if (error instanceof ApiBusinessError) {
      ElMessage.error(error.message)
    } else {
      ElMessage.error('删除章节失败，请稍后重试')
    }
  }
}

async function selectChapter(chapterNumber: number) {
  if (chapterNumber === selectedNumber.value) return
  if (hasLocalEdits.value) {
    try {
      await ElMessageBox.confirm(
        '当前章节还有未保存修改',
        '未保存修改',
        {
          confirmButtonText: '放弃修改并切换',
          cancelButtonText: '继续编辑',
          type: 'warning',
        },
      )
    } catch {
      return
    }
  }
  selectedNumber.value = chapterNumber
}
function goAdjacent(offset: -1 | 1) {
  const target = chapters.value[selectedIndex.value + offset]
  if (target) void selectChapter(target.chapterNumber)
}
function replaceChapter(updated: GeneratedChapterResponse) {
  const idx = chapters.value.findIndex((chapter) => chapter.chapterNumber === updated.chapterNumber)
  if (idx !== -1) chapters.value[idx] = updated
  volumeGroups.value = volumeGroups.value.map((group) => ({
    ...group,
    chapters: group.chapters.map((chapter) => chapter.chapterNumber === updated.chapterNumber ? updated : chapter),
  }))
}

async function saveEdit() {
  if (!selectedChapter.value || !projectStore.active) return
  if (!editTitle.value.trim()) {
    ElMessage.warning('章节标题不能为空')
    return
  }
  saving.value = true
  try {
    const updated = await overwriteChapterContent(
      projectStore.active.projectCode,
      selectedChapter.value.chapterNumber,
      { title: editTitle.value, content: editContent.value },
    )
    replaceChapter(updated)
    derivedSyncFailed.value = updated.status === 'DIRTY'
    if (derivedSyncFailed.value) {
      ElMessage.error('章节记忆更新失败')
    } else {
      ElMessage.success('保存成功')
    }
  } catch (error) {
    if (error instanceof ApiBusinessError) {
      ElMessage.error(error.message)
    } else {
      ElMessage.error('章节保存失败，请稍后重试')
    }
  } finally {
    saving.value = false
  }
}

async function resyncSelectedChapter() {
  const projectCode = projectStore.active?.projectCode
  const chapter = selectedChapter.value
  if (!projectCode || !chapter || chapter.status !== 'DIRTY') return

  resyncing.value = true
  try {
    const updated = await resyncChapterDerivedData(projectCode, chapter.chapterNumber)
    replaceChapter(updated)
    derivedSyncFailed.value = updated.status === 'DIRTY'
    if (derivedSyncFailed.value) {
      ElMessage.error('章节记忆更新失败')
    } else {
      ElMessage.success('章节记忆已更新')
    }
  } catch (error) {
    derivedSyncFailed.value = true
    if (error instanceof ApiBusinessError) {
      ElMessage.error(error.message)
    } else {
      ElMessage.error('章节记忆更新失败')
    }
  } finally {
    resyncing.value = false
  }
}

watch(() => projectStore.active?.projectCode, () => {
  void refresh(true).catch((error) => {
    if (error instanceof ApiBusinessError) {
      ElMessage.error(error.message)
    } else {
      ElMessage.error('章节目录加载失败，请稍后重试')
    }
  })
}, { immediate: true })
watch(selectedNumber, () => {
  editTitle.value = selectedChapter.value?.title ?? ''
  editContent.value = selectedChapter.value?.content ?? ''
})
onMounted(() => document.addEventListener('click', closeChapterMenu))
onBeforeUnmount(() => document.removeEventListener('click', closeChapterMenu))
</script>

<style scoped>
.chapter-menu { position: fixed; z-index: 10; padding: 5px; background: var(--surface); border: 1px solid var(--border); box-shadow: var(--shadow); border-radius: 6px; }.chapter-menu .el-button { display: flex; justify-content: flex-start; width: 100%; margin: 0; }
.read-workbench { width: 100%; height: 100%; min-width: 0; min-height: 0; display: grid; grid-template-columns: clamp(340px, 25vw, 380px) minmax(0, 1fr); overflow: hidden; background: var(--surface); color: var(--text); font-family: "Microsoft YaHei", "PingFang SC", "Noto Sans CJK SC", "Noto Sans SC", sans-serif; }
.reading-stage { grid-column: 2; grid-row: 1; min-width: 0; min-height: 0; display: flex; flex-direction: column; overflow: hidden; background: var(--surface); }
.derived-sync-control { display: flex; align-items: center; gap: 6px; }
.derived-sync-control small { max-width: 250px; color: var(--text-faint); font-size: 10px; line-height: 1.4; }
.chapter-menu { position: fixed; z-index: 10; padding: 5px; background: var(--surface); border: 1px solid var(--border); border-radius: 6px; box-shadow: var(--shadow); }
.chapter-menu .el-button { display: flex; justify-content: flex-start; width: 100%; margin: 0; }
.stage-empty { height: 100%; display: flex; flex-direction: column; align-items: center; justify-content: center; color: var(--text-faint); text-align: center; }
.stage-empty-icon { width: 66px; height: 66px; display: grid; place-items: center; margin-bottom: 18px; border: 1px solid var(--border); border-radius: 20px; background: var(--surface); box-shadow: var(--shadow-sm); color: var(--primary); font: 700 24px serif; }
.stage-empty h2 { margin-bottom: 5px; color: var(--text); font-size: 18px; }
.stage-empty p { font-size: 12px; }
.stage-empty a { margin-top: 16px; padding: 8px 16px; border-radius: 7px; background: var(--primary); color: var(--surface); font-size: 12px; text-decoration: none; }
@media (max-width: 767px) { .read-workbench { display: flex; flex-direction: column; overflow: hidden; }.reading-stage { flex: 1; min-height: 0; } }
</style>
