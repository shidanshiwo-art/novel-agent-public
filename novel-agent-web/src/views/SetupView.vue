<template>
  <div class="setup-wrap editor-page-shell">
    <el-tabs :model-value="tab" class="setup-tabs">
      <!-- 故事设定 -->
      <el-tab-pane label="设定" name="bible">
        <div class="bible-editor">
          <header class="editor-page-header">
            <div>
              <h1>设定</h1>
              <p>保持故事的世界边界和关键规则一致。</p>
            </div>
            <div class="page-header-actions">
              <el-tag v-if="bibleDraftId" type="warning" effect="plain">AI 草稿 · 可编辑</el-tag>
              <el-tag v-else-if="hasConfirmedBible" type="success" effect="plain">已确认</el-tag>
              <el-button plain :loading="generatingBible" :disabled="loadingBible" @click="openBibleGenerator">{{ hasConfirmedBible ? 'AI 调整' : 'AI 生成初始设定' }}</el-button>
              <el-button v-if="!bibleDraftId && !showBibleEmpty" type="primary" :loading="savingBible" :disabled="loadingBible" @click="saveBible">保存</el-button>
            </div>
          </header>

          <section v-if="showBibleEmpty" class="bible-empty">
            <div class="bible-empty-mark">设</div>
            <h2>还没有故事设定</h2>
            <p>你可以让 AI 根据题材生成一版，<br />也可以直接从世界背景开始写。</p>
            <div class="bible-empty-actions">
              <el-button type="primary" @click="openBibleGenerator">AI 生成初始设定</el-button>
              <el-button plain @click="startBibleEditing">直接开始编辑</el-button>
            </div>
          </section>

          <div v-else class="bible-content-layout">
            <aside class="bible-section-nav" aria-label="设定目录">
              <span class="bible-section-nav-title">设定目录</span>
              <nav class="bible-section-nav-list">
                <el-button
                  v-for="section in bibleSectionNavigation"
                  :key="section.key"
                  text
                  class="bible-section-nav-button"
                  :class="{ 'is-active': activeBibleSection === section.key }"
                  :aria-current="activeBibleSection === section.key ? 'page' : undefined"
                  @click="selectBibleSection(section.key)"
                >{{ section.label }}</el-button>
              </nav>
              <el-select
                v-model="activeBibleSection"
                class="bible-section-nav-select"
                aria-label="跳转到设定分区"
                placeholder="跳转到设定分区"
              >
                <el-option v-for="section in bibleSectionNavigation" :key="section.key" :label="section.label" :value="section.key" />
              </el-select>
            </aside>

            <main class="story-bible-canvas">
              <section v-if="bibleDraftId" class="bible-draft-banner">
                <div><span class="bible-draft-kicker">{{ statusLabel('DRAFT') }}</span><h2>{{ hasConfirmedBible ? 'AI 调整草稿' : 'AI 初始设定草稿' }}</h2><p>{{ hasConfirmedBible ? '这是基于当前正式设定生成的调整草稿。正式版本仍保留，应用修改后才会替换它。' : '这是待确认的初始设定草稿。应用修改后才会成为正式故事设定。' }}</p></div>
                <div class="bible-draft-actions">
                  <el-button text @click="discardBibleDraft">放弃修改</el-button>
                  <el-button type="primary" :loading="confirmingBible" @click="confirmBible">应用修改</el-button>
                </div>
              </section>

              <section v-if="activeBibleSection === 'world'" class="bible-section world-section">
                <div class="section-title-block"><div><h2>世界设定</h2><p>先从故事发生的世界开始，写下读者需要相信的基本边界。</p></div></div>
                <label class="bible-writing-field world-background-field"><span>世界背景</span><small>描述这个故事发生在怎样的世界：时代、社会、技术水平、特殊现象以及普通人的生活方式。</small><el-input v-model="bf.worldBackground" type="textarea" :autosize="{ minRows: 3 }" resize="none" placeholder="这里是什么时代？社会如何组织？技术或超自然现象发展到什么程度？普通人怎样生活？" /></label>
              </section>

              <section v-if="activeBibleSection === 'rules'" class="bible-section rules-section">
                <div class="section-title-block"><div><h2>不可违反的规则</h2><p>这些规则会作为后续 AI 创作的约束。</p></div></div>
                <div class="rules-list">
                  <div v-for="(_, index) in bf.hardRules" :key="`rule-${index}`" class="rule-row">
                    <span class="rule-number">{{ index + 1 }}</span>
                    <el-input v-model="bf.hardRules[index]" placeholder="写下一条真正不能违反的限制" />
                    <el-button text type="danger" class="local-delete-button" aria-label="删除规则" @click="removeBibleItem('hardRules', index)">删除</el-button>
                  </div>
                </div>
                <el-button text type="primary" class="add-rule-button" @click="addBibleItem('hardRules')">＋ 添加规则</el-button>
              </section>

              <section v-if="activeBibleSection === 'power'" class="bible-section bible-secondary-section">
                <div class="section-title-block"><div><h2>特殊体系</h2><p>题材需要时再补充能力、超自然或特殊机制。</p></div></div>
                <div class="secondary-section-body">
                  <div class="secondary-fields">
                    <label class="bible-field"><span>体系名称</span><el-input v-model="bf.powerName" placeholder="例如：灵视、术式、异能；不需要时可以留空" /></label>
                    <label class="bible-field long-text-field"><span>体系说明</span><el-input v-model="bf.powerDescription" type="textarea" :autosize="{ minRows: 3 }" resize="none" placeholder="用一段话说明这个体系是什么，以及它如何影响故事。" /></label>
                    <label class="bible-field long-text-field"><span>等级 / 境界</span><small>存在明确等级体系时填写，没有可以留空。</small><el-input v-model="bf.powerLevels" type="textarea" :autosize="{ minRows: 2 }" resize="none" placeholder="例如：入门、熟练、大成；没有可以留空" /></label>
                    <label class="bible-field long-text-field"><span>补充设定（可选）</span><el-input v-model="bf.powerSupplement" type="textarea" :autosize="{ minRows: 3 }" resize="none" placeholder="补充与体系有关的特殊设定、边界或例外。" /></label>
                  </div>
                </div>
              </section>

              <section v-if="activeBibleSection === 'theme'" class="bible-section bible-secondary-section">
                <div class="section-title-block"><div><h2>主题与冲突</h2><p>帮助 AI 理解故事想往哪里走。</p></div></div>
                <div class="secondary-section-body">
                  <p class="secondary-note">这些内容用于帮助 AI 理解故事方向，可以随创作调整，不属于不可违反的世界规则。</p>
                  <div class="direction-fields">
                    <label class="bible-field long-text-field"><span>一句话故事</span><el-input v-model="bf.oneSentencePremise" type="textarea" :autosize="{ minRows: 2 }" resize="none" maxlength="120" placeholder="用一句话说清楚这是一个什么故事" /></label>
                    <label class="bible-field"><span>核心主题</span><el-input v-model="bf.coreTheme" placeholder="例如：成长、救赎、权力与代价" /></label>
                    <label class="bible-field long-text-field"><span>主要矛盾</span><el-input v-model="bf.mainConflict" type="textarea" :autosize="{ minRows: 3 }" resize="none" placeholder="主角想得到什么，又被什么阻挡？" /></label>
                  </div>
                </div>
              </section>

              <section v-if="activeBibleSection === 'ending'" class="bible-section bible-secondary-section">
                <div class="section-title-block"><div><h2>结局方向</h2><p>为故事留下怎样的改变或余韵。</p></div></div>
                <div class="secondary-section-body">
                  <p class="secondary-note">结局方向可以随创作调整，用来帮助 AI 保持故事的整体走向。</p>
                  <label class="bible-field long-text-field"><span>结局方向</span><el-input v-model="bf.endingDirection" type="textarea" :autosize="{ minRows: 3 }" resize="none" placeholder="故事最后留下怎样的改变或余韵？" /></label>
                </div>
              </section>

              <section v-if="activeBibleSection === 'style'" class="bible-section bible-secondary-section">
                <div class="section-title-block"><div><h2>写作风格</h2><p>正文生成时参考的表达指导。</p></div></div>
                <div class="secondary-section-body">
                  <p class="secondary-note">这只是正文生成指导，不与世界规则处于同一视觉等级。</p>
                  <label class="bible-writing-field"><span>写作风格</span><el-input v-model="bf.styleGuide" type="textarea" :autosize="{ minRows: 3 }" resize="none" placeholder="第三人称限知；语言克制；句式和节奏服务于故事氛围……" /></label>
                </div>
              </section>
            </main>
          </div>

          <el-dialog v-model="bibleGeneratorOpen" :title="hasConfirmedBible ? '调整故事设定' : '生成故事设定'" width="620px" class="story-bible-generator-dialog" destroy-on-close>
            <div class="bible-dialog-copy">
              <p>{{ hasConfirmedBible ? '告诉 AI 你希望调整什么：' : '你还可以补充一些要求：' }}</p>
               <el-input v-model="bibleRequirement" type="textarea" :autosize="{ minRows: 3 }" resize="none" maxlength="1000" show-word-limit :placeholder="hasConfirmedBible ? '只描述你希望改变的部分，未提及的设定会尽量保留' : '告诉 AI 你希望这版设定重点考虑什么'" />
              <p class="bible-dialog-example">{{ hasConfirmedBible ? '例如：保留钟楼和残页规则，弱化超自然表现，整体更偏现实悬疑。' : '例如：现代都市悬疑，不存在真正的鬼怪；故事整体偏压抑，但不要纯黑暗。' }}</p>
            </div>
            <template #footer><el-button @click="bibleGeneratorOpen = false">取消</el-button><el-button type="primary" :loading="generatingBible" :disabled="loadingBible" @click="generateBible">生成</el-button></template>
          </el-dialog>
        </div>
      </el-tab-pane>

      <!-- 角色库 -->
      <el-tab-pane label="角色" name="character">
        <div class="character-editor management-page">
          <header class="editor-page-header">
            <div><h1>角色库</h1><p>查看故事里已经存在的人物，快速掌握他们的定位与状态。</p></div>
            <div class="page-header-actions">
              <!-- AI 补充人物 -->
              <el-button plain :loading="generatingCharacterDraft" @click="openCharacterGenerator">AI 补充人物</el-button>
              <el-button type="primary" @click="openCharacterCreator">＋ 新建角色</el-button>
            </div>
          </header>

          <section class="story-preparation" aria-label="创作准备">
            <div class="story-preparation-head">
              <div>
                <h2>创作准备</h2>
                <span>{{ preparationCompletedCount }}/{{ preparationItems.length }} 项已完成</span>
              </div>
              <small>{{ preparationSummary }}</small>
            </div>
            <el-progress
              :percentage="preparationProgress"
              :show-text="false"
              :stroke-width="4"
              aria-label="创作准备进度"
            />
            <div class="story-preparation-steps">
              <span v-for="item in preparationItems" :key="item.label" :class="{ 'is-complete': item.completed }">
                <i aria-hidden="true">{{ item.completed ? '✓' : '○' }}</i>{{ item.label }}
              </span>
            </div>
          </section>

          <div class="character-toolbar">
            <el-input v-model="characterKeyword" :prefix-icon="Search" clearable size="large" class="character-search" placeholder="搜索姓名、定位、性格或背景" />
            <span>共 {{ characters.length }} 位角色</span>
          </div>

          <section v-loading="loadingCharacters" class="character-library">
            <div v-if="filteredCharacters.length" class="character-grid">
              <el-card v-for="character in filteredCharacters" :key="character.characterCode" class="character-card" shadow="never" role="button" tabindex="0" :aria-label="`编辑角色 ${character.name}`" @click="openCharacterEditor(character)" @keydown.enter="openCharacterEditor(character)">
                <header class="character-card-head">
                  <div class="character-avatar">{{ character.name.trim().slice(0, 1) || '人' }}</div>
                  <div><h2>{{ character.name }}</h2><p>{{ character.ageDescription || genderLabel(character.gender) }}</p></div>
                  <el-tag class="role-tag" size="small" effect="plain" :type="roleTagType(character.roleType)">{{ roleLabel(character.roleType) }}</el-tag>
                </header>
                <div class="character-card-body">
                  <p v-if="character.personality || character.backgroundStory" class="character-card-summary">{{ characterCardSummary(character) }}</p>
                  <p v-if="!character.personality && !character.backgroundStory" class="muted-character">这个角色还没有补充人物小传。</p>
                </div>
                <footer class="character-card-footer">
                  <span><i :class="`life-${character.lifeStatus?.toLowerCase() || 'unknown'}`" />{{ lifeStatusLabel(character.lifeStatus) }}</span>
                  <el-button text circle :icon="Edit" class="character-edit-action" :aria-label="`编辑角色 ${character.name}`" @click.stop="openCharacterEditor(character)" />
                </footer>
              </el-card>
            </div>
            <div v-else-if="!loadingCharacters" class="character-empty">
              <span>人</span>
              <h2>{{ characterKeyword ? '没有找到匹配角色' : '角色库还是空的' }}</h2>
              <p>{{ characterKeyword ? '换一个关键词试试。' : '创建故事中的第一个人物，之后会在这里统一管理。' }}</p>
              <el-button v-if="!characterKeyword" type="primary" @click="openCharacterCreator">新建第一个角色</el-button>
            </div>
          </section>

          <el-drawer v-model="characterCreatorOpen" size="640px" modal-class="character-drawer-modal" :with-header="false" :close-on-click-modal="!deletingChar" :close-on-press-escape="!deletingChar" destroy-on-close>
            <div class="character-create-drawer">
              <header class="drawer-head">
                <div><h1>{{ editingCharacterCode ? '编辑角色' : '新建角色' }}</h1><p>{{ editingCharacterCode ? '修改人物档案，保存后立即用于后续章节生成。' : '从名字和定位开始，依次补充这个人物。' }}</p></div>
                <el-button text circle :icon="Close" aria-label="关闭角色编辑" :disabled="deletingChar" @click="characterCreatorOpen = false" />
              </header>

              <div class="character-form-avatar">{{ cf.name.trim().slice(0, 1) || '人' }}</div>
              <el-form :model="cf" :disabled="deletingChar" label-position="top" class="character-form-stack">
                <el-form-item label="姓名"><el-input v-model="cf.name" size="large" placeholder="角色叫什么名字？" /></el-form-item>
                <el-form-item label="角色定位"><el-select v-model="cf.roleType" size="large" placeholder="选择角色定位"><el-option v-for="option in roleOptions" :key="option.value" :label="option.label" :value="option.value" /></el-select></el-form-item>
                <el-form-item label="性别"><el-select v-model="cf.gender" size="large" placeholder="选择性别"><el-option label="男" value="男" /><el-option label="女" value="女" /><el-option label="其他或未知" value="其他" /></el-select></el-form-item>
                <el-form-item label="年龄"><el-input v-model="cf.ageDescription" size="large" placeholder="例如：二十岁出头" /></el-form-item>
                 <el-form-item label="人物小传"><el-input v-model="cf.backgroundStory" type="textarea" :autosize="{ minRows: 3 }" resize="none" placeholder="他从哪里来？经历过什么？如今为什么会站在故事开始的位置？" /></el-form-item>
                 <el-form-item label="性格与习惯"><el-input v-model="cf.personality" type="textarea" :autosize="{ minRows: 3 }" resize="none" placeholder="性格、习惯、面对压力时的反应方式……" /></el-form-item>
                 <el-form-item label="外形特征"><el-input v-model="cf.appearance" type="textarea" :autosize="{ minRows: 3 }" resize="none" placeholder="外貌特征、衣着和动作习惯……" /></el-form-item>
                 <el-form-item label="作者备注"><el-input v-model="cf.note" type="textarea" :autosize="{ minRows: 2 }" resize="none" placeholder="仅供自己查看的创作备注" /></el-form-item>
              </el-form>
              <footer class="drawer-actions">
                <el-tooltip v-if="editingCharacterCode" content="删除角色" placement="top">
                  <el-button type="danger" text circle class="local-delete-button" :icon="Delete" aria-label="删除角色" :loading="deletingChar" :disabled="savingChar" @click="removeCharacter" />
                </el-tooltip>
                <el-button :disabled="deletingChar" @click="characterCreatorOpen = false">取消</el-button>
                <el-button type="primary" :loading="savingChar" :disabled="deletingChar" @click="saveCharacter">{{ editingCharacterCode ? '保存修改' : '创建角色' }}</el-button>
              </footer>
            </div>
          </el-drawer>

          <!-- AI 补充人物 -->
          <el-dialog v-model="characterGeneratorOpen" title="AI 补充人物" width="860px" class="character-generator-dialog" :close-on-click-modal="false" destroy-on-close>
            <div class="character-generator-copy">
              <p>告诉 AI 你希望补充哪些人物功能、性格或主线关联：</p>
              <el-form label-position="top" class="character-generator-form">
                <el-form-item label="新增数量"><el-input-number v-model="characterPreferredCount" :min="1" :max="15" controls-position="right" /></el-form-item>
                 <el-form-item label="补充要求"><el-input v-model="characterRequirement" type="textarea" :autosize="{ minRows: 3, maxRows: 6 }" resize="none" maxlength="1000" show-word-limit placeholder="例如：补充与核心谜案有关的调查者或盟友，避免重复已有人物" /></el-form-item>
              </el-form>
            </div>
            <section v-if="characterDraftGenerated" class="character-draft-editor">
              <header class="character-draft-head">
                <div><strong>人物草稿</strong><small>AI 生成内容，可继续编辑</small></div>
                <span class="character-draft-count">{{ characterDrafts.length }} 人</span>
              </header>
              <el-empty v-if="!characterDrafts.length" description="当前没有人物草稿" />
              <div v-else class="character-draft-list">
                <article v-for="(draft, index) in characterDrafts" :key="`character-draft-${index}`" class="character-draft-item">
                  <header><strong>人物 {{ index + 1 }}</strong><el-button text type="danger" class="local-delete-button" @click="removeCharacterDraft(index)">删除人物草稿</el-button></header>
                  <el-form :model="draft" label-position="top" class="character-draft-form">
                    <div class="character-draft-grid">
                      <el-form-item label="姓名"><el-input v-model="draft.name" placeholder="人物姓名" /></el-form-item>
                      <el-form-item label="角色定位"><el-select v-model="draft.role" placeholder="选择角色定位"><el-option v-for="option in roleOptions" :key="option.value" :label="option.label" :value="option.value" /></el-select></el-form-item>
                      <el-form-item label="性别"><el-select v-model="draft.gender" placeholder="选择性别"><el-option label="男" value="男" /><el-option label="女" value="女" /><el-option label="其他" value="其他" /></el-select></el-form-item>
                      <el-form-item label="年龄"><el-input v-model="draft.ageDescription" placeholder="年龄或年龄段" /></el-form-item>
                    </div>
                    <el-form-item label="外貌"><el-input v-model="draft.appearance" type="textarea" :autosize="{ minRows: 2 }" resize="none" placeholder="外貌特征" /></el-form-item>
                    <el-form-item label="性格"><el-input v-model="draft.personality" type="textarea" :autosize="{ minRows: 2 }" resize="none" placeholder="性格特征" /></el-form-item>
                    <el-form-item label="背景"><el-input v-model="draft.backgroundStory" type="textarea" :autosize="{ minRows: 3 }" resize="none" placeholder="人物经历与来历" /></el-form-item>
                    <el-form-item label="备注"><el-input v-model="draft.note" type="textarea" :autosize="{ minRows: 2 }" resize="none" placeholder="创作备注" /></el-form-item>
                  </el-form>
                </article>
              </div>
            </section>
            <template #footer>
              <el-button @click="characterGeneratorOpen = false">关闭</el-button>
              <el-button v-if="!characterDraftGenerated" type="primary" :loading="generatingCharacterDraft" @click="generateCharacterDrafts">补充人物</el-button>
              <template v-else>
                <el-button plain :loading="generatingCharacterDraft" @click="generateCharacterDrafts">重新生成</el-button>
                <el-button :loading="discardingCharacterDraft" @click="discardCharacterDraft">放弃</el-button>
                <el-button type="primary" :loading="applyingCharacterDraft" :disabled="!characterDrafts.length" @click="applyCharacterDrafts">应用到角色库</el-button>
              </template>
            </template>
          </el-dialog>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, reactive, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Close, Delete, Edit, Search } from '@element-plus/icons-vue'
import { getBible, saveBible as apiSaveBible, generateStoryBible, generateCharacters, confirmCharacters as apiConfirmCharacters, discardCharacterDraft as apiDiscardCharacterDraft, confirmStoryBible as apiConfirmStoryBible, addCharacter, listCharacters, updateCharacter, deleteCharacter as apiDeleteCharacter } from '../api/project'
import { listOutlineNodes } from '../api/planning'
import { ApiBusinessError } from '../api/http'
import { useProjectStore } from '../stores/project'
import type { AddStoryCharacterRequest, CharacterDraft, CharacterDraftList, ConfirmCharactersRequest, OutlineNode, PlanningDraftResponse, StoryBibleDraft, StoryCharacterResponse } from '../types'
import { createEmptyStoryBibleForm, storyBibleDraftToForm, storyBibleFormToRequest, storyBibleToForm, type StoryBibleForm } from '../utils/storyBible'
import { statusLabel } from '../utils/uiSemantics'

const projectStore = useProjectStore()
const route = useRoute()
const tabByPath: Record<string, string> = {
  '/setup': 'bible',
  '/characters': 'character',
}
const tab = computed(() => tabByPath[route.path] ?? 'bible')

const bf = reactive<StoryBibleForm>(createEmptyStoryBibleForm())
const savingBible = ref(false)
const loadingBible = ref(false)
const generatingBible = ref(false)
const confirmingBible = ref(false)
const bibleDraftId = ref<string | null>(null)
const hasConfirmedBible = ref(false)
const confirmedBibleSnapshot = ref<StoryBibleForm | null>(null)
const bibleStartedEditing = ref(false)
const bibleGeneratorOpen = ref(false)
const bibleRequirement = ref('')
type BibleSectionKey = 'world' | 'rules' | 'power' | 'theme' | 'ending' | 'style'
type BibleSectionNavigationItem = { key: BibleSectionKey; label: string }
const bibleSectionNavigation: BibleSectionNavigationItem[] = [
  { key: 'world', label: '世界设定' },
  { key: 'rules', label: '不可违反的规则' },
  { key: 'power', label: '特殊体系' },
  { key: 'theme', label: '主题与冲突' },
  { key: 'ending', label: '结局方向' },
  { key: 'style', label: '写作风格' },
] as const
const activeBibleSection = ref<BibleSectionKey>('world')
const hasBibleContent = computed(() => [
  bf.oneSentencePremise,
  bf.coreTheme,
  bf.mainConflict,
  bf.endingDirection,
  bf.worldBackground,
  bf.powerName,
  bf.powerDescription,
  bf.powerLevels,
  bf.powerSupplement,
  bf.styleGuide,
  ...bf.hardRules,
].some((value) => value.trim().length > 0))
const showBibleEmpty = computed(() => !hasConfirmedBible.value && !bibleDraftId.value && !bibleStartedEditing.value && !hasBibleContent.value)
let bibleLoadSequence = 0
let characterLoadSequence = 0
const characters = ref<StoryCharacterResponse[]>([])
let outlineLoadSequence = 0
const outlineNodes = ref<OutlineNode[]>([])
const loadingCharacters = ref(false)
const characterKeyword = ref('')
const characterCreatorOpen = ref(false)
const editingCharacterCode = ref<string | null>(null)
const characterGeneratorOpen = ref(false)
const characterPreferredCount = ref(4)
const characterRequirement = ref('')
const generatingCharacterDraft = ref(false)
const applyingCharacterDraft = ref(false)
const discardingCharacterDraft = ref(false)
const characterDraftId = ref<string | null>(null)
const characterDrafts = ref<CharacterDraft[]>([])
const characterDraftGenerated = ref(false)
const preparationItems = computed(() => [
  { label: '世界设定', completed: hasConfirmedBible.value },
  { label: '核心人物', completed: characters.value.length > 0 },
  { label: '故事大纲', completed: outlineNodes.value.length > 0 },
])
const preparationCompletedCount = computed(() => preparationItems.value.filter((item) => item.completed).length)
const preparationProgress = computed(() => Math.round(preparationCompletedCount.value / preparationItems.value.length * 100))
const preparationSummary = computed(() => {
  if (preparationCompletedCount.value === 0) return '从故事设定开始'
  if (preparationCompletedCount.value === preparationItems.value.length) return '基础准备已完成'
  return `还差 ${preparationItems.value.length - preparationCompletedCount.value} 项`
})
const filteredCharacters = computed(() => {
  const keyword = characterKeyword.value.trim().toLowerCase()
  if (!keyword) return characters.value
  return characters.value.filter((character) => [
    character.name,
    roleLabel(character.roleType),
    character.personality,
    character.backgroundStory,
  ].some((value) => value?.toLowerCase().includes(keyword)))
})

function applyBible(form: StoryBibleForm) {
  Object.assign(bf, form)
}

function cloneBibleForm(form: StoryBibleForm): StoryBibleForm {
  return {
    ...form,
    hardRules: [...form.hardRules],
  }
}

function openBibleGenerator() {
  if (hasConfirmedBible.value && !bibleDraftId.value && !confirmedBibleSnapshot.value) {
    confirmedBibleSnapshot.value = cloneBibleForm(bf)
  }
  bibleGeneratorOpen.value = true
}

function startBibleEditing() {
  bibleStartedEditing.value = true
}

function addBibleItem(field: 'hardRules') {
  bf[field].push('')
}

function removeBibleItem(field: 'hardRules', index: number) {
  if (bf[field].length === 1) bf[field][0] = ''
  else bf[field].splice(index, 1)
}

function selectBibleSection(sectionKey: BibleSectionKey) {
  activeBibleSection.value = sectionKey
}

async function loadBible(projectCode: string) {
  const sequence = ++bibleLoadSequence
  loadingBible.value = true
  try {
    const bible = await getBible(projectCode)
    if (sequence === bibleLoadSequence) {
      const loadedForm = storyBibleToForm(bible)
      applyBible(loadedForm)
      hasConfirmedBible.value = Boolean(bible)
      bibleDraftId.value = null
      confirmedBibleSnapshot.value = bible ? cloneBibleForm(loadedForm) : null
      bibleStartedEditing.value = Boolean(bible)
      activeBibleSection.value = 'world'
      bibleGeneratorOpen.value = false
    }
  } finally {
    if (sequence === bibleLoadSequence) loadingBible.value = false
  }
}

async function loadCharacters(projectCode: string) {
  const sequence = ++characterLoadSequence
  loadingCharacters.value = true
  try {
    const result = await listCharacters(projectCode)
    if (sequence === characterLoadSequence) characters.value = result
  } finally {
    if (sequence === characterLoadSequence) loadingCharacters.value = false
  }
}

async function loadOutlineNodes(projectCode: string) {
  const sequence = ++outlineLoadSequence
  try {
    const result = await listOutlineNodes(projectCode)
    if (sequence === outlineLoadSequence) outlineNodes.value = result
  } catch {
    if (sequence === outlineLoadSequence) outlineNodes.value = []
  }
}

watch(
  () => projectStore.active?.projectCode,
  (projectCode) => {
    characterCreatorOpen.value = false
    editingCharacterCode.value = null
    characters.value = []
    outlineNodes.value = []
    hasConfirmedBible.value = false
    activeBibleSection.value = 'world'
    if (projectCode) {
      characterGeneratorOpen.value = false
      characterRequirement.value = ''
      applyingCharacterDraft.value = false
      discardingCharacterDraft.value = false
      characterDraftId.value = null
      characterDrafts.value = []
      characterDraftGenerated.value = false
      void loadBible(projectCode).catch(() => undefined)
      void loadCharacters(projectCode).catch(() => undefined)
      void loadOutlineNodes(projectCode).catch(() => undefined)
    } else {
      bibleLoadSequence += 1
      characterLoadSequence += 1
      outlineLoadSequence += 1
      loadingBible.value = false
      bibleDraftId.value = null
      hasConfirmedBible.value = false
      confirmedBibleSnapshot.value = null
      bibleStartedEditing.value = false
      bibleGeneratorOpen.value = false
      bibleRequirement.value = ''
      loadingCharacters.value = false
      characters.value = []
      characterGeneratorOpen.value = false
      characterRequirement.value = ''
      generatingCharacterDraft.value = false
      applyingCharacterDraft.value = false
      discardingCharacterDraft.value = false
      characterDraftId.value = null
      characterDrafts.value = []
      characterDraftGenerated.value = false
      applyBible(createEmptyStoryBibleForm())
    }
  },
  { immediate: true },
)

watch(
  () => route.path,
  (path) => {
    if (path === '/characters' && projectStore.active?.projectCode) {
      void loadOutlineNodes(projectStore.active.projectCode).catch(() => undefined)
    }
  },
)

async function saveBible() {
  savingBible.value = true
  try {
    const saved = await apiSaveBible(projectStore.active!.projectCode, storyBibleFormToRequest(bf))
    const savedForm = storyBibleToForm(saved)
    applyBible(savedForm)
    hasConfirmedBible.value = true
    confirmedBibleSnapshot.value = cloneBibleForm(savedForm)
    bibleStartedEditing.value = true
    ElMessage.success('世界观已保存')
  } catch (error) {
    if (error instanceof ApiBusinessError) {
      ElMessage.error(error.message)
    } else {
      ElMessage.error('故事设定保存失败，请稍后重试')
    }
  } finally { savingBible.value = false }
}

async function generateBible() {
  generatingBible.value = true
  try {
    const projectCode = projectStore.active!.projectCode
    const revisingBible = hasConfirmedBible.value
    if (revisingBible && !confirmedBibleSnapshot.value) {
      confirmedBibleSnapshot.value = cloneBibleForm(bf)
    }
    const draft: PlanningDraftResponse<StoryBibleDraft> = await generateStoryBible(
      projectCode,
      { requirement: bibleRequirement.value },
    )
    bibleDraftId.value = draft.draftId
    bibleStartedEditing.value = true
    applyBible(storyBibleDraftToForm(draft.payload))
    bibleGeneratorOpen.value = false
    ElMessage.success(revisingBible ? '调整草稿已生成，请修改后应用' : '初始设定草稿已生成，请先修改再确认')
  } catch (error) {
    if (error instanceof ApiBusinessError) {
      ElMessage.error(error.message)
    } else {
      ElMessage.error('故事设定生成失败，请稍后重试')
    }
  } finally { generatingBible.value = false }
}

function discardBibleDraft() {
  const snapshot = confirmedBibleSnapshot.value
  if (snapshot) {
    applyBible(cloneBibleForm(snapshot))
    hasConfirmedBible.value = true
    bibleStartedEditing.value = true
    ElMessage.success('已放弃调整，恢复正式设定')
  } else {
    applyBible(createEmptyStoryBibleForm())
    hasConfirmedBible.value = false
    bibleStartedEditing.value = false
    ElMessage.success('已放弃初始设定草稿')
  }
  bibleDraftId.value = null
  confirmedBibleSnapshot.value = null
  bibleGeneratorOpen.value = false
}

async function confirmBible() {
  if (!bibleDraftId.value) return
  const applyingRevision = hasConfirmedBible.value
  confirmingBible.value = true
  try {
    const saved = await apiConfirmStoryBible(
      projectStore.active!.projectCode,
      { draftId: bibleDraftId.value, ...storyBibleFormToRequest(bf) },
    )
    const savedForm = storyBibleToForm(saved)
    applyBible(savedForm)
    bibleDraftId.value = null
    hasConfirmedBible.value = true
    confirmedBibleSnapshot.value = cloneBibleForm(savedForm)
    bibleStartedEditing.value = true
    bibleGeneratorOpen.value = false
    ElMessage.success(applyingRevision ? '故事圣经修改已应用' : '故事圣经已确认并保存')
  } catch (error) {
    if (error instanceof ApiBusinessError) {
      ElMessage.error(error.message)
    } else {
      ElMessage.error('故事设定应用失败，请稍后重试')
    }
  } finally { confirmingBible.value = false }
}

type CharacterForm = AddStoryCharacterRequest

function emptyCharacter(): CharacterForm {
  return {
    name: '', roleType: '男主角', gender: '其他',
    ageDescription: '', appearance: '', personality: '', backgroundStory: '', note: '',
  }
}
const cf = reactive<CharacterForm>(emptyCharacter())
const roleOptions = [
  { label: '男主角', value: '男主角' }, { label: '女主角', value: '女主角' },
  { label: '盟友', value: '盟友' }, { label: '对手', value: '对手' },
  { label: '反派', value: '反派' }, { label: '配角', value: '配角' },
]
const savingChar = ref(false)
const deletingChar = ref(false)
function openCharacterGenerator() {
  characterGeneratorOpen.value = true
}

async function generateCharacterDrafts() {
  if (!projectStore.active) return
  generatingCharacterDraft.value = true
  try {
    const draft: PlanningDraftResponse<CharacterDraftList> = await generateCharacters(
      projectStore.active.projectCode,
      {
        preferredCount: characterPreferredCount.value,
        requirement: characterRequirement.value,
      },
    )
    characterDraftId.value = draft.draftId
    characterDrafts.value = Array.isArray(draft.payload?.characters)
      ? draft.payload.characters.map((character) => ({ ...character }))
      : []
    characterDraftGenerated.value = true
    ElMessage.success(characterDrafts.value.length ? '核心角色草稿已生成，可继续编辑' : '本次没有生成合适的人物')
  } catch (error) {
    if (error instanceof ApiBusinessError) {
      ElMessage.error(error.message)
    } else {
      ElMessage.error('人物草稿生成失败，请稍后重试')
    }
  } finally {
    generatingCharacterDraft.value = false
  }
}

function removeCharacterDraft(index: number) {
  characterDrafts.value.splice(index, 1)
}

function clearCharacterDraftState() {
  characterPreferredCount.value = 4
  characterRequirement.value = ''
  characterDraftId.value = null
  characterDrafts.value = []
  characterDraftGenerated.value = false
}

async function discardCharacterDraft() {
  if (!projectStore.active) return
  if (!characterDraftId.value) {
    clearCharacterDraftState()
    characterGeneratorOpen.value = false
    return
  }
  discardingCharacterDraft.value = true
  try {
    await apiDiscardCharacterDraft(projectStore.active.projectCode, characterDraftId.value)
    clearCharacterDraftState()
    characterGeneratorOpen.value = false
    ElMessage.success('已放弃人物草稿')
  } catch (error) {
    if (error instanceof ApiBusinessError) {
      ElMessage.error(error.message)
    } else {
      ElMessage.error('放弃人物草稿失败，请稍后重试')
    }
  } finally {
    discardingCharacterDraft.value = false
  }
}

async function applyCharacterDrafts() {
  if (!projectStore.active || !characterDraftId.value) return
  if (!characterDrafts.value.length) {
    ElMessage.warning('至少保留一个人物草稿后再应用')
    return
  }
  applyingCharacterDraft.value = true
  try {
    const request: ConfirmCharactersRequest = {
      draftId: characterDraftId.value,
      characters: characterDrafts.value.map((character) => ({ ...character })),
    }
    await apiConfirmCharacters(projectStore.active.projectCode, request)
    await loadCharacters(projectStore.active.projectCode)
    clearCharacterDraftState()
    characterGeneratorOpen.value = false
    ElMessage.success('人物草稿已应用到角色库')
  } catch (error) {
    if (error instanceof ApiBusinessError) {
      ElMessage.error(error.message)
    } else {
      ElMessage.error('人物草稿应用失败，请稍后重试')
    }
  } finally {
    applyingCharacterDraft.value = false
  }
}

function openCharacterCreator() {
  Object.assign(cf, emptyCharacter())
  editingCharacterCode.value = null
  characterCreatorOpen.value = true
}

function openCharacterEditor(character: StoryCharacterResponse) {
  Object.assign(cf, emptyCharacter(), {
    name: character.name,
    roleType: roleFormValue(character.roleType),
    gender: genderFormValue(character.gender),
    ageDescription: character.ageDescription,
    appearance: character.appearance,
    personality: character.personality,
    backgroundStory: character.backgroundStory,
    note: character.note,
  })
  editingCharacterCode.value = character.characterCode
  characterCreatorOpen.value = true
}

async function saveCharacter() {
  if (deletingChar.value || savingChar.value) return
  if (!cf.name.trim()) { ElMessage.warning('请填写角色姓名'); return }
  savingChar.value = true
  try {
    const projectCode = projectStore.active!.projectCode
    const characterData = {
      name: cf.name,
      roleType: cf.roleType,
      gender: cf.gender,
      ageDescription: cf.ageDescription,
      appearance: cf.appearance,
      personality: cf.personality,
      backgroundStory: cf.backgroundStory,
      note: cf.note,
    }
    if (editingCharacterCode.value) {
      const result = await updateCharacter(projectCode, editingCharacterCode.value, {
        ...characterData,
      })
      if (result.warning) ElMessage.warning(result.warning)
    } else {
      await addCharacter(projectCode, characterData)
    }
    await loadCharacters(projectCode)
    characterCreatorOpen.value = false
    ElMessage.success(editingCharacterCode.value ? '角色已更新' : '角色已添加')
  } catch (error) {
    if (error instanceof ApiBusinessError) {
      ElMessage.error(error.message)
    } else {
      ElMessage.error('角色保存失败，请稍后重试')
    }
  } finally { savingChar.value = false }
}

async function removeCharacter() {
  const projectCode = projectStore.active?.projectCode
  const characterCode = editingCharacterCode.value
  const character = characters.value.find((item) => item.characterCode === characterCode)
  if (!projectCode || !characterCode || !character || deletingChar.value || savingChar.value) return

  deletingChar.value = true
  try {
    try {
      await ElMessageBox.confirm(
        `确认删除角色“${character.name}”吗？此操作无法撤销。`,
        '删除角色',
        { type: 'warning', confirmButtonText: '删除角色', cancelButtonText: '取消' },
      )
    } catch {
      return
    }
    if (projectStore.active?.projectCode !== projectCode || editingCharacterCode.value !== characterCode) return

    await apiDeleteCharacter(projectCode, characterCode)
    if (projectStore.active?.projectCode !== projectCode) return
    characterCreatorOpen.value = false
    editingCharacterCode.value = null
    await loadCharacters(projectCode)
    if (projectStore.active?.projectCode === projectCode) ElMessage.success('角色已删除')
  } catch (error) {
    if (error instanceof ApiBusinessError) {
      ElMessage.error(error.message)
    } else {
      ElMessage.error('角色删除失败，请稍后重试')
    }
  } finally {
    deletingChar.value = false
  }
}

const roleNames: Record<string, string> = { MALE_LEAD: '男主角', FEMALE_LEAD: '女主角', ALLY: '盟友', RIVAL: '对手', ANTAGONIST: '反派', SUPPORTING: '配角' }
function roleLabel(value: string) { return roleNames[value] ?? value }
function roleTagType(value: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  if (value === 'ANTAGONIST') return 'danger'
  if (value === 'RIVAL') return 'warning'
  if (value === 'ALLY') return 'success'
  if (value === 'SUPPORTING') return 'info'
  return 'primary'
}
function characterCardSummary(character: StoryCharacterResponse) {
  return [character.personality, character.backgroundStory]
    .map((value) => value?.trim())
    .filter((value): value is string => Boolean(value))
    .join(' · ')
}
function genderLabel(value: string) { return ({ MALE: '男性', FEMALE: '女性', OTHER: '性别未设定' } as Record<string, string>)[value] ?? value }
function lifeStatusLabel(value?: string) { return ({ ALIVE: '在场', DEAD: '已故', MISSING: '失踪', UNKNOWN: '状态未知' } as Record<string, string>)[value || 'UNKNOWN'] ?? '状态未知' }
function roleFormValue(value: string) { return ({ MALE_LEAD: '男主角', FEMALE_LEAD: '女主角', ALLY: '盟友', RIVAL: '对手', ANTAGONIST: '反派', SUPPORTING: '配角' } as Record<string, string>)[value] ?? value }
function genderFormValue(value: string) { return ({ MALE: '男', FEMALE: '女', OTHER: '其他' } as Record<string, string>)[value] ?? value }
</script>

<style scoped>
.setup-wrap { width: 100%; }

.setup-tabs :deep(.el-tabs__header) { display: none; margin-bottom: 0; }
.setup-tabs :deep(.el-tabs__nav-wrap) { padding: 0 2px; }
.setup-tabs :deep(.el-tabs__content) { overflow: visible; }

/* Story bible editor */
.bible-editor { width: 100%; min-width: 0; padding: clamp(18px, 2.2vw, 34px) 0 72px; }
.page-header-actions { display: flex; align-items: center; justify-content: flex-end; flex-wrap: wrap; gap: 8px; }
.bible-content-layout { width: min(100%, 1260px); min-width: 0; display: grid; grid-template-columns: clamp(200px, 18vw, 220px) minmax(0, 900px); align-items: start; justify-content: start; gap: clamp(32px, 3vw, 48px); margin-inline: 0 auto; }
.bible-section-nav { position: sticky; top: 24px; min-width: 0; padding: 12px 0 18px; }
.bible-section-nav-title { display: block; margin: 0 10px 10px; color: var(--text-primary); font: 700 13px/1.5 "Songti SC", "STSong", serif; }
.bible-section-nav-list { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.bible-section-nav-select { display: none; }
.bible-section-nav-button { width: 100%; min-width: 0; justify-content: flex-start; margin-left: 0 !important; padding: 8px 10px !important; border-radius: 6px !important; color: var(--text-muted) !important; font-size: 12px !important; font-weight: 500; text-align: left; white-space: nowrap; }
.bible-section-nav-button:hover { color: var(--primary) !important; background: var(--primary-dim) !important; }
.bible-section-nav-button.is-active { color: var(--primary) !important; background: var(--primary-dim) !important; font-weight: 700; }

/* Story Bible canvas */
.bible-empty { min-height: 460px; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 48px 20px; border: 1px dashed var(--border); background: var(--surface); text-align: center; }
.bible-empty-mark { width: 58px; height: 58px; display: grid; place-items: center; margin-bottom: 18px; border-radius: 50%; background: var(--primary-dim); color: var(--primary); font: 700 24px "Songti SC", serif; }
.bible-empty h2 { color: var(--text-primary); font: 700 var(--font-size-section-title)/1.4 "Songti SC", serif; }
.bible-empty p { margin-top: 8px; color: var(--text-faint); font-size: 12px; line-height: 1.9; }
.bible-empty-actions { display: flex; flex-wrap: wrap; justify-content: center; gap: 9px; margin-top: 22px; }
.bible-empty-actions .el-button { margin-left: 0; }
.story-bible-canvas { width: 100%; min-width: 0; min-height: 0; background: transparent; }
.bible-draft-banner { display: flex; align-items: center; justify-content: space-between; gap: 18px; padding: 22px clamp(18px, 3.5vw, 42px); border-bottom: 1px solid var(--warning-border); background: var(--warning-surface); }
.bible-draft-banner > div:first-child { min-width: 0; }
.bible-draft-banner h2 { margin-top: 3px; color: var(--warning); font: 700 var(--font-size-section-title)/1.4 "Songti SC", serif; }
.bible-draft-banner p { margin-top: 4px; color: var(--text-secondary); font-size: 11px; line-height: 1.7; }
.bible-draft-kicker { color: var(--warning); font: 700 10px ui-monospace, SFMono-Regular, Menlo, monospace; letter-spacing: .16em; }
.bible-draft-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.bible-draft-banner .el-button { flex-shrink: 0; margin-left: 0; }
.bible-section { width: 100%; box-sizing: border-box; min-width: 0; padding: 24px 0 34px; }
.section-title-block { min-width: 0; margin-bottom: 18px; }
.section-title-block > div { min-width: 0; }
.section-title-block h2 { color: var(--text-primary); font: 700 var(--font-size-section-title)/1.4 "Songti SC", "STSong", serif; }
.section-title-block p { margin-top: 4px; color: var(--text-faint); font-size: 11px; line-height: 1.6; }
.world-section { padding-top: 26px; }
.bible-writing-field, .bible-field { position: relative; min-width: 0; display: flex; flex-direction: column; }
.bible-writing-field > span, .bible-field > span { color: var(--text); font-size: 12px; font-weight: 700; }
.bible-writing-field > small { max-width: 760px; margin: 5px 0 12px; color: var(--text-faint); font-size: 11px; line-height: 1.7; }
.bible-writing-field :deep(.el-textarea__inner), .bible-field :deep(.el-textarea__inner) { color: var(--text-secondary); font-size: 13px; line-height: 1.9; }
.rules-section { padding-top: 24px; }
.rules-section .section-title-block { margin-bottom: 14px; }
.rules-list { display: flex; flex-direction: column; gap: 7px; }
.rule-row { min-width: 0; display: grid; grid-template-columns: 26px minmax(0, 1fr) auto; align-items: center; gap: 9px; }
.rule-number { width: 24px; height: 24px; display: grid; place-items: center; border-radius: 50%; background: var(--primary-dim); color: var(--primary); font: 700 10px ui-monospace, monospace; }
.rule-row :deep(.el-input__wrapper) { min-height: 36px; padding: 6px 0 !important; border: 0 !important; border-bottom: 1px solid var(--border) !important; border-radius: 0 !important; background: transparent !important; box-shadow: none !important; }
.rule-row :deep(.el-input__wrapper:hover) { border: 0 !important; border-bottom: 1px solid var(--border-strong) !important; box-shadow: none !important; }
.rule-row :deep(.el-input__wrapper.is-focus) { border: 0 !important; border-bottom: 1px solid var(--primary-focus) !important; box-shadow: none !important; }
.rule-row :deep(.el-input__inner) { padding: 0 !important; background: transparent !important; }
.rule-row .el-button { margin-left: 0; }
.add-rule-button { margin-top: 10px; margin-left: 35px !important; }
.secondary-section-body { padding: 0 2px; }
.secondary-fields, .direction-fields { display: grid; grid-template-columns: minmax(0, 1fr); gap: 20px; }
.bible-field > span { margin-bottom: 7px; color: var(--text-muted); }
.bible-field > small { margin: -2px 0 8px; color: var(--text-faint); font-size: 11px; line-height: 1.6; }
.bible-field :deep(.el-input__wrapper) { padding: 8px 0 !important; border: 0 !important; border-bottom: 1px solid var(--border) !important; border-radius: 0 !important; background: transparent !important; box-shadow: none !important; }
.bible-field :deep(.el-input__wrapper.is-focus) { border-bottom-color: var(--primary) !important; }
.bible-writing-field :deep(.el-textarea__inner), .long-text-field :deep(.el-textarea__inner) { padding: 0 0 6px !important; border: 0 !important; border-bottom: 1px solid var(--border) !important; border-radius: 0 !important; background: transparent !important; box-shadow: none !important; color: var(--text-secondary) !important; font-size: 14px; line-height: 1.9; resize: none !important; overflow-y: hidden !important; }
.bible-writing-field :deep(.el-textarea__inner:hover), .long-text-field :deep(.el-textarea__inner:hover) { border-bottom-color: var(--border-strong) !important; box-shadow: none !important; }
.bible-writing-field :deep(.el-textarea__inner:focus), .long-text-field :deep(.el-textarea__inner:focus) { border-bottom-color: var(--primary-focus) !important; box-shadow: none !important; outline: none; }
.secondary-note { margin: -4px 0 20px; color: var(--text-faint); font-size: 11px; line-height: 1.7; }
.bible-dialog-copy > p:first-child { margin-bottom: 9px; color: var(--text); font-size: 12px; font-weight: 600; }
.bible-dialog-copy :deep(.el-textarea__inner) { line-height: 1.8; }
.bible-dialog-example { margin-top: 10px; color: var(--text-faint); font-size: 11px; line-height: 1.7; }
.bible-dialog-copy { min-width: 0; }
:global(.story-bible-generator-dialog) { width: min(620px, calc(100% - 24px)) !important; }
:global(.story-bible-generator-dialog .el-dialog__body) { padding-top: 2px; }

@media (max-width: 1080px) {
  .bible-content-layout { grid-template-columns: minmax(0, 1fr); gap: 8px; }
  .bible-section-nav { position: static; display: flex; align-items: center; gap: 8px; min-width: 0; padding: 0 0 8px; border-bottom: 1px solid var(--border); }
  .bible-section-nav-title { flex: 0 0 auto; margin: 0 4px 0 0; }
  .bible-section-nav-list { display: none; }
  .bible-section-nav-select { display: block; flex: 1 1 auto; width: 100%; min-width: 0; }
  .bible-draft-banner { align-items: stretch; flex-direction: column; }
  .bible-draft-banner .el-button { align-self: flex-start; }
}

@media (max-width: 600px) {
  .bible-empty { min-height: 400px; padding-inline: 16px; }
  .bible-empty-actions { width: 100%; flex-direction: column; }
  .bible-empty-actions .el-button { width: 100%; margin-left: 0; }
.bible-section { padding-inline: 18px; }
.rule-row { grid-template-columns: 24px minmax(0, 1fr) auto; gap: 6px; }
  .add-rule-button { margin-left: 30px !important; }
}

/* Outline and character editors */
.outline-editor, .character-editor { width: 100%; min-width: 0; padding: clamp(18px, 2.2vw, 34px) 0 72px; }
.story-preparation { margin-bottom: 24px; padding: 12px 0 16px; border-bottom: 1px solid var(--border); }
.story-preparation-head { display: flex; align-items: baseline; justify-content: space-between; gap: 16px; margin-bottom: 10px; }
.story-preparation-head > div { display: flex; align-items: baseline; gap: 10px; }
.story-preparation h2 { color: var(--text); font: 700 14px/1.4 "Songti SC", "STSong", serif; }
.story-preparation-head span,
.story-preparation-head small { color: var(--text-faint); font-size: 10px; }
.story-preparation-head small { white-space: nowrap; }
.story-preparation :deep(.el-progress-bar__outer) { background: var(--surface2); }
.story-preparation :deep(.el-progress-bar__inner) { background: var(--primary); }
.story-preparation-steps { display: flex; flex-wrap: wrap; gap: 6px 22px; margin-top: 10px; }
.story-preparation-steps span { display: inline-flex; align-items: center; gap: 5px; color: var(--text-faint); font-size: 11px; }
.story-preparation-steps span.is-complete { color: var(--text-muted); }
.story-preparation-steps i { color: var(--text-faint); font-size: 12px; font-style: normal; line-height: 1; }
.story-preparation-steps span.is-complete i { color: var(--success-strong); font-weight: 700; }
.character-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 18px; margin-bottom: 16px; }
.character-toolbar > span { flex-shrink: 0; color: var(--text-faint); font-size: 10px; }
.character-search { width: clamp(260px, 36vw, 520px); }
.character-search :deep(.el-input__wrapper) { padding-inline: 14px; border-radius: 9px; background: var(--surface); box-shadow: 0 0 0 1px var(--border) inset, 0 3px 12px rgba(52, 62, 88, .04) !important; }
.character-search :deep(.el-input__wrapper.is-focus) { box-shadow: 0 0 0 1px var(--primary) inset, 0 4px 16px rgba(79, 109, 245, .09) !important; }
.character-search :deep(.el-input__prefix) { color: var(--primary); font-size: 16px; }
.character-library { min-height: 440px; }
.character-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(min(320px, 100%), 1fr)); align-items: stretch; gap: clamp(12px, 1.4vw, 20px); }
.character-card { min-width: 0; height: 100%; min-height: 286px; overflow: hidden; border: 1px solid var(--border); border-top: 3px solid var(--primary-soft); background: var(--surface); color: inherit; cursor: pointer; transition: border-color .16s, transform .16s, box-shadow .16s; }
.character-card :deep(.el-card__body) { height: 100%; display: flex; flex-direction: column; padding: 0; }
.character-card:hover { border-color: var(--primary-focus); transform: translateY(-2px); box-shadow: 0 8px 24px rgba(44, 55, 86, .07); }
.character-card-head { display: grid; grid-template-columns: 52px minmax(0, 1fr) auto; align-items: center; gap: 12px; padding: 18px 18px 14px; }
.character-avatar { width: 52px; height: 52px; display: grid; place-items: center; border-radius: 8px; background: var(--primary-dim); color: var(--primary); font: 700 21px "Songti SC", serif; }
.character-card h2 { overflow: hidden; color: var(--text); font: 700 17px/1.4 "Songti SC", serif; text-overflow: ellipsis; white-space: nowrap; }
.character-card header p { margin-top: 2px; color: var(--text-faint); font-size: 10px; }
.role-tag { align-self: start; flex-shrink: 0; }
.character-card-body { flex: 1; min-height: 130px; padding: 4px 18px 18px; }
.character-card-body p { display: -webkit-box; margin-top: 10px; overflow: hidden; color: var(--text-muted); font-size: 11px; line-height: 1.75; -webkit-box-orient: vertical; -webkit-line-clamp: 4; }
.character-card-body .muted-character { color: var(--text-faint); }
.character-card-footer { min-height: 46px; display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 10px 18px; border-top: 1px solid var(--border); background: var(--surface-subtle); }
.character-card-footer span { display: flex; align-items: center; gap: 6px; color: var(--text-faint); font-size: 9px; }
.character-card-footer i { width: 6px; height: 6px; border-radius: 50%; background: var(--text-tertiary); }
.character-card-footer i.life-alive { background: var(--success-strong); }
.character-card-footer i.life-dead { background: var(--text-secondary); }
.character-card-footer i.life-missing { background: var(--warning); }
.character-edit-action { opacity: 0; color: var(--primary); transition: opacity .16s ease, background-color .16s ease; }
.character-card:hover .character-edit-action,
.character-card:focus-within .character-edit-action { opacity: 1; }
.character-card:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }
.character-empty { min-height: 420px; display: flex; flex-direction: column; align-items: center; justify-content: center; border: 1px dashed var(--border); background: rgba(255,255,255,.55); text-align: center; }
.character-empty > span { width: 52px; height: 52px; display: grid; place-items: center; margin-bottom: 13px; border-radius: 50%; background: var(--primary-dim); color: var(--primary); font: 700 20px serif; }
.character-empty h2 { color: var(--text); font-size: 15px; }
.character-empty p { margin: 5px 0 16px; color: var(--text-faint); font-size: 11px; }
.character-create-drawer { min-height: 100%; padding: 28px clamp(18px, 3vw, 34px) 34px; background: var(--bg); }
:global(.character-drawer-modal .el-drawer) { width: min(640px, calc(100% - 16px)) !important; }
:global(.character-generator-dialog.el-dialog) { width: min(860px, calc(100% - 30px)) !important; }
.drawer-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 18px; margin-bottom: 20px; padding-bottom: 18px; border-bottom: 1px solid var(--border); }
.drawer-head h1 { margin-top: 4px; font: 700 25px/1.35 "Songti SC", serif; }
.drawer-head p { margin-top: 3px; color: var(--text-faint); font-size: 10px; }
.character-form-avatar { width: 64px; height: 64px; display: grid; place-items: center; margin: 0 auto 24px; border-radius: 16px; background: var(--primary-dim); color: var(--primary); font: 700 25px "Songti SC", serif; }
.character-form-stack { padding: 24px clamp(18px, 3vw, 30px) 8px; border: 1px solid var(--border); border-top: 3px solid var(--primary); background: var(--surface); }
.character-form-stack :deep(.el-form-item) { margin-bottom: 21px; }
.character-form-stack :deep(.el-form-item__label) { height: auto; margin-bottom: 7px; color: var(--text-muted); font-size: 11px; font-weight: 700; line-height: 1.4; }
.character-form-stack :deep(.el-select) { width: 100%; }
.character-form-stack :deep(.el-input__wrapper), .character-form-stack :deep(.el-textarea__inner) { border-radius: 7px; background: var(--surface-subtle); box-shadow: 0 0 0 1px var(--border) inset !important; }
.character-form-stack :deep(.el-input__wrapper.is-focus), .character-form-stack :deep(.el-textarea__inner:focus) { background: var(--surface); box-shadow: 0 0 0 1px var(--primary) inset !important; }
.character-form-stack :deep(.el-textarea__inner) { line-height: 1.8; }
.drawer-actions { position: sticky; bottom: -34px; display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px; margin-top: 16px; padding: 16px 0 0; background: linear-gradient(transparent, var(--bg) 28%); }
.drawer-actions .el-button { margin-left: 0; }
.character-generator-copy p { margin-bottom: 10px; color: var(--text-muted); font-size: 12px; line-height: 1.7; }
.character-generator-copy :deep(.el-textarea__inner) { resize: none; line-height: 1.8; }
.character-draft-editor { margin-top: 24px; padding-top: 20px; border-top: 1px solid var(--border); }
.character-draft-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.character-draft-head > div { display: flex; align-items: baseline; gap: 10px; }
.character-draft-head strong { color: var(--text); font-size: 15px; }
.character-draft-head small { color: var(--text-faint); font-size: 10px; }
.character-draft-count { color: var(--text-faint); font-size: 11px; white-space: nowrap; }
.character-draft-list { display: flex; flex-direction: column; gap: 16px; }
.character-draft-item { padding: 18px; border: 1px solid var(--border); border-top: 3px solid var(--primary-soft); background: var(--surface); }
.character-draft-item > header { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.character-draft-item > header strong { color: var(--text); font-size: 13px; }
.character-draft-form :deep(.el-form-item) { margin-bottom: 14px; }
.character-draft-form :deep(.el-form-item__label) { height: auto; margin-bottom: 5px; color: var(--text-muted); font-size: 11px; line-height: 1.4; }
.character-draft-form :deep(.el-select) { width: 100%; }
.character-draft-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 12px; }
.character-draft-form :deep(.el-input__wrapper), .character-draft-form :deep(.el-textarea__inner) { border-radius: 6px; background: var(--surface-subtle); box-shadow: 0 0 0 1px var(--border) inset !important; }
.character-draft-form :deep(.el-input__wrapper.is-focus), .character-draft-form :deep(.el-textarea__inner:focus) { background: var(--surface); box-shadow: 0 0 0 1px var(--primary) inset !important; }
.character-draft-form :deep(.el-textarea__inner) { line-height: 1.8; }
@media (max-width: 600px) {
  .form-grid.two, .form-grid.three { grid-template-columns: 1fr; }
  .bible-editor { padding-top: 18px; }
  .story-preparation-head { align-items: flex-start; flex-direction: column; gap: 4px; }
  .page-header-actions { align-items: stretch; flex-direction: column; }
  .page-header-actions .el-button { width: 100%; margin-left: 0; }
  .page-header-actions .el-tag { align-self: flex-start; }
  .character-editor { padding-top: 18px; }
  .character-editor .page-header-actions { display: grid; grid-template-columns: 1fr 1fr; }
  .character-identity { grid-template-columns: 54px minmax(0, 1fr); gap: 14px; padding: 18px; }
  .avatar-placeholder { width: 54px; height: 54px; font-size: 20px; }
  .character-toolbar { align-items: stretch; flex-direction: column; gap: 8px; }
  .character-search { width: 100%; }
  .character-toolbar > span { align-self: flex-end; }
  .character-card-head { grid-template-columns: 46px minmax(0, 1fr) auto; padding-inline: 14px; }
  .character-avatar { width: 46px; height: 46px; }
  .character-card-body, .character-card-footer { padding-inline: 14px; }
  .character-draft-grid { grid-template-columns: 1fr; }
  .character-draft-item { padding: 14px; }
}

:deep(.code-input .el-textarea__inner) {
  font-family: 'JetBrains Mono', 'Fira Code', 'Cascadia Code', monospace;
  font-size: 12px;
}

.section-label {
  font-size: 11px;
  font-weight: 700;
  color: var(--text-faint);
  text-transform: uppercase;
  letter-spacing: .1em;
  margin-bottom: 12px;
  padding-bottom: 6px;
  border-bottom: 1px solid var(--border);
}

</style>
