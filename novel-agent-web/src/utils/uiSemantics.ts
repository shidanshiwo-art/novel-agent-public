export type StatusCategory = 'pending' | 'progress' | 'completed' | 'error'
export type StatusTone = 'primary' | 'success' | 'warning' | 'danger'

type SemanticValue = string | null | undefined

const STATUS_LABELS: Record<string, string> = {
  IDLE: '待开始',
  READY: '可生成',
  COMPLETED: '已完成',
  DRAFT: '草稿',
  PLANNED: '已规划',
  UNPLANNED: '待规划',
  REVIEWING: '正在检查',
  WAITING_HUMAN: '等待确认',
  REVIEW_FAILED: '检查未完成',
  DRAFTING: '正文生成中',
  REVISING: '修改中',
  COMPRESSING: '更新章节记忆中',
  PERSISTING: '保存中',
  PROCESSING: '处理中',
  ACTIVE: '进行中',
  FAILED: '处理失败',
  CANCELLED: '已停止',
  ABORTED: '已放弃',
  FINALIZED: '已定稿',
  DIRTY: '正文已修改',
}

const STATUS_CATEGORIES: Record<string, StatusCategory> = {
  IDLE: 'pending',
  READY: 'pending',
  DRAFT: 'pending',
  PLANNED: 'completed',
  UNPLANNED: 'pending',
  WAITING_HUMAN: 'pending',
  REVIEWING: 'progress',
  DRAFTING: 'progress',
  REVISING: 'progress',
  COMPRESSING: 'progress',
  PERSISTING: 'progress',
  PROCESSING: 'progress',
  ACTIVE: 'progress',
  COMPLETED: 'completed',
  FINALIZED: 'completed',
  REVIEW_FAILED: 'error',
  FAILED: 'error',
  CANCELLED: 'error',
  ABORTED: 'error',
  DIRTY: 'pending',
}

const STATUS_DESCRIPTIONS: Record<string, string> = {
  IDLE: '尚未开始本章生成。',
  READY: '章节计划已确认，可以开始生成正文。',
  COMPLETED: '本章正文已完成，可以查看正文。',
  DRAFT: '当前内容仍可编辑和确认。',
  PLANNED: '当前卷规划已完成，可以继续规划章节。',
  UNPLANNED: '请先完成当前卷规划。',
  REVIEWING: '正在检查正文质量，请稍候。',
  WAITING_HUMAN: '自动检查完成，请确认是否采用当前正文。',
  REVIEW_FAILED: '自动检查未完成，正文仍可恢复处理。',
  DRAFTING: '正文正在生成中。',
  REVISING: '正在根据审阅意见修改正文。',
  COMPRESSING: '正在更新章节记忆。',
  PERSISTING: '正在保存章节正文。',
  PROCESSING: '正在处理当前章节。',
  ACTIVE: '当前项目可以继续创作。',
  FAILED: '本次处理未完成，可以稍后重试。',
  CANCELLED: '本次处理已停止。',
  ABORTED: '本次处理已放弃。',
  FINALIZED: '正文已定稿。',
  DIRTY: '正文已修改，尚未重新保存。',
}

const DOMAIN_LABELS: Record<string, string> = {
  BOOK: '故事总纲',
  VOLUME: '卷',
  ARC: '章纲',
  DRAFT: '草稿',
  ChapterPlan: '章节计划',
  ChapterMemory: '章节记忆',
  REVIEW: '审稿',
  REVISION: '修改',
  REVISE: '修改',
  COMPRESSION: '更新章节记忆',
  PERSIST: '保存章节',
}

const BACKEND_TERMS: Array<[RegExp, string]> = [
  [/ChapterPlan\.title/g, '章节计划标题'],
  [/ARC\.title/g, '章纲标题'],
  [/parentNodeCode/g, '上级结构编号'],
  [/nodeCode/g, '结构编号'],
  [/startChapter/g, '起始章节'],
  [/endChapter/g, '结束章节'],
  [/DRAFT_COMPLETED/g, '草稿已完成'],
  [/REVIEW_COMPLETED/g, '审稿已完成'],
  [/REVISION_COMPLETED/g, '修改已完成'],
  [/COMPRESSION_STARTED/g, '更新章节记忆中'],
  [/COMPRESSION_COMPLETED/g, '章节记忆已更新'],
  [/PERSIST_STARTED/g, '保存中'],
  [/PERSIST_COMPLETED/g, '保存完成'],
  [/GENERATION_COMPLETED/g, '生成已完成'],
  [/GENERATION_STARTED/g, '开始生成'],
  [/HUMAN_REVIEW_REQUIRED/g, '等待确认'],
  [/REVIEW_FAILED/g, '检查未完成'],
  [/WAITING_HUMAN/g, '等待确认'],
  [/COMPRESSING/g, '更新章节记忆中'],
  [/PERSISTING/g, '保存中'],
  [/PROCESSING/g, '处理中'],
  [/FINALIZED/g, '已定稿'],
  [/CANCELLED/g, '已停止'],
  [/ABORTED/g, '已放弃'],
  [/FAILED/g, '处理失败'],
  [/ACTIVE/g, '进行中'],
  [/IDLE/g, '待开始'],
  [/DIRTY/g, '正文已修改'],
  [/DRAFTING/g, '正文生成中'],
  [/REVIEWING/g, '正在检查'],
  [/REVISING/g, '修改中'],
  [/COMPLETED/g, '已完成'],
  [/UNPLANNED/g, '待规划'],
  [/PLANNED/g, '已规划'],
  [/READY/g, '可生成'],
  [/ChapterPlan/g, '章节计划'],
  [/ChapterMemory/g, '章节记忆'],
  [/COMPRESSION/g, '更新章节记忆'],
  [/PERSIST/g, '保存章节'],
  [/REVISION/g, '修改'],
  [/REVIEW/g, '审稿'],
  [/DRAFT/g, '草稿'],
  [/BOOK/g, '故事总纲'],
  [/VOLUME/g, '卷'],
  [/ARC/g, '章纲'],
]

function semanticKey(value: SemanticValue) {
  return typeof value === 'string' ? value : ''
}

export function statusLabel(status: SemanticValue) {
  return STATUS_LABELS[semanticKey(status)] ?? '状态更新中'
}

export function statusCategory(status: SemanticValue): StatusCategory {
  return STATUS_CATEGORIES[semanticKey(status)] ?? 'pending'
}

export function statusTone(status: SemanticValue): StatusTone {
  const tones: Record<StatusCategory, StatusTone> = {
    pending: 'warning',
    progress: 'primary',
    completed: 'success',
    error: 'danger',
  }
  return tones[statusCategory(status)]
}

export function statusDescription(status: SemanticValue) {
  return STATUS_DESCRIPTIONS[semanticKey(status)] ?? '当前状态正在更新。'
}

export function domainLabel(value: SemanticValue) {
  return DOMAIN_LABELS[semanticKey(value)] ?? '内容'
}

export function userMessage(message: SemanticValue) {
  if (!message) return ''
  return BACKEND_TERMS.reduce((result, [term, label]) => result.replace(term, label), message)
}
