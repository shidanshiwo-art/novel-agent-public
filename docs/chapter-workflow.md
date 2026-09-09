# 章节生成具体流程

## 总览

```text
HTTP GenerateChapterRequest
  -> ChapterGenerationCommand
  -> LOAD_CONTEXT
  -> DRAFT
  -> REVIEW
  -> REVISE
  -> COMPRESSION
  -> PERSIST
  -> ChapterGenerationResult
```

工作流状态集中保存在 `ChapterGenerationContext`。一个节点成功返回后才会写入 `completedStages`；节点抛出异常时，后续节点不会执行。因此返回的阶段列表既能证明同步调用走完了哪些步骤，也能作为未来异步任务进度模型的基础。

DRAFT、REVIEW、REVISE 和 COMPRESSION 的模型调用共享当前 workflow 的重试预算，最多消耗三次额外重试；首次调用不计入预算。`retryCount` 累计记录已成功模型节点实际消耗的重试次数。REVIEW 的报告边界校验也在审稿重试闭包内执行；evidence 仅在统一 CRLF/LF、首尾空白和连续空白后判断是否为正文连续片段，不做语义模糊匹配。校验重试耗尽后进入 `REVIEW_FAILED` 检查点并保留当前正文，人工可重新审稿、接受正文或结束流程；其他节点参数、节点输出校验、路由和 PERSIST 不自动重试。

## Story Bible 在后续生成链路中的职责

Story Bible 不是每个阶段都要完整复制的背景资料，而是按权威程度分层使用：

- `worldBackground` 与 `hardRules` 定义世界边界。后续生成不得与其明显冲突。
- `oneSentencePremise`、`coreTheme`、`mainConflict`、`endingDirection` 是创作方向，用来帮助模型理解故事要往哪里发展，不视为已经发生的绝对事实。
- `styleGuide` 是正文写作指导，只约束表达方式、视角和语气，不属于世界规则。

各生成阶段的读取范围固定如下：

| 阶段 | 主要读取内容 | 使用原则 |
| --- | --- | --- |
| 大纲（Outline） | 创作方向 + 世界背景 | 用世界背景确定故事可以成立的边界，用创作方向确定整体走向。 |
| 章节章纲 | 标题 + 章节摘要 | 由 `LOAD_CONTEXT` 加载已确认的当前章节章纲，作为正文生成目标，不再调用模型二次规划。 |
| 正文（DRAFT / REVISE） | 世界背景 + 相关 `hardRules` + `styleGuide` | 世界背景用于避免明显越界，规则只在相关场景主动使用，文风用于正文表达。 |

世界背景的作用是限制模型不要明显超出世界边界，不要求每章重复解释或体现背景。`hardRules` 只有在当前情节相关时才需要主动写入计划或正文，但无论是否主动呈现，生成结果都不能与任何适用的硬规则冲突。

当前不新增 Setting RAG、向量库、token budget 或复杂规则检索。若未来设定规模持续增长，再单独评估相关规则筛选机制；在此之前按上述固定范围和现有上下文装载方式传递 Story Bible。

## 1. LOAD_CONTEXT

实现：`ContextRepository`

- 输入：`projectId`、`chapterNumber`。
- 查询：`chapterNumber` 之前最近八章 ChapterMemory、最近一次 `StoryStateSnapshot`，以及 `chapterNumber - 1` 的定稿正文。状态只取一个最新有效快照，不把各章状态列表累积传入模型。
- 人物上下文：Draft、Review、Revise 使用正式人物静态设定；历史剧情从 ChapterMemory 获取，当前有效状态从最近一次 StoryStateSnapshot 获取，不使用 `current_state_json` 或 `life_status` 作为剧情状态来源。
- 输出：`storyContext`。
- 失败：终止流程，不调用模型。
- 后续扩展：加入当前章节涉及的人物、地点、时间线和未回收伏笔；按 token 预算裁剪。

## 2. DRAFT

实现：`DraftChapterNode`

- 输入：`storyContext`、已确认的当前章节章纲。
- 人物上下文：使用正式人物静态设定、近期 ChapterMemory 和最近一次 StoryStateSnapshot，不读取 `current_state_json` 作为剧情状态。
- 输出：`draft`。
- 约束：不得修改已确认的章节计划和适用设定；新增内容只属于当前正文，是否进入后续章节上下文由 COMPRESSION 统一整理。
- 状态约束：不得无原因违反当前有效状态；如果本章改变某项状态，正文中必须出现明确事件或信息来源。
- 失败：从初稿节点重试，不重新加载上下文。
- 后续扩展：支持按场景分段生成，再合并为章节初稿。

## 3. REVIEW

实现：`ReviewChapterNode`

- 输入：`storyContext`、已确认的当前章节章纲、`draft`。
- 人物上下文：使用相关人物的正式静态设定、上一章 ChapterMemory 和最近一次 StoryStateSnapshot，不读取 `current_state_json` 作为剧情状态。
- 输出：`ReviewReport`。
- 检查项：时间线冲突、人物动机、世界规则、剧情推进、重复信息、文风和结尾钩子。
- 状态连续性检查：资源是否凭空恢复、物品是否无来源出现、能力是否突然升级、角色是否使用尚未获得的知识、人物是否无铺垫出现、当前地点或状态是否无原因跳变。
- 失败：报告校验在重试闭包内执行；重试耗尽后进入可恢复的 `REVIEW_FAILED` 检查点，不进入改稿，不覆盖已经生成的正文。人工可选择重新审稿、接受当前正文并继续 COMPRESSION，或结束流程。
- 后续扩展：把审稿拆成连续性、剧情、人物和文风四个并行检查器，最终合并结构化问题。

## 4. REVISE

实现：`ReviseChapterNode`

- 输入：`storyContext`、已确认的当前章节章纲、`draft`、`ReviewReport`。
- 人物上下文：使用正式人物静态设定、近期 ChapterMemory 和最近一次 StoryStateSnapshot，不读取 `current_state_json` 或 `life_status` 作为剧情状态。
- 输出：`revisedContent`。
- 规则：只修复有证据的问题，不允许为了修稿悄悄改写已确认的章节计划和适用设定。
- 失败：保留初稿和审稿报告后重试。
- 路由：`BLOCKER` 或 `MAJOR` 最多自动改写三轮，仍未解决则进入人工确认；仅有 `MINOR` 时允许继续。

## 5. COMPRESSION

实现：`CompressChapterNode`

- 输入：当前章节号、项目角色姓名清单、上一版 StoryStateSnapshot 和当前章节完整正文；需要判断章节核心推进时，附带当前章节章纲的标题与摘要。
- 不输入：大量历史、数据库字段或 `characterCode`。
- 输出：一次模型调用同时返回独立的章节记忆与 `state` 状态快照。ChapterMemory 包含 `shortSummary`、`keyEvents`、`unresolved` 和 `endingHook`；`shortSummary` 必须简洁概括本章主要推进和核心冲突结果，不复述全部正文、不写未来剧情或猜测；`keyEvents` 每条只描述“谁、做了什么、产生了什么结果”。`state` 只包含 `resources`、`abilities`、`knowledge` 和 `presence` 四类当前有效状态，并作为本章结束后的最新 StoryStateSnapshot。
- 核心原则：这不是知识图谱抽取，而是面向后续章节的短期记忆压缩；结果区分关键剧情、未解决问题和结尾钩子，只保留影响后续剧情连续性的内容。
- 明确禁止：琐碎动作、瞬时身体反应、无后续意义的场景细节和原子化过度拆分。
- `keyEvents` 只要影响后续剧情，就必须保留人物关键行动、位置变化、目标变化和重大状态变化，并写明事件结果。
- `unresolved` 只记录正文已经提出、但本章结束时仍未解决的问题，不记录成功与否的预测、未来可能发生的事情或作者创作建议。
- `endingHook` 单独保留本章结尾已经出现的明确钩子，用于下一章衔接；没有明确钩子时返回 `无`，不得返回 `null`，不补写正文未出现的悬念。
- `StoryStateSnapshot` 只记录本章结束时仍然有效、后续章节忘记后容易写错的状态：重要资源/物品，能力、伤势、限制和掌握程度，角色已知或未知的重要信息，以及地点和登场/离场状态。没有内容时对应数组为空；不拆分为更多自由字段。
- 状态更新规则：上一版状态未被正文改变时必须保留；正文明确产生新状态时更新；已失效状态不得保留；不得根据常识或剧情趋势推测；只保存遗忘后会造成明显逻辑错误的状态，不保存琐碎动作和短暂细节。
- 状态数量控制：每个分类只保留少量真正影响后续连续性的状态，删除已经失效、重复或不再重要的状态；不为凑数量补充内容，暂不设置固定条数上限。
- 四类提取标准：`resources` 只记重要资源/物品的持有、消耗或丢失；`abilities` 只记能力、伤势、限制和掌握程度，不把短暂动作当能力；`knowledge` 记录已知或尚未知的重要信息，防止角色越权获知；`presence` 只记会影响后续人物出场或地点连续性的地点、正式登场和离场状态。
- `state` 始终是这四组字符串列表，不引入动态键值、事实关系或来源、置信度等附加结构。
- 判断标准：如果下一章不知道某条信息，会不会容易写错剧情连续性或未解决问题；只有答案为“会”的信息才保留。
- 压缩范围：改变后续因果的关键事件、主角行动线结果、代价、未解决问题和下一章必须承接的结尾钩子。
- 明确舍弃：无关场景细节、普通动作、轻微伤痕、短暂情绪、衣物或装备的临时损耗、修辞、重复信息、猜测、计划和未实现的意图；除非这些细节明显改变后续剧情或未解决问题。
- 失败：正文尚未写入数据库，避免正文与长期记忆不一致。
- 历史剧情连续性使用 ChapterMemory；当前有效状态使用 StoryStateSnapshot，两者不互相替代。

## 6. PERSIST

实现：`PersistChapterNode` + `ChapterPersistRepository`

- 输入：项目、章节号、定稿正文、ChapterMemory 和 StoryStateSnapshot。
- 数据库：`novel_project`、`story_chapter`、`story_summary`、`chapter_plan`；ChapterMemory 与 StoryStateSnapshot 均写入 `story_summary`，不根据它们写入 `story_character.current_state_json`。
- 事务：正文、章节压缩记忆、状态快照、项目进度与章节卡更新在同一事务内完成。
- 幂等：正文和摘要都以项目与章节号唯一；同一章节重跑不会产生多份记录，当前内容会被替换。
- 失败：事务整体回滚。
- 人工修改正文后：正文先提交并标记为 `DIRTY`，章节记忆标记为 `STALE`，随后自动通过 `COMPRESSION` 生成新的章节记忆；失败时保留已保存正文和 `DIRTY` 状态，允许再次更新。
- 后续扩展：同时写 `chapter_version` 和 outbox 事件，再由异步消费者更新搜索索引。

## 同步版到异步版的映射

章节生成 Session 的 SSE 事件统一使用 `content` 字段传递正文片段或可读消息。`REVIEW_FAILED`、`GENERATION_FAILED`、`GENERATION_ABORTED` 和 `GENERATION_CANCELLED` 必须携带失败或终止原因，前端据此展示具体工作流反馈。

当前版本适合验证领域边界。真实模型接入后，单章可能耗时几十秒到数分钟，应增加 `generation_job` 和 `generation_step`：

```text
generation_job
  id, project_id, chapter_number, status, current_stage,
  request_id, retry_count, created_at, updated_at

generation_step
  job_id, stage, status, input_snapshot, output_snapshot,
  prompt_version, model, tokens, cost, error_message
```

调度器每次只推进一个未完成节点。节点输出落库后再确认阶段完成，因此进程重启后可以从最近一个成功节点继续，而不是从头生成整章。
