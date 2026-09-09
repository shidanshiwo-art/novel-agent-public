<template>
  <div class="outline-workbench workbench-shell">
    <aside class="outline-directory">
      <header class="directory-head">
        <div>
          <h1>故事大纲</h1>
          <p>按故事进度组织内容，逐步推进每一卷和每一章。</p>
        </div>
        <div class="directory-head-actions">
          <el-button text :loading="loading" @click="refreshWorkspace">刷新</el-button>
        </div>
      </header>

      <div class="directory-search">
        <el-input
          v-model="keyword"
          clearable
          :prefix-icon="Search"
          placeholder="搜索大纲..."
          aria-label="搜索大纲节点"
        />
      </div>

      <div class="directory-meta">
        <span>故事结构</span>
        <span>{{ nodeCount }} 个节点</span>
      </div>

      <div class="outline-tree" v-loading="loading" aria-label="大纲树">
        <el-skeleton v-if="loading" :rows="7" animated />

        <el-tree
          v-else-if="hasBook"
          ref="treeRef"
          :data="tree"
          node-key="nodeCode"
          :current-node-key="selectedCode"
          highlight-current
          default-expand-all
          :expand-on-click-node="false"
          :filter-node-method="filterNode"
          @node-click="selectNode"
        >
          <template #default="{ data }">
            <div class="tree-node-content">
              <div class="tree-node-main">
                <span class="tree-title" :title="outlineTreeLabel(data)">{{ outlineTreeLabel(data) }}</span>
              </div>
              <el-tag
                v-if="data.nodeKind === 'VOLUME'"
                size="small"
                effect="plain"
                :type="volumePlanStatusType(data)"
              >
                {{ volumePlanStatusLabel(data) }}
              </el-tag>

              <el-dropdown
                class="tree-actions"
                trigger="click"
                @click.stop
                @command="handleNodeCommand($event, data)"
              >
                <el-button text class="tree-more" aria-label="节点操作" @click.stop>...</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-if="canManuallyCreateChild(data)" command="add-child">+ 手动添加下一{{ nextChildKindLabel(data.nodeKind) }}</el-dropdown-item>
                    <el-dropdown-item v-if="canCreateChild(data)" command="generate-next">{{ nextGenerationButtonLabel(data) }}</el-dropdown-item>
                    <el-dropdown-item command="move-up" :disabled="!canMoveUp(data)">上移</el-dropdown-item>
                    <el-dropdown-item command="move-down" :disabled="!canMoveDown(data)">下移</el-dropdown-item>
                    <el-tooltip
                      v-if="!canDelete(data)"
                      :content="deleteBlockReason(data)"
                      placement="right"
                    >
                      <span class="disabled-delete-item">
                        <el-dropdown-item disabled>删除</el-dropdown-item>
                      </span>
                    </el-tooltip>
                    <el-dropdown-item v-else command="delete">删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-tree>
      </div>

      <footer class="directory-foot">
        <span>项目大纲</span>
      </footer>

      <el-empty
        v-if="!loading && !hasBook"
        class="mobile-root-outline-empty"
        description="暂无故事总纲"
        :image-size="72"
      >
        <p>先创建全书总纲，再按创作进度逐步生成下一卷和下一章。</p>
        <el-button type="primary" :disabled="!charactersReady" @click="openRootGeneration">AI 生成总纲</el-button>
        <span v-if="!charactersReady" class="root-outline-generation-hint">请先完成核心人物设定</span>
      </el-empty>
    </aside>

    <main class="outline-detail" :class="{ 'is-empty': !hasBook }">
      <el-empty v-if="!hasBook" class="root-outline-empty" description="暂无故事总纲">
        <p>先创建全书总纲，再按创作进度逐步生成下一卷和下一章。</p>
        <div class="root-outline-empty-actions">
          <el-button type="primary" :disabled="!charactersReady" @click="openRootGeneration">AI 生成总纲</el-button>
          <span v-if="!charactersReady" class="root-outline-generation-hint">请先完成核心人物设定</span>
        </div>
      </el-empty>

      <section v-else-if="selectedNode" class="detail-canvas">
        <header class="detail-head">
          <div class="detail-head-title">
            <div class="detail-title-row">
              <h2>{{ outlineDisplayTitle(selectedNode) }}</h2>
              <span class="detail-node-meta">
                {{ outlineKindLabel(selectedNode) }}
              </span>
            </div>
          </div>
          <div class="detail-head-actions">
            <div v-if="selectedNodeNextKind" class="detail-primary-actions">
              <el-button type="primary" @click="openSelectedNodeNextGeneration">{{ selectedNodeNextGenerationButtonLabel }}</el-button>
              <el-button v-if="canManuallyCreateChild(selectedNode)" plain @click="openSelectedNodeChild">+ 手动添加下一{{ selectedNodeNextKindLabel }}</el-button>
            </div>
            <div v-else-if="selectedNode.nodeKind === 'ARC'" class="detail-terminal-actions">
              <el-button type="primary" @click="openSelectedArcRegeneration">AI 重新生成</el-button>
              <el-button text @click="goToGeneration">去生成</el-button>
            </div>
            <el-dropdown class="detail-more" @command="handleSelectedNodeCommand">
              <el-button text>更多</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="move-up" :disabled="!selectedNodeCanMoveUp">上移</el-dropdown-item>
                  <el-dropdown-item command="move-down" :disabled="!selectedNodeCanMoveDown">下移</el-dropdown-item>
                  <el-tooltip
                    v-if="!selectedNodeCanDelete"
                    :content="selectedNodeDeleteReason"
                    placement="left"
                  >
                    <span class="disabled-delete-item">
                      <el-dropdown-item disabled>删除</el-dropdown-item>
                    </span>
                  </el-tooltip>
                  <el-dropdown-item v-else command="delete">删除</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </header>

        <el-alert
          v-if="selectedNode.nodeKind === 'VOLUME' && isVolumeUnplanned(selectedNode)"
          class="volume-unplanned-notice"
          title="该卷尚未完成卷纲"
          description="先生成卷纲后再规划章节"
          type="warning"
          :closable="false"
          show-icon
        />

        <div class="editor-surface">
          <div class="outline-title-field">
            <el-input
              v-if="selectedNode.nodeKind !== 'BOOK'"
              v-model="editForm.title"
              class="outline-title-editor"
              aria-label="标题"
              placeholder="输入标题"
            />
            <el-input
              v-else
              :model-value="projectStore.active?.title ?? selectedNode.title"
              class="outline-title-editor"
              aria-label="项目标题"
              readonly
            />
          </div>

          <div class="editor-form">
            <p class="editor-field-heading">大纲内容</p>
            <el-input
              v-model="editForm.summary"
              class="outline-summary-editor"
              type="textarea"
              :autosize="{ minRows: 4 }"
              resize="none"
              placeholder="写下这个节点的故事推进、冲突和关键转折……"
            />
          </div>
        </div>

        <footer class="detail-foot">
          <div class="detail-actions">
            <el-button @click="resetEditForm">取消</el-button>
            <el-button plain :loading="savingNode" @click="saveNode">保存修改</el-button>
          </div>
        </footer>
      </section>

      <el-empty v-else description="从左侧选择一个节点开始编辑" :image-size="96" />
    </main>

    <el-dialog
      v-model="rootGenerationVisible"
      class="root-generation-dialog"
      title="生成故事总纲"
      width="680px"
      :close-on-click-modal="false"
      destroy-on-close
      @closed="resetRootGeneration"
    >
      <template v-if="rootGenerationStep === 'config'">
        <div class="root-generation-context">
          <h2>生成故事总纲</h2>
        </div>

        <el-form class="root-generation-form" :model="rootGenerationForm" label-position="top">
          <el-form-item label="补充创作要求">
            <el-input
              v-model="rootGenerationForm.requirement"
              type="textarea"
              :autosize="{ minRows: 3 }"
              resize="none"
              placeholder="补充题材、节奏、主题或开篇方向……"
            />
          </el-form-item>
        </el-form>
      </template>

      <template v-else-if="rootGenerationStep === 'preview'">
        <div class="root-generation-context">
          <span class="eyebrow">草稿预览</span>
          <h2>AI 生成的故事总纲</h2>
          <p>以下内容仍是草稿，可修改后再确认。</p>
        </div>

        <el-form class="root-generation-draft-form" label-position="top">
          <el-form-item label="项目标题">
            <el-input :model-value="projectStore.active?.title ?? ''" readonly />
          </el-form-item>
          <el-form-item label="summary">
            <el-input
              v-model="rootDraft.summary"
              type="textarea"
              :autosize="{ minRows: 4 }"
              resize="none"
              placeholder="描述故事从开端到结局的主线、冲突与推进方向……"
            />
          </el-form-item>
        </el-form>
      </template>

      <template #footer>
        <div v-if="rootGenerationStep === 'config'" class="root-generation-actions">
          <el-button @click="rootGenerationVisible = false">取消</el-button>
          <el-button type="primary" :loading="rootGenerating" :disabled="!charactersReady" @click="generateRootDraft">生成</el-button>
        </div>
        <div v-else class="root-generation-actions">
          <el-button @click="restartRootGeneration">重新生成</el-button>
          <el-button type="primary" :loading="rootConfirming" @click="confirmRootDraft">确认</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-if="!isMobile"
      v-model="addChildVisible"
      class="add-child-dialog"
      title="新增下级大纲"
      width="min(92vw, 520px)"
      :close-on-click-modal="false"
      destroy-on-close
      @closed="resetAddChildForm"
      >
        <p class="add-child-context">为「{{ addChildParent ? outlineDisplayTitle(addChildParent) : '' }}」新增下级</p>
        <el-form
        ref="addChildFormRef"
        class="add-child-form"
        :model="addChildForm"
        :rules="addChildRules"
        label-position="top"
      >
        <el-form-item label="系统层级">
          <div class="system-kind-hint">
            <strong>{{ addChildSystemLabel }}</strong>
            <span>由系统按层级和序号自动生成，不可手动命名</span>
          </div>
        </el-form-item>
        <p v-if="addChildKind === 'ARC'" class="form-hint">
          章节号将由系统从当前卷自动分配。
        </p>

        <el-form-item label="标题" prop="title">
          <el-input v-model="addChildForm.title" placeholder="输入下级大纲标题" />
        </el-form-item>

        <el-form-item label="大纲内容" prop="summary">
          <el-input
            v-model="addChildForm.summary"
            type="textarea"
            :autosize="{ minRows: 4 }"
            resize="none"
            placeholder="描述这一层大纲的故事推进、冲突和关键转折……"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="add-child-actions">
          <el-button @click="cancelAddChild">取消</el-button>
          <el-button type="primary" @click="createChild">创建</el-button>
        </div>
      </template>
    </el-dialog>

    <el-drawer
      v-else
      v-model="addChildVisible"
      class="add-child-drawer"
      title="新增下级大纲"
      direction="btt"
      size="min(88vh, 560px)"
      destroy-on-close
      @closed="resetAddChildForm"
      >
        <p class="add-child-context">为「{{ addChildParent ? outlineDisplayTitle(addChildParent) : '' }}」新增下级</p>
        <el-form
        ref="addChildFormRef"
        class="add-child-form"
        :model="addChildForm"
        :rules="addChildRules"
        label-position="top"
      >
        <el-form-item label="系统层级">
          <div class="system-kind-hint">
            <strong>{{ addChildSystemLabel }}</strong>
            <span>由系统按层级和序号自动生成，不可手动命名</span>
          </div>
        </el-form-item>
        <p v-if="addChildKind === 'ARC'" class="form-hint">
          章节号将由系统从当前卷自动分配。
        </p>

        <el-form-item label="标题" prop="title">
          <el-input v-model="addChildForm.title" placeholder="输入下级大纲标题" />
        </el-form-item>

        <el-form-item label="大纲内容" prop="summary">
          <el-input
            v-model="addChildForm.summary"
            type="textarea"
            :autosize="{ minRows: 4 }"
            resize="none"
            placeholder="描述这一层大纲的故事推进、冲突和关键转折……"
          />
        </el-form-item>
      </el-form>

      <div class="add-child-actions">
        <el-button @click="cancelAddChild">取消</el-button>
        <el-button type="primary" @click="createChild">创建</el-button>
      </div>
    </el-drawer>

    <el-dialog
      v-model="nextGenerationVisible"
      class="ai-next-dialog"
      :title="nextGenerationActionLabel"
      width="680px"
      :close-on-click-modal="false"
      destroy-on-close
      @closed="resetNextGeneration"
    >
      <template v-if="nextGenerationStep === 'config'">
        <div class="ai-next-context">
          <h2>{{ nextGenerationActionLabel }}</h2>
          <p>每次只生成当前创作进度的下一步。</p>
        </div>

        <el-form class="ai-next-form" :model="nextGenerationForm" label-position="top">
          <el-form-item label="补充要求（可选）">
            <el-input
              v-model="nextGenerationForm.requirement"
              type="textarea"
              :autosize="{ minRows: 3, maxRows: 5 }"
              resize="none"
              placeholder="补充下一步的节奏、重点或创作方向……"
            />
          </el-form-item>
          <p v-if="isCurrentVolumePlanning" class="form-hint">
            系统将保留当前卷的编号和章节范围，模型只生成卷级剧情内容。
          </p>
          <p v-else-if="nextGenerationKind === 'ARC'" class="form-hint">
            系统将从当前卷中自动分配下一章。
          </p>
        </el-form>
      </template>

      <template v-else>
        <div class="ai-next-context">
          <span class="eyebrow">草稿预览</span>
          <h2>{{ nextGenerationActionLabel }}</h2>
          <p>结构编号由系统确定，可修改标题与大纲内容后确认。</p>
        </div>

        <el-form class="ai-next-draft-form" label-position="top">
          <div class="ai-next-structure-preview">
            <span>{{ nextDraftStructureLabel }}</span>
            <span class="ai-next-structure-separator">·</span>
            <span>{{ nextDraft.title.trim() || '未命名' }}</span>
          </div>
          <el-form-item label="标题">
            <el-input v-model="nextDraft.title" placeholder="输入标题" />
          </el-form-item>
          <el-form-item label="大纲内容">
            <el-input
              v-model="nextDraft.summary"
              type="textarea"
              :autosize="{ minRows: 4 }"
              resize="none"
              placeholder="描述下一步的故事推进、冲突、转折和结果……"
            />
          </el-form-item>
        </el-form>
      </template>

      <template #footer>
        <div v-if="nextGenerationStep === 'config'" class="ai-next-actions">
          <el-button @click="nextGenerationVisible = false">取消</el-button>
          <el-button type="primary" :loading="nextGenerating" @click="generateNextDraft">生成</el-button>
        </div>
        <div v-else class="ai-next-actions">
          <el-button @click="restartNextGeneration">重新生成</el-button>
          <el-button type="primary" :loading="nextConfirming" @click="confirmNextDraft">确认写入</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="arcRegenerationVisible"
      class="ai-regeneration-dialog"
      title="AI 重新生成"
      width="620px"
      :close-on-click-modal="false"
      destroy-on-close
      @closed="resetArcRegeneration"
    >
      <template v-if="arcRegenerationStep === 'config'">
        <div class="ai-regeneration-context">
          <span class="eyebrow">章纲调整</span>
          <h2>重新生成当前章</h2>
          <p v-if="arcRegenerationNode">
            固定第{{ arcRegenerationNode.startChapter }}章，只重新生成标题和大纲内容。
          </p>
        </div>

        <el-form class="ai-regeneration-form" :model="arcRegenerationForm" label-position="top">
          <el-form-item label="补充要求（可选）">
            <el-input
              v-model="arcRegenerationForm.requirement"
              type="textarea"
              :autosize="{ minRows: 3, maxRows: 5 }"
              resize="none"
              placeholder="例如：加强本章的悬念和人物对峙……"
            />
          </el-form-item>
        </el-form>
      </template>

      <template v-else>
        <div class="ai-regeneration-context">
          <span class="eyebrow">草稿预览</span>
          <h2>重新生成的章大纲</h2>
          <p>章节号由系统固定分配，只可编辑标题和大纲内容。</p>
        </div>

        <el-form class="ai-regeneration-draft-form" label-position="top">
          <div class="ai-regeneration-chapter-number">
            第{{ arcRegenerationDraft.chapterNumber }}章
          </div>
          <el-form-item label="标题">
            <el-input v-model="arcRegenerationDraft.title" placeholder="输入章标题" />
          </el-form-item>
          <el-form-item label="大纲内容">
            <el-input
                v-model="arcRegenerationDraft.summary"
                type="textarea"
                :autosize="{ minRows: 4 }"
                resize="none"
              placeholder="描述本章的剧情推进、冲突、转折和收束……"
            />
          </el-form-item>
        </el-form>
      </template>

      <template #footer>
        <div v-if="arcRegenerationStep === 'config'" class="ai-regeneration-actions">
          <el-button @click="arcRegenerationVisible = false">取消</el-button>
          <el-button type="primary" :loading="arcRegenerationGenerating" @click="generateArcRegenerationDraft">
            生成
          </el-button>
        </div>
        <div v-else class="ai-regeneration-actions">
          <el-button @click="restartArcRegeneration">重新生成</el-button>
          <el-button type="primary" :loading="arcRegenerationConfirming" @click="confirmArcRegenerationDraft">
            确认更新
          </el-button>
        </div>
      </template>
    </el-dialog>

    <el-drawer v-model="drawerVisible" title="编辑节点" direction="rtl" size="min(92vw, 440px)">
      <template v-if="selectedNode">
        <div class="drawer-context">
          <div class="drawer-context-title">
            <h2>{{ outlineDisplayTitle(selectedNode) }}</h2>
            <span class="drawer-node-meta">
              {{ outlineKindLabel(selectedNode) }}
            </span>
          </div>
          <div class="drawer-context-actions">
            <div v-if="selectedNodeNextKind" class="detail-primary-actions">
              <el-button type="primary" @click="openSelectedNodeNextGeneration">{{ selectedNodeNextGenerationButtonLabel }}</el-button>
              <el-button v-if="canManuallyCreateChild(selectedNode)" plain @click="openSelectedNodeChild">+ 手动添加下一{{ selectedNodeNextKindLabel }}</el-button>
            </div>
            <div v-else-if="selectedNode.nodeKind === 'ARC'" class="detail-terminal-actions">
              <el-button type="primary" @click="openSelectedArcRegeneration">AI 重新生成</el-button>
              <el-button text @click="goToGeneration">去生成</el-button>
            </div>
            <el-dropdown class="detail-more" @command="handleSelectedNodeCommand">
              <el-button text>更多</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="move-up" :disabled="!selectedNodeCanMoveUp">上移</el-dropdown-item>
                  <el-dropdown-item command="move-down" :disabled="!selectedNodeCanMoveDown">下移</el-dropdown-item>
                  <el-tooltip
                    v-if="!selectedNodeCanDelete"
                    :content="selectedNodeDeleteReason"
                    placement="left"
                  >
                    <span class="disabled-delete-item">
                      <el-dropdown-item disabled>删除</el-dropdown-item>
                    </span>
                  </el-tooltip>
                  <el-dropdown-item v-else command="delete">删除</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>

        <div class="drawer-form">
          <div class="outline-title-field">
            <el-input
              v-if="selectedNode.nodeKind !== 'BOOK'"
              v-model="editForm.title"
              class="outline-title-editor"
              aria-label="标题"
              placeholder="输入标题"
            />
            <el-input
              v-else
              :model-value="projectStore.active?.title ?? selectedNode.title"
              class="outline-title-editor"
              aria-label="项目标题"
              readonly
            />
          </div>

          <div class="editor-form">
            <p class="editor-field-heading">大纲内容</p>
            <el-input
              v-model="editForm.summary"
              class="outline-summary-editor"
              type="textarea"
              :autosize="{ minRows: 4 }"
              resize="none"
              placeholder="写下这个节点的故事推进、冲突和关键转折……"
            />
          </div>
        </div>

        <div class="drawer-actions">
          <el-button @click="resetEditForm(); drawerVisible = false">取消</el-button>
          <el-button plain :loading="savingNode" @click="saveNode">保存修改</el-button>
        </div>
      </template>
      <el-empty v-else description="暂无可编辑节点" :image-size="72" />
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, TreeInstance } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  confirmNextOutline,
  confirmArcRegeneration,
  confirmRootOutline,
  createOutlineNode,
  confirmVolumeOutline,
  createNextVolume,
  deleteOutlineNode,
  generateNextOutline,
  generateArcRegeneration,
  generateRootOutline,
  generateVolumeOutline,
  listOutlineNodes,
  reorderOutlineNode,
  updateOutlineNode,
} from '../api/planning'
import { listCharacters } from '../api/project'
import { useProjectStore } from '../stores/project'
import { ApiBusinessError } from '../api/http'
import type {
  ConfirmNextOutlineRequest,
  CreateOutlineNodeRequest,
  GenerateNextOutlineRequest,
  OutlineNode,
  OutlineNodeKind,
  StoryCharacterResponse,
} from '../types'
import { domainLabel, statusLabel, statusTone } from '../utils/uiSemantics'

type WorkbenchTreeNode = OutlineNode & { children?: WorkbenchTreeNode[] }
type AddableOutlineNodeKind = Exclude<OutlineNodeKind, 'BOOK'>
type EditForm = {
  title: string
  summary: string
}
type AddChildForm = {
  title: string
  summary: string
}
type NextGenerationStep = 'config' | 'preview'
type NextGenerationMode = 'NEXT' | 'VOLUME_PLAN'
type NextGenerationForm = {
  requirement: string
}
type NextDraft = OutlineNode
type RootGenerationStep = 'config' | 'preview'
type RootGenerationForm = {
  requirement: string
}
type RootDraft = {
  draftId: string
  summary: string
}
type ArcRegenerationStep = 'config' | 'preview'
type ArcRegenerationForm = {
  requirement: string
}
type ArcRegenerationDraft = OutlineNode & {
  draftId: string
  chapterNumber: number | null
}

const tree = reactive<WorkbenchTreeNode[]>([])

const projectStore = useProjectStore()
const router = useRouter()
const keyword = ref('')
const loading = ref(false)
const characters = ref<StoryCharacterResponse[]>([])
let characterLoadSequence = 0
const treeRef = ref<TreeInstance>()
const selectedCode = ref('')
const drawerVisible = ref(false)
const isMobile = ref(false)
const savingNode = ref(false)
const addChildVisible = ref(false)
const addChildParent = ref<WorkbenchTreeNode | null>(null)
const addChildFormRef = ref<FormInstance>()
const addChildForm = reactive<AddChildForm>({
  title: '',
  summary: '',
})
const addChildRules = computed(() => ({
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  summary: [{ required: true, message: '请输入大纲内容', trigger: 'blur' }],
}))
const nextGenerationVisible = ref(false)
const nextGenerationStep = ref<NextGenerationStep>('config')
const nextGenerationMode = ref<NextGenerationMode>('NEXT')
const nextGenerationParent = ref<WorkbenchTreeNode | null>(null)
const nextGenerationForm = reactive<NextGenerationForm>({ requirement: '' })
const nextDraft = reactive<NextDraft>({
  nodeCode: '',
  parentNodeCode: null,
  nodeKind: 'ARC',
  sequenceNo: 0,
  title: '',
  summary: '',
  startChapter: null,
  endChapter: null,
  status: '',
})
const nextDraftId = ref('')
const nextGenerating = ref(false)
const nextConfirming = ref(false)
const rootGenerationVisible = ref(false)
const rootGenerationStep = ref<RootGenerationStep>('config')
const rootGenerationForm = reactive<RootGenerationForm>({ requirement: '' })
const rootDraft = reactive<RootDraft>({ draftId: '', summary: '' })
const rootGenerating = ref(false)
const rootConfirming = ref(false)
const arcRegenerationVisible = ref(false)
const arcRegenerationStep = ref<ArcRegenerationStep>('config')
const arcRegenerationNode = ref<WorkbenchTreeNode | null>(null)
const arcRegenerationForm = reactive<ArcRegenerationForm>({ requirement: '' })
const arcRegenerationDraft = reactive<ArcRegenerationDraft>({
  draftId: '',
  chapterNumber: null,
  nodeCode: '',
  parentNodeCode: null,
  nodeKind: 'ARC',
  sequenceNo: 0,
  title: '',
  summary: '',
  startChapter: null,
  endChapter: null,
  status: '',
})
const arcRegenerationGenerating = ref(false)
const arcRegenerationConfirming = ref(false)
const initialNode = tree[0]
const editForm = reactive<EditForm>({
  title: initialNode?.title ?? '',
  summary: initialNode?.summary ?? '',
})

const selectedNode = computed(() => findNode(tree, selectedCode.value))
const hasBook = computed(() => tree.some((node) => node.nodeKind === 'BOOK'))
const charactersReady = computed(() => characters.value.length > 0)
const nodeCount = computed(() => countNodes(tree))
const selectedNodeNextKind = computed<AddableOutlineNodeKind | null>(() => {
  const node = selectedNode.value
  if (!node || !canCreateChild(node)) return null
  const nodeKind = node.nodeKind
  return nodeKind ? nextChildKind(nodeKind) : null
})
const selectedNodeNextKindLabel = computed(() => {
  const kind = selectedNodeNextKind.value
  return kind ? kindLabel(kind) : ''
})
const selectedNodeNextGenerationButtonLabel = computed(() => {
  const node = selectedNode.value
  return node ? nextGenerationButtonLabel(node) : ''
})
const selectedNodeCanMoveUp = computed(() => {
  const node = selectedNode.value
  return Boolean(node && canMoveUp(node))
})
const selectedNodeCanMoveDown = computed(() => {
  const node = selectedNode.value
  return Boolean(node && canMoveDown(node))
})
const selectedNodeCanDelete = computed(() => {
  const node = selectedNode.value
  return Boolean(node && canDelete(node))
})
const selectedNodeDeleteReason = computed(() => {
  const node = selectedNode.value
  return node ? deleteBlockReason(node) : ''
})
const addChildSystemLabel = computed(() => {
  const parent = addChildParent.value
  const childKind = parent ? nextChildKind(parent.nodeKind) : null
  return childKind ? kindLabel(childKind) : ''
})
const addChildKind = computed(() => {
  const parent = addChildParent.value
  return parent ? nextChildKind(parent.nodeKind) : null
})
const nextGenerationActionLabel = computed(() => {
  const parent = nextGenerationParent.value
  if (parent?.nodeKind === 'VOLUME' && isVolumeUnplanned(parent)) return '生成卷纲'
  return parent?.nodeKind === 'VOLUME' ? '生成下一章' : '生成下一步大纲'
})
const isCurrentVolumePlanning = computed(() => nextGenerationMode.value === 'VOLUME_PLAN')
const nextDraftStructureLabel = computed(() => {
  if (nextDraft.nodeKind === 'VOLUME') return `卷${toChineseNumber(nextDraft.sequenceNo)}`
  if (nextDraft.nodeKind === 'ARC' && nextDraft.startChapter != null) return `第${nextDraft.startChapter}章`
  return '下一步'
})
const nextGenerationKind = computed(() => {
  if (isCurrentVolumePlanning.value) return 'VOLUME'
  const parentKind = nextGenerationParent.value?.nodeKind
  return parentKind ? nextChildKind(parentKind) : null
})
watch(keyword, () => treeRef.value?.filter(keyword.value))
watch(
  () => projectStore.active?.projectCode,
  (projectCode) => {
    void loadOutline(projectCode)
    void loadCharacters(projectCode)
  },
  { immediate: true },
)

let viewportMediaQuery: MediaQueryList | null = null

function syncViewport() {
  isMobile.value = viewportMediaQuery?.matches ?? window.matchMedia('(max-width: 767px)').matches
}

onMounted(() => {
  viewportMediaQuery = window.matchMedia('(max-width: 767px)')
  syncViewport()
  viewportMediaQuery.addEventListener('change', syncViewport)
})

onBeforeUnmount(() => {
  viewportMediaQuery?.removeEventListener('change', syncViewport)
})

async function loadOutline(projectCode?: string, preferredSelectedCode?: string) {
  if (!projectCode) {
    tree.splice(0, tree.length)
    selectedCode.value = ''
    syncEditFormFromSelection()
    return
  }

  loading.value = true
  try {
    const nodes = await listOutlineNodes(projectCode)
    const loadedTree = buildOutlineTree(nodes)
    tree.splice(0, tree.length, ...loadedTree)
    selectedCode.value = preferredSelectedCode && findNode(loadedTree, preferredSelectedCode)
      ? preferredSelectedCode
      : findFirstBook(loadedTree)?.nodeCode ?? ''
    syncEditFormFromSelection()
  } catch (error) {
    tree.splice(0, tree.length)
    selectedCode.value = ''
    syncEditFormFromSelection()
    if (error instanceof ApiBusinessError) {
      ElMessage.error(error.message)
    } else {
      ElMessage.error('加载大纲失败，请稍后重试')
    }
  } finally {
    loading.value = false
  }
}

async function loadCharacters(projectCode?: string) {
  const sequence = ++characterLoadSequence
  if (!projectCode) {
    characters.value = []
    return
  }

  try {
    const result = await listCharacters(projectCode)
    if (sequence === characterLoadSequence) characters.value = result
  } catch {
    if (sequence === characterLoadSequence) characters.value = []
  }
}

function selectNode(node: WorkbenchTreeNode) {
  selectedCode.value = node.nodeCode
  syncEditForm(node)
  if (isMobile.value) drawerVisible.value = true
}

async function refreshWorkspace() {
  const projectCode = projectStore.active?.projectCode
  await Promise.all([
    loadOutline(projectCode, selectedCode.value),
    loadCharacters(projectCode),
  ])
}

function syncEditFormFromSelection() {
  if (selectedNode.value) {
    syncEditForm(selectedNode.value)
    return
  }
  editForm.title = ''
  editForm.summary = ''
}

function openSelectedNodeChild() {
  if (selectedNode.value) openAddChild(selectedNode.value)
}

function openSelectedNodeNextGeneration() {
  if (selectedNode.value) openNextGeneration(selectedNode.value)
}

function openSelectedArcRegeneration() {
  if (selectedNode.value) openArcRegeneration(selectedNode.value)
}

function openArcRegeneration(node: WorkbenchTreeNode) {
  if (node.nodeKind !== 'ARC') return
  if (isMobile.value) drawerVisible.value = false
  arcRegenerationNode.value = node
  arcRegenerationStep.value = 'config'
  arcRegenerationForm.requirement = ''
  clearArcRegenerationDraft()
  arcRegenerationVisible.value = true
}

function goToGeneration() {
  const node = selectedNode.value
  if (!node || node.nodeKind !== 'ARC' || node.startChapter == null) return
  router.push({ path: '/generate', query: { chapter: node.startChapter } })
}

function handleSelectedNodeCommand(command: string | number) {
  const node = selectedNode.value
  if (node) handleNodeCommand(command, node)
}

function openAddChild(node: WorkbenchTreeNode) {
  if (!canCreateChild(node)) return
  if (!ensureVolumePlanned(node)) return
  if (isMobile.value) drawerVisible.value = false
  addChildParent.value = node
  addChildForm.title = ''
  addChildForm.summary = ''
  addChildVisible.value = true
}

function cancelAddChild() {
  addChildVisible.value = false
}

async function createChild() {
  const valid = await addChildFormRef.value?.validate().catch(() => false)
  const projectCode = projectStore.active?.projectCode
  const parent = addChildParent.value
  const childKind = parent ? nextChildKind(parent.nodeKind) : null
  if (!valid || !projectCode || !parent || !childKind) return
  if (!ensureVolumePlanned(parent)) return

  try {
    const request: CreateOutlineNodeRequest = {
      parentNodeCode: parent.nodeCode,
      title: addChildForm.title.trim(),
      summary: addChildForm.summary.trim(),
    }
    await createOutlineNode(projectCode, request)
    addChildVisible.value = false
    await loadOutline(projectCode, parent.nodeCode)
    ElMessage.success('下级大纲已创建')
  } catch (error) {
    if (error instanceof ApiBusinessError) {
      ElMessage.error(error.message)
    } else {
      ElMessage.error('创建失败，请稍后重试')
    }
  }
}

function resetAddChildForm() {
  addChildParent.value = null
  addChildForm.title = ''
  addChildForm.summary = ''
  addChildFormRef.value?.resetFields()
}

async function openNextGeneration(node: WorkbenchTreeNode) {
  if (!canCreateChild(node)) return
  if (isMobile.value) drawerVisible.value = false

  if (node.nodeKind === 'BOOK') {
    const projectCode = projectStore.active?.projectCode
    if (!projectCode) return
    try {
      const created = await createNextVolume(projectCode, node.nodeCode)
      await loadOutline(projectCode, created.nodeCode)
      const createdNode = findNode(tree, created.nodeCode)
      if (!createdNode) throw new Error('下一卷结构加载失败')
      nextGenerationMode.value = 'VOLUME_PLAN'
      nextGenerationParent.value = createdNode
      nextGenerationStep.value = 'config'
      nextGenerationForm.requirement = ''
      clearNextDraft()
      nextGenerationVisible.value = true
      ElMessage.success('下一卷结构已创建，请完成卷纲')
    } catch (error) {
      if (error instanceof ApiBusinessError) {
        ElMessage.error(error.message)
      } else {
        ElMessage.error('创建下一卷失败，请稍后重试')
      }
    }
    return
  }

  nextGenerationMode.value = isVolumeUnplanned(node) ? 'VOLUME_PLAN' : 'NEXT'
  nextGenerationParent.value = node
  nextGenerationStep.value = 'config'
  nextGenerationForm.requirement = ''
  clearNextDraft()
  nextGenerationVisible.value = true
}

async function generateNextDraft() {
  const projectCode = projectStore.active?.projectCode
  const parent = nextGenerationParent.value
  const requirement = nextGenerationForm.requirement.trim()
  if (!projectCode || !parent) return
  if (!isCurrentVolumePlanning.value && !ensureVolumePlanned(parent)) return

  nextGenerating.value = true
  try {
    const request: GenerateNextOutlineRequest = { requirement }
    const draft = isCurrentVolumePlanning.value
      ? await generateVolumeOutline(projectCode, parent.nodeCode, request)
      : await generateNextOutline(projectCode, parent.nodeCode, request)
    const payload = draft.payload
    if (!payload || typeof payload !== 'object') throw new Error('下一步大纲草稿数据无效')
    const item = payload as Partial<OutlineNode>
    if (
      typeof item.nodeCode !== 'string'
      || typeof item.parentNodeCode !== 'string'
      || (item.nodeKind !== 'VOLUME' && item.nodeKind !== 'ARC')
      || typeof item.sequenceNo !== 'number'
      || typeof item.title !== 'string'
      || typeof item.summary !== 'string'
      || typeof item.startChapter !== 'number'
      || !Number.isInteger(item.startChapter)
      || typeof item.endChapter !== 'number'
      || !Number.isInteger(item.endChapter)
      || typeof item.status !== 'string'
    ) throw new Error('下一步大纲草稿数据无效')
    Object.assign(nextDraft, item)
    nextDraftId.value = draft.draftId
    nextGenerationStep.value = 'preview'
  } catch (error) {
    if (error instanceof ApiBusinessError) {
      ElMessage.error(error.message)
    } else {
      ElMessage.error('生成失败，请稍后重试')
    }
  } finally {
    nextGenerating.value = false
  }
}

function restartNextGeneration() {
  clearNextDraft()
  nextGenerationStep.value = 'config'
}

async function confirmNextDraft() {
  const projectCode = projectStore.active?.projectCode
  const parent = nextGenerationParent.value
  if (!projectCode || !parent || !nextDraftId.value) return
  if (!nextDraft.title.trim() || !nextDraft.summary.trim()) {
    ElMessage.warning('标题和大纲内容不能为空')
    return
  }

  nextConfirming.value = true
  try {
    const request: ConfirmNextOutlineRequest = {
      draftId: nextDraftId.value,
      title: nextDraft.title.trim(),
      summary: nextDraft.summary.trim(),
    }
    if (isCurrentVolumePlanning.value) {
      await confirmVolumeOutline(projectCode, parent.nodeCode, request)
    } else {
      await confirmNextOutline(projectCode, parent.nodeCode, request)
    }
    await loadOutline(projectCode, parent.nodeCode)
    nextGenerationVisible.value = false
    ElMessage.success('下一步大纲已写入')
  } catch (error) {
    if (error instanceof ApiBusinessError) {
      ElMessage.error(error.message)
    } else {
      ElMessage.error('确认失败，请稍后重试')
    }
  } finally {
    nextConfirming.value = false
  }
}

async function generateArcRegenerationDraft() {
  const projectCode = projectStore.active?.projectCode
  const node = arcRegenerationNode.value
  if (!projectCode || !node) return

  arcRegenerationGenerating.value = true
  try {
    const draft = await generateArcRegeneration(projectCode, node.nodeCode, {
      requirement: arcRegenerationForm.requirement.trim(),
    })
    const payload = draft.payload
    if (!payload || typeof payload !== 'object') {
      throw new Error('章纲调整草稿数据无效')
    }
    const item = payload as Partial<OutlineNode>
    if (
      item.nodeCode !== node.nodeCode
      || item.nodeKind !== 'ARC'
      || typeof item.parentNodeCode !== 'string'
      || typeof item.sequenceNo !== 'number'
      || typeof item.title !== 'string'
      || typeof item.summary !== 'string'
      || typeof item.startChapter !== 'number'
      || !Number.isInteger(item.startChapter)
      || item.endChapter !== item.startChapter
      || typeof item.status !== 'string'
    ) {
      throw new Error('章纲调整草稿数据无效')
    }
    Object.assign(arcRegenerationDraft, {
      ...item,
      chapterNumber: item.startChapter,
      draftId: draft.draftId,
    })
    arcRegenerationStep.value = 'preview'
  } catch (error) {
    if (error instanceof ApiBusinessError) {
      ElMessage.error(error.message)
    } else {
      ElMessage.error('生成失败，请稍后重试')
    }
  } finally {
    arcRegenerationGenerating.value = false
  }
}

function restartArcRegeneration() {
  clearArcRegenerationDraft()
  arcRegenerationStep.value = 'config'
}

async function confirmArcRegenerationDraft() {
  const projectCode = projectStore.active?.projectCode
  const node = arcRegenerationNode.value
  if (!projectCode || !node || !arcRegenerationDraft.draftId) return
  if (!arcRegenerationDraft.title.trim() || !arcRegenerationDraft.summary.trim()) {
    ElMessage.warning('标题和大纲内容不能为空')
    return
  }

  arcRegenerationConfirming.value = true
  try {
    await confirmArcRegeneration(projectCode, node.nodeCode, {
      draftId: arcRegenerationDraft.draftId,
      title: arcRegenerationDraft.title.trim(),
      summary: arcRegenerationDraft.summary.trim(),
    })
    await loadOutline(projectCode, node.nodeCode)
    arcRegenerationVisible.value = false
    ElMessage.success('章大纲已更新')
  } catch (error) {
    if (error instanceof ApiBusinessError) {
      ElMessage.error(error.message)
    } else {
      ElMessage.error('确认更新失败，请稍后重试')
    }
  } finally {
    arcRegenerationConfirming.value = false
  }
}

function clearArcRegenerationDraft() {
  arcRegenerationDraft.draftId = ''
  arcRegenerationDraft.chapterNumber = null
  arcRegenerationDraft.nodeCode = ''
  arcRegenerationDraft.parentNodeCode = null
  arcRegenerationDraft.nodeKind = 'ARC'
  arcRegenerationDraft.sequenceNo = 0
  arcRegenerationDraft.title = ''
  arcRegenerationDraft.summary = ''
  arcRegenerationDraft.startChapter = null
  arcRegenerationDraft.endChapter = null
  arcRegenerationDraft.status = ''
}

function resetArcRegeneration() {
  arcRegenerationNode.value = null
  arcRegenerationStep.value = 'config'
  arcRegenerationForm.requirement = ''
  clearArcRegenerationDraft()
  arcRegenerationGenerating.value = false
  arcRegenerationConfirming.value = false
}

function clearNextDraft() {
  nextDraftId.value = ''
  nextDraft.nodeCode = ''
  nextDraft.parentNodeCode = null
  nextDraft.nodeKind = 'ARC'
  nextDraft.sequenceNo = 0
  nextDraft.title = ''
  nextDraft.summary = ''
  nextDraft.startChapter = null
  nextDraft.endChapter = null
  nextDraft.status = ''
}

function resetNextGeneration() {
  nextGenerationParent.value = null
  nextGenerationMode.value = 'NEXT'
  nextGenerationStep.value = 'config'
  nextGenerationForm.requirement = ''
  clearNextDraft()
  nextGenerating.value = false
  nextConfirming.value = false
}

function openRootGeneration() {
  if (!charactersReady.value) return
  rootGenerationStep.value = 'config'
  rootGenerationForm.requirement = ''
  rootDraft.summary = ''
  rootGenerationVisible.value = true
}

async function generateRootDraft() {
  const projectCode = projectStore.active?.projectCode
  const requirement = rootGenerationForm.requirement.trim()
  if (!projectCode) return

  rootGenerating.value = true
  try {
    const draft = await generateRootOutline(projectCode, { requirement })
    const payload = draft.payload as { summary?: unknown }
    if (typeof payload.summary !== 'string') {
      throw new Error('故事总纲草稿数据无效')
    }
    rootDraft.draftId = draft.draftId
    rootDraft.summary = payload.summary
    rootGenerationStep.value = 'preview'
  } catch (error) {
    if (error instanceof ApiBusinessError) {
      ElMessage.error(error.message)
    } else {
      ElMessage.error('生成失败，请稍后重试')
    }
  } finally {
    rootGenerating.value = false
  }
}

function restartRootGeneration() {
  rootGenerationStep.value = 'config'
}

async function confirmRootDraft() {
  const projectCode = projectStore.active?.projectCode
  if (!projectCode || !rootDraft.draftId) return
  if (!rootDraft.summary.trim()) {
    ElMessage.warning('大纲内容不能为空')
    return
  }

  rootConfirming.value = true
  try {
    await confirmRootOutline(projectCode, {
      draftId: rootDraft.draftId,
      summary: rootDraft.summary.trim(),
    })
    await loadOutline(projectCode)
    rootGenerationVisible.value = false
    ElMessage.success('总纲已确认')
  } catch (error) {
    if (error instanceof ApiBusinessError) {
      ElMessage.error(error.message)
    } else {
      ElMessage.error('确认失败，请稍后重试')
    }
  } finally {
    rootConfirming.value = false
  }
}

function resetRootGeneration() {
  rootGenerationStep.value = 'config'
  rootGenerationForm.requirement = ''
  rootDraft.draftId = ''
  rootDraft.summary = ''
  rootGenerating.value = false
  rootConfirming.value = false
}

function normalizeOutlineNode(node: OutlineNode): WorkbenchTreeNode | null {
  return node.nodeKind ? { ...node } : null
}

function buildOutlineTree(nodes: OutlineNode[]): WorkbenchTreeNode[] {
  const normalizedNodes = nodes
    .map(normalizeOutlineNode)
    .filter((node): node is WorkbenchTreeNode => node !== null)
  const nodesByCode = new Map(normalizedNodes.map((node) => [node.nodeCode, node]))
  const roots: WorkbenchTreeNode[] = []

  for (const node of normalizedNodes) {
    const parent = node.parentNodeCode ? nodesByCode.get(node.parentNodeCode) : undefined
    if (parent) {
      parent.children ??= []
      parent.children.push(node)
    } else {
      roots.push(node)
    }
  }

  sortOutlineNodes(roots)
  return roots
}

function sortOutlineNodes(nodes: WorkbenchTreeNode[]) {
  nodes.sort((left, right) => left.sequenceNo - right.sequenceNo)
  nodes.forEach((node) => {
    if (node.children) sortOutlineNodes(node.children)
  })
}

function findFirstBook(nodes: WorkbenchTreeNode[]): WorkbenchTreeNode | null {
  for (const node of nodes) {
    if (node.nodeKind === 'BOOK') return node
    if (node.children) {
      const book = findFirstBook(node.children)
      if (book) return book
    }
  }
  return null
}

function resetEditForm() {
  if (selectedNode.value) syncEditForm(selectedNode.value)
}

function syncEditForm(node: WorkbenchTreeNode) {
  editForm.title = node.nodeKind === 'BOOK'
    ? (projectStore.active?.title ?? node.title)
    : node.title
  editForm.summary = node.summary
}

async function saveNode() {
  const projectCode = projectStore.active?.projectCode
  const node = selectedNode.value
  if (!projectCode || !node) return
  if (!editForm.title.trim() || !editForm.summary.trim()) {
    ElMessage.warning('标题和大纲内容不能为空')
    return
  }

  savingNode.value = true
  try {
    await updateOutlineNode(projectCode, node.nodeCode, {
      parentNodeCode: node.parentNodeCode ?? null,
      nodeKind: node.nodeKind,
      sequenceNo: node.sequenceNo,
      title: editForm.title.trim(),
      summary: editForm.summary.trim(),
      startChapter: node.startChapter ?? null,
      endChapter: node.endChapter ?? null,
      status: node.status,
    })
    await loadOutline(projectCode, node.nodeCode)
    if (isMobile.value) drawerVisible.value = false
    ElMessage.success('节点已保存')
  } catch (error) {
    if (error instanceof ApiBusinessError) {
      ElMessage.error(error.message)
    } else {
      ElMessage.error('保存失败，请稍后重试')
    }
  } finally {
    savingNode.value = false
  }
}

function filterNode(value: string, data: WorkbenchTreeNode) {
  if (!value) return true
  const query = value.trim().toLowerCase()
  return `${outlineDisplayTitle(data)} ${data.summary} ${data.nodeKind} ${kindLabel(data.nodeKind)}`.toLowerCase().includes(query)
}

function handleNodeCommand(command: string | number, node: WorkbenchTreeNode) {
  if (command === 'add-child') {
    openAddChild(node)
    return
  }
  if (command === 'generate-next') {
    openNextGeneration(node)
    return
  }
  if (command === 'move-up') {
    void moveNode(node, -1)
    return
  }
  if (command === 'move-down') {
    void moveNode(node, 1)
    return
  }
  if (command === 'delete') void confirmRemoveNode(node)
}

async function confirmRemoveNode(node: WorkbenchTreeNode) {
  if (!canDelete(node)) return
  try {
    await ElMessageBox.confirm(
      `确定删除「${node.title}」？\n删除后无法恢复。`,
      '删除节点',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
    await removeNode(node)
  } catch {
    // 用户取消删除时不提示错误。
  }
}

function canDelete(node: WorkbenchTreeNode) {
  return node.nodeKind !== 'BOOK' && !node.children?.length
}

function deleteBlockReason(node: WorkbenchTreeNode) {
  if (node.nodeKind === 'BOOK') return `${domainLabel('BOOK')}不能删除`
  return '请先删除下级大纲'
}

function siblingNodes(node: WorkbenchTreeNode) {
  if (!node.parentNodeCode) return tree
  return findNode(tree, node.parentNodeCode)?.children ?? []
}

function canMoveUp(node: WorkbenchTreeNode) {
  return siblingNodes(node).findIndex((sibling) => sibling.nodeCode === node.nodeCode) > 0
}

function canMoveDown(node: WorkbenchTreeNode) {
  const siblings = siblingNodes(node)
  const index = siblings.findIndex((sibling) => sibling.nodeCode === node.nodeCode)
  return index >= 0 && index < siblings.length - 1
}

async function moveNode(node: WorkbenchTreeNode, offset: -1 | 1) {
  const projectCode = projectStore.active?.projectCode
  if (!projectCode) return
  const siblings = siblingNodes(node)
  const currentIndex = siblings.findIndex((sibling) => sibling.nodeCode === node.nodeCode)
  const targetIndex = currentIndex + offset
  if (currentIndex < 0 || targetIndex < 0 || targetIndex >= siblings.length) return

  try {
    await reorderOutlineNode(projectCode, node.nodeCode, { targetSequence: targetIndex + 1 })
    await loadOutline(projectCode, node.nodeCode)
    ElMessage.success(offset < 0 ? '节点已上移' : '节点已下移')
  } catch (error) {
    if (error instanceof ApiBusinessError) {
      ElMessage.error(error.message)
    } else {
      ElMessage.error('排序失败，请稍后重试')
    }
  }
}

async function removeNode(node: WorkbenchTreeNode) {
  const projectCode = projectStore.active?.projectCode
  if (!projectCode || !canDelete(node)) return
  try {
    await deleteOutlineNode(projectCode, node.nodeCode)
    await loadOutline(projectCode, node.parentNodeCode ?? undefined)
    ElMessage.success('节点已删除')
  } catch (error) {
    if (error instanceof ApiBusinessError) {
      ElMessage.error(error.message)
    } else {
      ElMessage.error('删除失败，请稍后重试')
    }
  }
}

function findNode(nodeList: WorkbenchTreeNode[], nodeCode: string): WorkbenchTreeNode | null {
  for (const node of nodeList) {
    if (node.nodeCode === nodeCode) return node
    const child = node.children ? findNode(node.children, nodeCode) : null
    if (child) return child
  }
  return null
}

function countNodes(nodeList: WorkbenchTreeNode[]): number {
  return nodeList.reduce((count, node) => count + 1 + (node.children ? countNodes(node.children) : 0), 0)
}

function nextChildKind(nodeKind: OutlineNodeKind): AddableOutlineNodeKind | null {
  if (nodeKind === 'BOOK') return 'VOLUME'
  if (nodeKind === 'VOLUME') return 'ARC'
  return null
}

function nextChildKindLabel(nodeKind: OutlineNodeKind) {
  const kind = nextChildKind(nodeKind)
  return kind ? kindLabel(kind) : ''
}

function nextGenerationButtonLabel(node: WorkbenchTreeNode) {
  if (node.nodeKind === 'BOOK') return '生成下一卷'
  if (node.nodeKind === 'VOLUME' && isVolumeUnplanned(node)) return '生成卷纲'
  if (node.nodeKind === 'VOLUME') return '生成下一章'
  return ''
}

function isActiveVolume(node: WorkbenchTreeNode) {
  if (node.nodeKind !== 'VOLUME' || !node.parentNodeCode) return false
  const book = findNode(tree, node.parentNodeCode)
  return Boolean(book && latestVolume(book)?.nodeCode === node.nodeCode)
}

function canCreateChild(node: WorkbenchTreeNode) {
  return node.nodeKind === 'BOOK'
    || (node.nodeKind === 'VOLUME' && isActiveVolume(node))
}

function canManuallyCreateChild(node: WorkbenchTreeNode) {
  return canCreateChild(node)
    && !(node.nodeKind === 'VOLUME' && isVolumeUnplanned(node))
}

function isVolumeUnplanned(node: Pick<WorkbenchTreeNode, 'nodeKind' | 'summary' | 'status'>) {
  return node.nodeKind === 'VOLUME'
    && (!node.summary.trim() || node.status.toUpperCase() === 'UNPLANNED')
}

function volumePlanStatusLabel(node: Pick<WorkbenchTreeNode, 'nodeKind' | 'summary' | 'status'>) {
  return statusLabel(isVolumeUnplanned(node) ? 'UNPLANNED' : 'PLANNED')
}

function volumePlanStatusType(
  node: Pick<WorkbenchTreeNode, 'nodeKind' | 'summary' | 'status'>,
) {
  return statusTone(isVolumeUnplanned(node) ? 'UNPLANNED' : 'PLANNED')
}

function ensureVolumePlanned(node: WorkbenchTreeNode) {
  if (isVolumeUnplanned(node)) {
    ElMessage.warning('请先完成当前卷规划')
    return false
  }
  return true
}

function latestVolume(book: WorkbenchTreeNode) {
  return book.children
    ?.filter((child) => child.nodeKind === 'VOLUME')
    .reduce<WorkbenchTreeNode | null>(
      (latest, volume) => !latest || volume.sequenceNo > latest.sequenceNo ? volume : latest,
      null,
    ) ?? null
}

function kindLabel(kind: OutlineNodeKind) {
  if (kind === 'BOOK') return '故事总纲'
  if (kind === 'VOLUME') return '卷'
  return '章'
}

function outlineDisplayTitle(node: Pick<OutlineNode, 'nodeKind' | 'title' | 'sequenceNo'>) {
  if (node.nodeKind === 'BOOK') return projectStore.active?.title ?? node.title
  if (node.nodeKind === 'VOLUME') {
    return volumeStoryTitle(node) || volumeStructureLabel(node.sequenceNo)
  }
  return node.title
}

function volumeStoryTitle(
  node: Pick<OutlineNode, 'nodeKind' | 'title' | 'sequenceNo'>,
) {
  if (node.nodeKind !== 'VOLUME') return node.title
  const title = node.title.trim()
  if (!title) return ''
  return isStructuralVolumeTitle(title) ? '' : title
}

function volumeStructureLabel(sequenceNo: number) {
  return `卷${toChineseNumber(sequenceNo)}`
}

function isStructuralVolumeTitle(title: string) {
  return /^卷(?:[0-9]+|[零一二三四五六七八九十百千万两〇]+)$/.test(title)
    || /^第(?:[0-9]+|[零一二三四五六七八九十百千万两〇]+)卷$/.test(title)
}

function outlineKindLabel(node: Pick<OutlineNode, 'nodeKind' | 'sequenceNo' | 'startChapter'>) {
  if (node.nodeKind === 'BOOK') return '故事总纲'
  if (node.nodeKind === 'VOLUME') return volumeStructureLabel(node.sequenceNo)
  return node.startChapter == null ? '章节' : `第${node.startChapter}章`
}

function outlineTreeLabel(node: WorkbenchTreeNode) {
  const kind = outlineKindLabel(node)
  const title = outlineDisplayTitle(node)
  if (!title || title === kind) return kind
  return `${kind} ${title}`
}

function toChineseNumber(value: number) {
  const digits = '零一二三四五六七八九'
  if (!Number.isInteger(value) || value < 1 || value > 99) return String(value)
  if (value < 10) return digits[value]
  if (value < 20) return `十${value === 10 ? '' : digits[value - 10]}`
  const tens = Math.floor(value / 10)
  const ones = value % 10
  return `${digits[tens]}十${ones === 0 ? '' : digits[ones]}`
}

</script>

<style scoped>
.outline-workbench {
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  overflow: hidden;
  background: var(--surface);
}

.outline-directory {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--surface);
}

.directory-head {
  min-width: 0;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  min-height: 74px;
  padding: 20px 18px 16px;
}
.directory-head > div:first-child { min-width: 0; }
.directory-head-actions { display: flex; align-items: center; flex-shrink: 0; }

.eyebrow {
  display: block;
  color: var(--primary);
  font: 700 10px/1.4 ui-monospace, SFMono-Regular, Menlo, monospace;
  letter-spacing: .16em;
}

.directory-head h1 {
  margin: 0;
  font: 700 20px/1.25 "Songti SC", "STSong", serif;
  letter-spacing: .04em;
}
.directory-head p { max-width: 210px; margin-top: 5px; color: var(--text-faint); font-size: 11px; line-height: 1.5; }

.directory-search {
  padding: 0 16px 15px;
}

.directory-meta {
  min-height: 38px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 18px;
  border-top: 1px solid var(--border);
  border-bottom: 1px solid var(--border);
  color: var(--text-faint);
  font-size: 11px;
}

.directory-meta span:first-child {
  color: var(--text-muted);
  font-weight: 600;
}

.outline-tree {
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow-y: auto;
  padding: 16px 0 20px;
}

.outline-tree :deep(.el-tree) {
  background: transparent;
  color: var(--text);
}

.outline-tree :deep(.el-tree-node__content) {
  min-width: 0;
  min-height: 46px;
  padding: 3px 10px 3px 0;
  border-left: 3px solid transparent;
  background: transparent;
  transition: background .15s ease, border-color .15s ease;
}

.outline-tree :deep(.el-tree-node__content:hover) {
  background: var(--surface2);
}

.outline-tree :deep(.el-tree-node.is-current > .el-tree-node__content) {
  border-left-color: var(--primary);
  background: var(--primary-dim);
}

.outline-tree :deep(.el-tree-node__expand-icon) {
  color: var(--text-faint);
  font-size: 13px;
}

.tree-node-content {
  width: 100%;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  overflow: hidden;
}

.tree-node-main {
  min-width: 0;
  flex: 1;
  display: flex;
  align-items: center;
  gap: 6px;
  overflow: hidden;
}

.tree-node-content > .el-tag {
  flex-shrink: 0;
  white-space: nowrap;
}

.tree-title {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  color: var(--text);
  font-size: 13px;
  font-weight: 600;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-actions {
  flex-shrink: 0;
  opacity: 0;
  transition: opacity .15s ease;
}

.tree-node-content:hover .tree-actions,
.tree-node-content:focus-within .tree-actions {
  opacity: 1;
}

.tree-more {
  color: var(--text-muted);
}

.tree-more:hover,
.tree-more:focus-visible {
  color: var(--primary);
}

.disabled-delete-item {
  display: block;
}

.directory-foot {
  min-height: 44px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 0 16px;
  border-top: 1px solid var(--border);
  color: var(--text-faint);
  font-size: 10px;
}

.outline-detail {
  min-width: 0;
  min-height: 0;
  overflow-y: auto;
  padding: 28px clamp(28px, 6vw, 88px) 52px;
  background: var(--surface);
  scrollbar-gutter: stable;
}

.root-outline-empty {
  min-height: 100%;
  padding: 40px 20px;
}

.root-outline-empty :deep(.el-empty__description) {
  color: var(--text);
  font-size: 16px;
  font-weight: 700;
}

.root-outline-empty > p {
  margin: 4px 0 20px;
  color: var(--text-faint);
  font-size: 12px;
  line-height: 1.7;
  text-align: center;
}

.root-outline-empty-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
}

.root-outline-generation-hint {
  color: var(--warning);
  font-size: 11px;
}

.detail-canvas {
  width: min(var(--workbench-content-width), 100%);
  min-width: 0;
  margin: 0 auto;
}

.detail-head {
  min-width: 0;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  position: sticky;
  top: -28px;
  z-index: 2;
  padding-top: 28px;
  background: var(--surface);
  padding-bottom: 22px;
  border-bottom: 1px solid var(--border);
}

.detail-head-title {
  min-width: 0;
  flex: 1;
}

.detail-title-row {
  min-width: 0;
  display: flex;
  align-items: baseline;
  gap: 18px;
}

.detail-head h2,
.drawer-context h2 {
  min-width: 0;
  margin-top: 7px;
  overflow: hidden;
  color: var(--text);
  font: 700 clamp(24px, 3vw, 34px)/1.25 "Songti SC", "STSong", serif;
  letter-spacing: .04em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-node-meta,
.drawer-node-meta {
  flex-shrink: 0;
  color: var(--text-faint);
  font: 11px/1.5 ui-monospace, SFMono-Regular, Menlo, monospace;
  white-space: nowrap;
}

.detail-head-actions {
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 10px;
}

.volume-unplanned-notice {
  margin: 20px 0 0;
}

.detail-primary-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.detail-more {
  flex-shrink: 0;
}

.detail-terminal-note {
  color: var(--text-faint);
  font-size: 12px;
  line-height: 1.5;
  white-space: nowrap;
}

.drawer-context {
  min-width: 0;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--border);
}

.drawer-context-title {
  min-width: 0;
}

.drawer-context-title h2 {
  margin-bottom: 0;
}

.drawer-node-meta {
  display: block;
  margin-top: 8px;
}

.drawer-context-actions {
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 10px;
}

.drawer-context h2 {
  font-size: 24px;
}

.drawer-actions {
  justify-content: flex-end;
  margin-top: 26px;
}

.editor-surface {
  min-width: 0;
  padding: 28px 0 38px;
  border-top: 1px solid var(--border);
  background: transparent;
}

.editor-form {
  width: min(var(--workbench-content-width), 100%);
  min-width: 0;
}

.outline-title-field {
  min-width: 0;
  margin-bottom: 30px;
}

.outline-title-editor {
  width: 100%;
}

.outline-title-editor :deep(.el-input__wrapper),
.outline-title-editor :deep(.el-input__wrapper.is-focus) {
  padding: 0;
  border: 0 !important;
  border-radius: 0;
  background: transparent !important;
  box-shadow: none !important;
}

.outline-title-editor :deep(.el-input__inner) {
  height: auto;
  padding: 0;
  color: var(--text);
  font-size: 28px;
  line-height: 1.35;
  font-weight: 600;
}

.editor-field-heading {
  margin: 0 0 10px;
  color: var(--text-faint);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: .1em;
}

.form-hint {
  margin-top: 8px;
  color: var(--text-faint);
  font-size: 11px;
  line-height: 1.5;
}

.outline-summary-editor :deep(.el-textarea__inner) {
  padding: 0 0 6px;
  border: 0 !important;
  border-bottom: 1px solid var(--border) !important;
  border-radius: 0 !important;
  background: transparent !important;
  box-shadow: none !important;
  font: 15px/2 "Songti SC", "STSong", serif;
  resize: none !important;
  overflow-y: hidden !important;
}

.detail-foot {
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 16px;
  position: sticky;
  bottom: 0;
  z-index: 1;
  padding: 18px 0 12px;
  background: linear-gradient(transparent, var(--surface) 30%);
  color: var(--text-faint);
  font-size: 10px;
}

.detail-actions,
.drawer-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.add-child-context {
  margin: -4px 0 20px;
  color: var(--text-faint);
  font-size: 12px;
}

.add-child-form {
  min-width: 0;
}

.add-child-form :deep(.el-form-item) {
  margin-bottom: 20px;
}

.add-child-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

:global(.add-child-drawer .el-drawer__body) {
  padding: 20px clamp(16px, 5vw, 36px) 28px;
}

:global(.add-child-drawer .add-child-actions) {
  margin-top: 26px;
}

.ai-next-context {
  min-width: 0;
  margin: -4px 0 22px;
}

.ai-next-context h2 {
  min-width: 0;
  margin-top: 8px;
  overflow: hidden;
  color: var(--text);
  font: 700 24px/1.35 "Songti SC", "STSong", serif;
  letter-spacing: .04em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-next-context p {
  margin-top: 8px;
  color: var(--text-faint);
  font-size: 12px;
  line-height: 1.6;
}

.ai-next-form,
.ai-next-draft-form {
  min-width: 0;
}

.ai-next-form :deep(.el-form-item),
.ai-next-draft-form :deep(.el-form-item) {
  margin-bottom: 20px;
}

.ai-next-form :deep(.el-form-item__label),
.ai-next-draft-form :deep(.el-form-item__label) {
  height: auto;
  margin-bottom: 8px;
  color: var(--text-faint);
  font-size: 11px;
  font-weight: 700;
}

.system-kind-hint {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--ink-muted);
  font-size: 13px;
}

.system-kind-hint strong {
  flex-shrink: 0;
  color: var(--text);
  font-size: 14px;
}

.ai-next-draft-form {
  padding-right: 4px;
}

.ai-next-structure-preview {
  margin: 0 0 14px;
  color: var(--primary);
  font: 700 18px/1.35 "Songti SC", "STSong", serif;
}

.ai-next-structure-separator {
  margin: 0 4px;
  color: var(--text-faint);
}

.ai-next-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

:global(.ai-next-dialog) {
  max-width: calc(100vw - 24px);
}

:global(.ai-next-dialog .el-dialog__body) {
  max-height: calc(100vh - 180px);
  overflow-y: auto;
  min-width: 0;
  padding: 4px 24px 8px;
}

:global(.ai-regeneration-dialog .el-dialog__body) {
  max-height: calc(100vh - 180px);
  overflow-y: auto;
}

.root-generation-context {
  min-width: 0;
  margin: -4px 0 22px;
}

.root-generation-context h2 {
  min-width: 0;
  margin-top: 8px;
  overflow: hidden;
  color: var(--text);
  font: 700 24px/1.35 "Songti SC", "STSong", serif;
  letter-spacing: .04em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.root-generation-context p {
  margin-top: 8px;
  color: var(--text-faint);
  font-size: 12px;
  line-height: 1.6;
}

.root-generation-form,
.root-generation-draft-form {
  min-width: 0;
}

.root-generation-form :deep(.el-form-item),
.root-generation-draft-form :deep(.el-form-item) {
  margin-bottom: 20px;
}

.root-generation-form :deep(.el-form-item__label),
.root-generation-draft-form :deep(.el-form-item__label) {
  height: auto;
  margin-bottom: 8px;
  color: var(--text-faint);
  font-size: 11px;
  font-weight: 700;
}

.root-generation-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.mobile-root-outline-empty {
  display: none;
}

:global(.root-generation-dialog) {
  max-width: calc(100vw - 24px);
}

:global(.root-generation-dialog .el-dialog__body) {
  max-height: calc(100vh - 180px);
  overflow-y: auto;
  min-width: 0;
  padding: 4px 24px 8px;
}

@media (max-width: 767px) {
  :global(.ai-next-dialog) {
    width: calc(100vw - 24px) !important;
    margin-top: 4vh;
  }

  :global(.ai-next-dialog .el-dialog__body) {
    padding-inline: 16px;
  }

  :global(.root-generation-dialog) {
    width: calc(100vw - 24px) !important;
    margin-top: 4vh;
  }

  :global(.root-generation-dialog .el-dialog__body) {
    padding-inline: 16px;
  }

  .drawer-context {
    flex-direction: column;
    gap: 18px;
  }

  .drawer-context-actions {
    width: 100%;
    justify-content: flex-start;
    align-items: flex-start;
  }

  .drawer-context-actions .detail-primary-actions {
    width: 100%;
    justify-content: flex-start;
  }

  .drawer-context-actions .detail-primary-actions .el-button {
    min-width: 0;
    flex: 1;
  }
}

@media (max-width: 1199px) {
  .outline-workbench {
    grid-template-columns: 260px minmax(0, 1fr);
  }

  .directory-head {
    padding-inline: 18px;
  }

  .outline-detail {
    padding-inline: clamp(22px, 4vw, 48px);
  }
}

@media (max-width: 767px) {
  .outline-workbench {
    height: 100%;
    min-height: 0;
    display: block;
    overflow: hidden;
    border: 0;
    box-shadow: none;
  }

  .outline-directory {
    height: 100%;
    min-height: 0;
    border-right: 0;
  }

  .outline-detail {
    display: none;
  }

  .directory-head {
    padding: 22px 16px 18px;
  }

  .directory-head h1 {
    font-size: 20px;
  }

  .directory-search {
    padding-inline: 12px;
  }

  .outline-tree {
    padding-block: 10px 20px;
  }

  .directory-foot {
    padding-inline: 14px;
  }

  .mobile-root-outline-empty {
    display: flex;
    padding: 36px 18px 48px;
  }

  .mobile-root-outline-empty > p {
    margin: 4px 0 16px;
    color: var(--text-faint);
    font-size: 11px;
    line-height: 1.6;
    text-align: center;
  }
}

@media (max-width: 420px) {
  .detail-head-actions {
    width: 100%;
    justify-content: flex-start;
  }

  .detail-title-row {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
  }

  .detail-node-meta {
    white-space: normal;
  }

  .detail-head-actions .detail-primary-actions {
    width: 100%;
  }

  .detail-head-actions .detail-primary-actions .el-button {
    min-width: 0;
    flex: 1;
  }

  .tree-node-content {
    gap: 6px;
  }

}
</style>
