import http from './http'
import type {
  ChapterPlan,
  ConfirmChapterPlanRequest,
  ConfirmNextOutlineRequest,
  ConfirmArcRegenerationRequest,
  ConfirmRootOutlineRequest,
  CreateOutlineNodeRequest,
  GenerateChapterPlanRequest,
  GenerateNextOutlineRequest,
  GenerateArcRegenerationRequest,
  GenerateRootOutlineRequest,
  OutlineNode,
  PlanningDraftResponse,
  ReorderOutlineNodeRequest,
  UpdateChapterPlanRequest,
  UpdateOutlineNodeRequest,
} from '../types'

const base = (projectCode: string) => `/v1/novels/projects/${projectCode}`

export const listOutlineNodes = (projectCode: string) =>
  http.get<any, OutlineNode[]>(`${base(projectCode)}/outlines/tree`)

export const createOutlineNode = (projectCode: string, data: CreateOutlineNodeRequest) =>
  http.post<any, OutlineNode>(`${base(projectCode)}/outlines/nodes`, data)

export const updateOutlineNode = (
  projectCode: string,
  nodeCode: string,
  data: UpdateOutlineNodeRequest,
) => http.post<any, OutlineNode>(`${base(projectCode)}/outlines/nodes/${nodeCode}`, data)

export const deleteOutlineNode = (projectCode: string, nodeCode: string) =>
  http.post<any, void>(`${base(projectCode)}/outlines/nodes/${nodeCode}/delete`, {})

export const reorderOutlineNode = (
  projectCode: string,
  nodeCode: string,
  data: ReorderOutlineNodeRequest,
) => http.post<any, OutlineNode>(`${base(projectCode)}/outlines/nodes/${nodeCode}/reorder`, data)

export const listChapterPlans = (projectCode: string) =>
  http.get<any, ChapterPlan[]>(`${base(projectCode)}/chapter-plans`)

export const updateChapterPlan = (
  projectCode: string,
  chapterNumber: number,
  data: UpdateChapterPlanRequest,
) => http.post<any, ChapterPlan>(`${base(projectCode)}/chapter-plans/${chapterNumber}`, data)

export const generateRootOutline = (projectCode: string, data: GenerateRootOutlineRequest) =>
  http.post<any, PlanningDraftResponse>(`${base(projectCode)}/outlines/root/generate`, data)

export const confirmRootOutline = (projectCode: string, data: ConfirmRootOutlineRequest) =>
  http.post<any, void>(`${base(projectCode)}/outlines/root/confirm`, data)

export const createNextVolume = (projectCode: string, bookNodeCode: string) =>
  http.post<any, OutlineNode>(
    `${base(projectCode)}/outlines/${bookNodeCode}/volumes/create`,
    {},
  )

export const generateNextOutline = (
  projectCode: string,
  parentNodeCode: string,
  data: GenerateNextOutlineRequest,
) => http.post<any, PlanningDraftResponse>(
  `${base(projectCode)}/outlines/${parentNodeCode}/children/generate`,
  data,
)

export const confirmNextOutline = (
  projectCode: string,
  parentNodeCode: string,
  data: ConfirmNextOutlineRequest,
) => http.post<any, void>(
  `${base(projectCode)}/outlines/${parentNodeCode}/children/confirm`,
  data,
)

export const generateVolumeOutline = (
  projectCode: string,
  volumeNodeCode: string,
  data: GenerateNextOutlineRequest,
) => http.post<any, PlanningDraftResponse<OutlineNode>>(
  `${base(projectCode)}/outlines/volumes/${volumeNodeCode}/generate`,
  data,
)

export const confirmVolumeOutline = (
  projectCode: string,
  volumeNodeCode: string,
  data: ConfirmNextOutlineRequest,
) => http.post<any, void>(
  `${base(projectCode)}/outlines/volumes/${volumeNodeCode}/confirm`,
  data,
)

export const generateArcRegeneration = (
  projectCode: string,
  nodeCode: string,
  data: GenerateArcRegenerationRequest,
) => http.post<any, PlanningDraftResponse<OutlineNode>>(
  `${base(projectCode)}/outlines/nodes/${nodeCode}/regenerate`,
  data,
)

export const confirmArcRegeneration = (
  projectCode: string,
  nodeCode: string,
  data: ConfirmArcRegenerationRequest,
) => http.post<any, void>(
  `${base(projectCode)}/outlines/nodes/${nodeCode}/regenerate/confirm`,
  data,
)

export const generateChapterPlan = (
  projectCode: string,
  chapterNumber: number,
  data: GenerateChapterPlanRequest,
) =>
  http.post<any, PlanningDraftResponse<ChapterPlan>>(
    `${base(projectCode)}/chapter-plans/${chapterNumber}/generate`,
    data,
  )

export const confirmChapterPlan = (
  projectCode: string,
  chapterNumber: number,
  data: ConfirmChapterPlanRequest,
) => http.post<any, void>(
  `${base(projectCode)}/chapter-plans/${chapterNumber}/confirm`,
  data,
)
