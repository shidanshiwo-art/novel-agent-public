<template>
  <div class="proj-wrap editor-page-shell">
    <header class="project-page-head editor-page-header">
      <div>
        <h1>项目设置</h1>
        <p>创建作品、加载已有项目，或调整当前项目的写作规模。</p>
      </div>
      <el-tag v-if="projectStore.active" :type="statusTone(projectStore.active.status)" effect="plain">{{ statusLabel(projectStore.active.status) }}</el-tag>
    </header>

    <!-- Mode selector -->
    <section class="panel project-section">
      <header class="panel-head project-section-head">
        <div>
          <h2>{{ mode === 'create' ? '创建项目' : '加载项目' }}</h2>
        </div>
        <el-select v-model="mode" style="width:200px" size="large" aria-label="项目操作">
          <el-option label="新建项目" value="create" />
          <el-option label="加载项目" value="load" />
        </el-select>
      </header>
      <div class="panel-body">
        <!-- Create form -->
        <el-form v-if="mode === 'create'" :model="pf" label-position="top" class="mode-form">
          <div class="form-row">
            <el-form-item label="项目代号">
              <el-input v-model="pf.projectCode" placeholder="novel_001" />
            </el-form-item>
            <el-form-item label="书名">
              <el-input v-model="pf.title" placeholder="请输入小说标题" />
            </el-form-item>
          </div>
          <div class="form-row three">
            <el-form-item label="类型">
              <el-select v-model="pf.genre" style="width:100%">
                <el-option v-for="g in genres" :key="g" :label="g" :value="g" />
              </el-select>
            </el-form-item>
            <el-form-item label="预计章节数">
              <el-input-number v-model="pf.targetChapterCount" :min="1" style="width:100%" controls-position="right" />
            </el-form-item>
            <el-form-item label="每章字数">
              <el-input-number v-model="pf.wordsPerChapter" :min="500" :step="500" style="width:100%" controls-position="right" />
            </el-form-item>
          </div>
          <el-button type="primary" :loading="creating" @click="createProject" style="width:100%;margin-top:4px">
            创建项目
          </el-button>
        </el-form>

        <!-- Load form -->
        <div v-else class="mode-form load-area">
          <el-input
            v-model="loadCode"
            placeholder="输入项目代号"
            @keyup.enter="loadProject"
            size="large"
            style="flex:1"
          />
          <el-button type="primary" :loading="loadLoading" @click="loadProject" size="large">加载</el-button>
        </div>
      </div>
    </section>

    <!-- Project detail -->
    <section v-if="projectStore.active" class="detail-panel project-section">
      <header class="panel-head project-section-head">
        <div>
          <h2>当前项目</h2>
        </div>
        <div class="project-section-actions">
          <el-button text type="primary" size="small" @click="openTargetChapterEditor">
            调整预计章节数
          </el-button>
        </div>
      </header>
      <div class="detail-grid">
        <div v-for="field in detailFields" :key="field.key" class="detail-cell">
          <div class="detail-label">{{ field.label }}</div>
          <div class="detail-val">{{ field.value }}</div>
        </div>
      </div>
      <div class="detail-progress">
        <div class="dp-header">
          <span class="dp-label">章节进度</span>
          <span class="dp-nums">{{ projectStore.active.currentChapterNumber ?? 0 }} / {{ projectStore.active.targetChapterCount }}</span>
        </div>
        <div class="dp-bar">
          <div class="dp-fill" :style="{ width: pct + '%' }" />
        </div>
      </div>
    </section>

    <el-dialog
      v-model="targetChapterDialogVisible"
      title="调整预计章节数"
      width="min(92vw, 420px)"
      :close-on-click-modal="false"
    >
      <el-form label-position="top">
        <el-form-item label="预计章节数">
          <el-input-number
            v-model="targetChapterDraft"
            :min="1"
            controls-position="right"
            style="width:100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="targetChapterDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="targetChapterUpdating" @click="saveTargetChapterCount">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createProject as apiCreate, getProject, updateTargetChapterCount } from '../api/project'
import { useProjectStore } from '../stores/project'
import { statusLabel, statusTone } from '../utils/uiSemantics'
import type { CreateNovelProjectRequest } from '../types'

const projectStore = useProjectStore()
const route = useRoute()
const router = useRouter()
const genres = ['玄幻', '修真', '都市', '历史', '科幻', '悬疑', '言情', '武侠']
const mode = ref<'create' | 'load'>('create')

const pf = reactive<CreateNovelProjectRequest>({
  projectCode: '', title: '', genre: '玄幻', targetChapterCount: 100, wordsPerChapter: 3000,
})
const creating = ref(false)
async function createProject() {
  if (!pf.projectCode || !pf.title) { ElMessage.warning('请填写项目代号和书名'); return }
  creating.value = true
  try {
    const data = await apiCreate(pf)
    projectStore.setActive(data)
    ElMessage.success('项目创建成功')
    enterWorkbench()
  } finally { creating.value = false }
}

const targetChapterDialogVisible = ref(false)
const targetChapterDraft = ref(100)
const targetChapterUpdating = ref(false)

function openTargetChapterEditor() {
  const project = projectStore.active
  if (!project) return
  targetChapterDraft.value = project.targetChapterCount
  targetChapterDialogVisible.value = true
}

async function saveTargetChapterCount() {
  const project = projectStore.active
  if (!project || !Number.isInteger(targetChapterDraft.value)
      || targetChapterDraft.value < 1) return
  targetChapterUpdating.value = true
  try {
    const updated = await updateTargetChapterCount(project.projectCode, {
      targetChapterCount: targetChapterDraft.value,
    })
    projectStore.setActive(updated)
    targetChapterDialogVisible.value = false
    ElMessage.success('预计章节数已调整')
  } finally {
    targetChapterUpdating.value = false
  }
}

const loadCode = ref('')
const loadLoading = ref(false)
async function loadProject() {
  if (!loadCode.value) return
  loadLoading.value = true
  try {
    const data = await getProject(loadCode.value)
    projectStore.setActive(data)
    ElMessage.success('项目加载成功')
    enterWorkbench()
  } finally { loadLoading.value = false }
}

function enterWorkbench() {
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/read'
  router.push(redirect)
}

const detailFields = computed(() => {
  const p = projectStore.active!
  return [
    { key: 'projectCode', label: '项目代号', value: p.projectCode },
    { key: 'title',       label: '书名',     value: p.title },
    { key: 'genre',       label: '类型',     value: p.genre },
    { key: 'target',      label: '预计章节数', value: p.targetChapterCount + ' 章' },
    { key: 'words',       label: '每章字数', value: p.wordsPerChapter + ' 字' },
    { key: 'current',     label: '已完成',   value: (p.currentChapterNumber ?? 0) + ' 章' },
  ]
})

const pct = computed(() => {
  const p = projectStore.active
  if (!p || !p.targetChapterCount) return 0
  return Math.min(100, ((p.currentChapterNumber ?? 0) / p.targetChapterCount) * 100)
})
</script>

<style scoped>
.proj-wrap { display: flex; flex-direction: column; gap: 28px; }
.project-page-head { margin-bottom: 0; }
.project-page-head h1 { color: var(--text); font-family: "Songti SC", "STSong", serif; }
.project-section { min-width: 0; overflow: visible; border-bottom: 1px solid var(--border); background: transparent; }
.project-section-head {
  align-items: center;
  padding: 0 0 14px;
  background: transparent;
  border-bottom: 1px solid var(--border);
  color: var(--text);
  letter-spacing: 0;
  text-transform: none;
}
.project-section-head > div { min-width: 0; }
.project-section-head h2 { margin-top: 5px; color: var(--text); font: 700 var(--font-size-section-title)/1.4 "Songti SC", "STSong", serif; }
.project-section-actions { display: flex; align-items: center; gap: 8px; }
.panel-body { padding: 20px 0 28px; display: flex; flex-direction: column; gap: 16px; }

.mode-form { display: flex; flex-direction: column; gap: 0; }
.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 12px; }
.form-row.three { grid-template-columns: 1fr 1fr 1fr; }

.load-area { flex-direction: row !important; align-items: flex-start; gap: 10px; }

.detail-panel {
  padding-bottom: 1px;
}
.detail-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0;
}
@media (max-width: 700px) {
  .detail-grid { grid-template-columns: 1fr 1fr; }
  .detail-cell:nth-child(3n) { border-right: 1px solid var(--border); }
  .detail-cell:nth-child(2n) { border-right: none; }
}
.detail-cell {
  padding: 14px 16px 14px 0;
  border-right: 1px solid var(--border);
  border-bottom: 1px solid var(--border);
}
.detail-cell:nth-child(3n) { border-right: none; }
.detail-label { font-size: 11px; color: var(--text-faint); margin-bottom: 4px; letter-spacing: .04em; }
.detail-val { font-size: 14px; font-weight: 600; color: var(--text); }

.detail-progress { padding: 16px 0 18px; }
.dp-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.dp-label { font-size: 12px; color: var(--text-muted); }
.dp-nums { font-size: 12px; color: var(--gold); font-weight: 600; }
.dp-bar { height: 4px; background: var(--surface3); border-radius: 2px; }
.dp-fill { height: 100%; background: linear-gradient(90deg, var(--primary), var(--violet)); border-radius: 2px; transition: width .4s ease; }
@media (max-width: 600px) {
  .project-page-head, .project-section-head { align-items: stretch; flex-direction: column; }
  .project-section-head .el-select { width: 100% !important; }
  .project-section-actions { justify-content: flex-start; }
  .form-row, .form-row.three { grid-template-columns: 1fr; }
  .detail-grid { grid-template-columns: 1fr 1fr; }
  .detail-cell { padding-right: 12px; }
}
</style>
