import http from './http'
import type {
  GenerateChapterResponse,
  GenerationSessionResponse,
  GenerationSessionSnapshotResponse,
  GenerationSessionEvent,
  GenerationSessionEventType,
  ResumeChapterRequest,
} from '../types'

export const createGenerationSession = (projectCode: string, chapterNumber: number) =>
  http.post<any, GenerationSessionResponse>(
    `/v1/novels/projects/${projectCode}/chapters/${chapterNumber}/generation-sessions`
  )

export const getActiveGenerationSession = (projectCode: string, chapterNumber: number) =>
  http.get<any, GenerationSessionSnapshotResponse | null>(
    `/v1/novels/projects/${projectCode}/chapters/${chapterNumber}/generation-sessions`
  )

export const stopGenerationSession = (workflowId: string) =>
  http.post<any, void>(
    `/v1/novels/generation-sessions/${workflowId}/commands`,
    { command: 'STOP' },
  )

export const acceptGenerationSession = (workflowId: string) =>
  http.post<any, void>(
    `/v1/novels/generation-sessions/${workflowId}/commands`,
    { command: 'ACCEPT' },
  )

const generationSessionEventTypes: GenerationSessionEventType[] = [
  'GENERATION_STARTED',
  'DRAFT_STARTED',
  'DRAFT_CHUNK',
  'DRAFT_COMPLETED',
  'REVIEW_STARTED',
  'REVIEW_COMPLETED',
  'REVISION_STARTED',
  'REVISION_COMPLETED',
  'REVIEW_FAILED',
  'HUMAN_REVIEW_REQUIRED',
  'COMPRESSION_STARTED',
  'COMPRESSION_COMPLETED',
  'PERSIST_STARTED',
  'PERSIST_COMPLETED',
  'GENERATION_COMPLETED',
  'GENERATION_ABORTED',
  'GENERATION_FAILED',
  'GENERATION_CANCELLED',
]

export const subscribeGenerationSessionEvents = (
  workflowId: string,
  onEvent: (event: GenerationSessionEvent) => void,
) => {
  const source = new EventSource(
    `/api/v1/novels/generation-sessions/${encodeURIComponent(workflowId)}/events`,
  )
  source.onopen = () => {
    if (import.meta.env.DEV) {
      console.debug('SSE connected')
    }
  }
  const handleEvent = (event: Event) => {
    const message = event as MessageEvent<string>
    let payload: Partial<GenerationSessionEvent> = {}
    try {
      payload = JSON.parse(message.data) as Partial<GenerationSessionEvent>
    } catch {
      payload = {
        content: message.type === 'DRAFT_CHUNK' && message.data !== message.type
          ? message.data
          : null,
      }
    }

    const type = payload.type ?? message.type
    if (!generationSessionEventTypes.includes(type as GenerationSessionEventType)) return
    onEvent({
      type: type as GenerationSessionEventType,
      content: typeof payload.content === 'string' ? payload.content : null,
      reviewIssues: Array.isArray(payload.reviewIssues) ? payload.reviewIssues : null,
    })
  }

  generationSessionEventTypes.forEach((eventType) => {
    source.addEventListener(eventType, handleEvent)
  })
  return source
}

export const resumeChapter = (workflowId: string, data: ResumeChapterRequest) =>
  http.post<any, GenerateChapterResponse>(`/v1/novels/chapters/${workflowId}/resume`, data)
