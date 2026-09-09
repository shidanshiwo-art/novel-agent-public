import { createRouter, createWebHashHistory } from 'vue-router'
import ProjectView from '../views/ProjectView.vue'
import SetupView from '../views/SetupView.vue'
import OutlineView from '../views/OutlineView.vue'
import GenerateView from '../views/GenerateView.vue'
import ReadView from '../views/ReadView.vue'

const ACTIVE_PROJECT_KEY = 'novel-agent.active-project'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', redirect: '/project' },
    { path: '/project', component: ProjectView },
    { path: '/setup', component: SetupView, meta: { requiresProject: true } },
    { path: '/characters', component: SetupView, meta: { requiresProject: true } },
    { path: '/outline', component: OutlineView, meta: { requiresProject: true } },
    { path: '/planning', component: OutlineView, meta: { requiresProject: true } },
    { path: '/generate', component: GenerateView, meta: { requiresProject: true } },
    { path: '/read', component: ReadView, meta: { requiresProject: true } },
  ],
})

router.beforeEach((to) => {
  if (to.meta.requiresProject && !sessionStorage.getItem(ACTIVE_PROJECT_KEY)) {
    return { path: '/project', query: { redirect: to.fullPath } }
  }
})

export default router
