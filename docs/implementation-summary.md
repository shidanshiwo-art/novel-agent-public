# 实现工作总结

## 删除尾卷后恢复上一卷章节规划范围（2026-09-08）

- `novel-agent-infrastructure/src/main/java/cn/ninth/novel/infrastructure/adapter/repository/PlanningRepository.java` 在删除大纲节点的事务内锁定 BOOK 下的 Volume 兄弟节点；仅当待删节点是尾 Volume 时，删除后将上一 Volume 的 `endChapter` 恢复到 BOOK 终点，使其重新成为可继续规划章节的活动卷。
- ARC、非尾 Volume 和无前置 Volume 的删除仍沿用原有语义；`PlanningService.deleteOutlineNode()` 的叶节点及 ChapterPlan 保护逻辑未改变，序号由现有连续兄弟状态继续复用。
- `PlanningService.normalizeCreatedOutline()` 创建 ARC 时先走 `requireActiveVolumeForChapterOutline()`，与下一章生成路径统一活动卷校验；`novel-agent-app/src/test` 补充真实 MySQL 回归：卷一生成第 1 章、创建/删除卷二、重新查询卷一范围、继续生成第 2 章、重复创建/删除，并校验章节范围连续、Volume sibling sequence 稳定及普通 ARC 删除不改变范围；领域测试覆盖带 ChapterPlan 的 Volume 禁止删除和历史卷 ARC 创建拦截。
- 验证结果：尾卷恢复真实 MySQL 回归 1 项通过；`ManualOutlineCrudTest` 16 项通过；`novel-agent-infrastructure` 与 `novel-agent-app` 编译通过；`git diff --check` 通过。

## 章节生成失败后的立即重试与资源释放（2026-09-08）

- `novel-agent-domain/src/main/java/cn/ninth/novel/domain/chapter/service/ChapterService.java` 保存 checkpoint saver，在同步生成、异步 generation session 和人工恢复的终态 `finally` 中释放 LangGraph thread；仅非终态 HUMAN/REVIEW_FAILED 结果保留 checkpoint，成功路径和人工恢复语义不变，异常与 `Error` 仍按原有业务错误边界处理。
- `ChapterGenerationMetricsRecorder` 增加幂等 `releaseSession`，清理 JVM 内 `sessionLocks` 与 `sessionIdentities`；MySQL checkpoint thread 释放改为幂等，避免框架正常释放后服务兜底释放因 0 行更新再次失败。
- `ChapterGenerationSessionRegistry` 在终态保留五分钟快照供刷新回放，同时将 DRAFT in-progress 标记归零并清理 SSE listener 和 DRAFT Disposable；暂停的 REVIEW_FAILED 会话收到 STOP 时也会释放 checkpoint 和指标 JVM 状态。SSE Controller 统一使用幂等取消订阅回调，历史回放先登记 listener 以覆盖回放期间断开竞态；断开只释放连接资源，不取消可恢复的服务端工作流。
- `novel-agent-web/src/views/GenerateView.vue` 为 FAILED/CANCELLED 状态补充“重新生成”操作，刷新后保留失败信息但可立即创建新 generation session；新增并更新 `novel-agent-app/src/test` 下的失败重试、指标释放、SSE、页面错误和 checkpoint 测试。
- 验证结果：章节异步 `LOAD_CONTEXT` 失败后立即重试回归通过；相关 ChapterService/Session/Metrics/SSE/Page 定向测试 34 项通过；人工 checkpoint 恢复测试 4 项通过；真实 MySQL checkpoint 释放幂等测试 1 项通过；前端 `npm run build` 通过；`git diff --check` 通过。

## Task C：模型 Token Usage 采集（2026-09-07）

- 基于 Spring AI `ChatResponse`/`CallResponseSpec` 的 `metadata.usage` 接入 `promptTokens`、`completionTokens` 和 `totalTokens`，统一映射为 `inputTokens`、`outputTokens`、`totalTokens`；流式响应保留最后一个可用 Usage。
- 结构化响应发生内部 JSON 重试时合并各次响应 Usage，避免重复模型响应只保留最后一次 Token。
- DRAFT、REVIEW、REVISE、COMPRESSION 节点分别累计模型调用次数和 Usage；REVIEW/REVISE 的调用重试仍复用原有 retry executor，指标侧不改变 `retryCount` 的差值语义。
- Usage 缺失时 Token 字段保持 `NULL`，不按文本长度估算；指标仓储、查询和回调异常均隔离在旁路采集逻辑内，不影响正文生成和工作流路由。
- 新增 generation session 指标持久化、章节会话明细/单会话明细查询和项目汇总查询；更新 `docs/sql/schema.sql`、MyBatis 映射、数据库设计文档和指标测试。
- 验证结果：clean compile 通过；`GenerationMetricsDeltaTest`、`MvpPersistenceContractTest` 共 4 项通过；`GenerationMetricsRepositoryIntegrationTest` 共 2 项通过；指标采集器、查询 HTTP 和结构化输出定向测试分别通过 2、2、3 项。

## 历史文档与旧计划清理（2026-09-07）

- 删除已完成且不再承担当前设计约束的一次性实施计划、角色收敛方案和旧工作流审查材料。
- 保留当前工作流、数据库设计和本实现总结；实现总结仅作为已完成工作的记录，不作为待执行计划。
- 清理本总结中对已删除计划和审查材料的文件路径引用，避免文档断链。
- 验证结果：文档引用扫描无旧计划目录和已删除审查材料引用；`git diff --check` 通过。

## 旧兼容代码与死代码清理（2026-09-07）

- 清理角色 PO/VO、章节人物实体中的旧字段和空返回兼容访问器，统一测试夹具使用当前角色模型字段。
- 删除 `StoryChapterPO`、`IOutlineNodeDao` 的旧命名桥接，删除 `ChapterMemoryResponse` 的旧构造器，并将异常工厂统一为 `AppException.internal(...)`。
- 更新 `novel-agent-app/src/test` 下受影响的控制器、仓储、章节节点和结构化输出测试；保留仍承担历史数据读取职责的 JSON/文本兼容解析。
- 验证结果：7 个 Maven 模块编译成功，相关定向测试 60 项全部通过；旧兼容符号扫描无生产代码残留；`git diff --check` 通过。

## 历史规划契约测试清理（2026-09-07）

- 删除 `novel-agent-app/src/test/java/cn/ninth/novel/web` 下与已下线规划接口、旧规划模型和批量兼容能力绑定的 5 个历史契约测试。
- 保留当前规划服务、仓储、HTTP 接口和前端契约测试，测试目录结构保持不变。
- 验证结果：删除后 142 个测试源编译通过，规划仓储排序测试通过；另一个既有持久化契约测试因当前 SQL 多出 `chapter_model_trace` 与旧表集合期望不一致而未通过；`git diff --check` 通过。

## Task 84：Generate 页底部状态与操作收口（2026-09-06）

- `novel-agent-web/src/views/GenerateView.vue` 移除 REVIEWING、REVISING、COMPRESSING、PERSISTING 等阶段的底部纯状态说明，同时删除生成中、等待确认和已完成区域中的重复状态文案；工作流状态继续由顶部章节工具栏状态胶囊表达。
- 底部 `contextual-action-bar` 仅在存在可执行控件的 IDLE、DRAFTING、REVIEW_FAILED、WAITING_HUMAN、COMPLETED 阶段渲染；停止生成、重新审稿、采用当前正文、驳回重写、章节跳转及正文“回到底部”操作继续保留。
- 更新 `CreationCanvasVisualContractTest`、`ChapterGenerationSessionApiContractTest` 和 `UiSemanticMappingContractTest`，固化底部状态栏收口与正文滚动操作保留规则。验证结果：`npm run build` 通过；Generate/工作流/UI 语义相关 Maven 定向测试 20 项通过；`git diff --check` 通过。

## Task 84.3–84.6：Generate 页正文滚动操作收口（2026-09-06）

- `novel-agent-web/src/components/workbench/ChapterCanvas.vue` 仅暴露实际 `.chapter-canvas` 滚动元素；`GenerateView.vue` 直接对该正文容器调用 `scrollTo`，回到底部使用 `scrollHeight`，回到顶部使用 `0`，不再操作 window 或章节目录。
- 流式正文按实际滚动位置决定是否自动跟随：用户上滑后暂停，点击唯一滚动按钮回到底部后恢复；正文可滚动且位于底部时显示“回到顶部”，否则显示“回到底部”，两个动作不会同时出现。
- 更新 `ChapterGenerationSessionApiContractTest`、`CreationCanvasVisualContractTest`，覆盖真实滚动容器、单按钮切换和流式跟随规则。验证结果：`npm run build` 通过；两个相关契约测试共 14 项通过；`git diff --check` 通过。

## Task 84.7–84.9：Generate 页滚动控制验收（2026-09-06）

- 滚动按钮仅在正文实际可滚动距离超过 `STREAM_BOTTOM_THRESHOLD_PX` 时显示，短正文不显示“回到底部”或“回到顶部”。
- 保持按钮位于正文 canvas 边缘的 sticky 轻量位置，继续使用 Element Plus `el-button text type="primary"`，不使用固定大悬浮按钮。
- 补充短正文隐藏、按钮样式和底部业务操作保留契约。验证结果：前端构建与 Generate 相关定向测试通过，`git diff --check` 通过。

## Task 84.10：Generate 页审稿结果空态收口（2026-09-07）

- `novel-agent-web/src/views/GenerateView.vue` 以问题列表优先渲染审稿结果；审稿失败和审稿通过文案仅在无问题时显示，返修有问题时作为唯一例外同时显示问题列表和修改提示，返修无问题时不显示审稿区域。
- 审稿失败文案统一为“自动审稿失败，但正文已保留。”；返修有问题时保留问题列表并显示“正在根据以上问题修改正文。”，不影响停止生成和人工审核操作。
- 更新 `ChapterGenerationSessionApiContractTest`、`CreationCanvasVisualContractTest`。验证结果：`npm run build` 通过；相关 Maven 定向测试 14 项通过；`git diff --check` 通过。

## 设定页目录位置对齐（2026-09-06）

- `novel-agent-web/src/views/SetupView.vue` 调整 Story Bible 双栏布局的网格对齐方式，让“设定目录”从页面标题的内容起始线下方开始，保留 `200~220px` 目录、`32~48px` 间距和约 `900px` 编辑区。
- 未改动 `activeBibleSection` 分区切换、保存/确认逻辑或窄屏 Select；验证结果：浏览器确认目录与页面标题左侧对齐，相关构建和契约测试继续通过，`git diff --check` 通过。

## 设定页分区编辑视觉收尾（2026-09-06）

- `novel-agent-web/src/views/SetupView.vue` 将 Story Bible 内容区改为开放式 canvas，移除白卡背景、顶部蓝线和规则分区面板边界；桌面采用约 `200~220px` 目录、`32~48px` 间距和最大约 `900px` 编辑区居中布局。
- 目录按钮与窄屏 `el-select` 继续共用 `activeBibleSection`，所有编辑字段继续绑定同一份 `bf`；AI 草稿操作保留在内容区顶部作为全局状态，保存仍提交完整 Story Bible。
- 窄屏断点提升至 `1080px`，目录降级为 Select，避免窄桌面出现过窄编辑列；验证结果：`npm run build` 通过，三个相关契约测试共 7 项通过，`git diff --check` 通过。

## 设定页真正分区编辑模式（2026-09-06）

- `novel-agent-web/src/views/SetupView.vue` 使用 `activeBibleSection` 管理 `world`、`rules`、`power`、`theme`、`ending`、`style` 六个分区；左侧菜单点击只切换状态，右侧通过条件渲染只保留当前分区，Story Bible 表单数据和保存、确认逻辑保持不变。
- 移除旧的 `scrollIntoView`、`IntersectionObserver`、section ID、`el-collapse` 和展开状态；编辑画布改为占满右侧弹性空间，窄屏继续使用 Element Plus `el-select`，避免正文被压成窄列。
- 验证结果：`npm run build` 通过；`CreationCanvasVisualContractTest`、`StoryBibleEditingContractTest`、`ReadableContentWidthContractTest` 共 7 项定向 Maven 测试通过；本地浏览器验证分区切换只渲染当前内容且不滚动，`git diff --check` 通过。

## 设定页编号弱化与窄屏导航降级（2026-09-06）

- `novel-agent-web/src/views/SetupView.vue` 移除 Story Bible 右侧正文标题及折叠分区的 01–05 编号，保留标题、说明和连续编辑内容，减少与左侧目录重复的视觉噪音。
- 桌面端目录列调整为 `clamp(180px, 18vw, 220px)`，右侧正文使用剩余弹性空间；760px 以下隐藏侧栏按钮列表，改用 Element Plus `el-select` 复用同一 section 定位逻辑，正文保持完整可读宽度。
- 顶部“已确认 / AI 调整 / 保存”及 Story Bible 保存、确认流程未改；验证结果：`npm run build` 通过，`CreationCanvasVisualContractTest`、`UiSemanticMappingContractTest` 共 7 项定向 Maven 测试通过，本地浏览器确认去编号、桌面双栏和顶部操作保持正确，`git diff --check` 通过。

## 设定页目录自动高亮与固定定位（2026-09-06）

- `novel-agent-web/src/views/SetupView.vue` 使用原生 `IntersectionObserver` 观察连续 Story Bible 文档的 section，在正文滚动到顶部观察带时同步更新左侧目录 active 状态；目录点击后保留目标高亮，并在折叠内容展开完成后平滑定位。
- 保留桌面端 `position: sticky` 侧栏及 tabs 内容容器的可见溢出处理，长文滚动时目录持续可用；正文仍是一整份连续编辑 canvas，没有改成内容 Tab，也未新增滚动插件、路由、API 或数据字段。
- 验证结果：`npm run build` 通过；`CreationCanvasVisualContractTest`、`UiSemanticMappingContractTest` 共 7 项定向 Maven 测试通过；本地浏览器验证手动滚动后“不可违反的规则”自动高亮、点击“特殊体系”后目标展开并保持 active；`git diff --check` 通过。

## 设定页页面内二级导航（2026-09-06）

- `novel-agent-web/src/views/SetupView.vue` 在故事设定编辑区增加“设定目录”侧栏，覆盖世界设定、不可违反的规则、特殊体系、主题与冲突、结局方向和写作风格；桌面端与内容区并列，窄屏降为可横向浏览的页内导航。
- 导航通过 section ID 和 `scrollIntoView({ behavior: 'smooth' })` 完成页面内平滑定位；定位折叠分区前只展开对应现有 `el-collapse` 项，不刷新路由、不重新请求 Story Bible，也不触碰表单编辑值和保存逻辑。
- 验证结果：`npm run build` 通过；`CreationCanvasVisualContractTest`、`UiSemanticMappingContractTest` 共 7 项定向 Maven 测试通过，并在本地浏览器验证侧栏可见、特殊体系和结局方向可点击定位且会自动展开；`git diff --check` 通过。

## Outline 大纲树标题与状态展示统一（2026-09-06）

- `novel-agent-web/src/views/OutlineView.vue` 将 BOOK / VOLUME / ARC 的树节点标题由 `·` 分隔改为普通空格拼接，保留单行省略，并为完整标题增加 hover 提示。
- 大纲树状态继续作为节点右侧独立 Element Plus Tag 展示，左侧标题区域保持可收缩；未修改 `el-tree` 的层级、数据、展开和节点点击交互，也未调整后端数据结构或 API。
- 验证结果：`npm run build` 通过；`OutlineViewContractTest`、`UiSemanticMappingContractTest` 共 13 项定向测试通过，`git diff --check` 通过。

## Read / Generate 章节工作台最终验收（2026-09-06）

- 验收范围覆盖正文无边界编辑、章节标题内联编辑与字号统一、Read / Generate 单行标题栏、共享目录层级、行内状态展示，以及 Outline 边界和 UI 框架约束。
- `OutlineView.vue` 未进入本轮变更文件，Outline 原有 BOOK → VOLUME → ARC 结构编辑器保持不变；前端继续使用 Vue 3 + Element Plus，未新增 UI 框架。
- 验证结果：`npm run build` 通过；Outline、UI 框架、语义扫描及 Read / Generate 相关 Maven 定向测试共 42 项通过；`git diff --check` 通过。

## Read / Generate 章节目录层级与状态展示统一（2026-09-06）

- `novel-agent-web/src/components/workbench/ChapterDirectory.vue` 为卷、章和选中章统一使用 `volume-row`、`chapter-row`、`chapter-row--active`；卷下章节固定增加一级缩进，卷标题使用更高字重，选中背景仅覆盖章行。
- 章节目录状态改为行内右侧轻量文本，移除 Read 目录中的字数重复展示；`ReadView.vue` 与 `GenerateView.vue` 均向共享目录传递展示元信息，章标题不再使用第二行常驻副信息或 `·` 分隔符。
- 更新 `ReadViewChapterDirectoryContractTest`、`GenerateViewChapterPlanContractTest` 和 Read / Generate 共享工作台断言；验证结果：`npm run build` 通过，相关 Maven 定向测试共 30 项通过，`git diff --check` 通过。

## Read / Generate 统一章节标题栏单行布局（2026-09-06）

- `novel-agent-web/src/components/workbench/ChapterToolbar.vue` 将章节工具栏统一为单行 flex 布局：左侧章节导航与标题可收缩，右侧字数、状态和业务操作保持不换行；窄屏通过 `clamp()` 压缩间距和内边距，不再隐藏右侧操作。
- `ChapterToolbar.vue` 集中维护状态胶囊样式，`ChapterNavigation.vue` 收敛导航间距；移除 `ReadView.vue` 与 `GenerateView.vue` 中重复的状态样式，两个页面仅通过 slot 提供不同业务操作。
- `ReadViewCanvasStyleContractTest` 更新共享工具栏断言；验证结果：`npm run build` 通过，`CreationCanvasVisualContractTest`、`GenerateViewChapterPlanContractTest`、`ReadViewCanvasStyleContractTest`、`ReadViewEditingContractTest` 共 24 项定向测试通过，`git diff --check` 通过。

## 章节标题内联编辑与字号统一（2026-09-06）

- `novel-agent-web/src/components/workbench/ChapterTitle.vue` 将顶部标题改为不可编辑的“第 N 章”结构编号加内联可编辑标题，移除 `·` 分隔符；编号、标题和输入态统一继承 `20px / 600`，不新增独立标题输入区域。
- 继续复用 `ReadView.vue` 现有 `editTitle`、未保存变更判断和 `saveEdit()` 持久化链路，标题修改后仍与正文一起保存。
- `ReadViewCanvasStyleContractTest` 补充章节号标记、无分隔符和字号字重契约；验证结果：`npm run build` 通过，标题样式与编辑保存相关 Maven 定向测试共 8 项通过，`git diff --check` 通过。

## 正文编辑区去除表单边界（2026-09-06）

- `novel-agent-web/src/components/workbench/ChapterCanvas.vue` 将正文 `el-input` 的自适应起始高度调整为 3 行，并对默认、hover、focus 状态统一移除边框、圆角、背景和阴影，正文画布保持透明衔接；仅保留主题色光标作为轻量编辑反馈。
- `novel-agent-app/src/test/java/cn/ninth/novel/web/ReadViewCanvasStyleContractTest.java` 补充无边界正文编辑器和自适应高度契约断言。
- 验证结果：`npm run build` 通过；`ReadViewCanvasStyleContractTest` 定向 Maven 测试通过；`git diff --check` 通过。

## CHARACTER_GENERATION 思考强度对比验收（2026-09-06）

- 扩展 `CharacterGenerationModelRealModelIT`：保持现有 `PlanningPrompts.CHARACTER_SYSTEM` 和固定 user prompt 不变，使用 `dev` profile 分别执行 `LOW`、`MEDIUM` 各 3 次，记录 `firstContentMs`、`totalCostMs`、`completionTokens`、JSON 解析结果和人物字段完整性。
- 真实模型结果：`LOW` 为 JSON 解析 3/3、字段完整 12/12，平均 `firstContentMs=6041.33ms`、`totalCostMs=18430.33ms`、`completionTokens=1599.67`；`MEDIUM` 为 JSON 解析 3/3、字段完整 12/12，平均 `firstContentMs=188996.33ms`、`totalCostMs=197565ms`、`completionTokens=20093.67`。两档均返回 4/4 个人物，未观察到结构完整性下降，已将 CHARACTER_GENERATION 默认 reasoning 保留为 `LOW`。
- `MEDIUM` 第 2 次出现 `totalCostMs=242905ms`，超过当前 180 秒配置但仍持续收到流事件并完成；这说明当前 Reactor `Flux.timeout` 是无事件间隔超时，不是绝对墙钟上限，本次验收记录该行为但未改变超时语义。
- 验证结果：`CharacterGenerationModelRealModelIT` 1 个测试通过，耗时约 661.5 秒；测试源码编译通过。本次未修改 Character、Review、ChapterPlan 或 Outline Prompt。

## 生效配置日志与 REVIEW 真实模型验收（2026-09-06）

- `PlanningModelPort`、`ChapterModelPort` 的模型 start/success/failed 日志追加生效的 `profile`、stage timeoutMs、reasoning、temperature 和 model；结构化流额外记录 `firstResponseMs`、`firstContentMs`、`generationMs`、`totalCostMs`、`completionTokens`，不输出完整 options。
- 根据 DeepSeek Chat Completions 与 Spring AI OpenAI 适配器文档核对：使用 `reasoning_effort` 和 `extraBody.thinking.type`；当前接口支持 `low/high/max`，`medium` 按接口规则映射为 `high`，未引入新的模型 SDK。
- `ReviewChapterNodeRealModelIT` 使用 `dev` profile 和完整章节正文完成真实 REVIEW：`timeoutMs=300000`、`reasoning=MEDIUM`，实际 `firstResponseMs=1977`、`firstContentMs=165244`、`generationMs=2615`、`totalCostMs=167929`、`completionTokens=18726`，在 300 秒内成功返回；Axios/Vite proxy 仍配置为 360 秒。本次真实用例直接验证后端模型链路，未启动浏览器端到端请求。
- 相关定向 Maven 测试 6 个测试类共 17 个用例通过，测试源码编译通过。

## 设定页与章节工作台组件化（2026-09-06）

- `SetupView.vue` 保留“世界设定 / 不可违反的规则”主分区，规则编辑使用 Element Plus `el-input`、文本按钮和 `el-collapse`，顶部统一为状态、AI 操作和保存操作层级。
- 新增 `novel-agent-web/src/components/workbench/ChapterDirectory.vue`、`ChapterToolbar.vue`、`ChapterTitle.vue`、`ChapterNavigation.vue`、`ChapterCanvas.vue`，集中维护 Read/Generate 的章节目录、工具栏、标题、导航和正文画布样式与交互。
- `ReadView.vue` 仅保留正文保存和章节记忆更新；`GenerateView.vue` 仅保留章节计划 Drawer 与工作流操作栏，移除两页重复的目录、工具栏和正文画布 CSS，并统一工作台响应式列布局。
- 更新 `novel-agent-app/src/test` 下前端契约断言，覆盖公共组件复用、设定页低边界编辑、目录映射和工作流操作栏；验证结果：`novel-agent-web` 执行 `npm run build` 通过，本轮未执行全量测试。

## 统一文字宽度与内容密度（2026-09-06）

- 继续使用共享 `--workbench-content-width: 820px`，将正文画布、设定编辑区、大纲编辑区和 Generate 审稿结果限制在可读宽度；小屏通过 `min()` 保持随容器收缩。
- 角色库和项目设置保留管理页全宽布局，不套用正文窄栏。
- 新增 `ReadableContentWidthContractTest` 覆盖长文本可读宽度和管理页全宽边界；验证结果：完成前端构建及相关定向测试，未执行全量测试。

## 角色页降低后台管理感（2026-09-06）

- `SetupView.vue` 将角色页“故事准备”改为轻量进度提示，使用完成项数量、进度条和简短状态文案表达准备程度。
- 角色卡片正文合并性格与背景为人物摘要，减少固定字段标签；整卡保留点击进入角色 Drawer，编辑图标仅在 Hover/Focus 时出现，移除“点击编辑 →”。
- 更新 `CharacterLibraryContractTest`，覆盖角色准备进度和悬停编辑入口；验证结果：`novel-agent-web` 执行 `npm run build` 通过，未执行全量测试。

## 页面操作层级与大纲编辑画布（2026-09-06）

- Outline 页将树节点转换为“故事总纲 / 卷一 / 第1章”的产品层级文案，仅在卷节点保留“待规划/已规划”状态；生成下一步使用主按钮，手动添加、去生成、刷新、保存修改改用 plain/text 次级入口。
- Outline 右侧编辑区移除卡片式表单边界，标题和长文本改为弱边界、自然增长的文档式编辑画布；现有 `el-drawer` 用于节点上下文编辑，生成和调整任务继续使用 `el-dialog`。
- 设定空状态隐藏无内容可保存的主按钮，避免 AI 生成与保存同时抢主操作；新增 `PageActionHierarchyContractTest`，同步更新 Outline/Page Header 契约。
- 验证结果：`novel-agent-web` 执行 `npm run build` 通过，`git diff --check` 通过；按要求未执行全量测试。

## stage 配置收口与 token 上限约束（2026-09-06）

- 新增 `ModelStageConfig`，Planning 与 Chapter ModelPort 均按 stage 取得完整配置，再统一构建 `OpenAiChatOptions`；stage 分支不再直接判断具体 stage。
- 统一 options 只应用 `model`、`temperature` 和 `reasoning/thinking`，明确不设置 `maxTokens`、`maxCompletionTokens`，避免结构化 JSON 被截断。
- 新增 Planning/Chapter stage 配置与无 token 上限断言；相关定向 Maven 测试 6 个测试类共 16 个用例通过，并完成源码检索确认无 token 上限配置残留。

## 前端语义映射层（2026-09-06）

- 新增 `novel-agent-web/src/utils/uiSemantics.ts`，统一提供 `statusLabel()`、`statusTone()`、`statusDescription()` 和 `domainLabel()`；状态与领域枚举在进入页面、Drawer、错误提示和状态辅助文案前统一转换为产品语言，未知状态也不会直接回显后端值。
- Generate、Read、Setup、Outline 页面改用共享状态和领域映射：章节计划 Drawer、工作流状态、草稿预览、故事总纲/卷/章纲节点以及失败提示均移除 `READY`、`ChapterPlan`、`DRAFT PREVIEW`、`ARC REGENERATION` 等开发语义；接口判断、事件分支和类型定义仍保留原始枚举。
- 更新 `novel-agent-app/src/test` 下相关前端契约断言，并新增 `UiSemanticMappingContractTest` 覆盖映射表、未知状态兜底和页面可见文案边界。
- 验证结果：`novel-agent-web` 执行 `npm run build` 通过；按要求未执行全量测试。Maven 定向契约测试因工作区未跟踪的 `DeepSeekReasoningOptions.java` 存在 Java 非法转义字符而未能启动，未修改该非本次任务文件。

## 状态 Tag 数量收敛（2026-09-06）

- `uiSemantics.ts` 增加四类状态视觉归类：待处理、进行中、已完成、异常；业务状态文案仍保留“可生成”“已规划”“正在检查”等细分表达。
- Outline 详情区移除与左侧目录重复的卷状态 Tag；角色草稿人数改为普通辅助文字；项目状态 Tag 改为统一语义映射，角色定位 Tag 保留用于识别人物类型。
- 更新 Outline、视觉页面和语义映射契约断言；验证结果：`novel-agent-web` 执行 `npm run build` 通过，未执行全量测试。

## 统一顶部 Page Header（2026-09-06）

- 设定和角色页统一复用 `editor-page-header`，固定为中文主标题、辅助说明和右侧操作区；角色编辑 Drawer 也移除非产品必需的英文 eyebrow。
- Outline 目录页补充中文辅助说明和右侧刷新操作，详情、Drawer 及生成弹层移除 `OUTLINE WORKBENCH`、`CURRENT NODE` 等英文 eyebrow；项目设置页同步移除同类英文装饰文案。
- 新增 `PageHeaderContractTest` 校验设定、角色、大纲和项目页头结构及英文 eyebrow 清理；验证结果：`novel-agent-web` 执行 `npm run build` 通过，未执行全量测试。

## Planning/Chapter stage 思考强度配置（2026-09-06）

- 新增 `ReasoningLevel`、`ChapterModelProperties`，并为 Planning 与 Chapter 模型提供 `OFF`、`LOW`、`MEDIUM`、`HIGH` 四档配置及 stage 覆盖；初始分组已落到 `application.yml`：轻任务关闭或低强度，中等任务使用中强度，重规划使用中/高强度，`REVIEW` 使用 `MEDIUM`。
- 新增 DeepSeek 适配层，将业务侧思考强度转换为真实接口字段：`OFF` 使用 `thinking.type=disabled`，`LOW` 使用 `reasoning_effort=low`，`MEDIUM/HIGH` 使用启用思考并映射到接口支持的 `high`。
- `PlanningModelPort`、`ChapterModelPort` 的同步、流式和结构化调用均按 stage 注入模型、temperature 与思考配置；新增 Chapter 配置绑定及选项转换测试。相关定向 Maven 测试 7 个测试类共 33 个用例通过。

## 结构化模型 stage 级超时（2026-09-06）

- 新增 `ModelTimeoutProperties`，在 `application.yml` 配置 `CHARACTER_GENERATION`、`CHAPTER_PLAN`、`ROOT_OUTLINE`、`NEXT_VOLUME`、`NEXT_CHAPTER_OUTLINE`、`REVIEW`、`COMPRESSION` 及其他结构化 stage 的独立 timeout；`PlanningModelPort` 和 `ChapterModelPort` 均按规范化 stage 读取配置。
- OpenAI HTTP Client 的 connect/read/request timeout 调整为 10s / 最大 stage timeout + 30s；当前最大 stage 为 `REVIEW=300s`，因此 read/request 为 330s。Spring AI、Axios、Vite 代理同步调整为 330s / 360s，避免上层提前断开。
- 移除旧的全局 `STRUCTURED_STREAM_TIMEOUT` 常量，结构化流超时分类测试改为注入 stage 配置；同步更新 HTTP、观测契约和配置绑定测试，定向 Maven 测试 5 个测试类通过，多模块编译成功。

## 统一页面骨架（2026-09-06）

- 工作台页统一由 `App.vue` 的 `workbench-mode` 收口外层滚动，并在 `theme.css` 增加目录分割线、工具栏高度、选中态和正文最大宽度等共享 token；Outline、Read、Generate 保持左侧目录 + 右侧工作区，Outline 详情补充 sticky 头尾和稳定滚动边界。
- 角色库（`SetupView.vue`）统一页头、筛选栏、卡片等高和底部操作位置，角色定位改用 Element Plus `el-tag`；项目页（`ProjectView.vue`）整理为页头、项目操作 section 和当前项目 section，保留原有创建、加载及预计章节数调整流程。
- 验证结果：`novel-agent-web` 执行 `npm run build` 通过，Vite 产物构建成功；前端布局、角色库、项目设置和导航相关 Maven 定向契约测试共 24 项通过。

## 人物生成数量与增量上下文回归（2026-08-31）

- 补充 `CharacterGenerationServiceTest` 的 count=1 回归，并保留 count=4、非法数量拒绝和 Prompt 数量断言；两种合法数量均验证已有正式角色 `顾言` 会进入 Prompt。
- 为测试仓储记录正式角色更新调用，确认人物生成只保存 Draft，不会新增或覆盖已有正式角色。
- 验证结果：`CharacterGenerationServiceTest` 8 个用例全部通过。

## 人物生成数量请求与 Prompt 边界（2026-08-31）

- `GenerateCharacterRequestDTO` 增加 `Integer preferredCount`；`NovelProjectService` 固定校验 1 到 15，非法数量在模型调用前拒绝，`NovelProjectController` 将数量透传到 Service。
- 人物生成用户 Prompt 增加“建议生成 N 名核心人物”的自然语言要求，不暴露 `preferredCount` 等后端字段；前端 `SetupView.vue` 使用 Element Plus 数字输入框，默认 4，限制 1 到 15，并提交该请求字段。
- 在 `novel-agent-app/src/test` 下补充请求/界面契约、数量边界、HTTP 透传和 Prompt 断言；相关 Maven 定向测试 22 个用例全部通过，前端 `npm run build` 通过，`git diff --check` 通过。

## 角色资料更新与运行时状态合并回归（2026-08-31）

- 核验 `NovelProjectService.updateCharacter` 先读取 existing 角色，再使用请求中的资料字段和 existing 的 `currentStateJson`、`lifeStatus`、`status` 构造完整 `StoryCharacterVO`；请求携带的动态字段不会覆盖数据库状态。
- 核验 `NovelProjectController` 只负责资料 DTO 转换，不承担 existing 状态合并；Repository 继续接收 Service 已合并好的完整 VO。
- 在 `novel-agent-app/src/test` 下新增完整对象合并回归和 Controller 转换边界契约，验证资料更新后运行时状态保持原值；定向测试 25 个用例通过。

## 角色创建边界回归（2026-08-31）

- 核验并强化 `AddStoryCharacterRequestDTO`、`ConfirmCharacterDraftDTO`、`ConfirmCharactersRequestDTO` 以及前端 `CharacterDraft`/Confirm payload 的字段白名单：前端和模型不提供 `characterCode`、`currentStateJson`、`lifeStatus`、`status`，人工新增不再保留 `createCharacterCode`。
- 核验人工新增和 AI Confirm 均复用 `NovelProjectService.addCharacter`，由后端 `generateCharacterCode()` 生成编码，并统一初始化合法 JSON `{}`、`lifeStatus=ALIVE`、`status=ACTIVE`。
- 在 `novel-agent-app/src/test` 下补充 API/前端契约字段精确断言，并使用 `ObjectMapper` 验证两条创建路径的状态 JSON；定向测试共 29 个用例通过。

## 普通角色编辑资料字段边界（2026-08-31）

- `UpdateStoryCharacterRequestDTO` 固定为 `name`、`roleType`、`gender`、`ageDescription`、`appearance`、`personality`、`backgroundStory`、`note` 八个角色资料字段，不接收 `currentStateJson`、`lifeStatus`、`status`。
- `NovelProjectService.updateCharacter` 更新资料时只使用已有角色的运行时状态；即使内部更新对象携带不同状态值，也不会覆盖 `currentStateJson`、`lifeStatus` 或 `status`。
- 在 `novel-agent-app/src/test` 下新增 DTO 精确字段契约和 Service 动态字段隔离回归测试；定向 Maven 测试 24 个用例全部通过。

## 模型 Prompt 边界规则补充（2026-08-31）

- 在 `AGENTS.md` 新增“模型 Prompt 边界”规则，限定 Prompt 只包含任务所需业务语义/上下文和输出协议/Schema；明确禁止写入数据库、Java 类型、接口路径、内部状态、后端规则等实现细节。
- 明确结构字段由代码生成、补充和校验，Prompt 使用自然语言业务概念，不被数据库存储形式反向约束模型输出；不影响模型创作、判断或抽取结果的信息不得进入 Prompt。
- 验证结果：已检查规则文档差异，`git diff --check` 通过。

## Task 42.1：ChapterPlan 单章上下文精准性修复（2026-08-29）

- 修改旧 Fact 相关性筛选器：优先级为 0 的事实不再进入候选，相关事实不足 8 条时只返回实际命中项，允许返回空列表；新增 64 条候选的少量相关、全部无关和超过 8 条相关测试。
- 修改 `RelevantCharacterSelector`：ACTIVE/非 DEAD 仅作为候选资格，上一章命中优先于当前大纲命中；无命中时只回退一个有效 ACTIVE 主角，DEAD 主角不再兜底；新增 50 人、优先级和状态过滤测试。
- 更新 `CHAPTER_PLAN_SYSTEM` 与 `PlanningService.buildChapterPlanPrompt()`，统一使用“上级大纲/全书方向”，并覆盖 BOOK→STAGE→ARC 与 BOOK→VOLUME→ARC；确认前重新解析当前最具体大纲节点，旧节点 Draft 在结构变化后拒绝确认。
- 新增 Task 42.1 重构计划。23 项相关定向测试、真实 MySQL `ChapterPlanSingleChapterRegressionTest`（1 项）通过，`git diff --check` 通过（仅有换行符转换提示）。

## Task 41.17：单章链路完整回归（2026-08-29）

- 新增真实 MySQL 回归测试 `ChapterPlanSingleChapterRegressionTest`：覆盖第 17 章生成 Draft、编辑确认 `READY`、正文持久化 `COMPLETED`、人工修改、再次 AI 生成 Draft 以及对 `COMPLETED` 计划确认拒绝。
- 回归测试更新第 17 章正文和 `story_summary` 后生成第 18 章，验证 Prompt 使用更新后的实际摘要；同时验证第 18～20 章没有预先 ChapterPlan 也不影响第 17/18 章链路。
- 新增 Task 41.17 回归计划。真实 MySQL 回归测试通过（1 项）。

## Task 41.15：清理旧批量章纲模型和代码（2026-08-29）

- 删除 `IPlanningService`/`PlanningService` 的 `generateChapterPlans`、`confirmChapterPlans`，删除 `ChapterPlanDraftListVO`；`ConfirmChapterPlansRequestDTO` 已不再存在。
- 删除 `IPlanningRepository`/`PlanningRepository` 的整段 ChapterPlan 替换、批量保存和节点级批量清理逻辑，以及 `batchInsert`、`batchUpsert` 和按节点排除章节号的 Mapper SQL；保留单章 `upsertChapterPlan` 和项目级 ChapterPlan 查询。
- 删除旧批量生成/确认测试，更新单章确认、仓储、Prompt、HTTP 和 UI 契约测试；`PlanningRepositoryTest` 改为逐章验证单条写入和更新。
- 新增 Task 41.15 清理计划，并同步总重构计划的章节计划能力和 Prompt 说明。源码编译、13 项单章/Prompt/UI/兼容性测试、5 项 HTTP 契约测试及真实 MySQL `PlanningRepositoryTest` 全部通过，`git diff --check` 通过。

## Task 41.14：让下一章优先读取上一章实际结果（2026-08-29）

- 更新 `IPlanningRepository.findPreviousChapterSummary` 契约和 `PlanningRepository` 实现：第 `N` 章生成第 `N-1` 章上下文时，优先读取非 `STALE` 且有内容的 `story_summary.short_summary`，不可用时回退 `chapter_plan.summary`，再没有则为空。
- 每次生成实时查询上一章摘要，人工更新 `story_summary` 后会立即成为下一章规划上下文；`STALE` 摘要不会覆盖 ChapterPlan fallback。
- 在 `PlanningRepositoryTest` 新增真实 MySQL 集成覆盖 fallback、正文摘要优先级、人工更新和 `STALE` 回退；定向测试通过。

## Task 41.13：实现单章 Draft Dialog（2026-08-29）

- 更新 `novel-agent-web/src/views/OutlineView.vue`：章节槽位的“生成章纲/重新生成”先打开当前章节 Draft Dialog，配置状态提供可选补充要求，生成后编辑标题和章节计划。
- Dialog 按当前章节号调用 `generateChapterPlan` 与 `confirmChapterPlan`；确认成功后调用 `loadOutline` 刷新 ChapterPlan 列表，只关闭 Dialog，保持 ChapterPlan Drawer 打开，不重新加载页面。
- 移除 Drawer 内旧的 Draft 预览状态，新增单章 Dialog 响应式样式和移动端适配；更新 `ChapterPlanUiContractTest` 覆盖该交互契约。
- `ChapterPlanUiContractTest` 与前端 `npm run build` 通过。

## Task 41.12：重构 ChapterPlan Drawer 为章节槽位列表（2026-08-29）

- 更新 `novel-agent-web/src/views/OutlineView.vue`：按选中叶节点的 `startChapter`～`endChapter` 生成章节槽位，并按 `outlineNodeCode + chapterNumber` 回填已有 `chapterPlans`。
- 已有章纲槽位显示状态和“编辑”；非 `COMPLETED` 槽位提供“重新生成”；未匹配槽位以 `plan === undefined` 显示“未生成”和“生成章纲”，不会因展示创建数据库记录。
- 移除 Drawer 顶部按章节号生成的控件，单章生成仍使用已有 Draft 预览和确认流程；新增 `ChapterPlanUiContractTest` 断言槽位列表契约。
- `ChapterPlanUiContractTest` 与前端 `npm run build` 通过。

## Task 41.11：修改 ChapterPlan HTTP API 为单章语义（2026-08-29）

- `NovelPlanningController` 删除节点范围章纲生成/确认映射，新增 `POST /chapter-plans/{chapterNumber}/generate` 和 `POST /chapter-plans/{chapterNumber}/confirm`，分别调用单章 Service。
- 新增 `GenerateChapterPlanRequestDTO(requirement)` 与 `ConfirmChapterPlanRequestDTO(draftId, title, summary)`，删除批量确认 DTO；确认请求不再接收 `chapterNumber`、`outlineNodeCode`、`status` 或 `chapterPlans`。
- 更新 `novel-agent-web/src/api/planning.ts`、`src/types/index.ts` 和 `OutlineView.vue`：生成时选择单个章节并发送 `requirement`，确认时按路径章节号发送 `draftId/title/summary`；前端不再调用旧批量接口。
- 更新 HTTP、旧路由白名单和前端契约测试；ChapterPlan 生成/确认、规划接口清理、ChapterPlan UI 契约及 `npm run build` 全部通过。

## Task 41.10：调整 ChapterPlan 生命周期规则（2026-08-29）

- 明确 `PLANNED`、`READY`、`COMPLETED` 的最终语义：人工修改 `READY/COMPLETED` 的 `title/summary` 均允许且保持原状态，AI 单章确认只拦截当前 `chapterNumber` 已为 `COMPLETED` 的计划。
- 更新总重构计划并记录 ChapterPlan 生命周期规则，删除“同节点任一完成即禁止整个节点重新生成”的规则表述，保留已生成正文计划不可删除。
- 扩展 `ChapterPlanSingleConfirmationTest` 验证其他章节的 `COMPLETED` 不阻断当前章，扩展 `PlanningRepositoryTest` 验证 `COMPLETED` 仍可人工更新标题摘要且状态不变；定向测试通过，`git diff --check` 通过。

## Task 41.9：实现单章 confirmChapterPlan（2026-08-29）

- 在 `IPlanningService` 与 `PlanningService` 新增单章 `confirmChapterPlan(projectCode, chapterNumber, draftId, title, summary)`，校验 `CHAPTER_PLAN` Draft、Draft 章节号、当前项目大纲归属与范围，以及标题和摘要；章节号与 `outlineNodeCode` 不从确认请求接收。
- 在 `IPlanningRepository` 与 `PlanningRepository` 新增单条 `upsertChapterPlan`，不存在时 insert `READY`，已有非 `COMPLETED` 计划时 update 为 `READY`，`COMPLETED` 计划拒绝 AI 覆盖；新增 MyBatis 单条 insert 映射，单章确认不调用批量替换或清理。
- 新增 `novel-agent-app/src/test/java/cn/ninth/novel/domain/planning/service/ChapterPlanSingleConfirmationTest.java`，并扩展 `PlanningRepositoryTest` 覆盖单条插入、更新和完成态保护；单章确认、旧确认兼容、单章生成及仓储集成定向测试通过，`git diff --check` 通过。

## Task 41.8：实现 generateChapterPlan（2026-08-29）

- 在 `IPlanningService` 与 `PlanningService` 新增 `generateChapterPlan(projectCode, chapterNumber, requirement)`，按章节范围定位最具体叶节点，解析直接父节点和 BOOK 摘要，加载上一章摘要、有界近期事实、相关人物和最多 5 条硬规则，组装 `ChapterPlanContextVO` 后调用 `CHAPTER_PLAN_SYSTEM`。
- 将 `ChapterPlanDraftVO` 严格收敛为模型只返回 `title`、`summary`；Service 将结果补齐为 `ChapterOutlineVO(chapterNumber, outlineNodeCode, title, summary, PLANNED)`，以 `CHAPTER_PLAN` 类型保存单章 Draft。
- 扩展规划仓储和事实 DAO 的有界读取：上一章摘要按章节查询，事实按当前章之前的章节倒序并限制候选数量；规划 Prompt 使用固定语义分区，不序列化完整圣经、人物档案或大纲对象。
- 新增 `novel-agent-app/src/test/java/cn/ninth/novel/domain/planning/service/ChapterPlanSingleGenerationTest.java`，覆盖第 17 章 `ARC 11~20` 定位、位置 `7/10`、筛选上下文、模型输出字段、Service 元数据补齐及前置校验；定向测试通过，未运行全量测试。

## Task 41.7：重新设计 CHAPTER_PLAN_SYSTEM（2026-08-29）

- 重写 `novel-agent-domain/src/main/java/cn/ninth/novel/domain/planning/service/prompt/PlanningPrompts.java` 的 `CHAPTER_PLAN_SYSTEM`，改为只规划当前一章，固定输入分区为当前章节、当前剧情段、上层方向、当前位置、上一章、相关人物、相关事实、硬规则和补充要求。
- 明确信息优先级为硬规则 > 已发生事实 > 人物当前状态 > 当前最具体大纲 > 上层大纲方向 > 用户补充要求；要求基于上一章实际结果推进，不规划后续多章、不返回章节列表、不重复解释已有设定。
- 输出收敛为仅含 `title`、`summary` 的 JSON，并明确禁止 `chapterNumber`、`outlineNodeCode`、`status`、`startChapter`、`endChapter`、`sequenceNo` 等元数据字段。
- 更新 `PlanningPromptsTest` 与旧批量生成测试中的 Prompt 断言，并同步总重构计划；本任务未改动 PlanningService/API 批量实现，待 41.1 接口迁移继续处理。

## Task 41.6：实现相关人物筛选（2026-08-29）

- 新增 `novel-agent-domain/src/main/java/cn/ninth/novel/domain/planning/service/RelevantCharacterSelector.java`，按上一章摘要命中、当前大纲标题/摘要命中和 ACTIVE 角色排序，最多选择 4 人；无命中时最多回退一名主角。
- 将 `StoryCharacterVO` 投影为 `CharacterBriefVO` 的 `name`、`role`、`currentGoal`、`currentLocation`、`currentState` 五个字段，从当前状态 JSON 提取目标/位置/状态摘要，未携带人物编码、完整背景、外貌、备注或历史状态。
- 修改 `novel-agent-domain/src/main/java/cn/ninth/novel/domain/chapter/service/agent/PlanChapterNode.java`，实际规划 Prompt 改用筛选结果，只输出五字段人物摘要，不再遍历全部 ACTIVE 人物或输出完整人物档案。
- 新增 `RelevantCharacterSelectorTest` 和 `novel-agent-app/src/test/java/cn/ninth/novel/domain/chapter/service/agent/PlanChapterNodePromptContractTest.java`，覆盖 50 人候选的 4 人上限、优先级、完整档案隔离、主角兜底、DEAD 排除及实际 Prompt 条目数量。
- 定向验证：相关人物筛选与实际 Plan Prompt 测试通过（3+1 个用例）；未运行全量测试。

## Task 41.5：实现相关事实筛选（2026-08-29）

- 新增旧 Fact 相关性筛选器，以事实主语/客体的简单文本命中、事实类型和来源章节号完成 MVP 筛选；优先级为硬规则、上一章直接事实、人物状态、大纲相关、其他近期事实。
- 筛选结果去重后最多 8 条，过滤当前及未来章节来源事实；未引入 embedding、PGVector RAG、reranker 或 LLM 二次筛选。
- 新增旧 Fact 相关性筛选测试，覆盖优先级、目标/上一章命中、历史边界和 8 条上限。
- 定向验证：旧 Fact 相关性筛选测试 3 个用例通过；未接入 ChapterPlan Prompt 组装，未运行全量测试。

## Task 41.3：设计精简的 ChapterPlanContextVO（2026-08-29）

- 在 `novel-agent-domain/src/main/java/cn/ninth/novel/domain/planning/model/valobj` 新增 `ChapterPlanContextVO`、`OutlineBriefVO`、`ChapterPositionVO` 和 `CharacterBriefVO`，上下文不持有数据库 ID、完整故事圣经、完整人物或 `OutlineNodeVO` 列表。
- `ChapterPlanContextVO` 对事实、相关人物和硬规则执行 8/4/5 数量预算，使用不可变列表保存；空列表可用，超出预算时拒绝构造。
- 在 `novel-agent-app/src/test/java/cn/ninth/novel/domain/planning/model/valobj/ChapterPlanContextVOTest.java` 新增模型契约测试，覆盖字段白名单、禁止类型、不可变性和数量上限。
- 定向验证：`ChapterPlanContextVOTest` 3 个用例通过；未接入 Prompt 组装，未运行全量测试；本轮最终 `git diff --check` 通过。

## Task 41.2：ChapterPlan 生成上下文压缩契约（2026-08-29）

- 记录大纲上下文压缩规则：保留按 `chapterNumber` 找到最具体大纲节点及祖先链解析，但规定发送给模型的上下文最多三层：目标节点完整 `title/summary`、直接父节点 `title/summary`、BOOK 的 `summary`。
- 明确超过三层时可省略中间 `STAGE`，跳层结构按实际存在层级处理；禁止把完整 `OutlineNodeVO`、祖先列表或 `OutlineNodeVO.toString()` 直接拼接进 Prompt。
- 同步总重构计划中的 ChapterPlan 接口、Prompt、分阶段验收和前端生成语义；本次只修改 Markdown 文档，未修改实现或测试代码，未运行实现测试。

## Task 41.1：重定义 ChapterPlan 生成契约（2026-08-29）

- 锁定单章节 Draft 生成/确认语义：生成和确认均使用 `/chapter-plans/{chapterNumber}/...`，生成请求只允许 `requirement`，Draft 只包含 `draftId`、`chapterNumber`、`title`、`summary`，确认请求只允许 `draftId`、`title`、`summary`。
- 同步大纲树重构的领域接口、HTTP API、分阶段验收和完成标准；历史的批量生成/确认计划增加 Task 41.1 契约状态说明。
- 本次只修改 Markdown 文档，未修改 Java、DTO、Controller、前端或测试实现；未运行实现测试，完成 `git diff --check` 文档检查。

## 最终 UI / 响应式验收（2026-08-29）

- 使用本地前端和隔离的规划 API 数据完成 `OutlineView` 三尺寸浏览器验收：1440×900、1024×800、375×812 均无横向溢出；移动端详情区按预期隐藏，节点编辑、新增下级和章纲 Drawer 可正常打开。
- 已验证树搜索、节点选择、节点保存、手动新增、同级上移、刷新、根总纲生成与确认、AI 拆分与确认、20 条章纲生成与确认、章纲标题/摘要编辑，以及移动端 Drawer；AI 草稿预览只暴露业务允许编辑的标题和摘要字段。
- 修复 `novel-agent-web/src/views/OutlineView.vue` 的节点删除菜单：改用 Element Plus `ElMessageBox` 进行确认，避免删除菜单嵌套 `Popconfirm` 时无法触发；补充无根总纲时移动端的“AI 生成总纲”入口。
- `COMPLETED 只读`未通过当前验收：按 Task 39.6 的明确规则，已有正文的 ChapterPlan 仍允许人工修改 `title/summary`，实测输入框和保存按钮均可用；该规则与本次验收项冲突，未擅自改变 Service 契约。节点删除仅验证到确认框，未点击临时 Mock 数据的不可逆删除按钮。
- 前端 `npm run build` 通过，Vite 仅提示既有 bundle 超过 500 kB；`OutlineDeleteReorderUiContractTest`、`ReadViewEditingContractTest` 及规划相关 UI 契约均通过。后端全量执行 `mvn --% -pl novel-agent-app -am test`，结果为 198 个用例、2 个失败、11 个错误：失败涉及根 POM 未管理 `spring-tx` 和章节抽取测试类型转换，错误涉及 LangGraph 状态初始化及部分 MySQL/开发配置连接；`git diff --check` 通过。

## Task 39.13：新增“无旧链路”验证（2026-08-29）

- 清理限定生产范围内发现的旧项目章节卡链路：移除 `ChapterCard*` API DTO、领域 VO、项目 Service/Repository/Controller CRUD，以及未再使用的初始化状态 DAO/Mapper 入口；同步将相关测试夹具迁移到当前大纲节点模型。
- 新增无旧规划链路扫描契约，仅扫描各模块 `src/main` 和 `novel-agent-web/src`，禁止 `generateInitialization`、`generateVolumeDetail`、`generateArcChapterCards` 等旧方法及 `INITIALIZATION`、`VOLUME_DETAIL`、`ARC_CHAPTER_CARDS`、`CHAPTER_CARD` 标记；历史文档和专门测试目录不在扫描范围内。
- 修正项目人物 HTTP 请求到领域对象的 `appearance/personality` 字段映射顺序，保持现有项目管理接口契约测试通过。
- 验证结果：无旧链路契约测试扫描 172 个生产文件并通过；Service、Controller 和契约测试共 18 个通过；前端 `npm run build` 通过。真实 MySQL 仓储测试因本机 MySQL 端口拒绝连接未能执行。

## Task 39.12：删除旧前端类型兼容字段（2026-08-29）

- `novel-agent-web/src/types/index.ts` 的 `OutlineNode` 保留当前节点字段，移除 `nodeType`、节点误用的 `chapterNumber`、`protagonistGoal`、`conflictDescription`、`expectedPayoff`、`endingHook` 兼容字段；`OutlineNodeKind` 严格收敛为 `BOOK | STAGE | VOLUME | ARC`，`legacyOutlineKind()` 无残留。
- 删除未被页面使用的 `ChapterCardRequest`、`UpdateChapterCardRequest`、`ChapterCardResponse`、`SaveChapterCardsRequest` 前端类型，并移除 `project.ts` 中对应的旧保存/查询/更新 API 导出。
- 后端 `ChapterCardRequestDTO`、`UpdateChapterCardRequestDTO`、`ChapterCardResponseDTO`、`SaveChapterCardsRequestDTO` 仍被独立章节卡 CRUD 生产链路引用，按“生产代码无引用后再删”条件保留。
- 新增 `FrontendPlanningTypeRemovalContractTest`，覆盖 OutlineNode 字段白名单、节点类型白名单和旧前端 ChapterCard 类型/API 清理。
- 验证结果：前端类型契约测试 1 个通过，`npm run build` 通过；Vite 仍提示既有 bundle 超过 500 kB，但不影响构建。

## Task 39.11：删除 Repository Deprecated 兼容层（2026-08-29）

- 从 `IPlanningRepository` 删除 `updateBookOutline`、`confirmInitialization`、`resetInitialization`、`confirmVolumeDetail`、`confirmArcChapterCards` 等 `@Deprecated default` 兼容方法，并移除未被新链路使用的 `createBookOutline`。
- 从 `PlanningRepository` 清理对应旧总纲创建实现、旧模型 import 和转换 helper；保留项目/故事圣经/人物上下文、树节点 CRUD/排序、ChapterPlan 查询/更新/替换，以及独立 `IPlanningDraftRepository` 的 Draft 持久化。
- 新增规划仓储能力边界契约，校验接口、实现和 Draft Repository 的当前能力边界及旧兼容方法不可回归。
- 验证结果：Repository 兼容层契约测试 1 个通过；真实 MySQL `PlanningRepositoryTest` 2 个用例通过；Maven 构建成功。

## Task 39.10：删除旧领域模型和 Prompt（2026-08-29）

- 清理 Planning 领域中旧的 `BookOutline*`、`VolumeOutline*`、`StoryArc*`、规划侧 `ChapterCard*` 和 `InitializationDraft*` 模型；`domain/project` 下仍被独立章节卡 CRUD 使用的 `ChapterCardVO` 保留。
- 将 `PlanningPrompts` 收敛为 `ROOT_OUTLINE_SYSTEM`、`CHILD_OUTLINE_SYSTEM`、`CHAPTER_PLAN_SYSTEM` 三个当前 Prompt；未发现独立模块仍引用已删除的 Bible/Character Prompt 常量。
- 新增规划旧模型清理契约，校验旧模型文件、Planning 生产引用和旧 Prompt 常量均不可回归，并确认当前三类 Prompt 保留。
- 验证结果：规划旧模型清理、`PlanningPromptsTest` 和规划旧流程清理定向测试共 3 个通过，Maven 构建成功。

## Task 39.9：删除 PlanningService 旧流程（2026-08-29）

- 清理 `IPlanningService` 与 `PlanningService` 中的初始化、全书总纲、卷细纲和剧情弧章节卡旧流程；服务构造器同步移除旧流程专用的 `ObjectMapper` 和旧流程常量/依赖。
- 删除旧链路专用的圣经、人物、卷、剧情弧、章节卡 Draft 归一化、校验、转换及章节范围辅助方法；保留并复核根总纲、子大纲、ChapterPlan 和人工 CRUD 仍使用的公共校验与归一化 helper。
- 新增规划旧流程清理契约，同时校验 Service 接口/实现旧方法和旧 helper 不可回归，当前新规划流程仍存在。
- 验证结果：规划旧流程清理与规划接口清理定向测试共 2 个通过，Maven 构建成功。

## Task 39.8：删除旧 Planning HTTP 接口（2026-08-29）

- 收敛 `NovelPlanningController` 的 HTTP 映射，仅保留 `/outlines/tree`、`/outlines/nodes/**`、`/outlines/root/**`、`/outlines/{nodeCode}/children/**`、`/outlines/{nodeCode}/chapter-plans/**` 与 `/chapter-plans/**`。
- 确认 `/initialize*`、`/outlines/book*`、`/outlines/volumes/{volumeCode}/detail*`、`/outlines/arcs/{arcCode}/chapter-cards*` 及对应旧接口专用 DTO 均已删除。
- 扩展规划接口清理契约，以 HTTP 映射白名单、旧路径不存在和旧 DTO 文件不存在固化清理契约。
- 验证结果：规划接口清理定向测试 1 个通过，Maven 构建成功。

## Task 39.7：迁移旧前端引用并删除 Legacy API（2026-08-29）

- 将旧 `/planning` 页面路由迁移到当前 `OutlineView` 工作台，删除旧页面及其对初始化、卷细纲和剧情弧章节卡 API 的调用；`ReadView` 原有入口继续进入同一新工作台。
- 从 `novel-agent-web/src/api/planning.ts` 删除全部 Legacy 类型和方法，并移除后端对应的旧 Service、Controller、Repository 兼容声明、请求 DTO、旧规划草稿模型与旧提示词。
- 将大纲响应和前端节点类型收敛为当前 `nodeKind`、层级、章节范围、标题、摘要和状态字段，移除旧 `nodeType` 及章节卡兼容字段。
- 清理旧规划测试和模型联调测试，保留并更新当前根总纲、子大纲、ChapterPlan 的领域、HTTP、UI 契约覆盖。
- 验证结果：当前规划链路定向测试 63 个通过，Legacy 清理相关测试 4 个通过，前端 `npm run build` 通过；Vite 仍提示既有 bundle 超过 500 kB，但不影响构建。

## Task 39.6：修复 ChapterPlan 生命周期规则（2026-08-29）

- `PlanningService.generateRootOutline` 与 `generateChildOutlines` 将可选 requirement 统一归一为 `requirement == null ? "" : requirement.trim()`，空需求仍可正常进入模型 Draft 流程。
- 保留节点删除的 Service 门禁；已有正文的 `COMPLETED` ChapterPlan 允许通过人工更新接口修改 title/summary，并在 `confirmChapterPlans` 批量写入前由 Service 查询并拒绝 AI 覆盖，避免依赖前端 disabled 或 Mapper upsert 行为。
- 同步更新 ChapterPlan UI 与领域契约测试，覆盖已完成章节人工修改、真实 `updateChapterPlan` 保存、AI 批量覆盖拦截和空 requirement。
- 验证结果：相关领域测试 23 个通过，ChapterPlan UI 契约测试 1 个通过，前端 `npm run build` 通过，`git diff --check` 通过。Vite 仍提示既有 bundle 超过 500 kB，但不影响构建。

## Task 39.5：接入真实 ChapterPlan AI（2026-08-29）

- `novel-agent-web/src/views/OutlineView.vue` 的章纲生成改为调用 `generateChapterPlans(nodeCode)` 对应的真实项目 API，从 `PlanningDraftResponse.payload` 读取完整章节范围，仅将模型返回的 `chapterNumber`、`title`、`summary` 投影到 Draft 预览。
- 确认时调用 `confirmChapterPlans`，只提交 Draft ID、章节号、标题和摘要；确认成功后重新加载 `listOutlineNodes` 与 `listChapterPlans`，章节节点关联和状态继续由后端处理。
- 删除 `chapterPlanDraftTemplates` 及本地模拟章节内容，Draft 页面不再生成或编辑 `outlineNodeCode`、`status` 等后端上下文字段。
- 增加真实 MySQL 仓储回归，验证确认写入 1–20 章后，查询得到的 20 条记录全部带同一个 `outlineNodeCode`。
- 验证结果：前端 `npm run build` 通过；ChapterPlan Service、HTTP、UI 定向测试 16 个用例通过；`PlanningRepositoryTest` 真实 MySQL 2 个用例通过；`git diff --check` 通过。Vite 仍提示既有 bundle 超过 500 kB，但不影响构建。

## Task 39.4：接入真实 AI 拆分（2026-08-29）

- `novel-agent-web/src/views/OutlineView.vue` 的 AI 拆分生成改为调用 `generateChildOutlines`，从 Draft `payload` 读取各项内容，仅投影 `title`、`summary` 到前端编辑表单，并保存 `draftId`。
- 确认时调用 `confirmChildOutlines`，提交 `draftId`、用户选择的 `targetNodeKind` 和编辑后的标题/概要；确认成功后重新加载当前父节点下的大纲树。节点编码、父节点、节点类型、顺序、章节范围和状态不由前端编辑或生成。
- 删除 `splitDraftTemplates` 及本地模拟生成逻辑，补充 AI 拆分 UI 契约，覆盖真实生成/确认 API、Draft payload 和元数据不可编辑边界。
- 验证结果：`npm run build` 通过；AI 拆分领域、HTTP 与 UI 共 18 个定向测试全部通过；`git diff --check` 通过。Vite 仍提示现有 bundle 超过 500 kB，但不影响构建成功。

## Task 39.3：接入真实根总纲 AI（2026-08-28）

- `novel-agent-web/src/views/OutlineView.vue` 的根总纲生成改为调用 `generateRootOutline`，从 `PlanningDraftResponse.payload` 读取 Draft，只将 `title`、`summary` 绑定到可编辑预览，并保存 `draftId` 用于确认。
- 确认时调用 `confirmRootOutline`，仅提交 `draftId`、编辑后的 `title`、`summary`；成功后重新加载大纲树，根节点编码、类型、父节点、顺序、章节范围和状态继续由后端补齐。
- 移除旧根总纲生成/更新兼容入口及其 API DTO、Service、Repository、Controller 和过时测试引用；模型输出契约仍为 `RootOutlineDraftVO(title, summary)`。
- 验证结果：`npm run build` 通过；根总纲、前端页面、受影响 Service/HTTP 共 18 个定向测试全部通过；`git diff --check` 通过。Vite 仍提示现有 bundle 超过 500 kB，但不影响构建成功。

## Task 39.2：OutlineView 接入真实人工 CRUD（2026-08-28）

- `novel-agent-web/src/views/OutlineView.vue` 初始树与章纲数据改为空响应式数组，刷新通过 `listOutlineNodes` 与 `listChapterPlans` 重新加载真实数据，并移除布局预览文案及 noop 操作。
- 节点保存、新增下级、删除、上移/下移分别接入 `updateOutlineNode`、`createOutlineNode`、`deleteOutlineNode`、`reorderOutlineNode`；操作成功后刷新查询并保持当前节点，新增节点编码由当前树数据生成。
- 新增 `OutlineViewCrudContractTest`，同步更新根总纲 UI 契约对“手动创建”入口的断言，并修正响应式契约测试对换行格式的脆弱匹配。
- 验证结果：`npm run build` 通过；定向执行 OutlineView CRUD、根总纲、新增下级、删除排序、章纲和响应式共 7 个契约测试全部通过；`git diff --check` 通过。Vite 仍提示现有 bundle 超过 500 kB，但不影响构建成功。

## Task 39.1：ChapterPlan 查询契约补齐大纲节点（2026-08-28）

- 将 `ChapterOutlineVO` 与 `ChapterPlanResponseDTO` 统一扩展为 `chapterNumber`、`outlineNodeCode`、`title`、`summary`、`status`；`ChapterPlanDraftVO` 仍只保留 `chapterNumber`、`title`、`summary`，未修改章节计划模型 Prompt。
- `PlanningService` 生成 Draft 时由服务端补充目标节点编码；`PlanningRepository` 查询 `ChapterPlanPO` 后按 `outlineNodeId` 查询对应 `OutlineNodePO`，组装 `outlineNodeCode`，更新响应也复用该关联查询。
- 更新领域、Repository、HTTP 测试，GET `/chapter-plans` 已断言每条记录包含 `outlineNodeCode`，并增加 Prompt 不包含该字段的断言。
- 验证结果：定向执行 `ChapterPlanGenerationTest`、`ChapterPlanConfirmationTest`、`ChapterPlanEditingTest`、`ChapterOutlineModelTest`、`ChapterPlanGenerationHttpTest`、`ChapterPlanEditingHttpTest`、`PlanningRepositoryTest`，共 22 个用例通过；真实 MySQL Repository 测试验证节点编码关联，构建成功。

## Element Plus 组件使用规范补充（2026-08-28）

- 在 `AGENTS.md` 明确 Vue 3 + Element Plus 自动导入项目的新页面组件边界：优先使用 Element Plus，不新增其他 UI 组件库；输入、按钮、树、表单、Select、Dialog、Drawer、Tag、Tooltip、Popconfirm、Empty、Loading 等不重复手写基础实现。
- 进一步限定自定义 CSS 只用于布局、间距和少量视觉调整，并明确现有 `OutlineView.vue` 的旧手写树节点、原生搜索框和章纲平铺结构不作为新实现参考。
- 验证结果：已检查规则文档差异，`git diff --check` 通过。

## Task 39：删除与同级排序交互统一（2026-08-28）

- 在 `novel-agent-web/src/views/OutlineView.vue` 将大纲节点删除统一为 `el-popconfirm`，确认文案包含节点标题和“删除后无法恢复”；BOOK、存在子节点或 ChapterPlan 的节点禁用删除，子级数据阻止删除时通过 `el-tooltip` 提示“请先删除下级大纲”。
- 节点菜单增加“上移/下移”，首尾位置禁用；排序只计算当前 parent 下的相邻目标序号，不提供拖拽或跨父节点移动。前端在 `novel-agent-web/src/api/planning.ts` 调用 POST reorder 接口，成功后刷新树并保持当前选择。
- 新增 `POST /api/v1/novels/projects/{projectCode}/outlines/nodes/{nodeCode}/reorder`，请求体使用 `targetSequence`；领域服务校验项目、节点和目标序号，基础设施复用同级顺序归一化逻辑持久化连续序号。
- 将 `novel-agent-web/src/views/ReadView.vue` 的章节删除同步迁移到 `el-popconfirm`，项目持久化删除交互不再使用 `window.confirm()`。
- 新增 `OutlineDeleteReorderUiContractTest`，扩展 `ManualOutlineCrudTest`、`ManualOutlineCrudHttpTest` 和真实 MySQL `PlanningRepositoryTest`；定向领域/HTTP/UI/仓储测试与 `npm run build` 通过，`git diff --check` 通过。Vite 仍提示现有 bundle 超过 500 kB，但不影响构建成功。

## Task 38：章节计划 UI（2026-08-28）

- 在 `novel-agent-web/src/views/OutlineView.vue` 中将章节计划从左侧大纲树分离；仅当当前节点章节范围完整且没有子节点时，右侧显示“查看章纲”或“生成章纲”入口。
- 使用 `el-drawer size="720px"` 展示当前节点的章节计划，列表采用 `el-collapse` 按章展开；标题和章节计划支持编辑，`COMPLETED` 章节的输入框禁用并使用 `el-tag` 展示状态。
- AI 章纲在同一个 Drawer 内切换到 Draft Preview，逐章提供可编辑标题和计划内容，只有预览态显示“重新生成/确认”；本次确认不调用模型、不写入正式章节计划。
- 新增 `ChapterPlanUiContractTest` 和对应实施方案；左树仍只绑定 Outline 节点数据，没有插入 ChapterPlan 节点。
- 验证结果：按 TDD 先确认契约测试失败，补齐实现后定向契约测试通过；`npm run build` 通过；浏览器确认无 BOOK 空状态和 1440/900/390 三档布局无横向溢出；`git diff --check` 通过。Vite 仍提示现有 bundle 超过 500 kB，但不影响构建成功。

## Task 37：AI 生成根总纲 UI（2026-08-28）

- 在 `novel-agent-web/src/views/OutlineView.vue` 按是否存在 BOOK 切换页面状态；无 BOOK 时左侧不显示旧的“尚未生成总纲”按钮，右侧使用 `el-empty` 展示“暂无故事总纲”、说明文案和“AI 生成总纲/手动创建”按钮。
- 根总纲生成使用单个 `el-dialog`：第一步只有补充创作要求和“生成”，生成后切换到 Draft Preview，标题与 `summary` 可编辑，提供“重新生成/确认”；确认前不写入正式大纲。
- 大纲树与 ChapterPlan 改为通过已有 GET 查询加载，实际空项目能够进入无 BOOK 状态；移除前端旧 `generateBookOutline` 导出，未恢复世界观、人物和总纲一体化流程。
- 新增 `AiRootOutlineUiContractTest`，按 TDD 先确认旧实现缺少新 UI 后失败，再验证空状态、Dialog 字段、Draft 边界和旧入口清理；浏览器验证空项目及 1440/900/390 三档布局通过。
- 验证结果：定向契约测试通过，`npm run build` 通过，`git diff --check` 通过。Vite 仍提示现有 bundle 超过 500 kB，但不影响构建成功。

## Task 36：AI 拆分 UI Draft 预览（2026-08-28）

- 在 `novel-agent-web/src/views/OutlineView.vue` 接入非 ARC 节点的“AI 拆分”菜单处理，使用单个 `el-dialog width="680px"` 承载配置态和预览态；配置项包含拆分类型、建议数量和可选补充要求。
- 生成后只切换到 Draft 预览，逐项使用 `el-input` 和 textarea 编辑标题、自然语言概要；“确认写入”仅在预览态出现，重新生成可回到配置态，本次不调用模型或正式写入接口。
- 新增 `AiSplitOutlineUiContractTest`，覆盖入口、Dialog、配置字段、单 Dialog 状态切换、Draft 可编辑字段和确认按钮边界；移动端通过响应式宽度收缩 Dialog。
- 验证结果：先运行契约测试确认缺少实现后失败；补齐实现后定向契约测试通过，`npm run build` 通过，浏览器验证 1440/900/390 三档布局及 Draft 编辑确认流程通过，`git diff --check` 通过。

## 新增下级大纲 UI（2026-08-28）

- 在 `novel-agent-web/src/views/OutlineView.vue` 为非 ARC 节点接入“新增下级”入口；桌面使用 `el-dialog`，移动端使用 `el-drawer`，表单包含节点类型、标题和大纲内容，并提供取消/创建按钮及基础校验。
- 按父节点类型提供前端推荐顺序：BOOK 为阶段、卷、剧情弧；STAGE 为卷、剧情弧、阶段；VOLUME 为剧情弧、阶段、卷；第一项标记“推荐”，其他合法类型仍可选择；ARC 不显示新增下级入口。
- 新增 `AddChildOutlineUiContractTest`，本次仅完成 UI 骨架和交互，不新增后端调用。
- 验证结果：契约测试通过；`npm run build` 通过；浏览器验证 1440、900、390 三档布局无横向溢出，Dialog/Drawer 和类型排序符合要求；`git diff --check` 通过。

## 前端组件使用规范（2026-08-28）

- 在 `AGENTS.md` 的前端规则中补充 Element Plus 组件使用约束：存在合适内置组件时优先复用，不手写重复的基础控件和交互；仅在内置能力不足时自定义实现。
- 验证结果：已检查文档差异，`git diff --check` 通过。

## 右侧节点编辑器迁移 Element Plus Form（2026-08-28）

- 将 `novel-agent-web/src/views/OutlineView.vue` 桌面编辑区和移动端 Drawer 改为 `el-form label-position="top"`，仅保留标题、章节范围和大纲内容三个 `el-form-item`，移除手写 label 及旧章节字段。
- 章节范围使用起止 `el-input-number`；存在下级大纲或章节计划时立即禁用输入，并显示对应锁定原因；顶部使用 Tag 展示节点类型、状态和章节范围。
- 底部操作统一为“取消”和“保存修改”，本次仍不提交后端数据。
- 定向 Form 契约检查、桌面/移动端浏览器检查、章节范围锁定规则验证和 `npm run build` 通过，`git diff --check` 通过。

## Task 33：左侧大纲树迁移 Element Plus Tree（2026-08-28）

- 将 `novel-agent-web/src/views/OutlineView.vue` 左栏手工 `v-for` 树迁移为 Element Plus `el-tree`，使用嵌套树数据、`node-key="nodeCode"`、当前节点高亮、默认展开和自定义节点插槽。
- 搜索输入使用 `Search` 前缀图标，关键词通过 `treeRef.value?.filter(keyword.value)` 交给 Tree 过滤；节点仅显示标题、类型 Tag、章节范围，操作入口改为 hover 后的 `el-dropdown`。
- 节点菜单按规则隐藏 ARC 的“新增下级”和“AI 拆分”，BOOK 禁用删除；所有操作仍为页面骨架，不发送请求。
- 定向 Tree 契约检查、1440/900/390 三档浏览器交互检查和 `npm run build` 通过，`git diff --check` 通过。

## 大纲节点文字不可见问题诊断（2026-08-28）

- 检查 `novel-agent-web/src/views/OutlineView.vue` 的节点 DOM 与 Element Plus 按钮样式，确认节点文字已进入 DOM，但 `el-button` 自动生成的内部 `<span>` 包装层未参与伸展：包装层计算宽度为 `16px`，`.tree-copy`、标题和摘要计算宽度均为 `0px`，因此页面只显示分支符号。
- 使用临时页面复刻相同 DOM/CSS 验证：为 `.tree-node > span` 增加伸展规则后，包装层宽度恢复为 `275px`，节点内容区域恢复为 `259px`，文字可见。当前仅完成根因诊断，未修改业务组件。
- 验证结果：`npm run build` 通过；临时布局复刻验证通过；定向执行 `OutlineViewContractTest` 时 4 项中 1 项通过、3 项失败，失败项仍要求旧版页面中的“章纲”、生成按钮及保存逻辑，属于现有页面与契约测试不同步。

## 大纲工作台页面骨架（2026-08-28）

- 将 `novel-agent-web/src/views/OutlineView.vue` 调整为静态大纲工作台骨架：左侧树列表、右侧节点编辑区，并使用 Element Plus 的输入、按钮、标签、空状态、骨架屏、抽屉和确认组件。
- 完成响应式布局：桌面端 `320px minmax(0, 1fr)`、窄屏桌面左栏 `260px`，移动端隐藏右侧编辑区并通过 Drawer 展示编辑骨架；未接入真实树操作、HTTP API 或 AI。
- 验证结果：静态页面契约检查、桌面/窄屏/移动端浏览器布局检查和 `npm run build` 通过。

## Task 29：正文生成前置校验与状态闭环（2026-08-28）

- 保留 `ChapterService` 的 `LOAD_CONTEXT` 唯一生成入口校验：无 `ChapterPlan` 或计划状态不是 `READY` 时，在进入 PLAN 节点前拒绝生成。
- `ChapterPersistRepository` 持久化入口增加 `READY` 状态门禁；章节计划状态通过 `updateStatusIfCurrent` 仅允许在正文及派生数据事务成功后从 `READY` 推进为 `COMPLETED`，状态推进失败会回滚整笔事务。
- 调整 `ChapterPersistRepositoryTest`，覆盖无计划、非 READY、成功完成和持久化失败保持 READY；未修改 PLAN、DRAFT、REVIEW、REVISE 节点行为。
- 定向验证通过：`ChapterContextAggregateTest`、`PersistChapterNodeTest`、`MvpPersistenceContractTest`、`ChapterServiceResumeInputTest`，以及 PLAN/DRAFT/REVIEW/REVISE 相关 `ChapterNodeTest`；真实 MySQL `ChapterPersistRepositoryTest` 5 个用例通过，`git diff --check` 通过。

## Task 28：章节上下文模型命名收敛（2026-08-28）

- 复核章节上下文已使用 `ChapterOutlineEntity` 与 `chapterOutline`，由 `ContextRepository` 从 `chapter_plan` 加载；LangGraph 运行时 `ChapterPlanVO` 保持原有 `PLAN` 产物职责。
- 同步 `IDataService`、`ChapterPlanVO` 和 `SeverityEnum` 的章节模型说明，将残留“章节卡”表述改为章节章纲；未修改 `SystemPrompt` 以及 PLAN、DRAFT、REVIEW、REVISE 节点实现。
- 定向执行 `ChapterOutlineModelTest`、`ChapterContextAggregateTest`、`ChapterNodeTest`，用于确认模型字段、上下文校验和四个节点既有行为未变。

## 人工 Outline 删除接口改为 POST（2026-08-28）

- 将人工 Outline 删除路由改为 `POST /api/v1/novels/projects/{projectCode}/outlines/nodes/{nodeCode}/delete`，删除 Service 入口和业务校验保持不变。
- 同步 `ManualOutlineCrudHttpTest` 与人工 Outline 实施计划，测试请求改为 POST 删除动作。
- 按 TDD 先观察新路由返回 404，再补充 Controller 映射；定向执行 `ManualOutlineCrudHttpTest` 通过，`git diff --check` 通过。

## 规划 Prompt 独立清理边界（2026-08-28）

- 将规划 Prompt 清理独立出来，最终 `PlanningPrompts` 只保留 `ROOT_OUTLINE_SYSTEM`、`CHILD_OUTLINE_SYSTEM` 和 `CHAPTER_PLAN_SYSTEM`。
- 明确旧规划 Prompt 在仍有 `PlanningService`、旧 HTTP/测试或前端引用时暂留，待旧链路清理完成后作为最后一步删除；Chapter LangGraph 的 `SystemPrompt` 不在本次范围内。
- 本次仅调整实施计划与边界文档，未修改 Java、前端或 Chapter LangGraph Prompt；使用 `rg` 完成引用和边界扫描，未运行 Maven 测试。

## 人工章纲编辑：listChapterPlans/updateChapterPlan（2026-08-28）

- 新增 `IPlanningService.listChapterPlans`、`updateChapterPlan` 及对应 API DTO；Service 只做项目编号、章节号、标题和摘要的基本校验，不调用模型，更新时以路径章节号为准且不修改状态。
- 新增 `GET /api/v1/novels/projects/{projectCode}/chapter-plans` 和 `POST /api/v1/novels/projects/{projectCode}/chapter-plans/{chapterNumber}`；查询返回章节号、标题、摘要和状态，更新返回保存后的章节计划。
- `IPlanningRepository` 与 `PlanningRepository` 增加领域值对象转换；更新按项目和章节号定位已有记录，仅写入标题/摘要，保留原主键、所属大纲和状态。
- 新增 Service、HTTP 和真实 MySQL 仓储测试，包含不存在章节计划不调用模型、路径章节号覆盖请求值、状态和主键保持不变等场景；使用本地 MySQL `13306` 定向回归相关测试共 22 个用例通过，`git diff --check` 通过。

## Task 22：实现 confirmChapterPlans（2026-08-28）

- 新增 `IPlanningService.confirmChapterPlans`、确认请求 DTO 和 `POST /api/v1/novels/projects/{projectCode}/outlines/{nodeCode}/chapter-plans/confirm`；请求提交 `draftId` 与章节号、标题、摘要，确认成功后删除 `CHAPTER_PLANS` Draft。
- `PlanningService` 仅校验目标节点、Draft 类型/结构和确认内容基本字段，不查询已有章节计划、不校验章节号重复；保留用户提交顺序和重复章节号，统一将确认状态设为 `READY`。持久化遵循现有项目内章节号唯一键，重复项由数据库按后写内容覆盖前写内容。
- `IPlanningRepository` 与 MyBatis 增加章节计划覆盖写入：按项目内章节号使用 `ON DUPLICATE KEY UPDATE` 更新大纲节点、标题、摘要和状态并保留已有主键；整批确认会清理目标节点下未被正文引用且未出现在本次确认列表的旧计划，避免正文外键因确认失效。
- 新增 Service、HTTP 契约测试及真实 MySQL `PlanningRepositoryTest` 覆盖断言；使用本地 MySQL `13306` 定向执行相关测试类共 17 个用例通过，测试输出确认重复章节号不被 Service 拦截、同批后写内容覆盖前写内容、缺项被清理且已有主键保持不变；`git diff --check` 通过。

## Task 21：实现 generateChapterPlans（2026-08-28）

- 新增 `ChapterPlanDraftVO`、`ChapterPlanDraftListVO` 和 `CHAPTER_PLAN_SYSTEM`，模型返回 `chapterNumber`、`title`、`summary`；章节号列表由 Service 放入用户提示，模型编号不作为最终编号依据。
- 新增 `IPlanningService.generateChapterPlans` 与 `PlanningService` 实现：目标节点存在、章节范围完整且没有 children 时调用模型，`ARC` 不作类型限制；按目标范围生成连续 `ChapterOutlineVO`，状态为 `PLANNED`，保存为 `CHAPTER_PLANS` Draft。
- 新增 `POST /api/v1/novels/projects/{projectCode}/outlines/{nodeCode}/chapter-plans/generate`，无请求体并返回现有 `PlanningDraftResponseDTO`。
- 新增 Service、HTTP 和 Prompt 契约测试，覆盖 Service 编号覆盖模型编号、ARC 叶节点、目标/范围/叶节点校验、模型数量和内容校验；定向测试共 10 个用例通过，未调用真实模型。

## Task 20：confirmChildOutlines 事务化确认（2026-08-28）

- 新增 `IPlanningService.confirmChildOutlines` 与 `PlanningService` 实现：验证 `CHILD_OUTLINES` Draft、项目和 parent，拒绝 `ARC` parent 与 `BOOK` target，并验证 Draft 子节点的 parent、target kind、编码、顺序和章节范围连续完整覆盖父节点。
- 确认请求只接受每个子节点的 title/summary；Service 保留 Draft 分配的 nodeCode、parentNodeCode、nodeKind、sequenceNo 和章节范围，将状态置为 `READY`，通过一次 `saveChildOutlines` 批量落库，成功后删除 Draft。
- 在确认 Service 方法上增加 `@Transactional(rollbackFor = Exception.class)`，覆盖批量写入和 Draft 删除；新增确认请求 DTO 与 `POST /outlines/{parentNodeCode}/children/confirm` 路由。
- 新增 Service 规则测试和 HTTP 契约测试，覆盖成功确认、元数据不可被请求覆盖、Draft/parent/target/range/编辑内容校验及失败时不写库不删 Draft。
- 验证结果：Task 16/17/18 回归测试、Task 19 范围测试和 Task 20 测试共 20 个用例通过；`git diff --check` 通过。

## Task 19：子节点章节范围分配算法（2026-08-28）

- 新增独立无状态领域组件 `OutlineRangeAllocator`，按父节点章节范围和子节点数量分配连续、无重叠、无空洞且完整覆盖的子范围，并拒绝非法范围或无法为每个子节点分配章节的输入。
- `PlanningService` 的 `generateChildOutlines` 改为调用该组件构造 `startChapter`、`endChapter`，Task 18 的 LLM 输出、节点元数据分配和 `CHILD_OUTLINES` Draft 流程保持不变。
- 新增纯单元测试，覆盖 `1..10` 分 3 份得到 `1..4`、`5..7`、`8..10`、一般范围连续覆盖和非法输入拒绝。
- 验证结果：相关 Task 16/17/18 回归测试、`OutlineRangeAllocatorTest` 共 15 个用例通过；`git diff --check` 通过，计划无未完成步骤。

## Task 18：generateChildOutlines 通用子大纲生成（2026-08-28）

- 新增 `ChildOutlineDraftVO` 与 `ChildOutlineDraftListVO`，模型输出收敛为 `title`、`summary`；新增 `CHILD_OUTLINE_SYSTEM`，明确禁止模型生成节点编码、父节点、类型、顺序、章节范围和状态。
- 新增 `IPlanningService.generateChildOutlines` 与 `PlanningService` 实现：校验项目、父节点、`ARC` parent 和 `BOOK` target 规则，读取项目、故事圣经、人物和父节点上下文后调用模型并保存 `CHILD_OUTLINES` Draft。
- Service 按现有同级顺序和节点编码继续分配子节点，按父节点章节范围连续平均切分，构造 `nodeCode`、`parentNodeCode`、`nodeKind`、`sequenceNo`、`startChapter`、`endChapter` 和 `PLANNED` 状态；本次不写正式 `outline_node`，不新增 Confirm 接口。
- 新增 `GenerateChildOutlinesRequestDTO` 与 `POST /api/v1/novels/projects/{projectCode}/outlines/{parentNodeCode}/children/generate`，返回现有 `PlanningDraftResponseDTO`。
- 新增 `ChildOutlineGenerationTest`、`ChildOutlineGenerationHttpTest`，验证 Service 元数据分配、规则拒绝、Draft 保存及 HTTP 参数转换；Task 18 及 Task 16/17 相关定向测试共 12 个用例通过，测试未调用真实模型。

## Task 17：confirmRootOutline Service 确认规则（2026-08-28）

- 新增 `IPlanningService.confirmRootOutline` 和 `PlanningService` 实现：先验证 `ROOT_OUTLINE` Draft 类型及项目不存在 BOOK，再只接受用户修改的 title/summary，使用 Draft 根节点元数据写入正式 `outline_node`，状态置为 `READY`，保存成功后删除 Draft。
- 通过 `IPlanningRepository.findRootOutline` 检查项目根节点，确认流程不调用模型；失败路径不写库也不删除 Draft。
- 新增 `RootOutlineConfirmationTest`，覆盖确认成功、Draft 类型不匹配、项目已有 BOOK 三种路径。
- 新增 `ConfirmRootOutlineRequestDTO` 和 `POST /api/v1/novels/projects/{projectCode}/outlines/root/confirm`，HTTP 请求只携带 draftId、title、summary。
- 验证结果：`RootOutlineConfirmationTest` 与 `RootOutlineConfirmationHttpTest` 定向测试通过。

## generateRootOutline Service 根大纲草稿（2026-08-28）

- 新增 `RootOutlineDraftVO`，将根大纲模型输出收敛为 `title` 和 `summary`；新增 `PlanningService.generateRootOutline`，读取 Project、StoryBible、Characters 和 requirement 后仅调用一次模型。
- Service 构造 `BOOK_001` 根节点的 `BOOK` 类型、空 parent、顺序号 1、`1..targetChapterCount` 章节范围和 `PLANNED` 状态，并通过现有 Draft 仓储保存为 `ROOT_OUTLINE`。
- 新增 `ROOT_OUTLINE_SYSTEM` 和 `GenerateRootOutlineRequestDTO`，增加 `POST /api/v1/novels/projects/{projectCode}/outlines/root/generate`，返回现有 `PlanningDraftResponseDTO`；旧 `generateBookOutline` 流程保持不变。
- 新增 `RootOutlineGenerationTest`、`RootOutlineGenerationHttpTest`，打印并断言上下文读取、模型响应类型、根节点元数据、Draft 类型和 HTTP 路由。
- 验证结果：`RootOutlineGenerationTest` 与 `RootOutlineGenerationHttpTest` 定向测试均通过；两个测试均未调用真实模型。

## HTTP 方法约束修正（2026-08-28）

- 将人工大纲节点更新接口由 `PUT /api/v1/novels/projects/{projectCode}/outlines/nodes/{nodeCode}` 改为 `POST`，请求体和 Service 签名保持不变。
- 在 `AGENTS.md` 增加 HTTP 约束：新增或调整接口只使用 `GET`/`POST`，写操作统一使用 `POST`。
- 同步人工大纲 HTTP 测试及 Task 14/15 计划文档；未修改前端或历史兼容接口。
- 验证结果：定向执行 7 个测试类共 22 个测试通过；源码扫描未发现 `@PutMapping`、`RequestMethod.PUT` 或 `http.put`；`git diff --check` 通过。

## Task 15：大纲同级排序规范化（2026-08-28）

- 沿用人工大纲更新接口的 `sequenceNo` 作为同 parent 目标位置；`PlanningService` 保持 parent 不可修改，因此只支持同级排序，不支持跨 parent move。
- `IOutlineNodeDao` 和 `outline_node_mapper.xml` 增加同级节点查询，`PlanningRepository.updateOutline` 在同一事务内按目标位置重排并将同级顺序统一写回 `1..N`；目标位置超出范围时收敛到首位或末尾，避免重复和跳号。
- 未修改前端拖拽或前端 API，仅将现有 `POST /api/v1/novels/projects/{projectCode}/outlines/nodes/{nodeCode}` 作为后端排序入口。
- 新增排序单元测试、跨 parent Service 测试和真实 MySQL 排序断言；定向执行 `ManualOutlineCrudTest,PlanningRepositoryOrderingTest,ManualOutlineCrudHttpTest,PlanningServiceTest,NovelPlanningControllerHttpTest,OutlineViewContractTest,PlanningRepositoryTest` 共 22 个测试通过，输出确认同级顺序为 `[1, 2, 3]` 且人工 CRUD `modelCalls=0`。`git diff --check` 通过。

## Task 14：实现人工大纲 CRUD（2026-08-28）

- 在 `PlanningService` 增加 `listOutlineTree`、`createOutlineNode`、`updateOutlineNode`、`deleteOutlineNode` 四个人工入口；落实项目内唯一 BOOK、BOOK 不得有 parent、ARC 不得作为 parent、非 BOOK 必须有 parent、只允许删除叶节点、存在 ChapterPlan 不可删除，以及存在子节点或 ChapterPlan 时不可修改章节范围。
- 在 `IPlanningRepository` 和 `PlanningRepository` 增加子节点与 ChapterPlan 存在性查询，复用现有大纲节点和章节计划 DAO；四个人工 Service 入口均未调用 `modelPort.call`。
- 新增人工节点请求 DTO 和 HTTP 路由：查询使用 `/outlines/tree`，新增/修改使用 `/outlines/nodes`、`/outlines/nodes/{nodeCode}`，删除使用 POST `/outlines/nodes/{nodeCode}/delete`，保留原有章节卡兼容接口 `/outlines`，避免路由冲突。
- 新增领域 CRUD 规则测试 4 个和 HTTP 契约测试 1 个；与既有 `PlanningServiceTest`、`NovelPlanningControllerHttpTest`、`OutlineViewContractTest` 合计 20 个定向测试通过，测试输出确认 `modelCalls=0`。`PlanningRepositoryTest` 已通过路由上下文启动并执行到数据库写入，但因本机 MySQL 的旧 `outline_node` 表缺少现有 schema 中的 `node_kind` 列而未通过；未修改 SQL 或测试绕过该环境问题。`git diff --check` 通过。

## Task 13：新增持久化章纲领域模型（2026-08-28）

- 新增规划领域 `ChapterOutlineVO`，承载章节号、标题、摘要和状态；新增章节领域 `ChapterOutlineEntity`，承载 `chapter_plan` 对应的主键、项目、大纲节点关联及业务字段。
- 将章节上下文中的 `ChapterCardEntity` 重命名为 `ChapterOutlineEntity`，同步聚合、上下文仓储、上下文加载器、PLAN/DRAFT 节点和受影响测试的 `chapterOutline` 接线；`ContextRepository` 补齐章节计划主键和大纲节点主键映射。
- `ChapterPlanVO` 保持原有 LangGraph PLAN 运行时模型和字段不变；旧规划 Service 仍使用的 `ChapterCardVO` 及其草稿流程未改动。
- 新增模型契约测试。`ChapterOutlineModelTest`、`ChapterContextAggregateTest` 及 PLAN/DRAFT 受影响节点用例通过；真实 `ContextRepositoryTest` 因本机 `127.0.0.1:3306` MySQL 连接被拒绝未完成；全仓库 Java 源码未发现 `ChapterCardEntity`、`ChapterPlanItemVO` 或 `ChapterPlanEntity` 残留，`git diff --check` 通过。

## Task 12：重构 OutlineNodeVO（2026-08-28）

- 将规划领域 `OutlineNodeVO` 收敛为 `nodeCode`、`parentNodeCode`、`nodeKind`、`sequenceNo`、`title`、`summary`、`startChapter`、`endChapter`、`status` 九个字段；新增 `OutlineNodeKindEnum`，取值为 `BOOK`、`STAGE`、`VOLUME`、`ARC`。
- 同步规划 Service、仓储和 HTTP 转换对新 `nodeKind` 类型的直接引用；数据库仍保存枚举名称，并将历史 `STORY_ARC` 读取为 `ARC`，统一大纲查询会忽略旧 `CHAPTER_CARD` 节点。为保持前序总纲生成改动可编译，补齐 `createBookOutline` 接口声明及其最小仓储转换。
- `BookOutlineVO`、`VolumeOutlineVO`、`StoryArcVO` 当前仍被旧 `PlanningService` 和旧草稿流程引用，因此仅增加待后续 Service 迁移后删除的 `@Deprecated` 标记，未删除旧流程。
- 新增模型测试并迁移受影响测试构造器；`OutlineNodeVOTest`、`PlanningServiceTest`、`NovelPlanningControllerHttpTest` 定向验证共 12 个测试通过。`PlanningRepositoryTest` 已尝试执行，但因本机 MySQL 连接被拒绝而未完成；`git diff --check` 通过。

## 总纲独立生成接口重写（2026-08-27）

- 将总纲生成从整书初始化流程中解耦：新增 `POST /api/v1/novels/projects/{projectCode}/outlines/book/generate`，请求体仅包含 `instructions` 文本。
- `PlanningService.generateBookOutline` 只校验项目和使用说明、调用一次 `BookOutlineDraftVO` 模型并创建单个 BOOK 节点；不会读取或生成故事圣经、人物、分卷、剧情弧或章纲。已有大纲时仓储拒绝重复创建，避免覆盖用户内容。
- 更新 `novel-agent-web/src/views/OutlineView.vue`、规划 API 和类型定义，空状态改为“总纲使用说明”长文本输入，移除该页面对 `initialize/confirmInitialization` 及人物数、卷数参数的调用。
- 新增领域、MVC 和前端契约测试，验证请求字段、单次模型调用、BOOK-only 持久化及页面调用链；定向 Maven 测试与 `npm run build` 均通过。

## 角色模型收敛（2026-08-26）

- 完成 `story_character` 从 26 字段收敛到作者档案、运行时状态和内部字段：移除 14 个废字段，新增 `note`，并将 `gender` 默认值统一为 `OTHER`。
- 同步 StoryCharacter VO/PO/Entity、MyBatis Mapper、项目/规划仓储、HTTP DTO 转换、PLAN/DRAFT 提示词及前端角色表单；前端角色类型改为后端一致的六种取值。
- 将 `docs/sql/schema.sql` 设为唯一有效 DDL，删除重复的 `schema-mvp.sql`、`schema-v1.sql`，并更新迁移脚本、数据库设计和角色方案文档引用。
- `currentStateJson` / `lifeStatus` 的 EXTRACT 自动回填仍按范围暂不实现，仅保留字段和读取路径。
- 验证：`mvn -q -pl novel-agent-app -am -Dtest=MvpPersistenceContractTest,NovelProjectServiceTest,NovelProjectControllerHttpTest -Dsurefire.failIfNoSpecifiedTests=false test` 通过；`npm run build` 通过；`git diff --check` 通过。

> 更新时间：2026-08-18
>
> 本文记录当前已经完成并验证过的实现内容。后续每完成一段独立工作，都要在本文追加摘要。

## 文档状态更正（2026-08-18）

> 本节按代码实际状态更正下方历史条目中已失真的完成度描述。历史条目保留原文作为演进记录，不改写为已完成。

- **章节生成七阶段工作流与 HTTP 接口目前不在代码中。** 下方第 2 节记录的上下文加载、策划、初稿、
  审稿、改稿、事实抽取和持久化七个阶段类，以及对应的章节生成 Controller，已在后续结构化重构中被拆除
  （见第 23 条“并行重构已删除 `IChapterGenerationService` 命令/结果类”）。当前 `trigger` 层无 Controller，
  `domain` 的 `service` 下只有 `ChapterContextLoader` 与 `IDataService` 契约，`INovelService`/`IStoryRepository`
  尚无生产实现。
- **规则树框架仅有测试装配在用。** 第 21 条的 `AbstractStrategyRouter`/`StrategyHandler`/`StrategyMapper`
  已落地，但没有任何生产 Agent 节点接入；七阶段流程尚未迁移到规则树。
- **当前真实基线**是：六表 MySQL 模型与 MyBatis Mapper（第 15 条）、结构化上下文领域模型（第 23 条）、
  `IContextRepository`/`ContextRepository` 分项加载（第 24 条）、`IDataService`/`ChapterContextLoader` 契约（第 25 条）。
- 下方“验证结果”一节的“12 个测试全部通过”是第 15/17 条时的历史快照，与第 21 条（7 个既有 Spring 上下文失败）
  和第 24 条（仅定向运行 `ContextRepositoryTest`）不一致，不代表当前全量结果；重构完成前不应按该数字判断状态。

## 已完成工作

### 1. Java 多模块项目骨架

- 建立 `novel-agent-api`、`novel-agent-app`、`novel-agent-domain`、`novel-agent-trigger`、`novel-agent-infrastructure` 和 `novel-agent-types` 多模块结构。
- 使用 Spring Boot 3.2.5 和 Java 17。
- `novel-agent-app` 作为 Spring Boot 启动模块，负责应用组装和 HTTP 入口。

### 2. 章节生成七阶段工作流

> ⚠️ 已失效（2026-08-18）：本节描述的七阶段实现与章节生成 Controller 已在后续重构中拆除，当前代码不存在
> 对应类。保留原文作为演进记录，最新状态见顶部“文档状态更正”与第 23～25 条。

- 完成上下文加载、章节策划、初稿、审稿、改稿、事实抽取和持久化七个阶段。
- 领域层保持框架无关，通过端口接口连接模型和故事仓储。
- 提供本地可重复运行的演示模型适配器。
- 已实现章节生成 HTTP 接口。

### 3. MySQL 持久化（历史两表基线）

- 使用 Spring JDBC 实现 `MySqlStoryRepository`。
- 支持章节内容和章节事实的事务性保存。
- 重新生成章节时会替换旧事实，避免残留数据。
- 数据库脚本统一放在 [docs/sql/schema.sql](sql/schema.sql)，不再放入 Spring Boot classpath。

### 4. 测试统一归档到 app

- 所有测试统一放在 `novel-agent-app/src/test`。
- 已迁移领域服务测试、MySQL 仓储测试和 Spring Boot HTTP 测试。
- 测试保留原有 Java package，便于测试对应模块的公开类。
- 清理了 domain 和 infrastructure 模块中不再需要的测试依赖。

### 5. app 资源目录结构

- 增加公共配置 `application.yml`。
- 增加 `application-dev.yml`、`application-prod.yml`、`application-stress.yml` 和 `application-test.yml`。
- 增加 `logback-spring.xml` 日志配置。
- `mybatis/config` 和 `mybatis/mapper` 目录用于当前 MyBatis 配置和 XML Mapper。
- YAML 配置使用固定值，不使用环境变量占位符。

### 6. 数据库脚本与自动初始化规则

- 数据库 SQL 独立维护在 `docs/sql`。
- 已移除 Spring Boot 自动执行 `schema.sql` 的配置。
- 数据库初始化改为手动执行：

  ```bash
  mysql -h127.0.0.1 -P13306 -uroot -p novel_agent < docs/sql/schema.sql
  ```

### 7. 项目开发规则

- 在根目录增加 `AGENTS.md`，记录测试目录、app 资源目录和 SQL 脚本位置规则。
- `.gitignore` 自动忽略非 `novel-agent-app` 模块下的测试目录。
- `.gitignore` 自动忽略构建产物和运行日志目录。

### 8. 实现摘要规则

- 新增本文档作为统一的实现工作记录。
- 规则要求每完成一段独立工作，就追加实现内容、主要文件和验证结果。
- README 已增加本文档入口，便于查看当前完成情况。

### 9. 收敛版数据库设计与实施计划

- 将数据库设计从完整远期模型收敛为 15 张 V1 核心表，保留独立的复杂人物表，并将能力、物品和关系暂存于男主状态 JSON。
- V1 DDL 设计已归档到历史记录；当前运行使用唯一的 `docs/sql/schema.sql`。
- 新增分阶段实施计划，明确先实现创作规划和单章记忆闭环，再实现多章调度，最后接入真实模型。
- 本次只完成设计和计划文档，没有执行 V1 DDL，也没有修改 Java 业务代码。

### 10. 实施计划规则统一

- 将 Java 骨架和 MySQL 章节工作流计划标记为已完成的历史基线，将 V1 路线图明确为唯一有效的后续执行计划。
- 三份计划统一遵循当前 `AGENTS.md`：测试全部位于 `novel-agent-app/src/test`，SQL 全部位于 `docs/sql`，环境配置位于 app 主资源目录，数据库只允许手动初始化。
- 修正历史计划中的业务模块测试路径、子模块测试命令、classpath SQL、自动 SQL 初始化、环境变量密码、错误端口和 PostgreSQL 描述。
- 本次仅修改计划与实施总结文档；使用文本一致性扫描完成验证，未修改 Java、配置或 SQL，也未运行 Maven 测试。

### 11. 业务模块空测试目录清理

- 删除测试迁移后遗留的 `novel-agent-domain/src/test` 和 `novel-agent-infrastructure/src/test` 空目录树。
- 检查确认两个目录中没有测试源码或 Git 跟踪文件，现有测试仍统一位于 `novel-agent-app/src/test`。
- 使用全仓库测试目录扫描和 Git 状态检查验证；本次未修改测试代码，因此未运行 Maven 测试。

### 12. docs 历史计划清理

- 删除已经完成且内容被当前实现总结和 V1 路线图覆盖的 Java 骨架计划与 MySQL 单章工作流计划。
- 保留 `2026-08-04-novel-agent-v1-roadmap.md` 作为唯一有效实施计划，并将已完成基线统一指向本文档。
- 检查 README 引用、文档内部链接和剩余文件职责，确认当前架构、章节工作流、整体设计、数据库设计及两套 SQL 基线仍有独立用途；同时修正数据库设计中旧的 classpath SQL 路径描述。
- 使用失效引用扫描和 `git diff --check` 验证；本次仅清理文档，未运行 Maven 测试。

### 13. Task 1 独立实施计划

- 将 V1 路线图的数据库基线 Task 1 拆分为独立实施事项，总路线图只保留执行边界和验收摘要。
- 独立计划补充 V1 与旧仓储隔离测试库、Schema 元数据契约、非破坏迁移映射、数据核对、危险操作边界和最终验证步骤。
- 核对当前代码后修正原计划假设：`application-test.yml` 当前需要新建，且 Task 1 不提前改造仍使用旧两表字段的 `MySqlStoryRepository`。
- 本次仅新增和调整计划文档，使用链接、占位符和一致性扫描验证，未修改业务代码、配置或 SQL，未运行 Maven 测试。

### 14. 六表两章闭环实施计划

- 将下一阶段目标收敛为 6 张核心表以及“第一章摘要串联第二章”的最小闭环。
- 章节卡暂时由 `outline_node` 的 `CHAPTER_CARD` 节点承载，正文暂不版本化；真实模型、多章调度、精确事实和能力拆表全部延后。
- 将原15表 V1路线图和 Schema 基线计划标记为已被替代，仅保留为 MVP 验收后的远期参考，避免存在多个同时生效的计划。
- 本次仅修改计划和实现总结文档，未修改 Java、配置或 SQL，也未执行数据库操作。

### 15. 六表数据库与 MyBatis Mapper 落地

- 将当前数据库模型收敛并落地为 `novel_project`、`story_bible`、`story_character`、
  `outline_node`、`story_chapter` 和 `story_summary` 六张表。
- `story_character` 保留完整人物信息，并通过生成列唯一索引限制每个项目最多一个男主；
  `outline_node` 同时承载全书、分卷、剧情弧和章节卡。
- 新增六组 PO、DAO 接口和 XML Mapper，覆盖新增、修改、按项目查询、章节/摘要幂等写入和
  最近摘要查询；接入 MyBatis Spring Boot Starter 3.0.4。
- 将 `MySqlStoryRepository` 改为使用项目、章节和摘要 Mapper；下一章上下文读取最近章节摘要，
  重复生成相同章节时覆盖正文与摘要，不产生重复记录。
- 新增固定 YAML 测试库配置 `novel_agent_test`，Spring Boot 继续禁止自动初始化 SQL。
- 新增六表 DDL `schema.sql`；旧两表迁移脚本保留
  `story_chapter_v0_backup`、`story_fact_v0_backup` 两张备份表，并显式处理新旧表排序规则差异。
- 已在本地 MySQL `127.0.0.1:13306` 建好开发库六张正式表和独立测试库；旧库核对结果为
  旧章节 1 条 → 新章节 1 条，旧事实 2 条 → 新摘要 1 条，备份尚未删除。
- 新增 DDL/Mapper 契约测试、六组 Mapper 的真实 MySQL 集成测试和 MyBatis 仓储回归测试。
- 代码审查后补强跨项目组合外键，数据库会拒绝章节引用其他项目的章节卡、摘要引用其他项目
  或不同章节号的正文。
- 项目进度改为 `GREATEST` 原子推进，避免并发或补写旧章节导致当前章节号回退；JSON 改由
  Jackson 序列化，摘要查询排除 `STALE` 数据。
- `schema.sql` 保持完整，不依赖 MySQL `SOURCE` 才能干净安装；
  迁移脚本会检查源表/备份表状态，可在重命名阶段中断后继续执行，并已在开发库成功重跑。

## 验证结果（第 15/17 条时点快照）

> ⚠️ 该结果是第 15/17 条完成时的历史快照。后续第 21 条记录了 7 个既有 Spring 上下文失败、
> 第 23～24 条记录了并行重构对全量测试的阻塞，因此“12 个测试全部通过”不代表当前全量状态。
> 当前状态以顶部“文档状态更正”为准。

执行命令：

```bash
mvn -pl novel-agent-app -am test
```

结果（第 15/17 条时点）：12 个测试全部通过，Maven Reactor 构建成功。

## 当前未实现内容（第 15 条时点）

- 数据库脚本目前需要手动执行，尚未接入数据库迁移工具。
- 当前模型适配器仍是本地演示实现，尚未接入真实 LLM。
- 尚未实现两章自动调度 API；当前已具备两章串联所需的数据模型和持久化能力。

### 16. Spring Boot 4 与 Spring AI 2 升级计划

- 规划先升级框架基线，再开发真实小说 Agent。
- 版本收敛为 Java 17.0.19、Spring Boot 4.1.0、Spring AI 2.0.0 和 MyBatis Spring Boot
  Starter 4.1.0；不选择可能与 Spring AI Starter 产生依赖冲突的 Boot 4.0.x。
- 计划包含 Boot 3.5 过渡检查点、Boot 4 模块化 starter、Jackson 3、MyBatis 4、Spring AI
  provider 无关编译基线、默认无密钥启动、依赖树审计和完整回归。
- 按统一父 POM 规则调整计划：根 `dependencyManagement` 必须明确登记项目使用的全部依赖
  坐标，并通过 Boot/Spring AI BOM或根属性管理版本；六个子模块只能选择根目录内的依赖，
  且依赖和插件声明禁止出现版本。增加根依赖目录契约测试与 Maven Enforcer 收敛检查。
- 本次只完成升级计划，没有修改框架版本或接入真实模型。

### 17. Maven 多模块依赖结构与 Boot 4 基线

- 根 `pom.xml` 已成为唯一第三方版本入口，集中固定 Spring Boot 4.1.0、Spring Framework
  7.0.8、MyBatis Starter 4.1.0、MySQL Driver 9.7.0、Jackson 3.1.4 和 Lombok 1.18.46。
- 根 `dependencyManagement` 显式登记当前所有子模块依赖坐标；子模块只在
  `<dependencies>` 中选择自己需要的依赖，不再管理依赖或插件版本。
- `novel-agent-trigger` 已从聚合的 `spring-boot-starter-web` 切换到 Boot 4 的
  `spring-boot-starter-webmvc`；`novel-agent-app` 增加独立的
  `spring-boot-starter-webmvc-test`。
- 仓储 JSON 依赖与 import 已统一到 Jackson 3，避免 Boot 4 自动配置提供 Jackson 3 Bean、
  业务代码却注入 Jackson 2 `ObjectMapper` 的上下文启动冲突。
- 新增 `ChildPomVersionPolicyTest`：检查六个子模块没有自己的 `properties`、
  `dependencyManagement`、依赖版本或插件版本，并要求每个子模块依赖都已在根 POM 登记。
- 执行 `mvn -pl novel-agent-app -am test`，12 个测试全部通过，包含 HTTP、结构契约、
  Mapper 和本地 MySQL 仓储事务测试。

### 18. Spring AI 依赖版本遮蔽与启动修复

- 根 POM 已正确导入 `spring-ai-bom:2.0.0`，并为显式登记的
  `spring-ai-client-chat` 补充 `${spring-ai.version}`；避免本地无版本目录项遮蔽 BOM 导入版本。
- `ChildPomVersionPolicyTest` 增加根依赖目录版本检查：根 `dependencyManagement` 中每个显式
  依赖必须直接声明版本，子模块仍然禁止声明版本。
- Maven 已成功下载 Spring AI 2.0.0、完成七模块编译并重新生成
  `DeterministicLanguageModelPort.class`，解决默认 `dev` 启动找不到 `ILanguageModelPort` Bean。
- 全量 12 个测试通过；可执行 JAR 使用默认 `dev` profile 成功启动，Tomcat 正常监听 18091，
  看到 `Started Application` 后已执行优雅停止。

### 19. ChatClient 基础设施实施计划

- 将下一阶段收敛为 provider-neutral `NovelChatClient`、统一异常、`spring-ai` profile Bean 配置和无网络假模型测试。
- 计划明确本阶段不接供应商 starter、不配置 API Key、不替换 `ILanguageModelPort`，默认 dev/test
  继续使用 deterministic 实现；供应商 YAML 和小说步骤提示词留到后续独立计划。
- 本次只新增实施计划并更新文档记录，没有修改 Java 业务代码或运行时配置，也没有执行模型调用。

### 20. DeepSeek OpenAI 兼容真实接入计划

- 原 fake-only ChatClient 计划已标记为被替代，真实 OpenAI-compatible ChatClient 接入作为唯一执行入口。
- 新计划采用 Spring AI OpenAI starter 对接 DeepSeek OpenAI 兼容 API，模型地址和模型名放在
  YAML，真实 Key 放在 Git 忽略的本地 YAML，便于以后只改配置切换模型。
- 验收不再以 fake `ChatModel` 为完成标准：必须显式通过真实 DeepSeek 连通性 IT 和完整章节
  HTTP 端到端 IT；普通单测仍保持不联网、不消耗额度。
- 评审后删除了 infrastructure 内重复的 `ChatGateway` 接口设计：领域侧继续只保留
  `ILanguageModelPort`，基础设施使用具体 `NovelChatClient` 包装 Spring AI `ChatClient`，避免端口套端口。
- `ChatClientConfiguration` 改由 `novel-agent-app` 组合根管理，移除 `@Profile("deepseek")`；
  Java 代码只装配 `ChatModel → ChatClient → NovelChatClient`，供应商选择完全由 YAML 决定。
- 计划中的普通 Spring 测试将显式导入不联网的 test `ChatModel`，真实 DeepSeek IT
  不导入 fake，从装配层面防止真实验收被替换。
- 本次仅重写计划与实施记录，尚未添加 OpenAI starter、DeepSeek YAML 或真实模型适配器，
  也未执行任何 DeepSeek API 请求。

### 21. Agent 规则树基础架构

- 在 `novel-agent-types` 增加通用的 `StrategyHandler`、`StrategyMapper` 和
  `AbstractStrategyRouter`，节点可以执行自身逻辑、根据请求和动态上下文选择下一节点，并通过
  `DEFAULT` 处理器结束流程。
- `StrategyHandler.DEFAULT` 使用单一无状态实例，并通过泛型 `defaultHandler()` 方法安全复用，
  业务节点不需要使用原始类型或自行编写空节点。
- 在 `novel-agent-app/src/test` 增加测试内装配的分支规则树，验证 `Root → FAST/SAFE → End`
  条件路由、同一动态上下文跨节点传递、末端结果返回和默认处理器终止行为。
- 本阶段只提供多 Agent 节点装配的基础能力，尚未迁移现有七阶段章节工作流，也未增加多线程
  路由；具体 Agent 节点和并发策略将在后续业务树中按需要实现。
- 定向执行 `StrategyRouterTest`，2 个测试全部通过，Maven Reactor 构建成功。全量测试共发现
  14 个测试中的 7 个既有 Spring 上下文错误，原因是正在开发的 OpenAI 自动配置在 test
  profile 下没有 API Key 或假 `ChatModel`；新增的 2 个规则树测试均通过。

### 22. 真实模型联调 profile 与接口复用规则

- 在根目录 `AGENTS.md` 增加模型联调环境规则：本地真实模型启动和联调统一使用 `dev`
  profile，不再为 DeepSeek、OpenAI 或其他供应商创建独立 Spring profile。
- 供应商切换统一通过 `application-dev.yml` 的 Spring AI 配置完成，Java Bean 装配保持供应商无关；
  `test` profile 继续只承担不联网、不消耗额度的自动化测试。
- 明确真实模型端到端验证复用现有 `POST /api/v1/novels/chapters/generate`，业务契约不变时不重复
  新增 Controller 或 API；同时补充 API Key 不得提交 Git 的规则。
- 本次仅修改项目规则与实现总结，没有修改 Java、YAML 或接口代码；使用 `git diff --check` 验证文档格式。

### 23. 章节结构化上下文领域模型

- 在 `novel-agent-domain` 的 `model/aggregate`、`model/entity` 和 `model/valobj` 下补齐章节上下文模型，
  包含章节上下文聚合、章节卡与人物实体，以及近期摘要、上一章和章节历史值对象。
- `ChapterContextAggregate` 统一承载项目、故事圣经、章节卡、人物和历史数据，并提供章节号、必需数据及
  上一章连续性的生成前校验；本阶段没有实现 Loader、Repository、SQL 查询、Agent 或 HTTP 接口。
- 沿用已有 `NovelProjectEntity` 与 `StoryBibleEntity`，仅为项目实体补充外部加载需要的 `projectCode`；
  JSON 数据继续使用原始字符串承载，不在模型阶段提前解析。
- 使用 Java 17 和 Lombok 1.18.46 对 `domain/chapter/model` 全部源码进行独立编译，编译通过；
  `git diff --check` 通过。全模块 Maven 当前仍被并行重构中已删除命令/结果类但尚未同步调整的
  `IChapterGenerationService` 阻塞，因此未将全量测试记录为通过。
- 为本阶段全部聚合、实体和值对象补充类级 JavaDoc和字段注释，明确各模型及每个参数在结构化上下文中的职责和边界；
  同时补充聚合校验方法的参数与异常说明。

### 24. 结构化上下文仓储分项加载

- 完成 `IContextRepository` 的项目、故事圣经、章节卡、人物和历史五类分项加载契约，并补充方法参数、
  返回值和职责 JavaDoc；领域端统一使用 `projectCode`，不要求调用方传入数据库主键。
- 完成 infrastructure 的 `ContextRepository`：内部先解析项目数据库主键，再通过现有 MyBatis DAO 查询并
  映射为领域 Entity/VO；历史数据包含最近 8 章摘要和直接上一章，摘要统一按章节号升序返回。
- 仓储只负责查询和映射：项目或单项数据不存在时返回 `null`/空列表，第一章允许没有上一章；是否可以进入
  生成流程仍留给后续 domain Loader 和聚合校验处理。
- 新增 `ContextRepositoryTest`，使用 `dev` profile，通过真实 Spring 容器、MySQL、MyBatis Mapper 和六表
  测试数据验证结构化上下文加载；测试前后按独立项目编码清理数据，不再使用全 Mock 字段映射测试。
- 使用 `mvn -pl novel-agent-app -am -Dtest=ContextRepositoryTest
  -Dsurefire.failIfNoSpecifiedTests=false test` 连接 `dev` 环境真实 MySQL 验证，1 个测试通过，七模块 Reactor
  构建成功；测试覆盖六表写入、Repository 分项加载、PO 到领域模型映射及测试数据清理。
- 为 `ContextRepository` 的查询数量常量、DAO 成员、构造方法、公开加载方法和私有转换方法补充完整中文注释，
  并标明方法的 `@param`、`@return`；本次未改变上下文加载逻辑。
- 在 `AGENTS.md` 明确仓储、Mapper 和结构化上下文加载优先使用真实 MySQL 集成测试；本地数据库可验证的
  持久化逻辑不再使用全 Mock 测试替代。

### 24b. PO 字段用途注释

- 为 `novel-agent-infrastructure` 的 `dao/po` 下 6 个持久化对象补充全部字段的中文 Javadoc，明确项目、故事圣经、角色、大纲、章节和章节摘要字段在持久化、上下文组装及续写中的用途。
- 本次只增加代码注释，不改变字段、Mapper 映射或运行时行为；使用 Maven 编译和 `git diff --check` 验证。

### 25. 章节结构化数据 Service 契约

- 在 domain 的 `service/data` 定义 `IDataService`，通过 `loadContext(projectCode, chapterNumber)` 返回
  `ChapterContextAggregate`，作为后续结构化加载和 Agent 编排的统一入口。
- `ChapterContextLoader` 保持 Spring `@Service`；本阶段只确定 Service 契约，尚未让空实现类提前实现接口，
  也未编写 Repository 调用及聚合组装逻辑，避免产生未实现抽象方法的编译错误。

### 26. 章节工作流 VO 注释完善

- 为 `ChapterPlanVO`、`ReviewIssueVO`、`ReviewReportVO` 和 `ExtractedFactVO` 补充类级及字段 JavaDoc，
  明确它们分别作为 PLAN、REVIEW 和 EXTRACT 节点临时产物的职责、字段语义与流转边界。
- 完成 `ReviewReportVO.hasBlock()` 的空安全实现：问题列表为 `null` 或空列表时返回
  `false`，发现任一 `BLOCKER` 问题时返回 `true`，并容忍列表中的空问题项。
- 在 `novel-agent-app/src/test` 新增 `ReviewReportVOTest`，覆盖包含 BLOCKER、仅 MAJOR/MINOR、
  空列表和 `null` 列表四种情况；定向 Maven 测试共 4 个用例全部通过，Reactor 构建成功。

### 27. 章节生成上下文前置校验

- 在 `ChapterContextAggregate` 实现 `validateReadyForGeneration()`，在调用模型前统一校验
  小说项目、故事圣经和当前章节卡必须存在；第二章及之后还必须具备直接上一章快照。
- 校验失败统一抛出 `AppException`，使用 `ResponseCode.ILLEGAL_PARAMETER` 错误码并返回
  可定位缺失数据的中文消息；`novel-agent-domain` 同步增加对 `novel-agent-types` 的直接依赖。
- 在 `novel-agent-app/src/test` 新增 `ChapterContextAggregateTest`，覆盖三类必需数据缺失、
  第一章无历史、续写章节无历史/无上一章以及正常续写，定向测试 7 个用例全部通过。

### 28. LangGraph4j 领域 Agent Guide 修订

- 修订 LangGraph4j 领域 Agent 设计，明确保留
  “具体 domain agent 持有 prompt + 单一 `IChapterModelPort`”边界，不再为每种生成能力增加一对一接口。
- 新增计划中的 `ChapterExtractionVO` 组合产物，统一承载章节摘要和抽取事实，
  并把 `FactExtractor`、Graph State、PERSIST 仓储签名及节点说明同步到该产物。
- 明确事实与摘要的章节号由 `FactExtractor` 使用方法入参归一化，不信任模型生成的章节号；
  同时划分传输层瞬时重试与 Graph 语义重试，避免两层循环叠加。
- 将 Guide 内的 `ReviewReportVO` 字段和方法名称同步为已实现的 `reviewIssueVOList` / `hasBlock()`，
  并更正聚合前置校验的实际完成状态。本次仅修改设计文档和实施总结，未修改生产代码。

### 29. 领域 Agent 真实模型验收规则

- 将 LangGraph4j Guide 中的 Agent、Graph 和 HTTP 模型验收统一修订为真实模型测试，
  删除假 `IChapterModelPort` / 假 `ChatModel` 方案；真实模型 IT 显式使用 `dev` profile 运行，
  常规 `test` profile 仍不联网、不消耗模型额度。
- Agent / Graph 真实模型测试可 Mock 上下文数据服务和持久化端口，以固定输入隔离模型能力；
  仓储与 Mapper 的持久化逻辑仍使用真实 MySQL 集成测试。
- PASS / REVISE / HUMAN / ABORT 路由、改稿上限和 resume 契约通过直接构造 Graph State 的
  确定性测试验证，不为制造特定模型输出而引入假模型。

### 30. 领域 Agent 具体实施指引

- 扩展 LangGraph4j Guide §4.3，为 `ChapterPlanner`、`ChapterDrafter`、`ChapterReviewer`、
  `ChapterReviser` 和 `FactExtractor` 逐个明确文件位置、方法签名、system prompt 强制约束、
  user prompt 输入字段、模型端口调用方式、结果验证和确定性归一化规则。
- 将 REVIEW / REVISE 签名补充实际 `ChapterPlanVO` 入参，确保审稿和改稿依据 PLAN 节点的真实产物，
  并同步更新 Graph 节点调用指引。
- 新增 `MODEL_RESPONSE_INVALID` 语义失败契约，用于区分可由 Graph 重试的模型输出问题、
  请求参数问题与技术异常。
- 补充 `ChapterAgentsRealModelIT` 的逐 Agent 验收断言，真实模型输出只校验结构契约、
  必须约束和确定性归一化，不做逐字匹配。本次仅修改文档，未修改生产代码。

### 31. 模型端口与 Agent 实施顺序校正

- 将 LangGraph4j Guide 中的 `IChapterModelPort` 调整到领域 Agent 之前，实际开发顺序统一为
  “端口契约 → `NovelChatClient` 结构化能力 → `ChapterModelAdapter` → 具体 Agent → Graph”。
- 将模型端口设为 §4.2，Agent 具体指引顺延为 §4.3，并同步更新后续 Graph 节点和实施速查表的依赖顺序。
- 新增 `ChapterModelPortRealModelIT` 指引，在编写 Agent 前先使用真实模型验证纯文本与
  `ChapterPlanVO` 结构化输出两种端口能力。本次仅修改设计与实施总结文档。

### 32. 模型端口真实输出联调测试

- 在 `novel-agent-app/src/test/java/cn/ninth/novel/infrastructure/adapter/port` 新增
  `ChapterModelPortRealModelIT`，使用 `dev` profile 注入真实 `IChapterModelPort`，分别调用纯文本和
  `ChapterPlanVO` 结构化输出能力；测试按手工联调用途直接打印模型结果，不添加内容断言。
- 首次结构化调用发现模型将 `characterChanges` 的字符串 value 生成为嵌套对象；测试提示词补充明确的
  `Map<String, String>` 输出约束后，真实模型文本与结构化调用各 1 个用例均成功并打印结果。
- 使用 `mvn -pl novel-agent-app -am -Dspring.profiles.active=dev -Dtest=ChapterModelPortRealModelIT
  -Dsurefire.failIfNoSpecifiedTests=false test` 完成真实模型验证，2 个用例通过，七模块 Reactor 构建成功。
- 按项目规则补跑 `mvn -pl novel-agent-app -am test`；全量 22 个用例中 1 个依赖管理策略测试失败、4 个
  `test` profile 应用上下文用例因缺少可用数据源而错误，属于现有测试环境问题，本次未扩大范围修改。

### 33. 章节计划人物变化结构简化

- 将 `ChapterPlanVO.characterChanges` 从动态键的 `Map<String, String>` 改为 `List<String>`，每项使用完整句子描述人物及其状态、认知或关系变化，使模型结构化输出保持简单稳定，并与总体设计中的章节卡和章节摘要结构一致。
- 同步简化 `ChapterModelPortRealModelIT` 的提示词，不再要求模型遵守 Map value 的额外限制；LangGraph4j Guide 中的字段定义、提示词约束和空值归一化规则一并更新。
- 在 `novel-agent-app/src/test` 新增 `ChapterPlanVOTest`，以字段原始类型和泛型参数锁定 `List<String>` 契约。
- 定向 Maven 测试 1 个用例通过且七模块 Reactor 构建成功；按规则补跑全量测试共 23 个用例，新增契约测试通过，仍有既存的 1 个依赖管理策略失败和 4 个测试数据源错误。

### 34. 测试验证范围约束

- 更新根目录 `AGENTS.md`：修改或新增测试后只运行与当前改动直接相关的定向测试，默认禁止执行全量测试；仅当用户明确要求全量验证时例外。
- 补充 Maven 定向测试写法，要求通过 `-Dtest` 限定测试类或方法，多模块构建可使用 `-Dsurefire.failIfNoSpecifiedTests=false`。
- 本次仅修改项目规则与实施总结，使用 `git diff --check` 做文本格式验证，不运行测试。

### 35. data 目录分层日志配置

- 调整 `novel-agent-app/src/main/resources/logback-spring.xml` 与公共 `application.yml`，将默认日志目录改为
  `./data/log`，INFO 与 WARN/ERROR 分别写入独立文件，并通过异步 appender 输出。
- INFO 和 WARN/ERROR 文件均按日期及 100MB 大小滚动压缩；INFO 保留 15 天、总量上限 10GB，
  WARN/ERROR 保留 7 天、总量上限 5GB；dev profile 的项目 DEBUG 日志仅输出到控制台。
- 更新 `.gitignore` 忽略各启动目录下的 `data/log` 运行日志；使用定向 `ContextRepositoryTest` 启动完整
  dev Spring 上下文，1 个用例通过，并确认 `novel-agent-info.log`、`novel-agent-error.log` 自动创建且
  INFO 日志实际写入目标文件。

### 36. ChapterPlanner 真实模型联调用例

- 在 `novel-agent-app/src/test/java/cn/ninth/novel/domain/chapter/service/agent` 新增
  `ChapterPlannerRealModelIT`，使用 `dev` profile 注入真实 `ChapterPlanner`，构造第一章的项目、故事圣经、
  章节卡和人物上下文，调用 `plan()` 并校验核心结构化字段。
- 移除 `ChapterPlanner` 上未使用的 `@Slf4j`，避免领域模块因未引入 SLF4J API 而无法编译；不改变当前
  Planner 的调用和 prompt 组装逻辑。
- 按用户要求未运行测试或 Maven 验证命令，用例结果待后续手工联调确认。

### 37. domain 模块引入 SLF4J API

- 在 `novel-agent-domain/pom.xml` 新增 `org.slf4j:slf4j-api` 直接依赖，版本继续由 Spring Boot 父 POM
  统一管理，使领域服务可以使用 Lombok `@Slf4j` 生成的日志字段。
- 保留 `ChapterPlanner` 中已恢复的 `@Slf4j` 和章节计划生成开始日志，未修改 Planner 的业务逻辑。
- 本次未运行测试；使用 Maven 定向编译 `novel-agent-domain` 及其依赖模块验证配置。

### 38. ChapterDrafter 单节点闭环

- 完成 `novel-agent-domain` 中的 `ChapterDrafter`：生成前校验章节上下文和章节计划，通过纯文本
  `IChapterModelPort` 调用生成正文，对模型空输出统一抛出 `E0003`，并仅记录项目编码和章节号等必要日志。
- 补全 DRAFT 专用 system prompt 与 user prompt：完整传入 `ChapterPlanVO`、故事硬规则、力量体系、文风、
  人物当前状态、近期章节摘要、上一章完整正文及目标字数；移除误混入 system prompt 的对话说明文本。
- 在 `novel-agent-app/src/test` 新增 `ChapterDrafterRealModelIT`，使用 `dev` profile 构造固定上下文和章节
  计划，打印 DRAFT 的 system prompt、user prompt 与真实模型正文输出，不使用断言；定向真实模型测试
  1 个用例通过，七模块 Reactor 构建成功，输出正文落实了主要冲突、信息揭示、伏笔和结尾钩子。

### 39. Agent prompt 基础渲染复用

- 在 `novel-agent-domain` 的章节 Agent 包新增 `PromptAppender`，集中提供空值归一化、普通行、可选行和
  缩进行渲染，移除 `ChapterPlanner` 与 `ChapterDrafter` 中重复的 `appendLine` / `value` 实现。
- 保留两个 Agent 原有空值语义：PLAN 必需字段继续渲染为“无”，DRAFT 可选空字段继续跳过整行，避免纯
  重构意外改变发送给模型的 prompt。
- 按功能联调测试精简要求，不为渲染工具保留独立断言型单测；其集成结果由唯一的
  `ChapterDrafterRealModelIT` 打印完整提示词和模型输出进行人工检查。

### 40. ChapterReviewer 审稿提示词组装

- 完成 `novel-agent-domain` 中 `ChapterReviewer.buildReviewPrompt`，按独立分区传入故事硬规则与文风、
  `ChapterPlanVO` 全字段、人物当前状态、近期章节摘要、上一章完整正文和当前待审稿正文，并对空集合使用
  明确的“无”语义，避免模型混淆计划、历史与当前草稿。
- 在 `novel-agent-app/src/test` 新增 `ChapterReviewerTest`，使用固定上下文校验所有审稿证据均进入 user
  prompt，不调用外部模型。
- 使用 `mvn -pl novel-agent-app -am -Dtest=ChapterReviewerTest
  -Dsurefire.failIfNoSpecifiedTests=false test` 完成定向验证，1 个用例通过，七模块 Reactor 构建成功。

### 41. Agent 列表 prompt 渲染复用

- 将 `ChapterDrafter` 与 `ChapterReviewer` 重复的 `appendList` 下沉到 `PromptAppender`，统一列表标题、空值、
  空白项过滤和条目首尾空格处理逻辑；两个 Agent 改为静态导入共享实现。
- 在 `novel-agent-app/src/test` 新增 `PromptAppenderTest`，直接验证列表条目过滤与渲染格式，并保留
  `ChapterReviewerTest` 验证 REVIEW prompt 的集成结果。
- 使用 `mvn -pl novel-agent-app -am -Dtest=PromptAppenderTest,ChapterReviewerTest
  -Dsurefire.failIfNoSpecifiedTests=false test` 完成定向验证，2 个用例通过，七模块 Reactor 构建成功。

### 42. Agent 真实模型联调用例输出规则

- 更新根目录 `AGENTS.md`，明确 Agent 真实模型联调用例统一使用 `RealModelIT` 命名和 `dev` profile，
  不对模型内容添加 Assert 断言，只打印 system prompt、user prompt 与模型返回结果供人工检查。
- 将 `novel-agent-app/src/test` 中原 `ChapterReviewerTest` 调整为 `ChapterReviewerRealModelIT`，注入真实
  `ChapterReviewer`，依次打印 REVIEW 的两类提示词和结构化 `ReviewReportVO`，删除全部内容断言。
- 使用 `mvn -pl novel-agent-app -am -Dspring.profiles.active=dev -Dtest=ChapterReviewerRealModelIT
  -Dsurefire.failIfNoSpecifiedTests=false test` 完成定向真实模型验证；1 个用例通过，七模块 Reactor 构建成功，
  模型识别出草稿使用凝气境能力违反锻体境硬规则，并返回一条 `BLOCKER` 问题及对应正文证据。

### 43. ChapterReviser 改稿提示词与真实模型联调

- 完成 `novel-agent-domain` 中 `ChapterReviser.buildRevisePrompt`，按独立区块传入章节计划、故事硬规则与
  文风、人物当前状态、近期摘要、上一章正文、当前原稿，以及审稿问题的严重程度、分类、说明和原文证据。
- 补齐 `ChapterReviser.revise` 文本模型调用，对空响应统一抛出 `E0005`，正常结果去除首尾空白后返回；
  改稿日志不记录完整正文。
- 在 `novel-agent-app/src/test` 新增 `ChapterReviserRealModelIT`，使用固定的带证据 `BLOCKER` 审稿问题，
  不添加 Assert，依次打印 REVISE 的 system prompt、user prompt 和真实模型修改结果。
- 使用 `mvn -pl novel-agent-app -am -Dspring.profiles.active=dev -Dtest=ChapterReviserRealModelIT
  -Dsurefire.failIfNoSpecifiedTests=false test` 完成定向联调；1 个用例通过，七模块 Reactor 构建成功，模型删除了
  违反锻体境硬规则的凝气能力描写，并保留了船票信息揭示和结尾敲门钩子。

### 44. FactExtractor 事实抽取节点闭环

- 在 `novel-agent-domain` 新增 `ChapterExtractionVO`，统一承载 `List<ExtractedFactVO>` 与
  `ChapterSummaryVO`，作为 EXTRACT 节点的结构化组合结果。
- 完成 `FactExtractor`：user prompt 只传章节号和最终正文；调用结构化模型后校验摘要与事实必需字段，
  将空事实列表归一化为空集合，按三元组与事实类型去重，并用方法入参统一覆盖摘要和全部事实的章节号。
- 在 `novel-agent-app/src/test` 新增 `FactExtractorRealModelIT`，使用 `dev` profile 打印 EXTRACT 的 system
  prompt、user prompt 和真实模型结构化结果，不添加 Assert。
- 使用 `mvn -pl novel-agent-app -am -Dspring.profiles.active=dev -Dtest=FactExtractorRealModelIT
  -Dsurefire.failIfNoSpecifiedTests=false test` 完成定向联调；1 个用例通过，七模块 Reactor 构建成功，模型返回
  8 条结构化事实和完整章节摘要，摘要及全部事实的章节号均被统一为方法入参 `2`。

### 44. ChapterReviser 真实模型用例补齐上一章正文

- 修正 `novel-agent-app/src/test` 中 `ChapterReviserRealModelIT` 的历史上下文固件，将原先一句摘要式文本替换为
  包含环境、人物反应、动作和章末冲突的上一章正文，并补齐正文字数与终稿状态，使改稿模型能够获得有效的
  直接衔接上下文。
- 使用 Maven 定向执行 `ChapterReviserRealModelIT` 验证；打印的 user prompt 已包含补齐后的“直接上一章正文”，
  1 个用例通过、七模块 Reactor 构建成功，真实模型输出承接了雨夜客栈、老板堵门和剑鞘刻痕等历史细节。

### 45. ChapterReviser 联调故事上下文连续性修正

- 修正 `novel-agent-app/src/test` 中 `ChapterReviserRealModelIT` 的故事固件：上一章以老板上楼索要佩剑和船票
  结尾，当前草稿紧接老板夺票；上一章只留下未解的“柒”字仓印，本章再通过老板口供确认旧渡口，避免历史
  摘要提前完成当前章节目标。
- 将唯一的待修问题收敛为林澈使用凝气境力量隔空震退老板，并让审稿证据与该原句完全一致；同时明确大门
  保持关闭、章末敲门节奏为三短一长，并在当前草稿中显式关联佩剑刻痕与船票“柒”字仓印，避免场景状态、
  章节计划与结尾钩子互相矛盾。
- 使用 Maven 定向执行 `ChapterReviserRealModelIT` 验证；1 个用例通过、七模块 Reactor 构建成功。真实模型仅将
  “凝气境隔空震人”改为近身格挡和借力推摔，完整保留佩剑刻痕、老板口供、旧渡口柒号仓、追问兄长及
  三短一长敲门钩子，并自然承接上一章结尾。

### 46. ChapterGraphState 编排状态

- 在 `novel-agent-app/src/main/java/cn/ninth/novel/orchestration` 完成 `ChapterGraphState` 与
  `ChapterGraphKeys`：State 继承 LangGraph4j `AgentState`，集中定义章节图的领域数据和编排控制键，并提供
  类型化读取方法，不向 domain 或 HTTP 契约暴露 LangGraph4j 类型。
- 为 `reviseRound`、`retryCount` 提供零值默认状态；为 `completedStages` 配置追加去重 reducer；`extraction`
  不配置 reducer，确保 EXTRACT 重试时整体覆盖快照而不累积重复事实。
- 在 `novel-agent-app/src/test/java/cn/ninth/novel/orchestration` 新增 `ChapterGraphStateTest`，覆盖 schema 默认值、
  类型化读取、阶段追加去重和抽取快照覆盖；使用 `mvn -pl novel-agent-app -am -Dtest=ChapterGraphStateTest
  -Dsurefire.failIfNoSpecifiedTests=false test` 定向验证，4 个用例通过、七模块 Reactor 构建成功。

### 47. 章节图模块边界迁移

- 按项目模块职责将 `ChapterGraphState` 与 `ChapterGraphKeys` 从 `novel-agent-app` 迁移至
  `novel-agent-domain/src/main/java/cn/ninth/novel/domain/chapter/workflow`；测试仍物理保留在
  `novel-agent-app/src/test`，Java package 同步调整为 domain workflow。
- 将 `langgraph4j-core` 直接依赖从 app 调整到 domain 与 trigger：domain 承载 State、领域 Agent 节点和路由规则，
  trigger 后续由 Controller 组装并调用 `CompiledGraph`，app 只保留启动及数据源、模型客户端、checkpointer 等配置。
- 同步修订 LangGraph4j 开发指导及上游落地设计的决策 F，删除 `app/orchestration`、
  `ChapterGraphConfiguration`、`ChapterGraphRunner` 和一对一薄 Node 包装方案，明确领域 Agent 直接提供节点能力、
  Controller 单例初始化阶段只构图一次。
- 使用 `mvn -pl novel-agent-app -am -Dtest=ChapterGraphStateTest -Dsurefire.failIfNoSpecifiedTests=false test`
  完成迁移后的定向验证，4 个用例通过、七模块 Reactor 构建成功。
- 联合执行 `ChapterGraphStateTest,ChildPomVersionPolicyTest` 时，State 的 4 个用例通过；POM 策略测试在扫描到
  本次新增 LangGraph4j 依赖前，因 domain 原有 `org.slf4j:slf4j-api` 未登记根 POM 而失败。该既有版本治理问题
  不在本次边界迁移中扩改，需后续单独处理。

### 48. Domain 直接依赖根 POM 管理补齐

- 在根 `pom.xml` 显式锁定 SLF4J `2.0.18` 与 Reactor Core `3.8.6`，并将 `slf4j-api`、`reactor-core` 加入
  `dependencyManagement`，使 domain 的直接依赖全部遵循根 POM 唯一版本入口规则。
- 检查 domain 生产代码后确认没有使用任何 Tomcat API，删除 `novel-agent-domain/pom.xml` 中残留的
  `tomcat-embed-core`，避免为了通过依赖策略测试而继续保留无用容器依赖。
- 使用 `mvn -pl novel-agent-app -am -Dtest=ChildPomVersionPolicyTest
  -Dsurefire.failIfNoSpecifiedTests=false test` 定向验证，1 个用例通过、七模块 Reactor 构建成功。

### 49. 章节领域 Agent 直接节点化

- 让 `ChapterPlanner`、`ChapterDrafter`、`ChapterReviewer`、`ChapterReviser` 和 `FactExtractor` 直接实现
  LangGraph4j `NodeAction<ChapterGraphState>`，保留原业务方法，并由 `apply` 完成 State 输入读取与 partial State
  输出，不新增一对一 Node 包装类。
- 五个节点成功后分别写回 `plan`、`draft`、`reviewReport` 或 `extraction`，同时统一写入 `currentNode` 和追加
  `completedStages`；缺少前序 State 时抛出带节点名与字段名的明确异常。
- 将上述 Agent 及 `ChapterContextLoader` 从 `@Resource` 字段注入改为构造器注入，消除此前依赖
  `tomcat-embed-core` 传递提供 `jakarta.annotation` 的隐式编译关系，确保 domain 可从干净状态重新编译。
- 在 `novel-agent-app/src/test` 新增 `ChapterAgentNodeTest`，以固定业务产出覆盖 PLAN、DRAFT、REVIEW、REVISE、
  EXTRACT 五个节点的参数传递和 State 更新；使用 Maven 定向验证，5 个用例通过、七模块 Reactor 构建成功。

### 50. 章节节点命名与入口收敛

- 将 domain 中五个章节生成类型统一重命名为 `PlanChapterNode`、`DraftChapterNode`、`ReviewChapterNode`、
  `ReviseChapterNode`、`ExtractFactsNode`，类名直接表达其 LangGraph4j 节点身份，不再使用 Agent 风格名称。
- 将 `plan`、`draft`、`review`、`revise`、`extract` 收敛为节点内部 `private` 方法，外部统一通过
  `apply(ChapterGraphState)` 传递输入和获取 partial State；五个节点的输出测试严格限制为业务结果、
  `currentNode`、`completedStages` 三个 key。
- 将对应真实模型用例同步重命名为 `*NodeRealModelIT`，并改为从 State 调用节点；保留 system prompt、user prompt
  和模型结果打印，PLAN 用例移除模型内容 Assert。同步修订 LangGraph4j 开发指导与上游编排设计，明确 domain
  直接提供 Node、Controller 在 trigger 组图、内部业务步骤不可由图外调用。
- 按 TDD 先执行 `ChapterNodeTest`，确认因新节点类尚不存在而编译失败；完成实现后再次定向执行，5 个用例通过、
  七模块 Reactor 构建成功。未运行真实模型用例和全量测试。

### 51. 章节生成领域服务契约

- 在 `novel-agent-domain/src/main/java/cn/ninth/novel/domain/chapter/service` 新增 `IChapterService`，以
  `projectCode`、`chapterNumber` 作为领域入参，并通过 model 中的 `ChapterGenerationResult` 返回正文、审稿报告、
  事实抽取结果和已完成阶段；该契约不依赖 HTTP DTO 或统一响应包装。
- 将 `NovelChapterController` 的构造器依赖从已删除的 API 层 `INovelService` 切换为 domain 层
  `IChapterService`，保持 trigger → domain 的依赖方向；本次只建立服务契约，未实现 LangGraph4j 图执行逻辑。
- 在 `novel-agent-app/src/test/java/cn/ninth/novel/domain/chapter/service` 新增 `IChapterServiceTest`，验证接口可通过
  领域参数生成并返回不可变的领域结果；使用 `mvn -pl novel-agent-app -am -Dtest=IChapterServiceTest
  -Dsurefire.failIfNoSpecifiedTests=false test` 定向验证，1 个用例通过、七模块 Reactor 构建成功，未运行全量测试。

### 52. 章节 workflow 与生成结果包结构收敛

- 将 `ChapterGraphState`、`ChapterGraphKeys` 从 `domain/chapter/workflow` 迁移到
  `domain/chapter/service/workflow`，使章节编排状态与节点、数据服务共同归入 service；同步迁移测试物理目录，并更新
  五个章节节点及其自动化、真实模型用例的全部 Java import。
- 将 `ChapterGenerationResult` 从 `domain/chapter/service` 迁移到 `domain/chapter/model/valobj`，由
  `IChapterService` 显式依赖领域结果值对象，保持 service 目录只承载服务契约与行为。
- 同步更新 LangGraph4j 落地设计和开发指导中的目录说明；按 TDD 先将 `ChapterGraphStateTest` 迁入新 package，确认
  因生产类型尚未迁移而编译失败，再完成代码迁移。使用 `mvn -pl novel-agent-app -am
  -Dtest=ChapterGraphStateTest,IChapterServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` 定向验证，5 个用例通过、
  七模块 Reactor 构建成功，未运行全量测试。

### 53. 章节节点缺失 State 异常统一

- 将 `PlanChapterNode`、`DraftChapterNode`、`ReviewChapterNode`、`ReviseChapterNode` 和 `ExtractFactsNode` 中所有
  State 必需字段缺失分支从 `IllegalStateException` 统一改为 `AppException`，使用
  `ResponseCode.ILLEGAL_PARAMETER` 并保留原有节点名、字段名错误信息；各节点已有的模型输出错误码保持不变。
- 在 `novel-agent-app/src/test/java/cn/ninth/novel/domain/chapter/service/agent/ChapterNodeTest` 新增缺失 State
  契约用例，覆盖五个节点共 12 个必需输入分支，并校验异常类型、错误码和信息。
- 定向测试命令在进入 `ChapterNodeTest` 前，被当前尚未完成的 `ChapterService.loadContext(...)` 缺少返回语句阻塞；
  静态扫描确认五个 `*Node` 已不存在 `IllegalStateException`，且本次相关文件通过 `git diff --check`。未修改该在建
  Service，未运行全量测试。

### 54. 章节上下文重复校验收敛

- 将章节上下文完整性校验统一收敛到 `ChapterService` 的 `LOAD_CONTEXT` 节点：数据加载后仅调用一次
  `context.validateReadyForGeneration()`；删除 `PlanChapterNode`、`DraftChapterNode` 内对同一 context 的重复校验。
- 删除 `DraftChapterNode.draft(...)` 内对 plan 的重复空值判断；plan 是否存在仍由该节点的 `apply(...)` 在读取
  `ChapterGraphState` 时负责，保留节点直接输入校验和模型输出校验。
- 在 `ChapterNodeTest` 新增 PLAN、DRAFT 信任 LOAD 已校验上下文的契约用例；同时将 `loadContext(...)` 中误用的
  未定义变量 `state` 修正为方法参数 `chapterGraphState`，解除模块编译阻塞。使用 Maven 定向执行两个新增用例，
  2 个用例通过、七模块 Reactor 构建成功；静态扫描确认 domain 生产代码仅在 `ChapterService` 保留一次
  `validateReadyForGeneration()` 调用，未运行全量测试。

### 55. REVIEW 条件路由与自动改稿回环

- 在 `novel-agent-domain/src/main/java/cn/ninth/novel/domain/chapter/service/workflow` 新增 `ReviewRouter`，读取
  `reviewReport` 和 `reviseRound`，分别返回 PASS、REVISE、HUMAN；自动改稿上限固定为 3 轮，缺少审稿报告时统一
  抛出 `ILLEGAL_PARAMETER`。
- 使用 LangGraph4j 1.8.24 的 `Command(route, update)` 在决定进入 REVISE 的同一刻将 `reviseRound` 加一，保证每次
  路由只计数一次；`ReviseChapterNode` 不承担轮次更新。
- 在 `ChapterService` 注册 REVISE 与 HUMAN 节点，将原有 `REVIEW -> EXTRACT` 直线边替换为条件边，并补充
  `REVISE -> REVIEW` 回环。HUMAN 当前仅记录待人工处理阶段并结束本次图执行，后续接入 checkpointer 时再替换为
  可恢复的中断节点。
- 按用户要求，本次未新增或执行测试，修改尚未经过编译及自动化验证。

### 56. PERSIST 节点与章节结果事务持久化

- 在 `novel-agent-domain/src/main/java/cn/ninth/novel/domain/chapter/adapter/repository` 新增
  `IChapterPersistRepository`，并在 `domain/chapter/service/agent` 新增 `PersistChapterNode`；节点从 Graph State
  读取 `projectCode`、`chapterNumber`、最终 `draft` 和 `extraction`，写库成功后仅追加 `PERSIST` 阶段。
- 在 `novel-agent-infrastructure/src/main/java/cn/ninth/novel/infrastructure/adapter/repository` 新增
  `ChapterPersistRepository`，使用现有 MyBatis DAO 在同一事务中幂等 upsert `story_chapter`、`story_summary`，并推进
  `novel_project.current_chapter_number`；章节标题取当前章节卡，正文字符数按非空白 Unicode code point 计算。
- 将 `ChapterService` 的完成路径从 `EXTRACT -> END` 调整为 `EXTRACT -> PERSIST -> END`；HUMAN 占位路径仍直接结束，
  不会在需要人工处理时提前写入业务数据库。
- 当前 MVP 六表 Schema 没有独立事实表，因此本次持久化正文、摘要和项目进度，`ChapterExtractionVO.facts` 继续保留在
  Graph State，未擅自扩展数据库结构；开发指导已同步明确该边界。
- 按 TDD 先后验证节点和仓储测试在缺少生产类型时编译失败；最终使用 `mvn -pl novel-agent-app -am
  -Dtest=PersistChapterNodeTest,ChapterPersistRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test` 定向验证，
  3 个用例通过、七模块 Reactor 构建成功，真实 MySQL 覆盖首次写入及同章节幂等覆盖。未运行全量测试。

### 57. PERSIST 节点包位置收敛

- 将 `PersistChapterNode` 从 `domain/chapter/service/workflow` 迁移到 `domain/chapter/service/agent`，与 PLAN、DRAFT、
  REVIEW、REVISE、EXTRACT 五个业务节点保持同一目录；`workflow` 继续只保留 State、Key 和路由等编排类型。
- 同步迁移 `PersistChapterNodeTest` 的物理目录和 Java package，并更新 `ChapterService` import 及 PERSIST 实施计划路径。
- 按 TDD 先将测试切换到 agent package，确认因目标类型尚未迁移而编译失败；迁移后使用 `mvn -pl
  novel-agent-app -am -Dtest=PersistChapterNodeTest -Dsurefire.failIfNoSpecifiedTests=false test` 定向验证，2 个用例通过、
  七模块 Reactor 构建成功，未运行全量测试。

### 58. LangGraph4j MySQL Checkpoint 建表脚本

- 在 `docs/sql/langgraph4j-checkpoint.sql` 新增 LangGraph4j 1.8.24 MySQL Checkpoint 建表脚本，包含官方
  `MysqlSaver` 使用的 `LANGRAPH4J_THREAD`、`LANGRAPH4J_CHECKPOINT` 两张表及断点查询索引、级联外键。
- 保留上游组件使用的 `LANGRAPH4J` 表名拼写，并注明脚本需手动执行、应用接入时使用
  `CreateOption.CREATE_NONE`，避免应用启动自动初始化数据库。
- 本次仅新增 SQL 与实施摘要，未连接或修改数据库，按用户要求未运行测试。

### 59. LangGraph4j MySQL Checkpointer 实施计划

- 新增 LangGraph4j MySQL Checkpointer 的 Step 5 可执行计划，基于已核对的
  LangGraph4j 1.8.24 API，将 State 类型编解码、MySQL saver、Spring Bean、图挂载及重启恢复验证拆分为独立任务。
- 计划根据实际依赖修正了旧设计中直接使用官方 MySQL saver 的假设：保留项目 Jackson 3 基线，通过 infrastructure
  自有薄适配器恢复 State 中的领域对象类型；人工决策 HTTP resume 明确保留到下一阶段。
- 本次只新增实施计划和摘要，未修改生产代码，未运行测试。

### 60. LangGraph4j MySQL Checkpointer 与重启续跑

- 在 `novel-agent-domain/src/main/java/cn/ninth/novel/domain/chapter/service/workflow/checkpoint` 定义 checkpoint State
  编解码契约与 `ChapterGraphStateSerializer`，并在 `novel-agent-infrastructure/src/main/java/cn/ninth/novel/infrastructure/checkpoint`
  实现 `ChapterMysqlCheckpointSaver`、`ChapterCheckpointStateCodec`：复用手动创建的
  `LANGRAPH4J_*` 表，通过 Spring JDBC 保存、读取、更新及释放 checkpoint，并使用 Jackson 3 按 State key 恢复
  `ChapterContextAggregate`、`ChapterPlanVO`、`ReviewReportVO`、`ChapterExtractionVO` 等领域类型。
- 在 `novel-agent-app/src/main/java/cn/ninth/novel/config/CheckpointerConfiguration` 装配 saver 与 State serializer；
  `ChapterService` 负责把两者挂载到章节图、为每次生成分配唯一 workflowId，并在图正常结束后释放 active thread。
  MySQL/Jackson 实现保留在 infrastructure，没有把数据库职责下沉到 domain service。
- 图级验证首次暴露 LangGraph4j 默认 Java State serializer 无法克隆未实现 `Serializable` 的领域 VO；改为注入项目
  Jackson 3 State serializer 后解决，未要求整套领域模型实现 Java 序列化。
- 使用真实 dev MySQL 定向执行 `ChapterCheckpointStateCodecTest`、`ChapterMysqlCheckpointSaverTest`、
  `ChapterGraphCheckpointResumeTest`，共 4 个用例通过：覆盖类型 round-trip、缺失 State 拒绝、Saver 重建后读取、
  checkpoint 顺序与释放，以及中断后重建 Saver/CompiledGraph 并从下一节点恢复。未运行全量测试。
- 依赖树确认本次未引入官方 `langgraph4j-mysql-saver`；项目已有 OpenAI Java SDK 间接依赖 Jackson 2.21.4，
  Checkpointer 自身继续使用 Jackson 3.1.4。外部可调用的 workflowId/resume HTTP 契约仍属于下一阶段 HUMAN/HITL。

### 61. Checkpoint 分层边界调整

- 将 LangGraph4j 章节 State 的 `CheckpointStateCodec` 契约和 `ChapterGraphStateSerializer` 放入
  `novel-agent-domain/src/main/java/cn/ninth/novel/domain/chapter/service/workflow/checkpoint`，由 domain 持有章节工作流的
  checkpoint 语义。
- `ChapterCheckpointStateCodec` 作为 Jackson 3 适配器、`ChapterMysqlCheckpointSaver` 作为 JDBC/MySQL 适配器继续位于
  `novel-agent-infrastructure/src/main/java/cn/ninth/novel/infrastructure/checkpoint`，避免 domain 反向依赖 Jackson、Spring JDBC
  或数据库表结构；app 配置改为按 domain 契约装配。
- 先将图恢复测试切换到 domain serializer，确认目标 package 不存在而编译失败；迁移后定向执行
  `ChapterCheckpointStateCodecTest`、`ChapterMysqlCheckpointSaverTest`、`ChapterGraphCheckpointResumeTest`，共 4 个用例通过，
  七模块 Reactor 构建成功，未运行全量测试。

### 62. 一次性人工授权 HITL 实施计划

- 新增一次性人工授权 HITL 的 Step 6 实施计划，明确自动改稿达到上限后在
  HUMAN 节点前中断，同一 workflowId 通过 PASS、REVISE 或 ABORT 恢复。
- 计划采用一次性授权：`humanDecision` 路由后立即消费，人工 REVISE 只追加一轮改稿且不重置自动 `reviseRound`；
  若复审仍有 BLOCKER，则再次暂停等待新的人工作业，避免自动死循环和长期“人工模式”。
- 本段仅完成计划与摘要，尚未修改生产代码或测试，未运行测试。

### 63. HUMAN Checkpoint 一次性人工授权与恢复接口

- 在 domain 增加 `HumanDecisionEnum`、`ChapterWorkflowStatusEnum` 和 `HumanDecisionRouter`，章节图改为在 HUMAN 节点前
  中断；`humanDecision` 消费后清空，人工 REVISE 只增加 `humanReviseRound` 并执行一轮额外改稿，不重置自动
  `reviseRound`。复审仍有 BLOCKER 时再次暂停，PASS 继续抽取和持久化，ABORT 直接结束且不执行 PERSIST。
- `IChapterService` 新增 `resumeChapter(workflowId, decision)`，生成与恢复结果补充 `workflowId` 以及
  `WAITING_HUMAN`、`COMPLETED`、`ABORTED` 状态；`ChapterService` 使用同一单例 `CompiledGraph` 和 MySQL checkpoint
  执行 `GraphInput.resume(...)`，仅在真正到达 END 后释放 active thread。
- 在 API/trigger 增加 `ResumeChapterRequestDTO`，实现 `POST /api/v1/novels/chapters/generate` 和
  `POST /api/v1/novels/chapters/{workflowId}/resume`，响应包含 workflowId/status、当前正文、审稿问题、抽取事实及完成阶段。
  两个接口当前仍无认证，仅适用于本地 dev；上线前必须增加访问控制。
- 按 TDD 分别确认路由类型、领域 resume/status 契约和 HTTP DTO/方法缺失时编译失败，并验证非法人工决策未转换时测试失败；
  最终定向执行 `HumanDecisionRouterTest`、`IChapterServiceTest`、`ChapterHumanCheckpointResumeTest`、
  `NovelChapterControllerTest` 及原 checkpoint codec/saver/重启恢复回归，共 14 个用例通过，七模块 Reactor 构建成功。
  未运行全量测试或真实模型用例。

### 64. 单章节生成 Controller HTTP 验收

- 复用 `novel-agent-trigger/src/main/java/cn/ninth/novel/trigger/http/NovelChapterController` 的
  `POST /api/v1/novels/chapters/generate` 作为首个单章节生成入口；请求使用 `projectId/chapterNumber`，响应通过统一
  `Response` 返回 workflowId、执行状态、正文、审稿问题、抽取事实和完成阶段。
- 将原本使用 `@SpringBootTest` 的 HTTP 用例收敛为 `NovelChapterControllerHttpTest` MVC slice，并显式启用 `test`
  profile；测试提供确定性的 `IChapterService` 实现，只加载 Controller/MVC 契约，不加载 MyBatis、MySQL 或真实模型，
  符合 test profile 不联网、不消耗模型额度的项目规则。
- 首次定向执行确认原用例因 test profile 无数据源而在 ApplicationContext 初始化阶段失败；收敛测试边界后定向执行
  `NovelChapterControllerHttpTest`、`NovelChapterControllerTest`，共 3 个用例通过，七模块 Reactor 构建成功。
  未运行全量测试或真实模型用例。

### 65. 同步多章节顺序生成接口

- 在 `novel-agent-domain` 增加 `IChapterBatchService`、`ChapterBatchService` 和批量结果 VO，按包含首尾边界的章节号范围
  顺序复用现有 `IChapterService.generateChapter`；某章返回 `WAITING_HUMAN` 或 `ABORTED` 时立即停止，避免前章尚未持久化
  就继续生成依赖它的后续章节。请求同时校验项目编码、正数起始章节和合法范围。
- 在 `novel-agent-api` 增加批量请求/响应 DTO，在 `novel-agent-trigger` 增加独立的
  `POST /api/v1/novels/chapters/generate-batch` Controller；响应返回整体状态以及本次实际执行的有序单章结果，并保留每章的
  workflowId、正文、审稿问题、抽取事实和完成阶段。
- 按 TDD 先确认领域测试因批量服务类型缺失而编译失败、HTTP 测试因 DTO/Controller 缺失而编译失败；随后分别定向验证
  `ChapterBatchServiceTest` 3 个用例和 `NovelChapterBatchControllerTest`、`NovelChapterBatchControllerHttpTest` 2 个用例通过。
  最终合并定向验证共 5 个用例通过、七模块 Reactor 构建成功，`git diff --check` 无错误；未运行全量测试或真实模型用例。

### 66. 项目资料管理与章节查询接口

- 在 `novel-agent-domain/src/main/java/cn/ninth/novel/domain/project` 新增项目管理领域模型、`INovelProjectService`、
  `NovelProjectService` 和仓储端口，覆盖项目创建、世界观 upsert、章节卡批量 upsert、人物添加、项目进度与已生成章节查询；
  服务统一校验业务编码和章节号，补齐项目、章节卡、人物的默认值，并拒绝同一批次的重复章节号。
- 在 `novel-agent-infrastructure` 新增 `NovelProjectRepository`，复用现有六表 MyBatis DAO，不新增表或 Mapper SQL；世界观按
  project_id 更新，章节卡按 chapter_number 更新且保持请求顺序，人物按 characterCode 添加，章节正文和项目进度直接查询
  `story_chapter`、`novel_project`。真实 dev MySQL 集成测试覆盖了全部仓储操作及幂等更新。
- 在 `novel-agent-api` 增加项目、世界观、章节卡、人物和章节响应 DTO，在 `novel-agent-trigger` 增加
  `NovelProjectController`，实现六条 `/api/v1/novels/projects` 路由；最小章节卡请求只需 chapterNumber/title/summary，
  其余数据库必需字段由领域服务确定性补齐。
- 按 TDD 依次确认领域类型、MySQL 仓储和 HTTP Controller 缺失时测试编译失败；修复一次仅由 Java 文本块内嵌 JSON
  转义错误导致的 HTTP 测试固件失败后，领域服务 6 个用例、真实 MySQL 仓储 1 个用例、MockMvc 6 个用例分别通过。
  最终合并定向验证共 13 个用例通过、七模块 Reactor 构建成功，`git diff --check` 无错误；首次组合验证的 MySQL 连接被
  沙箱拒绝，授权本机数据库连接后原命令通过。未运行全量测试或真实模型用例。

### 67. 项目资料编辑、正文人工覆盖与指令化改稿

- 在 `novel-agent-domain/src/main/java/cn/ninth/novel/domain/project` 扩展项目服务与仓储契约，增加人物覆盖更新、单章大纲卡
  覆盖更新和已生成正文人工覆盖；领域服务以路径参数作为资源身份，复用原人物/章节卡校验与默认值，并按非空白 Unicode
  code point 重新计算正文 `wordCount`。
- 在 `novel-agent-infrastructure` 的 `NovelProjectRepository` 复用现有 MyBatis DAO 更新已存在记录，缺失目标时拒绝静默新增；
  人物、章节卡和正文更新使用事务并保留原数据库身份与章节关联。正文覆盖后通过 `IStorySummaryDao.markStale` 将旧章节摘要
  标记为 `STALE`，避免后续上下文继续使用与人工正文不一致的摘要。
- 在 `novel-agent-api` 新增三类编辑请求 DTO，并在 `NovelProjectController` 增加
  `POST /{projectCode}/characters/{characterCode}`、`POST /{projectCode}/outlines/{chapterNumber}`、
  `POST /{projectCode}/chapters/{chapterNumber}`；响应分别复用 `StoryCharacterResponseDTO`、`ChapterCardResponseDTO` 和
  `GeneratedChapterResponseDTO`。
- 扩展 `ResumeChapterRequestDTO` 增加可选 `revisionInstruction`，同时保留原单参数构造和两参数领域 resume 契约；
  `ChapterService` 每次恢复都覆盖工作流中的指令值，`ReviseChapterNode` 把非空意见加入“人工修改指令”提示段，并在本轮消费后
  清空。`SystemPrompt` 明确人工意见无需自动审稿证据且优先于自动审稿意见，但仍受章节计划、故事设定和历史事实约束。
- 按 TDD 观察到领域方法缺失、项目编辑路由 404/405、三参数 resume 缺失、提示词优先级缺失及一次性指令映射缺失等预期失败；
  独立代码审查发现并修复了旧指令可能跨 resume 泄漏和人工正文对应摘要未失效两个一致性问题。最终定向执行项目服务、真实
  dev MySQL 仓储、项目/章节 HTTP、Controller 兼容契约、Graph State、HITL 恢复和改稿节点等 10 个测试类，共 41 个用例
  通过，七模块 Reactor 构建成功。未运行全量测试或真实模型用例。

### 68. 用户驱动的长篇分层大纲实施计划

- 面向 500～1000 章项目规划
  `BOOK → VOLUME → STORY_ARC → CHAPTER_CARD` 四层大纲；初始化只生成 Bible、核心人物、全书总纲和分卷骨架，卷细纲及
  章节细纲均由用户通过独立接口显式生成和确认，不设计自动滚动补纲。
- 计划明确规划服务使用独立 `IPlanningModelPort` 直接调用 Spring AI、模型草稿使用 60 分钟内存缓存、确认操作使用 MySQL
  聚合事务；现有章节 LangGraph4j 不负责生成大纲，只在 `CHAPTER_CARD.status = READY` 时允许进入章节生成。
- 根据评审补充 `POST /initialize/reset` 安全重置路径：仅无正文项目允许事务性清空初始化数据并恢复 `DRAFT`；同时把单个
  STORY_ARC 最多 40 章的硬校验前移到卷细纲生成和确认阶段，章节卡生成仅保留防御性复核。
- 本段只完成实施计划和总结文档，未修改生产代码或测试，未运行 Maven 测试。

### 69. 用户驱动的长篇分层大纲与安全重置实现

- 在 `novel-agent-domain/src/main/java/cn/ninth/novel/domain/planning` 新增独立规划领域，使用
  `IPlanningModelPort` 直接结构化调用 Spring AI，初始化依次生成故事圣经、核心人物、BOOK 总纲和 VOLUME 骨架；
  VOLUME 细纲与 STORY_ARC 章节卡只在用户显式调用时生成。草稿由 infrastructure 的进程内缓存按项目隔离，TTL 为
  60 分钟，确认成功后删除，持久化失败时保留以便修正重试。
- 新增 `BOOK → VOLUME → STORY_ARC → CHAPTER_CARD` 持久化树及 7 条规划 HTTP 路由。初始化、卷细纲和章节卡确认分别
  使用 MySQL 事务；分卷和剧情弧章节范围必须连续、完整覆盖且编码/顺序唯一，每个剧情弧在生成和
  `confirmVolumeDetail` 时限制为 1～40 章，单卷据此限制为最多 480 章。确认 token 同时绑定项目和目标卷/剧情弧，且不能
  绕过已确认章节边界。
- 实现 `POST /api/v1/novels/projects/{projectCode}/initialize/reset`：无正文时事务性清空大纲、人物和故事圣经并把项目恢复为
  `DRAFT/currentChapterNumber=0`，成功后清除该项目全部内存草稿；已有 `story_chapter` 时拒绝重置并保持数据库及草稿不变。
- `outline_node` 增加 `planned_start_chapter/planned_end_chapter`，同步更新 PO、DAO、MyBatis Mapper、MVP schema 和
  `docs/sql/migrate-add-outline-chapter-ranges.sql`；迁移已手动应用于本地 dev MySQL，用于真实仓储集成验证。
- 章节生成上下文现在只接受 `CHAPTER_CARD.status=READY`，正文及摘要成功持久化后在同一事务内将卡片改为
  `COMPLETED`，规划流程不进入也不修改现有 LangGraph4j 图。另新增 `PlanningModelPortRealModelIT` 供 dev profile
  人工查看提示词和结构化模型输出，本次没有执行真实模型联调，未消耗模型额度。
- 最终定向执行 `PlanningServiceTest`、`InMemoryPlanningDraftRepositoryTest`、真实 dev MySQL 的
  `PlanningRepositoryTest`、`NovelPlanningControllerHttpTest`、`ChapterContextAggregateTest`、
  `ChapterPersistRepositoryTest` 和 `MvpPersistenceContractTest`，共 23 个用例通过、七模块 Reactor 构建成功；
  未运行全量测试。

### 70. 分层规划完整链路与全结构真实模型集成测试

- 新增 `novel-agent-app/src/test/java/cn/ninth/novel/trigger/http/PlanningFlowIntegrationTest.java`，使用完整 Spring Boot
  上下文、MockMvc、真实 PlanningService/内存草稿缓存/MyBatis 和 dev MySQL，仅把不可控的外部模型端口替换为确定性输出。
  用例串行覆盖项目创建、初始化 4 阶段生成与确认、目标卷剧情弧生成与确认、目标剧情弧 20 张章节卡生成与确认，最终从
  `IDataService` 重新装配第 1 章上下文并通过 `READY` 生成前置校验；另一个用例从 HTTP 确认初始化后执行安全重置，并验证
  Bible、人物、大纲全部删除且项目恢复 `DRAFT/0`。
- 扩展 `PlanningModelPortRealModelIT`，使用 dev profile 真实调用模型并打印 Bible、人物列表、全书总纲、800 章分卷骨架、
  STORY_ARC 和 CHAPTER_CARD 六种结构化返回。六次调用均成功完成 Spring AI 结构化反序列化；该用例只做人工可读联调，
  按项目规则不对模型文本内容添加 Assert。
- 端到端重置测试首次执行发现 Controller 当前契约已经是 `POST /initialize/reset`，而旧测试及文档仍记录
  `DELETE /initialize`，导致 405；已统一 MVC 测试、集成测试、架构文档和实施计划到当前 POST reset 路径。
- 定向执行 `PlanningFlowIntegrationTest` 2 个真实 MySQL 用例和 `NovelPlanningControllerHttpTest` 3 个 MVC 用例通过；
  `PlanningModelPortRealModelIT` 1 个真实模型用例通过。最终合并规划服务、缓存、真实仓储、完整 HTTP 链路、章节
  READY/COMPLETED 闭环及 Mapper 契约等 8 个测试类，共 25 个用例通过、七模块 Reactor 构建成功；未运行项目全量测试。

### 章节工作台与完整章节目录（2026-08-26）

- 将 `novel-agent-web/src/views/ReadView.vue` 改造为参考专业写作软件的章节工作台：左侧为可滚动、可搜索的完整章节目录，
  中间为带章节工具栏、前后章切换和阅读进度的纸张式正文区域，右侧为大纲、规划和生成入口；加载态、空目录、未加载项目及
  窄屏堆叠布局均有独立呈现。章节选择会同步到 `?chapter=` 查询参数，从生成页跳转时可直接定位目标章节。
- 在 `novel-agent-web/src/main.ts` 正式加载 `assets/theme.css`，恢复全局设计变量、Reset 和 Element Plus 覆盖；
  `App.vue` 为章节工作台提供无外边距全高画布，`api/project.ts` 增加一次读取全部已生成章节的客户端方法。
- 在 project Controller/Service/Repository、`IStoryChapterDao` 与 `story_chapter_mapper.xml` 增加按项目查询全部正文的链路，
  `GET /api/v1/novels/projects/{projectCode}/chapters` 返回按 `chapter_number ASC` 排序的现有章节 DTO 列表，不再由前端逐章发请求。
- 按 TDD 先确认新增 MockMvc 用例因列表契约缺失而编译失败，再补齐实现并定向通过；真实 dev MySQL 的
  `NovelProjectRepositoryTest#shouldPersistAndQueryProjectManagementData` 写入两章后验证查询顺序与标题，1 个用例通过。
  前端 `npm run build` 通过；浏览器使用 12 章模拟数据验证全部目录、搜索过滤、章节切换和正文同步，搜索“真相”只保留
  “真相代价”，选择后 URL 与正文均切换到第 11 章。未运行全量测试或真实模型用例。

### 项目入口与作品工作台分离（2026-08-26）

- 将 `novel-agent-web/src/App.vue` 从无条件显示全局侧栏的单一外壳改为两阶段界面：`/project` 只呈现创建/加载项目入口，
  进入作品后才呈现独立的全屏写作工作台顶栏和章节、设定、AI 规划、生成工具导航；章节阅读页不再重复套用旧项目侧栏。
- `ProjectView.vue` 在创建或加载成功后自动进入 `/read`；`router/index.ts` 为全部作品内路由增加项目访问保护，未选择项目时
  返回入口并保留原目标地址；`stores/project.ts` 使用会话存储恢复当前项目，支持工作台刷新，并在切换作品时清理状态。
- 执行前端 `npm run build` 成功，`vue-tsc` 与 Vite 构建均通过；浏览器直接访问未加载的 `/read`，实际跳转到
  `/project?redirect=/read`，页面仅显示创建/加载入口。执行相关文件 `git diff --check` 通过；本次未修改后端及测试代码。

### 规划页章纲术语统一（2026-08-26）

- 将 `novel-agent-web/src/views/PlanningView.vue` 中用户可见的“情节段章节卡/章节卡”统一调整为更符合创作习惯的“章纲”，
  覆盖页签、生成面板、操作按钮和成功提示；底层 `CHAPTER_CARD`、接口字段及数据库结构保持不变。
- 执行前端 `npm run build` 与相关文件 `git diff --check` 验证通过；本次仅调整展示文案，未修改后端或测试代码。

### 章节生成入口与结果查看整合（2026-08-26）

- 重构 `novel-agent-web/src/views/GenerateView.vue`，将原先并列的“单章生成”和“批量生成”合并为一个“章节生成”面板，
  使用“单章/连续章节”分段控件选择范围，共用同一提交入口，避免把同一业务动作拆成两个独立功能区。
- 新增稳定的“生成结果”区域：单章结果展示正文预览、流程阶段、审阅问题、提取事实和人工审核操作，完成后可直接“查看正文”；
  连续章节结果显示总体完成数量与逐章状态，每个已完成章节均可定位到章节工作台，页面同时保留“查看全部章节”入口。
- 执行前端 `npm run build` 成功，`vue-tsc` 和 Vite 构建均通过；执行 `git diff --check` 确认相关文件无空白错误。
  本次复用现有单章、批量生成和章节阅读接口，未修改后端或测试代码。

### 世界观圣经创作编辑器改版（2026-08-26）

- 重构 `novel-agent-web/src/views/SetupView.vue` 的世界观圣经页，将均匀铺开的后台表单改为分层创作编辑器：一句话核心使用
  大字号主编辑区并显示字数，核心主题、主要矛盾和结局方向组成“故事引擎”，世界背景使用独立长文编辑区，叙事声音作为辅助设置。
- 力量体系和世界硬规则收纳进默认折叠的“结构化规则”高级区域，降低 JSON 对主要创作流程的干扰；保存操作移到页头，
  并补充桌面、平板与移动端响应式布局。所有表单字段和保存接口保持不变。
- 执行前端 `npm run build` 成功，`vue-tsc` 与 Vite 构建均通过；执行相关文件 `git diff --check` 验证无空白错误。
  本次未修改后端或测试代码。

### 设定页前端领域适配与表单收敛（2026-08-26）

- 在 `novel-agent-web/src/views/SetupView.vue` 增加面向用户的前端适配：力量层级和世界硬规则改为每行一项的自然文本输入，
  保存时分别序列化为后端需要的对象和数组 JSON；页面不再显示 JSON、字段结构等技术概念。
- 将“章节大纲”收敛为“章纲”创作页，章节号由前端按顺序自动维护，用户只填写标题、本章事件、人物目标、核心阻碍和结尾钩子；
  删除章纲后自动重排章节号，未单独填写的后端字段继续由既有领域服务默认逻辑补齐。
- 将角色页从二十余项 DTO 字段缩减为姓名、角色定位、人物内核、人物小传和表达特征等主要创作输入；角色代号由前端根据姓名
  自动生成，状态等技术字段使用默认值，职业、阵营、关系、底线和秘密收纳为可选补充资料。
- 执行前端 `npm run build` 成功，`vue-tsc` 与 Vite 构建均通过；模板可见文案扫描确认不再暴露 JSON、角色代号、状态等技术字段，
  `git diff --check` 通过。本次保持现有 API DTO 和后端契约不变，未修改后端或测试代码。
### 章节工作台右侧设定面板布局修复（2026-08-26）

- 修复 `novel-agent-web/src/views/ReadView.vue` 顶层 CSS Grid 未声明设定面板列的问题：补齐“目录 / 正文 / 设定 / 工具栏”四列，增加设定面板、设定页签、文本框、提及搜索与保存按钮样式，并在 1280px、960px 和移动端断点下显式收缩布局，避免内容生成隐式列或原生控件样式裸露。
- 新增 `novel-agent-app/src/test/java/cn/ninth/novel/web/ReadViewLayoutContractTest.java`，锁定四列网格、设定面板样式和响应式断点契约。
- `novel-agent-web` 执行 `npm run build` 通过；`git diff --check` 通过。定向 Maven 测试被工作区既有的 `NovelProjectServiceTest` 与 `NovelProjectControllerHttpTest` 测试替身缺少新接口方法的编译错误阻塞，未能执行到新增契约测试。

### 工作台导航收拢至侧边栏（2026-08-26）

- 调整 `novel-agent-web/src/App.vue` 工作台布局，移除顶部章节/设定/规划/生成工具导航，新增常驻窄侧边栏并保留四个入口，其中“设定”直接作为侧边栏项进入 `/setup`。
- 调整 `novel-agent-web/src/views/ReadView.vue`，移除阅读页重复的设定面板和工具栏，阅读区收敛为章节目录与正文两列，保留窄屏响应式布局。
- 新增 `StudioNavigationLayoutContractTest` 并更新阅读布局契约；前端 `npm run build` 通过，`git diff --check` 通过。定向 Maven 测试仍受工作区既有测试替身接口缺失编译错误阻塞，未执行全量测试。

### 章节正文默认编辑与样式统一（2026-08-26）

- 调整 `novel-agent-web/src/views/ReadView.vue`，章节正文改为默认可编辑的统一编辑区，移除“编辑/取消”状态切换，顶部仅保留一个保存按钮。
- 编辑区沿用阅读正文的衬线字体、行距和边框体系，切换章节时自动载入对应内容，保存后通过既有覆写接口更新章节数据。
- 新增 `ReadViewEditingContractTest`；前端 `npm run build` 与 `git diff --check` 通过。Maven 定向测试仍被既有测试替身接口编译错误阻塞。

### 章节编辑画布去卡片化（2026-08-26）

- 参考写作编辑器布局，调整 `novel-agent-web/src/views/ReadView.vue`：移除正文白色卡片、边框、圆角和阴影，编辑区透明铺开在工作区中，标题改为左对齐并保留充足留白。
- 新增 `ReadViewCanvasStyleContractTest` 锁定无边框编辑画布样式；`npm run build` 与 `git diff --check` 均通过。

### 章节右键删除与分卷目录（2026-08-26）

- 新增章节删除链路：`DELETE /api/v1/novels/projects/{projectCode}/chapters/{chapterNumber}`，由 DAO、仓储和领域服务校验项目/章节后删除正文及章节摘要。
- `ReadView.vue` 改为调用已有按卷查询接口展示分卷目录；章节支持右键菜单删除，目录底部增加“新建卷”入口并跳转到现有规划页的卷管理流程。
- 前端 `npm run build`、后端 `mvn -pl novel-agent-app -am -DskipTests compile` 与 `git diff --check` 均通过；未运行全量测试。

### 章节标题直接编辑与保存（2026-08-26）

- 在 `novel-agent-web/src/views/ReadView.vue` 将正文上方的章节标题替换为可直接输入的标题框；切换章节时自动载入标题和正文，点击既有“保存”会一并提交两项内容，并阻止空标题保存。
- 扩展 `OverwriteChapterContentRequestDTO`、`NovelProjectController`、项目领域服务与 `NovelProjectRepository` 的覆写链路，标题、正文和字数在同一次更新中持久化，原有章节状态与摘要过期标记逻辑保持不变；目录和工具栏会使用服务端返回的最新标题即时同步。
- 新增并执行标题持久化、HTTP 契约、真实 MySQL 仓储及阅读页编辑契约的定向测试：`NovelProjectServiceTest`、`NovelProjectRepositoryTest`、`NovelProjectControllerHttpTest`、`ReadViewEditingContractTest` 共 25 个用例通过；`novel-agent-web` 的 `npm run build` 通过。未运行全量测试或真实模型用例。

### 章节右键菜单点击外部关闭（2026-08-26）

- 在 `novel-agent-web/src/views/ReadView.vue` 为章节右键菜单增加页面级 click 监听；点击菜单之外任意位置会清空菜单状态，菜单自身继续使用事件冒泡阻止，因此“删除本章”操作不受影响；组件卸载时移除监听，避免残留事件处理器。
- 扩展 `novel-agent-app/src/test/java/cn/ninth/novel/web/ReadViewEditingContractTest.java` 锁定页面监听注册、卸载和菜单内阻止冒泡的契约。定向 Maven 测试 3 个用例通过，`novel-agent-web` 的 `npm run build` 通过；未运行全量测试。

### 小说创作信息架构前端实施方案（2026-08-26）

- 将“创作 / 设定 / 角色 / 资料”的目标结构映射到现有 Vue 路由、页面、API 和测试目录，并拆分为可独立交付的 P0/P1 前端改造与后续资料库后端能力建设。
- 方案核对了现有世界观、角色、分层大纲和章节接口可直接支持的页面，明确审稿、候选事实、已确认事实、时间线和伏笔尚缺可查询、可确认的持久化 API，第一阶段只能提供诚实的空状态，不能伪装为已实现功能。
- 本次仅新增设计文档；已执行 `git diff --check` 通过，未修改业务代码、未执行构建或测试。

### 阅读页左右侧栏布局（2026-08-26）

- 调整 `novel-agent-web/src/App.vue` 与 `ReadView.vue`：阅读页保留全局工具侧栏在最左侧，将章节目录移到正文右侧，
  形成“工具侧栏 / 正文编辑区 / 章节目录”三列布局；1280px 与 960px 断点同步收窄右侧目录，移动端仍回落为纵向布局。
- 扩展 `StudioNavigationLayoutContractTest` 与 `ReadViewLayoutContractTest`，锁定阅读模式保留工具侧栏、正文占第一网格列、
  章节目录占第二网格列的契约。轻量契约检查按 TDD 先失败后通过，`novel-agent-web` 执行 `npm run build` 成功；浏览器
  在 1280px 宽度实测工具侧栏、正文、章节目录的横向范围分别为 `0–76`、`76–1030`、`1030–1280`，顺序正确。
- Maven 定向测试受工作区既有 `NovelProjectServiceTest`、`NovelProjectControllerHttpTest` 测试替身未实现新增接口方法的
  编译错误阻塞，未运行到目标用例；未修改这些无关代码，也未运行全量测试。

### 设定、角色与大纲拆分为左栏入口（2026-08-26）

- 调整 `novel-agent-web/src/App.vue`，将原“设定”入口拆成左侧工具栏中的“设定 / 角色 / 大纲”三个独立入口，
  保留章节、AI 规划和生成入口；三个入口分别使用独立图标与路由高亮。
- 在 `router/index.ts` 增加 `/characters` 和 `/outline` 受项目访问保护的路由；`SetupView.vue` 根据 `/setup`、
  `/characters`、`/outline` 分别展示世界观、角色和章纲内容，并隐藏原页面顶部重复页签，既有表单状态与保存接口保持不变。
- 扩展 `StudioNavigationLayoutContractTest`，轻量导航契约按 TDD 先失败后通过；`novel-agent-web` 执行
  `npm run build` 成功。浏览器逐项点击确认 URL 分别切换到三个独立路由，对应显示“世界观圣经 / 创建角色 / 章纲”，
  且顶部旧页签可见数量为 0。Maven 定向测试仍受工作区既有两个测试替身缺少 `findCharacters/listCharacters`
  实现的编译错误阻塞，未运行到目标用例；未运行全量测试。

### 大纲目录化与后台生成收敛（2026-08-26）

- 新增 `novel-agent-web/src/views/OutlineView.vue`，将原来要求用户逐章填写标题、事件、目标、冲突和钩子的章纲表单，
  改为参考专业写作工具的“左侧目录 / 右侧详情”大纲工作台；目录明确区分总纲、分卷、情节段和章纲，支持搜索、选择和刷新，
  详情只读展示摘要、人物目标、核心冲突、预期兑现与结尾承接。
- 用户只在项目尚未初始化时输入一段高层故事概念；总纲与分卷骨架、分卷细纲、情节段章纲均通过既有 AI 规划接口在后台
  生成并直接确认，不再向用户展示节点代号、结构化字段或 JSON。左侧栏移除独立“AI 规划”入口，`/planning` 保留为内部兼容路由。
- 新增 `GET /api/v1/novels/projects/{projectCode}/outlines/tree`，通过 `IPlanningService`、`IPlanningRepository` 和真实
  `outline_node` 查询返回 BOOK、VOLUME、STORY_ARC、CHAPTER_CARD 四层节点及父节点业务编码；新增
  `OutlineNodeResponseDTO` 和前端 `OutlineNode` 类型。`/outline` 改由独立 `OutlineView` 承载，`SetupView` 删除旧章纲手填页。
- 新增 `OutlineViewContractTest`，并扩展 `StudioNavigationLayoutContractTest`、`PlanningRepositoryTest`；轻量契约按 TDD
  先失败后通过，前端 `npm run build` 与后端 `mvn -pl novel-agent-app -am -DskipTests compile` 均成功。浏览器确认总纲、
  分卷、章纲三个目录分组均可见，原“人物想要什么”等手填字段和 JSON 均为 0 个；当前常驻旧后端进程未加载新接口，页面联调
  返回 405，需重启 dev 后端后读取真实大纲数据。Maven 测试编译仍受工作区既有两个项目服务测试替身缺少人物查询方法阻塞，
  未运行全量测试。
### 世界观圣经生成结果回填与编辑闭环（2026-08-26）

- 完善 `novel-agent-web/src/views/SetupView.vue`：进入设定页或切换当前作品时调用已有世界观查询接口，将 AI 初始化确认后持久化的世界观自动回填到可编辑表单；加载完成前禁用保存，避免用空表单误覆盖已有内容，保存成功后按服务端返回值刷新表单。
- 新增 `novel-agent-web/src/utils/storyBible.ts`，集中处理世界观接口数据与编辑表单之间的转换；力量层级和世界硬规则支持标准 JSON 转逐行文本，也保留旧版纯文本或异常 JSON 原值供用户修正。
- 新增 `novel-agent-app/src/test/java/cn/ninth/novel/web/StoryBibleEditingContractTest.java` 约束查询回填和结构化规则映射。`npm run build` 成功，Node 定向映射检查覆盖标准 JSON、空记录和旧版纯文本并通过；Maven 定向测试受工作区已有测试桩未实现新接口方法的编译错误阻断，尚未执行到该契约测试。

### 总纲与章纲统一可编辑工作台（2026-08-26）

- 调整 `novel-agent-web/src/views/OutlineView.vue`，用户可见目录只保留“总纲”和“章纲”，不再展示分卷与情节段；底层 BOOK/VOLUME/STORY_ARC/CHAPTER_CARD 层级仍完整保留，供 AI 规划和章节生成上下文使用。
- 总纲和章纲详情改为直接编辑标题、内容概述、人物目标、核心冲突、预期兑现及结尾承接；章纲复用已有单章更新接口，总纲新增 `POST /api/v1/novels/projects/{projectCode}/outlines/book`，更新时保留 BOOK 节点主键、层级、范围、排序和状态。
- 大纲页新增统一“AI 生成章纲”动作：前端在后台依次补齐尚未生成的分段细纲与章纲，最终只向用户展示可编辑的章纲列表；已有分段或章纲会跳过，避免重复确认。
- 按 TDD 先确认页面与总纲更新契约失败，再补齐实现；定向执行 `PlanningServiceTest`、`NovelPlanningControllerHttpTest`、`PlanningRepositoryTest` 和 `OutlineViewContractTest` 共 15 个用例通过，其中总纲仓储测试使用真实 dev MySQL 隔离数据并完成清理。`novel-agent-web` 执行 `npm run build` 成功，浏览器确认空状态目录仅显示总纲/章纲且布局无重叠；未调用真实模型，未运行全量测试。

### 数据库初始化脚本收敛（2026-08-26）

- 将 `docs/sql` 收敛为两个 SQL 入口：`schema.sql` 统一负责创建 `novel_agent` 数据库、六张业务表和两张 LangGraph4j Checkpoint 表；新增 `seed.sql`，以可重复执行且不覆盖已有编辑的方式写入 `demo-story` 演示项目、世界观和男主数据。
- 删除独立 Checkpoint 建表脚本及两份已被当前 schema 吸收或淘汰的历史迁移脚本，并同步更新 `README.md`、`docs/database-design.md`、Checkpoint Saver 注释和 `MvpPersistenceContractTest`；契约测试新增“仅存在两个 SQL 文件”、完整表集合、建库语句和 seed 入口校验。
- 定向执行 `mvn --% -pl novel-agent-app -am -Dtest=MvpPersistenceContractTest -Dsurefire.failIfNoSpecifiedTests=false test`，2 个用例全部通过；未运行全量测试，未对本地 dev 数据库执行建库或 seed，避免覆盖现有联调数据。

### 世界观设定分区编辑与 JSON 隐藏（2026-08-26）

- 重构 `novel-agent-web/src/views/SetupView.vue` 的世界观圣经编辑器，将原来纵向堆叠的所有大文本框改为“故事核心 / 世界底稿 / 力量与规则 / 叙事声音”左侧分区导航，右侧一次只编辑一个主题，并显示各分区完成状态；桌面端保持双栏工作台，移动端自动切换为顶部两列导航。
- 调整 `novel-agent-web/src/utils/storyBible.ts`，前端不再展示或要求用户填写 JSON：力量体系拆为体系名称、能力机制、使用代价和可增删的成长层级，世界硬规则改为可增删条目；加载时兼容既有 `name/cost/ranks/levels` JSON、规则数组及历史纯文本，保存时仍转换为后端现有 JSON 字段，接口与数据库契约不变。
- 更新 `StoryBibleEditingContractTest`，锁定分区导航、普通表单字段、列表编辑以及不绑定原始 JSON 的契约。`npm run build` 成功，定向执行 `StoryBibleEditingContractTest` 1 个用例通过；浏览器加载 `demo-story` 验证旧 JSON 已分别显示为“残页刻印”和代价文本，四分区切换正常，1280px 与 390px 视口均无横向溢出，控制台无警告或错误；未运行全量测试。

### 世界观圣经编辑画布化（2026-08-27）

- 在 `AGENTS.md` 新增创作编辑界面规则：小说设定、章纲和正文等连续写作场景使用聚焦编辑画布；名称字段采用低边界内联编辑，长文本单栏全宽，结构化规则采用可增删的紧凑条目，并以分区或逐步引导限制同时出现的输入量。
- 重构 `novel-agent-web/src/views/SetupView.vue` 的世界观圣经编辑面：保留既有四分区、所有字段绑定、加载与保存接口，将故事核心、世界底稿、力量与规则、叙事声音改为轻量的画布式编辑层级；体系名称改为标题式输入，长文改为纵向全宽输入，成长层级和硬规则仍以独立可增删条目维护。桌面、窄屏与移动端样式保持弹性布局，移动端结构化条目自动单栏。
- 更新 `novel-agent-app/src/test/java/cn/ninth/novel/web/StoryBibleEditingContractTest.java`，先验证新增画布钩子缺失而失败，再验证画布结构、现有数据回填和 JSON 隐藏契约。定向 Maven 测试 1 个用例通过；`novel-agent-web` 的 `npm run build` 通过（仅有既有的产物体积提示）。本地浏览器自动化未返回快照，未将其作为视觉验收依据；未运行全量测试。

### 设定工作台全宽自适应规则（2026-08-26）

- 调整 `novel-agent-web/src/views/SetupView.vue`，移除世界观和角色编辑器的 `1080px` 最大宽度，主容器改为占满可用空间并设置 `min-width: 0`；世界观分区导航使用 `clamp()` 弹性列宽与弹性间距，不再依赖固定 `220px` 主布局列。
- 在根目录 `AGENTS.md` 新增“前端响应式布局”规则：工作台主容器默认全宽、主栏不得使用固定像素宽度或固定最大宽度、多栏使用 `minmax()`/`clamp()`/弹性比例，并要求至少验证常规桌面、窄屏桌面和移动端且不得出现横向滚动。
- 扩展 `StoryBibleEditingContractTest` 锁定全宽容器和弹性分区列；`npm run build` 成功，定向执行该契约测试 1 个用例通过。浏览器在 1600px、1024px、390px 视口下实测编辑器宽度分别为 1463px、887px、297px，均随主内容区伸缩且 `body.scrollWidth` 等于视口宽度，控制台无警告或错误；未运行全量测试。

### 角色入口改为角色库（2026-08-26）

- 调整 `novel-agent-web/src/views/SetupView.vue`，将 `/characters` 默认页面从整页创建表单改为已有角色库：接入角色查询、显示角色总数与搜索框，并以自适应卡片网格展示姓名、定位、年龄、性格、背景、生存状态及稳定编码；无角色和无搜索结果时分别提供明确空状态。
- 在 `novel-agent-web/src/api/project.ts` 补充前端 `listCharacters` 查询方法；原创建表单移入右上角“新建角色”按钮触发的抽屉，创建成功后自动重新查询角色库并关闭抽屉，现有后端 GET/POST 接口及数据结构不变。
- 新增 `CharacterLibraryContractTest`，锁定“默认列出现有角色 / 创建功能由按钮触发 / 创建后刷新列表”的契约。`npm run build` 成功，定向执行该测试 1 个用例通过；浏览器加载 `demo-story` 后默认展示林渊角色卡，点击按钮可打开新建角色抽屉，1600px、1024px、390px 视口下角色库宽度分别为 1468px、887px、297px且均无横向滚动，控制台无警告或错误；未新增角色数据，未运行全量测试。

### 角色库编辑入口与纵向表单（2026-08-26）

- 调整 `novel-agent-web/src/views/SetupView.vue`：角色卡整体改为可点击的编辑入口，点击后复用角色抽屉并回填已有资料；保存时调用角色更新接口，创建与编辑完成后都重新加载角色库。
- 搜索框改用 Element Plus 带前缀图标、清空按钮和焦点反馈的输入组件；角色新建/编辑抽屉改为顶部标签的单列纵向表单，姓名、定位、性别、年龄及各段人物资料按阅读顺序向下排列，抽屉宽度在桌面端限制为 640px、移动端随视口收缩且不产生横向滚动。
- 扩展 `novel-agent-app/src/test/java/cn/ninth/novel/web/CharacterLibraryContractTest.java`，锁定组件化搜索、角色卡编辑入口、纵向表单和更新接口契约。`npm run build` 成功，定向执行 `CharacterLibraryContractTest` 1 个用例通过；浏览器验证搜索过滤及清空恢复、角色编辑资料回填和 390px 移动端布局均正常，未保存或新增联调数据，未运行全量测试。

### 人工审核、事实记忆与 MAJOR 返修闭环（2026-08-27）

- 统一 `novel-agent-web` 与章节领域的人工审核契约，前端改为发送后端实际支持的 `PASS / REVISE / ABORT`，并保留可选人工修改指令字段。
- 调整 `ReviewReportVO` 与 `ReviewRouter`：`BLOCKER` 和 `MAJOR` 均进入自动返修，达到三轮上限后转人工检查；仅有 `MINOR` 时直接进入事实抽取。
- 在 `novel-agent-domain` 的 EXTRACT 节点向模型提供稳定人物编码，要求人物状态事实以 `characterCode` 为主体；在 `novel-agent-infrastructure` 新增 `story_fact` DAO、PO 与 Mapper，章节持久化事务会幂等替换来源事实，并将人物状态合并回 `story_character.current_state_json / life_status`。`docs/sql/schema.sql` 与数据库设计同步扩展为七张业务表，删除章节时先清理其事实。
- 定向执行 `ChapterNodeTest`、`ReviewReportVOTest`、`ReviewRouterTest`、`HumanDecisionRouterTest`、`NovelChapterControllerHttpTest`、`MvpPersistenceContractTest` 共 27 个用例通过，后端 compile 与前端 `npm run build` 通过。真实 MySQL `ChapterPersistRepositoryTest` 已补充事实及人物状态断言，但本机 `127.0.0.1:13306` 未启动，未执行该集成用例；未运行全量测试或真实模型用例。

### 现有问题与代码改进清单（2026-08-27）

- 新增 `docs/现有问题与代码改进清单.md`，用中文记录本轮修复之后仍存在的数据一致性、事实上下文、人物筛选、章节重跑、规划校验、同步任务和模型调用可观测性问题，并逐项标注优先级、对应代码、建议方案与验收标准。
- 文档明确“角色”指小说人物，说明当前六种人物定位数量不是主要问题，真正的设计债是性别与叙事定位混在同一字段；同时给出后续推荐实施顺序。
- 本次仅新增和更新文档；执行 `git diff --check` 验证格式，未修改业务代码、未运行测试或构建。

### 角色状态事实类型过滤（2026-08-27）

- 修复 `ChapterPersistRepository.updateCharacterStates`：仅将 `CHARACTER_STATE` 事实投影到 `story_character.current_state_json`，`LOCATION` 等其他事实仍完整持久化到 `story_fact`。
- 在 `ChapterPersistRepositoryTest` 增加真实 MySQL 回归用例，覆盖同一 `characterCode` 的非人物事实不会污染角色运行时状态。
- 定向执行 `mvn -pl novel-agent-app -am -Dtest=ChapterPersistRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test`，2 个用例全部通过。

### lifeStatus 白名单边界校验（2026-08-27）

- 在 `ChapterPersistRepository` 持久化入口增加与 `factType` 无关的 `lifeStatus` 白名单校验，仅允许 `ALIVE`、`DEAD`、`MISSING`、`UNKNOWN`，非法值在写入章节事实前拒绝并回滚事务。
- 增加真实 MySQL 回归测试，覆盖非 `CHARACTER_STATE` 事实携带非法 `lifeStatus` 时不会写入 `story_fact` 或更新角色生存状态。
- 定向执行 `mvn -pl novel-agent-app -am -Dtest=ChapterPersistRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test`，验证用例先失败后通过。

### 章节模型调用有界重试（2026-08-27）

- 新增 `novel-agent-domain/src/main/java/cn/ninth/novel/domain/chapter/service/agent/ChapterModelRetryExecutor.java`，仅对章节模型端口统一转换的 `E0001` 边界失败执行 workflow 级最多三次额外重试；参数、输出校验、路由和持久化异常均直接失败，不触发重试。
- PLAN、DRAFT、REVIEW、REVISE、EXTRACT 节点均接入该执行器，共享当前 workflow 的 `retryCount` 预算，并把成功节点实际消耗的重试次数累加写回状态；PERSIST 不写入或重试该状态，避免重复数据库写入。
- 新增 `ChapterModelRetryExecutorTest`，扩展 `ChapterNodeTest` 覆盖文本与结构化模型调用在瞬态失败后恢复、三次上限及非瞬态错误不重试。定向执行 `ChapterModelRetryExecutorTest`、`ChapterNodeTest`、`ChapterGraphStateTest` 共 21 个用例通过。

### 人工返修上限前端闭环（2026-08-27）

- `ChapterService` 依据 checkpoint 中的 `humanReviseRound` 与 `HumanDecisionRouter.MAX_HUMAN_REVISE_ROUND` 计算 `canHumanRevise`，并通过 `ChapterGenerationResultVO`、`GenerateChapterResponseDTO` 及单章/批量 Controller 响应传递给前端；完成、终止及人工返修额度耗尽的流程均返回 `false`。
- `novel-agent-web/src/views/GenerateView.vue` 仅在 `canHumanRevise` 为真时展示“驳回重写”；HTTP 客户端保留业务错误码，遭遇后端 `E0008` 时同步将当前页面标记为不可返修，处理旧页面或并发提交。
- 先验证 `NovelChapterControllerHttpTest` 因响应缺少 `canHumanRevise` 而失败，补齐实现后定向执行 `mvn -pl novel-agent-app -am -Dtest=NovelChapterControllerHttpTest -Dsurefire.failIfNoSpecifiedTests=false test`，2 个用例通过；`novel-agent-web` 执行 `npm run build` 通过（仅有既有的产物体积提示）。

### 人工返修修改意见输入（2026-08-27）

- 在 `novel-agent-web/src/views/GenerateView.vue` 的人工审核区新增“补充修改意见（可选）”多行输入框，仅在允许人工返修时展示；输入为空时保留“按审阅问题重写”的既有语义，输入非空时随 `REVISE` 决策传入已有的 `revisionInstruction` 后端链路，成功恢复后清空输入。
- 新增 `novel-agent-app/src/test/java/cn/ninth/novel/web/GenerateViewHumanRevisionInstructionContractTest.java`，锁定输入控件、空值说明、请求字段和清理行为的前端页面契约。
- 定向测试先因缺少输入控件失败，修复后执行 `mvn -pl novel-agent-app -am -Dtest=GenerateViewHumanRevisionInstructionContractTest -Dsurefire.failIfNoSpecifiedTests=false test` 通过；`novel-agent-web` 执行 `npm run build` 通过（仅有产物体积提示）。本地页面在 1280px、900px、390px 宽度下无横向溢出；未在浏览器创建项目或触发真实生成，以避免写入业务数据及调用模型。

### 人工返修额度耗尽提示（2026-08-27）

- 调整 `novel-agent-web/src/views/GenerateView.vue`：当 `canHumanRevise` 为 `false` 时，不仅隐藏“驳回重写”及修改意见输入框，还在原位置展示“人工返修机会已用完，当前章节只能选择通过或放弃”的明确提示，避免用户把预期的单次上限误判为页面故障。
- 扩展 `GenerateViewHumanRevisionInstructionContractTest`，先验证额度耗尽提示缺失而失败，再验证其条件渲染和文案存在。
- 定向执行 `mvn -pl novel-agent-app -am -Dtest=GenerateViewHumanRevisionInstructionContractTest -Dsurefire.failIfNoSpecifiedTests=false test`，1 个用例通过；`novel-agent-web` 执行 `npm run build` 通过（仅有产物体积提示）。

### 规划编号改由服务端生成（2026-08-27）

- 在 `novel-agent-domain/src/main/java/cn/ninth/novel/domain/planning/model/valobj` 新增仅供模型结构化输出的内容草稿类型，并将人物、总纲、分卷、剧情弧和章节卡的模型返回契约移除稳定编码、排序号、章节范围、章节号及状态字段；`ChatClient.entity(...)` 仍按这些精简类型反序列化。
- `PlanningService` 依据模型返回顺序服务端生成 `CHAR_001`、`BOOK_001`、`VOL_001`、`ARC_001` 和连续章节号；分卷与剧情弧按返回条目数均分目标范围，章节卡按目标剧情弧的起始章节连续编号。已确认草稿的完整 VO 校验继续保留。
- 更新 Planning system prompt、真实模型打印用例和规划集成测试桩，明确模型不得输出编号类字段。`PlanningServiceTest` 先验证缺少 `characterCode` 被旧实现拒绝，再验证服务端生成连续编号；定向执行 `mvn -pl novel-agent-app -am -Dtest=PlanningFlowIntegrationTest,PlanningServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`，7 个用例通过（`PlanningFlowIntegrationTest` 使用隔离并清理的 dev MySQL 数据）。

### 规划模型提示词改为正向返回示例（2026-08-27）

- 重写 `PlanningPrompts` 的故事圣经、人物、全书总纲、分卷、剧情弧和章节细纲六组 system prompt，每组直接给出与当前结构化返回类型一致的 JSON 结构示例，并用正向任务描述替代字段排除清单。
- 同步精简 `PlanningModelPortRealModelIT` 的联调 user prompt，改为直接说明人物、分卷、剧情弧和章节细纲的正向生成目标；未调用真实模型。
- 新增 `novel-agent-app/src/test/java/cn/ninth/novel/domain/planning/service/prompt/PlanningPromptsTest.java`，先验证六组提示词缺少 JSON 示例而失败，再定向执行 `mvn -pl novel-agent-app -am -Dtest=PlanningPromptsTest -Dsurefire.failIfNoSpecifiedTests=false test`，1 个用例通过。

### Review 报告边界校验与审稿上下文补全（2026-08-27）

- 在 `ReviewChapterNode` 增加结构化审稿报告边界校验：问题列表必须返回数组，每条问题必须具备 `severity`、`category`、`description` 和 `evidence`，且 `evidence` 必须能在当前待审正文中精确定位；非法报告以 `E0004` 失败，不再被当作无问题通过。
- Review user prompt 补充力量体系、世界背景和项目单章目标字数，与 Draft 节点使用的世界设定约束对齐；`REVIEW_SYSTEM_PROMPT` 同步要求 `evidence` 直接复制最短连续正文原文，真实模型用例补齐对应示例上下文，未调用真实模型。
- `ChapterNodeTest` 先验证缺少问题数组、缺失必填字段、证据无法定位、Review 上下文不完整及 system prompt 未约束原文证据而失败，补齐实现后定向执行 6 个 Review 相关方法，全部通过。未执行真实模型用例或全量测试。

### 故事圣经模型输出取消嵌套 JSON（2026-08-27）

- 调整 `PlanningPrompts.BIBLE_SYSTEM` 与故事圣经模型草稿类型：力量体系改为原生 `powerSystem` 对象，硬规则改为原生 `hardRules` 数组，不再要求模型生成 `powerSystemJson`、`hardRulesJson` 两个转义 JSON 字符串。
- 新增 `PowerSystemDraftVO`，并在 `PlanningService` 的模型输出边界使用 Jackson 将对象和数组统一序列化为现有 `StoryBibleVO` 的 JSON 字符串字段；数据库、HTTP API 和前端编辑契约保持兼容，无需迁移已有数据。
- `PlanningPromptsTest` 先因旧提示词仍包含嵌套 JSON 而失败；更新 `PlanningServiceTest` 后定向执行 `PlanningPromptsTest,PlanningServiceTest`，共 6 个用例通过，覆盖模型返回类型和对象/数组到存储字段的转换。未调用真实模型，未运行全量测试。

### 规划提示词与整体流程复查（2026-08-27）

- 复查确认故事圣经提示词、`StoryBibleDraftVO` 和 `PlanningService.normalizeBible` 的对象/数组契约一致。
- 定向执行 `PlanningFlowIntegrationTest` 发现现有集成测试桩和 `PlanningModelPortRealModelIT` 仍请求旧的 `StoryBibleVO`，当前实现会在初始化首个模型调用处失败；同时记录人物数、建议卷数及人工确认字段校验的逻辑缺口，待后续修复。

### 开发规则补充不过度设计原则（2026-08-27）

- 在 `AGENTS.md` 新增“设计原则”，要求以当前明确需求为边界，优先采用简单、直接且易维护的实现，避免为假设场景预先增加抽象层、扩展点或复杂架构。
- 本次仅修改规则与工作总结文档，通过 `git diff --check` 完成格式检查，未运行测试。

### 开发说明保持聚焦（2026-08-27）

- 在 `AGENTS.md` 的“设计原则”中补充说明规则：不对未做事项作过度解释，不再解释已经决定删除的内容，说明聚焦实际采用的方案与结果。
- 本次仅修改规则与工作总结文档，通过 `git diff --check` 完成格式检查，未运行测试。

### 计划统一使用中文（2026-08-27）

- 在 `AGENTS.md` 的“设计原则”中补充语言规则，要求计划、任务拆解和实施步骤统一使用中文。
- 本次仅修改规则与工作总结文档，通过 `git diff --check` 完成格式检查，未运行测试。

### 测试过程与结果输出规则（2026-08-27）

- 在 `AGENTS.md` 的“测试验证”中补充测试编写规则，要求打印关键执行过程和测试结果，不能只有简单断言，同时保留断言用于自动判定。
- 本次仅修改规则与工作总结文档，通过 `git diff --check` 完成格式检查，未运行测试。

### 大纲树与章节计划重构实施计划（2026-08-27）

- 确定大纲采用统一递归树与 `BOOK/STAGE/VOLUME/ARC` 轻量类型，章节计划从 `outline_node` 拆分为独立 `chapter_plan`，正文改为关联章节计划。
- 计划核对并覆盖 `docs/sql`、领域模型、MyBatis Mapper、Repository、HTTP API、章节工作流必要接线、前端大纲工作台和现有定向测试，按三表契约、持久化、规划服务、HTTP、章节接线、前端和清理七个阶段实施。
- 本次只新增实施计划并更新工作总结，未修改业务代码、数据库或测试，未运行构建和测试。

### 旧规划与架构文档清理（2026-08-27）

- 清理已被当前大纲树方案取代的旧规划与架构文档，将有效设计说明收敛到当前工作流、数据库设计和实现总结。
- 更新 `README.md` 和当前文档引用；`docs/implementation-summary.md` 继续按项目规则记录已经完成的工作，不作为架构方案。
- 本次只清理和更新文档，未修改业务代码、数据库或测试，未运行构建和测试。

### 大纲节点灵活层级规则确认（2026-08-27）

- 更新大纲树重构规则：保留 `node_kind` 用于展示、卷级阅读和统计，但取消 `STAGE → VOLUME → ARC` 固定链路，允许 `BOOK → VOLUME`、`BOOK → ARC`、`STAGE → ARC` 等跳层结构。
- 明确 `BOOK` 项目内唯一且必须为根节点，`ARC` 不允许创建大纲子节点；章节计划的生成条件统一为“目标节点有完整章节范围且没有子节点”，不再绑定 `ARC` 类型。
- 本次只更新实施计划和工作总结，未修改业务代码、数据库或测试，未运行构建和测试。

### 大纲与章节计划写入约束确认（2026-08-27）

- 更新当前大纲重构计划，明确已生成正文的章节计划禁止覆盖、编辑或删除；所有正文必须从 `READY` 章节计划生成，并通过非空 `chapter_plan_id` 保持项目及章节号一致关联。
- 明确大纲节点存在子节点或章节计划后锁定章节范围，本期只允许同一父节点内排序，不支持跨父节点移动；`nodeKind` 仅作产品语义，前端推荐下一层但后端不建立固定转换状态机。
- 本次只更新实施计划和工作总结，未修改业务代码、数据库或测试，未运行构建和测试。

### PlanningRepository 通用大纲树访问（2026-08-27）

- 将 `PlanningRepository` 收敛为项目上下文下的通用 `OutlineNode` 树访问，支持查找根节点、查询全部节点和子节点、保存单节点及子节点、更新、删除和计算同级下一顺序号。
- 清理 Repository 中按 `BOOK`、`VOLUME`、`STORY_ARC`、`CHAPTER_CARD` 区分的初始化、卷细纲和章节卡持久化流程；`IPlanningRepository` 保留过渡兼容声明，未改 `PlanningService`。
- 重写 `novel-agent-app/src/test/java/cn/ninth/novel/infrastructure/adapter/repository/PlanningRepositoryTest.java`，使用真实 MySQL 验证根节点、子节点顺序、更新和叶节点删除；`MvpPersistenceContractTest` 及定向 Repository 测试通过。

### 章节上下文迁移到 chapter_plan（2026-08-28）

- 将 `ContextRepository` 的当前章节读取改为通过 `IChapterPlanDao` 按项目和章节号查询 `chapter_plan`，并将章节号、标题、摘要和状态接入现有章节上下文对象；`ChapterContextLoader` 与 LangGraph 工作流保持不变。
- 将 `ContextRepositoryTest` 的真实 MySQL 夹具改为 `BOOK → ARC → chapter_plan`，正文使用 `chapter_plan_id` 关联，补充上下文装载结果日志和断言。
- 定向执行 `mvn --% -pl novel-agent-app -am -Dtest=ContextRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test`，1 个用例通过。

### 正文持久化迁移到 chapter_plan（2026-08-28）

- 将 `ChapterPersistRepository.persist` 改为加载 `ChapterPlanPO`，正文写入 `chapter_plan_id`，正文及派生数据完成后在同一 `@Transactional` 事务中把计划状态更新为 `COMPLETED`；状态更新影响行数异常会触发回滚。
- 将 `ChapterPersistRepositoryTest` 的真实 MySQL 夹具迁移到 `BOOK → ARC → chapter_plan`，验证正文计划关联、成功状态推进，以及后续持久化失败时正文/摘要回滚且计划保持 `READY`。
- 定向执行 `mvn --% -pl novel-agent-app -am -Dtest=ChapterPersistRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test`，4 个用例通过。

### 按卷阅读查询迁移到大纲范围（2026-08-28）

- 将 `NovelProjectRepository.findChaptersByVolume` 的卷识别改为 `node_kind = VOLUME`，并使用 `start_chapter/end_chapter` 将正文按章节范围分组；其他项目仓储方法未改动。
- 新增真实 MySQL 定向测试 `NovelProjectRepositoryVolumeTest`，验证两卷章节按范围归组并输出关键结果。
- 定向执行 `mvn --% -pl novel-agent-app -am -Dtest=NovelProjectRepositoryVolumeTest -Dsurefire.failIfNoSpecifiedTests=false test`，1 个用例通过。

### 大纲工作台节点显示与编辑框样式修复（2026-08-28）

- 调整 `novel-agent-web/src/views/OutlineView.vue`，让 Element Plus 按钮生成的内部插槽容器占满树节点宽度，恢复固定大纲节点的标题、类型和摘要显示。
- 清除“大纲内容”文本域由 Element Plus 阴影和焦点轮廓形成的可见框线，保留聚焦写作画布样式。
- 使用真实浏览器完成修复前后回归检查，并验证常规桌面、窄屏桌面和移动端均无横向滚动且节点内容可见；`npm run build` 构建通过。

### 实现前确定必要设计细节（2026-08-29）

- 在 `AGENTS.md` 的“设计原则”中补充规则，要求实现前先确定当前需求所必需的设计细节，影响实现结果的事项未确定时先确认，不自行扩大设计范围。
- 本次仅修改规则与工作总结文档，通过 `git diff --check` 完成格式检查，未运行测试。
### Task 43：Story Bible Draft 生成与确认后端链路（2026-08-29）

- 在 `novel-agent-domain/src/main/java/cn/ninth/novel/domain/project/model/valobj` 新增 `StoryBibleDraftVO` 与 `PowerSystemDraftVO`，模型输出仅包含创作内容，不包含状态、项目编码或数据库字段；新增 `PlanningPrompts.BIBLE_SYSTEM`，明确世界边界、创作方向、正文风格和不生成角色的职责。
- 扩展规划服务与仓储，新增 `POST /api/v1/novels/projects/{projectCode}/bible/generate` 和 `POST /api/v1/novels/projects/{projectCode}/bible/confirm`：生成只保存 `STORY_BIBLE` Draft，确认时才由服务端固定状态、序列化结构化字段并写入正式故事圣经，成功后删除 Draft；原有正式 Story Bible 查询与手工保存接口保持不变。
- 新增 `GenerateStoryBibleRequestDTO`、`ConfirmStoryBibleRequestDTO` 和力量体系请求 DTO；未生成或抽取任何角色。
- 验证结果：执行 `mvn -q -pl novel-agent-app -am -DskipTests compile` 成功。

### Task 43：Story Bible Draft 与前端确认闭环验证（2026-08-29）

- 在 `novel-agent-web/src/types/index.ts`、`src/api/project.ts` 和 `src/utils/storyBible.ts` 增加 Draft 类型、生成/确认 API 及 Draft 与编辑画布之间的转换；`SetupView.vue` 提供生成输入、Draft 状态提示、编辑回填、确认保存和后续正式保存，硬规则与力量层级仍为可增删条目。
- 新增 `StoryBibleGenerationTest`、`StoryBibleGenerationHttpTest`、`StoryBibleConfirmationHttpTest`，并更新提示词、旧流程清理和设定页契约测试；测试覆盖角色不参与、Draft 元数据隔离、用户修改、确认持久化与 HTTP 字段映射。
- 验证结果：相关 Maven 定向测试 7 个用例通过；`novel-agent-web` 执行 `npm run build` 通过（仅保留既有产物体积提示）。

### Task 43：Story Bible 规划仓储集成测试（2026-08-29）

- 新增 `novel-agent-app/src/test/java/cn/ninth/novel/infrastructure/adapter/repository/StoryBiblePlanningRepositoryTest.java`，覆盖规划仓储对正式故事圣经的插入、更新和查询往返。
- 验证结果：定向集成测试已启动 Spring 容器，但本机 `127.0.0.1:13306` MySQL 连接被拒绝，未执行到数据库断言；此前的内存领域测试、HTTP 测试和前端构建不受此环境问题影响。

### Task 43：Story Bible 兼容性契约回归（2026-08-29）

- 同步规划 API、仓储能力和设定页契约测试，保留当前故事圣经生成/确认路由，同时继续禁止已删除的旧初始化、批量章纲和角色自动生成链路。
- 验证结果：规划接口、无旧规划链路、规划仓储能力边界和 `StoryBibleEditingContractTest` 共 4 个用例通过。

### Task 43：最终定向验证（2026-08-29）

- 合并执行 Story Bible 领域、HTTP、Prompt、旧链路兼容性和前端设定页契约测试，共 10 个测试类通过；`novel-agent-web` 再次执行 `npm run build` 成功，`git diff --check` 通过。
- MySQL 集成测试仍受本机 `127.0.0.1:13306` 未启动影响，未纳入通过结论。

### Task 43.2：Story Bible 初始生成 Prompt 收敛（2026-08-30）

- 在 `novel-agent-domain/src/main/java/cn/ninth/novel/domain/planning/service/prompt/PlanningPrompts.java` 新增并启用 `STORY_BIBLE_INIT_SYSTEM`，明确初始设定只接收项目标题、题材、预计章节数和用户补充要求；禁止注入角色、大纲、历史信息，限制硬规则数量，并允许不需要力量体系的题材返回 `powerSystem = null`。
- 调整 `GenerateStoryBibleRequestDTO`、规划服务、HTTP 控制器和前端生成请求，模型用户输入仅由上述四项组成；确认链路同步支持空力量体系，正式 Story Bible 在该场景下保存空力量字段。
- 扩展 `StoryBibleGenerationTest`、`StoryBibleGenerationHttpTest` 和 `PlanningPromptsTest`，验证 Prompt 常量、四项输入隔离、Draft 输出类型及空力量体系；定向 Maven 测试通过，`novel-agent-web` 执行 `npm run build` 通过。

### Task 43.3：Story Bible Generate-Draft-Confirm 归属项目服务（2026-08-30）

- 在 `INovelProjectService` / `NovelProjectService` 增加 `generateStoryBible(projectCode, requirement)` 与 `confirmStoryBible(projectCode, draftId, editedBible)`；生成通过项目基础信息构造初始 Prompt 并保存 `STORY_BIBLE` Draft，确认接收正式 `StoryBibleVO` 内容、强制 `CONFIRMED` 后调用现有 `saveBible` 并删除 Draft。
- 将 Story Bible 生成/确认路由迁移到 `NovelProjectController`，确认请求改为正式 Story Bible 字段；移除规划服务和规划仓储中的重复 Story Bible 写入能力，保留项目服务现有 `saveBible/getBible` 供后续手动编辑。
- 更新项目服务、项目控制器和规划兼容性测试，定向 Maven 测试通过；`novel-agent-web` 执行 `npm run build` 通过。

### Task 43.4：Story Bible HTTP API 契约确认（2026-08-30）

- 确认 `NovelProjectController` 提供 `POST /{projectCode}/bible/generate` 与 `POST /{projectCode}/bible/confirm`：生成请求仅包含 `requirement`，返回现有 Draft 响应中的 `draftId` 和 `payload`；确认请求包含 Draft ID 与编辑后的 Story Bible 内容字段，不接收 `status`。
- 保留 `POST /{projectCode}/bible` 人工保存和 `GET /{projectCode}/bible` 人工读取接口，前端继续通过 `src/api/project.ts` 调用这些项目接口及生成/确认接口。
- 扩展 HTTP/API 契约测试，验证路由、请求字段、Draft 返回内容和人工接口均保持可用；定向 Maven 测试、前端 `npm run build` 与 `git diff --check` 通过。

### Task 43.5：Story Bible 设定页信息架构重构（2026-08-30）

- 重构 `novel-agent-web/src/views/SetupView.vue` 的设定页：新增“还没有故事设定”空态、核心世界背景编辑区、突出显示的不可违反规则列表，以及默认收起的特殊体系、创作方向和写作风格折叠区；角色库页面保持原有结构。
- 将 AI 生成入口改为 Element Plus Dialog，生成结果进入独立 Draft 编辑态，提供“重新生成”和“应用这份设定”，只有应用操作调用 Confirm；正式 Story Bible 仍可通过顶部“保存”继续人工编辑保存。
- 补充响应式样式，主内容使用弹性宽度并覆盖桌面、窄屏和移动端布局；更新 `StoryBibleEditingContractTest` 以校验新的页面文案、字段绑定、折叠区域和 Draft 交互。
- 验证结果：`novel-agent-web` 执行 `npm run build` 通过；`StoryBibleEditingContractTest` 定向 Maven 测试通过；`git diff --check` 通过（仅有既有换行符提示）。

### Task 43.7：Story Bible 后续 Prompt 职责约束（2026-08-30）

- 更新 `docs/chapter-workflow.md`，明确大纲读取创作方向与世界背景，`ChapterPlan` 读取精简世界背景与少量必要硬规则，正文读取世界背景、相关硬规则与 `styleGuide`。
- 明确世界背景用于防止明显越界而不是要求每章重复体现；`hardRules` 仅在相关场景主动使用，但生成结果任何时候都不能冲突。
- 记录当前非目标：不新增 Setting RAG、向量库、token budget 或复杂规则检索；设定规模增长后再单独评估相关规则筛选。
- 验证结果：文档变更通过 `git diff --check`，未修改生成链路、数据模型或 HTTP API。

### Task 43：大纲层级收敛与 STAGE 清理（2026-08-30）

- 将 `OutlineNodeKindEnum`、前端 `OutlineNodeKind`、Repository 读取和数据库 Schema 收敛为 `BOOK（书）→ VOLUME（卷）→ ARC（章级大纲节点）`，移除 `STAGE` 及旧的 `STORY_ARC` 读取兼容逻辑；开发 seed 同步改为 `BOOK → VOLUME → ARC`。
- 在 `PlanningService` 的手工创建、更新、AI 子大纲生成和确认入口统一校验固定父子关系，禁止跳级、同级嵌套、反向嵌套和 ARC 下挂子节点；前端新增/AI 拆分选项只提供当前父节点允许的下一层，并将类型标签改为“书/卷/章”。
- 更新大纲 Prompt、数据库/前端/HTTP/领域测试夹具与契约断言，补充 `BOOK → VOLUME → ARC` 层级校验测试；章节计划上下文测试数据同步移除中间阶段节点。
- 验证结果：12 个相关 Maven 定向测试通过；`novel-agent-web` 执行 `npm run build` 通过（仅保留既有产物体积提示）；`git diff --check` 通过。

### Task 43.3：AI 子大纲类型由服务端按父节点推导（2026-08-30）

- 收敛 `IPlanningService.generateChildOutlines`、生成请求 DTO、Controller 和前端 API，请求不再接收 `targetNodeKind`；服务端根据父节点通过 `expectedChildKind` 固定推导 `BOOK → VOLUME`、`VOLUME → ARC`，`ARC` 无可生成子级。
- 确认子大纲时同步移除客户端类型字段，服务端重新根据父节点校验 Draft 类型；AI 拆分弹窗仅展示服务器决定的子节点类型，不再提供类型选择器。
- 更新领域、HTTP 和前端契约测试，新增 `VOLUME` 父节点自动生成 `ARC` 的回归覆盖。
- 验证结果：后端编译及 `ChildOutlineGenerationTest`、`ChildOutlineConfirmationTest`、相关 HTTP/UI 契约测试通过；`novel-agent-web` 执行 `npm run build` 通过；`git diff --check` 通过。

### Task 43.4：大纲页按节点类型展示下一步操作（2026-08-30）

- 重构 `novel-agent-web/src/views/OutlineView.vue` 的选中节点操作区：`BOOK` 直接提供“AI 生成卷 / 手动添加卷”，`VOLUME` 提供“AI 生成章 / 手动添加章”，`ARC` 明确显示“没有下一级大纲”。
- 手动添加弹窗和 AI 拆分弹窗均取消类型选择，前端只展示由父节点层级决定的类型；创建请求和生成请求使用当前父节点推导结果。
- 移除 OutlineView 中的 ChapterPlan 入口、抽屉和 Draft 生成弹层；章节计划 API 数据仍保留为节点删除及章节范围锁定所需的保护信息。
- 更新大纲、手动添加、AI 拆分和 ChapterPlan UI 契约测试，验证节点驱动操作和 ChapterPlan 不出现在大纲页。
- 验证结果：相关 Maven UI 契约测试通过；`novel-agent-web` 执行 `npm run build` 通过；`git diff --check` 通过。

### Task 43.5：当前节点右上角主动作重构（2026-08-30）

- 将 `novel-agent-web/src/views/OutlineView.vue` 的 AI 生成和手动添加动作从标题下方独立操作区移动到当前节点标题栏右侧，并按节点类型展示“AI 生成卷/章”“+ 手动添加卷/章”；`ARC` 显示“没有下一级大纲”。
- 将上移、下移、删除统一收进当前节点右上角“更多”菜单；移动端编辑抽屉同步使用同一组当前节点主动作，左侧不再承担 AI 主流程。
- 更新 `OutlineViewContractTest` 与 `ChapterPlanUiContractTest`，校验标题栏主动作、更多菜单及旧 `NEXT STEP` 操作区移除。
- 验证结果：`novel-agent-web` 执行 `npm run build` 通过；两个 UI 契约测试定向通过；`git diff --check` 通过（仅有既有换行符提示）。

### Task 43.6：大纲节点中文展示与紧凑层级信息（2026-08-30）

- 重构 `novel-agent-web/src/views/OutlineView.vue` 的左侧大纲树和当前节点信息：用户可见节点类型统一为“书/卷/章”，卷节点按序号显示“卷一/卷二”等，不再展示内部英文枚举。
- 树节点改为“类型 · 标题 + 第1-50章”紧凑排列，当前节点与移动端抽屉同步显示中文类型和紧凑章节范围，并移除面向用户的内部状态标签。
- 更新 `OutlineViewContractTest`，增加中文节点标签、卷序号、紧凑章节范围及无原始枚举插值的契约校验。
- 验证结果：`novel-agent-web` 执行 `npm run build` 通过；大纲显示、响应式和 ChapterPlan UI 契约测试定向通过；`git diff --check` 通过（仅有既有换行符提示）。

### Task 43.7：卷/章层级标识由系统自动生成（2026-08-30）

- 调整 `novel-agent-web/src/views/OutlineView.vue` 的新增卷/章表单，仅保留用户填写的标题和大纲内容；类型提示改为只读“系统层级”，明确层级名称不可手动命名。
- 手动新增卷时由现有子节点最大 `sequenceNo + 1` 生成“卷一/卷二”等预览，并使用同一序号计算值提交；章节点仍只显示“章”，不额外生成章段名称。
- 更新 `AddChildOutlineUiContractTest` 与 `OutlineViewContractTest`，校验无类型名称输入、系统层级提示和 sequenceNo 驱动的显示逻辑。
- 验证结果：`novel-agent-web` 执行 `npm run build` 通过；大纲显示、手动新增和响应式 UI 契约测试定向通过；`git diff --check` 通过（仅有既有换行符提示）。

### Task 43.8：AI 子大纲生成弹窗收敛（2026-08-30）

- 重构 `novel-agent-web/src/views/OutlineView.vue` 的 AI 子大纲配置弹窗：标题动态显示“生成卷大纲/生成章大纲”，配置区仅保留建议生成数量和可选补充要求，默认数量调整为 2。
- 移除 AI 弹窗中的系统层级/目标类型提示；Draft 预览只保留可编辑草稿项，BOOK→VOLUME、VOLUME→ARC 继续由当前节点与服务端决定。
- 更新 `AiSplitOutlineUiContractTest`，按弹窗配置区范围校验无目标节点类型及英文枚举展示，同时保留 Draft→Confirm 流程校验。
- 验证结果：`novel-agent-web` 执行 `npm run build` 通过；AI 弹窗与大纲 UI 契约测试定向通过；`git diff --check` 通过（仅有既有换行符提示）。

### Task 43.9：AI 子大纲 Draft 保留并支持章节范围编辑（2026-08-30）

- 在 `novel-agent-web/src/views/OutlineView.vue` 的 Draft 预览中保留服务端分配的章节范围，并为每个卷/章级大纲提供标题、自然语言概要和章节范围编辑；确认前增加范围合法性校验，仍支持重新生成或确认写入。
- 扩展确认请求的章节范围字段。`PlanningService` 确认时只采用用户编辑的标题、概要和合法范围，节点类型、nodeCode、parentNodeCode、sequenceNo 继续从 Draft/父节点服务端元数据构造，确认后统一保存为 `READY` 并删除 Draft；模型返回仍为语义 Draft 内容。
- 更新 `ChildOutlineConfirmationTest`、`ChildOutlineConfirmationHttpTest` 和 `AiSplitOutlineUiContractTest`，覆盖编辑范围持久化、非连续范围拒绝、HTTP 字段映射以及 UI Draft→Confirm 约束。
- 验证结果：3 个相关 Maven 定向测试类通过；`novel-agent-web` 执行 `npm run build` 通过（仅保留既有产物体积提示）；`git diff --check` 通过（仅有既有换行符提示）。

### Task 43.10：ARC 作为终端大纲节点（2026-08-30）

- 在 `novel-agent-web/src/views/OutlineView.vue` 中将 ARC 明确处理为终端节点：当前节点标题栏和移动端编辑抽屉隐藏 AI 生成下级、手动新增下级等入口，仅保留标题、章节范围和大纲内容编辑。
- ARC 操作区增加轻量“去生成”按钮，仅执行 `router.push('/generate')`，不在大纲页生成 ChapterPlan；卷和书节点的下一层大纲入口保持不变。
- 更新 `OutlineViewContractTest` 与 `ChapterPlanUiContractTest`，验证 ARC 终端分支和生成页跳转，继续约束 ChapterPlan 生成逻辑不进入大纲页。

### Task 43.11：ChapterPlan 从 OutlineView 完全移除（2026-08-30）

- 清理 `novel-agent-web/src/views/OutlineView.vue` 中残留的 ChapterPlan 类型、列表加载、章节计划判断和相关锁定/删除依赖；大纲页现在只负责书/卷/章节点 CRUD、AI 大纲 Draft 生成确认及 ARC 到 `/generate` 的跳转。
- 更新 `ChapterPlanUiContractTest` 与 `OutlineViewCrudContractTest`，明确 OutlineView 不包含 ChapterPlan UI 或数据依赖；ChapterPlan 生成与编辑继续由 `GenerateView.vue` 负责。
- 验证结果：`OutlineViewContractTest`、`OutlineViewCrudContractTest`、`ChapterPlanUiContractTest` 共 8 个用例通过；`novel-agent-web` 执行 `npm run build` 通过（仅保留既有产物体积提示）；`git diff --check` 通过（仅有既有换行符提示）。

### 手动新增大纲范围校验（2026-08-30）

- 修复 `novel-agent-web/src/views/OutlineView.vue` 手动新增卷/章时固定提交空章节范围的问题；新增表单要求填写起止章节，并在创建前检查范围位于父节点内且不与已有同级大纲重叠。
- 在 `PlanningService` 创建/更新大纲的服务端校验中要求章节范围完整存在，同时校验子节点不得越出父节点范围、不得与同级节点范围重叠；有效范围如 `1-50`、`51-100` 才能保存。
- 验证结果：`ManualOutlineCrudTest`、`ManualOutlineCrudHttpTest`、`AddChildOutlineUiContractTest`、`OutlineViewContractTest` 定向通过；`novel-agent-web` 执行 `npm run build` 通过（仅保留既有产物体积提示）；`git diff --check` 通过（仅有既有换行符提示）。

### 44.1.4：手动创建大纲结构字段由后端生成（2026-08-30）

- 收敛 `CreateOutlineNodeRequestDTO`、前端 `CreateOutlineNodeRequest` 和 `OutlineView.vue` 手动新增请求，仅提交 `parentNodeCode`、标题、概要和章节范围，不再提交 `nodeCode`、`nodeKind`、`sequenceNo` 或 `status`。
- `PlanningService` 根据父节点固定推导 VOLUME/ARC，使用仓储现有同级最大序号加一、统一 nodeCode 生成规则，并固定新节点状态为 `PLANNED`；ARC 父节点拒绝创建。
- 更新手动创建领域、HTTP、UI 契约测试，覆盖服务器生成结构字段和客户端不再提交结构字段。
- 验证结果：上述 Maven 定向测试通过；`novel-agent-web` 执行 `npm run build` 通过（仅保留既有产物体积提示）；`git diff --check` 通过（仅有既有换行符提示）。

### 44.1.5：手动新增 DTO 仅负责创建卷/章（2026-08-30）

- 确认 `CreateOutlineNodeRequestDTO` 仅保留 `parentNodeCode`、`title`、`summary`、`startChapter` 和 `endChapter`，不接收结构元数据。
- 手动创建服务要求存在父节点并依据父节点推导 VOLUME 或 ARC；无父节点的 BOOK 根节点请求被拒绝，BOOK 仍通过 `generateRootOutline` → `confirmRootOutline` 创建。
- 补充手动创建根节点边界测试，并验证既有卷/章结构生成逻辑未受影响。
- 验证结果：`ManualOutlineCrudTest`、`ManualOutlineCrudHttpTest` 定向通过；测试打印确认 `VOL_001`、`VOL_002`、`ARC_001` 由服务端生成。

### 44.1.6：清理前端手动新增结构字段生成逻辑（2026-08-30）

- 清理 `OutlineView.vue` 中遗留的 `childKindOrders` 映射；手动新增请求继续只提交标题、摘要和章节范围，结构字段不由前端生成或提交。
- 保留后端返回的 `nodeKind`、`sequenceNo` 用于节点层级和“卷一/卷二”等展示，固定 BOOK→VOLUME、VOLUME→ARC 的标签逻辑仅服务于界面提示和入口控制。
- 更新 `AddChildOutlineUiContractTest`，明确约束旧结构字段生成逻辑不得回归。
- 验证结果：`AddChildOutlineUiContractTest` 定向通过；`novel-agent-web` 执行 `npm run build` 通过（仅保留既有产物体积提示）。

### 44.1.7：收敛 Root Outline AI Prompt 上下文（2026-08-30）

- 在 `PlanningService` 中显式组装根大纲 Prompt，仅传项目标题、题材、预计章节数、Story Bible 的创作方向字段、世界背景、最多 4 个已有核心人物的 `name`/`roleType`/`personality` 摘要及用户补充要求。
- 更新 `ROOT_OUTLINE_SYSTEM`，明确根大纲只使用显式摘要上下文，不要求或推断完整角色档案、历史资料、地理体系或其他数据库信息。
- 更新 `RootOutlineGenerationTest`，覆盖分区字段、人物摘要投影、空补充要求及完整 VO 元数据不进入 Prompt。
- 验证结果：`RootOutlineGenerationTest`、`PlanningPromptsTest` 定向通过；`git diff --check` 通过（仅有既有换行符提示）。

### 44.1.8：收敛 Child Outline AI Prompt 上下文（2026-08-30）

- 在 `PlanningService` 中显式组装卷/章级子大纲 Prompt，仅传项目基础信息、创作方向、世界背景、父节点的标题/摘要/章节范围、目标层级、建议数量和用户补充要求。
- 复用轻量核心人物摘要逻辑，优先主角/核心角色，最多传 4 人且只包含 `name`、`roleType`、`personality`；不新增复杂 Selector。
- 更新 `CHILD_OUTLINE_SYSTEM` 与相关测试，禁止完整 `StoryBibleVO`、`StoryCharacterVO` 列表和 `OutlineNodeVO` 整对象进入 Prompt。
- 验证结果：`ChildOutlineGenerationTest`、`RootOutlineGenerationTest`、`PlanningPromptsTest` 定向通过；`git diff --check` 通过（仅有既有换行符提示）。

### 44.1.9：固定 Prompt 职责边界（2026-08-30）

- 在 `ROOT_OUTLINE_SYSTEM` 与 `CHILD_OUTLINE_SYSTEM` 中明确 Outline 只读取创作方向、世界背景和必要人物摘要，不将 `hardRules`、`powerSystemJson` 或 `styleGuide` 作为大纲上下文。
- 扩展根大纲和子大纲生成测试，验证这些设定字段及其示例值不会进入 Outline user Prompt；ChapterPlan 相关回归继续验证少量硬规则与当前状态上下文。
- 保持 `docs/chapter-workflow.md` 的职责定义：Outline 使用创作方向与世界背景，ChapterPlan 使用精简边界和少量规则，正文使用世界背景、相关规则与文风指导。
- 验证结果：`RootOutlineGenerationTest`、`ChildOutlineGenerationTest`、`PlanningPromptsTest`、`ChapterPlanSingleGenerationTest` 定向通过；`git diff --check` 通过（仅有既有换行符提示）。

### 44.1.10：补充手动大纲与 Prompt 边界测试（2026-08-30）

- 扩展 `ManualOutlineCrudTest`，覆盖 BOOK→VOLUME 的服务端结构字段生成、VOLUME→ARC 创建、ARC 子节点拒绝、子节点越界/同级重叠拒绝，以及修改节点造成兄弟范围重叠时拒绝并保持原范围。
- 保留 `ManualOutlineCrudHttpTest` 与 `AddChildOutlineUiContractTest` 对创建请求不传 `nodeKind`、`nodeCode`、`sequenceNo`、`status` 的验证。
- 保留 `RootOutlineGenerationTest`、`ChildOutlineGenerationTest` 对完整 `StoryBibleVO`、`StoryCharacterVO` 列表和 `OutlineNodeVO` 整对象不进入 Prompt 的验证。
- 验证结果：上述 6 个测试类定向通过，并打印关键结构生成、范围校验和 Prompt 隔离结果；`git diff --check` 通过（仅有既有换行符提示）。

### 数据库测试复用应用配置（2026-08-30）

- 在 `AGENTS.md` 的“测试验证”中补充数据库连接规则，要求测试复用 `novel-agent-app/src/main/resources/application.yml` 及对应环境配置的数据源信息，不在测试代码或测试资源中重复配置或硬编码连接信息。
- 本次仅修改规则与工作总结文档，通过 `git diff --check` 完成格式检查，未运行测试。

### Task 44.2：章级大纲单章化与自动递增生成（2026-08-30）

- 在 `novel-agent-domain/.../PlanningService.java` 中收敛 BOOK/VOLUME/ARC 章节语义：BOOK 必须覆盖项目全书范围，VOLUME 继续使用连续章节范围，ARC 创建和 AI 生成统一从父卷已有章节点的最大章节号后自动递增分配连续单章范围；ARC 的 nodeCode、sequenceNo、status 仍由后端生成。
- ARC 手动创建忽略客户端传入的章节范围；AI 子大纲 Prompt 仅要求模型返回 `title` 和 `summary`，Draft 确认只采用用户编辑的内容，ARC 章节范围保留服务端分配值；ARC 更新禁止修改章节号。
- 在 `PlanningRepository.java` 与 `docs/sql/schema.sql` 增加 ARC 单章持久化保护；`docs/sql/seed.sql` 将演示 ARC 迁移为单章并更新插入固件。前端 `OutlineView.vue` 隐藏 ARC 范围输入，仅对 VOLUME 提交和校验章节范围；ARC 确认请求不提交范围字段。
- 更新 `ArcSingleChapterOutlineTest`、相关手动/HTTP/UI 契约测试及旧测试固件，覆盖手动创建、AI 递增生成、Draft 确认、更新拒绝和旧多章 ARC 固件迁移。
- 验证结果：Maven 定向测试 36 个用例通过；`novel-agent-web` 执行 `npm run build` 通过；`git diff --check` 通过（仅有既有换行符提示）。

### Task 44.2.2～44.2.3：BOOK→VOLUME 区间分配与 VOLUME→ARC 递增分配回归（2026-08-30）

- 明确 `OutlineRangeAllocator` 只用于 BOOK→VOLUME 的连续区间拆分；VOLUME→ARC 不再重新均分父卷范围。
- 在 `ArcSingleChapterOutlineTest` 增加父卷 1～50、已有 ARC 1～20 的回归场景：先生成第 21 章，再生成 3 个章级大纲得到第 22、23、24 章，并验证每个 ARC 均为单章。
- 验证结果：`ArcSingleChapterOutlineTest` 4 个用例定向通过，打印了 21、22、23、24 的实际分配结果。

### Task 44.2.4：统一 next available chapter 分配（2026-08-30）

- 在 `PlanningService` 增加统一的 `findNextAvailableChapters(projectCode, volume, count)` 逻辑，仅扫描指定 VOLUME 范围内的同父 ARC，跳过已占用章节并从最小未占用章节开始递增；可用章节不足时拒绝生成。
- 手动创建、AI 生成及 Draft 确认校验统一复用该逻辑；VOLUME→ARC 不再调用 `OutlineRangeAllocator`，BOOK→VOLUME 的区间分配保持不变。
- 在 `ArcSingleChapterOutlineTest` 覆盖已有 ARC 为 1、2、4 时生成 3 个得到 3、5、6，以及可用章节不足时拒绝生成，并打印实际分配结果。
- 验证结果：`ArcSingleChapterOutlineTest` 5 个用例定向通过；`git diff --check` 通过（仅有既有换行符提示）。

### Task 44.2.5：拆分 VOLUME/ARC 子大纲归一化（2026-08-30）

- 在 `PlanningService` 将 `normalizeChildOutlines` 拆分为 `normalizeVolumeOutlines` 与 `normalizeArcOutlines`；VOLUME 分支独立使用 `OutlineRangeAllocator` 生成连续区间，ARC 分支先获取可用章节号，再逐项绑定为 `startChapter=endChapter`。
- ARC 归一化只读取模型返回的 `title` 与 `summary`，章节号、范围、nodeCode、sequenceNo 和 status 继续由后端生成。
- 验证结果：`ArcSingleChapterOutlineTest`、`ChildOutlineConfirmationTest`、`ChildOutlineGenerationTest` 共 17 个用例定向通过；前端 `npm run build` 通过；`git diff --check` 通过（仅有既有换行符提示）。

### Task 44.2.6：简化 preferredCount 默认语义（2026-08-30）

- 将前端 AI 子大纲生成数量的初始值、打开弹窗重置值和关闭弹窗重置值统一改为 `1`。
- 后端请求未提交 `preferredCount` 时默认传入 `1`；显式传入 `2`、`3` 仍表示生成对应数量的子大纲，并继续由 ARC 可用章节分配逻辑绑定未规划章节。
- 新增 HTTP 契约测试验证省略 `preferredCount` 时服务端收到 `1`，并更新 UI 契约测试锁定前端默认值。
- 验证结果：`ChildOutlineGenerationHttpTest`、`ChildOutlineGenerationTest`、`ArcSingleChapterOutlineTest`、`AiSplitOutlineUiContractTest` 共 13 个用例定向通过；前端 `npm run build` 通过。

### Task 44.2.7：VOLUME→ARC 的 AI 生成章界面（2026-08-30）

- 在 `OutlineView.vue` 的 AI 子大纲生成配置区将数量标签简化为“生成数量”，VOLUME 生成 ARC 时显示“系统将从当前卷中自动选择下一个未规划章节”。
- 配置区不展示开始章节、结束章节、目标范围或 chapterNumber 输入；ARC 预览仍只展示后端已分配的章节结果，不允许手动指定章节号。
- 更新 `AiSplitOutlineUiContractTest`，锁定生成章提示和无范围输入约束。
- 验证结果：`AiSplitOutlineUiContractTest`、`OutlineViewContractTest` 共 6 个用例定向通过；前端 `npm run build` 通过。

### Task 44.2.8：ARC Draft 预览只读显示后端章节号（2026-08-30）

- 在 `OutlineView.vue` 的 ARC Draft 条目中，将后端分配的章节号以“第 N 章”只读文本显示在标题和概要编辑区之前。
- ARC Draft 仅保留 `title`、`summary` 的编辑绑定；`startChapter`、`endChapter` 和 `chapterNumber` 不提供编辑控件或绑定。
- 更新 `AiSplitOutlineUiContractTest`，验证章节号展示位置及 ARC 章节字段只读约束。
- 验证结果：`AiSplitOutlineUiContractTest` 1 个用例定向通过；前端 `npm run build` 通过。

### Task 44.2.9：ARC Draft 确认固定使用后端结构字段（2026-08-30）

- 在 `PlanningService` 抽取确认合并逻辑；ARC 确认时固定沿用 Draft 的 `nodeCode`、`parentNodeCode`、`nodeKind`、`sequenceNo`、`startChapter` 和 `endChapter`，仅使用编辑请求中的 `title`、`summary`，状态继续由后端设置。
- `OutlineView.vue` 仅在 VOLUME 确认分支回传 `startChapter`、`endChapter`，ARC 请求不回传章节范围或 chapterNumber。
- 扩展 ARC 确认测试，验证全部结构字段保持 Draft 值，并加强前端范围回传分支契约。
- 验证结果：`ArcSingleChapterOutlineTest`、`ChildOutlineConfirmationHttpTest`、`AiSplitOutlineUiContractTest` 共 7 个用例定向通过；`git diff --check` 通过（仅有既有换行符提示）。

### Task 44.2.10：ARC 手动新增自动分配章号（2026-08-30）

- 手动新增 ARC 继续由 `PlanningService` 调用 `findNextAvailableChapters(projectCode, volume, 1)`，忽略客户端范围并自动选择当前卷最小未占用章节；MVP 不提供指定章节创建入口。
- `OutlineView.vue` 的桌面弹窗和移动端抽屉仅为 VOLUME 显示章节范围输入；新增 ARC 时只填写标题和概要，并提示章节号由当前卷自动分配。
- 增加手动新增场景回归：已有 ARC 1、2、4 时手动创建自动得到第 3 章。
- 验证结果：`ArcSingleChapterOutlineTest`、`ManualOutlineCrudTest`、`AddChildOutlineUiContractTest` 共 17 个用例定向通过；前端 `npm run build` 通过。

### Task 44.2.11：ARC 更新禁止修改章节号（2026-08-30）

- `PlanningService.updateOutlineNode` 对 ARC 的 `startChapter`、`endChapter` 变化统一拒绝，不受是否存在 ChapterPlan 影响；章节号作为结构字段固定保留。
- `OutlineView.vue` 将 ARC 编辑页的章节范围控件设为只读，用户仅修改标题和大纲内容。
- 增加 ARC 无 ChapterPlan 时的内容更新成功用例，以及第 21 章改为第 35 章被拒绝的回归用例。
- 验证结果：`ArcSingleChapterOutlineTest`、`OutlineViewContractTest` 共 12 个用例定向通过；`git diff --check` 通过（仅有既有换行符提示）。

### Task 44.2.12：ARC 独立重新生成并更新现有节点（2026-08-30）

- 在 `IPlanningService`/`PlanningService` 增加 ARC 重新生成 Draft 与确认更新能力；生成时读取 Story Bible、BOOK、当前 VOLUME 和当前 ARC，上下文提交给模型，模型只返回 `title`、`summary`。
- 重新生成 Draft 固定保存当前 ARC 的 `nodeCode`、`parentNodeCode`、`nodeKind`、`sequenceNo`、`startChapter`、`endChapter` 和 `status`；确认时校验结构未变化，并通过现有 `updateOutlineNode` 更新原 ARC，明确不执行 INSERT。
- 增加 POST 生成/确认接口及 `OutlineView.vue` 的“AI 重新生成”配置、Draft 预览和确认更新流程；前端只允许编辑标题与大纲内容，章节号只读展示。
- 增加领域、HTTP、前端契约测试，验证固定章节号、请求字段和 UPDATE 写入路径；验证结果：Maven 定向测试 11 个用例通过，`novel-agent-web` 执行 `npm run build` 通过。

### Task 45.1：前端 UI 开发规则收紧（2026-08-30）

- 更新根目录 `AGENTS.md` 的“Element Plus 组件使用规范”，明确禁止自定义基础控件、通过深层样式重建组件体系、使用原生控件替代 Element Plus，以及保留废弃 UI 代码。
- 更新“创作编辑界面”，明确长文本使用自适应高度的 `el-input` 文档式编辑、默认 `resize="none"`，并限制固定大高度、拖拽调整、过度卡片化和多套表单视觉体系。
- 验证结果：通过 `git diff --check`；本次仅修改规则和实现总结文档，未运行测试。

### Task 45.2：Story Bible 设定页编辑体验重构（2026-08-31）

- 调整 `novel-agent-web/src/views/SetupView.vue` 中的世界背景、主要矛盾、结局方向、能力机制、能力代价、写作风格和一句话故事，统一使用 Element Plus `el-input` 的 `autosize` 与 `resize="none"`，默认从 2～3 行开始并随内容向下增长。
- 移除世界背景和写作风格的固定大高度、最大行数、内部大留白、完整矩形边框和灰底样式；保留核心主题、体系名称、hardRule 与 rank 的标准 `el-input`。
- 验证结果：`novel-agent-web` 执行 `npm run build` 通过；`StoryBibleEditingContractTest` 定向测试 1 个用例通过；`git diff --check` 通过；在 1440、900、390 宽度下检查无横向溢出。

### Task 45.2.3：Story Bible 主页面压缩（2026-08-31）

- 在 `novel-agent-web/src/views/SetupView.vue` 中收紧设定页头部、世界背景和不可违反规则两块核心内容的内外间距，保持世界背景与硬规则默认展开并连续呈现。
- 将硬规则区调整为与世界背景一致的轻量画布样式，压缩规则行和“添加规则”间距；将特殊体系、创作方向、写作风格折叠标题收紧为紧凑入口，默认状态仍保持折叠。
- 验证结果：`novel-agent-web` 执行 `npm run build` 通过；`StoryBibleEditingContractTest` 定向测试 1 个用例通过；`git diff --check` 通过。

### Task 45.2.4-45.2.5：减少视觉包装并清理 SetupView 遗留 UI（2026-08-31）

- 保持 Story Bible 继续使用现有 Element Plus `el-input`、`el-button`、`el-collapse` 和 `el-tag`，未新增自定义基础控件。
- 删除 `novel-agent-web/src/views/SetupView.vue` 中模板未引用的旧 Story Bible 布局与样式，包括 `bible-workspace`、`bible-nav`、`bible-panel`、`premise-editor`、`story-engine`、`world-editor`、`voice-setting`、`creative-field`、`canvas-*`、`rules-setting` 及相关媒体查询规则。
- 在 `novel-agent-app/src/test/java/cn/ninth/novel/web/StoryBibleEditingContractTest.java` 增加遗留 Story Bible class 不得回归的契约检查。
- 验证结果：`novel-agent-web` 执行 `npm run build` 通过；`StoryBibleEditingContractTest` 定向测试 1 个用例通过；`git diff --check` 通过。

### Task 45.2.6：Story Bible 验收范围确认（2026-08-31）

- 确认本次 Story Bible 重构未修改 `SetupView.vue` 中角色模板、角色样式或角色业务逻辑；验收范围保持在设定页编辑体验。
- 复核世界背景 `minRows: 3`、所有创作型长文本 `autosize`、`resize="none"`、隐藏正常编辑滚动条、默认折叠辅助设置和已删除遗留 Story Bible CSS，未新增自定义基础 UI 组件。
- 验证结果：`novel-agent-web` 执行 `npm run build` 通过；`StoryBibleEditingContractTest` 定向测试 1 个用例通过；`git diff --check` 通过；1440、1024、390（移动端）视口检查无横向溢出。

### Task 45.3.2：正式 Story Bible 的 AI 调整语义（2026-08-31）

- 在 `PlanningPrompts` 新增 `STORY_BIBLE_REVISION_SYSTEM`，要求模型基于当前正式 Story Bible 做局部调整，只修改用户明确涉及的内容，未涉及内容尽量保持，并仍只返回完整 `StoryBibleDraftVO` 字段。
- 在 `NovelProjectService.generateStoryBible` 中根据是否存在正式 Bible 自动选择初始或调整 Prompt；调整输入包含当前正式 Story Bible 全部设定字段和用户调整要求，继续复用原有 `/bible/generate` 接口及 `STORY_BIBLE` Draft 保存/确认流程。
- 在 `SetupView.vue` 中将正式设定入口改为“AI 调整设定”，调整弹窗和 Draft 提示明确基于正式设定生成调整草稿；调整生成后保持正式 Bible 状态，只有确认 Draft 后才替换正式设定。
- 更新 `GenerateStoryBibleRequestDTO` 注释、前端 Story Bible 契约测试和领域生成测试；未修改 Story Bible 数据结构、角色页面逻辑或新增 AI API。
- 验证结果：`novel-agent-web` 执行 `npm run build` 通过；`StoryBibleGenerationTest` 与 `StoryBibleEditingContractTest` 定向共 5 个用例通过；`git diff --check` 通过。

### Task 45.3.3：Draft 与正式 Story Bible 并存（2026-08-31）

- 删除 `SetupView.vue` 中 `generateBible()` 对 `hasConfirmedBible` 的赋值，使其继续表示数据库是否存在正式版本，不再因生成 Draft 而被置为 `false`。
- 保持正式版本调整期间 `hasConfirmedBible = true`、`bibleDraftId != null` 的状态组合；Draft 仍需编辑并确认后才替换正式版本。
- 在 `StoryBibleEditingContractTest` 增加生成函数作用域检查，防止恢复正式版本与 Draft 互斥的逻辑。
- 验证结果：`novel-agent-web` 执行 `npm run build` 通过；`StoryBibleEditingContractTest` 定向测试 1 个用例通过；`git diff --check` 通过。

### Task 45.3.1：Story Bible 初始生成语义保留（2026-08-31）

- 将 `novel-agent-web/src/views/SetupView.vue` 中无正式 Story Bible 时的生成入口统一显示为“AI 生成初始设定”；已有正式 Bible 时仍显示“AI 重新生成”。
- 保留初始生成现有链路，继续使用 `STORY_BIBLE_INIT_SYSTEM`，并传递项目标题、题材、预计章节数和用户补充要求；未新增 API、未修改数据结构、Prompt 或角色页面逻辑。
- 更新 `novel-agent-app/src/test/java/cn/ninth/novel/web/StoryBibleEditingContractTest.java` 的入口文案契约检查。
- 验证结果：`novel-agent-web` 执行 `npm run build` 通过；`StoryBibleEditingContractTest` 定向测试 1 个用例通过；`StoryBibleGenerationTest` 定向测试 3 个用例通过；`git diff --check` 通过。

### Task 45.3.4-45.3.6：Story Bible 调整快照与 Draft 应用边界（2026-08-31）

- 在 `SetupView.vue` 增加 `confirmedBibleSnapshot`，从已加载、保存或确认后的正式版本建立快照；AI 调整生成后保留正式版本状态，同时将画布切换为 Draft 内容。
- 增加“放弃修改 / 应用修改”操作：放弃时仅恢复正式快照并清理前端 `bibleDraftId`，不请求 AI 或 Confirm；应用时才调用现有 Confirm 接口、更新正式 Story Bible 并清理 Draft 状态。
- 将界面文案统一为“AI 生成初始设定”“AI 调整”“AI 调整草稿”，移除“AI 重新生成”和旧的含混应用文案；未新增版本管理表、RAG 或 Context Budgeter。
- 在 `StoryBibleEditingContractTest` 增加快照、放弃路径无 AI/Confirm 调用以及 Draft 操作文案的回归检查。
- 验证结果：`novel-agent-web` 执行 `npm run build` 通过；`StoryBibleGenerationTest`、`StoryBibleGenerationHttpTest` 与 `StoryBibleEditingContractTest` 定向测试共 6 个用例通过；`git diff --check` 通过。

### Task 45.4：Planning Prompt 创作语义收敛（2026-08-31）

- 在 `novel-agent-domain/.../planning/service/PlanningService.java` 中，以“卷大纲 / 单章大纲”表达子大纲任务类型，保留 `preferredCount` 对应的建议数量、父级剧情范围和“每项对应一章”的创作要求。
- 调整 `planning/service/prompt/PlanningPrompts.java`，使用父级大纲、子大纲等自然语言描述任务，维持现有输出协议和后端业务行为，未引入新的 Prompt Framework 或抽象层。
- 更新 `novel-agent-app/src/test/java/cn/ninth/novel/domain/planning/service` 下的 `ChildOutlineGenerationTest` 与 `ArcSingleChapterOutlineTest`，打印实际 Prompt，验证创作语义、后端词禁入及既有章节分配结果。
- 验证结果：上述两个测试类定向运行共 13 个用例全部通过；6 段 Planning System Prompt 与 4 个 User Prompt 构造方法的文本检查通过；`git diff --check` 通过。

### Task 45.4.1：人物摘要角色语义转换（2026-08-31）

- 在 `PlanningService` 的人物摘要拼接中，将 `roleType` 后端枚举映射为“男主角、女主角、盟友、对手、反派、配角”等自然语言角色定位；未知或缺失值使用“其他角色”，不透传原始枚举。
- 更新根大纲和子大纲生成测试，锁定自然语言角色标签并禁止 `roleType`、`MALE_LEAD` 等后端枚举出现在 Prompt 中；角色筛选逻辑和其他 Prompt 内容保持不变。
- 验证结果：`ChildOutlineGenerationTest`、`RootOutlineGenerationTest`、`ArcSingleChapterOutlineTest` 定向共 15 个用例通过；`git diff --check` 通过。

### Task 45.5.1：PLAN 人物变化使用名称表达（2026-08-31）

- 更新 `novel-agent-domain/src/main/java/cn/ninth/novel/domain/chapter/service/agent/SystemPrompt.java` 中的 PLAN Prompt，要求 `characterChanges` 每项明确写出人物名称，自然语言示例与 JSON 示例统一使用“林澈在发现顾言隐瞒关键线索后，预期会降低对顾言的信任。”，保持字符串数组输出协议。
- 验证结果：PLAN 文本检查确认名称要求与两处示例一致；`git diff --check` 通过。定向执行 `mvn -pl novel-agent-app -am -Dtest=PlanChapterNodePromptContractTest,ChapterPlanVOTest -Dsurefire.failIfNoSpecifiedTests=false test`，2 个用例中 1 个通过、1 个失败；`ChapterPlanVOTest` 通过，`PlanChapterNodePromptContractTest` 的人物数量断言预期 4、实际 0。失败用例调用 `buildUserPrompt`，不读取本次调整的 System Prompt，人物筛选逻辑与该测试保持原样。

### Task 45.5.2：审核严重等级使用中文模型协议（2026-08-31）

- 调整 `novel-agent-domain/.../chapter/service/agent/SystemPrompt.java` 的 REVIEW Prompt，`severity` 使用“严重 / 一般 / 轻微”业务等级；`SeverityEnum` 提供中文标签及反向映射，`ReviseChapterNode` 按中文等级拼接返修意见。
- 在 `novel-agent-infrastructure/.../adapter/port` 新增 `ReviewModelResponse`，以字符串及中文字段说明生成模型 Schema；`ChapterModelPort` 将模型响应转换为 `ReviewReportVO`。领域报告继续使用 `BLOCKER / MAJOR / MINOR`，审核路由与后端 JSON 序列化保持原样，非法、空白或空值等级由后端降级为 `UNKNOWN`。
- 在 `novel-agent-app/src/test/java` 新增 `ReviewSeverityModelContractTest`，使用真实 ChatClient 和离线 ChatModel 桩检查实际发送的 Schema、三档中文 JSON 映射、空报告、非法输入及后端序列化；更新 `ChapterNodeTest` 验证返修输入。
- 验证结果：修改前已复现 Schema 英文枚举、中文反序列化及返修等级契约失败；修改后定向执行 `mvn -pl novel-agent-app -am '-Dtest=ReviewSeverityModelContractTest,ChapterNodeTest#review*+revise*,ReviewReportVOTest,ReviewRouterTest' -Dsurefire.failIfNoSpecifiedTests=false test`，27 个用例通过；`git diff --check` 通过，未调用真实模型。
- 同时运行完整 `ChapterNodeTest` 时，36 个定向用例中 34 个通过，两个 EXTRACT 用例因测试桩返回 `ChapterExtractionVO`、实际请求 `ChapterExtractionResponse` 而失败；该抽取链路不在本次改动范围，保持原样。

### Task 45.5.3：EXTRACT 使用事实业务语义并由后端映射（2026-08-31）

- 更新 `novel-agent-domain/.../chapter/service/agent/SystemPrompt.java` 的 EXTRACT Prompt，事实类型、关系、人物主体和生存状态全部使用“人物状态 / 生存状态 / 失踪”等业务语义；返回字段改为 `subject / relation / object / type`，不再向模型暴露事实枚举、`characterCode`、`predicate`、`lifeStatus` 或生存状态枚举值。
- 将 `ChapterExtractionResponse` 的模型边界字段改为自然语言字符串；`ExtractFactsNode` 根据角色姓名映射稳定人物编码，根据中文事实类型、关系和生存状态映射领域枚举及后端字段，章节号仍由程序补充，未知事实类型或无法识别的人物会被拒绝。
- EXTRACT 用户上下文改为只展示角色姓名、当前状态和中文生存情况；保留领域事实的去重、校验、章节来源号和后续持久化契约，并拒绝模型返回的 `lifeStatus`、`ALIVE` 等旧后端术语。更新 `ChapterNodeTest`，新增 `ExtractFactsSemanticPromptContractTest` 验证 Prompt 隔离与“林澈 → char-lin-che、失踪 → MISSING”的完整转换。
- 验证结果：`mvn -pl novel-agent-app -am -Dtest=ChapterNodeTest,ExtractFactsSemanticPromptContractTest -Dsurefire.failIfNoSpecifiedTests=false test` 共 20 个用例通过；`git diff --check` 通过。

### Task 45.5.4：EXTRACT 摘要数组与数据库字段解耦（2026-08-31）

- 将 `ChapterExtractionResponse.Summary` 的 `protagonistChangesJson` / `unresolvedQuestionsJson` 改为 `List<String> protagonistChanges` / `List<String> unresolvedQuestions`；EXTRACT Prompt 改为要求真正的 JSON 数组，不再出现数据库字段后缀或“字符串形式的空数组”。
- `ExtractFactsNode` 使用 `ObjectMapper` 将模型数组序列化回现有 `ChapterSummaryVO` 的 JSON 字符串字段，保持基础设施现有数据库存储结构不变，同时模型 Schema 与数据库命名隔离。
- 更新 `ExtractFactsSemanticPromptContractTest` 和章节节点测试，覆盖数组协议、序列化结果及空数组；验证结果：`mvn -pl novel-agent-app -am -Dtest=ChapterNodeTest,ExtractFactsSemanticPromptContractTest -Dsurefire.failIfNoSpecifiedTests=false test` 共 20 个用例通过；`git diff --check` 通过。

### Task 45.6.1：REVIEW 输出字段去 VO 化（2026-08-31）

- 将 REVIEW 模型输出根字段从 `reviewIssueVOList` 改为 `issues`，`ReviewModelResponse` 只保留 `issues` 与 `severity / category / description / evidence` 业务字段，再由适配层映射为领域 `ReviewIssueVO` 和 `ReviewReportVO`。
- 更新 `SystemPrompt.REVIEW_SYSTEM_PROMPT` 的 JSON 示例和空结果说明；Prompt 中不再出现 `VO`、`DTO`、`PO` 或 `reviewIssueVOList`，审核节点内部仍使用领域报告完成校验和路由。
- 更新 `ReviewSeverityModelContractTest` 的离线模型响应，覆盖 `issues` 空数组、中文等级映射和非法等级降级为 `UNKNOWN`。验证结果：定向执行 `mvn -pl novel-agent-app -am '-Dtest=ReviewSeverityModelContractTest,ChapterNodeTest#review*+revise*' -Dsurefire.failIfNoSpecifiedTests=false test`，18 个用例通过；`git diff --check` 通过。

### Task 45.6.2：REVIEW 严重程度中文协议确认（2026-08-31）

- 确认 `SystemPrompt.REVIEW_SYSTEM_PROMPT` 只向模型提供“严重 / 一般 / 轻微”三个等级，Prompt 中不出现 `BLOCKER / MAJOR / MINOR`。
- `SeverityEnum` 增加后端 `UNKNOWN` 兜底档；中文三档分别映射到现有严重等级，缺失或无法识别的模型值映射为 `UNKNOWN`，不作为模型输出选项。
- 更新 `ReviewSeverityModelContractTest` 覆盖空值、空字符串、旧英文值及未知文本的默认映射。验证结果：`mvn -pl novel-agent-app -am '-Dtest=ReviewSeverityModelContractTest,ChapterNodeTest#review*+revise*,ReviewReportVOTest,ReviewRouterTest' -Dsurefire.failIfNoSpecifiedTests=false test` 共 27 个用例通过；`git diff --check` 通过。

### Task 45.6.3：Story Bible Revision 去 JSON 存储格式（2026-08-31）

- `NovelProjectService` 在构造 Story Bible Revision Prompt 前解析 `powerSystemJson` 与 `hardRulesJson`，分别渲染为特殊体系字段、层级列表和硬规则列表的自然语言业务上下文；同时兼容中文键名、层级别名、规则对象和历史多行文本。
- Revision Prompt 不再直接暴露 JSON 字符串或数据库字段命名；正式 Story Bible 的数据库存储结构及保存流程保持不变，历史结构化或文本内容均转换后再提供给模型。
- 更新 `StoryBibleGenerationTest` 锁定标准及历史结构的自然语言上下文和数据库格式隔离。验证结果：`mvn -pl novel-agent-app -am -Dtest=StoryBibleGenerationTest -Dsurefire.failIfNoSpecifiedTests=false test` 共 5 个用例通过；`git diff --check` 通过。

### Task 45.6.4：Planning 人物定位使用自然语言（2026-08-31）

- `RelevantCharacterSelector` 在生成提供给模型的人物摘要时，将 `MALE_LEAD`、`FEMALE_LEAD`、`ANTAGONIST`、`VILLAIN`、`SUPPORTING` 等角色值转换为男主角、女主角、反派、配角等业务语义；未知枚举样式值降级为其他角色。
- Root/Child/Arc 大纲、单章 Planning 和章节 PLAN 共用的相关人物上下文均不再把角色枚举原值发送给模型。
- 更新人物选择、单章 Planning 与章节 PLAN 契约测试，覆盖男主角、女主角、反派、配角及枚举隔离。

### Task 45.6.5：补充 Prompt Boundary Test（2026-08-31）

- 新增 `PromptBoundaryContractTest`，集中检查 Planning 与 Chapter Agent 的系统 Prompt 不包含 `reviewIssueVOList`、`powerSystemJson`、`hardRulesJson`、`characterCode`、英文审核等级及人物角色枚举。
- 保留业务层内部 DTO/VO、数据库字段和领域枚举实现，Boundary Test 只检查发送给模型的 Prompt 文本。

### Task 45.6.6：Prompt 边界回归验证（2026-08-31）

- Planning、Story Bible 及章节 PLAN 相关定向测试共 24 个用例通过。
- Chapter Agent 的 PLAN/REVIEW/REVISE/EXTRACT 契约、审核适配和 Story Bible HTTP/前端契约共 32 个用例通过，其中包含 `PlanChapterNodePromptContractTest`。
- 两组定向 Maven 测试及 `git diff --check` 均通过，未调用真实模型。

### Task 46.1：定义人物 Draft 模型（2026-08-31）

- 新增 `CharacterDraftVO`，仅包含 `name`、`role`、`gender`、`ageDescription`、`appearance`、`personality`、`backgroundStory` 和 `note` 八个创作字段。
- 新增 `CharacterDraftListVO`，以 `characters` 数组承载人物草稿列表；未加入 `characterCode`、`roleType`、`currentStateJson`、`lifeStatus`、`status` 等后端字段。
- 新增 `CharacterDraftModelTest` 校验 record 字段顺序及列表承载行为。验证结果：`mvn -pl novel-agent-app -am -Dtest=CharacterDraftModelTest -Dsurefire.failIfNoSpecifiedTests=false test` 通过 1 个用例；`git diff --check` 通过。

### Task 46.2：新增人物生成 Prompt（2026-08-31）

- 在 `novel-agent-domain/src/main/java/cn/ninth/novel/domain/planning/service/prompt/PlanningPrompts.java` 新增人物设计 System Prompt，要求模型结合项目标题、题材、当前 Story Bible（故事设定）、已有角色摘要和用户补充要求，设计适合主线发展的核心人物。
- Prompt 的返回协议使用 `characters` 数组和八个创作字段，与 `CharacterDraftVO` 保持一致；没有合适人物时返回空数组。

### Task 46.3：人物 Prompt 去后端化（2026-08-31）

- 人物定位和性别统一使用男主角、女主角、反派、盟友、配角以及男、女、其他等自然语言，Prompt 不包含 `characterCode`、`MALE_LEAD`、`FEMALE_LEAD`、`ALIVE`、`ACTIVE`、`currentStateJson` 或数据库标识。
- 更新 Planning Prompt 常量收敛契约，并将人物 Prompt 纳入统一 `PromptBoundaryContractTest`；新增 `CharacterPromptContractTest` 锁定业务上下文、创作字段和后端术语隔离。
- 验证结果：定向执行人物 Prompt、Prompt 边界、规划旧模型清理和人物 Draft 模型测试共 4 个用例通过；`git diff --check` 通过。

### Task 46.4：后端人物定位映射（2026-08-31）

- `NovelProjectService` 在人物新增和更新的归一化流程中，将男主角、女主角、盟友、对手、反派、配角等业务定位转换为数据库使用的内部值，同时兼容已有内部值；性别也统一转换为后端值，并保留男主角的性别约束。
- 模型和前端人物编辑使用自然语言定位，内部枚举值只在后端归一化后进入持久化层。

### Task 46.5：人物编码由后端生成（2026-08-31）

- 新增人物时忽略请求体中的编码，由领域服务生成 `char-` 加随机标识；更新人物仍只使用路径中的既有编码。
- `AddStoryCharacterRequestDTO`、前端新增人物请求类型和新增人物提交 payload 不再承载后端状态字段；前端删除本地 `createCharacterCode`，更新请求仍由后端状态字段保持既有状态。
- 更新 HTTP、领域服务和前端边界测试，覆盖后端生成编码、自然语言定位转换及新增请求隔离。验证结果：定向 Maven 测试共 21 个用例通过；`npm run build` 通过；`git diff --check` 通过。

### Task 46.6：后端统一新人物初始状态（2026-08-31）

- `NovelProjectService` 在新建人物时统一生成 `char-` 编码，并由后端固定设置 `lifeStatus=ALIVE`、`status=ACTIVE`、空的 `currentState={}`；请求体和模型均不参与这些状态字段。
- 更新人物时继续沿用路径编码和已有状态，避免把新建默认值覆盖到后续编辑流程。
- `NovelProjectServiceTest` 与 `NovelProjectControllerHttpTest` 覆盖默认状态及请求响应，打印关键归一化结果。

### Task 46.7：新增人物生成 Draft API（2026-08-31）

- 新增 `POST /api/v1/novels/projects/{projectCode}/characters/generate` 及 `GenerateCharacterRequestDTO`，请求只接收 `requirement`，复用现有 `PlanningDraftResponseDTO` 返回人物草稿列表。
- `NovelProjectService.generateCharacters` 将项目标题、题材、自然语言 Story Bible、已有角色摘要和用户要求提供给人物 Prompt，模型只返回 `CharacterDraftListVO` 创作字段。
- 生成流程仅保存 `CHARACTERS` Draft，不调用正式人物仓储；前端新增 `generateCharacters` API 和对应草稿类型，方便后续预览与确认流程接入。
- 验证结果：定向 Maven 测试 `CharacterGenerationServiceTest`、`NovelProjectServiceTest`、`NovelProjectControllerHttpTest` 共 22 个用例通过；`npm run build` 与 `git diff --check` 通过。

### Task 46.10：前端增加 AI 生成人物入口（2026-08-31）

- 在角色页顶部增加“AI 生成核心角色”和“＋ 新建角色”并列入口，点击 AI 入口打开 Element Plus Dialog，接收用户补充要求。
- 生成过程复用 `generateCharacters` API，按当前项目切换自动清理旧草稿，避免跨项目展示人物内容。

### Task 46.11：人物 Draft 编辑 UI（2026-08-31）

- 在 AI Dialog 中展示 `characters[]` 草稿列表，每项可编辑姓名、角色定位、性别、年龄、外貌、性格、背景和备注。
- 每项支持删除；草稿编辑区不显示 `characterCode`、`lifeStatus`、`status` 或其他正式人物后端字段。
- 使用 Element Plus `el-dialog`、`el-form`、`el-input`、`el-select`、`el-button` 和 `el-empty`，长文本采用自适应高度且禁止拖拽调整。
- 验证结果：`CharacterGenerationUiContractTest` 通过；`npm run build` 通过；人物生成、创建默认值、Controller 和 Prompt 边界回归共 25 个用例通过；`git diff --check` 通过。

### Task 46.12：Draft 确认交互（2026-08-31）

- 新增 `POST /api/v1/novels/projects/{projectCode}/characters/confirm` 和 `characters/discard`；确认请求只承载八个创作字段，后端重新生成正式人物编码并补齐初始状态，写入角色库后删除 Draft。
- 放弃操作只删除 `CHARACTERS` Draft，不写正式人物表；确认成功后前端刷新角色库，Dialog 底部提供“放弃”和“应用到角色库”操作。
- 验证结果：`CharacterGenerationServiceTest`、`CharacterDraftConfirmationHttpTest`、`NovelProjectControllerHttpTest` 及相关 UI 契约共 19 个用例通过。

### Task 46.13：角色旧 UI 使用 Element Plus（2026-08-31）

- 角色库卡片改用 Element Plus `el-card`，角色编辑抽屉关闭操作改用带 `Close` 图标的 `el-button`。
- 清除角色区域原生 `<button>` 和手写 `×` 关闭按钮，保留键盘回车打开角色编辑的可访问交互。
- `CharacterEditorElementPlusContractTest` 通过，`npm run build` 与 `git diff --check` 均通过。

### Task 46.14：人工新建角色回归（2026-08-31）

- 保留“＋ 新建角色”入口及原有保存流程；前端新增请求只提交人物创作字段，不再生成或提交 `characterCode`。
- 人工创建继续复用后端人物归一化流程，由后端生成编码、映射自然语言定位并设置默认状态。
- `CharacterCreationBoundaryContractTest` 与 `NovelProjectServiceTest` 覆盖人工创建入口、请求字段隔离和后端编码生成。

### Task 46.15：人物生成与确认后端测试（2026-08-31）

- 增加同名角色确认拒绝测试：与已有角色姓名相同（忽略首尾空格和大小写）时，不写正式角色、不删除 Draft，并返回非法参数错误。
- 回归覆盖 AI Generate 不写正式角色、Confirm 写入正式角色、后端生成编码、`ALIVE / ACTIVE / {}` 默认值、自然语言角色定位映射及人工创建。

### Task 46.16：人物 Prompt 边界测试（2026-08-31）

- 人物生成测试锁定 Prompt 不包含 `characterCode`、`MALE_LEAD`、`FEMALE_LEAD`、`ALIVE`、`ACTIVE` 和 `currentStateJson`。
- 模型仍只接收故事业务上下文和创作字段协议，后端字段保持在服务与持久化映射中。

### Task 46.17：人物前端回归与最终验证（2026-08-31）

- 前端契约覆盖 AI 入口、Draft 编辑和删除、放弃、确认后调用角色列表刷新，以及不生成 `characterCode`。
- 最终定向 Maven 回归共 33 个用例通过；`npm run build` 通过；`git diff --check` 通过。

### Task 46.18：人物运行时状态 JSON 边界保护（2026-08-31）

- `NovelProjectService` 在进入人物仓储前校验非空 `currentStateJson` 是否为合法 JSON；空值统一归一化为 `{}`，非法值返回参数错误。
- 新建人物不再写入自然语言“初始状态”，统一保存合法 JSON `{}`；普通人物编辑请求移除 `currentStateJson`、`lifeStatus` 和 `status` 字段，仅修改创作资料并保留已有运行时状态。
- 前端角色编辑表单及 `UpdateStoryCharacterRequest` 不再提交运行时状态字段，章节事实状态链路继续直接维护运行时状态。
- 验证结果：定向 Maven 回归共 37 个用例通过，覆盖 JSON 校验、状态保留、人工编辑 API 和人物 Prompt 契约；`npm run build` 与 `git diff --check` 通过。

### Task 46.19.1：正式角色仓储删除（2026-08-31）

- `novel-agent-domain` 的 `INovelProjectRepository` 新增 `deleteCharacter(projectCode, characterCode)`；`novel-agent-infrastructure` 的 `NovelProjectRepository` 先解析项目 ID，再校验人物存在并在事务中物理删除，不存在或删除影响行数为零时返回“人物不存在”参数错误。
- `IStoryCharacterDao` 与 app 的 `mybatis/mapper/story_character_mapper.xml` 新增按 `project_id + character_code` 删除 `story_character`，保留同项目其他人物和其他项目同编码人物。
- app 测试新增 `CharacterDeletionRepositoryTest`，复用 dev 数据源连接真实 MySQL，使用 UUID 隔离项目并通过事务回滚清理，打印删除过程与结果；同步补齐三个已有测试仓储实现的新接口方法。
- 验证结果：定向运行 `CharacterDeletionRepositoryTest`、`CharacterGenerationServiceTest`、`NovelProjectServiceTest`、`StoryBibleGenerationTest` 共 25 个用例通过，其中 4 个真实 MySQL 用例覆盖删除成功、重复删除、跨项目隔离及项目不存在；`git diff --check` 通过。

### Task 46.19.2：Infrastructure 删除实现核验（2026-08-31）

- 核验上一小节已实现的 `NovelProjectRepository.deleteCharacter`：通过 `projectCode` 查询项目 ID，再按 `projectId + characterCode` 查询人物；不存在时抛出“人物不存在”参数错误，存在时删除 `story_character`。
- `novel-agent-infrastructure` 中的 `IStoryCharacterDao` 与 app 的 `mybatis/mapper/story_character_mapper.xml` 已按项目和人物编码限定删除范围，本次无需调整业务代码。
- 验证结果：重新定向运行 `CharacterDeletionRepositoryTest`，4 个真实 MySQL 用例全部通过（删除成功、重复删除、跨项目隔离、项目不存在），测试数据通过事务回滚清理；`git diff --check` 通过。

### Task 46.19.3：DAO 删除条件核验（2026-08-31）

- 核验 `novel-agent-infrastructure` 的 `IStoryCharacterDao.deleteByProjectIdAndCharacterCode` 及 app 的 `mybatis/mapper/story_character_mapper.xml`：删除条件同时包含 `project_id = #{projectId}` 和 `character_code = #{characterCode}`，不按人物编码全局删除；已有实现满足要求，无需调整业务代码。
- 验证结果：定向重跑 `CharacterDeletionRepositoryTest` 的项目内删除与跨项目隔离两个真实 MySQL 用例，均通过；确认同项目其他人物和其他项目同编码人物保留，测试数据事务回滚清理；`git diff --check` 通过。

### Task 46.19.4：Service 正式角色删除（2026-08-31）

- `novel-agent-domain` 的 `INovelProjectService` 新增 `deleteCharacter(projectCode, characterCode)`；`NovelProjectService` 复用非空校验，拒绝项目编码和人物编码为 null、空字符串或纯空白，校验通过后调用 Repository 删除并原样传递异常。
- app 的 `NovelProjectServiceTest` 新增正常委托、两类编码非空校验及仓储异常传递测试，打印关键结果并确认非法参数不触发仓储删除。
- 验证结果：定向运行 `NovelProjectServiceTest`，22 个用例全部通过。

### Task 46.19.5：Controller 正式角色 DELETE API（2026-08-31）

- 按本次明确要求，在 `novel-agent-trigger` 的 `NovelProjectController` 使用 `@DeleteMapping` 新增 `DELETE /api/v1/novels/projects/{projectCode}/characters/{characterCode}`，调用 Service 删除，成功返回 `Response<Void>`，未新增 POST 删除入口。
- app 的 `NovelProjectControllerHttpTest` 验证 DELETE 请求成功、路径参数传递准确及空 data 响应，打印 HTTP 请求和响应；更新测试 Service 实现以支持新增接口。
- 验证结果：新增 HTTP 测试在实现前因 DELETE 返回 405 失败，实现后通过；`NovelProjectControllerHttpTest` 的 12 个用例通过，与 Service 定向回归合计 34 个用例全部通过；`git diff --check` 通过。

### Task 46.19.6：前端正式角色删除 API（2026-08-31）

- `novel-agent-web/src/api/project.ts` 新增 `deleteCharacter(projectCode, characterCode)`，调用正式 DELETE 路径，返回 `void`，复用统一响应及错误处理。
- 验证结果：`CharacterDeletionUiContractTest` 校验 HTTP 方法与双编码路径；独立内存 API 的浏览器验证实际收到 DELETE 请求，删除成功后收到角色列表刷新请求。

### Task 46.19.7：角色详情 Drawer 删除入口（2026-08-31）

- `novel-agent-web/src/views/SetupView.vue` 在已有角色 Drawer 底部新增 Element Plus danger 样式“删除角色”按钮，新建角色时隐藏；删除期间锁定编辑与保存操作，按钮区支持换行。
- 切换项目时立即清空旧人物列表、编辑目标并关闭 Drawer，防止新项目加载失败后残留旧人物造成跨项目误删。
- 验证结果：浏览器确认已有角色显示删除按钮、新建角色不显示；1440、1024、390 三种屏宽下页面与 Drawer 无横向溢出，新增项目切换清理契约测试通过。

### Task 46.19.8：Element Plus 删除确认与交互验证（2026-08-31）

- `SetupView.vue` 使用 `ElMessageBox.confirm` 展示角色原始姓名及“此操作无法撤销”，确认后才调用删除 API；取消或关闭确认框不发删除请求，成功后移除本地角色、关闭 Drawer 并刷新列表，失败保留编辑内容并由统一拦截器提示。
- 新增 app 下的 `CharacterDeletionUiContractTest` 与 `src/test/resources/web/character-delete-preview.mjs`，后者提供独立 Vite 预览和内存 API，支持成功、失败场景，不连接真实数据库或模型。
- 验证结果：6 个定向前端契约测试通过；浏览器验证取消、关闭确认框、成功删除并保留其他人物、模拟失败保留未保存编辑及新建入口；`npm run build`、预览脚本语法检查和 `git diff --check` 通过，构建仍有大于 500 kB 的分包提示。独立审查发现的跨项目旧列表问题已修复并复核通过；临时预览服务已停止。

### Task 46.19.9：删除图标与成功刷新顺序（2026-08-31）

- `novel-agent-web/src/views/SetupView.vue` 使用 Element Plus danger 图标按钮、`Delete` 图标及 Tooltip 提供删除入口，保留“删除角色”无障碍标签和标准确认框。
- 删除成功后依次关闭 Drawer、清空当前选择编码 `editingCharacterCode`、重新加载角色列表，再展示成功消息；选择状态复用现有字段。
- 验证结果：前端行为测试校验刷新和成功提示顺序，契约测试校验图标入口；浏览器在 1440、1024、390 宽度下验证无横向溢出，并确认成功后详情关闭、列表刷新。

### Task 46.19.10：删除失败原因展示与界面保留（2026-08-31）

- `NovelProjectController` 的人物删除接口将业务异常转换为包含原始 `code/info` 的响应；`novel-agent-web/src/api/http.ts` 对非 2xx 响应也优先展示后端 `info`。
- 删除请求失败时保留本地人物列表、当前选择及未保存编辑；请求进行中不提前移除人物，列表刷新失败时不显示成功消息。
- 验证结果：HTTP 测试验证引用拒绝原因返回，前端行为测试验证失败/请求进行中/刷新失败路径；浏览器使用内存 API 的 409 响应确认拒绝原因显示且编辑内容保留。

### Task 46.19.11：强引用拒绝与故事数据保护（2026-08-31）

- `NovelProjectRepository` 通过 `IStoryFactDao` 检查项目内以人物编码为主体的正式人物状态事实，有引用时明确拒绝删除；数据库完整性约束拒绝也转换为明确的业务原因。删除操作不级联章节事实、人物状态、章节计划或正文。
- `IStoryCharacterDao`、`IStoryFactDao` 及对应 MyBatis Mapper 提供人物行锁和引用当前读；`ChapterPersistRepository` 在写入事实前按编码顺序锁定并校验人物，与删除使用同一行锁，人物已不存在时拒绝写入悬空状态事实。
- 验证结果：真实 MySQL 测试确认引用拒绝时人物状态、事实、计划和正文全部保留；交错事务测试确认删除事务旧快照不漏掉后来提交的引用，章节保存测试确认已删除人物不会产生悬空事实，审查复核通过。

### Task 46.19.12：人物删除定向回归（2026-08-31）

- app 测试补齐存在人物成功、不存在人物失败、项目不匹配、引用保护、HTTP DELETE 成功及拒绝原因；`src/test/resources/web/character-deletion.test.mjs` 执行实际前端删除函数、API 和错误拦截代码，验证取消、重复点击、失败保留、项目切换及刷新顺序。
- `ChapterPersistRepositoryTest` 复用 dev 环境数据源；仓储测试使用隔离数据和事务回滚，交错事务提交的 UUID 数据在 finally 中清理。测试仅验证数据库，不调用模型。
- 验证结果：定向 Maven 测试 `CharacterDeletionRepositoryTest`、`ChapterPersistRepositoryTest`、`NovelProjectControllerHttpTest`、`NovelProjectServiceTest`、`CharacterDeletionUiContractTest`、`CharacterEditorElementPlusContractTest` 共 55 个用例通过；`node --test novel-agent-app/src/test/resources/web/character-deletion.test.mjs` 的 9 个行为测试通过；`npm run build` 和 `git diff --check` 通过，保留构建的大包提示。临时内存预览服务已停止。

### Task 47.1：前端按钮视觉规格收口（2026-08-31）

- `SetupView.vue`（Setup / Character）、`OutlineView.vue` 和 `GenerateView.vue` 的主按钮保持 Element Plus 正常尺寸，移除页面局部按钮的字号、高度和内边距覆盖及未使用的旧大纲卡片样式；Outline、Generate 的局部操作统一使用 `text`，Generate 的目录与正文入口改为 Element Plus 按钮。
- 规则、层级、角色和人物草稿删除操作统一增加低视觉权重的 `local-delete-button`；主题样式避免覆盖 `text`、`link`、`plain` 变体，并保留局部删除操作悬停/聚焦时的危险色提示。
- 验证结果：定向 Maven `VisualButtonSpecContractTest` 1 个用例通过；`npm run build` 通过（仅保留既有的大于 500 kB 分包提示）；Playwright/Chrome 在 1440、1024、390 三种宽度核对四个路由，按钮可见高度均为 32px 且无横向溢出；`git diff --check` 通过。

### Task 47.2：全局业务按钮 Element Plus 迁移（2026-08-31）

- `novel-agent-web/src/App.vue` 和 `novel-agent-web/src/views/ReadView.vue` 的工作台导航、章节目录、搜索清空、章节切换、删除、刷新和正文保存操作全部改为 Element Plus `el-button`；局部操作使用 `text`，删除入口使用低视觉权重的 `local-delete-button`。
- 清理 Read 页对应的原生按钮 reset 样式，保留目录行、章节行和工具栏的布局样式；视觉契约测试扩展覆盖 App 与 Read 页，防止原生业务 `<button>` 回归。
- 验证结果：`npm run build` 通过；定向 `VisualButtonSpecContractTest` 与 `git diff --check` 通过；`novel-agent-web/src` 全局检索未发现原生 `<button>`。

### Task 47.3：Story Bible 长文本去框（2026-08-31）

- `novel-agent-web/src/views/SetupView.vue` 将世界背景、特殊体系、创作方向和写作风格的长文本样式直接收口到 `.el-textarea__inner`，移除完整矩形边界，保留透明背景、autosize、不可拖拽、无内部滚动和弱底线焦点状态。
- 验证结果：已确认七个 Story Bible 长文本字段继续使用 `autosize` 与 `resize="none"`，样式不再依赖 `.el-textarea__wrapper`。

### Task 47.4：Hard Rules 输入去 Card/Form 感（2026-08-31）

- `novel-agent-web/src/views/SetupView.vue` 将规则与成长层级输入改为透明单底线样式，去掉完整矩形边框和 focus 阴影；编号保留，删除入口保持低权重 danger text，`＋ 添加规则` 继续使用 text button。
- 验证结果：规则输入的 hover/focus 均只更新底线，未增加局部新增按钮的字号、高度或填充。

### Task 47.5：Generate 外层大框去除（2026-08-31）

- `novel-agent-web/src/views/GenerateView.vue` 去除 `generation-panel` 与 `results-panel` 的外围 border、圆角和白色卡片底，header 改为透明背景，并以留白和底部分隔线组织生成区与结果区；页面主容器改为占满可用宽度并设置 `min-width: 0`。
- 生成表单内的章节输入控件保持 Element Plus 自身边界，未改变输入交互样式。
- 验证结果：定向 `CreationCanvasVisualContractTest` 的 3 个用例、`VisualButtonSpecContractTest` 的 1 个用例全部通过；`npm run build` 通过，外层不再声明 Card 式边框或灰色 header 背景。

### Task 47.6：Generate 表单紧凑化（2026-08-31）

- `novel-agent-web/src/views/GenerateView.vue` 将单章章节号整理为字段组，Input Number 与批量模式共用 104px 紧凑宽度，生成按钮继续使用 Element Plus 默认高度、字号和内边距；移动端生成按钮占满内容列，章节字段仍保持紧凑宽度。
- 验证结果：由 `CreationCanvasVisualContractTest` 的 Generate 表单尺寸用例覆盖，确认 Input Number 紧凑且未对按钮增加独立字号、高度或 padding。

### Task 47.7：Generate 结果区正文预览去框（2026-08-31）

- `novel-agent-web/src/views/GenerateView.vue` 移除 `.content-preview` 的完整矩形边框，改为浅背景配弱左侧标识；Stage Tag 与 Status Tag 保持原有状态元素边界。
- 新增表单尺寸和正文预览边界契约，分别锁定紧凑 Input Number、标准按钮尺寸及无完整正文框。
- 验证结果：`CreationCanvasVisualContractTest` 5 个用例、`VisualButtonSpecContractTest` 1 个用例全部通过；`npm run build` 与 `git diff --check` 通过。

### Task 47.8：建立轻量 UI 尺寸规则（2026-08-31）

- `novel-agent-web/src/assets/theme.css` 增加页面标题、Section 标题、正文、辅助文字、按钮、输入、Tag 及 32/24/40px 控件高度变量；Element Plus 按钮、输入、Textarea、Select 和 Tag 统一复用现有全局规格，没有新增自定义组件。
- Setup、Character 和 Generate 的主要标题与生成字段标签改用全局字号变量；Input Number 与普通按钮均回归 Element Plus 标准 32px 高度。
- 验证结果：`CreationCanvasVisualContractTest` 的全局尺寸契约通过，前端 `npm run build` 通过。

### Task 47.9：清理 Element Plus 深层覆盖（2026-08-31）

- 修正全局主题对 textarea 的 DOM 命中，从失效的 `.el-textarea__wrapper` 改为实际 `.el-textarea__inner`，并用 inset 阴影保持标准输入边界，避免实际边框额外撑高控件。
- `SetupView.vue` 的 Character 表单同步改用 `.el-textarea__inner` 及 `:focus`；全局源码检索不再存在 `.el-textarea__wrapper`，其余深层覆盖均命中实际 Element Plus 节点。
- 验证结果：`CreationCanvasVisualContractTest` 与 `VisualButtonSpecContractTest` 共 7 个用例通过；深层覆盖和原生业务 `<button>` 检索通过，`git diff --check` 通过。

### Task 47.10：创作页面响应式回归（2026-08-31）

- Generate 移动端保留章节 Input Number 的 104px 紧凑宽度，仅生成按钮占满内容列；单章和批量模式共用同一 32px 控件高度体系。
- 使用本地临时空数据在 1440、1024、768、390 宽度检查 Setup、Character、Outline、Generate 四个路由：页面 scrollWidth 与视口一致，无横向溢出；Generate 单章/批量按钮与 Input Number 高度一致，Story Bible textarea 实际呈 autosize、透明背景、无内部滚动和弱底线。
- 验证结果：上述四档视口浏览器回归通过，`npm run build` 通过。

### Task 47.11：Textarea 与轻量控件最终收口（2026-08-31）

- Story Bible AI 生成/调整输入由固定 `rows=5` 改为 `autosize minRows=3` 并禁用 resize；Generate 人工返修输入增加 `resize="none"`，保留 `maxRows=5` 控制审核区高度。
- Setup 人物表单与 Outline 各类创作摘要统一改用 autosize + resize none；可选的短要求输入使用 autosize，并仅保留必要的 maxRows 限制。
- `theme.css` 删除重复的 `--surface3`、`--border2` 声明；角色搜索栏实测为独立 toolbar 控件，40px 高度与上下文合理，保留 `size="large"`。
- 验证结果：`CreationCanvasVisualContractTest` 7 个用例通过，覆盖 28 个 textarea 模板；1440、1024、768、390 四档快速视觉回归通过；`npm run build` 与 `git diff --check` 通过。

### Task 48.1：删除正文流程中的二次 PLAN（2026-09-01）

- `ChapterService` 将正文主链路改为 `LOAD_CONTEXT → DRAFT → REVIEW`；删除 `PlanChapterNode`、`PLAN_SYSTEM_PROMPT`、`ChapterGraphKeys.PLAN`、`ChapterGraphState.plan()` 及 checkpoint 中的 PLAN 类型映射。
- DRAFT、REVIEW、REVISE 改为直接使用 `ChapterContextAggregate` 中已确认的 `ChapterOutlineEntity`，正文生成不再调用模型二次规划；同步删除无生产用途的 `ChapterPlanVO` 及其测试和真实模型用例。
- 更新章节流程文档及 State、Prompt、Checkpoint 定向契约测试；可空 State 通道改为无默认值的 last-write-wins，保证 LangGraph4j 能正常初始化图状态。
- 验证结果：生产代码编译通过；`ChapterNodeTest`、`ChapterGraphStateTest`、`ChapterCheckpointStateCodecTest`、`PromptBoundaryContractTest`、`ChapterOutlineModelTest` 共 24 个定向用例通过，`git diff --check` 通过。`ChapterGraphCheckpointResumeTest` 与 `ChapterMysqlCheckpointSaverTest` 因本机 MySQL `127.0.0.1:3306` 未启动未完成。

### Task 48.2：正文直接消费已确认 ChapterPlan（2026-09-01）

- 保持 `ChapterContextLoader` 通过 `ContextRepository` 从 `chapter_plan` 加载当前章节计划，并由 `ChapterService.LOAD_CONTEXT` 统一校验仅允许 `READY` 状态进入正文生成；`DRAFT` 直接将上下文中的计划标题和摘要传给模型。
- 在 `ChapterContextAggregateTest` 补充无计划、`PLANNED`、`READY`、`COMPLETED` 四种准入场景；在 `ChapterNodeTest` 捕获 DRAFT 实际模型输入，验证标题和摘要来自已加载章节计划；`ContextRepositoryTest` 补充 Loader 映射后的摘要、状态和 READY 校验断言。
- 验证结果：`ChapterContextAggregateTest`、`ChapterNodeTest`、`ChapterGraphStateTest`、`ChapterCheckpointStateCodecTest`、`PromptBoundaryContractTest`、`ChapterOutlineModelTest` 共 33 个定向用例通过；真实 MySQL `ContextRepositoryTest` 通过；`git diff --check` 通过。

### Task 48.3：统一 ChapterPlan 领域命名（2026-09-01）

- 将章节领域实体 `ChapterOutlineEntity` 重命名为 `ChapterPlanEntity`，`ChapterContextAggregate.chapterOutline` 重命名为 `chapterPlan`。
- 将 `IContextRepository`、`ContextRepository` 和 `ChapterContextLoader` 的 `loadChapterOutline()` 接口链路重命名为 `loadChapterPlan()`，同步更新正文节点、测试和模型测试类名；规划层 `ChapterOutlineVO` 保持不变。
- 未调整章节生成流程、状态校验或 Prompt 行为；验证结果：相关定向测试共 27 个通过，`git diff --check` 通过，旧实体/字段/方法引用扫描为空。

### Task 48.4：Generate 页接入 ChapterPlan 查询（2026-09-01）

- `GenerateView.vue` 进入页面时通过既有 `listChapterPlans` 查询项目章节计划，并按当前章节号同步刷新；展示计划状态、title 和 summary，不存在时显示“不存在”。
- 使用 Element Plus `el-tag` 展示 `PLANNED`、`READY`、`COMPLETED` 状态；未新增生成按钮逻辑，保留原有提交、禁用和批量生成行为。
- 验证结果：前端 `npm run build` 通过；`CreationCanvasVisualContractTest`、`GenerateViewChapterPlanContractTest`、`GenerateViewHumanRevisionInstructionContractTest` 共 9 个定向用例通过；浏览器三宽度检查受项目加载接口 502 影响仅完成可达项目页，Generate 页被路由守卫阻断；`git diff --check` 通过。

### Task 48.5：Generate 页增加 ChapterPlan AI 生成（2026-09-01）

- `GenerateView.vue` 接入既有 `generateChapterPlan`，增加可选补充要求输入和“生成计划”操作；AI 返回结果仅保留 `title/summary` 作为本地 Draft Preview。
- Draft Preview 只提供 title、summary 两个可编辑字段，不展示确认或落库操作；章节号变化时清除旧预览，避免跨章节复用草稿。
- `planning.ts` 将章节计划生成接口的返回类型明确为 `PlanningDraftResponse<ChapterPlan>`，新增契约测试覆盖请求接入、Draft Preview 字段边界和未确认落库约束。
- 验证结果：前端 `npm run build` 通过；`CreationCanvasVisualContractTest`、`GenerateViewChapterPlanContractTest`、`GenerateViewHumanRevisionInstructionContractTest` 共 9 个定向用例通过；`git diff --check` 通过。

### Task 48.6：Generate 页增加 ChapterPlan Confirm（2026-09-01）

- `GenerateView.vue` 接入 `confirmChapterPlan`，确认时提交当前 Draft 的 `draftId/title/summary`；确认成功后清理本地 Draft 并自动重新加载当前章节 ChapterPlan，由服务端返回 `READY` 状态。
- stale Draft 或大纲结构变化产生的 `ApiBusinessError` 保留拦截器提供的业务提示；仅对非业务错误显示通用确认失败提示，避免覆盖“草稿不存在或已过期”“大纲结构已变化”等具体信息。
- 更新 Generate 页契约测试，验证确认请求字段、成功刷新、READY 提示和未暴露其他 Draft 字段；三档视口检查的可达页面无横向溢出，目标路由因项目加载接口不可达被路由守卫拦截。
- 验证结果：前端 `npm run build` 通过；相关 9 个定向用例通过；`git diff --check` 通过。

### Task 48.7：正文生成按钮增加前置状态控制（2026-09-01）

- `GenerateView.vue` 将单章正文生成按钮限制为当前 ChapterPlan 状态为 `READY` 且计划加载完成时可用；不存在、`PLANNED`、加载中或加载失败时禁用。
- 当前章 ChapterPlan 为 `COMPLETED` 时隐藏普通生成操作，改为“查看正文”并跳转当前章节阅读页；批量生成保持原有范围操作不变。
- 切换章节时先清空旧计划，避免在新章节计划加载期间误触发正文生成；扩展 Generate 页契约测试覆盖四种计划状态和正文按钮分支。
- 验证结果：前端 `npm run build` 通过；`CreationCanvasVisualContractTest`、`GenerateViewChapterPlanContractTest`、`GenerateViewHumanRevisionInstructionContractTest` 共 10 个定向用例通过；`git diff --check` 通过。

### Task 48.8：Outline → Generate 章节跳转闭环（2026-09-01）

- `OutlineView.vue` 的 ARC“去生成”只读取当前 ARC 的 `startChapter`，通过 Generate 路由的 `chapter` 查询参数传递章节号；未引入 ChapterPlan 查询、状态或展示。
- `GenerateView.vue` 读取 `route.query.chapter`，对有效正整数自动初始化当前章节号，随后沿用既有 ChapterPlan 加载与正文生成控制。
- 更新 Outline/Generate 契约测试，验证 ARC 章节号传递、Generate 自动选章及 Outline 无 ChapterPlan UI；验证结果：前端 `npm run build` 通过，相关 17 个定向用例通过，`git diff --check` 通过。

### 正文 Prompt 内部存储格式清理（2026-09-01）

- 新增 `ChapterPromptFormatter`，将 DRAFT、REVIEW、REVISE Prompt 中的人物状态、生命状态、硬规则、力量体系和历史摘要统一渲染为业务文本、中文状态和列表；人物 `characterCode` 不再进入模型输入。
- EXTRACT Prompt 同步将 `currentStateJson` 渲染为自然语言；仅调整 Prompt 组装方式，未修改实体、DAO 或 JSON 存储字段。
- 验证结果：`ChapterNodeTest`、`ExtractFactsSemanticPromptContractTest` 共 20 个定向用例通过；覆盖存储格式不直出、业务文本渲染及 JSON 数组列表化；`git diff --check` 通过。

### Task 48.10：正文手动修改后标记派生数据失效（2026-09-01）

- `NovelProjectRepository.overwriteChapterContent()` 在同一事务中将正文状态改为 `DIRTY`，保留正文内容更新，标记对应 `story_summary` 为 `STALE`，并删除当前章 `story_fact`。
- `ContextRepository`、章节摘要 Mapper、章节正文近期查询和规划事实查询均排除 `DIRTY` 章节，后续章节不会读取该章正文、摘要或事实作为确认历史；未增加人物状态重建。
- 更新数据库脚本状态说明及仓储集成测试，覆盖 DIRTY 章节历史隔离和覆写后的派生数据清理。验证结果：`ContextRepositoryTest`、`PlanningRepositoryTest` 共 7 个定向用例通过；`ChapterPersistRepositoryTest#shouldInvalidateDerivedDataAfterManualChapterOverwrite` 通过；`git diff --check` 通过。旧 `NovelProjectRepositoryTest` 的既有根 ARC 夹具仍受数据库 `ck_outline_node_parent` 约束影响，未作为本次逻辑验证依据。

### Task 48.11：重新同步正文派生数据（2026-09-01）

- `ChapterService.resyncChapterDerivedData()` 读取当前 `DIRTY` 正文，直接调用现有 `ExtractFactsNode`，仅执行 `EXTRACT → PERSIST_DERIVED_DATA`，不经过 DRAFT/REVIEW。
- `ChapterPersistRepository.persistDerivedData()` 在事务中替换当前章 summary 与 facts，按抽取结果更新人物 runtime state；全部写入成功后才将章节状态从 `DIRTY` 恢复为 `FINALIZED`，正文和已完成 ChapterPlan 保持不变。
- 新增项目章节 POST 同步接口及阅读页 `DIRTY` 状态下的“重新同步派生数据”操作，并补充仓储、服务、HTTP 与前端契约测试。验证结果：相关定向测试共 26 个用例通过，`MvpPersistenceContractTest` 2 个用例通过，前端 `npm run build` 通过。

### Task 48.12：限制中间章节删除（2026-09-01）

- `NovelProjectRepository.deleteChapter()` 删除前查询同一项目是否存在更大章节号的正文；存在时拒绝删除，MVP 仅允许删除最后一章。
- 新增 `IStoryChapterDao`/MyBatis 章节号计数查询；原有事实、摘要、正文级联删除顺序及其他删除逻辑保持不变。
- 验证结果：`ChapterDeletionRepositoryTest` 1 个用例、`MvpPersistenceContractTest` 2 个用例通过；`git diff --check` 通过。

### Task 48.13：删除最后一章时恢复 ChapterPlan 与项目进度（2026-09-01）

- 删除最后一章的正文、摘要和事实后，将对应 ChapterPlan 以条件更新从 `COMPLETED` 恢复为 `READY`。
- 删除操作继续在同一事务内执行，并根据删除后的正文最大章节号回写 `currentChapterNumber`；原有中间章节删除校验保持不变。
- 验证结果：`ChapterDeletionRepositoryTest` 1 个用例、`MvpPersistenceContractTest` 2 个用例通过；覆盖派生数据删除、计划恢复和项目进度回退，`git diff --check` 通过。

### Task 48.14：删除章节后重建人物运行时状态（2026-09-01）

- 删除最后一章后，读取剩余 `FINALIZED` 正文中的 `CHARACTER_STATE` facts，按章节号和事实顺序从空状态重放，重建人物 `currentStateJson` 与 `lifeStatus`。
- 所有人物都会被覆盖更新；没有剩余确认事实的人物重置为空状态和默认 `ALIVE`，避免被删除章节的 runtime state 残留；重建与 48.13 删除流程共用同一事务。
- 新增独立人物状态重建集成测试和事实查询映射契约；验证结果：`ChapterDeletionRepositoryTest`、`ChapterDeletionRuntimeStateRepositoryTest`、`MvpPersistenceContractTest` 共 4 个定向用例通过，`git diff --check` 通过。

### Task 48.15：结构化模型空响应诊断与保护（2026-09-01）

- `ChapterModelPort` 的结构化调用先读取原始 `response.content()`，在 Jackson 转换前拦截 `null`、空串和空白串，返回明确的 `章节模型返回空内容`；非空内容再手动转换为 REVIEW 模型响应或其他结构化类型。
- 结构化失败日志记录模型响应类型、内容长度、是否为空，并在 Spring AI 响应可用时补充 `finishReason` 和 `usage`；不记录原始模型内容。
- `ReviewModelResponse` 已确认使用 `issues` 根字段，问题等级为 `String`；其 `toDomain()` 将问题列表映射为领域 `reviewIssueVOList`，并通过 `SeverityEnum.fromLabel()` 完成中文严重度转换。契约测试同时覆盖类别、描述、证据和三档严重度映射。
- 新增 REVIEW 空响应定向测试，覆盖 null、空串和空白串；验证结果：`ReviewSeverityModelContractTest` 12 个用例通过，`git diff --check` 通过。

### Task 48.15.4：章节模型瞬态重试增加递增退避（2026-09-01）

- `ChapterModelRetryExecutor` 对可重试的 `E0001` 失败按额外重试次数等待 `300ms → 1s → 2s`；达到三次额外重试上限时直接抛出，不再等待。
- 保持现有重试范围和 `retryCount` 语义不变，未引入 Resilience4j 或新的重试抽象；等待被线程中断时恢复中断标记并终止当前重试流程。
- 扩展重试执行器测试和 DRAFT 节点重试测试；验证结果：相关 6 个用例通过，退避实测约 `1322ms`（两次失败后成功）和 `3310ms`（三次失败后终止）。

### Task 48.16：Review Context 收口（2026-09-01）

- `ReviewChapterNode` 仅向 REVIEW Prompt 输出当前章纲、当前正文、硬规则、世界背景、力量体系、文风/目标字数、相关人物、上一章短摘要和相关确认 Facts；移除 8 条近期历史摘要、上一章完整正文及无关人物。
- `ContextRepository` 通过既有事实 Mapper 有界加载当前章之前的确认事实候选，转换为 `ChapterHistoryVO.confirmedFacts`；REVIEW 再按当前章纲、正文和上一章摘要做相关性筛选，人物最多 4 个、Facts 最多 8 条，不新增抽象层。
- 新增 REVIEW Prompt 边界测试和真实 MySQL 仓储测试，覆盖历史截断、上一章正文隔离、无关人物/Facts 过滤及 DIRTY 章节派生数据排除；验证结果：Prompt 定向用例 3 个、`ContextRepositoryTest` 2 个全部通过，`git diff --check` 通过。

### Task 48.17：结构化模型错误分类（2026-09-01）

- `ChapterModelPort` 将模型请求失败和空响应归为 `E0001`，非空但无法解析的结构化响应归为新增说明的 `E0007`；结构化错误日志继续只记录类型、长度、空值、finishReason 和 usage，不记录原始内容。
- `ChapterModelRetryExecutor` 保持 `E0001` 最多三次重试，并将 `E0007` 限制为最多一次重试；REVIEW/EXTRACT 的 `E0004/E0006` 业务校验错误原样抛出，不进入模型重试。
- 补充请求失败、空响应、非法结构化内容、结构化重试上限及 EXTRACT 业务校验分类测试；验证结果：相关定向用例 25 个全部通过，`git diff --check` 通过。

### Task 49.1：生成 Session API（2026-09-01）

- 新增 `POST /api/v1/novels/projects/{projectCode}/chapters/{chapterNumber}/generation-sessions`，由 `NovelChapterGenerationSessionController` 返回仅包含 `workflowId` 的 Session 响应；`IChapterService` 与 `ChapterService` 新增异步会话创建能力，复用原章节 workflow 执行逻辑并在后台启动，不等待工作流结束。
- 前端 `chapter.ts` 新增 `createGenerationSession`，`GenerateView.vue` 的单章生成改为创建 Session 并展示后台执行中的 workflowId；原同步 `generateChapter` 前端调用和请求类型已移除，批量生成链路保持不变。
- 新增 Session Controller 单元/HTTP 契约测试及前端入口契约测试；验证结果：Maven 定向测试 9 个通过，前端 `npm run build` 通过。

### Task 49.2：SSE 事件通道（2026-09-01）

- 新增 `GET /api/v1/novels/generation-sessions/{workflowId}/events` SSE 接口，使用轻量内存 Session 登记表保存事件历史和连接监听器，不引入独立 Event Bus；未知 workflowId 返回 404，终态事件推送后关闭连接。
- 章节 workflow 在 DRAFT、REVIEW、EXTRACT、PERSIST、人工审核和完成/失败边界发布第一版 11 种固定事件：`GENERATION_STARTED`、`DRAFT_STARTED`、`DRAFT_CHUNK`、`DRAFT_COMPLETED`、`REVIEW_STARTED`、`REVIEW_COMPLETED`、`HUMAN_REVIEW_REQUIRED`、`EXTRACT_STARTED`、`PERSIST_COMPLETED`、`GENERATION_COMPLETED`、`GENERATION_FAILED`。
- 新增 Session 登记表、SSE Controller 单元/HTTP 测试及事件类型契约测试；验证结果：相关 Maven 定向测试 11 个通过。

### Task 49.3：DraftChapterNode 改流式正文（2026-09-01）

- `DraftChapterNode` 改用现有 `IChapterModelPort.stream()`，每个 Flux chunk 追加到 `StringBuilder` 并通过会话回调发布 `DRAFT_CHUNK`；流结束后将完整正文写入 `ChapterGraphKeys.DRAFT`，再发布 `DRAFT_COMPLETED`。
- 保留现有章节模型瞬态重试和空正文校验；REVIEW 继续从 Graph State 读取完整正文字符串，不改变后续审稿输入协议。
- 更新 `ChapterNodeTest` 的模型流夹具并新增 chunk 累积/回调测试；验证结果：`ChapterNodeTest` 21 个定向用例通过。

### Task 49.4：工作流节点状态事件（2026-09-01）

- `ChapterService` 为 DRAFT、REVIEW、REVISE、EXTRACT、PERSIST 五个工作流节点统一增加开始/完成事件包装；REVISE 对外映射为业务语义事件 `REVISION_STARTED`、`REVISION_COMPLETED`，不直接暴露 LangGraph 节点名。
- 扩展 `ChapterGenerationEventType`，补齐 EXTRACT、PERSIST 的另一侧状态事件以及 REVISE 的业务语义事件；SSE 仍只发送固定协议枚举，不读取 `NodeOutput` 的内部节点名。
- 更新 SSE 事件类型契约测试；验证结果：章节节点、Session 登记表、SSE Controller 及 Session API 共 28 个 Maven 定向用例通过，`git diff --check` 通过。

### Task 49.5：生成 STOP（2026-09-01）

- 新增 `POST /api/v1/novels/generation-sessions/{workflowId}/commands`，仅接受 `{"command":"STOP"}`；前端章节 API 同步提供 `stopGenerationSession` 调用。
- Session 登记表在 DRAFT 流式阶段接受停止命令，释放当前 Reactor 订阅，标记工作流 `CANCELLED` 并发送 `GENERATION_CANCELLED`；取消异常不会转为 `GENERATION_FAILED`，工作流不会进入 PERSIST。
- DRAFT 已接收 chunk 不写入 Graph State 或章节持久化，只作为客户端临时预览；补充节点、登记表、SSE、HTTP Command 和前端 API 契约测试。验证结果：相关 Maven 定向用例 35 个通过，`git diff --check` 通过。

### Task 49.6：ACCEPT 当前正文（2026-09-01）

- 扩展 `POST /api/v1/novels/generation-sessions/{workflowId}/commands` 支持 `{"command":"ACCEPT"}`，仅在 `DRAFT_COMPLETED` 后接受，并在 Session 层保持重复命令幂等；前端章节 API 同步提供 `acceptGenerationSession` 调用。
- ACCEPT 会让 DRAFT 后直接进入 EXTRACT；REVIEW/REVISE 节点在执行前后检查 accept flag，链路中途接受时跳过剩余自动审核和改稿。若工作流已暂停在 HUMAN 检查点，则异步按 PASS 恢复并继续 EXTRACT、PERSIST。
- 补充 ACCEPT 命令 HTTP/单元契约、Session 完整正文门禁和前端 API 契约测试。验证结果：相关非数据库 Maven 定向用例 20 个通过，前端 `npm run build` 通过，`git diff --check` 通过。

### Task 49.7：Generate 页左右两栏（2026-09-01）

- `GenerateView.vue` 改为响应式生成工作台：桌面端左侧使用 `minmax(360px, 420px)`，右侧使用 `minmax(0, 1fr)`；左侧承载当前章节、ChapterPlan、补充要求、开始/停止操作和流程进度，右侧承载实时正文、Review Issues、最终状态及批量结果。
- 视口宽度小于 768px 时切换为上下布局，保留 Element Plus 控件与正文自适应编辑区域；单章生成期间提供 STOP 操作，停止后展示 `CANCELLED/已停止` 状态。
- 更新 Generate 页的响应式布局、章节计划和按钮契约测试。验证结果：相关 Maven 定向用例 12 个通过，前端 `npm run build` 通过，`git diff --check` 通过。

### Task 49.8：ChapterPlan UI 收缩（2026-09-01）

- `GenerateView.vue` 的常驻 ChapterPlan 区域收缩为“章节计划摘要”、状态和“编辑计划 / AI 生成/调整”入口，不再在正文生成页面长期展示编辑表单。
- 使用 Element Plus `el-drawer` 承载章节计划标题、摘要编辑、补充要求、AI Draft Preview 和确认操作；直接编辑复用已有 `updateChapterPlan` API，AI Draft 仍需确认后才替换当前计划。
- 更新 ChapterPlan 页面契约，验证摘要区不包含编辑表单且 Drawer 包含编辑与 AI 流程。验证结果：相关 Maven 定向用例 13 个通过，前端 `npm run build` 通过。

### Task 49.9：正文流式显示（2026-09-01）

- SSE 事件增加轻量 `{ type, content }` payload；`DRAFT_CHUNK` 从 `DraftChapterNode` 经 Session 登记表和 SSE Controller 携带正文片段，前端通过 EventSource 订阅并追加到 `streamingContent`。
- Generate 页正文容器监听滚动位置：用户在底部时自动跟随新 chunk，用户上滑后暂停自动滚动，并显示“回到底部”操作；切换章节、停止生成或页面卸载时关闭 SSE 连接。
- 补充 Session payload、HTTP SSE 和前端流式滚动契约测试。验证结果：相关 Maven 定向用例 24 个通过，前端 `npm run build` 通过。

### Task 49.10：流程 Timeline（2026-09-01）

- `GenerateView.vue` 将旧的 `completedStages` 标签列表改为业务语义 Timeline：固定展示“正文生成、自动审稿、事实整理、保存”，完成/进行中/待处理分别使用 `✓`、`●`、`○`。
- 前端将 SSE 的节点状态事件映射到 Timeline 状态；收到自动修改事件后按事件次数显示“自动修改 · 第 N 轮”，页面模板不渲染内部节点编码。
- 新增 Generate 页 Timeline 视觉契约测试。验证结果：前端 `npm run build` 通过，相关 Maven 定向测试 11 个通过，`git diff --check` 通过。

### Task 49.11：Review 人工控制 UI（2026-09-01）

- `GenerateView.vue` 在自动审稿流程中增加“接受当前正文 / 按建议修改 / 停止流程”控制；`接受当前正文` 仅在收到完整正文事件后启用，并调用既有 `ACCEPT` Session 命令。
- 收到 `HUMAN_REVIEW_REQUIRED` 后切换到人工审核区，显示“人工修改意见 / 通过 / 驳回重写 / 放弃”；`PASS`、人工返修和放弃继续使用人工审核恢复接口，不与 `ACCEPT` 混用。
- 补充 Review 控制语义契约测试。验证结果：前端 `npm run build` 通过，相关 Maven 定向测试 12 个通过，`git diff --check` 通过。

### Task 49.12：流程进度移动到右侧顶部（2026-09-01）

- 删除左栏的流程进度区，将 Timeline 移入右侧 `results-head`，与章节标题和“查看全部章节”同属顶部 Header。
- 桌面端 Timeline 改为横向业务流程链，并使用阶段之间的连接线；768px 以下切换为不产生横向滚动的纵向布局。

### Task 49.12.1：右侧 Header 重构（2026-09-01）

- 右侧 Header 改为显示“第 N 章”和当前 ChapterPlan 标题，移除“生成结果”及旧状态句；`查看全部章节` 保持在 Header 最右侧。
- 更新 Generate 页布局契约，验证结果：前端 `npm run build` 通过，相关 Maven 定向测试 9 个通过，`git diff --check` 通过。

### Task 49.12.2：Timeline 横向（2026-09-01）

- 仅调整 Generate 页 Timeline 的 DOM/CSS，沿用现有 `timelineStages` 状态逻辑，桌面端横向排列并以连接线表达流程顺序。
- 移除 Header 内完成数量展示；补齐 pending 灰、active 蓝、completed 绿、failed 红的状态样式，768px 以下保持无横向滚动的纵向布局。
- 更新 Timeline 视觉契约测试。验证结果：前端 `npm run build` 通过，相关 Maven 定向测试 9 个通过，`git diff --check` 通过。

### Task 49.12.3：生成按钮留在左边（2026-09-01）

- 保持左栏承载章节选择、ChapterPlan 和生成控制；“生成本章 / 停止生成 / 查看正文”未随 Timeline 移动。
- 右侧 Header 仅承载章节信息、流程 Timeline 和“查看全部章节”导航；新增布局契约测试锁定左右职责。
- 验证结果：相关 Maven 定向测试 9 个通过，`git diff --check` 通过。

### 右侧结果流结构收敛（2026-09-01）

- 删除单独的“最终状态”大 Section；完成状态在右侧 Header 显示“✓ 已完成”，完成正文下保留“查看正文”。
- Review Issues 仅在 Timeline 已进入自动审稿阶段后显示；Review Controls 和人工审核继续按会话状态按需显示。
- 提取事实并入正文下的轻量详情，Review/控制/人工审核去除卡片式背景，保持结果流的连续阅读结构。
- 更新结果区顺序契约。验证结果：前端 `npm run build` 通过，相关 Maven 定向测试 9 个通过，`git diff --check` 通过。

### Review 与终态展示精简（2026-09-01）

- `Review Issues` 和 `Review 控制` 改为用户可见中文“审稿问题”和“审稿控制”。
- Review 区改为仅在 `reviewStarted || result?.reviewIssues?.length || humanReviewRequired` 成立时渲染，未进入审稿时不再显示空白区域。
- 右侧 Header 仅显示 `COMPLETED`、`FAILED`、`CANCELLED`、`ABORTED` 等终态；生成中和等待人工审核不再重复显示状态徽标，正文区标题保持聚焦内容。
- 验证结果：前端 `npm run build` 通过，相关 Maven 定向测试 12 个通过，`git diff --check` 通过。

### Task 50.1：EXTRACT 重构为章节压缩节点（2026-09-01）

- `novel-agent-domain/.../SystemPrompt.java` 将 EXTRACT 的职责改为章节压缩：仅保留下一章不知道就可能写错剧情、人物状态或未解决问题的信息，并明确舍弃无关细节、猜测、计划和未实现意图。
- `ChapterExtractionResponse` 与 `ExtractFactsNode` 的模型交互和映射只保留章节摘要字段；不再要求、解析或校验 `subject / predicate / object / type` 原子事实，领域结果中的事实列表固定为空以兼容既有持久化边界。
- `docs/chapter-workflow.md` 和 `GenerateView.vue` 将 EXTRACT 的业务语义同步为“章节压缩”；历史 `story_fact` 表及读取边界暂不迁移。
- 验证结果：`ChapterNodeTest` 22 个、`ExtractFactsSemanticPromptContractTest` 2 个、`PromptBoundaryContractTest` 1 个通过，`npm run build` 通过，`git diff --check` 通过；`ChapterPersistRepositoryTest` 因本机 MySQL `127.0.0.1:13306` 连接被拒绝而未进入业务断言。

### Task 50.2：章节压缩节点命名统一（2026-09-01）

- 将章节工作流阶段、State key、人工路由和 SSE 事件统一命名为 `COMPRESSION`，同步后端节点 `CompressChapterNode`、领域结果 `ChapterMemoryVO` 和模型边界 `ChapterMemoryResponse`。
- 将 Graph State、章节生成结果、持久化端口/仓储、HTTP 响应、前端 Timeline、测试及当前工作流文档中的旧节点命名全部切换为章节记忆/章节压缩语义；未保留旧类名、旧阶段名或兼容别名。
- 历史 `story_fact` 读取与持久化边界继续使用既有事实值对象，但当前 COMPRESSION 模型不生成原子事实。
- 验证结果：相关 Maven 定向测试 42 个通过，`npm run build` 通过；活动代码、测试、前端和当前文档检索不到 `EXTRACT`、`ExtractFactsNode`、`ChapterExtractionVO` 等旧引用。

### Task 50.3：定义章节记忆模型输出 Schema（2026-09-01）

- 将 `ChapterMemoryResponse` 改为扁平结构，模型只返回 `shortSummary`、`keyEvents`、`characterStates`、`unresolved` 和 `endingHook`；人物状态项固定包含 `character` 与 `state`。
- 更新 `SystemPrompt.COMPRESSION_SYSTEM_PROMPT`、`CompressChapterNode` 映射和校验：数组缺省时归一化为空数组，人物状态必须包含人物与状态，模型结果继续转换为章节记忆领域结果。
- 更新 `ChapterMemoryPromptContractTest` 与 `ChapterNodeTest`，覆盖新 Schema、关键事件、人物状态和未解决问题的映射；当前持久化边界将关键事件、人物状态和未解决问题写入既有摘要字段。
- 验证结果：`ChapterNodeTest` 与 `ChapterMemoryPromptContractTest` 共 24 个通过，`PromptBoundaryContractTest` 1 个通过，`npm run build` 通过。

### Task 50.4：keyEvents 仅记录关键事件（2026-09-01）

- 在 `COMPRESSION_SYSTEM_PROMPT` 中明确 `keyEvents` 不是事件流水账，每条必须说明“谁、做了什么、产生了什么结果”。
- 增加关键事件与普通动作的正反例，并要求除非明显影响后续剧情、人物状态或未解决问题，否则舍弃普通动作、轻微伤痕和环境细节。
- 更新 `ChapterMemoryPromptContractTest` 与 `docs/chapter-workflow.md`，锁定该筛选标准。
- 验证结果：`ChapterNodeTest` 与 `ChapterMemoryPromptContractTest` 共 24 个通过，`git diff --check` 通过。

### Task 50.5：characterStates 仅记录持续状态（2026-09-01）

- 在 `COMPRESSION_SYSTEM_PROMPT` 中规定 `characterStates` 只描述跨章节仍有意义的状态，覆盖位置、目标、重大身体/心理状态、生存、身份、关系和关键物品持有状态。
- 增加轻微擦伤、出汗、暂时紧张、衣服脏了和鞋底破损等短暂细节的舍弃规则，仅在正文明确表明其会持续影响后续时允许保留。
- 更新 `ChapterMemoryPromptContractTest` 与 `docs/chapter-workflow.md`，锁定持续状态筛选标准。
- 验证结果：`ChapterNodeTest` 与 `ChapterMemoryPromptContractTest` 共 24 个通过，`git diff --check` 通过。

### Task 50.6：characterStates 收敛为固定字段（2026-09-01）

- 将 `characterStates` 的模型输出协议明确收敛为每项只能包含 `character` 与 `state`，持续状态统一使用自然语言写入 `state`。
- 在 `COMPRESSION_SYSTEM_PROMPT` 和 `docs/chapter-workflow.md` 中禁止拆分为 `field`、`predicate`、`lifeStatus`、`location`、`currentGoal` 等自由字段。
- 扩展 `ChapterMemoryPromptContractTest`，通过记录组件字段白名单验证 `CharacterState` 不会重新引入复杂事实结构。
- 验证结果：`ChapterNodeTest` 与 `ChapterMemoryPromptContractTest` 共 24 个通过，`git diff --check` 通过。

### Task 50.7：characterStates 人名精确映射（2026-09-01）

- `CompressChapterNode` 对模型返回的 `character` 仅使用角色姓名 `equals` 精确匹配，成功后转换为后端生成的 `characterCode` 与 `state`；不引入包含、前缀、别名 NLP 或模糊搜索。
- 单个人物状态匹配失败时记录 `warn` 并忽略该项，其他章节压缩内容继续保留，不因单条异常导致整章失败；角色编码缺失也按同一规则忽略。
- 扩展 `ChapterMemoryPromptContractTest` 与 `ChapterNodeTest`，验证有效姓名映射、部分状态丢弃和模型 `CharacterState` 仍只有 `character`、`state`。
- 验证结果：`ChapterMemoryPromptContractTest` 3 个、`ChapterNodeTest` 22 个、`PromptBoundaryContractTest` 1 个，共 26 个通过，`git diff --check` 通过。

### Task 50.8：shortSummary 作为核心压缩结果（2026-09-01）

- 在 `COMPRESSION_SYSTEM_PROMPT` 中明确 `shortSummary` 必须存在，并简洁概括本章主要推进和核心冲突结果。
- 明确 `shortSummary` 不复述全部正文，只能描述正文已发生或明确揭示的内容，不得写未来剧情、计划或猜测；未增加字符数硬性门禁。
- 更新 `ChapterMemoryPromptContractTest` 与 `docs/chapter-workflow.md`，锁定核心摘要职责。
- 验证结果：`ChapterMemoryPromptContractTest` 3 个、`ChapterNodeTest` 22 个、`PromptBoundaryContractTest` 1 个，共 26 个通过，`git diff --check` 通过。

### Task 50.9：unresolved 仅记录正文未解决问题（2026-09-01）

- 在 `COMPRESSION_SYSTEM_PROMPT` 中明确 `unresolved` 只保留正文已经提出、但本章结束时仍未解决的问题。
- 增加守钟人身份、残页来源、第十三座钟楼等保留示例，并排除成功预测、未来可能发生的事情和作者创作建议。
- 更新 `ChapterMemoryPromptContractTest` 与 `docs/chapter-workflow.md`，锁定未解决问题的来源和截止状态。
- 验证结果：`ChapterMemoryPromptContractTest` 3 个、`ChapterNodeTest` 22 个、`PromptBoundaryContractTest` 1 个，共 26 个通过，`git diff --check` 通过。

### Task 50.10：endingHook 独立保留（2026-09-01）

- 在 `COMPRESSION_SYSTEM_PROMPT` 中明确 `endingHook` 独立用于下一章衔接，只记录本章结尾已经出现的明确钩子。
- 增加戴兜帽人影示例；没有明确钩子时要求返回“无”，禁止返回 `null` 或补写正文未出现的悬念。
- 更新 `ChapterMemoryPromptContractTest` 与 `docs/chapter-workflow.md`，验证明确钩子和“无”标记均保持独立映射。
- 验证结果：`ChapterMemoryPromptContractTest` 4 个、`ChapterNodeTest` 22 个、`PromptBoundaryContractTest` 1 个，共 27 个通过，`git diff --check` 通过。

### Task 50.11：统一新的 Compression Prompt 核心原则（2026-09-01）

- 将 `COMPRESSION_SYSTEM_PROMPT` 的总纲明确为后续章节所需的短期记忆压缩，声明不是知识图谱抽取，只保留影响剧情连续性的信息。
- 要求模型区分关键剧情、持续人物状态、未解决问题和结尾钩子，并集中禁止琐碎动作、瞬时身体反应、无后续意义的场景细节和原子化过度拆分。
- 更新 `ChapterMemoryPromptContractTest` 与 `docs/chapter-workflow.md`，锁定新的 Compression Prompt 职责边界。
- 验证结果：`ChapterMemoryPromptContractTest` 4 个、`ChapterNodeTest` 22 个、`PromptBoundaryContractTest` 1 个，共 27 个通过，`git diff --check` 通过。

### Task 50.12：收敛 Compression User Prompt 输入（2026-09-01）

- `CompressChapterNode` 的 User Prompt 现在只注入当前章节号、角色姓名清单和当前章节完整正文；存在当前章节章纲时，仅附带标题与摘要，用于判断核心推进。
- 角色清单只使用角色姓名，不注入角色编码、人物持久化状态或其他后端字段；Prompt 不读取大量历史或全部旧 Facts。
- 更新 `ChapterMemoryPromptContractTest` 与 `docs/chapter-workflow.md`，锁定最小章节压缩输入边界。
- 验证结果：`ChapterMemoryPromptContractTest` 5 个、`ChapterNodeTest` 22 个、`PromptBoundaryContractTest` 1 个，共 28 个通过，`git diff --check` 通过。

### Task 50.13：删除 Compression Fact 模型协议（2026-09-02）

- 确认 `ChapterMemoryResponse` 顶层模型输出仅保留 `shortSummary`、`keyEvents`、`characterStates`、`unresolved` 和 `endingHook`，不再包含 `facts`、`subject`、`relation`、`object`、`type`。
- 确认 `resolveSubject`、`resolvePredicate`、`resolveObject`、`resolveFactType`、`validateCharacterStateFact` 和 `FactKey` 已从当前源码移除，Compression 不再执行 Fact 解析协议。
- 新增顶层 Schema 契约测试，防止旧 Fact 字段回流；历史事实值对象仍仅作为既有历史数据兼容边界，不属于 Compression 模型输出。
- 验证结果：`ChapterMemoryPromptContractTest` 5 个、`ChapterNodeTest` 22 个、`PromptBoundaryContractTest` 1 个，共 28 个通过，`git diff --check` 通过。

### Task 50.14-50.15：Fact 类型与历史事实模型引用审计（2026-09-02）

- 全局搜索确认 `FactTypeEnum` 仍被 `ExtractedFactVO`、相关事实筛选、章节持久化、上下文仓储和规划仓储使用，不是 unused enum。
- 全局搜索确认 `ExtractedFactVO` 仍被 REVIEW 的历史事实筛选、Planning 的相关事实选择、Context/Planning 仓储映射、章节事实持久化及现有章节接口使用，不只服务旧 EXTRACT。
- 当前不删除 `FactTypeEnum` 或 `ExtractedFactVO`；它们继续作为既有历史事实业务链路的兼容模型，且不属于 Compression 模型输出协议。
- 验证结果：全局引用审计完成，确认两者均存在生产代码调用方；`git diff --check` 通过。

## Task 50.16：`story_fact` 全部真实引用盘点（2026-09-02）

- 盘点口径：全局搜索 `story_fact`、`IStoryFactDao`、`StoryFactPO`、`ExtractedFactVO`、`FactTypeEnum`、旧 Fact 相关性筛选器、`findRecentConfirmedFacts`、旧 Fact 候选上限常量、旧事实写入方法、`deleteByProjectIdAndChapterNumber`，并补充追踪 `storyFactDao`、`queryCharacterStateReferenceIdForUpdate`、`queryRecentByProjectIdBeforeChapter`、`queryConfirmedCharacterStateFacts` 及 `ChapterMemoryVO.facts` 的实际调用。

### Domain

- `novel-agent-domain/src/main/java/cn/ninth/novel/domain/chapter/model/valobj/ExtractedFactVO.java:3,20,32`：历史事实值对象定义，承载 subject/predicate/object、`FactTypeEnum` 和来源章节；作为后续历史读取与旧事实持久化输入的类型载体，不是当前 COMPRESSION 模型输出协议。
- `novel-agent-domain/src/main/java/cn/ninth/novel/domain/chapter/model/valobj/enums/FactTypeEnum.java:14`：历史事实类型定义；被事实筛选和章节持久化逻辑读取其类型，枚举定义本身无数据库读写。
- `novel-agent-domain/src/main/java/cn/ninth/novel/domain/chapter/model/valobj/ChapterHistoryVO.java:25`：保存有界的 `confirmedFacts` 历史候选，属于读取上下文的数据载体。
- `novel-agent-domain/src/main/java/cn/ninth/novel/domain/chapter/service/agent/ReviewChapterNode.java:11,321-343`：读取 `ChapterHistoryVO.confirmedFacts`，按章节号和正文/摘要相关性筛选并格式化为 REVIEW Prompt；读取用途。
- `novel-agent-trigger/src/main/java/cn/ninth/novel/trigger/http/NovelChapterController.java:7,78-94`、`NovelChapterBatchController.java:8,74-90`：读取生成结果中的事实列表并转成接口展示文本；接口消费/展示用途，不直接读写数据库。

### Planning

- 旧 Fact 相关性筛选器：读取事实候选，按硬规则、上一章、人物状态和大纲文本筛选排序，最多返回 8 条；读取/相关性选择用途。
- `novel-agent-domain/src/main/java/cn/ninth/novel/domain/planning/adapter/repository/IPlanningRepository.java:3,41-47`：声明 `findRecentConfirmedFacts` 历史事实读取契约。
- `novel-agent-domain/src/main/java/cn/ninth/novel/domain/planning/service/PlanningService.java:3,29,36,663-666`：以 64 条候选上限读取候选，经旧 Fact 相关性筛选器处理后放入章节计划上下文和 Prompt；读取用途。
- `novel-agent-infrastructure/src/main/java/cn/ninth/novel/infrastructure/adapter/repository/PlanningRepository.java:3-4,14,24,48,512-552`：根据项目和章节号调用 `queryRecentByProjectIdBeforeChapter`，将 `StoryFactPO` 映射为 `ExtractedFactVO`；读取用途。未知 `factType` 仅保留三元组文本供筛选。

### Infrastructure

- `novel-agent-infrastructure/src/main/java/cn/ninth/novel/infrastructure/dao/IStoryFactDao.java:10-36`：Fact DAO 的全部接口用途如下：`insert` 为写入；`deleteByProjectIdAndChapterNumber` 为按章节删除；`queryCharacterStateReferenceIdForUpdate` 为删除人物前读取正式人物状态引用并加锁；`queryByProjectIdAndChapterNumber` 为按章节读取；`queryConfirmedCharacterStateFacts` 为读取已定稿人物状态事实；`queryRecentByProjectIdBeforeChapter` 为读取当前章节前的近期事实。
- `novel-agent-infrastructure/src/main/java/cn/ninth/novel/infrastructure/dao/po/StoryFactPO.java:7-20`：Fact 表持久化对象，作为数据库事实行的读写载体；自身不执行操作。
- `novel-agent-infrastructure/src/main/java/cn/ninth/novel/infrastructure/adapter/repository/ContextRepository.java:10-11,19,36,53,206-208,282-301`：以 REVIEW 专用 64 条候选上限读取历史事实并映射到 `ChapterHistoryVO.confirmedFacts`，供 REVIEW 使用；读取用途。
- `novel-agent-infrastructure/src/main/java/cn/ninth/novel/infrastructure/adapter/repository/ChapterPersistRepository.java:6-18,47,96,131,146-167,169-210`：历史上 `persist` 和 `persistDerivedData` 会删除本章旧事实，再把 `ExtractedFactVO` 转为 `StoryFactPO` 插入；同时读取 `FactTypeEnum.CHARACTER_STATE` 事实并合并人物运行时状态；删除 + 写入用途，人物状态同步属于旧事实维护用途。
- `novel-agent-infrastructure/src/main/java/cn/ninth/novel/infrastructure/adapter/repository/NovelProjectRepository.java:15,49,180,218,233,257`：删除人物前读取人物状态事实引用；人工覆盖章节和删除章节时删除该章事实；删除章节后读取剩余已确认人物状态事实重建角色运行时状态；读取 + 删除用途。
- `novel-agent-infrastructure/src/main/java/cn/ninth/novel/infrastructure/dao/IStoryChapterDao.java:12`、`IStorySummaryDao.java:20`：命中同名 `deleteByProjectIdAndChapterNumber`，但分别删除 `story_chapter` 和 `story_summary`，不是 Fact 引用，属于搜索误命中。

### Mapper / SQL

- `novel-agent-app/src/main/resources/mybatis/mapper/story_fact_mapper.xml:3-75`：Fact Mapper 的 SQL 逐项对应为：`insert` 写入 `story_fact`；`queryCharacterStateReferenceIdForUpdate` 读取人物状态引用；`deleteByProjectIdAndChapterNumber` 删除章节事实；`queryByProjectIdAndChapterNumber` 读取章节事实；`queryConfirmedCharacterStateFacts` 读取已定稿人物状态事实；`queryRecentByProjectIdBeforeChapter` 读取当前章节前、非 DIRTY 来源章节的近期事实。
- `novel-agent-app/src/main/resources/mybatis/mapper/story_fact_mapper.xml:4-14`：`resultMap` 将 `story_fact` 行映射为 `StoryFactPO`，属于读取映射；`story_fact` 表名在 INSERT/SELECT/DELETE 中均出现。
- `docs/sql/schema.sql:184-198`：定义 `story_fact` 表、近期/主体索引和项目/章节外键；仅是数据库结构用途，不属于运行时读写调用。
- `novel-agent-app/src/main/resources/mybatis/mapper/story_chapter_mapper.xml:36-38`、`story_summary_mapper.xml:61`：同名删除语句分别针对章节和摘要表，非 Fact 语义，属于搜索误命中。

### Test

- `novel-agent-app/src/test/java/cn/ninth/novel/infrastructure/dao/MvpPersistenceContractTest.java:32,70-73,143`：校验 `story_fact` 表、`IStoryFactDao`/Mapper 文件和方法契约存在；测试/结构契约用途。
- `novel-agent-app/src/test/java/cn/ninth/novel/infrastructure/dao/MvpMapperIntegrationTest.java:45,63`：注入 Fact DAO 作为 Mapper 组合测试的一部分，并在清理阶段直接删除项目事实；测试清理用途，测试主体未单独断言 Fact 读写。
- `novel-agent-app/src/test/java/cn/ninth/novel/infrastructure/dao/StoryChapterMapperIntegrationTest.java:99`：调用的是 `storyChapterDao.deleteByProjectIdAndChapterNumber`，只删除章节，不是 Fact；搜索误命中。
- `novel-agent-app/src/test/java/cn/ninth/novel/infrastructure/adapter/repository/ContextRepositoryTest.java:17,25,79,256,317`：写入 `StoryFactPO` 测试夹具，验证 `ContextRepository.loadHistory` 读取 `confirmedFacts`，并在清理阶段删除 `story_fact`；写入 + 读取 + 测试清理用途。
- `novel-agent-app/src/test/java/cn/ninth/novel/infrastructure/adapter/repository/PlanningRepositoryTest.java:8,12,57,78,443,453-457`：写入 `StoryFactPO`，验证 DIRTY 来源事实不会被 `findRecentConfirmedFacts` 读取，并清理事实；写入 + 读取 + 测试清理用途。
- `novel-agent-app/src/test/java/cn/ninth/novel/infrastructure/adapter/repository/ChapterPersistRepositoryTest.java:7-8,18,70,82,229-240,353-364,450,489-493,579-606`：使用 `ExtractedFactVO`/`FactTypeEnum` 作为 PERSIST 输入，验证事实写入、章节覆盖删除、DIRTY 派生数据重同步、非 `CHARACTER_STATE` 事实仍落表、悬空人物/非法生存状态/异常时不写入；写入 + 读取断言 + 删除断言 + 测试用途。
- `novel-agent-app/src/test/java/cn/ninth/novel/infrastructure/adapter/repository/NovelProjectRepositoryTest.java:13,20,56,160,168-191,289`：写入并读取事实夹具，验证人工覆盖章节会删除事实，并在清理阶段删除事实；写入 + 读取 + 删除 + 测试清理用途。
- `novel-agent-app/src/test/java/cn/ninth/novel/infrastructure/adapter/repository/ChapterDeletionRepositoryTest.java:8,14,47,90,179,195`：写入事实夹具，验证删除最后一章后事实为空，并清理项目事实；写入 + 删除结果读取 + 测试清理用途。
- `novel-agent-app/src/test/java/cn/ninth/novel/infrastructure/adapter/repository/ChapterDeletionRuntimeStateRepositoryTest.java:9,15,45,78,176-184,192`：写入两章人物状态事实，验证删除最后一章后只按剩余事实重建人物状态，并清理事实；写入 + 删除结果读取 + 测试清理用途。
- `novel-agent-app/src/test/java/cn/ninth/novel/infrastructure/adapter/repository/CharacterDeletionRepositoryTest.java:153,178,183,196`：直接 SQL 写入/读取/清理 `story_fact`，验证人物删除时的正式事实引用阻断和跨项目隔离；写入 + 读取 + 测试清理用途。
- 旧 Fact 相关性筛选测试：构造 `ExtractedFactVO`/`FactTypeEnum` 候选，验证历史边界、相关性、优先级和 8 条上限；纯测试/读取选择用途，无数据库写入。
- `novel-agent-app/src/test/java/cn/ninth/novel/domain/planning/service/ChapterPlanSingleGenerationTest.java:3-4,258-320`：测试替身通过 `findRecentConfirmedFacts` 返回事实候选，验证 Planning Prompt 的相关事实读取和筛选；纯测试/模拟读取用途。
- `novel-agent-app/src/test/java/cn/ninth/novel/domain/chapter/service/agent/ChapterNodeTest.java:12,16,453-458`：构造事实值对象和类型，作为 REVIEW/上下文 Prompt 测试夹具；纯测试用途。
- `novel-agent-app/src/test/java/cn/ninth/novel/trigger/http/NovelChapterControllerTest.java:8,115`、`NovelChapterControllerHttpTest.java:5,136`：构造生成结果中的 `ExtractedFactVO`，验证 HTTP 响应事实摘要展示；纯测试/接口读取用途。
- `novel-agent-app/src/test/java/cn/ninth/novel/domain/planning/service/ChapterPlanSingleChapterRegressionTest.java:240,294`：使用空的 legacy `ChapterMemoryVO.facts` 测试夹具，并在项目清理时删除 `story_fact`；测试兼容/清理用途，不验证事实业务写入。
- `novel-agent-app/src/test/java/cn/ninth/novel/domain/chapter/service/agent/PersistChapterNodeTest.java:68`：使用空的 legacy `ChapterMemoryVO.facts` 测试夹具；纯测试兼容用途。

### Docs

- `docs/database-design.md:37,87`：记录 `story_fact` 的表职责、章节定稿时“删除旧事实并写入本次事实”的设计说明；文档用途。
- `docs/chapter-workflow.md:97,104-105`：记录当前 COMPRESSION 不再生成原子事实，但历史 `story_fact` 仍由既有上下文和持久化边界兼容读取；文档用途。
- `docs/implementation-summary.md:41,122,124-125,593,760,930,1474,1485,1492,2180-2181,2314,2473,2480,2562-2564`：记录历史 Fact 模型、持久化、删除、兼容边界及此前审计结论；文档用途。
- 未发现其他 Docs 目录下的 `story_fact`/Fact 专用引用。

### 盘点结论

- 读取用途：`ContextRepository`、`PlanningRepository`、`ReviewChapterNode` 以及 `NovelProjectRepository` 的人物引用检查/状态重建；旧 Fact 相关性筛选器和 HTTP Controller 分别负责筛选和展示。
- 写入用途：历史 `ChapterPersistRepository.persist`/`persistDerivedData` 曾将旧 `ExtractedFactVO` 转为 `StoryFactPO` 写入 `story_fact`；测试中另有 DAO/SQL 夹具写入。
- 删除用途：PERSIST 重跑替换本章事实，人工覆盖章节清理事实，删除章节清理事实；测试还会为隔离数据直接清理整项目事实。
- 测试用途：DAO/Mapper 契约、真实 MySQL 上下文/Planning/PERSIST/章节删除/人物删除回归、相关性筛选、节点和 HTTP 映射测试均仍覆盖旧 Fact 边界。
- 当前 COMPRESSION 模型没有直接生成原子 Fact；但旧 Fact 的 Context、Planning、Review、PERSIST、章节/人物删除保护和接口展示引用仍然存在，因此本轮只盘点，不删除代码。
- 验证结果：全局搜索与方法级上下文核对完成；未运行测试（本轮无代码逻辑修改）；`git diff --check` 通过（仅有换行符转换提示）。

## Task 50.17：新增 ChapterMemory 查询能力（2026-09-02）

- 在 `IPlanningRepository` 与 `IContextRepository` 增加 `findRecentChapterMemories(projectCode, chapterNumber, limit)`；`PlanningRepository` 和 `ContextRepository` 复用现有 `story_summary.queryRecent`，读取当前章节之前最近 N 章，并沿用非 `STALE` 摘要与非 `DIRTY` 正文过滤。
- 新增 `ChapterMemoryVO` 的章节记忆业务字段 `chapterNumber`、`shortSummary`、`keyEvents`、`unresolved`、`endingHook`；`ChapterMemoryMapper` 将 `actual_result` 投影为关键事件列表，将 `unresolved_questions_json` 解析为业务列表，查询结果不携带数据库 JSON 字符串或持久化对象。
- 保留原有 `findRecentConfirmedFacts`、`loadHistory` 和 Fact 持久化链路不变；本轮只新增 Memory 查询入口，没有删除旧 Fact 代码。
- 在 `novel-agent-app/src/test` 下扩展 `PlanningRepositoryTest` 与 `ContextRepositoryTest`，验证章节边界、字段投影和 DIRTY 章节排除，并打印查询到的记忆章节、关键事件与未解决问题。
- 验证结果：`git diff --check` 通过；定向 Maven 测试命令已执行，但仓库基线在 `novel-agent-domain` 编译阶段因缺少既有 `CompressChapterNode` 类而中止，尚未进入本次两个仓储测试。

## Task 50.18：PlanningRepository 实现近期 ChapterMemory 查询（2026-09-02）

- `story_summary_mapper.xml` 的近期摘要查询明确限定 `chapter_number < 当前章`、`status IN ('GENERATED', 'VERIFIED')`，按章节号倒序并使用调用方 `LIMIT`；同时继续排除来源正文为 `DIRTY` 的摘要。
- `PlanningRepository.findRecentChapterMemories` 读取该查询结果并转换为 `ChapterMemoryVO`；`ChapterMemoryMapper` 支持数据库 JSON 数组、JSON 字符串和历史普通文本，统一输出 `List<String>`。
- Planning 集成测试增加 JSON `keyEvents` 映射、`unresolved` 映射及摘要标记 `STALE` 后不再返回的验证；领域层查询对象不接收 `actualResult`/`unresolvedQuestionsJson` 等持久化字段。
- 验证结果：`git diff --check` 通过；定向 Maven 测试仍被仓库基线缺少既有 `CompressChapterNode` 阻断，未进入测试执行阶段。

## Task 50.19：ChapterPlanContextVO 移除 relevantFacts（2026-09-02）

- `ChapterPlanContextVO` 将 `List<String> relevantFacts` 替换为明确的 `List<ChapterMemoryBriefVO> recentMemories`，继续保留近期记忆 5 条、人物 4 条、硬规则 5 条的边界与不可变性。
- 新增 `ChapterMemoryBriefVO`，只携带章节号、短摘要、关键事件、未解决问题和结尾钩子；PlanningService 从 Repository 的 `ChapterMemoryVO` 投影为该结构后再组装 ChapterPlan 上下文。
- ChapterPlan Prompt 的“相关事实”分区改为结构化“近期章节记忆”，按章节输出摘要、关键事件、未解决问题和结尾钩子；PlanningService 不再调用旧 Fact 相关性筛选器或读取近期 Fact 候选。
- 更新 `ChapterPlanContextVOTest` 与 `ChapterPlanSingleGenerationTest`，验证字段白名单、结构类型、数量预算、不可变性、Memory 查询上限和 Prompt 分区。
- 验证结果：`git diff --check` 通过；定向 Maven 测试被仓库基线缺少既有 `CompressChapterNode` 阻断，未进入测试执行阶段。

## Task 50.20：PlanningService 替换旧事实读取（2026-09-02）

- `PlanningService` 删除近期 Fact 候选读取、旧 Fact 相关性筛选及 `relevantFacts` 上下文组装，改为直接调用 `findRecentChapterMemories`。
- Repository 返回的 `ChapterMemoryVO` 在 PlanningService 边界投影为 `ChapterMemoryBriefVO`，直接构造 `ChapterPlanContextVO.recentMemories`；未新增记忆二次筛选器。
- 近期记忆窗口固定为当前章节之前最近 5 章，并在 Prompt 中按章节输出摘要、关键事件、未解决问题和结尾钩子。
- 更新 PlanningService 与 ChapterPlanContext 定向测试，验证 Memory 查询上限、结构化 Prompt 内容及旧事实调用不再进入单章规划流程。
- 验证结果：`git diff --check` 通过；定向 Maven 测试仍被仓库基线缺少既有 `CompressChapterNode` 阻断，未进入测试执行阶段。

## Task 50.21：删除旧 Fact 筛选器业务依赖（2026-09-02）

- 确认 PlanningService 已无 `relevantFactSelector`、旧候选数量常量、`ExtractedFactVO` 导入和 `relevantFacts` 构造链。
- 删除失去业务入口的旧 Fact 相关性筛选类及其专属测试；未迁移测试，因为近期 ChapterMemory 已直接成为章节规划上下文。
- 将 ContextRepository 中仍服务 REVIEW 历史上下文的事实候选上限改名为 `REVIEW_FACT_CANDIDATE_LIMIT`，保留原有 64 条查询行为。
- 全局搜索旧筛选器类名结果为零；旧 Fact 的 REVIEW、PERSIST、章节删除保护和接口展示链路仍按 50.16 盘点结果保留。
- 验证结果：`git diff --check` 通过；Planning 定向 Maven 测试仍被仓库基线缺少既有 `CompressChapterNode` 阻断，未进入测试执行阶段。

## Task 50.22：修改 ChapterPlan Prompt（2026-09-02）

- ChapterPlan Prompt 的记忆分区改为“近期剧情记忆”，按章节展示章节号、摘要、关键事件、未解决问题和结尾钩子。
- 同步调整 ChapterPlan 系统提示词，使用“近期剧情记忆”和“已发生剧情”的创作语义，不向模型暴露事实类型、主体、谓词、客体等三元组字段。
- 更新单章规划 Prompt 测试，验证新分区名称、结构化记忆内容以及 Fact 三元组字段名均不会出现在 Prompt 中。
- 验证结果：`git diff --check` 通过；Planning 定向 Maven 测试仍被仓库基线缺少既有 `CompressChapterNode` 阻断，未进入测试执行阶段。

## Task 50.23：IPlanningRepository 删除 Fact 接口（2026-09-02）

- 从 `IPlanningRepository` 删除 `findRecentConfirmedFacts` 及其 `ExtractedFactVO` 导入。
- 确认 `PlanningRepository` 是唯一生产实现，移除对应 `@Override` 方法、Fact DAO 注入和三元组转换逻辑；同步删除只验证该接口的旧 Planning 集成测试。
- 保留 `ContextRepository` 的 REVIEW 历史 Fact 查询及 PERSIST/删除保护等独立 Fact 链路，不改变其行为。
- 扩展规划仓储能力边界契约，约束接口和实现不再暴露旧 Fact 查询契约。
- 验证结果：`git diff --check` 通过；相关 Maven 定向测试仍被仓库基线缺少既有 `CompressChapterNode` 阻断，未进入测试执行阶段。

## Task 50.24：Persist Repository 统一接收 ChapterMemory（2026-09-02）

- 确认 `IChapterPersistRepository.persist` 与 `ChapterPersistRepository.persist` 均只保留 `projectCode`、`chapterNumber`、`content`、`ChapterMemoryVO` 四个参数。
- 全局核对写入节点、正文生成服务和持久化测试调用方，未发现 `ChapterExtractionVO` 参数或旧 `persist` 兼容 overload。
- 新增 `PersistChapterNodeTest` 的接口签名契约，并改用纯 `ChapterMemoryVO` 测试夹具，确保未来不会重新增加旧抽取结果或 Facts 参数。
- 验证结果：`git diff --check` 通过；相关 Maven 定向测试仍被仓库基线缺少既有 `CompressChapterNode` 阻断，未进入测试执行阶段。

## Task 50.25：删除 Persist Repository 的 Fact 表写入（2026-09-02）

- 从 `ChapterPersistRepository.persist` 与 `persistDerivedData` 移除事实表删除/插入步骤，不再注入 `IStoryFactDao`，并删除对应的旧事实 PO 映射方法。
- Persist 保留正文、章节记忆摘要、Character Runtime State、ChapterPlan 状态和项目进度的事务写入；Character Runtime State 更新逻辑不受影响。
- 更新真实 MySQL 持久化测试：确认正文保存、派生数据同步和人物运行时状态仍然有效，同时确认 Persist 不产生 `story_fact` 行。
- 同步修正 `docs/database-design.md` 与 `docs/chapter-workflow.md` 的当前 PERSIST 说明，明确历史 `story_fact` 仅保留兼容边界，不再由当前 Persist 写入。
- 验证结果：`git diff --check` 通过；PERSIST 定向 Maven 测试仍被仓库基线缺少既有 `CompressChapterNode` 阻断，未进入测试执行阶段。

## Task 50.26：章节删除逻辑移除 Fact 删除（2026-09-02）

- 从 `NovelProjectRepository.deleteChapter` 移除 `storyFactDao.deleteByProjectIdAndChapterNumber`，章节删除链只处理章节记忆、正文、ChapterPlan 状态、项目进度和人物运行时状态重建。
- 更新 `ChapterDeletionRepositoryTest` 与 `ChapterDeletionRuntimeStateRepositoryTest`，确认章节删除测试不再依赖被删除章节的 Fact 写入/删除，同时章节记忆/正文删除、计划恢复、项目进度回退和人物状态重建仍然有效。
- 同步更新 `docs/database-design.md`，明确章节删除不再因为历史表存在而删除 Fact。
- 验证结果：`git diff --check` 通过；章节删除定向 Maven 测试仍被仓库基线缺少既有 `CompressChapterNode` 阻断，未进入测试执行阶段。

## Task 50.27：正文重新同步逻辑移除 Fact 链路（2026-09-02）

- 从 `NovelProjectRepository.overwriteChapterContent` 移除正文人工修改后的 Fact 删除；人工修改只将正文标记为 `DIRTY` 并将章节记忆标记为 `STALE`。
- 保留 `ChapterService.resyncChapterDerivedData` 的 `COMPRESSION → persistDerivedData` 流程，由新的 `ChapterMemory` 完成摘要同步和人物运行时状态更新，当前流程不产生事实表写入。
- 更新真实 MySQL 仓储测试：确认人工修改不会删除历史 Fact，重新压缩后章节记忆恢复有效且事实表没有新增记录。
- 同步更新 `docs/database-design.md` 与 `docs/chapter-workflow.md`，明确人工修改后的同步链路只产生新的 ChapterMemory，不再删除或写入 Fact。
- 验证结果：`git diff --check` 通过；正文重新同步相关定向 Maven 测试仍被仓库基线缺少既有 `CompressChapterNode` 阻断，未进入测试执行阶段。

## Task 50.28：人物状态更新改用 ChapterMemory.characterStates（2026-09-02）

- 新增 `ChapterMemoryCharacterStateVO`，将 COMPRESSION 的人物姓名和自然语言持续状态作为 `ChapterMemoryVO.characterStates` 业务字段。
- `ChapterPersistRepository` 移除 `FactTypeEnum`、`ExtractedFactVO`、事实类型分组和生存状态事实校验；人物状态更新改为按项目角色姓名严格 exact match，再锁定 `characterCode` 并更新 `currentStateJson.summary`。
- 更新真实 MySQL 持久化测试，覆盖 ChapterMemory 人物状态写入、exact match 拒绝和重新同步后的人物状态更新；旧 Fact 不再参与 Persist 人物状态更新。
- 同步清理章节工作流审查文档中已不适用的 FactType 人物状态问题记录。
- 验证结果：`git diff --check` 通过；人物状态相关定向 Maven 测试仍被仓库基线缺少既有 `CompressChapterNode` 阻断，未进入测试执行阶段。

## Task 50.29：人物状态存储收敛为固定摘要（2026-09-02）

- `ChapterPersistRepository` 不再 read-modify-write 旧状态 JSON，人物状态写入固定为 `summary` 与 `_updatedChapter` 两个字段。
- 更新持久化测试，确认旧 `goal`/`location` 等自由 key 不会被带入新的 `currentStateJson`。
- 同步更新章节工作流、数据库设计和代码审查文档，明确人物状态不恢复自由 key 结构。
- 验证结果：`git diff --check` 通过；定向 Maven 测试仍被仓库基线缺少既有 `CompressChapterNode` 阻断，未进入测试执行阶段。

## Task 50.30：人物状态重建改用 ChapterMemory（2026-09-02）

- `story_summary` 新增 `character_states_json`，PERSIST 将 `ChapterMemory.characterStates` 持久化为记忆数据，Repository 映射时恢复为 `List<ChapterMemoryCharacterStateVO>`。
- 新增 `ChapterMemoryCharacterStateRebuilder`：按章节号升序读取全部有效 ChapterMemory，逐章按人物姓名 exact match 到 `characterCode`，后出现的状态覆盖先出现的状态，最终只写入 `summary` 与 `_updatedChapter`。
- 章节删除和正文重同步均复用 ChapterMemory 重建器，不再读取 `queryConfirmedCharacterStateFacts`，即使历史 `story_fact` 被删除也不会中断人物状态重建。
- 更新删除状态重建真实 MySQL 测试，覆盖多章记忆顺序覆盖、旧自由 key 清理和 `lifeStatus` 不由记忆摘要推断；同步更新 DAO/Mapper 契约与工作流文档。
- 验证结果：`git diff --check` 通过；人物状态相关定向 Maven 测试仍被仓库基线缺少既有 `CompressChapterNode` 阻断，未进入测试执行阶段。

## Task 50.31：ChapterHistoryVO 改用 Memory 语义（2026-09-02）

- `ChapterHistoryVO.recentSummaries` 改为 `List<ChapterMemoryVO> recentMemories`；ContextRepository 通过 ChapterMemoryMapper 将历史摘要转换为短摘要、关键事件、未解决问题和结尾钩子，不再把 ChapterSummaryVO 暴露给历史上下文。
- DraftChapterNode 与 ReviseChapterNode 的近期历史 Prompt 改为“近期剧情记忆”，只渲染 `shortSummary`、`keyEvents`、`unresolved` 和 `endingHook`；ReviewChapterNode 的上一章摘要查找同步读取 ChapterMemory。
- 保留 `PreviousChapterVO` 作为上一章正文衔接快照；REVIEW 的历史上下文迁移在后续 50.34 完成。
- 更新 ContextRepository、正文节点和相关测试夹具，验证历史记忆四类业务字段及旧摘要字段不再进入 DRAFT/REVISE Prompt。
- 验证结果：`git diff --check` 通过；相关 Maven 定向测试仍被仓库基线缺少既有 `CompressChapterNode` 阻断，未进入测试执行阶段。

## Task 50.32：ContextRepository 不再组装旧 Summary Fact 结构（2026-09-02）

- ContextRepository 的近期历史读取链统一为 `story_summary → ChapterMemoryMapper → ChapterMemoryVO`，不再生成 `ChapterSummaryVO`。
- 将 Persist、章节规划回归测试和章节接口测试中的临时 `ChapterSummaryVO/facts` 兼容夹具迁移为 `ChapterMemoryVO` 的 `shortSummary/keyEvents/unresolved/endingHook` 字段。
- Persist 写入历史存储时由 Repository 负责将 Memory 列表序列化为数据库 JSON；领域层不再持有旧 Summary 或 Fact 兼容字段。
- 完成全局搜索，`ChapterSummaryVO` 已无生产和测试引用并删除旧类；ContextRepository 返回的历史记忆只暴露 Memory 业务字段。
- 验证结果：`git diff --check` 通过；相关 Maven 定向测试仍被仓库基线缺少既有 `CompressChapterNode` 阻断，未进入测试执行阶段。

## Task 50.33：DRAFT Prompt 使用压缩记忆（2026-09-02）

- 确认 DRAFT/REVISE 的历史 Prompt 只渲染 ChapterMemory 的章节号、短摘要、关键事件、未解决问题和结尾钩子，并继续保留上一章完整正文用于叙事衔接。
- 删除旧 Summary 字段在 DRAFT 历史上下文中的展示，Prompt 不再向模型传递 Facts 三元组结构。
- 更新 ChapterNodeTest，验证 DRAFT 的近期剧情记忆结构和上一章正文边界。
- 验证结果：`git diff --check` 通过；定向 Maven 测试仍被仓库基线缺少既有 `CompressChapterNode` 阻断，未进入测试执行阶段。

## Task 50.34：REVIEW Prompt 使用压缩记忆（2026-09-02）

- REVIEW Prompt 改为使用当前章节章纲、故事设定、相关人物、上一章 ChapterMemory 的摘要/关键事件/未解决问题/结尾钩子和当前正文。
- 移除 `ChapterHistoryVO.confirmedFacts`、ContextRepository 的 Fact 候选查询以及 ReviewChapterNode 的 Fact 筛选和渲染链，REVIEW 不再消费旧 Fact 数据。
- 同步修正单章和批量章节 HTTP 响应，将 `chapterMemory` 列表改为压缩记忆关键事件（无关键事件时使用短摘要），Controller 不再读取 `ChapterMemoryVO.facts` 或依赖 `ExtractedFactVO`。
- 更新 ChapterNodeTest，验证 REVIEW 只保留上一章压缩记忆，不包含上一章完整正文、非相邻历史和 Facts 三元组字段。
- 验证结果：`git diff --check` 通过；定向 Maven 测试仍被仓库基线缺少既有 `CompressChapterNode` 阻断，未进入测试执行阶段。

## Task 50.35：删除 ExtractedFactVO（2026-09-02）

- 全局核对 Planning、Repository、Persist、Context 和测试，确认已无 `ExtractedFactVO` Java 类型依赖。
- 删除 `novel-agent-domain` 中的 `ExtractedFactVO`，同步移除 Planning 契约测试中仅用于禁止旧引用的字符串断言；HTTP Controller 已使用 ChapterMemory，不再依赖旧事实值对象。
- 验证结果：Java 源码搜索无 `ExtractedFactVO`；`git diff --check` 通过。

## Task 50.36：删除 FactTypeEnum（2026-09-02）

- 全局核对已无 `FactTypeEnum` Java 类型引用，删除该枚举，不保留 deprecated 兼容类。
- 当前历史 `story_fact.fact_type` 数据库字段仍作为旧事实存储字段保留，但不再由领域枚举建模或由当前章节生成链路消费。
- 验证结果：Java 源码搜索无 `FactTypeEnum`；定向 Maven 测试仍被仓库基线缺少既有 `CompressChapterNode` 阻断，未进入测试执行阶段。

## Task 50.37：删除 ChapterExtractionVO（2026-09-02）

- 确认当前工作树已无 `ChapterExtractionVO` 类型；章节压缩结果统一使用 `ChapterMemoryVO`。
- 确认 `ChapterGraphKeys` 已无 `EXTRACTION`，`ChapterGraphState` 已无 `extraction()`、Extraction State Schema 或相关读写链路。
- 验证结果：Java 源码搜索无 `ChapterExtractionVO`、`EXTRACTION` 和 `extraction()` 引用；`git diff --check` 通过。

## Task 50.38-50.40：删除 Fact DAO、PO 与 MyBatis Mapper（2026-09-02）

- 移除 `IStoryFactDao`、`StoryFactPO` 和 `story_fact_mapper.xml`；核对 MyBatis 通配扫描配置，没有额外的显式 Mapper 注册需要清理。
- 移除 `NovelProjectRepository` 对人物状态 Fact 引用保护的 DAO 调用，人物状态来源统一为 ChapterMemory；删除相关 Fact 引用和并发快照测试，保留人物删除及项目数据隔离测试。
- 清理 Mapper 契约测试与各仓储测试对 Fact DAO/PO 的依赖；测试中的 `story_fact` 仅用于验证当前 Persist 不写入或隔离清理历史物理表，不再调用应用 Mapper。
- 当时仅移除应用映射；物理表删除在后续 50.41 完成。
- 验证结果：应用 Java 源码无 `IStoryFactDao`、`StoryFactPO`、`story_fact_mapper.xml` 引用；`git diff --check` 通过。定向 Maven 测试仍被仓库基线缺少既有 `CompressChapterNode` 阻断，未进入测试执行阶段。

## Task 50.41：移除 story_fact 数据库表（2026-09-02）

- 从 `docs/sql/schema.sql` 删除 `story_fact` 建表定义，并加入开发环境迁移语句 `DROP TABLE IF EXISTS story_fact`。
- 清理 Mapper 契约测试和所有仓储测试对 `story_fact` 的直接清理、查询及事实表存在性断言；代码层保持 `story_fact` 读写零引用。
- `docs/sql/seed.sql` 没有事实表种子数据；同步将演示人物运行时状态改为 `summary` 与 `_updatedChapter` 的简单结构。
- 更新 `docs/database-design.md`、`docs/chapter-workflow.md` 和章节工作流审查文档，当前 schema 与说明统一使用 ChapterMemory，不再维护事实表。
- 验证结果：`story_fact` 在应用主源码和测试 SQL 中无读写引用，schema 仅保留旧表删除迁移；`git diff --check` 通过。定向 Maven 测试仍被仓库基线缺少既有 `CompressChapterNode` 阻断，未进入测试执行阶段。

## Task 50.42-50.44：story_summary 统一为 ChapterMemory 存储（2026-09-02）

- `docs/sql/schema.sql` 的 `story_summary` 字段收敛为 `short_summary`、`key_events_json`、`character_states_json`、`unresolved_questions_json`、`ending_hook` 和 `status`，删除旧目标/结果/代价/主角变化列。
- `StorySummaryPO`、`story_summary_mapper.xml`、`ChapterPersistRepository` 和 `ChapterMemoryMapper` 改为写入、读取和解析 `key_events_json`；业务层不再写入旧 Summary 字段。
- 迁移 Mapper 集成测试、Planning/Context/Persist 测试夹具与断言，确认 ChapterMemory 的关键事件通过 JSON 列持久化并还原为业务列表。
- 领域层继续只使用 `ChapterMemoryVO` 的 `shortSummary`、`keyEvents`、`characterStates`、`unresolved` 和 `endingHook`，不保留旧 Summary VO 字段。
- 验证结果：应用生产代码不再引用旧 Summary 属性或数据库列；`git diff --check` 通过。定向 Maven 测试仍被仓库基线缺少既有 `CompressChapterNode` 阻断，未进入测试执行阶段。

## Task 50.45：Graph Key 统一为 MEMORY（2026-09-02）

- 将章节记忆结果的 Graph State key 从 `compression` 统一为 `memory`，使用 `ChapterGraphKeys.MEMORY`；`COMPRESSION` 仅保留为工作流节点和阶段名称。
- 同步更新 `ChapterGraphState`、`ChapterService`、Checkpoint 编解码、节点测试和持久化测试；不保留 `ChapterGraphKeys.COMPRESSION` 或 `EXTRACTION` 兼容 key。
- Checkpoint schema version 升级为 2，旧 key 不再被静默读取为章节记忆。
- 验证结果：`ChapterGraphKeys.COMPRESSION` 与 `EXTRACTION/extraction` 在生产代码和测试中的引用为 0；`git diff --check` 通过。定向 Maven 测试仍被仓库基线缺少既有 `CompressChapterNode` 阻断，未进入测试执行阶段。

## Task 50.46-50.48：章节 Graph、SSE 与 Generation Result 收口（2026-09-02）

- 确认 ChapterService 当前工作流节点为 DRAFT、REVIEW、COMPRESSION、PERSIST（保留既有上下文加载、人工审核和返修路由），生产代码没有 EXTRACT 节点。
- SSE 事件统一使用 `COMPRESSION_STARTED`、`COMPRESSION_COMPLETED`；前端阶段文案改为“章节整理”。
- `GenerateChapterResponseDTO`、前端 `GenerateChapterResponse` 和 GenerateView 移除压缩内部 `chapterMemory` 的对外字段与展示，正文生成页不再展示压缩结果；领域工作流内部仍保留 ChapterMemory 供 PERSIST 使用。
- 移除 Controller 与接口测试对该响应字段的映射和断言；当前生产 Java、API、前端和测试代码中 `EXTRACT_STARTED`、`EXTRACT_COMPLETED`、`extractedFacts` 均为 0 引用。
- 验证结果：`git diff --check` 通过；前端 `npm run build` 因工作区未安装 `vue-tsc` 未执行构建；Maven 定向测试被仓库基线缺少既有 `CompressChapterNode` 阻断，未进入测试执行阶段。

## Task 50.49：Generate 页清理旧事实文案（2026-09-02）

- 清理 Generate 页及其视觉契约测试中的“提取事实”“Facts”“事实整理”旧文案；Timeline 统一使用“正文生成、自动审稿、章节整理、保存”。
- 保留返修发生时的动态“自动修改”轮次提示；它属于审稿返修过程，不是旧事实阶段。
- 验证结果：前端源码与相关测试无旧事实文案；`git diff --check` 通过。

## Task 50.50-50.56：旧 Fact 测试收口与最终验收（2026-09-02）

- 盘点并清理旧 Fact 测试：删除无业务价值的 `RelevantFactSelectorTest`，确认没有 `ExtractFactsNodeTest`、`ExtractFactsNodeRealModelIT`、`StoryFact...` 或 `FactType...` 测试文件；ChapterNode、Persist、章节删除和正文重同步测试改为验证 ChapterMemory/COMPRESSION 链路。
- 补齐 `CompressChapterNode` 与模型响应结构，新增 `ChapterMemoryCrossChapterPlanningTest`，覆盖第一章 COMPRESSION 写入记忆后，第二章 Planning 加载短摘要、关键事件、未解决问题和结尾钩子。
- 更新删除与重同步回归：删除章节后同时清理对应 ChapterMemory、恢复 ChapterPlan、回退项目进度；正文重同步覆盖 `STALE → COMPRESSION → GENERATED`，不再依赖人物状态重建。
- 当前设计文档同步改用实际的 ChapterMemory、COMPRESSION、ContextRepository 和 ChapterPersistRepository 术语，移除旧 Fact 流程描述。
- 最终搜索结果：Java/Vue/TS 运行时代码和旧实现测试中，`ExtractedFactVO`、`FactTypeEnum`、`RelevantFactSelector`、`findRecentConfirmedFacts`、`IStoryFactDao`、`StoryFactPO`、`story_fact_mapper`、`ChapterExtractionVO`、`EXTRACT_STARTED`、`EXTRACT_COMPLETED`、`extractedFacts` 均为 0 引用；`story_fact` 仅保留 `docs/sql/schema.sql` 的开发环境删除迁移语句。
- 验证结果：纯单元/HTTP 定向 Maven 测试 49 个通过；包含 MySQL 仓储回归的定向构建完成编译，但本机 `13306` 数据库不可连接、`3306` 账号无权限，导致 18 个集成测试环境错误，未发现断言失败；前端执行 `npm ci` 后 `npm run build` 通过；`git diff --check` 通过。

## Task 50.57-50.60：移除 ChapterMemory 人物状态（2026-09-02）

- 删除 `ChapterMemoryVO.characterStates`、`ChapterMemoryCharacterStateVO` 及 Compression Response 的人物状态字段和映射校验。
- 移除章节摘要持久化与 MyBatis/schema 中的人物状态 JSON 字段，删除不再有数据来源的人物状态重建器、运行时状态 DAO/Mapper 接口及相关测试链路。
- Compression Prompt 收敛为短摘要、关键事件、未解决问题和结尾钩子，不再要求提取人物状态；同步更新工作流与数据库设计文档。
- 验证结果：`ChapterNodeTest`、`PromptBoundaryContractTest`、`MvpPersistenceContractTest` 共 26 个定向用例通过；Maven 定向构建编译通过，两个真实 MySQL 集成测试因本机 `127.0.0.1:13306` 连接被拒绝产生环境错误；`git diff --check` 通过。

## Task 50.61-50.64：收敛关键事件与人物状态链路（2026-09-02）

- Compression Prompt 明确要求：凡是影响后续剧情的人物关键行动、位置变化、目标变化和重大状态变化，都作为 `keyEvents` 保留并写明结果。
- 确认 `ChapterMemoryCharacterStateRebuilder` 已删除；`Persist`、章节删除和正文重同步均不再根据 ChapterMemory 更新 `story_character.current_state_json` 或执行人物状态重建。
- 验证结果：`PromptBoundaryContractTest` 3 个定向用例通过；Maven 定向构建编译通过。

## Task 50.65-50.67：以正式人物设定和 ChapterMemory 作为正文上下文来源（2026-09-02）

- Draft、Review 人物上下文改为正式人物静态设定，不再读取 `current_state_json` 作为剧情状态；位置、目标和重大状态变化由近期 ChapterMemory 的 `keyEvents` 提供。
- 保留 `current_state_json` 字段及角色管理、规划侧现有用途；ChapterMemory、PERSIST、章节删除和正文重同步均不写入该字段。
- 扩展 `ChapterMemoryCrossChapterPlanningTest`：验证上一章位置/目标变化写入 `keyEvents` 后，下一章 Context 和 Planning 均可读取；删除上一章后对应 ChapterMemory 从两者查询结果中消失。
- 删除项目唯一正文时将当前章节进度回退为 0，避免非空进度字段阻止 ChapterMemory 清理事务完成。
- 验证结果：`ChapterNodeTest`、`PromptBoundaryContractTest`、`MvpPersistenceContractTest` 通过；Maven 定向构建编译通过。真实 MySQL 回归用例因本机 `127.0.0.1:13306` 不可连接产生环境错误；`git diff --check` 通过。

## Task 50.58：正文链路去掉 `lifeStatus` 动态约束（2026-09-02）

- `DraftChapterNode`、`ReviewChapterNode`、`ReviseChapterNode` 的人物上下文统一只渲染姓名对应的正式静态档案，不再输出 `story_character.lifeStatus`，返修链路也不再输出 `current_state_json`。
- 正文动态剧情状态继续由近期 ChapterMemory 的 `shortSummary`、`keyEvents`、`unresolved` 和 `endingHook` 提供；保留 `current_state_json` 字段供其他仍在使用的角色资料与规划场景兼容。
- 回归测试覆盖人物死亡字段不进入三类正文 Prompt，同时保留上一章位置、目标变化的 `keyEvents` 并验证下一章 Prompt 可读取。
- 验证结果：`ChapterNodeTest`、`PromptBoundaryContractTest`、`MvpPersistenceContractTest` 共 27 个定向用例通过；`ChapterMemoryCrossChapterPlanningTest` 的 2 个真实 MySQL 用例因本机 `127.0.0.1:13306` 连接被拒绝产生环境错误；`git diff --check` 通过。

## Task 50.59：删除章节生成侧无用的 currentState 语义（2026-09-02）

- `StoryCharacterEntity` 注释收敛为正式人物静态档案；`currentStateJson` 和 `lifeStatus` 保留兼容字段，但明确不参与章节生成上下文及 DRAFT、REVIEW、REVISE Prompt。
- `ContextRepository` 不再将两个兼容字段映射进 `ChapterContextAggregate`；`NovelProjectRepository` 的人物 CRUD 和 `PlanningRepository` 的规划兼容读取保持不变。
- 全仓库确认章节生成主链不再使用 `getCurrentStateJson`、`getLifeStatus`、`appendCurrentState`、`lifeStatusLabel`；删除已无调用的 `appendCurrentState` 与 `currentStateText` 格式化 helper。
- 回归测试同步确认章节上下文不携带人物当前状态字段。
- 验证结果：`ChapterNodeTest`、`PromptBoundaryContractTest`、`ContextRepositoryTest`、`MvpPersistenceContractTest` 共 29 个定向用例通过；`git diff --check` 通过。

## Task 50.60：清理失效的 Prompt Formatter 能力（2026-09-02）

- 全局确认 `ChapterPromptFormatter.appendCurrentState(...)`、`currentStateText(...)`、`lifeStatusLabel(...)` 无调用、无方法声明。
- 保持前端 `SetupView.vue` 的同名展示函数不变；它不属于章节 Prompt Formatter 或 runtime-state Prompt 能力。
- 验证结果：目标 API 搜索结果为 0，`ChapterPromptFormatter.java` 无残留方法，`git diff --check` 通过；此前相关 29 个定向测试已通过。

## Task 50.61：统一人物静态设定命名（2026-09-02）

- `REVIEW`、`REVISE` 人物区块统一使用 `## 相关人物静态设定`；`DRAFT` 保持 `## 人物静态设定`。
- 三类正文 Prompt 及章节 `SystemPrompt` 均不再使用“人物当前状态”“人物运行时状态”“角色状态快照”等 runtime-state 语义，动态人物变化统一指向近期剧情记忆。
- 回归测试锁定三类 Prompt 的新标题及旧标题排除规则；`ChapterNodeTest` 22 个用例通过，`git diff --check` 通过。

## Task 50.62：增加 ChapterMemory 与人物生存字段边界回归（2026-09-02）

- 新增第 2 章 DRAFT Prompt 回归场景：第 1 章 ChapterMemory 记录“林渊杀死张三，张三死亡”，人物兼容字段仍为 `lifeStatus=ALIVE`。
- 测试确认 Prompt 保留死亡关键事件，同时不输出“张三 生存状态：存活”或其他生存状态字段。
- 验证结果：测试输出 `keyEventRendered=true`、`databaseLifeStatusLeaked=false`；`ChapterNodeTest` 23 个定向用例通过，`git diff --check` 通过。

## Task 51：章节生成工作流可恢复性、可观测性与 UI 状态收口（2026-09-02）

- `ReviewChapterNode` 将审稿报告校验放入模型重试闭包；校验重试耗尽后返回 `REVIEW_FAILED`，保留已生成正文，不再把整条工作流直接置为失败。
- 增加 `REVIEW_FAILED` 检查点和 `REVIEW` 人工决策：用户可重新审稿、接受当前正文或停止流程；重新审稿会清理自动重试计数并回到 REVIEW，接受和停止分别走已有落库与终止分支。
- Session Registry、SSE 事件和章节结果映射补齐 `REVIEW_FAILED`、`GENERATION_ABORTED` 状态，前端时间线、状态徽标和人工操作区同步收口；批量生成遇到 `REVIEW_FAILED` 会停止后续章节。
- 主要目录：`novel-agent-domain` 工作流与 Session、`novel-agent-trigger` SSE、`novel-agent-web` Generate 页面及类型、`novel-agent-app/src/test` 定向契约测试、`docs` 工作流说明。
- 验证结果：Maven 定向测试 64 个全部通过；前端 `npm run build` 通过；`git diff --check` 通过。

## Task 51.1：Review evidence 格式归一化（2026-09-02）

- `ReviewChapterNode` 在 evidence 连续片段校验前统一 CRLF/LF、首尾空白和连续空白，仅做格式归一化，不进行语义模糊匹配。
- 新增审稿 Prompt 边界回归，确认模型仅调整换行或空格时不会触发 `E0004`。
- 验证结果：相关 Maven 定向测试 65 个全部通过；前端 `npm run build` 通过；`git diff --check` 通过。

## Task 51.2：工作流失败事件携带可读原因（2026-09-02）

- `ChapterGenerationSessionEvent.content` 统一承载失败或终止原因；`REVIEW_FAILED`、`GENERATION_FAILED`、`GENERATION_ABORTED` 和 `GENERATION_CANCELLED` 禁止为空消息。
- `ChapterService` 将审稿校验详情、解包后的 `AppException.info` 或异常消息写入事件；Generate 页展示 SSE 失败原因，避免只显示流程结束。
- 补充 Session Registry、HTTP SSE 和前端契约测试，覆盖失败消息传输与显示。
- 验证结果：Session、State Schema、HTTP SSE 和前端契约相关 Maven 定向测试 20 个全部通过；前端 `npm run build` 通过；`git diff --check` 通过。

## Task 51.3：前端统一 workflowStatus（2026-09-02）

- `novel-agent-web/src/types/index.ts` 增加统一的 `WorkflowStatus` 类型，覆盖 IDLE、DRAFTING、REVIEWING、REVIEW_FAILED、REVISING、COMPRESSING、PERSISTING、WAITING_HUMAN、COMPLETED、FAILED、CANCELLED。
- `GenerateView.vue` 以 `workflowStatus` 作为单一工作流状态源：SSE 事件和批量/恢复接口结果统一映射，时间线、正文等待态、人工审核区、生成按钮和终态展示不再通过 `generationSession && !result` 或结果状态直接推断；后端 `ABORTED` 归一化为前端 `CANCELLED`。
- 补充前端状态枚举与旧推断条件排除契约；验证结果：相关 Maven 定向测试 16 个全部通过，前端 `npm run build` 通过。

## Task 51.4：右侧工作流三层固定布局（2026-09-02）

- `GenerateView.vue` 右侧结果区固定为 Workflow Header、Content Canvas、Contextual Action Bar 三层；Header 和操作栏脱离正文滚动，Canvas 独立纵向滚动。
- 自动审稿按钮、人工审核输入和决策按钮移入底部操作栏；正文预览取消内部高度滚动，避免按钮随正文长度下沉；移动端保留独立结果区高度和响应式操作布局。
- 新增三层 DOM 顺序、滚动容器、底部操作栏和桌面/移动端高度契约；验证结果：相关 Maven 定向测试 17 个全部通过，前端 `npm run build` 通过。

## Task 51.5：正文单一内容区域（2026-09-02）

- `GenerateView.vue` 将正文、等待态和空态收口为同一组 `v-if / v-else` 内容分支；有正文时不再在其下方追加“正在等待正文流”。
- 滚动提示改为独立条件，正文优先使用非空的最终结果或流式内容，避免状态切换造成重复正文区域。
- 验证结果：相关 Maven 定向测试 17 个全部通过；前端 `npm run build` 通过；`git diff --check` 通过。

## Task 51.6：Contextual Action Bar 状态化操作（2026-09-02）

- `GenerateView.vue` 将右侧 Action Bar 改为由 `workflowStatus` 驱动的互斥分支：DRAFTING 可停止，REVIEWING 可接受或停止，REVIEW_FAILED 可重新审稿/接受/停止，WAITING_HUMAN 固定通过/驳回重写/放弃。
- REVISING、COMPRESSING、PERSISTING 仅显示处理中并禁止重复操作；COMPLETED 提供查看正文和生成下一章，批量完成时按批次末章计算下一章。
- 左侧生成入口移除重复的停止/完成操作，避免与右侧 Contextual Action Bar 产生双重控制；补充 Action Bar 状态、按钮顺序和处理中禁操作契约。
- 验证结果：原流式显示相关 Maven 定向测试 18 个全部通过；本次滚动跟随补充定向测试 1 个通过；前端 `npm run build` 通过；`git diff --check` 通过。

## Task 51.7：接受正文后的持续整理反馈（2026-09-02）

- `GenerateView.vue` 在接受正文接口成功后立即将 `workflowStatus` 切换为 `COMPRESSING`、Timeline 阶段切换为 `COMPRESSION`，并保留当前正文。
- 接受请求等待后端压缩事件期间，Action Bar 显示“正在整理章节”和 loading/禁用按钮；压缩或持久化事件到达后自动收口为处理中状态，避免重复操作；同时防止迟到的审稿阶段事件回退 Timeline。
- 补充接受反馈状态、loading 按钮和 Timeline 阶段契约测试。
- 验证结果：相关 Maven 定向测试 18 个全部通过；前端 `npm run build` 通过；`git diff --check` 通过。

## Task 51.8：流式正文前端显示缓冲（2026-09-02）

- `GenerateView.vue` 保持后端 `DRAFT_CHUNK` SSE 协议不变，前端收到 chunk 后先写入 `receivedBuffer`，再以 30ms 定时器每次取 4 个字符追加到 `streamingContent`，形成连续吐字效果。
- 缓冲定时器在切章、重新生成和组件卸载时统一清理；生成结束仅关闭 SSE，保留未展示缓冲并继续排空，避免正文尾部丢失。
- 更新流式显示契约，覆盖缓冲入队、定时排空、自动滚动和旧的直接整块追加逻辑排除。
- 追加正文前统一按 24px 阈值重新判断 `results-content` 是否接近底部；仅在用户仍处于底部附近时自动跟随，用户上滑后保留当前位置并继续显示“回到底部”，点击后恢复跟随。
- 验证结果：相关 Maven 定向测试 18 个全部通过；前端 `npm run build` 通过；`git diff --check` 通过。

## Task 51.9：工作流阶段耗时日志（2026-09-02）

- `ChapterService` 在 DRAFT、REVIEW、REVISION、COMPRESSION、PERSIST 节点包装器中按 `workflowId` 记录阶段开始时间，并在完成、审稿失败或节点异常时输出阶段事件与 `durationMs`。
- 使用 `System.nanoTime()` 计算耗时，工作流正常结束或恢复调用结束后清理计时记录；审稿重试耗时覆盖整个 REVIEW 节点执行过程。
- 新增阶段耗时日志契约测试，确认五个阶段均具备开始/完成事件和耗时日志字段。
- 验证结果：`ChapterWorkflowObservabilityContractTest` 定向测试通过；`git diff --check` 通过。

## Task 51.10：章节模型 Prompt Trace（2026-09-03）

- 新增 `PromptTraceRecord` 与 `PromptTraceRecorder`，四个章节模型节点在每次模型调用完成后输出单条 `PROMPT_TRACE` INFO 日志，包含 `workflowId`、`projectCode`、`chapterNumber`、`node`、`attempt`、完整 system/user Prompt 和 `createdAt`。
- `workflowId` 纳入章节图 State 并随初始调用、派生数据同步和断点恢复传递；`ChapterModelRetryExecutor` 暴露 0-based attempt（首次调用为 0，第一次重试为 1），模型重试会产生独立 Trace。
- Prompt 中的换行和反斜杠在日志中转义为单行，便于按 `PROMPT_TRACE` 检索；本次未新增数据库表或 HTTP 接口。
- 新增 Prompt Trace 字段与节点覆盖契约，并补充 attempt 序号行为测试；验证结果：相关 Maven 定向测试通过，`git diff --check` 通过。

## Task 51.11：Prompt Trace 模型响应摘要（2026-09-03）

- `PromptTraceRecord` 与 `PromptTraceRecorder` 补充 `responseText`、`success`、`errorCode`、`errorMessage`、`durationMs`；每次模型尝试最终只输出一条包含 Prompt、响应和结果摘要的 `PROMPT_TRACE` 日志。
- DRAFT 保留完整流式正文，流式异常时保留已接收片段；REVISION 保留完整文本响应；REVIEW/COMPRESSION 通过 `ChapterModelResponse` 携带完整原始结构化响应，旧模型适配器无原文时使用解析结果序列化兜底。
- 响应校验、结构化结果校验和模型调用异常均写入失败 Trace，保留对应错误码、可读错误信息和本次调用耗时；新增接口保持旧 `call` 实现兼容，不新增 HTTP 接口。
- 验证结果：章节节点、重试执行器、恢复输入和可观测性契约 Maven 定向测试共 37 个全部通过；`git diff --check` 通过。

## Task 51.12：Prompt Trace 独立调试表（2026-09-03）

- 新增 `chapter_model_trace` 独立表及 `docs/sql/schema.sql` 建表定义，保存工作流、章节、节点、重试序号、完整 Prompt、模型响应、成功/失败信息、错误信息和耗时；不与 `story_chapter`、`story_summary` 建立业务耦合。
- 新增 `IPromptTraceRepository`、`PromptTraceRepository`、`IPromptTraceDao`、`PromptTracePO` 和 MyBatis Mapper；`PromptTraceRecorder` 在保留结构化日志的同时写入独立调试表，调试表写入失败只告警、不阻断正文生成。
- 新增独立持久化契约测试，验证 Trace 字段完整且业务表不参与；应用上下文编译/装配路径已检查。验证结果：可观测性契约测试 3 个全部通过，现有 MySQL 集成测试因本机 `127.0.0.1:13306` 连接被拒绝未执行到业务断言，`git diff --check` 通过。

## Task 51.13：Retry 尝试独立 Prompt Trace（2026-09-03）

- `ChapterModelRetryExecutor` 将传给模型节点的尝试序号统一为 0-based：首次调用 `attempt=0`，第一次重试 `attempt=1`，并保持已有最大重试次数和退避边界不变。
- DRAFT、REVIEW、REVISION、COMPRESSION 每次尝试均独立写入 `chapter_model_trace`，失败尝试不会被后续成功尝试覆盖，可直接对比每次 Prompt/Response。
- 更新重试序号行为测试、Trace 持久化契约和表字段注释；验证结果：相关 Maven 定向测试通过，`git diff --check` 通过。

## Task 51.14：结构化解析失败保留 raw response（2026-09-03）

- 新增 `ChapterModelResponseException` 携带结构化模型响应原文；`ChapterModelPort` 在 JSON 解析失败或结构化结果为空时将已读取的 raw response 一并带入异常。
- `PromptTraceRecorder` 在失败 Trace 未拿到显式响应时，从异常链提取 raw response；因此 REVIEW/COMPRESSION 的解析失败也会保存原始模型返回，evidence 校验失败则继续保存已解析响应原文。
- 新增结构化解析失败 Trace 行为测试和可观测性契约；验证结果：相关 Maven 定向测试 4 个全部通过，`git diff --check` 通过。

## Task 51.15：Prompt Trace 日志瘦身（2026-09-03）

- `PromptTraceRecorder` 的普通 INFO 日志只保留 `workflowId`、`node`、`attempt`、合并后的 `promptLength`、`responseLength` 和 `durationMs`，不再输出完整 Prompt/Response。
- 完整 system/user Prompt、模型响应和失败摘要继续写入独立的 `chapter_model_trace` 调试表，便于按 Trace 数据排错而不刷爆业务日志。
- 更新日志字段契约测试；验证结果：可观测性契约测试通过，`git diff --check` 通过。

## Task 51.16：开发用途 Trace 查询接口（2026-09-03）

- 新增 `GET /api/v1/novels/generation-sessions/{workflowId}/traces`，按创建时间返回节点、attempt、成功状态、耗时、错误信息及完整 system/user Prompt、responseText，不增加复杂分页。
- `IPromptTraceRepository`、MyBatis DAO/Mapper 和 `PromptTraceRepository` 补齐按 workflowId 查询及领域记录映射；前端暂不增加 Trace UI。
- Trace Controller 限定在 `dev` profile 下装配，避免开发调试数据在生产配置中暴露；新增 Trace Controller 字段、路径和 profile 契约测试。
- README 补充开发环境 Trace 查询调用示例。
- README 补充 `dev` profile 下复用现有章节生成 Session 的真实模型验收步骤。
- 验证结果：相关 Maven 定向测试 12 个全部通过，前端 `npm run build` 通过；本机 `127.0.0.1:13306` 当前未监听，真实模型端到端验收需在数据库和 dev 模型可用后执行；`git diff --check` 通过。

## Task 51.1：活动 generation session 查询与 Generate 页面恢复（2026-09-03）

- `ChapterGenerationSessionRegistry` 记录会话对应的 `projectCode` 与 `chapterNumber`，新增活动会话查询并排除已完成、已失败、已取消和已放弃的终态会话；章节服务创建 Session 时同步登记归属。
- `NovelChapterGenerationSessionController` 新增同资源路径的 `GET /api/v1/novels/projects/{projectCode}/chapters/{chapterNumber}/generation-sessions`，有活动会话返回 `workflowId`，没有时返回空数据。
- `GenerateView.vue` 在页面挂载和章节切换时查询活动会话，恢复后重新连接 SSE，利用事件历史恢复正文流、工作流阶段和 Review 控制；新增前端章节 API。
- 验证结果：Registry、Session Controller 和前端契约定向 Maven 测试共 16 个通过；`novel-agent-web` 执行 `npm run build` 通过；`git diff --check` 通过。

## Task 51.2：生成会话恢复快照（2026-09-03）

- 新增章节生成 Session 快照模型和 HTTP 响应，活动会话查询至少返回 `workflowId`、`chapterNumber`、`status`、`accumulatedContent`、`completedStages`、`reviewIssues`、`failureMessage`。
- Session Registry 按事件维护工作流状态、DRAFT 正文片段和阶段；章节工作流结果完成后补充当前正文、已完成阶段和 Review 问题，失败与终止事件保留可读原因。
- Generate 页恢复时先应用快照，再连接 SSE 回放历史事件，避免刷新或重新进入页面后丢失正文、阶段和 Review 控制上下文。
- 验证结果：Registry、Session Controller 和前端契约定向 Maven 测试共 17 个通过；`novel-agent-web` 执行 `npm run build` 通过；`git diff --check` 通过。

## Task 51.3：活动会话恢复与 SSE 重连顺序收口（2026-09-03）

- Generate 页将活动 Session 快照恢复收口为独立函数，按 `streamingContent`、`workflowStatus`、workflow timeline、`reviewIssues` 的顺序恢复，并在恢复完成后重新连接对应 workflow 的 SSE。
- SSE 重连收到历史事件时保留现有事件重放逻辑：从 `GENERATION_STARTED` 重新构建流式正文和 timeline，快照中的审稿问题与失败信息继续保留。
- 验证结果：新增前端恢复顺序契约检查，相关 Maven 定向测试共 18 个通过；`novel-agent-web` 执行 `npm run build` 通过；`git diff --check` 通过。

## Task 51.4：SSE 断线通过 Session 快照补齐状态（2026-09-03）

- SSE 连接发生错误时主动关闭旧连接，重新查询当前项目和章节的活动 Session 快照，不使用前端旧内存推断断线期间的状态。
- 服务端快照查询成功后重新恢复正文、工作流状态、timeline 和 Review 上下文，再连接当前 `workflowId` 的 SSE；查询暂时失败时按 1 秒间隔重试，并通过连接版本号丢弃章节切换或新连接产生的过期结果。
- 验证结果：新增 SSE 断线快照补齐契约检查，相关 Maven 定向测试共 26 个通过；`novel-agent-web` 执行 `npm run build` 通过；`git diff --check` 通过。

## Task 51.5：终态 Session 短期保留与刷新恢复（2026-09-03）

- Session Registry 为 `COMPLETED`、`FAILED`、`ABORTED` 和 `CANCELLED（停止）` 记录结束时间，终态快照保留 5 分钟；页面查询优先返回活动 Session，没有活动 Session 时返回最近的短期终态快照。
- 终态快照继续保留正文、工作流状态、完成阶段、Review 问题和失败/停止原因；查询和 SSE 订阅时惰性清理过期 Session，避免终态事件历史无限留存。
- 验证结果：新增终态保留、过期清理测试，相关 Maven 定向测试共 27 个通过；`novel-agent-web` 执行 `npm run build` 通过；`git diff --check` 通过。

## Task 51.6：Generate 页单主滚动区域（2026-09-03）

- `GenerateView.vue` 将 `generation-page` 锁定在工作台可用高度并隐藏整页溢出；`results-panel` 改为纵向 flex 容器，header 与 footer 保持固定层，footer 不参与正文滚动。
- `results-content` 使用 `flex: 1`、`min-height: 0` 和 `overflow-y: auto` 承担唯一正文滚动；移动端同步取消 `height: auto` 和独立结果区固定高度，保持同一滚动模型。
- 更新 Generate 页面视觉契约，覆盖桌面两栏与移动端单列下的滚动职责；验证结果：单主滚动区域 Maven 定向测试 1 个通过，`novel-agent-web` 执行 `npm run build` 通过；浏览器检查因本地无活动项目被路由守卫阻断，未写入测试业务数据；`git diff --check` 通过。

## Task 51.9（REVIEWING 文案）：跳过审稿并采用（2026-09-03）

- `GenerateView.vue` 将 REVIEWING 状态按钮从“接受当前正文”调整为“跳过审稿并采用”，同步更新处理中提示与成功/失败反馈，明确该操作会跳过剩余自动审稿和修改。
- 点击仍调用既有 `ACCEPT` Session 命令；后端 `routeAfterReview`、`routeAfterRevise` 和 `COMPRESSION → PERSIST` 边保持不变，继续沿既有路径完成章节整理与保存。
- 更新 Generate Review 控制和工作流路由契约；验证结果：前端/后端定向 Maven 测试 3 个通过，`novel-agent-web` 执行 `npm run build` 通过；`git diff --check` 通过。

## Task 51.7：Generate 父级 Layout 滚动收口（2026-09-03）

- `App.vue` 为 `/generate` 路由增加 `generate-mode`，在保留其他工作台页面 `studio-main` 滚动行为的同时关闭 Generate 页父级外层滚动。
- `router-view` 继续直接承载 Generate 页面，父级 `studio-main.generate-mode`、`generation-page` 与 `results-content` 的高度和 overflow 形成单一纵向滚动链，避免外层页面与正文区同时出现滚动条。
- 新增父级 Layout 滚动契约；验证结果：父级/子级 Maven 定向测试 2 个通过，`novel-agent-web` 执行 `npm run build` 通过；浏览器检查再次受本地无活动项目的路由守卫限制，未写入测试业务数据；`git diff --check` 通过。

## Task 51.11：WAITING_HUMAN 仅保留 Review 决策（2026-09-03）

- `GenerateView.vue` 将 WAITING_HUMAN 的说明和操作收口为“通过、驳回重写、放弃”三个真正的人工 Review 决策，不混入自动 Review 的采用或停止操作。
- 保留人工修改意见输入和返修次数限制：返修不可用时继续禁用“驳回重写”，但“通过”和“放弃”仍可用。
- 新增 WAITING_HUMAN 控制范围契约测试；验证结果：相关 Maven 定向测试 4 个通过，`novel-agent-web` 执行 `npm run build` 通过；`git diff --check` 通过。

## Task 51.12：REVIEW_FAILED 采用文案明确化（2026-09-03）

- `GenerateView.vue` 将 REVIEW_FAILED 的“接受当前正文”统一改为“直接采用当前正文”，同步更新失败提示和控制说明，明确该操作仅针对当前已保留正文。
- 保持既有 `resume('PASS')` 恢复语义不变；REVIEWING 仍使用“跳过审稿并采用”，WAITING_HUMAN 仍仅保留三项人工 Review 决策。
- 更新 REVIEW_FAILED 文案契约测试；验证结果：相关 Maven 定向测试 4 个通过，`novel-agent-web` 执行 `npm run build` 通过；`git diff --check` 通过。

## Task 51.13：COMPRESSION 移除二次确认按钮（2026-09-03）

- `GenerateView.vue` 删除 COMPRESSION 状态下的 disabled“跳过审稿并采用”按钮，统一使用工作流处理中状态，仅展示“正在整理章节……”状态。
- 保留 REVIEWING 的“跳过审稿并采用”入口；请求成功后进入 COMPRESSION 时不再渲染任何确认控件，避免用户误以为需要再次确认。
- 更新工作流处理中状态契约；验证结果：相关 Maven 定向测试 4 个通过，`novel-agent-web` 执行 `npm run build` 通过；`git diff --check` 通过。

## Task 51.14-51.15：服务端状态恢复与刷新回归（2026-09-03）

- `GenerateView.vue` 增加服务端状态就绪门控：页面挂载、章节切换和生成会话创建后先查询 Session 快照，再以服务端 `workflowStatus` 恢复正文、Timeline、Review 控制和 Compression 状态；状态查询未完成时禁止重复启动生成。
- 创建、接受、停止、重新审稿和批量生成路径移除前端硬编码的工作流状态赋值；命令结果或服务端快照成功后才更新页面状态，EventSource 仅负责订阅事件，不作为工作流真实状态来源。
- 增补刷新恢复与状态源契约，覆盖生成中、Reviewing、Review Failed、Compression 刷新、禁止重复启动、单滚动区和 Review 按钮语义；验证结果：相关 Maven 定向测试 11 个通过，`novel-agent-web` 执行 `npm run build` 通过；`git diff --check` 通过。

## Task 52.1：修复 Generate 页主滚动高度链路（2026-09-03）

- `novel-agent-web/src/App.vue` 为 `studio-body`、`studio-main` 和路由过渡承载层补齐 `height: 100%`、`min-height: 0` 及可伸缩布局，确保 Generate 页面能够取得稳定的可用高度；`/generate` 继续关闭 `studio-main` 外层滚动。
- `novel-agent-web/src/views/GenerateView.vue` 保持 `generation-page` 外层 `overflow: hidden`，结果区固定使用 `height: 100%`、`min-height: 0`，正文区使用 `flex: 1`、`min-height: 0`、`overflow-y: auto`；移动端移除 `height: auto` 覆盖，避免正文过长时滚动区域高度失效。
- 更新 `CreationCanvasVisualContractTest` 与 `StudioNavigationLayoutContractTest`，锁定高度链路和单一主滚动区域；验证结果：`npm run build` 通过，Maven 定向测试 16 个通过，`git diff --check` 通过。浏览器检查因本地无活动项目被路由守卫阻断，未创建测试项目或写入业务数据。

## Task 52.2：ChapterPlan 无 requirement 时自动续写（2026-09-03）

- `PlanningService` 为 ChapterPlan Prompt 增加 Story Bible 的创作语义投影，并继续提供当前大纲、上一章摘要、近期 ChapterMemory、相关人物和硬规则；不把 JSON 存储结构直接传给模型。
- `PlanningPrompts.CHAPTER_PLAN_SYSTEM` 明确无 requirement 时按 Story Bible、当前大纲和前文自然生成下一章计划；有 requirement 时仅作为本章额外创作偏好，不能替代或违背既有故事语义。前端 `GenerateChapterPlanRequest.requirement` 改为可选，并补充“留空将按当前大纲和前文自然生成。”弱提示。
- 新增并更新 ChapterPlan 上下文、Prompt、HTTP 可选字段和 Generate 页面契约测试；验证结果：Maven 相关定向测试 15 个通过，`novel-agent-web` 执行 `npm run build` 通过，`git diff --check` 通过。

## Task 52.3：重做 ReadView 视觉层级（2026-09-03）

- `novel-agent-web/src/views/ReadView.vue` 将章节目录调整到左侧，宽度使用 260–300px 的弹性范围；正文工作区占据剩余空间，正文内容限制在 820px 并居中。
- 阅读区域改为白色编辑工作区，移除大面积灰色阅读舞台感；顶部工具栏保持固定，`paper-scroll` 独立纵向滚动，标题收小，正文保留舒适行高，正文 textarea 保持无边界样式；移动端改为目录在上、正文区独立滚动且不产生外层滚动。
- 更新 ReadView 布局与视觉契约，并同步当前 Generate 页面按钮 DOM 结构的旧断言；验证结果：相关 Maven 定向测试 14 个通过，`novel-agent-web` 执行 `npm run build` 通过，`git diff --check` 通过。浏览器检查因本地无活动项目被路由守卫阻断，未创建测试项目或写入业务数据。

## Task 52.4：ReadView 删除无价值视觉元素（2026-09-03）

- `novel-agent-web/src/views/ReadView.vue` 删除正文区的 `CHAPTER 01` 装饰、重复标题和底部章节进度条，移除对应的 `readingProgress` 计算及无效样式。
- 标题编辑移入顶部工具栏，与章节号、字数、状态和保存操作集中展示；正文从工具栏下方直接开始，减少标题留白并保持正文区独立滚动，整体继续使用轻量白色编辑工作区。
- 更新 ReadView 编辑与视觉契约；验证结果：相关 Maven 定向测试 14 个通过，`novel-agent-web` 执行 `npm run build` 通过，`git diff --check` 通过。

## Task 52.5：OutlineView 隐藏章节范围（2026-09-03）

- `novel-agent-web/src/views/OutlineView.vue` 删除树节点的 `tree-range`、桌面详情和移动端抽屉中的范围展示，以及当前节点编辑表单的章节范围控件。
- 详情元信息仅对 ARC 显示“第 N 章”；BOOK/VOLUME 不展示章节范围。当前节点保存继续沿用已有 `startChapter/endChapter` 值，新增下级和 AI 拆分中的范围分配控件保持不变，后台分配逻辑未修改。
- 更新 OutlineView 章节范围可见性契约；验证结果：Outline 相关 Maven 定向测试 10 个通过，`novel-agent-web` 执行 `npm run build` 通过，`git diff --check` 通过。

## Task 52.6：Story Bible 特殊体系去过度结构化（2026-09-03）

- `novel-agent-web/src/views/SetupView.vue` 将特殊体系编辑收敛为“体系名称、体系说明、等级/境界（可选）、补充设定（可选）”四个自然语言字段，移除能力机制、代价限制和可增删成长层级列表。
- `PowerSystemDraftVO`、Story Bible Prompt、前端类型与映射统一使用 `name / description / levels / supplement`；继续沿用现有 `powerSystemJson` 存储字段，不新增灵根、血脉、资质、职业、属性、技能树等固定字段。读取历史 `mechanism / cost / ranks` 内容时兼容映射到新字段。
- 更新章节 Prompt 的特殊体系业务文本、开发 seed 和 Story Bible/正文/前端契约测试；验证结果：Story Bible 及正文 Prompt 相关 Maven 定向测试最终通过，`novel-agent-web` 执行 `npm run build` 通过，`git diff --check` 通过。

## Task 52.7：powerRanks UI 文案收敛（2026-09-03）

- `novel-agent-web/src/views/SetupView.vue` 将特殊体系等级字段展示为“等级 / 境界”，并补充“存在明确等级体系时填写，没有可以留空。”的弱提示，降低对非升级题材的误导。
- 更新 `StoryBibleEditingContractTest` 前端契约断言；验证结果：相关 Maven 定向测试和 `novel-agent-web` 的 `npm run build` 通过。

## Task 52.8：特殊体系自然语言承载题材概念（2026-09-03）

- 更新 Story Bible 初始生成与修订 Prompt，明确灵根、血脉、天赋、属性、职业、魔法亲和、异能类型、装备体系等信息可完整写入 `description` 或 `supplement`，不新增对应固定字段。
- 明确 `PowerSystemDraftVO` 与前端保存映射保留开放文本，补充生成草稿和正式保存的内容保真测试，避免题材信息因没有专门字段而丢失。

## Task 52.9：正文修改状态改用用户语义（2026-09-03）

- `novel-agent-web/src/views/ReadView.vue` 将 `DIRTY / 待同步` 展示为“正文已修改”，将操作按钮改为“更新章节记忆”，并补充剧情摘要与人物状态更新说明；内部 `DIRTY` 判断、接口和同步流程保持不变。
- 更新 `ChapterDerivedDataResyncUiContractTest`；验证结果：相关 Maven 定向测试和 `novel-agent-web` 的 `npm run build` 通过。

## Task 52.10：正文保存后自动更新章节记忆（2026-09-03）

- `NovelProjectService.overwriteChapterContent` 在正文覆写事务提交后调用现有 Compression → `persistDerivedData` 链路；同步成功重新读取正常章节状态，失败则返回已保存的 DIRTY 正文并记录日志，不回滚正文。
- `ReadView.vue` 根据自动同步结果显示“章节记忆更新失败”，并提供“重新更新”按钮；手动重试失败时保持同样的失败状态和操作入口。
- 补充正文保存自动同步成功/失败的领域测试及 ReadView 契约断言。

## Task 53.1：Generate 页面恢复唯一主滚动条（2026-09-03）

- `novel-agent-web/src/views/GenerateView.vue` 将唯一纵向滚动容器恢复为 `generation-page`，把流式正文滚动监听和“回到底部”定位同步移到页面根节点；`results-panel` 与 `results-content` 不再承担内部纵向滚动或内容裁剪。
- 移除移动端 Generate 页面旧的 `overflow: hidden` 和结果区固定高度约束，桌面端两栏、移动端单列继续共用页面根节点滚动模型。
- 更新 Generate 页面滚动、会话流式显示和工作台布局契约；验证结果：相关 Maven 定向测试 25 个通过，`novel-agent-web` 执行 `npm run build` 通过；浏览器检查因本地无活动项目被路由守卫阻断，未创建测试项目或写入业务数据；`git diff --check` 通过。

## Task 53.2：results-panel 不再截断页面高度（2026-09-03）

- `novel-agent-web/src/views/GenerateView.vue` 保留 `.results-panel` 的 `min-width: 0`、`min-height: 0`，移除固定高度和内容裁剪；`.results-content` 显式使用 `overflow: visible`，由 `generation-page` 统一承接页面纵向滚动。
- 更新 Generate 页面滚动契约，覆盖左侧 ChapterPlan、右侧正文及 Review 区域内容增长时的自然高度行为。
- 验证结果：相关 Maven 定向测试通过，`novel-agent-web` 执行 `npm run build` 通过；`git diff --check` 通过。

## Task 53.3：流式正文自动滚动适配页面滚动容器（2026-09-03）

- `GenerateView.vue` 的流式滚动引用统一指向 `generation-page`：页面根节点监听 `handleStreamingScroll`，`drainReceivedBuffer` 和 `scrollStreamingContentToBottom` 均使用同一容器判断底部并执行定位。
- 保留既有交互语义：用户位于底部时正文流自动跟随，用户上滑后通过 `streamingAtBottom` 暂停跟随，点击“回到底部”后滚动到页面最新正文并恢复跟随。
- 新增流式滚动容器绑定契约测试；验证结果：相关 Maven 定向测试通过，`novel-agent-web` 执行 `npm run build` 通过；`git diff --check` 通过。

## Task 53.4：Generate 页面滚动回归验证（2026-09-03）

- 已对本地前端执行 1366×768、1920×1080 以及对应 125%、150% 缩放后的等效 CSS 视口探测；页面级静态滚动规则和等效视口横向尺寸检查未发现 Generate 之外的横向溢出。
- Generate 实际页面回归未完成：本地无活动项目，路由守卫将 `/generate` 重定向到 `/project`；尝试加载 `novel-001` 时后端返回 HTTP 500，因此未能在真实 Generate 页面检查正文、Review、ChapterPlan 按钮可达性或双滚动条。

## Task 53.5：Chapter Directory 层级语义重做（2026-09-03）

- `novel-agent-web/src/views/ReadView.vue` 将章节目录改为“书 → 卷 → 章节”三级结构：书节点展示当前作品名，卷节点展示卷名和章节数，章节节点展示章节号、标题及状态信息；保留章节选择、搜索、右键删除和卷入口行为。
- 目录使用自定义轻量节点与缩进连接线表达层级，不复制 `el-tree` 的组件样式；搜索结果同步按卷裁剪，保留原有无结果提示。
- 新增 ReadView 目录层级契约测试；验证结果：ReadView 相关 Maven 定向测试 4 个通过，`novel-agent-web` 执行 `npm run build` 通过，`git diff --check` 通过。
- 浏览器实际页面检查因本地无活动项目被路由守卫重定向到 `/project?redirect=/read`，未创建测试项目或写入业务数据；已完成源码级响应式约束检查。

## Task 53.6：Chapter Directory 增加真实 BOOK 层级数据（2026-09-03）

- `ReadView.vue` 复用现有 `/outlines/tree` 查询，与章节列表、卷分组并行加载 Outline 节点；书节点标题只取 `nodeKind === 'BOOK'` 的大纲节点，不再使用项目标题进行前端猜测或兜底。
- 无 BOOK 标题时保留书层级并显示“未设置书名”，同时清空无活动项目状态下的目录与 Outline 数据，避免旧项目标题残留。
- 更新 ReadView 目录契约测试；验证结果：ReadView 相关 Maven 定向测试 4 个通过，`novel-agent-web` 执行 `npm run build` 通过，`git diff --check` 通过。

## Task 53.7：章节目录直接显示章节号与标题（2026-09-03）

- `ReadView.vue` 章节节点直接使用“第 N 章 · 标题”单行标题语义，移除独立的 `chapter-index` 编号结构及其旧样式。
- 复用 Chapter Directory 契约测试验证数字章节号、标题连接符和旧编号结构均符合要求；验证结果：相关 Maven 定向测试 4 个通过，`novel-agent-web` 执行 `npm run build` 通过，`git diff --check` 通过。

## Task 53.8：章节标题允许最多两行显示（2026-09-03）

- `ReadView.vue` 取消章节标题的单行省略号规则，改用最多两行的自然换行和 `-webkit-line-clamp: 2`，保留目录宽度约束下的可读性。
- 更新 Chapter Directory 契约测试，覆盖换行、截断方式和行高；验证结果：相关 Maven 定向测试 4 个通过，`novel-agent-web` 执行 `npm run build` 通过，`git diff --check` 通过。

## Task 53.9：章节目录适当加宽（2026-09-03）

- `ReadView.vue` 将左侧章节目录桌面宽度调整为 `320px`，`1280px` 及以下降为 `280px`；正文列继续使用 `minmax(0, 1fr)`，移动端单列断点保持不变。
- 更新 ReadView 布局契约测试，覆盖桌面与窄屏目录宽度及左右列位置；验证结果：相关 Maven 定向测试 4 个通过，`novel-agent-web` 执行 `npm run build` 通过，`git diff --check` 通过。

## Task 53.10：章节目录信息降噪（2026-09-03）

- `ReadView.vue` 删除章节行右箭头，章节行保留章节号、标题、字数和定稿状态四项主要信息；旧的独立编号结构此前已移除。
- 选中态改为背景色、左侧 `primary` 边条和文字颜色共同表达，不增加状态图标；验证结果：相关 Maven 定向测试 4 个通过，`novel-agent-web` 执行 `npm run build` 通过，`git diff --check` 通过。

## Task 53.11：ReadView 统一中文 UI 字体栈（2026-09-03）

- `ReadView.vue` 将阅读页面基础字体、正文 textarea 和章节标题统一到 `Microsoft YaHei`、`PingFang SC`、`Noto Sans CJK SC`、`Noto Sans SC`、`sans-serif` 字体栈，移除 ReadView 正文的宋体 fallback，不引入字体文件。
- 新增 ReadView 字体栈契约测试；验证结果：相关 Maven 定向测试 5 个通过，`novel-agent-web` 执行 `npm run build` 通过，`git diff --check` 通过。

## Task 53.12：ReadView 正文字距恢复正常（2026-09-03）

- `ReadView.vue` 将正文 textarea 的 `letter-spacing` 从人为扩大值改为 `normal`，保持中文正文的自然字符间距，排除字体组合造成的视觉挤压变量。
- 更新 ReadView 字体与正文契约测试；验证结果：相关 Maven 定向测试 5 个通过，`novel-agent-web` 执行 `npm run build` 通过，`git diff --check` 通过。

## Task 53.13：ReadView 正文与标题恢复编辑器尺度（2026-09-03）

- `ReadView.vue` 将正文 textarea 调整为 `16px / 1.9 / normal`，章节标题控件调整为 `30px / 1.3 / 0`；移动端正文同步保持 `16px / 1.9`，移除旧的 `17px`、`2.15` 排版稿参数。
- 更新 ReadView 字体契约测试，覆盖正文与标题的新字号、行高和字距；验证结果：相关 Maven 定向测试 5 个通过，`novel-agent-web` 执行 `npm run build` 通过，`git diff --check` 通过。

## Task 53.14：ReadView 正文区按剩余工作区伸展（2026-09-03）

- `ReadView.vue` 将 `.chapter-paper` 调整为纵向 flex 容器，正文 textarea 使用 `flex: 1`，最小高度降为 `500px`；长正文继续由 `.paper-scroll` 承担滚动。
- 更新 ReadView 画布与字体契约测试；验证结果：相关 Maven 定向测试 5 个通过，`novel-agent-web` 执行 `npm run build` 通过，`git diff --check` 通过。

## Task 53.15：ReadView 目录最终层级表现（2026-09-03）

- `ReadView.vue` 将书节点与卷节点统一表现为可进入大纲的结构导航，并增加 `▾` 展开态层级提示；章节继续作为实际阅读入口，保留当前章节高亮和最多两行标题，不恢复无意义的 `01` 编号列。
- 更新章节目录契约测试；验证结果：相关 Maven 定向测试 5 个通过，`novel-agent-web` 执行 `npm run build` 通过，`git diff --check` 通过。

## Task 53.16：ReadView 章节搜索保留层级上下文（2026-09-03）

- `ReadView.vue` 保持章节搜索仅匹配章节号和标题；搜索结果通过 `filteredVolumeGroups` 重新筛选章节，但继续保留 Book 节点及对应 Volume 父节点。
- 扩展章节目录契约测试并验证通过：`ReadViewChapterDirectoryContractTest` 定向测试 1 个通过，`novel-agent-web` 执行 `npm run build` 通过，`git diff --check` 通过。

## Task 53.17：ReadView 目录层级与职责收敛（2026-09-03）

- `ReadView.vue` 明确书、卷、章的一级/二级/三级缩进，移除“新建卷”和目录内刷新按钮；目录仅保留书/卷导航、章节选择和必要的右键删除。
- 扩展章节目录契约测试并验证通过：`ReadViewChapterDirectoryContractTest` 定向测试 1 个通过，`novel-agent-web` 执行 `npm run build` 通过，`git diff --check` 通过。

## Task 53.18：ReadView 目录标题去重（2026-09-03）

- `ReadView.vue` 将目录顶部简化为“章节目录”和章节数，作品名只在树的 Book 节点显示，删除重复的“章节结构”副标题及无效 `.eyebrow` 样式。
- 更新章节目录契约测试并验证通过：ReadView 相关 Maven 定向测试 4 个通过，`novel-agent-web` 执行 `npm run build` 通过，`git diff --check` 通过。

## Task 53.19：ReadView 正文标题显示规则统一（2026-09-03）

- `ReadView.vue` 保持正文顶部仅显示“第 N 章”和可编辑章节标题，确认没有 `CHAPTER 01` 文案及 `.paper-kicker` 装饰节点。
- 扩展正文画布契约测试并验证通过：`ReadViewCanvasStyleContractTest` 定向测试 1 个通过，`git diff --check` 通过。

## Task 53.20：Generate 页增加初始化阶段（2026-09-03）

- `novel-agent-web/src/views/GenerateView.vue` 新增 `initializingGenerationPage`，首次进入页面时先读取现有 ChapterPlan，解析默认章节，设置 `chapterNum` 后加载对应计划；初始化前不再以第 1 章发起计划查询。
- 左侧章节计划摘要在初始化期间显示轻量 loading，章节输入和生成入口同步保持不可操作；章节号初始化前使用空值，避免第 1 章闪现后跳转到实际默认章节。
- 更新 `novel-agent-app/src/test/java/cn/ninth/novel/web/GenerateViewChapterPlanContractTest.java` 与 `ChapterGenerationSessionApiContractTest.java`，补充初始化状态和顺序契约。
- 验证结果：`novel-agent-web` 执行 `npm run build` 通过；Generate 相关前端契约定向测试 15 个通过；`git diff --check` 通过。

## Task 53.21：Generate 默认章节解析规则收敛（2026-09-03）

- `GenerateView.vue` 将无 URL 章节时的默认优先级收敛为：可恢复的活动 generation session、最大未完成 ChapterPlan、最大现有 ChapterPlan，最后回退第 1 章；不根据项目进度自动创建下一章。
- 复用现有按项目和章节查询 generation session 的接口，并按已有 ChapterPlan 章节号从大到小检查；终态 session 快照不参与默认章节选择，不新增 generation target 接口。
- 更新 Generate 默认章节优先级契约测试；验证结果：前端 `npm run build` 通过，相关 Maven 定向测试 16 个通过，`git diff --check` 通过。

## Task 53.22：Generate URL 指定章节优先（2026-09-03）

- `GenerateView.vue` 保持 URL `chapter` 作为默认章节解析的第一优先级；例如从 `/generate?chapter=7` 进入时直接设置并加载第 7 章，不参与活动 Session 和最新 ChapterPlan 的默认兜底选择。
- 增加 URL 章节优先回归契约测试；验证结果：相关 Maven 定向测试 17 个通过，`git diff --check` 通过。

## Task 53.23：Generate 初始化避免重复请求（2026-09-03）

- `GenerateView.vue` 将 ChapterPlan 的实际加载统一交给 `watch(chapterNum)`；初始化函数只负责读取计划、解析并设置章节号，初始化阶段复用已读取的计划列表，不再走初始化函数和 watcher 两条加载路径。
- 章节号由初始化设置时，watcher 跳过 SSE、正文、timeline 和已有 generation session 的清理，待当前计划加载后再结束 `initializingGenerationPage`，避免误清理恢复状态。
- 更新初始化顺序与重复请求契约测试；验证结果：相关 Maven 定向测试 18 个通过，`npm run build` 通过，`git diff --check` 通过。

## Task 53.24：Generate 手动切章节与初始化逻辑分开（2026-09-03）

- `GenerateView.vue` 增加 `generationPageInitialized`，初始化设置默认章节时只加载对应 ChapterPlan；初始化完成后，用户手动切换章节才执行 SSE、正文 preview、timeline 清理及新章节计划加载。
- 更新章节切换与恢复状态契约测试；验证结果：相关 Maven 定向测试 18 个通过，`npm run build` 通过，`git diff --check` 通过。

## Task 53.25：Read 页目录禁止跳转大纲页（2026-09-03）

- `ReadView.vue` 移除 Book/Volume 节点到 `/planning` 的导航行为，Book 只控制卷列表展开/收起，Volume 只控制章节列表展开/收起；章节节点继续负责阅读选择。
- 增加目录折叠状态和 `aria-expanded`，按项目切换时初始化展开状态并保留用户后续折叠结果。
- 更新 ReadView 目录契约测试；验证结果：Read 相关 Maven 定向测试 10 个通过，`npm run build` 通过，`git diff --check` 通过。

## Task 53.26：Read 页不复用 Outline 业务交互（2026-09-03）

- `ReadView.vue` 保持目录交互边界：Book/Volume 只负责展开收起，章节负责选择和右键操作；不引入 Outline Tree，也不带入新增子节点、AI 生成、移动、结构节点增删改或 Planning 导航。
- 扩展 ReadView 目录契约测试，固定 Outline 交互不得出现在 Read 页；验证结果：相关 Maven 定向测试 3 个通过，`git diff --check` 通过。

## Task 53.27：Read 目录明确三层层级（2026-09-03）

- `ReadView.vue` 使用卷分组的 `sequenceNo` 显示“卷一、卷二”等卷序号，形成“书 · 书名 / 卷一 · 卷名 / 第 N 章 · 章名”的三层目录。
- 统一 Book/Volume 展开箭头和两级子节点缩进，保留章节标题、章节号及层级关系；验证结果：ReadView 目录 Maven 定向测试 1 个通过，`npm run build` 通过，`git diff --check` 通过。

## Task 53.28：Read 卷标题与章节数量分区（2026-09-03）

- `ReadView.vue` 将卷主标题与章节数量拆成独立区域，章节数量靠右显示；通过容器查询在空间不足时隐藏 `.volume-count`，避免数量挤压卷标题。
- 更新 ReadView 目录契约测试；验证结果：ReadView 目录 Maven 定向测试 1 个通过，`npm run build` 通过，`git diff --check` 通过。

## Task 53.29：Read 章节主标题支持两行（2026-09-03）

- `ReadView.vue` 将章节号与章节名合并为单个主标题，格式为“第 N 章 · 章节名”；字数与状态显示在独立的第二行。
- 主标题使用 `line-height: 1.45` 并限制最多两行，移除旧的横向双列和单行省略样式；验证结果：ReadView 目录 Maven 定向测试 1 个通过，`npm run build` 通过，`git diff --check` 通过。

## Task 53.30：Read 目录章节选中态简化（2026-09-03）

- `ReadView.vue` 保持章节选中态仅使用左侧 3px primary bar、浅色背景和标题主色；章节行不引入箭头、编号列或 badge。
- 扩展 ReadView 目录契约测试，固定章节行的清爽交互边界；验证结果：ReadView 目录 Maven 定向测试 1 个通过，`git diff --check` 通过。

## Task 53.31：Outline ARC 显示已分配章节号（2026-09-03）

- `OutlineView.vue` 对 ARC 使用已分配的 `startChapter` 显示“第 N 章 · 标题”，详情区和抽屉复用同一章节编号标签。
- 移除独立的 ARC 章节位置拼接，不读取或恢复章节范围 UI；更新 OutlineView 契约测试。验证结果：`OutlineViewContractTest` 6 个测试与 `ArcRegenerationViewContractTest` 1 个测试通过，`npm run build` 通过，`git diff --check` 通过。

## Task 53.32：Outline 三层前缀统一（2026-09-03）

- `OutlineView.vue` 明确固定三层前缀：BOOK 为“书”、VOLUME 为“卷 + sequenceNo”、ARC 为“第 + startChapter + 章”，统一与标题组成“前缀 · 标题”。
- 移除大纲树前缀回退到通用“卷/章”的路径，不增加章节范围展示；更新 OutlineView 契约测试。验证结果：`OutlineViewContractTest` 6 个测试与 `ArcRegenerationViewContractTest` 1 个测试通过，`npm run build` 通过，`git diff --check` 通过。

## Task 53.33：Read/Outline 卷显示正式序号（2026-09-03）

- `ReadView.vue` 使用后端 `sequenceNo` 生成“卷一、卷二”等正式卷序号，`OutlineView.vue` 同样使用卷节点的 `sequenceNo`；两处均只保留轻量中文数字 formatter，不引入通用 numbering framework。
- 验证结果：相关 Maven 定向测试 8 个通过，`npm run build` 通过，`git diff --check` 通过。

## Task 53.34：Book 不使用序号（2026-09-03）

- Read/Outline 的 BOOK 前缀固定为“书”，仅显示“书 · 小说名”；卷序号 formatter 不作用于 BOOK，保持单项目单小说的目录语义。
- 更新 ReadView 与 OutlineView 契约测试，禁止出现“书一”等 Book 序号；验证结果：`OutlineViewContractTest` 6 个测试与 `ReadViewChapterDirectoryContractTest` 1 个测试通过，`git diff --check` 通过。

## Task 53.35：Outline 继续隐藏章节范围（2026-09-03）

- `OutlineView.vue` 保持 BOOK/VOLUME/ARC 的节点树、详情和编辑区不显示 `startChapter/endChapter`；ARC 仅使用已分配的 `startChapter` 显示“第 N 章”，范围字段继续作为后端结构信息保留。
- 新增卷或 AI 拆分所需的后台分配控件边界不变；强化 OutlineView 范围可见性契约。验证结果：`OutlineViewContractTest` 6 个测试通过，`git diff --check` 通过。

## Task 53.36：正文编辑器字体统一（2026-09-03）

- `ReadView.vue` 正文编辑器与 `GenerateView.vue` 正文预览统一使用 Microsoft YaHei、PingFang SC、Noto Sans CJK SC、Noto Sans SC、sans-serif 字体链。
- 不加载额外字体文件；更新 ReadView 字体与生成画布契约测试。验证结果：`CreationCanvasVisualContractTest` 11 个测试与 `ReadViewTypographyContractTest` 1 个测试通过，`npm run build` 通过，`git diff --check` 通过。

## Task 53.37：正文与标题字距收口（2026-09-03）

- Read 正文编辑器与 Generate 正文预览明确使用 `letter-spacing: normal`，Read 章节标题及 Generate 页面标题使用 `letter-spacing: 0`，移除正文区域的额外字距影响。验证结果：相关定向测试 12 个通过，前端构建通过。

## Task 53.38：正文排版参数统一（2026-09-03）

- Read 正文编辑器与 Generate 正文预览统一为 `font-size: 16px`、`line-height: 1.9`；Read 章节标题保持 `30px`、`line-height: 1.3`，处于要求的 28–32px 范围内。
- 移除正文预览的偏小字号，避免出现超大或失衡的杂志化排版。验证结果：相关定向测试 12 个通过，前端构建通过。

## Task 53.39：移除 Read 正文 CHAPTER 装饰（2026-09-03）

- Read 正文区域不再渲染英文 `CHAPTER 01` 装饰，只保留顶部 toolbar 的中文章节号与可编辑标题。
- 清理并验证正文画布契约边界。验证结果：`ReadViewCanvasStyleContractTest` 1 个测试通过。

## Task 53.40：Read 页面目录与正文布局收口（2026-09-03）

- Read 桌面目录列改为 `clamp(300px, 23vw, 320px)`，正文列继续使用 `minmax(0, 1fr)`，避免章节标题被压缩到约 250px 的窄栏。
- 正文画布继续以 `max-width: 820px` 居中，外围工作区保持自适应占满；toolbar 保持固定可见，正文区域作为阅读区滚动容器。
- 更新 Read 画布布局契约测试。验证结果：`ReadViewCanvasStyleContractTest` 与 `ReadViewTypographyContractTest` 共 2 个测试通过。

## Task 53.41：章节记忆同步文案统一（2026-09-03）

- Read 页同步按钮统一显示“更新章节记忆”，状态显示“正文已修改”，提示改为“正文已修改，更新章节记忆后后续章节才能使用最新内容。”
- 保持原有 `DIRTY` 判断、同步接口和后端逻辑不变。验证结果：`ChapterDerivedDataResyncUiContractTest` 1 个测试通过。

## Task 53.42：Generate 页面统一滚动区域（2026-09-03）

- Generate 页面由 `generation-page` 统一承担纵向滚动，`results-content` 保持 `overflow: visible`，移除左右内容区各自滚动的布局风险。
- 保持现有实时正文滚动定位逻辑改为操作页面容器。验证结果：`CreationCanvasVisualContractTest` 11 个测试通过。

## Task 53.43：移除 Generate 结果面板高度截断（2026-09-03）

- 确认 `.results-panel` 不再使用 `height: 100%` 或 `overflow: hidden`，内容高度可以随页面主滚动区域自然增长。
- 强化 Generate 单滚动区域契约，防止结果面板恢复高度截断。验证结果：`CreationCanvasVisualContractTest` 11 个测试通过。

## Task 53.44：SSE 自动滚动迁移到页面主容器（2026-09-03）

- SSE 滚动状态统一使用 `generationPageContainer`：`handleStreamingScroll` 读取页面事件目标，`scrollStreamingContentToBottom` 调用页面容器的 `scrollTo()`，`streamingAtBottom` 按页面容器高度计算。
- 增加契约检查，禁止恢复 `streamingContentContainer` 引用。验证结果：`CreationCanvasVisualContractTest` 12 个测试通过。

## Task 54.1：Read 目录三层对齐（2026-09-03）

- `novel-agent-web/src/views/ReadView.vue` 将书、卷、章统一为 `.directory-node`，通过 `level-book`、`level-volume`、`level-chapter` 和单一 `--directory-indent` 计算三级缩进；箭头、节点前缀和标题改为同一行基线，移除嵌套容器的横向 margin/padding 叠加。
- 选中章节改用内嵌阴影标识，避免边框改变文字起始位置；`ReadViewChapterDirectoryContractTest` 同步锁定层级 class、统一缩进规则和旧 margin 的移除。验证结果：该定向 Maven 测试 1 个通过，`npm run build` 通过，`git diff --check` 通过；浏览器在桌面、窄屏桌面、移动端宽度检查无横向溢出，但因无活动项目页面被项目访问守卫拦截，未创建测试项目或写入业务数据。

## Task 54.2：Read 目录缩进只由 level 控制（2026-09-03）

- `ReadView.vue` 移除动态缩进变量，明确使用 `.level-book` `12px`、`.level-volume` `32px`、`.level-chapter` `52px` 的固定左内边距；书、卷节点的展开箭头统一使用 `.node-toggle` 的 `16px` 固定宽度，保证箭头状态变化不影响标题位置。
- 更新 `ReadViewChapterDirectoryContractTest`，锁定三档 padding、固定箭头占位和动态 level 变量不回归。验证结果：ReadView 目录定向 Maven 测试、`npm run build` 与 `git diff --check` 通过。

## Task 54.3：Read 目录文字基线统一（2026-09-03）

- `ReadView.vue` 将 Book/Volume 主文字统一为 `13px / 600 / 1.45`，Chapter 统一为 `13px / 500 / 1.45`，节点前缀与主文字共用同一基线；层级差异继续由缩进、颜色和字重表达，字数与状态等辅助信息保持小字号。
- 更新 `ReadViewChapterDirectoryContractTest` 与 `ReadViewTypographyContractTest`，锁定目录三层字号、字重和行高。验证结果：相关 ReadView 定向 Maven 测试、`npm run build` 与 `git diff --check` 通过。

## Task 54.4：章节点击仅更新本地选中状态（2026-09-03）

- `ReadView.vue` 的 `selectChapter` 只更新 `selectedNumber`，移除章节点击时的路由 query 写入和无效 `useRouter` 依赖；章节切换不再触发路由层级变化。
- 更新 `ReadViewChapterDirectoryContractTest`，锁定章节选择函数为本地状态变更并禁止 `router.replace`。验证结果：ReadView 目录定向 Maven 测试、`npm run build` 与 `git diff --check` 通过。

## Task 54.5：深链接章节仅用于首次进入（2026-09-03）

- `ReadView.vue` 在页面初始化时只读取一次 `const requested = Number(route.query.chapter)`；首次数据加载按深链接定位章节，后续刷新保留当前本地选中章节或回退到首章，不再重复使用 URL 驱动章节切换。
- 扩展 `ReadViewChapterDirectoryContractTest`，锁定深链接读取只出现一次、初始化标记和本地章节选择边界。验证结果：ReadView 目录定向 Maven 测试、`npm run build` 与 `git diff --check` 通过。

## Task 54.6：相邻章节切换仅更新本地状态（2026-09-03）

- `ReadView.vue` 的上一章/下一章直接从已加载的 `chapters` 计算目标并更新 `selectedNumber`；结合目录点击路径，三种章节入口均不修改路由。
- 扩展 `ReadViewChapterDirectoryContractTest`，锁定 `goAdjacent` 使用本地章节数组和选中状态。验证结果：ReadView 目录定向 Maven 测试、`npm run build` 与 `git diff --check` 通过。

## Task 54.7：Read 章节列表按需刷新（2026-09-03）

- `ReadView.vue` 增加“刷新目录”主动入口；章节目录仍只在项目切换/首次进入时通过 `watch(..., { immediate: true })` 初始化加载，手动刷新沿用同一个 `refresh()`。
- 目录点击、上一章、下一章和 `selectedNumber` 监听均不调用 `refresh()`；保存使用返回章节更新本地列表，删除直接同步本地章节与卷目录，避免无必要的列表请求。
- 扩展 `ReadViewChapterDirectoryContractTest`，锁定刷新入口及章节切换不触发刷新。验证结果：定向 Maven 测试 4 个通过，`npm run build` 与 `git diff --check` 通过。

## Task 54.8：Read 正文使用已加载章节数据（2026-09-03）

- `ReadView.vue` 保持 `selectedChapter` 从本地 `chapters` 计算；`selectedNumber` 变化后仅同步 `editTitle` 与 `editContent`，章节切换不新增正文 API 请求。
- 新增 `ReadViewEditingContractTest`，锁定目录切换、相邻切换和正文编辑器均使用已加载章节数据，并禁止切换路径调用 `getChapter`。验证结果：定向 Maven 测试 5 个通过，`npm run build` 与 `git diff --check` 通过。

## Task 54.9：章节切换不触发加载态（2026-09-03）

- `ReadView.vue` 的 `loading` 仅用于项目切换/首次目录请求；用户主动刷新目录使用独立的 `refreshing` 状态，已有目录不会被切换成首次加载骨架。
- 目录点击、上一章和下一章只更新 `selectedNumber`，不设置 `loading`、`refreshing`，也不触发章节列表请求。
- 扩展 `ReadViewChapterDirectoryContractTest`，锁定首次加载与主动刷新的状态边界。验证结果：定向 Maven 测试 5 个通过，`npm run build` 与 `git diff --check` 通过。

## Task 54.10：保留底部刷新目录入口（2026-09-03）

- `ReadView.vue` 将唯一的显式“刷新目录”入口放在目录底部，并直接调用 `refresh()`；按钮使用 `refreshing` 展示主动刷新状态。
- 保持章节切换只使用本地 `chapters` 数据，用户只有主动点击“刷新目录”时才重新获取服务端最新目录数据。
- 更新 `ReadViewChapterDirectoryContractTest`，锁定底部刷新入口和直接调用 `refresh()`。验证结果：定向 Maven 测试 5 个通过，`npm run build` 与 `git diff --check` 通过。

## Task 54.11：目录节点不跳转 Planning（2026-09-03）

- `ReadView.vue` 的 Book/Volume 节点点击仅调用本地展开/折叠函数，章节节点点击仅切换正文；目录中不保留 Planning 跳转行为。
- `ReadViewChapterDirectoryContractTest` 锁定 `router.push('/planning')`、`useRouter` 等目录跳转实现不回归。验证结果：定向 Maven 测试 5 个通过，`npm run build` 与 `git diff --check` 通过。

## Task 54.12：目录展开状态本地维护（2026-09-03）

- `ReadView.vue` 使用 `bookExpanded` 和 `expandedVolumeCodes` 保存本地展开状态；首次加载默认展开 Book，并仅展开当前章节所在卷，后续刷新同项目时保留用户展开状态。
- Book/Volume 展开折叠只修改本地状态，不调用章节或大纲 API；章节切换路径保持不变。验证结果：定向 Maven 测试 5 个通过，`npm run build` 与 `git diff --check` 通过。

## Task 54.13：章节行合并为完整标题（2026-09-03）

- `ReadView.vue` 将章节号与标题统一渲染为“第 N 章 · 标题”单行完整标题，字数与状态单独显示在第二行。
- 新增章节标题契约检查，禁止恢复独立章节号节点。验证结果：定向 Maven 测试 6 个通过，`npm run build` 与 `git diff --check` 通过。

## Task 54.14：章节主标题与副信息分离（2026-09-03）

- `ReadView.vue` 使用 `div.chapter-node-copy` 包含章节 `strong` 主标题和 `small` 副信息，移除旧的 `chapter-copy` 类名。
- 章节行不再维护 `chapter-index`、`row-arrow` 等额外列元素，避免目录节点出现三列挤压。验证结果：定向 Maven 测试 6 个通过，`npm run build` 与 `git diff --check` 通过。

## Task 54.15：章节标题最多两行（2026-09-03）

- `ReadView.vue` 将章节主标题的两行限制直接应用到 `.chapter-node-copy strong`，使用 `-webkit-line-clamp: 2`、`white-space: normal` 和 `line-height: 1.4`，避免标题被压缩为单行省略。
- 更新目录与字体契约测试，锁定标题最多两行的样式规则。验证结果：定向 Maven 测试 7 个通过，`npm run build` 与 `git diff --check` 通过。

## Task 54.16：Toolbar 章节号与标题字号统一（2026-09-03）

- `ReadView.vue` 保留可编辑标题输入框，将章节号与标题输入统一为 `20px / 1.3 / 600`，toolbar 使用 baseline 对齐和 `8px` 间距，章节号使用较淡的 `var(--text-muted)` 颜色。
- 章节号与标题以“第 N 章 · 标题”连续显示，避免小字号章节号与超大标题形成视觉断裂。验证结果：定向 Maven 测试 8 个通过，`npm run build` 与 `git diff --check` 通过。

## Task 54.17：章节选中 watcher 仅同步编辑值（2026-09-03）

- `ReadView.vue` 保留 `watch(selectedNumber, ...)` 对 `editTitle` 和 `editContent` 的同步职责，不在选中章节变化时调用 `refresh()` 或章节列表 API。
- 不增加 `route.query.chapter` watcher，路由章节参数仍只用于初始化定位。新增 watcher 契约测试，验证结果：定向 Maven 测试 7 个通过，`npm run build` 与 `git diff --check` 通过。

## Task 54.18：切换未保存章节前确认（2026-09-03）

- `ReadView.vue` 增加 `hasLocalEdits`，比较当前编辑值与 `selectedChapter` 的标题和正文；目录点击、上一章和下一章统一在切换前检查本地未保存修改。
- 发现修改时使用 Element Plus 确认框提供“继续编辑”和“放弃修改并切换”，确认后才更新 `selectedNumber`，不引入自动保存。
- 更新 ReadView 目录与编辑契约测试，锁定未保存提示和切换确认边界。验证结果：定向 Maven 测试 7 个通过，`npm run build` 与 `git diff --check` 通过。

## Task 54.19：Outline 标题去输入框视觉（2026-09-03）

- `OutlineView.vue` 将主编辑区标题从带标签的表单项调整为文档式标题输入，保留 Element Plus `el-input` 的编辑能力并移除标准边框、阴影和下划线视觉。
- `OutlineViewContractTest` 增加主标题无边框契约检查，验证标题字段结构和相关旧样式已移除。验证结果：定向 Maven 测试 7 个通过，`npm run build` 与 `git diff --check` 通过。

## Task 54.20–54.22：Outline 文档标题式编辑（2026-09-03）

- `OutlineView.vue` 将主标题编辑器命名为 `outline-title-editor`，使用 28px、600 字重的文档标题样式；普通和 focus 状态均保持透明无边框、无阴影。
- 主标题继续隐藏字段 label，“大纲内容”保留原有 label，形成标题直显、正文带 label 的创作编辑结构。
- `OutlineViewContractTest` 补充 class、placeholder、字号和 focus 样式契约。验证结果：定向 Maven 测试 7 个通过，`npm run build` 与 `git diff --check` 通过。

## Task 54.23–54.28：Outline 详情与树节点命名核验（2026-09-04）

- `OutlineView.vue` 的 Detail 编辑区和移动端编辑抽屉不显示章节范围，树节点不显示 `tree-range`，Detail header 仅保留书、卷序号或 ARC 的章节号标识。
- 左侧树节点沿用“书 · 名称”“卷一 · 名称”“第 1 章 · 名称”的前缀规则；新增下级大纲中的章节范围仍属于创建流程，不影响 Detail 页面。
- 扩展 `OutlineViewContractTest`，锁定 Detail header 和左侧树节点的无范围、可读命名结构。验证结果：定向 Maven 测试 7 个通过，`npm run build` 与 `git diff --check` 通过。

## Task 54.29–54.31：Generate 默认章节计划解析（2026-09-04）

- `GenerateView.vue` 初始化先请求当前项目 ChapterPlan 列表，再按 URL chapter、最大有效 `chapterNumber`、第 1 章的顺序确定 `chapterNum`；不再查询活动 Session、区分未完成计划或推导下一章。
- 最大章节计划不受 `COMPLETED` 状态排除，所有状态均参与“最新章节计划”解析；初始化失败时保留 URL 章节，否则回退第 1 章，并避免重复请求计划列表。
- 更新 `GenerateViewChapterPlanContractTest`，锁定初始化顺序、默认章节优先级和单次计划加载边界。验证结果：定向 Maven 测试 18 个通过，`npm run build` 与 `git diff --check` 通过。

## Task 54.32：Read 与 Outline 交互职责隔离（2026-09-04）

- Read 章节目录继续在 `ReadView.vue` 内负责章节导航、阅读和展开收起；Outline 大纲树继续在 `OutlineView.vue` 内负责结构编辑、AI、删除和排序。
- 新增 `ReadOutlineInteractionSeparationContractTest`，锁定两页不抽取共用业务组件，只保持视觉层级的一致性。

## Task 54.33：清理 Read/Outline 旧样式（2026-09-04）

- 清理并锁定 `chapter-index`、`row-arrow`、`tree-range`、`chapterRangeLocked` UI 和 Outline 旧标题 wrapper 不再出现在页面源码中。
- `range-editor` 当前仍被新增下级大纲和 AI 拆分创建流程使用，因此保留有效样式；同步清理过时的 ReadView 契约断言。
- 验证结果：定向 Maven 测试 17 个通过，`npm run build` 与 `git diff --check` 通过。

## Task 54.A：ReadView 去掉路由驱动切章（2026-09-04）

- 核验 `ReadView.vue` 的章节目录、上一章和下一章均只更新本地 `selectedNumber`；`route.query.chapter` 只在初始化时读取一次用于深链接定位，不再写入路由。
- `ReadViewEditingContractTest` 增加路由切章隔离契约，锁定 `router.replace`、章节 query 写入和重复读取不回归。验证结果：定向 Maven 测试 8 个通过，`npm run build` 与 `git diff --check` 通过。

## Task 54.B：ReadView 书卷节点只负责展开收起（2026-09-04）

- 核验 `ReadView.vue` 的书、卷节点仅调用本地 `toggleBook`、`toggleVolume`，不跳转 `/planning` 或写入路由。
- 使用 `bookExpanded` 和 `expandedVolumeCodes` 保存本地展开状态，并补充 `ReadViewChapterDirectoryContractTest` 防止页面导航行为回归。验证结果：定向 Maven 测试 9 个通过，`npm run build` 与 `git diff --check` 通过。

## Task 54.D：ReadView 章节标题整体显示（2026-09-04）

- 核验 `ReadView.vue` 章节节点将章节号与标题合并为“第 N 章 · 标题”，字数与状态单独位于第二行；旧 `chapter-index`、`row-arrow` 结构不再出现在页面源码中。
- 章节主标题使用 `-webkit-line-clamp: 2`、自然换行和 `line-height: 1.4`，并补充 `ReadViewChapterDirectoryContractTest` 回归契约。
- 验证结果：定向 Maven 测试 10 个通过，`npm run build` 与 `git diff --check` 通过。

## Task 54.E：ReadView 顶部章节标题统一为 h1（2026-09-04）

- `ReadView.vue` 将顶部章节号和可编辑章节标题统一放入单个 `h1.toolbar-title-text`，共享同一组字号、行高和字重；保留标题编辑能力，输入框仅继承 h1 的字体样式。
- 清理旧的 `.toolbar-title`、章节号与标题分开设置字号的样式，并更新正文画布、编辑和字体契约测试。
- 验证结果：定向 Maven 测试 12 个通过，`npm run build` 与 `git diff --check` 通过。

## Task 54.F：ReadView 已加载章节切换不重新拉取目录（2026-09-04）

- `ReadView.vue` 的章节点击、上一章和下一章继续只通过 `selectedNumber` 切换本地 `chapters` 数据，不调用 `refresh()`；`selectedChapter` computed 继续作为正文切换来源。
- 保留首次进入、项目切换和底部“刷新目录”触发目录同步；删除章节后改为调用一次 `refresh()`，重新同步章节列表、卷分组和大纲节点。
- 验证结果：定向 Maven 测试 13 个通过，`npm run build` 与 `git diff --check` 通过。

## Task 54.G：ReadView 未保存切章保护（2026-09-04）

- `ReadView.vue` 比较当前 `editTitle`、`editContent` 与选中章节原值；存在本地修改时，目录点击、上一章和下一章统一先弹出未保存确认。
- “继续编辑”直接取消切换，“放弃修改并切换”后才更新本地 `selectedNumber`；确认流程不触发章节目录请求。
- 验证结果：定向 Maven 测试 12 个通过，`npm run build` 与 `git diff --check` 通过。

## Task 4.H：OutlineView 标题彻底采用文档式无边框编辑（2026-09-04）

- `OutlineView.vue` 的桌面详情编辑区和移动端详情抽屉均移除“标题”表单 label，统一使用 `outline-title-editor` 编辑标题。
- 标题输入的普通和 focus 状态均保持透明背景、无边框、无阴影，并使用 28px、600 字重、1.35 行高的文档标题样式；“大纲内容” label 保持不变。
- 验证结果：OutlineView 定向 Maven 测试 8 个通过，`npm run build` 与 `git diff --check` 通过。

## Task 54.I：OutlineView 章节范围 UI 彻底移除（2026-09-04）

- `OutlineView.vue` 删除树节点、详情编辑区、手动新增和 AI 拆分预览中的章节范围控件、范围提示、范围校验及相关零引用 CSS。
- 保留 `startChapter/endChapter` 数据字段和保存 payload；手动新增卷未提供范围时，由 `PlanningService` 在父节点剩余章节中自动分配连续范围。
- 更新 `OutlineViewContractTest` 和 `ManualOutlineCrudTest`，验证结果：定向 Maven 测试 20 个通过，`npm run build` 与 `git diff --check` 通过。

## Task 54.J：OutlineView 左树命名统一（2026-09-04）

- `OutlineView.vue` 左树统一使用“书 · 名称”“卷一 · 名称”“第 N 章 · 名称”；ARC 的 N 直接来自自身 `startChapter`。
- 扩展 `OutlineViewContractTest`，禁止树节点退回“卷 ·”或“章 ·”前缀。
- 验证结果：OutlineView 定向 Maven 测试通过，`npm run build` 与 `git diff --check` 通过。

## Task 54.K：GenerateView 默认显示最新 ChapterPlan（2026-09-04）

- 核验 `GenerateView.vue` 首次初始化顺序为先调用 `listChapterPlans()`，再按 URL chapter、最大有效 `chapterNumber`、第 1 章依次解析默认章节。
- 最大章节计划不按状态过滤，因此 `COMPLETED` 仍参与最新计划选择；不推导下一章，也不会从第 3 章自动跳到第 4 章。
- 验证结果：`GenerateViewChapterPlanContractTest` 定向测试通过，`git diff --check` 通过。

## Task 54.L：GenerateView 首屏初始化 loading 门控（2026-09-04）

- `GenerateView.vue` 使用 `initializingGenerationPage` 门控整个生成工作区；ChapterPlan 列表、默认章节解析和当前计划加载完成前，只显示轻量 loading，不先渲染第 1 章或未命名章节内容。
- 扩展 `GenerateViewChapterPlanContractTest`，锁定 `listChapterPlans → resolve chapterNum → loadCurrentChapterPlan → initializing=false` 的首屏顺序。
- 验证结果：定向 Maven 测试 9 个通过，`npm run build` 与 `git diff --check` 通过。

## Task 54.M：GenerateView 初始化与 chapter watch 单一加载入口（2026-09-04）

- `GenerateView.vue` 将 `loadCurrentChapterPlan()` 收敛到 `chapterNum` watcher；初始化只加载 ChapterPlan 列表并解析 `chapterNum`，使用初始列表缓存避免重复请求。
- 章节计划确认后直接同步本地状态；“生成下一章”等待 watcher 的同一次加载 Promise，不再额外调用计划加载函数。
- 扩展 `GenerateViewChapterPlanContractTest`，验证加载入口数量和初始化期间不重复触发。验证结果：定向 Maven 测试 9 个通过，`npm run build` 与 `git diff --check` 通过。

## Task 54.N：清理旧代码和样式（2026-09-04）

- 全局核验并清理 Read/Outline 前端中的 `chapter-index`、`row-arrow`、`tree-range`、`range-editor`、章节范围锁定 UI/展示逻辑，以及旧标题 input wrapper 样式；Read 目录不再包含 `/planning` 导航和章节切换路由写入。
- 修正 `AiSplitOutlineUiContractTest` 的过期断言，保留 `startChapter/endChapter` 作为后台 payload 字段；补充 Read/Outline 旧样式与隐藏导航的反向契约检查。
- 验证结果：相关定向 Maven 测试 24 个通过，`npm run build` 与 `git diff --check` 通过。

## Task 55.1：移除连续章节生成模式（2026-09-04）

- `GenerateView.vue` 删除“单章 / 连续章节”切换及 `mode` 状态，页面固定使用当前章节号和单章生成会话。
- 移除连续章节起止输入、批量结果展示、批量提交状态/函数、批量 API 与类型引用，并清理对应样式；更新 `GenerateViewChapterPlanContractTest` 锁定单章入口和连续模式不回归。
- 验证结果：`GenerateViewChapterPlanContractTest` 9 项通过，`npm run build` 与 `git diff --check` 通过。

## Task 55.2：删除批量章节输入（2026-09-04）

- 核验 `GenerateView.vue` 的章节生成输入只保留 `chapterNum`，`batchStart`、`batchEnd`、`batchCount` 及起止章节文案均不存在。
- 扩展 `GenerateViewChapterPlanContractTest` 的反向契约，验证批量章节输入不会回归。
- 验证结果：GenerateView 定向契约测试通过，`git diff --check` 通过。

## Task 55.3：删除前端 batch 状态（2026-09-04）

- 核验 `GenerateView.vue` 不再引用 `batching`、`batchResult`、`completedBatchCount` 或 `GenerateChaptersResponse`，前端页面只维护单章生成状态。
- 扩展 `GenerateViewChapterPlanContractTest` 的反向契约，防止批量状态和类型重新进入 GenerateView。
- 验证结果：GenerateView 定向契约测试通过，`git diff --check` 通过。

## Task 55.4：删除 batch UI（2026-09-04）

- 核验 `GenerateView.vue` 右侧结果区只保留当前章节的实时正文、章节状态和工作流操作，不再包含连续章节结果区域。
- 扩展 `GenerateViewChapterPlanContractTest`，禁止 `batch-overview`、`chapter-results`、`chapter-result-row` 及相关旧结果 class 回归。
- 验证结果：GenerateView 定向契约测试通过，`git diff --check` 通过。

## Task 55.5：简化 submitGeneration（2026-09-04）

- 删除 `GenerateView.vue` 中仅转发到单章生成函数的 `submitGeneration`，将“生成本章”按钮直接绑定 `generateSingleChapter`。
- 更新 `GenerateViewChapterPlanContractTest`，确保按钮直接调用单章生成且旧转发入口不回归。
- 验证结果：前端构建、GenerateView 定向契约测试和 `git diff --check` 均通过。

## Task 55.6：删除 generateChapterRange（2026-09-04）

- 核验 `GenerateView.vue` 已彻底删除 `generateChapterRange()`，且不再导入或调用 `generateBatch()`；页面仅通过单章会话生成正文。
- 保留未被本项要求删除的 `api/chapter.ts` 批量接口封装，不影响 GenerateView 的单章边界。
- 验证结果：GenerateView 定向契约测试通过，`git diff --check` 通过。

## Task 55.7：删除前端 generateBatch API（2026-09-04）

- 确认 `generateBatch` 及 `GenerateChaptersRequest`、`GenerateChaptersResponse` 在前端已零引用后，从 `api/chapter.ts` 和 `types/index.ts` 删除对应 API 封装与类型。
- 后端批量 DTO、Controller 和后端测试保持不变，仅移除已无页面消费者的前端代码。
- 验证结果：前端构建、GenerateView 定向契约测试和 `git diff --check` 均通过。

## Task 55.8：删除后端批量生成接口（2026-09-04）

- 全局核验确认批量生成链路仅由 `NovelChapterBatchController`、`I/ChapterBatchService`、`ChapterBatchGenerationResultVO`、`GenerateChapters*DTO` 及其专属测试组成，已全部删除。
- 清理 README 的批量生成接口示例和代码审查文档中的失效 `ChapterBatchService` 说明；同步修正页面滚动、章节会话契约测试中对旧批量结果的过期断言。
- 保留单章 `NovelChapterController`、`IChapterService` 和 `GenerateChapterResponseDTO` 链路；后端批量 DTO、VO、Controller、Service 和测试不再存在。
- 验证结果：`ChapterGenerationSessionApiContractTest`、`CreationCanvasVisualContractTest`、`GenerateViewChapterPlanContractTest` 共 31 项通过，七模块 Reactor 构建成功，`git diff --check` 通过。

## Task 55.9：保留生成下一章并改为仅定位计划（2026-09-04）

- `GenerateView.vue` 保留“生成下一章”按钮；完成当前章节后只将 `chapterNum` 切换到下一章并等待对应 `ChapterPlan` 加载，不再自动调用单章正文生成。
- 下一章无计划时继续展示“当前章节不存在章节计划”，由用户通过 AI 生成/确认计划后再手动生成正文；完成区文案同步改为“切换到下一章”。
- 新增 `GenerateViewChapterPlanContractTest` 契约，锁定下一章定位不创建生成会话、不触发正文生成。
- 验证结果：`GenerateViewChapterPlanContractTest` 10 项通过，`npm run build` 通过，`git diff --check` 通过。

## Task 55.11：重命名下一章按钮（2026-09-04）

- 将完成态按钮从“生成下一章”改为“继续下一章”，按钮加载状态仅表示下一章定位过程。
- 同步更新 Generate 页面契约测试，明确按钮不会直接启动正文生成。
- 验证结果：`CreationCanvasVisualContractTest` 12 项、`GenerateViewChapterPlanContractTest` 10 项通过，`npm run build` 通过，`git diff --check` 通过。

## Task 54.O：Codex 固定使用 Clash 代理（2026-09-04）

- 新增 `scripts/codex-with-clash.ps1`，启动前检查 Clash `127.0.0.1:7899`，并仅为本次 Codex 进程设置 HTTP/HTTPS/ALL_PROXY 环境变量；脚本结束后恢复原环境变量。
- 使用方式：`powershell -ExecutionPolicy Bypass -File .\scripts\codex-with-clash.ps1`；Codex 参数可直接追加，端口可通过 `-ProxyPort` 覆盖。
- 验证结果：本机 `codex` 命令可定位，`127.0.0.1:7899` 端口连通，脚本通过 PowerShell 语法解析检查，`git diff --check` 通过。

## Task 54.P：桌面双击启动 Codex（2026-09-04）

- 新增 `scripts/启动Codex-走Clash7899.cmd`，通过 PowerShell 启动桌面版 Codex，并复用 Clash `127.0.0.1:7899` 代理脚本。
- 验证结果：CMD 文件已完成静态检查，所调用的 PowerShell 脚本通过 `codex --version` 启动验证。

## Task 54.Q：修复桌面启动参数绑定（2026-09-04）

- 调整 `scripts/codex-with-clash.ps1` 参数位置，将 Codex 参数设为第一个位置参数并保留 `ValueFromRemainingArguments`，避免 `.cmd` 传入的 `app` 被误解析为 `ProxyPort`。
- 验证结果：`app`、`--version` 和 `--help` 均能正确转发，7899 端口检查与 Codex 版本启动验证通过。

## Task 55.12：保留单章 Session 工作流（2026-09-04）

- 核对单章 `generation session`、SSE、停止、接受、恢复、审稿、压缩和持久化链路均保留，未因批量能力删除而改动。
- 验证结果：`ChapterGenerationSessionApiContractTest` 10 项通过。

## Task 55.13：清理连续章节相关界面文案（2026-09-04）

- 全局核验前端界面无“连续章节”“批量生成”“生成 N 章”“起始章节”“结束章节”“第 X 至 Y 章”等连续生成概念；补充 Generate 页面契约断言防止文案回归。
- 领域规划中的章节范围字段属于大纲语义，不属于连续章节生成 UI，保持不变。
- 验证结果：`GenerateViewChapterPlanContractTest` 10 项通过，`npm run build` 通过，`git diff --check` 通过。

## Task 55.14：删除无效类型和 CSS（2026-09-04）

- 前端全局检索确认 `GenerationMode`、批量状态/类型及 `batch-*` 结果样式均已无引用，不存在死代码。
- 验证结果：前端生产源码、类型声明和样式零命中，`git diff --check` 通过。

## Task 55.15：确认最终 Generate 页面结构（2026-09-04）

- Generate 页保持左侧当前章节、ChapterPlan 摘要与计划操作、单章生成入口，右侧当前章节标题、单章工作流时间线、实时正文、审稿问题和人工审核操作；完成态保留“查看正文”和“继续下一章”。
- 单章 Session 工作流与相关操作未被批量能力清理影响。
- 验证结果：`ChapterGenerationSessionApiContractTest` 10 项、`CreationCanvasVisualContractTest` 12 项、`GenerateViewChapterPlanContractTest` 10 项通过，`npm run build` 通过。

## Task 56.1-56.2：DRAFT 历史正文边界盘点与收口（2026-09-04）

- 核实真实调用链为 `ChapterService.LOAD_CONTEXT` → `ChapterContextLoader` → `ContextRepository.loadHistory` → `ChapterContextAggregate` → `DraftChapterNode.buildDraftUserPrompt`；`ContextRepository` 的完整正文查询只按 `chapterNumber - 1` 查询一条，`RECENT_MEMORY_CHAPTER_LIMIT = 8` 只用于 `story_summary` 的 ChapterMemory 查询。
- 明确 DRAFT 当前传入内容：当前 `ChapterPlan` 的标题/摘要；Story Bible 的硬规则、力量体系、世界背景、文风指南；全部人物的姓名及角色定位、性别、年龄、外貌、性格、背景故事、作者备注；此前章节（`N-2` 及更早）的 ChapterMemory 短摘要、关键事件、未解决问题、结尾钩子；以及直接上一章的章节号、标题和完整正文。项目标题、题材、当前章节号、当前章节标题和目标字数作为写作参数传入；旧 Fact、旧 Summary 字段、人物运行时状态和其他完整正文没有进入 DRAFT。仓储最多加载 8 章候选 Memory，但 `N-1` 章 Memory 不进入 DRAFT 最终 Prompt。
- DRAFT 增加直接前章校验，只有 `previousChapter.chapterNumber == currentChapterNumber - 1` 才渲染完整正文；第 1 章或章节号错位时不传正文。8 章历史仍保留为压缩记忆，不再被解释为 8 章完整正文。
- 新增 `ChapterNodeTest` 回归覆盖第 10 章保留第 9 章完整正文、过滤第 8 章错位正文，以及第 1 章不带上一章正文。
- 验证结果：`ChapterNodeTest` 29 项通过，Reactor 定向构建成功，`git diff --check` 通过。

## Task 56.3：更早历史统一使用 ChapterMemory（2026-09-04）

- 保持现有 `ChapterMemoryVO` 结构不变；DRAFT 对第 `N-2` 章及更早历史继续只渲染 `shortSummary`、`keyEvents`、`unresolved`、`endingHook`，不读取或拼接这些章节的完整正文。
- `ContextRepository.loadHistory` 继续通过现有 `story_summary.queryRecent` 加载最多 8 章已确认记忆，并由 `ChapterMemoryMapper` 映射为现有 `ChapterMemoryVO`；未新增 Memory 字段，也未修改 Compression Schema。
- 扩展 DRAFT 回归测试，覆盖较早章节 Memory 的四个既有字段与直接上一章完整正文的并存边界。
- 验证结果：`ChapterNodeTest` 29 项通过，`git diff --check` 通过。

## Task 56.4：固定 DRAFT 历史上下文结构（2026-09-04）

- DRAFT Prompt 分区顺序固定为：`本章计划` → `上一章正文` → `此前章节记忆` → `人物资料` → `故事设定`，必要的本章写作参数继续单独放在末尾。
- `N-1` 只承担直接衔接，完整正文仅进入“上一章正文”；`N-2` 及更早章节只进入“此前章节记忆”，使用现有 `ChapterMemoryVO` 四字段。DRAFT 系统提示词同步固定为“上一章负责直接衔接，此前章节记忆负责保持长期剧情连续性”。
- REVIEW、REVISE 继续使用各自既有上下文规则，未改变它们的审稿/改稿输入契约。
- 验证结果：`ChapterNodeTest` 29 项通过，覆盖分区顺序、`N-1` Memory 排除、`N-2` Memory 四字段和唯一上一章正文；Reactor 定向构建成功，`git diff --check` 通过。

## Task 56.5：DRAFT 上一章正文与 Compression 去重（2026-09-04）

- 明确 DRAFT 的去重规则：第 `N-1` 章只传完整正文，不再传该章 `shortSummary`、`keyEvents`、`unresolved` 或 `endingHook`；第 `N-2` 章及更早章节继续传现有完整 `ChapterMemoryVO` 四字段。
- 未新增 Memory 字段，也未修改 Compression Schema；在 DRAFT 历史记忆组装循环中固定排除 `N-1`，并补充回归测试锁定同一剧情不得以正文和 Compression 双份进入 Prompt。
- 验证结果：`ChapterNodeTest` 29 项通过，Reactor 定向构建成功，`git diff --check` 通过。

## Task 56.6：保持既有 ChapterMemory 范围（2026-09-04）

- 复核 DRAFT 历史记忆组装，没有新增 Memory 章数、摘要字数或关键事件条数限制；现有 `ContextRepository` 的 `RECENT_MEMORY_CHAPTER_LIMIT = 8` 继续沿用原有查询范围。
- 本轮只保留两项边界：完整正文仅传直接上一章，以及直接上一章的 ChapterMemory 不重复传入；更早章节 Memory 的现有字段和原有取数范围不变。
- 验证结果：`ChapterNodeTest` 29 项通过，Reactor 定向构建成功，`git diff --check` 通过。

## Task 56.7：不新增人物状态抽取（2026-09-04）

- 复核本轮改动未引入 `characterStates`、`currentState` 抽取、人物状态表或人物状态 Memory；DRAFT、REVIEW、REVISE 的章节上下文不渲染人物运行时状态字段。
- `CompressChapterNode` 和现有 `ChapterMemoryVO` 继续只使用 `shortSummary`、`keyEvents`、`unresolved`、`endingHook` 表达人物及剧情变化，未恢复已简化掉的状态模型。
- 验证结果：`ChapterNodeTest` 29 项通过，Reactor 定向构建成功，`git diff --check` 通过。

## Task 56.8：不做 DRAFT 人物语义匹配（2026-09-04）

- DRAFT 继续直接遍历现有 `ChapterContextAggregate.characters`，沿用当前已经工作的角色资料上下文；未新增 `RelevantCharacterSelector`、名字匹配、embedding、人物召回或人物打分。
- 本轮改动范围保持在章节历史上下文：上一章完整正文与更早章节 ChapterMemory 的分层及去重；人物资料组装逻辑不增加新的筛选或召回层。
- 验证结果：`ChapterNodeTest` 29 项通过，Reactor 定向构建成功，`git diff --check` 通过。

## Task 56.9：去掉重复的上一章摘要来源（2026-09-04）

- 核实 DRAFT 的 `ChapterContextAggregate` 通过 `ChapterHistoryVO` 只接收 `recentMemories` 和 `previousChapter`，不存在并行注入的 `previousChapterContent`、`previousChapterSummary` 或独立 `storySummary` 字段。
- `previousChapterSummary` 仅属于章节计划生成的 Planning 上下文，不进入 DRAFT；DRAFT 对 `recentMemories` 中的 N-1 也不渲染，只保留 N-1 完整正文，避免上一章摘要重复注入。
- 未删除仍被其他节点使用的字段或 API；复用现有 DRAFT 去重回归测试验证 N-1 摘要、关键事件、未解决问题和结尾钩子均不进入 DRAFT。
- 验证结果：`ChapterNodeTest` 29 项通过，Reactor 定向构建成功，`git diff --check` 通过。

## Task 56.10：ChapterMemory 使用自然语言格式（2026-09-04）

- DRAFT 的更早章节 Memory 继续渲染为“摘要 / 关键事件 / 未解决 / 结尾钩子”等自然语言标签和列表，不输出 `shortSummary=`、`keyEvents=[]`、`unresolved=`、`endingHook=` 等技术格式或 JSON。
- 空的摘要、事件、未解决问题或结尾钩子不输出对应 section；不改变 ChapterMemory 字段、内容范围或其他节点的既有格式。
- 新增 DRAFT 回归测试覆盖空 Memory 字段不生成空 section；验证结果：`ChapterNodeTest` 30 项通过，Reactor 定向构建成功，`git diff --check` 通过。

## Task 56.11：历史 Memory 按章节顺序稳定排列（2026-09-04）

- DRAFT 在历史 Memory 渲染入口按 `chapterNumber` 升序排列更早章节记忆，每条继续以“第 X 章”明确标注来源，不把不同章节的 `keyEvents` 合并成无来源列表。
- 仓储原有 `story_summary.queryRecent` 升序结果继续保留；DRAFT 额外在 Prompt 边界稳定排序，确保即使上游传入乱序列表，模型仍能看到正确的剧情先后关系。
- 强化现有乱序 Memory 回归测试，验证第 6 章在第 8 章之前渲染且各章来源标题保留；验证结果：`ChapterNodeTest` 30 项通过，Reactor 定向构建成功，`git diff --check` 通过。

## Task 56.12：DRAFT System Prompt 保留核心生成约束（2026-09-04）

- 将 DRAFT System Prompt 收敛为已确认章节计划、故事设定与历史连续性、当前章节边界、目标篇幅和正文输出协议五类核心约束。
- 合并重复的“只输出正文”表达及细碎禁止项；REVIEW、REVISE、COMPRESSION 的 System Prompt 和输出 Schema 未改变。
- 新增 Prompt 边界回归测试，限制 DRAFT System Prompt 只保留精简核心约束；验证结果：`ChapterNodeTest` 30 项、`PromptBoundaryContractTest` 5 项通过，Reactor 定向构建成功，`git diff --check` 通过。

## Task 56.13：删除 DRAFT Prompt 中的后端实现细节（2026-09-04）

- 核对 DRAFT System/User Prompt 未包含数据库字段、状态枚举、节点类型、Graph 流程、DTO/VO、持久化、sequence、workflowId、chapterPlanId 或后端校验等实现信息。
- 保留章节创作真正需要的业务语义，例如章节号、章节标题、目标字数、故事设定和历史剧情；未删除仍供后端或其他节点使用的内部字段和 API。
- 新增 DRAFT 专属 Prompt 边界断言，锁定 System/User Prompt 不暴露上述后端术语；验证结果：`ChapterNodeTest` 30 项、`PromptBoundaryContractTest` 6 项通过，Reactor 定向构建成功，`git diff --check` 通过。

## Task 56.14：删除 DRAFT Prompt 重复规则（2026-09-04）

- 核对 DRAFT User Prompt 只承载本章计划、历史、人物资料和故事设定等业务上下文，不重复写入“遵守计划、只写本章、不要提前未来剧情、只输出正文”等生成规则。
- DRAFT System Prompt 将章节计划、当前章节边界和正文输出协议各保留一处清晰表达，删除重复的角色说明、阶段性结果和最终输出措辞；其他节点 Prompt 未改变。
- 验证结果：`ChapterNodeTest` 30 项、`PromptBoundaryContractTest` 6 项通过，并锁定“本章计划”在 DRAFT System Prompt 中只出现一次；Reactor 定向构建成功，`git diff --check` 通过。

## Task 56.15：收敛 DRAFT User Prompt 为上下文（2026-09-04）

- DRAFT User Prompt 保持本章计划、历史正文与记忆、人物资料、故事设定和写作参数等业务上下文，不加入生成指令性措辞。
- 将 Story Bible 的文风指南从“故事设定”中拆为独立的“写作风格”区段，保留上下文分区的自然语言呈现；DRAFT System Prompt 继续集中承载生成规则。
- 新增 User Prompt 边界回归测试，锁定上下文区段顺序及“你必须/你禁止/务必/请注意/只输出/不要提前”等生成指令不泄漏；验证结果：`ChapterNodeTest` 31 项、`PromptBoundaryContractTest` 6 项通过，Reactor 定向构建成功。

## Task 56.16：收敛 DRAFT Story Bible 上下文（2026-09-04）

- 核对 DRAFT 实际 Story Bible 渲染，仅保留当前正文需要的 `worldBackground`、`hardRulesJson`、特殊体系 `powerSystemJson` 和 `styleGuide`；`oneSentencePremise`、`coreTheme`、`mainConflict`、`endingDirection` 当前未进入 DRAFT，因此不做无依据的机械删除。
- 新增 Story Bible 字段边界回归测试，验证章节相关设定会自然语言渲染、全书梗概/主题/主线矛盾/结局方向不会重复灌入 DRAFT；未修改 Story Bible 数据结构或 Planning/Outline Prompt。
- 验证结果：`ChapterNodeTest` 32 项、`PromptBoundaryContractTest` 6 项通过，Reactor 定向构建成功，`git diff --check` 通过。

## Task 56.17：去除 DRAFT 中重复的 ChapterPlan（2026-09-04）

- 删除 DRAFT“本章写作参数”中重复输出的当前章节号和章节标题，当前 `ChapterPlan` 仅在“本章计划”区段渲染一次。
- 保留上一章正文区段中的章节号和标题作为历史正文标识，写作参数仍保留小说标题、题材和目标字数等上下文。
- 新增重复计数回归断言；验证结果：`ChapterNodeTest` 32 项、`PromptBoundaryContractTest` 6 项通过，Reactor 定向构建成功，`git diff --check` 通过。

## Task 56.18：增加 DRAFT 上下文大小日志（2026-09-04）

- 在 DRAFT 完成 System/User Prompt 组装、进入模型重试执行前增加一次 `DRAFT context` 日志，记录章节号、两类 Prompt 字符数、上一章正文字符数、实际传入的 Memory 数量与字符数、人物上下文字符数和 Story Bible 字符数。
- 字符数直接使用 Java 字符串长度，Memory 数量按实际渲染到“此前章节记忆”区段的章节数统计；未引入 tokenizer，也未改变上下文内容和取数规则。
- 验证结果：定向运行 `ChapterNodeTest` 32 项、`PromptBoundaryContractTest` 6 项全部通过，日志在模型调用前实际输出，Reactor 构建成功，`git diff --check` 通过。

## Task 56.19：DRAFT 日志不输出正文内容（2026-09-04）

- DRAFT `context` 日志仅输出章节号、字符数和数量，不输出上一章正文、完整 Prompt 或 ChapterMemory 内容；既有 Prompt Trace 日志也只输出长度字段。
- 验证结果：定向测试日志实际出现 `DRAFT context` 数值记录，未输出上下文正文；`ChapterNodeTest`、`PromptBoundaryContractTest` 和 Mapper XML/接口契约测试通过。

## Task 56.20：核对 REVIEW 历史正文范围（2026-09-04）

- REVIEW 当前仅组装当前章节计划、当前待审正文、上一章 ChapterMemory 及必要人物/故事设定，没有“最近 8 章完整正文”查询或 Prompt 注入；按要求不做无必要改动。

## Task 56.21：核对 COMPRESSION 仅处理当前章（2026-09-04）

- COMPRESSION User Prompt 当前只包含当前章节号和最终正文，不读取或注入历史正文、上一章正文或 ChapterMemory。
- 新增回归测试锁定历史内容不得进入压缩 Prompt；验证结果：`ChapterNodeTest` 33 项通过。

## Task 56.22：保持 ChapterPlan 现有上下文方案（2026-09-04）

- 未扩展 ChapterPlan 或 Planning/Outline Prompt；仅保留此前已确认的 DRAFT 当前计划去重调整，不将本轮范围扩展为所有 Prompt 重构。

## Task 56.23：删除 DRAFT 多章正文旧参数（2026-09-04）

- 全局代码搜索未发现旧的多章正文参数引用。
- 无生产引用的章节正文 `queryRecent` DAO 方法及 Mapper SQL 已删除，旧测试调用同步移除；没有保留 DRAFT 多章全文新旧两套实现。

## Task 56.24：收口章节正文 Repository 查询职责（2026-09-04）

- DRAFT 历史加载继续通过 `storyChapterDao.queryByProjectIdAndChapterNumber(projectId, chapterNumber - 1)` 获取单一上一章正文，并通过 `storySummaryDao.queryRecent` 获取 ChapterMemory。
- 删除 `IStoryChapterDao.queryRecent` 及对应 XML 查询；`IStoryChapterDao` 当前不再提供无调用的最近多章完整正文接口。
- 验证结果：DRAFT/Prompt/COMPRESSION 测试及 `MvpPersistenceContractTest.shouldProvideDaoInterfacesAndXmlMappers` 共 40 项通过，Reactor 构建成功；数据库集成测试因本机 `root@localhost` 凭据拒绝访问未能执行，另有既存 schema 契约中的 `chapter_model_trace` 表差异，未纳入本次修改。

## Task 57.1：Outline 滚动式下一步生成（2026-09-04）

- 将规划领域、HTTP DTO 和前端 API 统一改为 `generateNextOutline/confirmNextOutline`：BOOK 只生成一个下一卷，VOLUME 只生成一个下一章，ARC 不再生成子大纲；移除 Outline 的 `preferredCount`、列表 Draft、批量确认和批量保存入口。
- `CHILD_OUTLINE_SYSTEM` 收敛为单对象 `title/summary` 输出，章节位置、节点编码、顺序和状态由服务端补齐；前端 Draft Dialog 删除生成数量和多项列表编辑，改为一次预览并确认一个下一步节点。
- 下一卷的卷号、节点编码、顺序和当前可用章节范围均由服务端分配；本轮不扩展卷边界动态收口规则。
- 前端 BOOK 操作文案按现有卷数显示“生成卷一大纲”“生成卷二大纲”等，VOLUME 显示“生成下一章大纲”。
- 验证结果：后端生产代码编译通过；规划相关定向测试 22 项通过；前端 `npm run build` 通过。单独执行 `PlanningRepositoryTest` 时，本机 MySQL 拒绝了配置账号 `root@localhost` 的连接，数据库集成验证未完成。

## Task 57.3：VOLUME 滚动生成下一章（2026-09-04）

- VOLUME → ARC 继续保持单个下一章生成，服务端按当前卷已有 ARC 的最大章节号加一分配 chapterNumber；例如已有第 28～30 章时分配第 31 章，前端和确认请求不提交章节号。
- 前端 VOLUME 的 AI 操作文案按当前卷进度显示“生成第 N 章大纲”，移除 ARC 生成数量语义。
- 验证结果：新增“已有第 28～30 章后分配第 31 章”的领域测试；相关 Maven 定向测试与前端 `npm run build` 通过。

## Task 57.4～57.10：活动卷边界动态收口（2026-09-04）

- 保留 `endChapter` 字段；首卷继续使用 BOOK 的目标章节数作为临时上界，当前最后一卷按活动卷处理，允许 ARC 持续增长，不因临时上界到达而提前封卷。
- 新增规划服务专用的 `finalizeCurrentVolumeAndCreateNext` 事务操作：锁定当前最后一卷及其 ARC，按最大 `ARC.startChapter` 收缩上一卷实际结束章，再创建从实际结束章加一开始、以上层 BOOK 目标章节为临时上界的下一卷。
- 空卷不能创建下一卷；普通 `updateOutlineNode` 的子节点/章节计划范围保护保持不变，卷边界收口不复用用户范围更新入口；DAO 增加对应的 `FOR UPDATE` 查询。
- 验证结果：新增“第 27 章收口并从第 28 章创建下一卷”和“空卷拒绝创建下一卷”测试；规划相关 Maven 定向测试 31 项通过，前端 `npm run build` 通过。

## Task 57.11～57.12：下一卷 Prompt 衔接前置卷（2026-09-04）

- BOOK → VOLUME 的 Prompt 新增“已规划卷”上下文，按卷序提供所有前置卷标题和摘要，并单独强调上一卷标题、摘要及其最大已规划章节号；不注入正文。
- 下一卷 System Prompt 明确承接上一卷阶段性结果、推进全书主线下一阶段、形成新阶段目标/冲突/转折/结果，禁止重复前置卷、提前完成全书剧情或规划具体章节。
- 模型输出协议收敛为只返回 `title` 和 `summary`，不返回卷号、章节号或章节范围；VOLUME → ARC 的下一章 Prompt 行为保持不变。
- 验证结果：新增卷二/卷三前置卷上下文回归测试；`ChildOutlineGenerationTest` 与 `PromptBoundaryContractTest` 共 11 项通过。

## Task 57.13～57.16：下一章增量 Prompt 与章节目标上限（2026-09-04）

- 新增独立的 `NEXT_VOLUME_SYSTEM` 与 `NEXT_CHAPTER_OUTLINE_SYSTEM`；VOLUME → ARC 的下一章 Prompt 传入当前卷、上一章、当前卷已有章纲、目标章节号和必要创作设定，模型只返回 `title`、`summary`，章节号由服务端绑定。
- 下一章按当前卷 ARC 的最大实际章节号加一分配；当下一章超过项目预计章节数时，在模型调用前返回“预计章节数已达到 N 章，请先调整预计章节数后继续创作。”，不创建 Draft。
- 新增项目预计章节数调整 POST 接口和项目页入口；事务内更新项目目标、BOOK 临时上界及当前活动卷临时上界，已封口卷的实际结束章保持不变。
- 新增真实仓储集成用例，验证提高目标后 BOOK 与活动卷上界同步、已封口卷范围不变，并按实际已完成章节号保护缩小目标值。
- `targetChapterCount` 的领域、实体和界面文案统一表达为“预计章节数”；它仍是当前规划边界，但不是永久不可超过的硬上限。
- 验证结果：规划、项目服务、HTTP 和 Prompt/UI 契约定向测试共 58 项通过；前端 `npm run build` 通过；`git diff --check` 通过。仓储集成用例沿用现有 MySQL 测试配置，未在本机重复执行。

## Task 57.20～57.22：下一卷/下一章入口与 Draft Preview 结构（2026-09-04）

- BOOK/VOLUME 的 AI 生成入口统一显示为“生成下一卷”和“生成下一章”；弹窗保留具体目标标题，并在生成下一卷时显示上一卷标题及当前已规划章节进度，不再提供数量交互。
- Draft Preview 将卷号或章节号作为只读结构前缀展示，分别形成“卷 N · 标题”和“第 N 章 · 标题”的预览结构；标题与大纲内容仍可编辑。
- 更新 Outline UI 契约测试，验证滚动生成入口、卷上下文和结构编号展示；前端构建及该定向测试均通过。

## Task 58：错误响应与用户提示统一收口（2026-09-04）

- `ResponseCode` 增加错误码解析和默认提示能力；`Response` 与 `GlobalExceptionHandler` 统一按“明确业务提示优先，否则使用 `ResponseCode.message`”生成 API `info`，未处理异常和 `ResponseStatusException` 均不再返回底层 reason。
- API 统一异常输出补充 HTTP 契约：Controller 不再自行拼接异常响应，`GlobalExceptionHandler` 将审稿技术异常统一输出为 `E0004`、用户可执行提示和 `data=null`，原始模型解析异常仅写日志。
- 未预期 `Exception`/`RuntimeException` 统一使用 `0001` 和“系统暂时出现异常，请稍后重试。”，处理器通过 `ERROR` 日志保留完整 stacktrace，浏览器不再收到空指针、SQL 或框架原始信息。
- 前端 `http.ts` 删除拦截器自动 `ElMessage.error()`；业务响应、HTTP 错误和网络错误统一转换为只含友好文案的 `ApiBusinessError`，由页面决定使用 toast、状态或 dialog，避免重复提示。
- 58.10 将 `http.ts` 职责限定为后端响应标准化：只生成包含 `code`/`message` 的 `ApiBusinessError`，通过 `resolveFallbackMessage` 提供安全兜底文案，不承担任何页面 UI 展示。
- 58.11 页面按交互场景消费标准化错误：大纲、设定和阅读页的普通操作由页面使用 `ElMessage.error(error.message)`，正文工作流写入 `workflowFailureMessage`，审稿失败和人工决策继续展示页内错误状态及重试、采用、停止等操作。
- 错误码保持有限集合：参数/业务条件复用 `0002`，系统未知复用 `0001`，模型异常复用现有 `E0001`～`E0008`；未新增 `E010x` 等技术异常码，并增加错误码契约测试验证未知技术码回退为 `0001`。
- `0002` 下明确的业务条件文案原样作为用户提示返回（例如“当前卷尚未规划章节，请先完成本卷章节规划。”）；只有包含节点字段、DTO 字段、JSON 等技术详情的校验信息才进入 `internalDetail`，不再统一拼接“非法参数：”。
- `AppException` 将用户提示 `info` 与日志排查用 `internalDetail` 分离，新增 `withInternalDetail` 工厂；章节工作流、模型结构化响应、JSON 校验、节点标识、DTO 字段和持久化约束详情均改为内部详情，并通过异常字符串和显式日志保留排查信息。
- 模型调用失败保留原始 cause，Prompt Trace 和工作流失败日志记录 `workflowId`、`projectCode`、`chapterNumber`、`node`、`errorCode`、异常及 cause；Trace 的 `errorMessage`、API `info` 和 SSE 失败 `content` 只保存安全用户提示。
- 章节生成失败、审稿失败和终止事件在 Session 事件边界再次拦截 Jackson/Reactor/Flux/SpringAI/SQL/nodeCode 等技术详情；Session 命令、Trace 查询和项目控制器的校验文案改为用户语言。
- 58.12～58.16 将 SSE 失败事件收口为固定用户文案：审稿失败使用“自动审稿失败，但正文已保留。你可以重新审稿、直接采用当前正文或停止本次生成。”；正文生成、章节整理和章节保存分别使用对应的重试提示，未知失败统一回退系统提示；节点日志仍保留完整异常和上下文。
- 章节工作流在 DRAFT、REVIEW、REVISION、COMPRESSION、PERSIST 阶段捕获异常后只发布安全 `GENERATION_FAILED.content`；Session 事件构造器仅放行预定义文案，前端恢复快照和终止事件也经过同一安全白名单，审稿问题区不再展示底层失败原因。
- 验证结果：最终相关定向测试 111 项通过（Failures=0、Errors=0）；补充规划字段收口后，`ChapterPlanSingleConfirmationTest`、`ChildOutlineGenerationTest`、`ManualOutlineCrudTest` 再通过 25 项；58.5～58.6 错误响应、工作流、项目服务和人物仓储定向测试再通过 51 项；58.7～58.8 API/SSE 统一异常输出定向测试再通过 18 项；58.9～58.10 前端 HTTP 错误契约测试再通过 2 项；58.11 页面错误展示契约测试再通过 2 项，正文 Session、阅读页和 HTTP 契约回归通过，`npm run build` 通过；补充仓储定向测试时，现有共享 MySQL 清理阶段因 `fk_outline_node` 外键残留报错，未归因于本次代码改动。
- 58.12～58.16 验证结果：`ChapterGenerationSessionRegistryTest`、SSE HTTP、Session HTTP、前端 Session/页面错误契约定向测试共 30 项通过；`npm run build` 通过；`git diff --check` 通过。
- 清理 `AppException.toString()` 中遗留的旧项目异常类名，统一输出为 `AppException`，保留错误码、用户信息和内部详情字段；异常响应相关定向测试随后复核。
- 58.17 将 `AppException` 的带 message 构造器移除，仅保留 `code`/`cause` 基础公开构造器；新增 `user(...)` 与 `internal(...)` 显式工厂，结构化模型响应异常继续通过受保护的 internal 语义构造。
- 58.17 迁移全部带 message 的 `new AppException(...)` 调用：业务条件和 `ResponseCode.message` 改用 `AppException.user(...)`，模型重试测试和技术详情改用 `AppException.internal(...)`；`ChapterModelPort` 的模型请求失败显式保留用户提示并记录 cause。
- 58.17 验证结果：`ResponseErrorContractTest`、`GlobalExceptionHandlerHttpTest`、`ChapterModelRetryExecutorTest`、`ChapterNodeTest`、`NovelProjectServiceTest`、`NovelProjectControllerHttpTest` 共 91 项通过；新增构造器可见性、user/internal 语义及 `ChapterModelResponseException.getUserMessage()` 安全断言；`git diff --check` 通过。
- 58.17 收尾复验：`ResponseErrorContractTest`、`GlobalExceptionHandlerHttpTest` 共 8 项通过（Failures=0、Errors=0），确认结构化模型异常的 JSON/Jackson/rawText 详情仍只保留在 internal 详情中。
- 58.18 `GlobalExceptionHandler` 按异常语义收口日志级别：`0002/E0008` 等预期业务异常使用不带堆栈的 WARN；含 `internalDetail`、`0001`、`E0001`～`E0007` 或未知 AppException 使用 ERROR 并保留异常链；未知 `Exception` 继续使用 ERROR。
- 58.18 `ResponseStatusException` 仅记录 HTTP status 和请求路径，保留原 HTTP 状态与安全的 `0001` 响应，不记录或返回 reason；新增 Logback 定向测试覆盖 WARN/ERROR、堆栈保留及 reason 隔离。
- 58.18 验证结果：`GlobalExceptionHandlerLogLevelTest`、`GlobalExceptionHandlerHttpTest` 共 7 项通过（Failures=0、Errors=0）；`git diff --check` 通过。
- 58.19 删除前端 `http.ts` 中 `E0001`～`E0008` 的具体业务文案映射，`resolveFallbackMessage()` 只保留 `0001` 系统异常和 `0002` 通用参数提示；未知错误码及缺失后端 `info` 统一回退 `0001`，具体业务语义继续以后端响应为准。
- 58.19 保持页面按交互场景消费 `ApiBusinessError.message`，普通操作、工作流状态和人工决策的展示职责不回收到 HTTP interceptor；前端 `npm run build` 通过，`E0001`～`E0008` 在前端仅保留必要的流程控制判断。
- 58.20 删除仓库根目录下的 `logPath_IS_UNDEFINED/` 运行产物，并在 `.gitignore` 中加入普通日志、轮转压缩日志和该异常目录规则，避免运行日志再次进入版本管理。
- 58.20 将开发环境日志目录明确为 `./logs`、应用名明确为 `novel-agent`；`logback-spring.xml` 的文件路径和轮转路径增加 `./logs`/`novel-agent` 内联默认值，即使 Spring 属性尚未注入也不会生成 `*_IS_UNDEFINED`。
- 58.20 验证结果：`GlobalExceptionHandlerHttpTest` 定向启动测试 3 项通过（Failures=0、Errors=0）；确认 `logPath_IS_UNDEFINED/` 不存在，日志文件使用 `novel-agent` 命名；`git diff --check` 通过。测试期间生成的 `logs/` 文件仍被运行中的 Java 进程占用，已保持忽略且未强制终止进程。
- 58.21 按当前 schema 外键关系新增测试专用项目清理 helper：先删 `story_summary`、`story_chapter`、`chapter_plan`，再循环删除 `outline_node` 叶子到根，最后删除其他项目子表和 `novel_project`；所有相关 DAO、仓储和规划 teardown 已统一复用，未修改业务外键，也未使用 `FOREIGN_KEY_CHECKS=0`。
- 58.21 定位结论与验证：`fk_outline_node_parent` 报错来自测试 teardown 直接删除自引用大纲树，属于清理顺序问题，不是业务外键设计问题；当前测试库未发现本次测试项目残留。`ChapterDeletionRepositoryTest`、`ChapterMemoryCrossChapterPlanningTest`、`NovelProjectRepositoryVolumeTest`、`PlanningRepositoryTest` 连续运行两次共 16 项通过（Failures=0、Errors=0），确认上一轮数据不会影响下一轮。

## Task 59.1～59.4：ChapterPlan 与 Compression Prompt 收敛（2026-09-05）

- `CharacterBriefVO` 和 `RelevantCharacterSelector` 将 ChapterPlan 人物摘要收敛为名称、角色、当前目标和当前位置，ChapterPlan System/User Prompt 不再输出“人物当前状态”；角色实体及兼容存储字段保持不变。
- `PlanningService` 检查并处理 ChapterPlan 的重复上下文：当 N-1 章 ChapterMemory 已包含可用短摘要时省略重复的上一章摘要；没有可用 N-1 Memory 摘要时继续使用上一章计划摘要作为回退。
- `SystemPrompt.COMPRESSION_SYSTEM_PROMPT` 删除重复规则和示例，保留短期记忆压缩边界、关键事件/未解决问题/结尾钩子语义及原有四字段 Schema。
- `PlanningPrompts.STORY_BIBLE_INIT_SYSTEM` 与 `STORY_BIBLE_REVISION_SYSTEM` 将特殊体系内容调整为“题材确实涉及才写入，涉及内容完整保留”，不改变 `name`、`description`、`levels`、`supplement` 结构。
- 更新 ChapterPlan、人物摘要、Story Bible 和 Prompt 边界定向测试；验证结果：Maven 定向测试 28 项全部通过（Failures=0、Errors=0），`git diff --check` 通过。

## Task 60：Story Bible 与正式角色边界修正（2026-09-05）

- `STORY_BIBLE_INIT_SYSTEM` 与 `STORY_BIBLE_REVISION_SYSTEM` 明确只定义故事方向和世界设定，不创建正式人物；涉及人物时使用剧情功能、身份或处境称谓，不主动起具体姓名，但尊重用户明确提供的姓名。
- 新增 Story Bible 双 Prompt 角色边界契约测试，验证初始化与修订均包含上述约束。
- Story Bible 的 `oneSentencePremise`、`mainConflict`、`worldBackground`、`endingDirection` 明确优先使用功能、身份或处境描述，避免模型主动造角色姓名。
- 验证结果：`StoryBibleGenerationTest` 定向测试 7 项全部通过（Failures=0、Errors=0），`git diff --check` 通过。

### Task 60.3：Character Prompt 明确 Story Bible 人物描述边界（2026-09-05）

- `CHARACTER_SYSTEM` 改为根据当前 Story Bible 和已有正式人物补充新的故事人物；已有角色是已确认设定，不创建替代版本，也不新增承担完全相同剧情功能的人物。
- Story Bible 中的功能称谓仍不视为已创建人物；未被已有角色摘要确认的具体姓名仅在用户补充要求明确指定时予以尊重，否则优先按故事功能重新设计。
- Story Bible 中的正式人物以“已有角色摘要”为准；已有角色承担的功能优先复用，新人物优先补充尚缺功能，不为凑数量重复类型或剧情作用。
- 更新人物 Prompt 契约测试；验证结果：`CharacterPromptContractTest` 定向测试 1 项通过（Failures=0、Errors=0），`git diff --check` 通过。

### Task 60.7：preferredCount 明确为新增数量（2026-09-05）

- 人物生成 User Prompt 将数量文案从“建议生成 N 名核心人物”改为“本次建议新增 N 名人物”，明确 `preferredCount` 是基于已有角色的增量数量。
- 更新人物生成服务定向测试断言；验证结果：`CharacterGenerationServiceTest` 定向测试 8 项通过（Failures=0、Errors=0），`git diff --check` 通过。

### Task 60.8～60.10：人物补充 UI 与边界保持（2026-09-05）

- 前端人物生成入口、弹窗标题和操作按钮统一改为“AI 补充人物”，数量字段改为“新增数量”，补充说明改为围绕人物功能缺口展开。
- 本轮未新增男主/女主/反派唯一性、主角数量或角色类型唯一约束；后端继续只保留已有的同名角色防重复校验。
- 保持 Story Bible 与 Character 的松耦合，不新增角色 ID、角色编码、角色引用表或功能映射字段。
- 更新前端人物生成 UI 契约测试；验证结果：`CharacterGenerationUiContractTest` 定向测试 1 项通过（Failures=0、Errors=0），前端 `npm run build` 通过，`git diff --check` 通过。

### Task 60.11：ROOT_OUTLINE 标题归属收敛（2026-09-05）

- `RootOutlineDraftVO` 与 `ROOT_OUTLINE_SYSTEM` 收敛为模型只返回 `summary`；根节点标题不再由模型生成。
- `PlanningService` 在根大纲生成、确认以及根节点创建/更新时统一使用 `project.title` 绑定 BOOK 标题；根大纲确认接口和前端请求同步移除独立 `title` 字段。
- 前端根大纲 Draft 预览及已存在 BOOK 节点编辑均展示只读项目标题，只允许编辑大纲内容；保持 Story Bible、Character 与 Outline 的松耦合，不新增角色关联或唯一性约束。
- 更新根大纲生成、确认、HTTP、手动 BOOK 归属和前端 UI 契约测试；验证结果：Maven 定向测试共 19 项通过（Failures=0、Errors=0），前端 `npm run build` 通过，`git diff --check` 通过。

### Task 60.12：BOOK 标题展示统一为项目标题（2026-09-05）

- `OutlineView.vue` 的 BOOK 树节点、桌面详情标题、移动端抽屉标题和新增下级上下文统一通过项目标题展示；旧 BOOK 节点标题也不会继续显示为独立书名。
- BOOK 标题编辑控件保持只读，VOLUME 与 ARC 继续使用自身标题；大纲搜索同步使用 BOOK 的项目标题。
- 更新 `OutlineViewContractTest` 的展示契约；验证结果：前端相关 Maven 定向测试 10 项通过（Failures=0、Errors=0），`npm run build` 通过，`git diff --check` 通过。

### Task 61.1～61.4：Review 无问题直接进入 COMPRESSION（2026-09-05）

- 调整 `novel-agent-domain/src/main/java/cn/ninth/novel/domain/chapter/service/workflow/ReviewRouter.java`：有效审稿报告的问题列表为空时直接返回 `COMPRESSION`，优先于人工返修阶段判断；保留有问题时既有严重度、自动改稿轮次和人工处理边界，`REVIEW_FAILED` 仍通过内部人工检查点保持可恢复状态。
- 更新 `novel-agent-app/src/test/java/cn/ninth/novel/domain/chapter/service/workflow/ReviewRouterTest.java`，覆盖人工返修轮次存在时空问题报告仍直接进入 `COMPRESSION`。
- 验证结果：`ReviewRouterTest` 定向测试 6 项全部通过（Failures=0、Errors=0）。

### Task 61.5～61.9：采用当前正文恢复章节后处理（2026-09-05）

- 确认 `novel-agent-domain/src/main/java/cn/ninth/novel/domain/chapter/service/ChapterService.java` 的 ACCEPT 语义：Session 标记 accepted 后，在 `WAITING_HUMAN` 或 `REVIEW_FAILED` 检查点异步调用 `resumeChapter(workflowId, HumanDecisionEnum.PASS)`，由 `HumanDecisionRouter` 进入 `COMPRESSION`，再沿章节图进入 `PERSIST` 和完成态；不会只更新 Session 标记，也不会强制重新审稿。
- 确认 `HumanDecisionRouter` 的 PASS 唯一路由为 `COMPRESSION`，REVISE 继续进入自动修改，ABORT 进入工作流结束节点。
- 新增 `novel-agent-app/src/test/java/cn/ninth/novel/domain/chapter/service/ChapterServiceAcceptTest.java`，覆盖 `WAITING_HUMAN` 与 `REVIEW_FAILED` 下 accepted 标记和 PASS 恢复触发；扩展 `ChapterHumanCheckpointResumeTest` 验证 `REVIEW_FAILED + PASS` 从 checkpoint 完成 `COMPRESSION → PERSIST`。
- 修正 `ChapterHumanCheckpointResumeTest` 中与人工返修重置自动轮次行为不符的旧断言，保持生产逻辑不变。
- 验证结果：`ChapterServiceAcceptTest`、`ChapterHumanCheckpointResumeTest`、`HumanDecisionRouterTest`、`ReviewRouterTest` 定向测试共 18 项全部通过（Failures=0、Errors=0），使用 `dev` 配置的真实 MySQL checkpoint 测试通过；`git diff --check` 通过。

### Task 61.10：ACCEPT 幂等避免重复恢复（2026-09-05）

- 调整 `novel-agent-domain/src/main/java/cn/ninth/novel/domain/chapter/service/session/ChapterGenerationSessionRegistry.java`：已设置 accepted 标记的重复 ACCEPT 优先返回 `alreadyAccepted`，即使首次恢复已经完成并进入终态，也不会再次调度工作流恢复。
- 扩展 `novel-agent-app/src/test/java/cn/ninth/novel/domain/chapter/service/ChapterServiceAcceptTest.java`，验证连续 ACCEPT 只触发一次 `HumanDecisionEnum.PASS` 恢复；扩展 `ChapterGenerationSessionRegistryTest`，验证终态后的重复 ACCEPT 仍保持幂等。
- 验证结果：`ChapterServiceAcceptTest` 3 项与 `ChapterGenerationSessionRegistryTest` 11 项定向测试全部通过（Failures=0、Errors=0），确认不会重复执行 `COMPRESSION`/`PERSIST`；`git diff --check` 通过。

### Task 61.11～61.13：采用后即时转态与 Session 快照收口（2026-09-05）

- 调整 `ChapterService` 与 `ChapterGenerationSessionRegistry`：首次从 `WAITING_HUMAN`/`REVIEW_FAILED` ACCEPT 时立即发布一次可去重的 `COMPRESSION_STARTED`，Session 同步进入 `COMPRESSING`，后续继续沿真实图执行 `COMPRESSION → PERSIST → COMPLETED`。
- Session 快照新增并维护 `currentNode`；阶段开始时清空旧 `failureMessage`、清理旧人工状态，阶段完成事件追加去重的 `completedStages`，`GENERATION_COMPLETED` 收口为 `status=COMPLETED`、`currentNode=END`。快照 API 和前端恢复类型同步更新。
- 前端 PASS 统一调用 `acceptGenerationSession()`，并在 `COMPRESSION_STARTED`/后续运行阶段清除旧审稿失败提示，避免页面继续展示 `WAITING_HUMAN` 或旧 `REVIEW_FAILED` 文案。
- 新增 Session、ACCEPT 和 HTTP 快照定向断言，覆盖即时 `COMPRESSING`、失败文案清空、当前节点推进、阶段完成和终态恢复；后端定向测试 29 项通过，前端 `npm run build` 通过，`git diff --check` 通过。

### Task 61.14～61.18：人工采用按钮语义收敛（2026-09-05）

- `GenerateView.vue` 将按钮统一命名为“采用当前正文”，仅在 `WAITING_HUMAN` 与 `REVIEW_FAILED` 分支显示；`REVIEWING` 改为只展示“自动审稿中/自动审稿通过”，`REVISING`、`COMPRESSING`、`PERSISTING` 继续只展示处理中状态。
- PASS 点击立即使用 `resuming/acceptingRequested` 进入 loading 和 disabled，收到 `COMPRESSION_STARTED` 或终态事件后释放请求标记；请求失败时允许恢复重试，避免重复点击和冲突决策。
- Review 失败页面保留“重新审稿 / 采用当前正文 / 停止流程”，失败提示收敛为“自动审稿失败，但正文已保留。”；未新增 retry API。
- 更新前端 UI 契约测试，覆盖人工决策状态、正常审稿状态和 Review 失败操作边界；`ChapterGenerationSessionApiContractTest`、`CreationCanvasVisualContractTest`、`PageErrorPresentationContractTest` 共 24 项通过，前端 `npm run build` 通过，`git diff --check` 通过。

### Task 61.19～61.23：Review 后处理事件与 ACCEPT/STOP 流转收口（2026-09-05）

- `ReviewRouter` 对带有空问题列表的有效报告直接返回 `COMPRESSION`，不再经过 `HUMAN`；`REVIEW_FAILED` 且缺少报告的原有人工恢复边界保持不变。
- `WAITING_HUMAN` 与 `REVIEW_FAILED` 采用当前正文后只推进 `COMPRESSION → PERSIST → COMPLETED`，不会补发 `REVIEW_COMPLETED`；`REVIEW_FAILED` 历史事件不阻止后续压缩和持久化。
- Session 在 ACCEPT 后进入 `COMPRESSION` 时清空旧 `failureMessage`，并通过 `acceptRequested` 与 `COMPRESSION_STARTED` 去重保护避免重复恢复、重复压缩或重复持久化；进入后处理后旧 STOP 不会再发出取消事件。
- 新增 Review 空报告优先级、人工采用事件顺序和 ACCEPT/STOP 竞态定向测试；`ReviewRouterTest`、`HumanDecisionRouterTest`、`ChapterServiceAcceptTest`、`ChapterGenerationSessionRegistryTest`、`ChapterHumanCheckpointResumeTest` 共 34 项通过，`git diff --check` 通过。

### Task 63：审稿结果实时同步（2026-09-05）

- `ChapterGenerationSessionEvent` 增加可选 `reviewIssues`，保留原有两参数构造方式；`ChapterService` 在 `REVIEW_COMPLETED` 发布时从本次 `ReviewReportVO` 提取问题列表，SSE JSON 会携带当前结果。
- `ChapterGenerationSessionRegistry` 收到 `REVIEW_COMPLETED` 后整体替换 Session 的 Review 问题列表，二次 Review 不会与首轮问题累加；HTTP SSE 增加当前问题列表传输测试。
- 前端 SSE 类型和解析器透传 `reviewIssues`，`GenerateView.vue` 只在 `REVIEW_COMPLETED` 时覆盖 `result.reviewIssues`；审稿进行中隐藏“0 项/无问题”提示，审稿完成空列表显示“审稿通过 · 0 项”，`REVISING` 阶段显示正在根据问题修改。
- 验证结果：Session 覆盖、HTTP SSE 和前端契约定向测试 42 项全部通过，前端 `npm run build` 通过，`git diff --check` 通过。

### Task 64：补齐模型调用耗时日志（2026-09-05）

- `ChapterModelPort` 与 `PlanningModelPort` 统一记录 `[MODEL]` INFO 开始/成功、ERROR 失败和 15 秒 WARN 慢调用日志，包含 `stage`、1-based `attempt`、`costMs`、输出长度、`finishReason`、`usage` 以及提示词长度，不输出提示词或正文内容。
- 流式 DRAFT 按实际订阅记录首 token `ttftMs`、总耗时 `totalMs`、`chunkCount`、`contentChars` 以及流式响应最后可用的 `finishReason/usage`；同步与结构化调用在请求失败、响应读取失败、空响应和解析失败时均保留耗时及可用元数据。
- `IChapterModelPort`、`IPlanningModelPort` 增加带观测上下文的兼容重载；章节节点和规划/项目生成服务传入具体阶段与 attempt，重试轮次分别可定位。
- 更新 `ChapterWorkflowObservabilityContractTest`，验证同步/结构化/流式指标、慢调用阈值、attempt 传递和日志脱敏；验证结果：47 项定向测试全部通过（Failures=0、Errors=0），`git diff --check` 通过。

### Task 65：SSE / Session 日志降噪（2026-09-05）

- `NovelChapterGenerationEventController` 不再逐条记录任何 SSE 事件或 `DRAFT_CHUNK`，仅记录一次 connected/completed/disconnected/error 生命周期；通过幂等保护避免超时、错误和完成回调重复输出。
- `ChapterGenerationSessionRegistry` 在 Session 内累计初稿 chunk 数和字符数，从 `DRAFT_STARTED` 计时，并在 `DRAFT_COMPLETED` 一次性输出 `[SSE] draft completed` 的 chunks/chars/durationMs INFO 汇总。
- `ChapterService` 的章节阶段日志统一为 `[CHAPTER]` 状态摘要，包含 workflow、stage、chapter、Review issues、阶段 costMs 和工作流 totalMs，不再输出逐事件 SSE 摘要。
- 前端 `novel-agent-web/src/api/chapter.ts` 移除 DEV 环境下逐条 SSE `console.debug` 及累计长度变量，仅保留连接建立提示，避免浏览器控制台随正文片段刷屏。
- 更新 SSE 日志契约测试、Session 日志测试和前端 SSE 契约测试；验证结果：本轮相关定向测试共 46 项通过（Failures=0、Errors=0），前端 `npm run build` 通过，`git diff --check` 通过。

### Task 66：默认卷补齐（2026-09-05）

- `PlanningService.confirmRootOutline()` 增加事务内默认卷补齐：首次确认 BOOK 后，若其下没有 VOLUME，则自动创建结构节点 `VOL_001`（沿用现有编号规则），标题为“卷一”、概要为空、范围继承 BOOK、状态为 `PLANNED`，不调用模型。
- 根大纲确认定向测试补充默认卷的父子关系、编号、范围、标题、空概要和状态断言，并保持模型零调用；验证结果：`RootOutlineConfirmationTest` 3 项通过，`git diff --check` 通过。

### Task 66.3/66.4：活动卷与切卷边界（2026-09-05）

- `PlanningService` 不再允许从 BOOK 生成或确认首个 VOLUME；“生成下一卷”必须存在活动卷，并统一通过 `finalizeCurrentVolumeAndCreateNext()` 收口当前卷后创建新卷。
- 新增 ARC（AI 生成确认或手动创建）必须挂在当前最新 VOLUME 下；历史卷仍保留 ARC 重生成能力。`OutlineView.vue` 同步隐藏历史卷的新增章节操作，并明确下一卷确认会收口当前卷。
- 更新滚动大纲生成、切卷确认、手动大纲和 UI 契约测试；验证结果：规划与 UI 定向测试 28 项通过，前端 `npm run build` 通过，`git diff --check` 通过。

### Task 66.6～66.8：ChapterPlan 使用真实 ARC，默认卷仅弱化展示（2026-09-05）

- `PlanningService` 的 ChapterPlan 生成与确认统一解析真实 `BOOK → VOLUME → ARC` 链路；默认卷存在但当前章节没有 ARC 时，在模型调用和持久化前返回“当前章节尚未创建章纲”。
- `GenerateView.vue` 同时加载真实大纲节点和 ChapterPlan，按当前章节命中的 ARC 匹配计划；确认 Draft 后重新从后端加载，移除空 `outlineNodeCode` 的前端伪造对象，并在无 ARC 时保留 ChapterPlan 面板显示明确提示。
- `ReadView.vue` 仅在后端返回单个真实“卷一/第一卷且概要为空”的默认 VOLUME 时弱化卷标题，章节仍来自真实卷分组；多卷或用户规划后的结构继续显示完整层级。
- 新增 ChapterPlan 无 ARC 的生成/确认边界测试和默认卷展示契约；验证结果：相关 Maven 定向测试 31 项通过（Failures=0、Errors=0），收紧无 ARC 统一错误边界后 ChapterPlan 定向测试再通过 15 项，前端 `npm run build` 通过，`git diff --check` 通过。

### Task 64.1：覆盖所有 Planning 模型调用（2026-09-05）

- `PlanningModelPort` 收口全部结构化 Planning 调用日志，统一输出 `mode`、`stage`、`attempt`、`responseType`、提示词长度、`requestCostMs`、`parseCostMs`、`totalCostMs`、响应长度、`finishReason` 和 `usage`；请求失败与解析失败也沿用同一字段格式，不输出提示词或正文内容。
- `NovelProjectService` 与 `PlanningService` 为 Story Bible 初始化/修订、人物生成、根大纲、下一卷、下一章章纲、ARC 重生成、章节计划传入统一阶段名：`STORY_BIBLE_INIT`、`STORY_BIBLE_REVISION`、`CHARACTER_GENERATION`、`ROOT_OUTLINE`、`NEXT_VOLUME`、`NEXT_CHAPTER_OUTLINE`、`ARC_REGENERATION`、`CHAPTER_PLAN`；业务方法不再各自实现模型日志。
- 更新 `ChapterWorkflowObservabilityContractTest` 的 Planning 日志与阶段覆盖契约；验证结果：`ChapterWorkflowObservabilityContractTest` 5 项通过，domain/infrastructure 编译通过，`git diff --check` 通过。

### Task 64.2：覆盖 Chapter 工作流所有节点（2026-09-05）

- `ChapterModelPort` 统一收口 DRAFT、REVIEW、REVISION、COMPRESSION 的模型耗时日志；DRAFT 流式调用记录 `stage`、`mode`、`attempt`、提示词长度、`ttftMs`、`totalCostMs`、chunk 数和正文长度。
- REVIEW/COMPRESSION 的结构化调用与 REVISION 的同步调用统一拆分 `requestCostMs`、`parseCostMs`、`totalCostMs`，并保留 `contentChars`、`finishReason`、`usage`；请求、响应读取、空响应、解析失败和流式取消均记录统一指标。
- 保持四个 Chapter 节点已有的 `stage` 和 1-based `attempt` 传递，扩展 `ChapterWorkflowObservabilityContractTest` 校验 DRAFT/REVIEW/REVISION/COMPRESSION 阶段覆盖与日志脱敏。
- 验证结果：`ChapterWorkflowObservabilityContractTest` 与 `ReviewSeverityModelContractTest` 共 20 项通过，`ChapterNodeTest` 与 `ChapterModelRetryExecutorTest` 共 42 项通过，编译成功，`git diff --check` 通过。

### Task 64.3：拆分 structured 调用耗时（2026-09-05）

- `PlanningModelPort` 与 `ChapterModelPort` 的结构化调用统一按 `requestCostMs`、`readCostMs`、`parseCostMs`、`totalCostMs` 记录；模型请求、响应内容读取和结构化映射/反序列化拥有独立计时边界。
- 请求失败、响应读取失败、空响应、结构化解析失败和成功路径均使用同一组耗时字段；REVISION 同步文本调用保留统一字段，`readCostMs=0`，避免日志语义漂移。
- 慢调用告警同步输出四段耗时，`ChapterWorkflowObservabilityContractTest` 增加 `readCostMs` 契约校验。
- 验证结果：`ChapterWorkflowObservabilityContractTest` 与 `ReviewSeverityModelContractTest` 共 20 项、`ChapterNodeTest` 与 `ChapterModelRetryExecutorTest` 共 42 项定向测试通过，编译成功，`git diff --check` 通过。

### Task 64.4：流式调用记录 TTFT（2026-09-05）

- `ChapterModelPort.stream()` 从实际订阅开始计时，在第一个非空正文 chunk 到达时通过 `firstTokenAt.compareAndSet` 固化时间点，并输出 `first-token` 日志中的 `ttftMs`。
- DRAFT 结束汇总复用同一首 token 时间点，继续输出 `ttftMs`、`totalCostMs`、`chunkCount` 和 `contentChars`，可区分首 token 延迟与后续全文生成耗时；没有正文 chunk 时 `ttftMs` 保持为空。
- 更新 `ChapterWorkflowObservabilityContractTest`，校验首 token 时间点、TTFT 计算和结束汇总字段契约。

### Task 64.5：所有模型重试携带 attempt（2026-09-05）

- 核对 Planning 的八类模型调用与 Chapter 的 DRAFT、REVIEW、REVISION、COMPRESSION 调用：Planning 初始调用明确传入 `attempt=1`，Chapter 重试回调的 0-based attempt 在模型边界统一转换为 `attempt + 1`。
- `PlanningModelPort`、`ChapterModelPort` 的成功、失败、慢调用、流式首 token 和结束汇总日志均保留 `attempt`，不会把多次重试折叠成一个总耗时。
- 加强 `ChapterWorkflowObservabilityContractTest`，锁定适配器的 attempt 归一化和四个节点的 1-based attempt 传递；此前章节重试定向测试继续覆盖多次调用。

### Task 64.6：记录模型基础信息（2026-09-05）

- 新增 `ModelObservabilityMetadata`，从当前 Spring AI 配置提取 `provider`、`modelName` 和可选的 `baseUrlHost`；URL 只解析 host，不保留完整 URL、query、API Key 或 Authorization。
- `PlanningModelPort` 与 `ChapterModelPort` 的结构化、同步、流式首 token、结束汇总和慢调用日志统一附带模型基础信息，便于跨模型对比请求耗时、解析耗时和 TTFT。
- 保留模型端口单参数构造兼容离线测试，并新增基础信息脱敏测试与日志契约断言。
- 验证结果：`ChapterWorkflowObservabilityContractTest` 与 `ReviewSeverityModelContractTest` 共 21 项通过，编译成功，`git diff --check` 通过。

### Task 64.7～64.10：统一模型日志级别、慢调用阈值与解析失败观测（2026-09-05）

- `PlanningModelPort` 与 `ChapterModelPort` 的模型结果事件统一为 `success`、`failed`、`parse failed`；成功使用 INFO，失败使用 ERROR，慢调用额外使用 WARN，不再使用 DEBUG 承载模型性能日志。
- 新增 `ModelObservabilityThresholds` 集中定义 `SLOW_MODEL_CALL_MS=15000`、流式 TTFT 阈值 8000 毫秒和流式总耗时阈值 30000 毫秒；结构化/同步调用按统一模型调用阈值告警，流式调用分别告警首 token 慢和总生成慢。
- 结构化解析失败继续记录 `requestCostMs`、`readCostMs`、`parseCostMs`、`totalCostMs`、`responseType` 和 `contentChars`，并明确输出 `parse failed`；模型日志仍只记录 Prompt/响应长度，不输出完整内容。
- 验证结果：`ChapterWorkflowObservabilityContractTest` 与 `ReviewSeverityModelContractTest` 共 21 项、`ChapterModelRetryExecutorTest` 9 项通过，结构化解析失败日志包含拆分耗时，编译成功，`git diff --check` 通过。

### Task 64.11～64.13：统一失败分类与模型日志字段格式（2026-09-05）

- 新增 `REQUEST_FAILED`、`READ_FAILED`、`PARSE_FAILED`、`EMPTY_RESPONSE` 四类失败类型；Planning 与 Chapter 的请求、读取、解析和空响应路径分别输出对应分类。
- 新增 `ModelObservabilityLogFormatter`，PlanningModelPort 与 ChapterModelPort 共用同一字段顺序：`status`、`mode`、`stage`、`attempt`、失败类型/原因、模型信息、`responseType`、长度字段、耗时字段、流式指标、响应元数据；不可用字段直接省略，不输出 `null`。
- 继续使用普通结构化日志，没有引入 Micrometer、Prometheus、Tracing SDK、性能表或外部 APM。
- 验证结果：`ChapterWorkflowObservabilityContractTest` 与 `ReviewSeverityModelContractTest` 共 22 项、`ChapterModelRetryExecutorTest` 9 项通过，实际日志验证失败分类和空字段省略，编译成功，`git diff --check` 通过。

### Task 64.14～64.15：核对节点 stage 与 SSE 日志降噪（2026-09-05）

- 核对生产代码中的八类 Planning 调用以及 DRAFT、REVIEW、REVISION、COMPRESSION 四个 Chapter 节点，均显式传入业务 `stage`；不存在通过 `responseType.getSimpleName()` 推断业务阶段的调用路径。
- SSE 事件发送不逐条打印 `DRAFT_CHUNK`；阶段生命周期按 INFO 记录，阶段失败按 WARN/ERROR 记录，DRAFT 完成时仅输出 `chunks`、`chars` 和 `durationMs` 汇总。
- 验证结果：`ChapterWorkflowObservabilityContractTest`、`ChapterGenerationSessionRegistryTest`、`NovelChapterGenerationEventControllerTest` 共 23 项通过，确认节点 stage 传递和 SSE 汇总日志契约，编译成功。

### Task 64.16：修正 Planning structured 响应转换（2026-09-05）

- `PlanningModelPort` 对模型响应只调用一次 `response.content()`，不再调用 `response.entity(responseType)`，改用 `ObjectMapper.readValue(content, responseType)`，与 ChapterModelPort 的结构化解析保持一致。
- 将等待完整响应内容的耗时单独记录为 `contentReadMs`，`parseCostMs` 只覆盖 JSON 到 DTO 的转换；共享日志格式保留 Chapter 现有 `readCostMs` 兼容调用。
- 验证结果：`ChapterWorkflowObservabilityContractTest` 与 `ReviewSeverityModelContractTest` 共 22 项通过，确认 Planning 响应读取单次、解析方式和字段契约，编译成功。


### 2026-09-05：结构化模型输出确定性清洗与业务校验

- 在 infrastructure/adapter/port 新增 `StructuredJsonCleanup`，并接入 `PlanningModelPort`、`ChapterModelPort`：只清洗 Markdown JSON fence、BOM、首尾空白和明确前后说明；JSON 正文保持原样，歧义外壳拒绝处理。章节原始响应继续用于 trace 与失败排查。
- 结构化响应先严格解析 JSON，再映射 DTO、调用 domain/common/validation/StructuredModelOutputValidator；日志和异常详情区分 `FORMAT_CLEANUP_FAILED`、`JSON_PARSE_FAILED`、`SCHEMA_VALIDATION_FAILED`，审稿与压缩业务校验保留原有 E0004/E0006 恢复语义。
- 在 PlanningService、NovelProjectService、ReviewChapterNode、CompressChapterNode 接入领域校验。总纲仅要求 summary；章节计划与子大纲检查标题、摘要；人物草稿检查姓名和列表有效性；审稿检查问题数组、等级、分类、描述、证据；章节记忆检查摘要、钩子及文本数组。已有的正文证据匹配校验继续执行，并标识 Schema 失败。
- 字段长度沿用已有明确约束：标题 300 字、人物姓名及年龄描述 100 字、一句话故事 1000 字、章节短摘要 2000 字；无既定业务上限的长文本未新增限制。
- 所有测试位于 novel-agent-app/src/test。新增 StructuredOutputValidationTest，调整 ReviewSeverityModelContractTest、ChapterWorkflowObservabilityContractTest、ChapterNodeTest 的相关契约，打印清洗、拒绝和边界校验结果。
- 验证：定向运行 StructuredOutputValidationTest、ReviewSeverityModelContractTest、ChapterWorkflowObservabilityContractTest、RootOutlineGenerationTest、ChapterPlanSingleGenerationTest、ChapterNodeTest、CharacterGenerationServiceTest、ChildOutlineGenerationTest，共 83 项通过，0 失败、0 错误；未调用真实模型。git diff --check 通过。


### 2026-09-05：共享结构化响应解析器与一次有限解析重试

- infrastructure/adapter/port/StructuredModelResponseParser 提供 normalize(raw)、parse(raw, type)，集中完成确定性外壳清洗、严格 JSON 解析与类型映射；PlanningModelPort、ChapterModelPort 及 Review 响应均使用同一解析器。解析器不调用模型、不执行领域业务校验。
- 两个模型适配器在清洗后 JSON_PARSE_FAILED 时重新请求模型一次，逐次记录 attempt；第二次失败直接抛出。外壳清洗失败和字段校验失败不触发适配器新增的解析重试。
- domain/chapter/model/valobj/ChapterModelResponseException 标记解析重试额度已用完，ChapterModelRetryExecutor 识别后停止外层重试；第二次请求出现网络错误、JSON 错误或 Schema 错误均不会叠加模型请求，仍保留可用的原始响应。
- app/src/test 新增 StructuredModelResponseRetryTest，验证重新请求后恢复、持续失败最多两次、外层不叠加、Review 共用解析器以及解析器不执行业务校验；更新 StructuredOutputValidationTest 和 ChapterWorkflowObservabilityContractTest。
- 验证：定向运行 StructuredModelResponseRetryTest、StructuredOutputValidationTest、ReviewSeverityModelContractTest、ChapterWorkflowObservabilityContractTest、ChapterModelRetryExecutorTest，38 项全部通过；整理 import 后再次运行 StructuredModelResponseRetryTest，4 项全部通过。git diff --check 通过，未调用真实模型。


### 2026-09-05：结构化模型失败日志保留统计字段并避免原文泄露

- infrastructure/adapter/port/PlanningModelPort、ChapterModelPort 的失败日志保留 stage、attempt、responseType、原始 contentChars、failureType 及耗时字段；异常仅记录类型，不向日志附加可能包含模型原文的异常堆栈，默认不记录正文或前缀。
- StructuredModelResponseParser 的解析异常只携带失败分类和异常类型，不保留可能包含响应片段的 Jackson message/cause，避免异常被上层打印时间接泄露原文。章节原始响应仍独立保留供既有 trace 使用。
- app/src/test 新增 StructuredModelLoggingTest，通过 Logback ListAppender 检查两类模型入口的三种失败、attempt=1/2 和清洗前响应长度，并验证日志及传播异常堆栈不含测试原文标记。
- 验证：定向运行 StructuredModelLoggingTest、StructuredModelResponseRetryTest、StructuredOutputValidationTest、ReviewSeverityModelContractTest、ChapterWorkflowObservabilityContractTest，共 30 项全部通过；git diff --check 通过。未调用真实模型，未进行新的性能基准测试。


### 2026-09-05：结构化解析失败保留开发环境 raw response 调试日志

- `StructuredModelResponseParser.ParseException` 现在保留规范化响应以及 Jackson 解析位置 `line/column`，但异常消息仍只包含失败分类和异常类型，避免生产日志通过异常堆栈泄露正文。
- `PlanningModelPort`、`ChapterModelPort` 根据 Spring `dev`/`local` profile 开启结构化解析调试：仅对 `JSON_PARSE_FAILED` 以 DEBUG 输出 `[MODEL_RAW]` 完整原始响应和 `[MODEL_NORMALIZED]` 完整规范化响应；`FORMAT_CLEANUP_FAILED`、`SCHEMA_VALIDATION_FAILED` 不输出正文。
- 结构化失败统计日志追加 `line`、`column`；生产默认继续只保留既有 stage、attempt、responseType、contentChars、failureType 及耗时/模型元数据，不输出完整正文。`logback-spring.xml` 为 `local` profile 同步开启 DEBUG logger。
- 新增 `StructuredModelRawDebugLoggingTest`，覆盖 Planning/Chapter 的 dev/local 双标记日志、prod 正文隔离、解析位置与规范化响应传递；验证该专项 4 项定向测试全部通过，配套回归共 33 项通过，`git diff --check` 通过，未调用真实模型。


### Task 66.9：默认卷迁移后修复 ARC 归属（2026-09-05）

- `PlanningRepository.listOutlines()` 在读取大纲树前自动执行幂等迁移：发现直接挂在 BOOK 下的 ARC 时，复用 `VOL_001`/卷一等默认 VOLUME；不存在时创建范围继承 BOOK 的“卷一”，再只更新 ARC 的 `parent_id`。
- 迁移不修改 ARC 的 `nodeCode`、章节范围或 ChapterPlan 关联，因此 `ChapterPlan.outlineNodeCode` 保持原 ARC；重复执行不会重复建卷或重复迁移。
- 新增 `PlanningRepositoryTest` 真实 MySQL 定向覆盖无卷创建、已有默认卷复用、ARC 父节点迁移、ChapterPlan 引用保持和二次执行幂等性；`GenerateViewChapterPlanContractTest` 锁定前端 `currentChapterArc` 只接受 VOLUME 父节点。

### 2026-09-06：RootOutline 人物前置校验与角色边界

- `PlanningService.generateRootOutline` 在模型调用前检查项目正式人物列表；为空时直接返回“请先创建并确认核心人物，再生成故事大纲”，不产生 RootOutline 草稿或模型请求。
- RootOutline 用户 Prompt 增加“已确认核心人物”事实边界，明确姓名、身份、核心背景和主要关系不可被替换或改写；允许剧情发展，但不把未来发展写成初始事实。
- Root、Volume、ARC 及章节大纲模型 Prompt 明确只负责剧情规划，不创建、确认或保存正式人物记录；未定义人物只能以临时称谓或功能描述出现。
- 人物更新增加已有大纲检测：`roleType`、`backgroundStory` 或 `lifeStatus` 发生结构性变化时仍允许保存，但 API 返回 warning，前端显示“该人物已参与现有故事大纲……”提示；轻量资料修改不触发提示。
- 验证结果：`RootOutlineGenerationTest`、`NovelProjectServiceTest`、`NovelProjectControllerHttpTest`、`PromptBoundaryContractTest` 共 54 项通过；前端 `npm run build` 通过，未调用真实模型。

### 2026-09-06：Character 生成接入滚动式大纲上下文

- `NovelProjectService.generateCharacters` 在存在规划上下文时读取已确认大纲，仅向 Character Prompt 注入 BOOK 和按序号最新的 VOLUME；无 BOOK 时保持首次 `StoryBible → Character` 流程。
- Character Prompt 明确区分事实边界：已确认人物和已确认故事设定优先作为后续生成事实输入；大纲中的未命名人物只作为待补充的剧情位置，不覆盖、修改或替换已有确认人物。
- 新增 Character 生成测试，验证 BOOK/当前 VOLUME 的标题与摘要进入 Prompt，且不会暴露节点类型、编码或序号等后端字段；正式人物仍只生成草稿，确认后才写入角色库。
- 验证结果：`CharacterGenerationServiceTest`、`CharacterPromptContractTest`、`PromptBoundaryContractTest` 共 18 项通过。

### 2026-09-06：收敛 StructuredModelOutputValidator 包位置

- 结构化模型输出校验器已统一到 `domain.common.validation.StructuredModelOutputValidator`。
- 更新 Planning、Chapter、Project、模型端口及测试中的全部 import 和调用点，删除旧校验器文件；未引入接口、Bean 或额外策略抽象，校验逻辑保持不变。
- 验证结果：旧全限定名全局检索为零引用；`StructuredOutputValidationTest`、`ChapterWorkflowObservabilityContractTest` 共 8 项通过，多模块编译成功。

### 2026-09-06：统一 Spring AI OpenAI HTTP 超时

- 确认当前 Spring AI OpenAI starter 使用 OpenAI Java SDK 的 `SpringAiOpenAiHttpClient`，在现有 `ChatClientConfiguration` 注册 `OpenAiHttpClientBuilderCustomizer`，没有新增 HTTP Client。
- 底层 OkHttp 明确设置连接超时 10s、读取超时 180s、单次请求总超时 180s；dev/prod 的 Spring AI 请求超时同步调整为 180s。
- Axios、Vite API proxy 和 proxy request 均设置为 300s，长于后端模型请求上限；仓库内未发现额外反向代理配置。
- 新增 `HttpTimeoutConfigurationContractTest`，实测底层 Client 参数并检查各环境和前端代理配置；验证该测试 2 项、多模块编译和前端 `npm run build` 均通过。

### 2026-09-06：Outline 人物前置提示与角色页故事准备状态

- Outline 页读取已确认人物；没有人物时禁用两个“AI 生成总纲”空状态入口和弹窗生成按钮，并显示“请先完成核心人物设定”，同时在事件处理函数中保留前置保护。
- 角色页新增轻量“故事准备”提示，按已确认世界设定、角色库中已确认人物和已存在大纲实时显示 `✓` / `○`，大纲状态复用已有大纲树查询接口。
- 仅调整 `novel-agent-web/src/views/OutlineView.vue`、`SetupView.vue` 及 `novel-agent-app/src/test` 下对应 UI 契约；角色库契约同步改为兼容当前 Controller 换行格式的稳定方法边界定位。
- 验证结果：前端 `npm run build` 通过；`OutlineViewContractTest` 10 项、`CharacterLibraryContractTest` 3 项全部通过；未调用真实模型。

### 2026-09-06：结构化模型调用改为流式读取并补齐阶段指标

- `PlanningModelPort`、`ChapterModelPort` 的结构化调用改为使用 Spring AI `stream().chatResponse()`，由后端用 `StringBuilder` 累加所有 chunk，流结束后统一执行 `StructuredModelResponseParser.parse` 和 `StructuredModelOutputValidator.validate`；对外仍返回原有同步 DTO/HTTP JSON。
- 结构化日志新增 `ttftMs`、`generationMs`、`chunkCount`、`contentChars`、`totalCostMs`，保留 `contentReadMs` 兼容字段；普通章节流式日志同步记录 `generationMs`。
- 更新相关离线模型测试替身为真实覆盖 `stream()` 的模型，并新增多 chunk 结构化解析与指标日志测试；未改变解析、校验和重试逻辑。
- 验证结果：`StructuredModelStreamingTest`、结构化重试/日志/校验、审核模型和工作流观测契约共 35 项通过；多模块编译通过。

### 2026-09-06：结构化流生命周期失败分类

- 新增结构化流失败分类器；连接超时、首 chunk 前超时、首 chunk 后读取超时和其他流异常分别记录为 `CONNECT_TIMEOUT`、`STREAM_START_TIMEOUT`、`STREAM_READ_TIMEOUT`、`STREAM_FAILED`。
- `EMPTY_RESPONSE`、`JSON_PARSE_FAILED`、`SCHEMA_VALIDATION_FAILED` 保持原有空响应、解析和 Schema 校验分类；失败日志追加 `exceptionType` 与最深层 `rootCauseType`，不再将结构化流异常统一记为 `REQUEST_FAILED`。
- Planning/Chapter 结构化流入口均接入分类逻辑，新增 `StructuredStreamFailureClassificationTest` 覆盖两类模型端口的四种生命周期失败。
- 验证结果：结构化流分类、流式读取、重试、日志、校验、审核模型和工作流观测契约共 36 项通过；多模块编译通过，`git diff --check` 通过。

### 2026-09-06：结构化流超时取消与 Planning 统一收口

- Planning、Chapter 结构化流统一在订阅链上使用 180 秒 `Flux.timeout`；超时由 Reactor 向上游传播取消，停止继续接收模型输出，并转换为统一的“模型响应超时，请重试” `AppException`。
- Planning 的 `STORY_BIBLE_INIT`、`STORY_BIBLE_REVISION`、`CHARACTER_GENERATION`、`ROOT_OUTLINE`、`NEXT_VOLUME`、`NEXT_CHAPTER_OUTLINE`、`ARC_REGENERATION`、`CHAPTER_PLAN` 均通过同一个 `PlanningModelPort` 结构化调用路径处理。
- 新增短超时订阅取消测试，覆盖首 chunk 前和首 chunk 后超时，并验证上游取消计数与统一超时异常；同步补充模型调用契约断言。
- 验证结果：结构化流取消、流式读取、重试、日志、校验和工作流观测契约共 16 项通过；多模块编译成功。

### 2026-09-06：保持结构化 JSON 容错链并校验前端请求超时

- 结构化流最终拼接文本仍统一交给 `StructuredModelResponseParser`，解析成功后再交给 `StructuredModelOutputValidator`；Markdown fence 清理、一次 `JSON_PARSE_FAILED` 重试、Schema 校验及 dev/local raw response 调试日志均保持不变。
- Axios、Vite proxy 与 `proxyTimeout` 均保持 300 秒，长于后端模型 180 秒超时，`/characters/generate` 等模型接口可等待完整响应。
- 验证结果：流式拼接、Markdown fence、解析重试、Schema 校验、raw response 调试及 HTTP 超时契约共 14 项通过；多模块编译成功。

### 2026-09-06：客户端断开降噪与 CHARACTER_GENERATION 专项验收

- 新增客户端断开异常识别，覆盖 `ClientAbortException`、`AsyncRequestNotUsableException` 及 `ServletOutputStream failed to write` 因果链；统一 API 异常入口与章节 SSE 回调将其归类为 `CLIENT_DISCONNECTED`，仅记录 WARN，不附带完整堆栈。
- Planning/Chapter 结构化流增加统一 `status=start` 生命周期日志；Planning 的 `CHARACTER_GENERATION` 离线验收使用 1086/1379 字符 Prompt、3 个角色分片，验证最终返回 `CharacterDraftListVO` 以及 `ttftMs`、`generationMs`、`chunkCount`、`contentChars`、`totalCostMs`。
- 新增 `ClientDisconnectNoiseTest`、`CharacterGenerationModelAcceptanceTest`，并更新 SSE 与模型观测契约。
- 新增 `CharacterGenerationModelRealModelIT`，使用现有 `dev` profile 和 `PlanningModelPort` 做真实模型人工验收；本次实际返回 4 名人物，`systemChars=1086`、`userChars=509`，`ttftMs=154540`、`generationMs=9520`、`chunkCount=16814`、`contentChars=2346`、`totalCostMs=164138`，在 180 秒后端上限内完成且未出现 `ClientAbortException`。
- 验证结果：客户端断开、SSE 生命周期、CHARACTER_GENERATION、结构化流和工作流观测定向测试共 18 项通过；真实 `CharacterGenerationModelRealModelIT` 1 项通过；多模块编译成功，`git diff --check` 通过。

### 2026-09-06：Planning 按 stage 统一两类模型参数配置

- 在 `novel-agent-infrastructure/src/main/java/cn/ninth/novel/infrastructure/config/PlanningModelProperties.java` 新增 `FAST_STRUCTURED` 与 `CREATIVE_PLANNING` 两类配置，支持 `model`、`temperature`、DeepSeek OpenAI-compatible 的 `reasoning_effort` 和 `extra_body.thinking.type`。
- `PlanningModelPort` 按既有八个 Planning stage 归组选取配置，并通过 Spring AI 2.0 `OpenAiChatOptions.Builder` 注入每次请求的 model、temperature、reasoningEffort 和 thinking 参数；保持现有单一 `PlanningModelPort`、流式结构化解析、重试和观测链路不变。
- `novel-agent-app/src/main/resources/application.yml` 增加两类默认配置：`CHARACTER_GENERATION`、`CHAPTER_PLAN` 使用 `thinking=disabled`；Story Bible、总纲、分卷、章纲和 ARC 重生成使用创作规划配置。
- 在 `novel-agent-app/src/test/java/cn/ninth/novel/infrastructure/adapter/port/PlanningModelConfigurationTest.java` 验证 YAML 风格配置绑定、原有八个 stage 的两类归组，以及最终 `Prompt` 中的 `OpenAiChatOptions` 参数；未调用真实模型。
- 验证结果：Planning 配置及结构化流/重试回归定向测试共 9 项通过，多模块编译成功，`git diff --check` 通过。

### 2026-09-06：Planning 保持模型默认输出上限

- 未在 Planning 两类配置和 `PlanningModelPort` 中设置 `maxTokens` 或 `maxCompletionTokens`，保持模型当前输出上限，避免结构化 JSON 因长度限制被截断。
- `PlanningModelConfigurationTest` 增加两类配置的 token 上限为空断言；未新增输出长度控制逻辑。
- 验证结果：定向配置测试通过，确认 `maxTokens`、`maxCompletionTokens` 均未设置。

### 2026-09-06：Planning 日志补充实际模型配置摘要

- Planning 的 start、success/failed 和 slow 日志追加 `profile`、按 stage 生效的 `model`、`temperature` 及安全的 `reasoning` 摘要。
- 摘要仅输出标量配置，不输出 API Key、`extraBody` 或完整 `OpenAiChatOptions`。
- `PlanningModelConfigurationTest` 增加日志契约，验证 `CHARACTER_GENERATION` 的 `FAST_STRUCTURED`、实际模型、temperature、`reasoning=disabled` 及敏感字段隔离。
- 验证结果：Planning 配置、日志、结构化流、重试和审核模型回归定向测试共 24 项通过，`git diff --check` 通过。

### 2026-09-06：保留 Planning 首响应/首内容及 token 观测并完成三次真实验收

- `PlanningModelPort` 保留 `ttftMs` 兼容字段，并补充显式的 `firstResponseMs`、`firstContentMs`、`completionTokens`；`generationMs`、`usage`、`finishReason` 和 `totalCostMs` 继续记录。未改变 Prompt、preferredCount、token 上限、结构化校验、JSON parser、timeout 或前端交互。
- `CharacterGenerationModelRealModelIT` 使用同一份 Prompt 在 dev profile 连续调用 3 次，打印每次 `firstContentMs`、`completionTokens`、`generationMs`、`totalCostMs` 及完整模型结果，作为后续优化对照基线。
- 本次三次结果：`firstContentMs=1753/545/307ms`（平均约 868ms），`completionTokens=582/597/812`，`totalCostMs=6926/5886/7456ms`（平均 6756ms）；三次均为 `finishReason=STOP`，未出现结构化 JSON 截断。
- 对比此前记录的 `ttftMs=154540ms`、`totalCostMs=164138ms`，关闭 `CHARACTER_GENERATION` reasoning 后首内容和总耗时均明显下降，方向验证成立。
- 验证结果：真实 `CharacterGenerationModelRealModelIT` 1 项通过；离线 Planning/结构化流/日志回归 24 项通过，多模块编译成功。

### 2026-09-06：ChapterPlan 标题统一由 ARC.title 提供

- `PlanningService.generateChapterPlan` 生成的 ChapterPlan 草稿标题改为当前 ARC.title，模型返回的 `ChapterPlanDraftVO.title` 不再采信；确认保存时同样强制使用当前 ARC.title。
- `CHAPTER_PLAN_SYSTEM` 和动态 user prompt 明确当前章节标题已经确定，不得重新创作、修改或替换，只生成本章写作执行计划；兼容保留的模型 title 字段不承担标题创作职责。
- `ChapterPlanSingleGenerationTest`、`ChapterPlanSingleConfirmationTest` 和 Prompt 契约验证了模型标题与 ARC.title 不一致时仍保存 ARC.title；正文 Draft 继续通过 ChapterPlan.title 获取正式标题。
- 验证结果：ChapterPlan 生成、确认、Prompt 边界和 HTTP 定向测试共 29 项通过；真实 MySQL 回归测试因本地 root 账号拒绝连接未执行完成。

### 2026-09-06：ARC 摘要限制为章级规划粒度

- `NEXT_CHAPTER_OUTLINE_SYSTEM` 和 `ARC_REGENERATION_SYSTEM` 明确 ARC 摘要只描述核心事件、主要冲突、关键信息揭示、人物状态变化和结尾状态/钩子，禁止展开为场景级正文。
- Prompt 将摘要长度约束为 300～600 字的语义参考，不新增字数、Token 或模型输出上限校验。
- `PromptBoundaryContractTest` 新增 ARC 摘要粒度契约验证。

### 2026-09-06：ARC 时间线、ChapterPlan 拆解与章节标题一致性

- `NEXT_CHAPTER_OUTLINE_SYSTEM`、`ARC_REGENERATION_SYSTEM` 增加事件去重、禁止时间回跳、禁止重复前章主体和提前展开下一章的约束，保持单一时间线向前推进。
- `CHAPTER_PLAN_SYSTEM` 明确只把 ARC 拆解为开场、关键节拍、冲突升级、转折、人物变化和结尾钩子，职责限定为“怎么写”，不重新定义本章内容。
- 章节上下文加载当前 ARC；生成准入、ChapterPlan 更新、正文持久化均校验 ARC 存在、`outlineNodeCode` 匹配、章节号落在 ARC 范围内以及 `ChapterPlan.title == ARC.title`，不一致直接报数据错误。
- DRAFT、REVIEW、REVISE 和正文持久化统一使用 `ARC.title`；Generate 页只编辑 ChapterPlan 摘要并提交 ARC 标题，Outline 页和 Generate 页均展示 ARC 标题，ChapterPlan 区域不再展示第二个标题。
- 新增/更新 ARC 时间线、场景拆解、上下文一致性、标题来源和前端展示契约测试；验证结果：后端相关定向测试通过，前端 `npm run build` 通过，`OutlineViewContractTest` 定向测试 10 项通过。

### 2026-09-06：Generate 页改为上一章/下一章切换

- 移除 Generate 页的 `el-input-number` 和手动输入章节号，改为 Element Plus `el-button` 组成的“上一章 / 当前章 / 下一章”导航。
- 取消“当前章节”冗余标题和章节计划摘要头部重复的章节号；保留 ARC.title 作为当前章正式标题展示。
- 上一章在第 1 章禁用，下一章按项目预计章节数禁用；加载、生成、保存和确认期间禁止重复切换。
- 验证结果：`GenerateViewChapterPlanContractTest` 定向测试通过，前端 `npm run build` 通过。

### 2026-09-06：Generate 页切章限制在相邻 ARC

- Generate 页上一章/下一章只从已加载且挂在 VOLUME 下的 ARC 列表中定位相邻 ARC，切换目标使用相邻 ARC 的章节边界，不再使用章节号加减。
- 无相邻 ARC 时按钮禁用；DRAFTING、REVIEWING、REVISING、WAITING_HUMAN 及其他进行中的工作流阶段禁止切章，已完成操作的“继续下一章”复用相同 ARC 边界。
- 验证结果：`GenerateViewChapterPlanContractTest` 定向测试通过，前端 `npm run build` 通过，`git diff --check` 通过。

### 2026-09-06：Generate 页增加历史章节计划抽屉

- 当前章节区域增加“查看章节计划”入口，使用 Element Plus `el-drawer` 展示历史章节规划，不新增页面。
- 历史列表由现有 `outlineNodes` 和 `chapterPlans` 投影生成，按 `VOLUME → ARC → ChapterPlan` 分卷、分章展示；章节标题统一使用 ARC.title，ChapterPlan 不存在时显示“未创建”。
- 保存或确认当前 ChapterPlan 后同步更新历史列表，避免抽屉继续显示旧摘要或状态。
- 验证结果：`GenerateViewChapterPlanContractTest` 定向测试通过，前端 `npm run build` 通过。

### 2026-09-06：历史章节计划默认只读，显式操作才切换当前章节

- 历史计划抽屉中的章节目录改为可选择的 Element Plus 文本按钮，点击后仅在抽屉内展示章节号、ARC 标题、ARC 摘要、ChapterPlan 摘要和状态，不修改当前工作章节。
- 详情提供“切换到此章”操作；仅该操作会关闭历史抽屉并写入 `chapterNum`，随后复用既有 watcher 刷新当前 ARC、ChapterPlan、workflow state 和正文结果。
- 验证结果：`GenerateViewChapterPlanContractTest` 定向测试通过，前端 `npm run build` 通过。

### 2026-09-06：简化当前章节区域并收敛历史目录状态

- 当前章节区域调整为“当前章节”标签、上一章/当前章/下一章导航和“查看章节计划”入口，移除重复的当前章节信息展示。
- 历史计划目录每章仅显示章节号、ARC.title、轻量状态（无计划/DRAFT/READY/COMPLETED）和当前工作章节标识；ARC 摘要与 ChapterPlan 摘要仅在详情区展示。
- 验证结果：`GenerateViewChapterPlanContractTest` 定向测试通过，前端 `npm run build` 通过。

### 2026-09-06：默认卷卷纲生成与章节规划解锁

- 新增当前未规划卷的 `VOLUME_OUTLINE` Prompt、Draft 生成/确认 Service 和 HTTP 接口，模型只返回卷级 `title`、`summary`；`VOL_001` 的卷号、结构编码、序号和章节范围由系统固定维护。
- `OutlineView.vue` 在 `VOLUME + UNPLANNED` 时将主操作显示为“生成卷纲”，确认后保存当前卷并推进为 `PLANNED`；随后恢复“生成下一章”和手动添加下一章入口。
- 新增卷纲生成/确认的领域、HTTP、Prompt、模型配置和前端契约测试；`VOLUME_OUTLINE` 使用创作规划模型配置。
- 验证结果：规划领域、HTTP、Prompt、配置和 UI 定向测试共 48 项通过；前端 `npm run build` 通过，`git diff --check` 通过。

### 2026-09-06：默认卷未规划状态与 ARC 创建前置校验

- BOOK 确认及历史迁移创建的默认 `VOL_001 / 卷一` 使用 `UNPLANNED` 状态；同步更新 `docs/sql/schema.sql` 的大纲状态说明。
- `PlanningService` 在 AI 生成/确认下一章和手动创建 ARC 前检查当前卷的摘要与状态，未完成卷纲统一返回“请先完成当前卷规划”；用户保存默认卷摘要后自动将状态推进为现有的 `PLANNED`。
- `OutlineView.vue` 在 AI 生成和手动添加下一章入口增加同文案前置提示；新增规划领域及前端契约回归覆盖默认卷状态、AI/手动拦截和状态推进。
- 验证结果：`RootOutlineConfirmationTest`、`ChildOutlineGenerationTest`、`ManualOutlineCrudTest`、`OutlineViewContractTest` 定向 Maven 测试共 36 项通过；前端 `npm run build` 通过，`git diff --check` 通过。

### 2026-09-06：后继卷结构创建与卷纲上下文补齐

- 新增 `createNextVolume` 服务和 `POST /outlines/{bookNodeCode}/volumes/create` 接口：服务端按 BOOK 下现有最大 `sequenceNo` 创建 `VOL_002`、`VOL_003` 等结构节点，使用 `UNPLANNED` 状态，并在同一事务内按当前卷实际最后一个 ARC 收口章节范围。
- `OutlineView.vue` 的“生成下一卷”先创建后继卷结构，再直接打开统一的“生成卷纲”流程；模型只生成新卷的 `title`、`summary`，确认前不会开放该卷 ARC 生成或手动添加章节，未规划卷也不展示手动添加下一章入口。
- 新卷卷纲 Prompt 补充 BOOK 总纲、前置卷正式卷纲、前置卷 ARC、实际上一章摘要、近期章节记忆、人物和 Story Bible；历史卷纲摘要为空但已有章节时仍优先使用实际章节事实。
- 禁止旧的 BOOK→模型直接生成下一卷路径，后继卷必须先由服务端创建结构节点。
- 验证结果：规划领域、HTTP、Prompt、配置和 UI 定向 Maven 测试共 48 项通过；多模块编译成功；前端 `npm run build` 通过，`git diff --check` 通过。

### 2026-09-06：历史卷兼容与卷状态语义

- 历史大纲读取迁移复用已有默认卷；当卷摘要为空时仅将该卷标记为 `UNPLANNED`，不创建新卷、不修改已有 ARC 的 `parent_id`，不移动章节或 ChapterPlan。
- `OutlineView.vue` 为卷显示“未规划/已规划”标签；选中未规划卷显示“该卷尚未完成卷纲 / 先生成卷纲后再规划章节”，并将“卷一/卷二”等结构名与可选故事标题分开显示。
- 卷结构名统一取服务端 `sequenceNo`；卷纲生成和确认会把模型或用户返回的结构型“卷N/第N卷”标题归一为当前卷名，避免模型覆盖卷序，同时保留非结构型故事标题。
- 验证结果：规划领域和 UI 定向 Maven 测试 41 项通过；前端 `npm run build` 通过；`git diff --check` 通过。新增 MySQL 历史迁移集成测试因本机 root 账号认证失败未执行完成。

### 2026-09-06：后继卷与 ARC 生成准入校验

- 生成 ARC 前统一要求父级当前卷存在且已完成卷纲；生成下一卷前统一要求当前最后一卷已规划，并继续校验至少存在一个 ARC。
- 下一卷序号继续由服务端按当前 BOOK 下最大 `sequenceNo + 1` 计算；仓储层在现有事务和 `FOR UPDATE` 行锁流程内再次校验当前卷规划状态、ARC 存在性和后继序号，避免并发或绕过服务层造成非法卷结构。
- 补充未规划卷禁止创建下一卷的领域回归，覆盖“卷纲确认后才能生成 ARC/下一卷”的新项目流程约束。
- 验证结果：规划领域、仓储编译和 UI 定向 Maven 测试 41 项通过；历史迁移 MySQL 集成测试仍因本机 root 账号认证失败未执行完成。

### 2026-09-06：Generate 页面双栏与章节计划摘要布局

- `GenerateView.vue` 桌面布局固定为 `340～380px` 左栏与 `minmax(0, 1fr)` 右栏，左栏顶层子容器统一设置 `width: 100%`、`min-width: 0` 和 `box-sizing: border-box`，页面根容器继续隐藏横向溢出。
- `chapter-plan-summary` 改为纵向 flex 布局；摘要文本使用正常换行、断词和 `overflow-wrap: anywhere`，避免窄栏中每几个字折行；右侧结果区保持弹性占位。
- “查看章节计划”保持在当前章节区域的正常文档流中，取消右对齐挤压布局；同步更新 Generate 视觉契约测试。
- 验证结果：`CreationCanvasVisualContractTest` 与 `GenerateViewChapterPlanContractTest` 共 26 项通过；前端 `npm run build` 通过。

### 2026-09-06：Generate 页面统一正文工作台布局

- `GenerateView.vue` 改为复用 Read 页的“章节目录 + 章节工作区”骨架：左侧目录固定在 `340～380px`，右侧使用弹性正文区，目录选中态、分隔线、工具栏和正文宽度统一采用 Read 页语义。
- 移除左侧 generation-panel 和主页面常驻 ChapterPlan 摘要；“查看章节计划”改为打开 Element Plus Drawer，Drawer 内保留 `章纲 / ChapterPlan / 状态 / 编辑计划 / AI 生成/调整` 及 Draft 确认流程。
- 正文区仅展示空态、流式正文和完整正文；章节目录与右侧正文各自承担滚动，顶部章节栏和底部 workflow 操作栏保持稳定，生成中上一章/下一章自动禁用。
- 同步更新 Generate、章节计划、流式滚动和人工审核相关契约测试。
- 验证结果：`npm run build` 通过；Generate 相关 Maven 定向测试 35 项全部通过；`git diff --check` 通过。

### 2026-09-06：统一全站滚动职责与 Design Tokens

- `App.vue` 将 AppShell 固定为视口高度并隐藏页面级溢出，普通页面由 `studio-main` 承担主滚动；Read/Generate 工作台根节点隐藏溢出，章节目录、正文画布和 Outline 左右工作区保留明确的内部滚动边界。
- 移除 Outline AI 草稿表单在移动端的嵌套限高，AI 生成、重新生成和卷纲 Dialog 统一由 Element Plus Dialog body 自身滚动；Drawer 继续使用自身内容区滚动。
- `theme.css` 建立 `--app-bg`、`--surface`、`--border`、文本/语义色、侧栏/工具栏尺寸、可读宽度和圆角等 canonical tokens，并保留旧变量作为兼容别名；AppShell、Setup、Outline、Read/Generate、Project 及章节工作台共享组件已迁移到新变量，页面样式不再直接写颜色十六进制值。
- 新增 `DesignTokensAndScrollContractTest`，与可读宽度、工作台和页面契约一起验证本次改动。
- 验证结果：相关 Maven 定向测试 35 项全部通过；`npm run build` 通过；`git diff --check` 通过。

### 2026-09-06：坚持单一 UI 框架并完成前端语义泄露扫描

- `novel-agent-web/package.json` 和 `src/main.ts` 保持 Vue 3 + Element Plus，未引入 Ant Design Vue、Naive UI、Arco、Vuetify 或 Quasar；新增 `UiFrameworkContractTest` 固化该约束。
- `uiSemantics.ts` 补充 `userMessage()`，HTTP 响应边界统一把后端状态、节点类型、流程阶段和字段名转换为产品语言，避免错误提示把 `BOOK`、`ARC`、`ChapterPlan` 等开发语义带到 UI。
- `UiSemanticMappingContractTest` 扫描全部 Vue 模板插值和可见枚举文本；状态、节点类型、章节计划和角色状态均通过既有 mapper 展示，模板不直接展示后端字段或枚举。
- 验证结果：UI 框架与语义专项 Maven 定向测试 4 项通过；`npm run build` 通过；`git diff --check` 通过。

### 2026-09-07：增加极简故事状态快照，补强跨章连续性

- 新增 `StoryStateSnapshot`，只保留资源、能力、知识、出场状态四类当前有效状态；`ChapterMemory` 继续只负责短摘要、关键事件、未解决问题和章末钩子。
- COMPRESSION 一次调用接收上一版快照和当前最终正文，同时输出 ChapterMemory 与 `state` 快照；明确保留未改变状态、更新正文明确变化、移除失效状态和禁止推测等规则。DRAFT、REVIEW、REVISE 同时读取历史 ChapterMemory 与最近快照，图状态、章节持久化和人工重同步均保持两者分离。
- `story_summary` 增加快照 JSON 对象列，补齐 MyBatis 映射、历史加载、检查点编解码及提示词边界；数据库设计与章节工作流文档同步更新。
- 验证结果：多模块编译通过；COMPRESSION 相关 ChapterNode、PromptBoundary、PersistChapterNode、CheckpointCodec、StructuredOutputValidation 等定向测试共 58 项通过；`ChapterMemoryMapperTest` 定向测试 2 项通过；MySQL 集成测试受现有 ARC 测试夹具不一致及本机 root 认证失败影响未完成。

### 2026-09-07：明确四类状态提取标准并禁止通用状态系统

- COMPRESSION Prompt 补充 `resources`、`abilities`、`knowledge`、`presence` 的具体提取边界和业务示例，明确资源持有/消耗/丢失、能力与限制、角色已知/未知信息、人物出场与地点连续性分别如何记录。
- `state` 继续限定为四组字符串列表，不新增动态键值、事实关系、来源或置信度等结构；新增契约测试锁定 `StoryStateSnapshot` 的四个 record 组件。
- 验证结果：`PromptBoundaryContractTest` 定向测试 14 项通过；多模块编译成功。

### 2026-09-07：采用替换式快照并补充正文状态连续性检查

- 上下文只传递最近一次有效 `StoryStateSnapshot`，不累积各章状态列表；COMPRESSION 使用上一版快照与当前最终正文生成下一版快照，PERSIST 才将其写入长期存储。
- DRAFT 增加当前有效状态约束，状态改变必须有正文中的明确事件或信息来源；REVIEW 增加资源恢复、物品来源、能力升级、知识越权、人物出场和地点跳变六类状态连续性检查。
- 保持 DRAFT → REVIEW → REVISE → COMPRESSION → PERSIST 链路，DRAFT、REVIEW、REVISE 不产生或持久化 StoryStateSnapshot。
- 验证结果：ChapterNodeTest 与 PromptBoundaryContractTest 定向测试共 51 项通过；多模块编译成功。

### 2026-09-07：补充旧历史兼容、快照数量控制与章节回归验收

- `ChapterHistoryVO` 读取缺失的 `StoryStateSnapshot` 时统一返回四个空列表；旧 `ChapterMemory` 不做迁移，后续章节在首次 COMPRESSION 后自然产生快照。
- COMPRESSION Prompt 要求每个分类只保留少量真正影响连续性的状态，删除失效、重复和不再重要的状态，不为凑数量补充内容，暂不设置固定条数上限。
- 新增回归用例验证当前章节压缩结果可将沈夜的尸核、灵息掌握程度、炼气初阶未知信息及苏晚晴未正式见面状态传递到下一章 DRAFT，并排除无来源尸核、越权术语、未建立能力和无铺垫人物入队提示。
- 未引入状态历史、通用属性系统、复杂合并机制、事实图谱或 RAG 等本 Task 明确排除的结构。
- 验证结果：`ChapterHistoryVOTest`、`ChapterMemoryMapperTest`、`ChapterNodeTest` 与 `PromptBoundaryContractTest` 定向测试共 55 项通过，多模块构建成功。

### 2026-09-07：新增章节生成指标模型与持久化

- `docs/sql/schema.sql` 新增 `generation_metrics` 表，以 `(project_id, chapter_number, generation_session_id)` 复合主键保存 DRAFT、REVIEW、REVISE 调用次数、修订轮数、重试次数、人工介入、耗时、Token 数、最终字数及创建/更新时间，并关联 `novel_project`。
- 新增章节域 `GenerationMetricsDO`、`IGenerationMetricsRepository`，基础设施层新增 `GenerationMetricsPO`、`IGenerationMetricsDao`、`GenerationMetricsRepository` 和 `generation_metrics_mapper.xml`；Repository 使用幂等 upsert 保存并按复合键查询。
- 项目测试清理补充指标表删除顺序，数据库设计文档同步说明指标表职责；持久化契约测试同步覆盖新表和 Mapper。
- 验证结果：多模块 `compile` 通过；`MvpPersistenceContractTest` 定向测试 2 项通过；真实 MySQL `GenerationMetricsRepositoryIntegrationTest` 定向测试 1 项通过，验证全部字段读写和复合键更新后仍只有一条记录。

### 2026-09-07：提供章节生成指标查询接口与 session 汇总

- 新增 `NovelGenerationMetricsController` 的 GET 查询：按现有项目业务编码和章节号查询全部 generation session 指标，并查询项目级 session 汇总；响应包含总生成章节数、平均耗时、平均 Review 次数、平均 Revise 轮数、HITL 次数/比例、Token 总量和平均最终字数，同时保留原始累计字段。
- 指标旁路采集按 `(projectId, chapterNumber, generationSessionId)` 幂等聚合节点调用、重试增量、人工介入、生命周期耗时、最终正文非空白字数和可用 Token；采集/持久化异常由 `ChapterGenerationMetricsRecorder` 隔离，不改变章节工作流结果，REVIEW/REVISE 继续使用现有 `retryCount` 增量语义。
- `docs/sql/schema.sql` 增加旧版 `generation_metrics` 表的幂等兼容迁移，补齐压缩调用、总 Token 和生命周期列，并将耗时/Token 列调整为可空；不引入 MQ、Redis、ES 或 Dashboard。
- 验证结果：多模块 `compile` 通过；`ChapterGenerationMetricsRecorderTest` 2 项、`NovelGenerationMetricsControllerHttpTest` 2 项和真实 MySQL `GenerationMetricsRepositoryIntegrationTest` 3 项定向测试全部通过。

### 2026-09-08：补齐指标会话初始字数

- `ChapterGenerationMetricsRecorder` 创建新 generation session 时将尚未生成正文的最终字数初始化为 0，确保严格 MySQL 模式下首个指标快照能够正常落库。
- 验证结果：随后执行指标采集、HTTP 查询和真实 MySQL 仓储定向测试，共 7 项通过。

### 2026-09-08：完成 Chapter Agent 运行指标生命周期采集

- `ChapterService` 接入指标记录旁路：记录 generation session 开始/结束时间，在 DRAFT、REVIEW、REVISE、COMPRESSION 成功返回后累计调用次数，在进入 HITL 时记录人工介入；未调整状态图、节点路由或业务语义。
- `ChapterModelPort` 与章节节点传递可获取的 Token Usage；模型重试沿用现有 `retryCount`，指标仅记录状态中的重试增量，Token 不可获取时保持为空。
- 指标采集和持久化异常均在 `ChapterGenerationMetricsRecorder` 内隔离；新增单章 session 明细、项目汇总 GET 查询，未引入额外基础设施。
- 验证结果：多模块编译通过；指标增量、采集器、HTTP 查询、持久化契约、章节节点定向测试通过，真实 MySQL 指标仓储 3 项定向测试通过，`git diff --check` 无错误。

### 2026-09-09：清理公开分支配置中的敏感信息

- `novel-agent-app/src/main/resources/application-dev.yml`、`application-prod.yml` 和 `application-stress.yml` 移除数据库连接信息及模型 API Key，改为通过环境变量注入。
- `.gitignore` 增加本地 `.env` 和本地 application 配置忽略规则，避免后续误提交密钥。
- 验证结果：配置文件中不再保留明文数据库凭据或模型 API Key；公开分支采用无父提交的干净快照，不包含原私有提交历史。
