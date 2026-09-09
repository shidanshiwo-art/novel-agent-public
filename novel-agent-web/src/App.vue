<template>
  <div v-if="isProjectEntry" class="entry-shell">
    <header class="entry-brand">
      <span class="brand-mark">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" /></svg>
      </span>
      <span>小说工坊</span>
    </header>
    <main class="entry-main">
      <div class="entry-copy">
        <span>AI NOVEL STUDIO</span>
        <h1>开始你的故事</h1>
        <p>创建一部新作品，或加载已有项目继续写作。</p>
      </div>
      <router-view />
    </main>
  </div>

  <div v-else class="studio-shell">
    <header class="studio-topbar">
      <el-button text class="studio-brand" title="返回项目入口" @click="leaveProject">
        <span class="brand-mark compact">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" /></svg>
        </span>
        <span class="studio-project">
          <strong>{{ projectStore.active?.title }}</strong>
          <small>{{ projectStore.active?.projectCode }}</small>
        </span>
      </el-button>

      <div class="studio-status">
        <span class="sync-dot" />已载入
        <el-button text type="primary" class="studio-settings-button" title="项目设置" @click="openProjectSettings">
          项目设置
        </el-button>
        <el-button text type="primary" class="studio-switch-button" title="切换作品" @click="leaveProject">切换作品</el-button>
      </div>
    </header>

    <div class="studio-body">
      <aside class="studio-sidebar" aria-label="作品工具">
        <nav>
          <router-link v-for="item in studioNav" :key="item.path" :to="item.path" :title="item.label">
            <span v-html="item.icon" /><span>{{ item.label }}</span>
          </router-link>
        </nav>
      </aside>
      <main
        class="studio-main"
        :class="{
          'reading-mode': route.path === '/read',
          'generate-mode': route.path === '/generate',
          'workbench-mode': workbenchRoutes.includes(route.path),
        }"
      >
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <div class="studio-route-view" :key="route.fullPath">
              <component :is="Component" />
            </div>
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useProjectStore } from './stores/project'

const route = useRoute()
const router = useRouter()
const projectStore = useProjectStore()
const isProjectEntry = computed(() => route.path === '/project')
const workbenchRoutes = ['/read', '/outline', '/generate']

const studioNav = [
  { path: '/read', label: '章节', icon: '<svg viewBox="0 0 24 24"><path d="M4 5h16M4 12h16M4 19h16"/></svg>' },
  { path: '/setup', label: '设定', icon: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="3"/><path d="M19.1 4.9a10 10 0 0 1 0 14.2M4.9 4.9a10 10 0 0 0 0 14.2"/></svg>' },
  { path: '/characters', label: '角色', icon: '<svg viewBox="0 0 24 24"><circle cx="12" cy="8" r="4"/><path d="M5 21a7 7 0 0 1 14 0"/></svg>' },
  { path: '/outline', label: '大纲', icon: '<svg viewBox="0 0 24 24"><path d="M5 4h14v16H5zM9 8h6M9 12h6M9 16h4"/></svg>' },
  { path: '/generate', label: '生成', icon: '<svg viewBox="0 0 24 24"><path d="M8 5v14l11-7z"/></svg>' },
]

function leaveProject() {
  projectStore.clearProject()
  router.push('/project')
}

function openProjectSettings() {
  router.push('/project')
}
</script>

<style scoped>
.entry-shell { min-height: 100vh; background: var(--app-bg); color: var(--text-primary); }
.entry-brand { height: 66px; display: flex; align-items: center; gap: 10px; padding: 0 32px; border-bottom: 1px solid var(--border); background: var(--surface); font-size: 16px; font-weight: 700; }
.brand-mark { width: 34px; height: 34px; display: grid; place-items: center; border-radius: var(--radius-md); background: var(--primary); color: var(--surface); }
.brand-mark.compact { width: 30px; height: 30px; }
.brand-mark svg { width: 18px; height: 18px; fill: none; stroke: currentColor; stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; }
.entry-main { width: 100%; min-width: 0; margin: 0 auto; padding: 64px clamp(20px, 5vw, 72px) 80px; }
.entry-copy { margin-bottom: 28px; text-align: center; }
.entry-copy span { font-size: 10px; font-weight: 700; color: var(--primary); letter-spacing: .18em; }
.entry-copy h1 { margin-top: 8px; font-size: 32px; line-height: 1.3; letter-spacing: 0; }
.entry-copy p { margin-top: 8px; font-size: 14px; color: var(--text-faint); }

.studio-shell { height: 100vh; min-height: 0; display: flex; flex-direction: column; overflow: hidden; background: var(--bg); }
.studio-topbar { height: var(--app-header-height); flex-shrink: 0; display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 0 20px; border-bottom: 1px solid var(--border); background: var(--surface-overlay); }
.studio-brand { min-width: 0; display: flex; align-items: center; gap: 10px; margin: 0; padding: 0; color: inherit; text-align: left; }
.studio-project { min-width: 0; display: flex; flex-direction: column; }
.studio-project strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; }
.studio-project small { font-size: 10px; color: var(--text-faint); }
.studio-status { justify-self: end; display: flex; align-items: center; gap: 7px; font-size: 11px; color: var(--text-faint); }
.studio-settings-button { margin-left: 8px !important; }
.sync-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--success); }
.studio-switch-button { margin-left: 8px !important; }
.studio-body { flex: 1; height: 100%; min-height: 0; display: flex; }
.studio-sidebar { width: var(--sidebar-width); flex-shrink: 0; display: flex; justify-content: center; padding: 18px 10px; border-right: 1px solid var(--border); background: var(--surface); }
.studio-sidebar nav { display: flex; flex-direction: column; gap: 8px; width: 100%; }
.studio-sidebar a { min-height: 58px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 5px; border-radius: 8px; color: var(--text-muted); text-decoration: none; font-size: 10px; }
.studio-sidebar a:hover { background: var(--surface2); color: var(--text); }
.studio-sidebar a.router-link-active { background: var(--primary-dim); color: var(--primary); font-weight: 600; }
.studio-sidebar :deep(svg) { width: 19px; height: 19px; fill: none; stroke: currentColor; stroke-width: 1.9; stroke-linecap: round; stroke-linejoin: round; }
.studio-main { flex: 1; min-width: 0; min-height: 0; height: 100%; display: flex; flex-direction: column; overflow: auto; padding: 24px 28px; }
.studio-main.generate-mode { overflow: hidden; }
.studio-main.reading-mode { padding: 0; overflow: hidden; }
.studio-main.workbench-mode { padding: 0; overflow: hidden; }
.studio-route-view { flex: 1; min-width: 0; min-height: 0; height: 100%; }
.fade-enter-active, .fade-leave-active { transition: opacity .12s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
@media (max-width: 820px) {
  .studio-topbar { gap: 10px; padding: 0 12px; }
  .studio-status { display: none; }
  .studio-sidebar { width: 64px; padding-inline: 7px; }
}
@media (max-width: 560px) {
  .entry-main { padding-top: 36px; }
  .entry-copy h1 { font-size: 26px; }
  .studio-project small { display: none; }
  .studio-topbar { height: 56px; }
  .studio-main { padding: 16px 12px; }
}
</style>
