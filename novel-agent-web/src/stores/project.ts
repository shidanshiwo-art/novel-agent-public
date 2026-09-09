import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { NovelProjectResponse } from '../types'

const ACTIVE_PROJECT_KEY = 'novel-agent.active-project'

function restoreActiveProject(): NovelProjectResponse | null {
  const stored = sessionStorage.getItem(ACTIVE_PROJECT_KEY)
  if (!stored) return null

  try {
    return JSON.parse(stored) as NovelProjectResponse
  } catch {
    sessionStorage.removeItem(ACTIVE_PROJECT_KEY)
    return null
  }
}

export const useProjectStore = defineStore('project', () => {
  const active = ref<NovelProjectResponse | null>(restoreActiveProject())

  function setActive(p: NovelProjectResponse) {
    active.value = p
    sessionStorage.setItem(ACTIVE_PROJECT_KEY, JSON.stringify(p))
  }

  function clearProject() {
    active.value = null
    sessionStorage.removeItem(ACTIVE_PROJECT_KEY)
  }

  return { active, setActive, clearProject }
})
