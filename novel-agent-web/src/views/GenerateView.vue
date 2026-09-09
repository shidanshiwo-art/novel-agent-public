<template>
  <div class="generation-page workbench-shell">
    <div v-if="initializingGenerationPage" class="generation-page-loading" role="status" aria-live="polite">
      <span class="chapter-plan-loading-indicator" aria-hidden="true">●</span>
      <span>正在准备章节计划……</span>
    </div>
    <template v-else>
      <ChapterDirectory
        :volumes="chapterDirectoryVolumes"
        :chapter-count="chapterArcs.length"
        :secondary-tab-label="`已规划 ${chapterArcs.length}`"
        :footer-label="`共 ${chapterArcs.length} 章`"
        :selected-chapter-number="chapterNum"
        :navigation-busy="chapterNavigationBusy"
        :empty-title="chapterPlanHistoryVolumes.length ? '没有可显示的章节' : '还没有章节计划'"
        :empty-description="chapterPlanHistoryVolumes.length ? '完成章节准备后，内容会显示在这里。' : '完成章纲后，章节会显示在这里。'"
        @select-chapter="selectDirectoryChapter"
      >
        <template #footer-action>
          <el-button text @click="router.push('/read')">查看全部章节</el-button>
        </template>
      </ChapterDirectory>

      <main class="generation-stage">
        <ChapterToolbar
          :chapter-number="result?.chapterNumber ?? chapterNum"
          :title="currentChapterArc?.title || '未命名章节'"
          :previous-disabled="!canNavigatePreviousChapter || chapterNavigationBusy"
          :next-disabled="!canNavigateNextChapter || chapterNavigationBusy"
          @previous="goPreviousChapter"
          @next="goNextChapter"
        >
          <span class="status-pill">{{ statusLabel(workflowStatus) }}</span>
          <el-button text type="primary" :disabled="outlineLoadFailed" @click="openChapterPlanDrawer">查看章节计划</el-button>
        </ChapterToolbar>

        <ChapterCanvas
          ref="generationCanvas"
          :content="result?.content || streamingContent"
          :empty-busy="isWorkflowBusy"
          @scroll="handleStreamingScroll"
        >
          <template #actions>
            <div v-if="streamingContent && streamingContentScrollable" class="streaming-scroll-actions">
              <el-button text type="primary" size="small" @click="toggleStreamingContentScroll">
                {{ streamingAtBottom ? '回到顶部' : '回到底部' }}
              </el-button>
            </div>
            <div v-if="workflowStatus === 'COMPLETED' && (result?.content || streamingContent)" class="read-chapter-actions">
              <el-button text type="primary" class="primary-link" @click="goReadChapter(result?.chapterNumber ?? chapterNum)">
                查看正文
              </el-button>
            </div>
          </template>
        </ChapterCanvas>

        <footer v-if="contextualActionBarVisible" class="contextual-action-bar" aria-label="上下文操作">
          <section v-if="reviewIssuesPanelVisible" class="workflow-review-summary" aria-label="审稿问题">
            <div class="result-section-head">
              <span class="meta-label">{{ result?.reviewIssues?.length || isReviewFailed || workflowStatus === 'WAITING_HUMAN' ? '审稿问题' : '审稿结果' }}</span>
              <span v-if="reviewCompleted && !result?.reviewIssues?.length" class="section-heading-note">审稿通过 · 0 项</span>
              <span v-else class="section-heading-note">{{ result?.reviewIssues?.length ?? 0 }} 项</span>
            </div>
            <div v-if="result?.reviewIssues?.length" class="review-detail">
              <ul><li v-for="(issue, index) in result.reviewIssues" :key="index">{{ issue }}</li></ul>
            </div>
            <p v-if="workflowStatus === 'REVISING' && result?.reviewIssues?.length" class="detail-empty">正在根据以上问题修改正文。</p>
            <p v-else-if="isReviewFailed && !result?.reviewIssues?.length" class="detail-empty">自动审稿失败，但正文已保留。</p>
            <p v-else-if="reviewCompleted && !result?.reviewIssues?.length" class="detail-empty">审稿通过，当前没有需要处理的问题。</p>
          </section>

          <section v-if="workflowFailureMessage && !isReviewFailed" class="workflow-message-panel" aria-label="工作流消息">
            <div class="result-section-head">
              <span class="meta-label">工作流消息</span>
            </div>
            <p class="detail-empty">{{ workflowFailureMessage }}</p>
          </section>

          <section v-if="workflowStatus === 'IDLE'" class="review-controls" aria-label="章节生成入口">
            <div class="review-control-buttons">
              <el-button
                v-if="chapterPlan?.status === 'COMPLETED'"
                type="primary"
                class="generate-button"
                @click="goReadChapter(chapterNum)"
              >
                查看正文
              </el-button>
              <el-button
                v-else
                type="primary"
                class="generate-button"
                :loading="submitting"
                :disabled="!workflowStateReady || !canGenerateSingleChapter || isWorkflowBlocked"
                @click="generateSingleChapter"
              >
                生成本章
              </el-button>
            </div>
          </section>

          <section v-else-if="workflowStatus === 'DRAFTING'" class="review-controls" aria-label="正文生成控制">
            <div class="review-control-buttons">
              <el-button
                type="danger"
                plain
                class="stop-button"
                :loading="stopping"
                :disabled="stopping"
                @click="stopCurrentGeneration"
              >
                停止生成
              </el-button>
            </div>
          </section>

          <section v-else-if="workflowStatus === 'REVIEW_FAILED'" class="review-controls" aria-label="审稿失败控制">
            <div class="review-control-buttons">
              <el-button type="warning" :loading="resuming || acceptingRequested" :disabled="resuming || acceptingRequested" @click="resume('REVIEW')">重新审稿</el-button>
              <el-button type="success" :loading="resuming || acceptingRequested" :disabled="resuming || acceptingRequested" @click="resume('PASS')">采用当前正文</el-button>
              <el-button type="danger" :loading="resuming || acceptingRequested" :disabled="resuming || acceptingRequested" @click="resume('ABORT')">停止流程</el-button>
            </div>
          </section>

          <section v-else-if="workflowStatus === 'FAILED' || workflowStatus === 'CANCELLED'" class="review-controls" aria-label="章节生成结束控制">
            <div class="review-control-buttons">
              <el-button type="primary" :loading="submitting" :disabled="submitting" @click="generateSingleChapter">重新生成</el-button>
            </div>
          </section>

          <section v-else-if="workflowStatus === 'WAITING_HUMAN'" class="review-actions" aria-label="人工审核控制">
            <div v-if="canHumanReviewRevise" class="revision-instruction">
              <label for="revision-instruction">人工修改意见</label>
              <el-input
                id="revision-instruction"
                v-model="revisionInstruction"
                type="textarea"
                :autosize="{ minRows: 2, maxRows: 5 }"
                resize="none"
                maxlength="1000"
                show-word-limit
                placeholder="例如：强化主角面对抉择时的犹豫，收束结尾的解释性对白。"
                :disabled="resuming || acceptingRequested"
              />
              <span>留空则按审阅问题重写。</span>
            </div>
            <div v-else class="revision-limit-notice" role="status">
              <strong>人工返修机会已用完</strong>
              <span>当前章节只能选择通过或放弃。</span>
            </div>
            <div class="review-decisions">
              <el-button type="success" :loading="resuming || acceptingRequested" :disabled="resuming || acceptingRequested" @click="resume('PASS')">采用当前正文</el-button>
              <el-button type="warning" :loading="resuming || acceptingRequested" :disabled="!canHumanReviewRevise || resuming || acceptingRequested" @click="resume('REVISE')">驳回重写</el-button>
              <el-button type="danger" :loading="resuming || acceptingRequested" :disabled="resuming || acceptingRequested" @click="resume('ABORT')">放弃</el-button>
            </div>
          </section>

          <section v-else-if="workflowStatus === 'COMPLETED'" class="review-controls" aria-label="已完成操作">
            <div class="review-control-buttons">
              <el-button text type="primary" class="primary-link" @click="goReadChapter(result?.chapterNumber ?? chapterNum)">
                查看正文
              </el-button>
              <el-button type="primary" :loading="nextChapterPreparing" :disabled="nextChapterPreparing || !canNavigateNextChapter" @click="generateNextChapter">
                继续下一章
              </el-button>
            </div>
          </section>
        </footer>
      </main>

    <el-drawer
      v-model="chapterPlanDrawerVisible"
      :title="chapterPlanDrawerTitle"
      size="min(560px, 100%)"
      append-to-body
    >
      <div class="chapter-plan-drawer" aria-label="章节计划编辑">
        <div class="chapter-plan-drawer-head">
          <div>
            <span class="meta-label">章纲</span>
            <h2>章节计划</h2>
          </div>
          <el-tag size="small" :type="chapterPlanTagType(chapterPlan?.status)">
            {{ chapterPlanStatusLabel }}
          </el-tag>
        </div>
        <p class="chapter-plan-drawer-context">第 {{ chapterNum ?? '—' }} 章 · 计划内容只在此处编辑和确认</p>

        <span class="meta-label chapter-plan-edit-label">编辑计划</span>
        <el-form class="chapter-plan-editor-form" label-position="top">
          <el-form-item label="摘要">
            <el-input
              v-model="chapterPlanEditor.summary"
              type="textarea"
              :autosize="{ minRows: 5 }"
              resize="none"
              maxlength="2000"
              show-word-limit
              placeholder="输入本章摘要"
            />
          </el-form-item>
        </el-form>

        <section class="chapter-plan-ai-section" aria-label="AI 生成或调整章节计划">
          <div class="chapter-plan-ai-head">
            <div>
              <span class="meta-label">AI 生成/调整</span>
              <span class="chapter-plan-ai-hint">补充要求（可选）</span>
            </div>
            <el-button
              type="primary"
              size="small"
              :loading="chapterPlanGenerating"
              @click="generateCurrentChapterPlan"
            >
              生成计划
            </el-button>
          </div>
          <el-input
            v-model="chapterPlanRequirement"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 5 }"
            resize="none"
            maxlength="1000"
            show-word-limit
            aria-label="章节计划补充要求"
            placeholder="例如：强化本章冲突，增加主角与反派的第一次正面交锋。"
          />
          <p class="chapter-plan-ai-note">留空将按当前大纲和前文自然生成。</p>
        </section>

        <section v-if="chapterPlanDraft" class="chapter-plan-draft-preview" aria-label="章节计划草稿预览">
          <div class="chapter-plan-draft-head">
            <div>
              <span class="meta-label">草稿预览</span>
              <span class="chapter-plan-draft-note">可编辑后确认，确认前不会替换当前计划</span>
            </div>
          </div>
          <el-form class="chapter-plan-draft-form" label-position="top">
            <el-form-item label="摘要">
              <el-input
                v-model="chapterPlanDraft.summary"
                type="textarea"
                :autosize="{ minRows: 4 }"
                resize="none"
                placeholder="输入本章摘要"
              />
            </el-form-item>
          </el-form>
        </section>

        <div class="chapter-plan-drawer-actions">
          <el-button @click="chapterPlanDrawerVisible = false">取消</el-button>
          <el-button
            v-if="chapterPlanDraft"
            type="success"
            :loading="chapterPlanConfirming"
            :disabled="!chapterPlanDraftId || !chapterPlanDraft.summary.trim() || !currentChapterArc"
            @click="confirmCurrentChapterPlan"
          >
            确认计划
          </el-button>
          <el-button
            v-else
            type="primary"
            :loading="chapterPlanUpdating"
            :disabled="!chapterPlanEditor.summary.trim() || !currentChapterArc"
            @click="saveCurrentChapterPlan"
          >
            保存计划
          </el-button>
        </div>
      </div>
    </el-drawer>

    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { acceptGenerationSession, createGenerationSession, getActiveGenerationSession, resumeChapter, stopGenerationSession, subscribeGenerationSessionEvents } from '../api/chapter'
import { ApiBusinessError } from '../api/http'
import { confirmChapterPlan, generateChapterPlan, listChapterPlans, listOutlineNodes, updateChapterPlan } from '../api/planning'
import { useProjectStore } from '../stores/project'
import type { ChapterPlan, GenerateChapterResponse, GenerationSessionEvent, GenerationSessionResponse, OutlineNode, PlanningDraftResponse, WorkflowStatus } from '../types'
import { domainLabel, statusLabel, statusTone } from '../utils/uiSemantics'
import ChapterCanvas from '../components/workbench/ChapterCanvas.vue'
import ChapterDirectory, { type ChapterDirectoryVolume } from '../components/workbench/ChapterDirectory.vue'
import ChapterToolbar from '../components/workbench/ChapterToolbar.vue'

type ChapterPlanDraft = Pick<ChapterPlan, 'summary'>
type ChapterPlanHistoryEntry = {
  arc: OutlineNode
  plan: ChapterPlan | null
}
type ChapterPlanHistoryVolume = {
  volume: OutlineNode
  chapters: ChapterPlanHistoryEntry[]
}
type WorkflowStageCode = 'DRAFT' | 'REVIEW' | 'REVISE' | 'COMPRESSION' | 'PERSIST'
type ActiveGenerationSessionSnapshot = {
  workflowId: string
  chapterNumber: number
  status: string
  currentNode: string
  accumulatedContent: string
  completedStages: string[]
  reviewIssues: string[]
  failureMessage: string
}

const router = useRouter()
const route = useRoute()
const projectStore = useProjectStore()

const initializingGenerationPage = ref(true)
const generationPageInitialized = ref(false)
const chapterNum = ref<number | null>(null)
const generating = ref(false)
const nextChapterPreparing = ref(false)
const resuming = ref(false)
const stopping = ref(false)
const acceptingRequested = ref(false)
const revisionInstruction = ref('')
const result = ref<GenerateChapterResponse | null>(null)
const generationSession = ref<GenerationSessionResponse | null>(null)
const workflowStatus = ref<WorkflowStatus>('IDLE')
const workflowStateReady = ref(false)
const streamingContent = ref('')
const receivedBuffer = ref('')
type ChapterCanvasInstance = {
  getElement: () => HTMLElement | null
}
const generationCanvas = ref<ChapterCanvasInstance | null>(null)
const streamingAtBottom = ref(true)
const streamingContentScrollable = ref(false)
const STREAM_DISPLAY_INTERVAL_MS = 30
const STREAM_DISPLAY_CHARS_PER_TICK = 4
const STREAM_BOTTOM_THRESHOLD_PX = 24
const GENERATION_SESSION_RECONNECT_DELAY_MS = 1000
let streamingDisplayTimer: ReturnType<typeof setInterval> | null = null
let generationEventSource: EventSource | null = null
let generationSessionReconnectTimer: ReturnType<typeof setTimeout> | null = null
let generationConnectionVersion = 0
const chapterPlan = ref<ChapterPlan | null>(null)
const chapterPlans = ref<ChapterPlan[]>([])
const outlineNodes = ref<OutlineNode[]>([])
const outlineLoadFailed = ref(false)
const chapterPlanLoading = ref(false)
const chapterPlanLoadFailed = ref(false)
const chapterPlanRequirement = ref('')
const chapterPlanGenerating = ref(false)
const chapterPlanDraft = ref<ChapterPlanDraft | null>(null)
const chapterPlanDraftId = ref('')
const chapterPlanConfirming = ref(false)
const chapterPlanUpdating = ref(false)
const chapterPlanDrawerVisible = ref(false)
const chapterPlanDrawerMode = ref<'edit' | 'ai'>('edit')
const chapterPlanEditor = ref<ChapterPlanDraft>({ summary: '' })
const workflowStageCode = ref<WorkflowStageCode | ''>('')
const completedWorkflowStages = ref<WorkflowStageCode[]>([])
const reviewCompleted = ref(false)
const reviseRound = ref(0)
const workflowFailureMessage = ref('')
const REVIEW_FAILURE_MESSAGE = '自动审稿失败，但正文已保留。你可以重新检查、直接采用当前正文或停止本次生成。'
const GENERIC_WORKFLOW_FAILURE_MESSAGE = '系统暂时出现异常，请稍后重试。'
const SAFE_WORKFLOW_FAILURE_MESSAGES = new Set([
  GENERIC_WORKFLOW_FAILURE_MESSAGE,
  '正文生成失败，请重新生成本章。',
  '正文修改失败，请重新审稿或采用当前正文。',
  '章节记忆更新失败，正文不会丢失，请重新更新。',
  '章节保存失败，请重试。',
  '用户已结束章节生成流程',
  '用户已停止章节生成流程',
])
let initialChapterPlans: ChapterPlan[] | null = null
let initialChapterPlanLoadFailed = false
let chapterPlanLoadPromise: Promise<void> = Promise.resolve()

const activeWorkflowStatuses: WorkflowStatus[] = [
  'DRAFTING',
  'REVIEWING',
  'REVISING',
  'COMPRESSING',
  'PERSISTING',
]

const workflowStageByEvent: Partial<Record<GenerationSessionEvent['type'], WorkflowStageCode>> = {
  DRAFT_STARTED: 'DRAFT',
  DRAFT_CHUNK: 'DRAFT',
  DRAFT_COMPLETED: 'DRAFT',
  REVIEW_STARTED: 'REVIEW',
  REVIEW_COMPLETED: 'REVIEW',
  REVISION_STARTED: 'REVISE',
  REVISION_COMPLETED: 'REVISE',
  COMPRESSION_STARTED: 'COMPRESSION',
  COMPRESSION_COMPLETED: 'COMPRESSION',
  PERSIST_STARTED: 'PERSIST',
  PERSIST_COMPLETED: 'PERSIST',
}

const workflowStatusByEvent: Partial<Record<GenerationSessionEvent['type'], WorkflowStatus>> = {
  GENERATION_STARTED: 'DRAFTING',
  DRAFT_STARTED: 'DRAFTING',
  DRAFT_CHUNK: 'DRAFTING',
  REVIEW_STARTED: 'REVIEWING',
  REVIEW_FAILED: 'REVIEW_FAILED',
  REVISION_STARTED: 'REVISING',
  COMPRESSION_STARTED: 'COMPRESSING',
  PERSIST_STARTED: 'PERSISTING',
  HUMAN_REVIEW_REQUIRED: 'WAITING_HUMAN',
  GENERATION_COMPLETED: 'COMPLETED',
  GENERATION_FAILED: 'FAILED',
  GENERATION_ABORTED: 'CANCELLED',
  GENERATION_CANCELLED: 'CANCELLED',
}

const completedWorkflowStageByEvent: Partial<Record<GenerationSessionEvent['type'], WorkflowStageCode>> = {
  DRAFT_COMPLETED: 'DRAFT',
  REVIEW_COMPLETED: 'REVIEW',
  REVISION_COMPLETED: 'REVISE',
  COMPRESSION_COMPLETED: 'COMPRESSION',
  PERSIST_COMPLETED: 'PERSIST',
}

function isWorkflowStageCode(code: string): code is WorkflowStageCode {
  return ['DRAFT', 'REVIEW', 'REVISE', 'COMPRESSION', 'PERSIST'].includes(code)
}

function resetWorkflowTimeline() {
  workflowStatus.value = 'IDLE'
  workflowStageCode.value = ''
  completedWorkflowStages.value = []
  reviewCompleted.value = false
  reviseRound.value = 0
  acceptingRequested.value = false
  workflowFailureMessage.value = ''
}

function resolveWorkflowFailureMessage(
  content: string | null | undefined,
  status?: string,
) {
  if (status === 'REVIEW_FAILED') return REVIEW_FAILURE_MESSAGE
  const message = content?.trim()
  return message && SAFE_WORKFLOW_FAILURE_MESSAGES.has(message)
    ? message
    : GENERIC_WORKFLOW_FAILURE_MESSAGE
}

function markWorkflowStageCompleted(stage: WorkflowStageCode) {
  if (!completedWorkflowStages.value.includes(stage)) {
    completedWorkflowStages.value.push(stage)
  }
}

function shouldUpdateWorkflowStage(stage: WorkflowStageCode) {
  if (workflowStatus.value === 'COMPLETED') return false
  if (workflowStatus.value === 'PERSISTING') return stage === 'PERSIST'
  if (workflowStatus.value === 'COMPRESSING') return stage === 'COMPRESSION'
  return true
}

const isWorkflowBusy = computed(() => activeWorkflowStatuses.includes(workflowStatus.value))
const isWorkflowBlocked = computed(() => !workflowStateReady.value
  || isWorkflowBusy.value
  || workflowStatus.value === 'WAITING_HUMAN'
  || workflowStatus.value === 'REVIEW_FAILED')
const isReviewFailed = computed(() => workflowStatus.value === 'REVIEW_FAILED')
const contextualActionBarStatuses: WorkflowStatus[] = [
  'IDLE',
  'DRAFTING',
  'REVIEW_FAILED',
  'WAITING_HUMAN',
  'COMPLETED',
  'FAILED',
  'CANCELLED',
]
const submitting = computed(() => generating.value)
const canHumanReviewRevise = computed(() => result.value?.canHumanRevise ?? workflowStatus.value === 'WAITING_HUMAN')
const hasReviewIssues = computed(() => Boolean(result.value?.reviewIssues?.length))
const reviewIssuesPanelVisible = computed(() => {
  if (workflowStatus.value === 'REVISING' && !hasReviewIssues.value) return false
  return hasReviewIssues.value || isReviewFailed.value || reviewCompleted.value
})
const contextualActionBarVisible = computed(() => workflowStateReady.value
  && (contextualActionBarStatuses.includes(workflowStatus.value) || reviewIssuesPanelVisible.value))
const canGenerateSingleChapter = computed(() =>
  chapterPlan.value?.status === 'READY'
  && !chapterPlanLoading.value
  && !chapterPlanLoadFailed.value,
)
const chapterNavigationBusy = computed(() =>
  initializingGenerationPage.value
  || chapterPlanLoading.value
  || chapterPlanGenerating.value
  || chapterPlanConfirming.value
  || chapterPlanUpdating.value
  || nextChapterPreparing.value
  || generating.value
  || resuming.value
  || stopping.value
  || isWorkflowBusy.value
  || workflowStatus.value === 'WAITING_HUMAN',
)
const chapterPlanStatusLabel = computed(() => {
  if (chapterPlanLoading.value) return '加载中'
  if (chapterPlanLoadFailed.value) return '加载失败'
  if (outlineLoadFailed.value) return '结构加载失败'
  if (!currentChapterArc.value) return '未创建章纲'
  if (!chapterPlan.value) return '不存在'
  return statusLabel(chapterPlan.value.status)
})
const chapterArcs = computed(() => outlineNodes.value
  .filter((node) => node.nodeKind === 'ARC'
    && node.parentNodeCode
    && outlineNodes.value.some((parent) =>
      parent.nodeCode === node.parentNodeCode && parent.nodeKind === 'VOLUME',
    )
    && node.startChapter != null
    && node.endChapter != null
    && node.startChapter <= node.endChapter)
  .sort((left, right) =>
    (left.startChapter! - right.startChapter!)
    || (left.endChapter! - right.endChapter!),
  ))
const currentChapterArc = computed(() => {
  const chapterNumber = chapterNum.value
  if (chapterNumber === null) return null
  return chapterArcs.value
    .filter((node) => node.startChapter! <= chapterNumber && chapterNumber <= node.endChapter!)
    .sort((left, right) =>
      (left.endChapter! - left.startChapter!) - (right.endChapter! - right.startChapter!),
    )[0] ?? null
})
const currentChapterArcIndex = computed(() => {
  const currentArc = currentChapterArc.value
  return currentArc
    ? chapterArcs.value.findIndex((arc) => arc.nodeCode === currentArc.nodeCode)
    : -1
})
const previousChapterArc = computed(() => {
  const index = currentChapterArcIndex.value
  return index > 0 ? chapterArcs.value[index - 1] : null
})
const nextChapterArc = computed(() => {
  const index = currentChapterArcIndex.value
  return index >= 0 && index < chapterArcs.value.length - 1
    ? chapterArcs.value[index + 1]
    : null
})
const canNavigatePreviousChapter = computed(() => previousChapterArc.value !== null)
const canNavigateNextChapter = computed(() => nextChapterArc.value !== null)
const chapterPlanHistoryVolumes = computed<ChapterPlanHistoryVolume[]>(() => {
  const plansByChapter = new Map<string, ChapterPlan>(
    chapterPlans.value.map((plan) => [
      `${plan.outlineNodeCode}:${plan.chapterNumber}`,
      plan,
    ] as [string, ChapterPlan]),
  )
  return outlineNodes.value
    .filter((node) => node.nodeKind === 'VOLUME'
      && node.parentNodeCode
      && outlineNodes.value.some((parent) =>
        parent.nodeCode === node.parentNodeCode && parent.nodeKind === 'BOOK',
      ))
    .sort((left, right) => left.sequenceNo - right.sequenceNo)
    .map((volume) => ({
      volume,
      chapters: outlineNodes.value
        .filter((node) => node.nodeKind === 'ARC'
          && node.parentNodeCode === volume.nodeCode
          && node.startChapter != null
          && node.endChapter != null)
        .sort((left, right) =>
          (left.startChapter! - right.startChapter!)
          || (left.sequenceNo - right.sequenceNo),
        )
        .map((arc) => ({
          arc,
          plan: plansByChapter.get(`${arc.nodeCode}:${arc.startChapter}`) ?? null,
        })),
    }))
    .filter((volumeGroup) => volumeGroup.chapters.length > 0)
})
const chapterDirectoryVolumes = computed<ChapterDirectoryVolume[]>(() => chapterPlanHistoryVolumes.value.map((volumeGroup) => ({
  key: volumeGroup.volume.nodeCode,
  sequenceNo: volumeGroup.volume.sequenceNo,
  title: volumeGroup.volume.title,
  chapters: volumeGroup.chapters.map(({ arc, plan }) => ({
    key: arc.nodeCode,
    chapterNumber: arc.startChapter ?? null,
    title: arc.title,
    metaLabel: chapterPlanHistoryStatus(plan),
  })),
})))
const chapterPlanDrawerTitle = computed(() =>
  chapterPlanDrawerMode.value === 'ai' ? 'AI 生成/调整章节计划' : '编辑章节计划',
)

function chapterNumberForArc(arc: OutlineNode | null, direction: -1 | 1) {
  if (!arc) return null
  return direction === -1 ? (arc.endChapter ?? null) : (arc.startChapter ?? null)
}

function goPreviousChapter() {
  if (!canNavigatePreviousChapter.value || chapterNavigationBusy.value) return
  const targetChapter = chapterNumberForArc(previousChapterArc.value, -1)
  if (targetChapter !== null) chapterNum.value = targetChapter
}

function goNextChapter() {
  if (!canNavigateNextChapter.value || chapterNavigationBusy.value) return
  const targetChapter = chapterNumberForArc(nextChapterArc.value, 1)
  if (targetChapter !== null) chapterNum.value = targetChapter
}

function closeGenerationEventStream() {
  generationConnectionVersion += 1
  if (generationSessionReconnectTimer !== null) {
    clearTimeout(generationSessionReconnectTimer)
    generationSessionReconnectTimer = null
  }
  generationEventSource?.close()
  generationEventSource = null
}

function resetStreamingContent() {
  stopStreamingDisplayTimer()
  receivedBuffer.value = ''
  streamingContent.value = ''
  streamingAtBottom.value = true
  streamingContentScrollable.value = false
}

function stopStreamingDisplayTimer() {
  if (streamingDisplayTimer === null) return
  clearInterval(streamingDisplayTimer)
  streamingDisplayTimer = null
}

function drainReceivedBuffer() {
  if (!receivedBuffer.value) {
    stopStreamingDisplayTimer()
    return
  }

  const container = generationCanvas.value?.getElement() ?? null
  if (container) {
    streamingAtBottom.value = isStreamingContentNearBottom(container)
  }
  const shouldAutoScroll = streamingAtBottom.value
  const nextChunk = receivedBuffer.value.slice(0, STREAM_DISPLAY_CHARS_PER_TICK)
  receivedBuffer.value = receivedBuffer.value.slice(nextChunk.length)
  streamingContent.value += nextChunk
  void nextTick(() => {
    const currentContainer = generationCanvas.value?.getElement() ?? null
    if (!currentContainer) return
    if (shouldAutoScroll && streamingAtBottom.value) {
      scrollStreamingContentToBottom('auto')
      return
    }
    updateStreamingScrollState(currentContainer)
  })
  if (!receivedBuffer.value) {
    stopStreamingDisplayTimer()
  }
}

function enqueueReceivedContent(content: string) {
  receivedBuffer.value += content
  drainReceivedBuffer()
  if (receivedBuffer.value && streamingDisplayTimer === null) {
    streamingDisplayTimer = setInterval(drainReceivedBuffer, STREAM_DISPLAY_INTERVAL_MS)
  }
}

function handleGenerationSessionEvent(event: GenerationSessionEvent) {
  if (event.type === 'GENERATION_STARTED') {
    resetStreamingContent()
    resetWorkflowTimeline()
  }

  const nextWorkflowStatus = workflowStatusByEvent[event.type]
  if (nextWorkflowStatus) {
    workflowStatus.value = nextWorkflowStatus
  }

  const stage = workflowStageByEvent[event.type]
  if (stage && shouldUpdateWorkflowStage(stage)) {
    workflowStageCode.value = stage
  }
  const completedStage = completedWorkflowStageByEvent[event.type]
  if (completedStage) {
    markWorkflowStageCompleted(completedStage)
  }
  if (event.type === 'REVISION_STARTED') {
    reviseRound.value += 1
  }
  if (event.type === 'REVIEW_STARTED') {
    reviewCompleted.value = false
  }
  if (event.type === 'REVIEW_COMPLETED') {
    reviewCompleted.value = true
    if (result.value) {
      result.value = {
        ...result.value,
        reviewIssues: event.reviewIssues ?? [],
      }
    }
  }
  if (event.type === 'REVIEW_STARTED'
    || event.type === 'REVISION_STARTED'
    || event.type === 'COMPRESSION_STARTED'
    || event.type === 'PERSIST_STARTED') {
    workflowFailureMessage.value = ''
  }
  if (event.type === 'COMPRESSION_STARTED'
    || event.type === 'GENERATION_COMPLETED'
    || event.type === 'GENERATION_ABORTED'
    || event.type === 'GENERATION_CANCELLED'
    || event.type === 'GENERATION_FAILED') {
    acceptingRequested.value = false
  }
  if (event.type === 'REVIEW_FAILED') {
    reviewCompleted.value = false
    workflowFailureMessage.value = REVIEW_FAILURE_MESSAGE
  }
  if (event.type === 'GENERATION_FAILED') {
    workflowFailureMessage.value = resolveWorkflowFailureMessage(event.content?.trim())
  }
  if (event.type === 'GENERATION_ABORTED' || event.type === 'GENERATION_CANCELLED') {
    workflowFailureMessage.value = resolveWorkflowFailureMessage(event.content?.trim())
  }
  if (event.type === 'HUMAN_REVIEW_REQUIRED'
    || event.type === 'REVIEW_FAILED'
    || event.type === 'GENERATION_COMPLETED'
    || event.type === 'GENERATION_ABORTED'
    || event.type === 'GENERATION_CANCELLED'
    || event.type === 'GENERATION_FAILED') {
    workflowStageCode.value = ''
  }

  if (event.type === 'DRAFT_CHUNK' && event.content) {
    enqueueReceivedContent(event.content)
  }

  if (event.type === 'GENERATION_COMPLETED'
    || event.type === 'GENERATION_ABORTED'
    || event.type === 'GENERATION_CANCELLED'
    || event.type === 'GENERATION_FAILED') {
    closeGenerationEventStream()
  }
}

function connectGenerationEventStream(workflowId: string) {
  closeGenerationEventStream()
  const source = subscribeGenerationSessionEvents(
    workflowId,
    handleGenerationSessionEvent,
  )
  generationEventSource = source
  source.onerror = () => {
    if (generationEventSource !== source) return
    closeGenerationEventStream()
    void refreshActiveGenerationSessionAndReconnect()
  }
}

function scheduleGenerationSessionReconnect() {
  if (generationSessionReconnectTimer !== null) return
  generationSessionReconnectTimer = setTimeout(() => {
    generationSessionReconnectTimer = null
    void refreshActiveGenerationSessionAndReconnect()
  }, GENERATION_SESSION_RECONNECT_DELAY_MS)
}

async function refreshActiveGenerationSessionAndReconnect() {
  const projectCode = projectStore.active?.projectCode
  const chapterNumber = chapterNum.value
  const connectionVersion = generationConnectionVersion
  workflowStateReady.value = false
  if (!projectCode || chapterNumber === null) {
    workflowStateReady.value = !initializingGenerationPage.value
    return
  }

  try {
    const activeSession = await getActiveGenerationSession(projectCode, chapterNumber)
    if (connectionVersion !== generationConnectionVersion
      || projectCode !== projectStore.active?.projectCode
      || chapterNumber !== chapterNum.value) return
    if (!activeSession) {
      resetStreamingContent()
      resetWorkflowTimeline()
      generationSession.value = null
      result.value = null
      workflowStateReady.value = true
      return
    }

    resetStreamingContent()
    resetWorkflowTimeline()
    restoreGenerationSessionSnapshot(activeSession, projectCode)
    workflowStateReady.value = true
    connectGenerationEventStream(activeSession.workflowId)
  } catch (error) {
    if (connectionVersion === generationConnectionVersion) {
      if (error instanceof ApiBusinessError) {
        workflowFailureMessage.value = error.message
      } else {
        workflowFailureMessage.value = '章节生成状态暂时无法加载，请稍后重试。'
      }
      scheduleGenerationSessionReconnect()
    }
  }
}

function handleStreamingScroll(container: HTMLElement | null) {
  if (!container) return
  updateStreamingScrollState(container)
}

function isStreamingContentNearBottom(container: HTMLElement) {
  return container.scrollHeight - container.scrollTop - container.clientHeight <= STREAM_BOTTOM_THRESHOLD_PX
}

function updateStreamingScrollState(container: HTMLElement) {
  streamingAtBottom.value = isStreamingContentNearBottom(container)
  streamingContentScrollable.value = container.scrollHeight - container.clientHeight > STREAM_BOTTOM_THRESHOLD_PX
}

function scrollStreamingContentToBottom(behavior: ScrollBehavior = 'smooth') {
  const container = generationCanvas.value?.getElement() ?? null
  if (!container) return
  container.scrollTo({
    top: container.scrollHeight,
    behavior,
  })
  streamingAtBottom.value = true
  streamingContentScrollable.value = container.scrollHeight - container.clientHeight > STREAM_BOTTOM_THRESHOLD_PX
}

function scrollStreamingContentToTop(behavior: ScrollBehavior = 'smooth') {
  const container = generationCanvas.value?.getElement() ?? null
  if (!container) return
  container.scrollTo({
    top: 0,
    behavior,
  })
  streamingAtBottom.value = false
  streamingContentScrollable.value = container.scrollHeight - container.clientHeight > STREAM_BOTTOM_THRESHOLD_PX
}

function toggleStreamingContentScroll() {
  if (streamingAtBottom.value) {
    scrollStreamingContentToTop()
  } else {
    scrollStreamingContentToBottom()
  }
}
async function loadCurrentChapterPlan(existingPlans?: ChapterPlan[]) {
  const projectCode = projectStore.active?.projectCode
  const chapterNumber = chapterNum.value
  if (!projectCode || chapterNumber === null) {
    chapterPlan.value = null
    chapterPlanLoadFailed.value = false
    chapterPlanLoading.value = false
    return
  }

  chapterPlan.value = null
  chapterPlanLoading.value = true
  chapterPlanLoadFailed.value = existingPlans !== undefined && initialChapterPlanLoadFailed
  try {
    const plans = existingPlans ?? await listChapterPlans(projectCode)
    chapterPlans.value = plans
    const arc = currentChapterArc.value
    chapterPlan.value = arc
      ? plans.find((plan) =>
        plan.chapterNumber === chapterNumber && plan.outlineNodeCode === arc.nodeCode,
      ) ?? null
      : null
  } catch {
    chapterPlan.value = null
    chapterPlanLoadFailed.value = true
  } finally {
    chapterPlanLoading.value = false
  }
}

function restoreGenerationSessionSnapshot(
  activeSession: ActiveGenerationSessionSnapshot,
  projectCode: string,
) {
  const restoredStatus = workflowStatusFromApi(activeSession.status, 'DRAFTING')

  streamingContent.value = activeSession.accumulatedContent
  workflowStatus.value = restoredStatus

  completedWorkflowStages.value = activeSession.completedStages.filter(isWorkflowStageCode)
  reviewCompleted.value = restoredStatus !== 'REVIEW_FAILED'
    && activeSession.completedStages.includes('REVIEW')
  workflowStageCode.value = workflowStageFromStatus(restoredStatus)

  result.value = {
    workflowId: activeSession.workflowId,
    status: activeSession.status,
    projectId: projectCode,
    chapterNumber: activeSession.chapterNumber,
    content: null,
    reviewIssues: activeSession.reviewIssues,
    completedStages: activeSession.completedStages,
    canHumanRevise: restoredStatus === 'WAITING_HUMAN',
  }
  workflowFailureMessage.value = resolveWorkflowFailureMessage(
    activeSession.failureMessage,
    activeSession.status,
  )
  generationSession.value = activeSession
  void nextTick(() => {
    const container = generationCanvas.value?.getElement() ?? null
    if (container) updateStreamingScrollState(container)
  })
}

async function loadActiveGenerationSession() {
  const projectCode = projectStore.active?.projectCode
  const chapterNumber = chapterNum.value
  workflowStateReady.value = false
  if (!projectCode || chapterNumber === null) {
    resetWorkflowTimeline()
    generationSession.value = null
    result.value = null
    workflowStateReady.value = !initializingGenerationPage.value
    return
  }

  closeGenerationEventStream()
  const connectionVersion = generationConnectionVersion
  try {
    const activeSession = await getActiveGenerationSession(projectCode, chapterNumber)
    if (connectionVersion !== generationConnectionVersion
      || projectCode !== projectStore.active?.projectCode
      || chapterNumber !== chapterNum.value) return

    resetStreamingContent()
    resetWorkflowTimeline()
    if (!activeSession) {
      generationSession.value = null
      result.value = null
      workflowStateReady.value = true
      return
    }

    restoreGenerationSessionSnapshot(activeSession, projectCode)
    workflowStateReady.value = true
    connectGenerationEventStream(activeSession.workflowId)
  } catch (error) {
    if (connectionVersion === generationConnectionVersion) {
      if (error instanceof ApiBusinessError) {
        workflowFailureMessage.value = error.message
      } else {
        workflowFailureMessage.value = '章节生成状态暂时无法加载，请稍后重试。'
      }
      scheduleGenerationSessionReconnect()
    }
  }
}

function isValidChapterNumber(value: number) {
  return Number.isInteger(value) && value > 0
}

function resolveDefaultChapter(
  existingPlans: ChapterPlan[],
  requestedChapter: number,
) {
  if (isValidChapterNumber(requestedChapter)) return requestedChapter
  const latestPlan = [...existingPlans]
    .filter((plan) => isValidChapterNumber(plan.chapterNumber))
    .sort((left, right) => right.chapterNumber - left.chapterNumber)[0]
  return latestPlan?.chapterNumber ?? 1
}

async function initializeGenerationPage() {
  initializingGenerationPage.value = true
  chapterPlanLoading.value = true
  chapterPlanLoadFailed.value = false
  outlineLoadFailed.value = false
  outlineNodes.value = []
  chapterPlans.value = []
  initialChapterPlans = null
  initialChapterPlanLoadFailed = false

  const project = projectStore.active
  if (!project) {
    outlineNodes.value = []
    outlineLoadFailed.value = false
    chapterPlanLoading.value = false
    initializingGenerationPage.value = false
    return
  }

  const requestedChapter = Number(route.query.chapter)
  let availablePlans: ChapterPlan[] = []
  try {
    const existingPlans = await listChapterPlans(project.projectCode)
    chapterPlans.value = existingPlans
    initialChapterPlans = existingPlans
    availablePlans = existingPlans
  } catch {
    chapterPlans.value = []
    initialChapterPlans = []
    initialChapterPlanLoadFailed = true
  }

  try {
    const existingOutlines = await listOutlineNodes(project.projectCode)
    outlineNodes.value = existingOutlines
  } catch {
    outlineLoadFailed.value = true
  }

  const defaultChapter = resolveDefaultChapter(availablePlans, requestedChapter)
  chapterNum.value = defaultChapter
}

onMounted(() => {
  void initializeGenerationPage()
})
watch(chapterNum, async (chapterNumber) => {
  if (chapterNumber === null) return

  const isInitialChapterSelection = !generationPageInitialized.value
  if (generationPageInitialized.value) {
    workflowStateReady.value = false
    closeGenerationEventStream()
    resetStreamingContent()
    resetWorkflowTimeline()
    generationSession.value = null
    chapterPlanDrawerVisible.value = false
    chapterPlanDraft.value = null
    chapterPlanDraftId.value = ''
  }

  if (!isInitialChapterSelection || initialChapterPlans !== null) {
    chapterPlanLoadPromise = loadCurrentChapterPlan(
      isInitialChapterSelection ? initialChapterPlans ?? [] : undefined,
    )
    await chapterPlanLoadPromise
  }

  if (isInitialChapterSelection) {
    initialChapterPlans = null
    initializingGenerationPage.value = false
    generationPageInitialized.value = true
  }
  void loadActiveGenerationSession()
})
onBeforeUnmount(() => {
  closeGenerationEventStream()
  stopStreamingDisplayTimer()
})

function openChapterPlanEditor() {
  if (!chapterPlan.value) return
  chapterPlanDrawerMode.value = 'edit'
  chapterPlanEditor.value = {
    summary: chapterPlan.value.summary,
  }
  chapterPlanDrawerVisible.value = true
}

function openChapterPlanDrawer() {
  if (!currentChapterArc.value || outlineLoadFailed.value) return
  if (chapterPlan.value) {
    openChapterPlanEditor()
    return
  }
  openChapterPlanAi()
}

function selectDirectoryChapter(targetChapter: number) {
  if (targetChapter == null || targetChapter === chapterNum.value || chapterNavigationBusy.value) return
  chapterNum.value = targetChapter
}

function replaceChapterPlanInHistory(plan: ChapterPlan) {
  const exists = chapterPlans.value.some((item) =>
    item.chapterNumber === plan.chapterNumber && item.outlineNodeCode === plan.outlineNodeCode,
  )
  chapterPlans.value = exists
    ? chapterPlans.value.map((item) =>
      item.chapterNumber === plan.chapterNumber && item.outlineNodeCode === plan.outlineNodeCode
        ? plan
        : item,
    )
    : [...chapterPlans.value, plan]
}

function openChapterPlanAi() {
  if (!currentChapterArc.value || outlineLoadFailed.value) return
  chapterPlanDrawerMode.value = 'ai'
  chapterPlanEditor.value = {
    summary: chapterPlan.value?.summary ?? '',
  }
  chapterPlanDrawerVisible.value = true
}

async function generateCurrentChapterPlan() {
  const projectCode = projectStore.active?.projectCode
  const chapterNumber = chapterNum.value
  if (!projectCode || chapterNumber === null) return
  if (!currentChapterArc.value) {
    ElMessage.warning('当前章节尚未创建章纲')
    return
  }

  chapterPlanGenerating.value = true
  try {
    const draft: PlanningDraftResponse<ChapterPlan> = await generateChapterPlan(
      projectCode,
      chapterNumber,
      { requirement: chapterPlanRequirement.value.trim() },
    )
    const payload = draft.payload
    if (!payload || typeof payload.summary !== 'string') {
      throw new Error(`${domainLabel('ChapterPlan')}草稿数据无效`)
    }
    chapterPlanDraft.value = {
      summary: payload.summary,
    }
    chapterPlanDraftId.value = draft.draftId
    ElMessage.success('章节计划草稿已生成')
  } catch (error) {
    if (error instanceof ApiBusinessError) {
      ElMessage.error(error.message)
    } else {
      ElMessage.error('章节计划生成失败，请稍后重试')
    }
  } finally {
    chapterPlanGenerating.value = false
  }
}

async function confirmCurrentChapterPlan() {
  const projectCode = projectStore.active?.projectCode
  const chapterNumber = chapterNum.value
  const draft = chapterPlanDraft.value
  const draftId = chapterPlanDraftId.value
  if (!projectCode || chapterNumber === null || !draft || !draftId) return
  const arc = currentChapterArc.value
  if (!arc) {
    ElMessage.warning('当前章节尚未创建章纲')
    return
  }

  chapterPlanConfirming.value = true
  try {
    const summary = draft.summary.trim()
    await confirmChapterPlan(projectCode, chapterNumber, {
      draftId,
      title: arc.title,
      summary,
    })
    chapterPlanDraft.value = null
    chapterPlanDraftId.value = ''
    await loadCurrentChapterPlan()
    chapterPlanDrawerVisible.value = false
    ElMessage.success('章节计划已确认，现在可以生成正文')
  } catch (error) {
    if (error instanceof ApiBusinessError) {
      ElMessage.error(error.message)
    } else {
      ElMessage.error('确认章节计划失败，请稍后重试')
    }
  } finally {
    chapterPlanConfirming.value = false
  }
}

async function saveCurrentChapterPlan() {
  const projectCode = projectStore.active?.projectCode
  const chapterNumber = chapterNum.value
  const editor = chapterPlanEditor.value
  const arc = currentChapterArc.value
  if (!projectCode || chapterNumber === null || !chapterPlan.value || !arc || !editor.summary.trim()) return

  chapterPlanUpdating.value = true
  try {
    const updatedPlan = await updateChapterPlan(projectCode, chapterNumber, {
      title: arc.title,
      summary: editor.summary.trim(),
    })
    chapterPlan.value = updatedPlan
    replaceChapterPlanInHistory(updatedPlan)
    chapterPlanDrawerVisible.value = false
    ElMessage.success('章节计划已保存')
  } catch (error) {
    if (error instanceof ApiBusinessError) {
      ElMessage.error(error.message)
    } else {
      ElMessage.error('保存章节计划失败，请稍后重试')
    }
  } finally {
    chapterPlanUpdating.value = false
  }
}

async function generateNextChapter() {
  if (nextChapterPreparing.value) return

  nextChapterPreparing.value = true
  try {
    const targetChapter = chapterNumberForArc(nextChapterArc.value, 1)
    if (targetChapter === null) return
    chapterNum.value = targetChapter
    await nextTick()
    await chapterPlanLoadPromise
  } finally {
    nextChapterPreparing.value = false
  }
}

async function generateSingleChapter() {
  const projectCode = projectStore.active?.projectCode
  const chapterNumber = chapterNum.value
  if (!projectCode || chapterNumber === null) return

  generating.value = true
  workflowStateReady.value = false
  closeGenerationEventStream()
  resetStreamingContent()
  resetWorkflowTimeline()
  generationSession.value = null
  result.value = null
  try {
    generationSession.value = await createGenerationSession(
      projectCode,
      chapterNumber,
    )
    await loadActiveGenerationSession()
    ElMessage.success('章节生成已启动')
  } catch (error) {
    workflowStateReady.value = true
    if (error instanceof ApiBusinessError) {
      workflowFailureMessage.value = error.message
    } else {
      workflowFailureMessage.value = '章节生成工作流启动失败，请稍后重试。'
    }
  } finally {
    generating.value = false
  }
}

async function stopCurrentGeneration() {
  const session = generationSession.value
  if (!session) return

  stopping.value = true
  try {
    await stopGenerationSession(session.workflowId)
    await refreshActiveGenerationSessionAndReconnect()
    ElMessage.success('章节生成已停止')
  } catch (error) {
    if (error instanceof ApiBusinessError) {
      workflowFailureMessage.value = error.message
    } else {
      workflowFailureMessage.value = '停止章节生成失败，请稍后重试。'
    }
  } finally {
    stopping.value = false
  }
}

async function resume(decision: 'PASS' | 'REVISE' | 'REVIEW' | 'ABORT') {
  const workflowId = result.value?.workflowId ?? generationSession.value?.workflowId
  if (!workflowId) return
  resuming.value = true
  try {
    if (decision === 'PASS') {
      acceptingRequested.value = true
      await acceptGenerationSession(workflowId)
    } else {
      result.value = await resumeChapter(workflowId, {
        humanDecision: decision,
        revisionInstruction: revisionInstruction.value.trim() || undefined,
      })
      workflowStatus.value = workflowStatusFromApi(result.value.status, workflowStatus.value)
    }
    revisionInstruction.value = ''
    ElMessage.success('审核完成')
  } catch (error) {
    if (decision === 'PASS') {
      acceptingRequested.value = false
    }
    if (error instanceof ApiBusinessError) {
      workflowFailureMessage.value = error.message
    } else {
      workflowFailureMessage.value = '人工审核操作失败，请稍后重试。'
    }
    if (error instanceof ApiBusinessError && error.code === 'E0008' && result.value) {
      result.value = { ...result.value, canHumanRevise: false }
    }
  } finally {
    resuming.value = false
  }
}

function goReadChapter(chapterNumber: number | null) {
  if (chapterNumber === null) return
  router.push({ path: '/read', query: { chapter: chapterNumber } })
}

function workflowStatusFromApi(status: string | undefined, fallback: WorkflowStatus = 'IDLE'): WorkflowStatus {
  if (status === 'ABORTED') return 'CANCELLED'
  if (status === 'PROCESSING') return 'DRAFTING'
  if (status && [
    'IDLE',
    'DRAFTING',
    'REVIEWING',
    'REVIEW_FAILED',
    'REVISING',
    'COMPRESSING',
    'PERSISTING',
    'WAITING_HUMAN',
    'COMPLETED',
    'FAILED',
    'CANCELLED',
  ].includes(status)) {
    return status as WorkflowStatus
  }
  return fallback
}

function workflowStageFromStatus(status: WorkflowStatus): WorkflowStageCode | '' {
  const stages: Partial<Record<WorkflowStatus, WorkflowStageCode>> = {
    DRAFTING: 'DRAFT',
    REVIEWING: 'REVIEW',
    REVIEW_FAILED: 'REVIEW',
    REVISING: 'REVISE',
    COMPRESSING: 'COMPRESSION',
    PERSISTING: 'PERSIST',
  }
  return stages[status] ?? ''
}

function chapterPlanTagType(status?: string) {
  return statusTone(status)
}

function chapterPlanHistoryStatus(plan: ChapterPlan | null) {
  if (!plan) return '无计划'
  return statusLabel(plan.status)
}
</script>

<style scoped>
.generation-page { width: 100%; height: 100%; min-width: 0; min-height: 0; display: grid; grid-template-columns: clamp(340px, 25vw, 380px) minmax(0, 1fr); overflow: hidden; background: var(--surface); color: var(--text); font-family: "Microsoft YaHei", "PingFang SC", "Noto Sans CJK SC", "Noto Sans SC", sans-serif; }
.generation-stage { grid-column: 2; grid-row: 1; min-width: 0; min-height: 0; display: flex; flex-direction: column; overflow: hidden; background: var(--surface); }
.generation-page-loading { grid-column: 1 / -1; min-height: 240px; display: flex; align-items: center; justify-content: center; gap: 8px; color: var(--text-faint); font-size: 12px; }
.chapter-plan-loading-indicator { color: var(--primary); font-size: 10px; }
.chapter-plan-drawer-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.chapter-plan-drawer-head > div { min-width: 0; }
.chapter-plan-drawer-head .meta-label { margin-bottom: 3px; }
.chapter-plan-drawer-head h2 { margin: 0; color: var(--text); font-size: 20px; line-height: 1.35; }
.chapter-plan-drawer { display: flex; flex-direction: column; gap: 22px; min-width: 0; }
.chapter-plan-drawer-context { margin: -8px 0 0; color: var(--text-faint); font-size: 12px; line-height: 1.6; }
.chapter-plan-editor-form, .chapter-plan-draft-form { min-width: 0; }
.chapter-plan-editor-form .el-form-item, .chapter-plan-draft-form .el-form-item { margin-bottom: 18px; }
.chapter-plan-ai-section { display: flex; flex-direction: column; gap: 8px; padding-top: 4px; }
.chapter-plan-ai-head, .chapter-plan-draft-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.chapter-plan-ai-head > div, .chapter-plan-draft-head > div { display: flex; align-items: baseline; gap: 8px; min-width: 0; }
.chapter-plan-ai-hint, .chapter-plan-draft-note { color: var(--text-faint); font-size: 10px; }
.chapter-plan-ai-note { margin: -2px 0 0; color: var(--text-faint); font-size: 11px; line-height: 1.5; }
.chapter-plan-draft-preview { display: flex; flex-direction: column; gap: 12px; padding: 16px; border: 1px solid var(--border); background: var(--surface2); }
.chapter-plan-draft-form .el-form-item:last-child { margin-bottom: 0; }
.chapter-plan-drawer-actions { display: flex; justify-content: flex-end; gap: 8px; padding-top: 4px; }
.generate-button { min-width: 112px; }
.stop-button { min-width: 112px; }
.primary-link { font-weight: 600; white-space: nowrap; }
.workflow-review-summary, .workflow-message-panel { width: min(var(--workbench-content-width), calc(100% - 48px)); box-sizing: border-box; margin-inline: auto; }
.workflow-review-summary { min-width: 0; padding: 14px 0 0; border-bottom: 1px solid var(--border); }
.result-section-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.result-section-head > div { display: flex; align-items: baseline; gap: 10px; min-width: 0; }
.result-section-head .meta-label { margin-bottom: 0; }
.streaming-scroll-actions { position: sticky; bottom: 12px; z-index: 1; display: flex; justify-content: flex-end; margin-top: -8px; pointer-events: none; }
.streaming-scroll-actions .el-button { pointer-events: auto; }
.detail-empty { margin: 12px 0 0; color: var(--text-faint); font-size: 12px; }
.meta-label { display: block; margin-bottom: 7px; color: var(--text-faint); font-size: 10px; font-weight: 700; }
.review-detail ul { margin: 0; padding-left: 18px; color: var(--text-muted); font-size: 12px; line-height: 1.8; }
.review-detail { margin-top: 12px; }
.review-detail li { color: var(--warning); }
.read-chapter-actions { margin-top: 14px; }
.contextual-action-bar { flex: 0 0 auto; min-width: 0; position: sticky; bottom: 0; z-index: 3; border-top: 1px solid var(--border); background: var(--surface-overlay-strong); }
.review-controls { display: flex; align-items: center; justify-content: flex-end; gap: 14px; margin-top: 0; padding: 16px 0; }
.review-control-buttons { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px; }
.review-actions { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 14px 18px; margin-top: 0; padding: 18px 0; }
.review-actions strong { font-size: 12px; }.review-actions span { color: var(--text-faint); font-size: 10px; }
.revision-instruction { grid-column: 1 / -1; min-width: 0; display: flex; flex-direction: column; gap: 6px; }
.revision-instruction label { color: var(--text-muted); font-size: 11px; font-weight: 600; }
.revision-instruction span { line-height: 1.4; }
.revision-limit-notice { grid-column: 1 / -1; display: flex; flex-direction: column; gap: 3px; padding: 9px 10px; border-left: 3px solid var(--warning-border); background: var(--warning-surface); }
.revision-limit-notice strong { color: var(--warning); }
.revision-limit-notice span { line-height: 1.4; }
.review-decisions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px; }
@media (max-width: 767px) {
  .generation-page { display: flex; flex-direction: column; overflow: hidden; }
  .generation-stage { flex: 1; min-height: 0; }
  .review-controls { align-items: stretch; flex-direction: column; }
  .review-control-buttons { justify-content: flex-start; }
}
@media (max-width: 680px) {
   .generate-button, .stop-button { width: 100%; margin-top: 4px; }
   .review-actions { grid-template-columns: minmax(0, 1fr); }
   .review-decisions { justify-content: flex-start; }
}
</style>
