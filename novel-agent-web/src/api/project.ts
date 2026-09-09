import http from './http'
import type {
  CreateNovelProjectRequest,
  UpdateTargetChapterCountRequest,
  NovelProjectResponse,
  SaveStoryBibleRequest,
  StoryBibleResponse,
  ConfirmStoryBibleRequest,
  GenerateStoryBibleRequest,
  GenerateCharacterRequest,
  PlanningDraftResponse,
  StoryBibleDraft,
  CharacterDraftList,
  ConfirmCharactersRequest,
  AddStoryCharacterRequest,
  UpdateStoryCharacterRequest,
  StoryCharacterResponse,
  GeneratedChapterResponse,
  OverwriteChapterContentRequest,
  ChapterSearchResult,
  VolumeChapterGroupResponse,
} from '../types'

export const createProject = (data: CreateNovelProjectRequest) =>
  http.post<any, NovelProjectResponse>('/v1/novels/projects', data)

export const updateTargetChapterCount = (
  projectCode: string,
  data: UpdateTargetChapterCountRequest,
) => http.post<any, NovelProjectResponse>(
  `/v1/novels/projects/${projectCode}/target-chapter-count`, data,
)

export const getProject = (projectCode: string) =>
  http.get<any, NovelProjectResponse>(`/v1/novels/projects/${projectCode}`)

export const saveBible = (projectCode: string, data: SaveStoryBibleRequest) =>
  http.post<any, StoryBibleResponse>(`/v1/novels/projects/${projectCode}/bible`, data)

export const getBible = (projectCode: string) =>
  http.get<any, StoryBibleResponse | null>(`/v1/novels/projects/${projectCode}/bible`)

export const generateStoryBible = (projectCode: string, data: GenerateStoryBibleRequest) =>
  http.post<any, PlanningDraftResponse<StoryBibleDraft>>(
    `/v1/novels/projects/${projectCode}/bible/generate`, data,
  )

export const generateCharacters = (projectCode: string, data: GenerateCharacterRequest) =>
  http.post<any, PlanningDraftResponse<CharacterDraftList>>(
    `/v1/novels/projects/${projectCode}/characters/generate`, data,
  )

export const confirmCharacters = (projectCode: string, data: ConfirmCharactersRequest) =>
  http.post<any, StoryCharacterResponse[]>(
    `/v1/novels/projects/${projectCode}/characters/confirm`, data,
  )

export const discardCharacterDraft = (projectCode: string, draftId: string) =>
  http.post<any, void>(
    `/v1/novels/projects/${projectCode}/characters/discard`, { draftId },
  )

export const confirmStoryBible = (projectCode: string, data: ConfirmStoryBibleRequest) =>
  http.post<any, StoryBibleResponse>(`/v1/novels/projects/${projectCode}/bible/confirm`, data)

export const searchChapters = (projectCode: string, keyword: string) =>
  http.post<any, ChapterSearchResult[]>(`/v1/novels/projects/${projectCode}/chapters/search`, { keyword })

export const addCharacter = (projectCode: string, data: AddStoryCharacterRequest) =>
  http.post<any, StoryCharacterResponse>(`/v1/novels/projects/${projectCode}/characters`, data)

export const listCharacters = (projectCode: string) =>
  http.get<any, StoryCharacterResponse[]>(`/v1/novels/projects/${projectCode}/characters`)

export const updateCharacter = (projectCode: string, characterCode: string, data: UpdateStoryCharacterRequest) =>
  http.post<any, StoryCharacterResponse>(`/v1/novels/projects/${projectCode}/characters/${characterCode}`, data)

export const deleteCharacter = (projectCode: string, characterCode: string) =>
  http.delete<any, void>(`/v1/novels/projects/${projectCode}/characters/${characterCode}`)

export const getChapter = (projectCode: string, chapterNumber: number) =>
  http.get<any, GeneratedChapterResponse>(
    `/v1/novels/projects/${projectCode}/chapters/${chapterNumber}`
  )

export const listChapters = (projectCode: string) =>
  http.get<any, GeneratedChapterResponse[]>(
    `/v1/novels/projects/${projectCode}/chapters`
  )

export const overwriteChapterContent = (projectCode: string, chapterNumber: number, data: OverwriteChapterContentRequest) =>
  http.post<any, GeneratedChapterResponse>(
    `/v1/novels/projects/${projectCode}/chapters/${chapterNumber}`,
    data
  )
export const resyncChapterDerivedData = (projectCode: string, chapterNumber: number) =>
  http.post<any, GeneratedChapterResponse>(
    `/v1/novels/projects/${projectCode}/chapters/${chapterNumber}/resync`
  )
export const deleteChapter = (projectCode: string, chapterNumber: number) => http.delete<any, void>(`/v1/novels/projects/${projectCode}/chapters/${chapterNumber}`)
export const listChaptersByVolume = (projectCode: string) => http.post<any, VolumeChapterGroupResponse[]>(`/v1/novels/projects/${projectCode}/chapters/list`)
