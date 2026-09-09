// ——— 通用响应 ———
export interface ApiResponse<T> {
  code: string
  info: string
  data: T
}

// ——— 项目 ———
export interface CreateNovelProjectRequest {
  projectCode: string
  title: string
  genre: string
  targetChapterCount: number
  wordsPerChapter: number
}

export interface UpdateTargetChapterCountRequest {
  targetChapterCount: number
}

export interface NovelProjectResponse {
  projectCode: string
  title: string
  genre: string
  targetChapterCount: number
  wordsPerChapter: number
  currentChapterNumber: number | null
  status: string
}

// ——— 世界观 ———
export interface SaveStoryBibleRequest {
  oneSentencePremise: string
  coreTheme: string
  mainConflict: string
  endingDirection: string
  worldBackground: string
  powerSystemJson: string
  hardRulesJson: string
  styleGuide: string
  status?: string
}

export interface StoryBibleResponse extends SaveStoryBibleRequest {
  status: string
}

export interface PowerSystemDraft {
  name: string
  description: string
  levels: string
  supplement: string
}

export interface StoryBibleDraft {
  oneSentencePremise: string
  coreTheme: string
  mainConflict: string
  endingDirection: string
  worldBackground: string
  powerSystem: PowerSystemDraft | null
  hardRules: string[]
  styleGuide: string
}

export interface GenerateStoryBibleRequest {
  requirement: string
}

export interface GenerateCharacterRequest {
  preferredCount: number
  requirement: string
}

export interface CharacterDraft {
  name: string
  role: string
  gender: string
  ageDescription?: string
  appearance?: string
  personality?: string
  backgroundStory?: string
  note?: string
}

export interface CharacterDraftList {
  characters: CharacterDraft[]
}

export interface ConfirmCharactersRequest {
  draftId: string
  characters: CharacterDraft[]
}

export interface ConfirmStoryBibleRequest {
  draftId: string
  oneSentencePremise: string
  coreTheme: string
  mainConflict: string
  endingDirection: string
  worldBackground: string
  powerSystemJson: string
  hardRulesJson: string
  styleGuide: string
}

export interface ChapterSearchResult {
  chapterNumber: number
  title: string | null
  snippet: string
  matchCount: number
}

// ——— 大纲 ———
// ——— 角色 ———
export interface AddStoryCharacterRequest {
  name: string
  roleType: string
  gender: string
  ageDescription?: string
  appearance?: string
  personality?: string
  backgroundStory?: string
  note?: string
}

export interface UpdateStoryCharacterRequest {
  name: string
  roleType: string
  gender: string
  ageDescription?: string
  appearance?: string
  personality?: string
  backgroundStory?: string
  note?: string
}

export interface StoryCharacterResponse {
  characterCode: string
  name: string
  roleType: string
  gender: string
  ageDescription?: string
  appearance?: string
  personality?: string
  backgroundStory?: string
  note?: string
  currentStateJson?: string
  lifeStatus: string
  status: string
  warning?: string | null
}

// ——— 章节生成 ———
export type WorkflowStatus =
  | 'IDLE'
  | 'DRAFTING'
  | 'REVIEWING'
  | 'REVIEW_FAILED'
  | 'REVISING'
  | 'COMPRESSING'
  | 'PERSISTING'
  | 'WAITING_HUMAN'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED'

export interface GenerateChapterResponse {
  workflowId: string
  status: string
  projectId: string
  chapterNumber: number
  content: string | null
  reviewIssues: string[]
  completedStages: string[]
  canHumanRevise: boolean
}

export interface GenerationSessionResponse {
  workflowId: string
}

export interface GenerationSessionSnapshotResponse extends GenerationSessionResponse {
  chapterNumber: number
  status: string
  currentNode: string
  accumulatedContent: string
  completedStages: string[]
  reviewIssues: string[]
  failureMessage: string
}

export type GenerationSessionEventType =
  | 'GENERATION_STARTED'
  | 'DRAFT_STARTED'
  | 'DRAFT_CHUNK'
  | 'DRAFT_COMPLETED'
  | 'REVIEW_STARTED'
  | 'REVIEW_COMPLETED'
  | 'REVISION_STARTED'
  | 'REVISION_COMPLETED'
  | 'REVIEW_FAILED'
  | 'HUMAN_REVIEW_REQUIRED'
  | 'COMPRESSION_STARTED'
  | 'COMPRESSION_COMPLETED'
  | 'PERSIST_STARTED'
  | 'PERSIST_COMPLETED'
  | 'GENERATION_COMPLETED'
  | 'GENERATION_ABORTED'
  | 'GENERATION_FAILED'
  | 'GENERATION_CANCELLED'

export interface GenerationSessionEvent {
  type: GenerationSessionEventType
  content: string | null
  reviewIssues?: string[] | null
}

export interface ResumeChapterRequest {
  humanDecision: 'PASS' | 'REVISE' | 'REVIEW' | 'ABORT'
  revisionInstruction?: string
}

// ——— 章节覆写 ———
export interface OverwriteChapterContentRequest {
  title: string
  content: string
}

// ——— 章节阅读 ———
export interface GeneratedChapterResponse {
  chapterNumber: number
  title: string
  content: string
  wordCount: number
  status: string
}
export interface VolumeChapterGroupResponse { volumeCode: string; sequenceNo?: number; title: string; startChapter?: number; endChapter?: number; status?: string; chapters: GeneratedChapterResponse[] }

// ——— Planning ———
export type OutlineNodeKind = 'BOOK' | 'VOLUME' | 'ARC'

export interface OutlineNode {
  nodeCode: string
  parentNodeCode?: string | null
  nodeKind: OutlineNodeKind
  sequenceNo: number
  title: string
  summary: string
  startChapter?: number | null
  endChapter?: number | null
  status: string
}

export interface ChapterPlan {
  chapterNumber: number
  outlineNodeCode: string
  title: string
  summary: string
  status: string
}

export interface CreateOutlineNodeRequest {
  parentNodeCode: string
  title: string
  summary: string
  startChapter?: number
  endChapter?: number
}

export interface UpdateOutlineNodeRequest {
  parentNodeCode?: string | null
  nodeKind: OutlineNodeKind
  sequenceNo: number
  title: string
  summary: string
  startChapter?: number | null
  endChapter?: number | null
  status: string
}

export interface ReorderOutlineNodeRequest {
  targetSequence: number
}

export interface UpdateChapterPlanRequest {
  title: string
  summary: string
}

export interface GenerateRootOutlineRequest {
  requirement: string
}

export interface ConfirmRootOutlineRequest {
  draftId: string
  summary: string
}

export interface GenerateNextOutlineRequest {
  requirement?: string
}

export interface ConfirmNextOutlineRequest {
  draftId: string
  title: string
  summary: string
}

export interface GenerateArcRegenerationRequest {
  requirement?: string
}

export interface ConfirmArcRegenerationRequest {
  draftId: string
  title: string
  summary: string
}

export interface GenerateChapterPlanRequest {
  requirement?: string
}

export interface ConfirmChapterPlanRequest {
  draftId: string
  title: string
  summary: string
}

export interface PlanningDraftResponse<T = unknown> {
  draftId: string
  draftType: string
  payload: T
  expiresAt: string
}
