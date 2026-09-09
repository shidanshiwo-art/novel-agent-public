-- Novel Agent clean-install schema.
-- Creates the database and every application-owned table.

CREATE DATABASE IF NOT EXISTS novel_agent
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE novel_agent;

-- Development migration: remove the retired atomic-fact table before applying
-- the current ChapterMemory-based schema.
DROP TABLE IF EXISTS story_fact;

CREATE TABLE IF NOT EXISTS novel_project (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_code VARCHAR(64) NOT NULL COMMENT '对外项目编号',
    title VARCHAR(200) NOT NULL COMMENT '小说名称',
    genre VARCHAR(64) NOT NULL COMMENT '题材',
    target_chapter_count INT UNSIGNED DEFAULT NULL COMMENT '目标章节数',
    words_per_chapter INT UNSIGNED NOT NULL DEFAULT 2500 COMMENT '单章目标字数',
    current_chapter_number INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '当前已生成章节',
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/READY/WRITING/COMPLETED/ARCHIVED',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_novel_project_code (project_code),
    KEY idx_novel_project_status (status, updated_at),
    CONSTRAINT ck_novel_project_words CHECK (words_per_chapter > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='小说项目';

CREATE TABLE IF NOT EXISTS story_bible (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT UNSIGNED NOT NULL COMMENT '小说项目',
    one_sentence_premise VARCHAR(1000) NOT NULL COMMENT '一句话故事',
    core_theme TEXT DEFAULT NULL COMMENT '核心命题',
    main_conflict TEXT DEFAULT NULL COMMENT '核心矛盾',
    ending_direction TEXT DEFAULT NULL COMMENT '结局方向',
    world_background LONGTEXT DEFAULT NULL COMMENT '世界背景',
    power_system_json JSON DEFAULT NULL COMMENT '特殊体系设定（JSON）',
    hard_rules_json JSON DEFAULT NULL COMMENT '不可违反的世界规则',
    style_guide LONGTEXT DEFAULT NULL COMMENT '文风规范',
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/CONFIRMED',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_story_bible_project (project_id),
    CONSTRAINT fk_story_bible_project FOREIGN KEY (project_id) REFERENCES novel_project (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='小说圣经';

CREATE TABLE IF NOT EXISTS story_character (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT UNSIGNED NOT NULL COMMENT '小说项目',
    character_code VARCHAR(64) NOT NULL COMMENT '项目内稳定人物编号',
    name VARCHAR(100) NOT NULL COMMENT '姓名',
    role_type VARCHAR(32) NOT NULL COMMENT 'MALE_LEAD/FEMALE_LEAD/ALLY/RIVAL/ANTAGONIST/SUPPORTING',
    gender VARCHAR(16) NOT NULL DEFAULT 'OTHER' COMMENT 'MALE/FEMALE/OTHER',
    age_description VARCHAR(100) DEFAULT NULL COMMENT '年龄或外观年龄',
    appearance TEXT DEFAULT NULL COMMENT '外貌',
    personality TEXT DEFAULT NULL COMMENT '性格',
    background_story LONGTEXT DEFAULT NULL COMMENT '人物背景',
    note TEXT DEFAULT NULL COMMENT '作者备注',
    current_state_json JSON DEFAULT NULL COMMENT '人物当前运行时状态',
    life_status VARCHAR(32) NOT NULL DEFAULT 'ALIVE' COMMENT 'ALIVE/DEAD/MISSING/UNKNOWN',
    status VARCHAR(32) NOT NULL DEFAULT 'PLANNED' COMMENT 'PLANNED/ACTIVE/EXITED',
    male_lead_project_guard BIGINT UNSIGNED
        GENERATED ALWAYS AS (CASE WHEN role_type = 'MALE_LEAD' THEN project_id ELSE NULL END) STORED
        COMMENT '单男主唯一索引辅助列',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_story_character_code (project_id, character_code),
    UNIQUE KEY uk_single_male_lead (male_lead_project_guard),
    KEY idx_story_character_role (project_id, role_type, status),
    KEY idx_story_character_name (project_id, name),
    CONSTRAINT fk_story_character_project FOREIGN KEY (project_id) REFERENCES novel_project (id),
    CONSTRAINT ck_story_character_male_lead CHECK (role_type <> 'MALE_LEAD' OR gender = 'MALE')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='人物主数据';

CREATE TABLE IF NOT EXISTS outline_node (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT UNSIGNED NOT NULL COMMENT '小说项目',
    parent_id BIGINT UNSIGNED DEFAULT NULL COMMENT '父节点',
    node_code VARCHAR(64) NOT NULL COMMENT '项目内稳定节点编号',
    node_kind VARCHAR(32) NOT NULL COMMENT 'BOOK/VOLUME/ARC',
    sequence_no INT UNSIGNED NOT NULL COMMENT '同级排序',
    start_chapter INT UNSIGNED DEFAULT NULL COMMENT '章节范围起始章节',
    end_chapter INT UNSIGNED DEFAULT NULL COMMENT '章节范围结束章节',
    title VARCHAR(300) NOT NULL COMMENT '标题',
    summary LONGTEXT NOT NULL COMMENT '节点自然语言大纲',
    status VARCHAR(32) NOT NULL DEFAULT 'PLANNED' COMMENT 'UNPLANNED/PLANNED/READY/COMPLETED/CANCELLED',
    book_project_guard BIGINT UNSIGNED
        GENERATED ALWAYS AS (CASE WHEN node_kind = 'BOOK' THEN project_id ELSE NULL END) STORED
        COMMENT '项目内 BOOK 唯一约束辅助列',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_outline_node_code (project_id, node_code),
    UNIQUE KEY uk_outline_project_id (project_id, id),
    UNIQUE KEY uk_outline_single_book (book_project_guard),
    KEY idx_outline_node_tree (project_id, parent_id, sequence_no),
    KEY idx_outline_node_kind (project_id, node_kind, status),
    CONSTRAINT fk_outline_node_project FOREIGN KEY (project_id) REFERENCES novel_project (id),
    CONSTRAINT fk_outline_node_parent FOREIGN KEY (project_id, parent_id)
        REFERENCES outline_node (project_id, id),
    CONSTRAINT ck_outline_node_kind CHECK (node_kind IN ('BOOK', 'VOLUME', 'ARC')),
    CONSTRAINT ck_outline_node_parent CHECK (
        (node_kind = 'BOOK' AND parent_id IS NULL)
        OR (node_kind <> 'BOOK' AND parent_id IS NOT NULL)
    ),
    CONSTRAINT ck_outline_node_chapter_range CHECK (
        (start_chapter IS NULL AND end_chapter IS NULL)
        OR (start_chapter IS NOT NULL AND end_chapter IS NOT NULL
            AND start_chapter <= end_chapter)
    ),
    CONSTRAINT ck_outline_arc_single_chapter CHECK (
        node_kind <> 'ARC'
        OR (start_chapter IS NOT NULL AND end_chapter IS NOT NULL
            AND start_chapter = end_chapter)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='大纲树节点';

CREATE TABLE IF NOT EXISTS chapter_plan (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT UNSIGNED NOT NULL COMMENT '小说项目',
    outline_node_id BIGINT UNSIGNED NOT NULL COMMENT '所属大纲叶节点',
    chapter_number INT UNSIGNED NOT NULL COMMENT '章节号',
    title VARCHAR(300) NOT NULL COMMENT '章节标题',
    summary LONGTEXT NOT NULL COMMENT '章节计划',
    status VARCHAR(32) NOT NULL DEFAULT 'PLANNED' COMMENT 'PLANNED/READY/COMPLETED/CANCELLED',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_chapter_plan_project_number (project_id, chapter_number),
    UNIQUE KEY uk_chapter_plan_identity (project_id, id, chapter_number),
    KEY idx_chapter_plan_node_number (project_id, outline_node_id, chapter_number),
    CONSTRAINT fk_chapter_plan_project FOREIGN KEY (project_id) REFERENCES novel_project (id),
    CONSTRAINT fk_chapter_plan_outline FOREIGN KEY (project_id, outline_node_id)
        REFERENCES outline_node (project_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='章节计划';

CREATE TABLE IF NOT EXISTS story_chapter (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT UNSIGNED NOT NULL COMMENT '小说项目',
    chapter_plan_id BIGINT UNSIGNED NOT NULL COMMENT '章节计划',
    chapter_number INT UNSIGNED NOT NULL COMMENT '章节号',
    title VARCHAR(300) NOT NULL COMMENT '章节标题',
    content LONGTEXT NOT NULL COMMENT '当前正文',
    word_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '正文字数',
    status VARCHAR(32) NOT NULL DEFAULT 'FINALIZED' COMMENT 'DRAFT/FINALIZED/DIRTY',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_story_chapter (project_id, chapter_number),
    UNIQUE KEY uk_story_chapter_identity (project_id, id, chapter_number),
    KEY idx_story_chapter_status (project_id, status, chapter_number),
    KEY idx_story_chapter_plan (project_id, chapter_plan_id, chapter_number),
    CONSTRAINT fk_story_chapter_project FOREIGN KEY (project_id) REFERENCES novel_project (id),
    CONSTRAINT fk_story_chapter_plan
        FOREIGN KEY (project_id, chapter_plan_id, chapter_number)
        REFERENCES chapter_plan (project_id, id, chapter_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='章节正文';

CREATE TABLE IF NOT EXISTS story_summary (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT UNSIGNED NOT NULL COMMENT '小说项目',
    chapter_id BIGINT UNSIGNED NOT NULL COMMENT '来源章节',
    chapter_number INT UNSIGNED NOT NULL COMMENT '章节号',
    short_summary VARCHAR(2000) NOT NULL COMMENT '章节压缩记忆短摘要',
    key_events_json JSON DEFAULT NULL COMMENT '章节压缩记忆关键事件',
    unresolved_questions_json JSON DEFAULT NULL COMMENT '未解决问题',
    ending_hook TEXT DEFAULT NULL COMMENT '章末钩子',
    story_state_snapshot_json JSON DEFAULT NULL COMMENT '当前故事状态快照',
    status VARCHAR(32) NOT NULL DEFAULT 'GENERATED' COMMENT 'GENERATED/VERIFIED/STALE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_story_summary_chapter (chapter_id),
    UNIQUE KEY uk_story_summary_number (project_id, chapter_number),
    KEY idx_story_summary_recent (project_id, status, chapter_number),
    CONSTRAINT fk_story_summary_project FOREIGN KEY (project_id) REFERENCES novel_project (id),
    CONSTRAINT fk_story_summary_chapter
        FOREIGN KEY (project_id, chapter_id, chapter_number)
        REFERENCES story_chapter (project_id, id, chapter_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='章节压缩记忆';

CREATE TABLE IF NOT EXISTS generation_metrics (
    project_id BIGINT UNSIGNED NOT NULL COMMENT '小说项目',
    chapter_number INT UNSIGNED NOT NULL COMMENT '章节号',
    generation_session_id VARCHAR(64) NOT NULL COMMENT '章节生成会话编号',
    draft_calls INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'DRAFT 模型调用次数',
    review_calls INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'REVIEW 模型调用次数',
    revise_calls INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'REVISE 模型调用次数',
    compression_calls INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'COMPRESSION 模型调用次数',
    revise_rounds INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'REVISE 修订轮数',
    retry_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '重试次数',
    human_intervened BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否发生人工介入',
    generation_duration_ms BIGINT UNSIGNED DEFAULT NULL COMMENT '生成耗时毫秒',
    input_tokens BIGINT UNSIGNED DEFAULT NULL COMMENT '输入 Token 数，不可获取时为空',
    output_tokens BIGINT UNSIGNED DEFAULT NULL COMMENT '输出 Token 数，不可获取时为空',
    total_tokens BIGINT UNSIGNED DEFAULT NULL COMMENT '总 Token 数，不可获取时为空',
    final_word_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '最终正文词数',
    generation_started_at DATETIME(3) DEFAULT NULL COMMENT '生成开始时间',
    generation_ended_at DATETIME(3) DEFAULT NULL COMMENT '生成结束时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (project_id, chapter_number, generation_session_id),
    CONSTRAINT fk_generation_metrics_project FOREIGN KEY (project_id) REFERENCES novel_project (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='章节生成指标';

-- Development migration: make an existing generation_metrics table compatible
-- with the current nullable Usage and lifecycle fields.
ALTER TABLE generation_metrics
    MODIFY COLUMN generation_duration_ms BIGINT UNSIGNED DEFAULT NULL
        COMMENT '生成耗时毫秒',
    MODIFY COLUMN input_tokens BIGINT UNSIGNED DEFAULT NULL
        COMMENT '输入 Token 数，不可获取时为空',
    MODIFY COLUMN output_tokens BIGINT UNSIGNED DEFAULT NULL
        COMMENT '输出 Token 数，不可获取时为空';

SET @generation_metrics_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'generation_metrics'
          AND column_name = 'compression_calls'
    ),
    'SELECT 1',
    'ALTER TABLE generation_metrics ADD COLUMN compression_calls INT UNSIGNED NOT NULL DEFAULT 0 AFTER revise_calls'
);
PREPARE generation_metrics_stmt FROM @generation_metrics_sql;
EXECUTE generation_metrics_stmt;
DEALLOCATE PREPARE generation_metrics_stmt;

SET @generation_metrics_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'generation_metrics'
          AND column_name = 'total_tokens'
    ),
    'SELECT 1',
    'ALTER TABLE generation_metrics ADD COLUMN total_tokens BIGINT UNSIGNED DEFAULT NULL AFTER output_tokens'
);
PREPARE generation_metrics_stmt FROM @generation_metrics_sql;
EXECUTE generation_metrics_stmt;
DEALLOCATE PREPARE generation_metrics_stmt;

SET @generation_metrics_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'generation_metrics'
          AND column_name = 'generation_started_at'
    ),
    'SELECT 1',
    'ALTER TABLE generation_metrics ADD COLUMN generation_started_at DATETIME(3) DEFAULT NULL AFTER final_word_count'
);
PREPARE generation_metrics_stmt FROM @generation_metrics_sql;
EXECUTE generation_metrics_stmt;
DEALLOCATE PREPARE generation_metrics_stmt;

SET @generation_metrics_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'generation_metrics'
          AND column_name = 'generation_ended_at'
    ),
    'SELECT 1',
    'ALTER TABLE generation_metrics ADD COLUMN generation_ended_at DATETIME(3) DEFAULT NULL AFTER generation_started_at'
);
PREPARE generation_metrics_stmt FROM @generation_metrics_sql;
EXECUTE generation_metrics_stmt;
DEALLOCATE PREPARE generation_metrics_stmt;

CREATE TABLE IF NOT EXISTS chapter_model_trace (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    workflow_id VARCHAR(64) NOT NULL COMMENT '章节生成工作流编号',
    project_code VARCHAR(64) NOT NULL COMMENT '项目业务编码',
    chapter_number INT UNSIGNED NOT NULL COMMENT '章节号',
    node VARCHAR(32) NOT NULL COMMENT 'DRAFT/REVIEW/REVISION/COMPRESSION',
    attempt INT UNSIGNED NOT NULL COMMENT '本节点模型调用序号，从0开始',
    system_prompt LONGTEXT NOT NULL COMMENT '完整系统 Prompt',
    user_prompt LONGTEXT NOT NULL COMMENT '完整用户 Prompt',
    response_text LONGTEXT DEFAULT NULL COMMENT '模型原始响应或失败时已接收片段',
    success BOOLEAN NOT NULL COMMENT '本次模型调用及结果校验是否成功',
    error_code VARCHAR(32) DEFAULT NULL COMMENT '失败错误码',
    error_message TEXT DEFAULT NULL COMMENT '失败错误信息',
    duration_ms BIGINT UNSIGNED NOT NULL COMMENT '本次模型调用耗时毫秒',
    created_at DATETIME(3) NOT NULL COMMENT 'Trace 创建时间（UTC）',
    PRIMARY KEY (id),
    KEY idx_chapter_model_trace_workflow (workflow_id, created_at),
    KEY idx_chapter_model_trace_chapter (project_code, chapter_number, node, created_at),
    KEY idx_chapter_model_trace_failure (success, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='章节模型 Prompt 调试 Trace';

CREATE TABLE IF NOT EXISTS LANGRAPH4J_THREAD (
    thread_id VARCHAR(36) NOT NULL COMMENT '内部线程 UUID',
    thread_name VARCHAR(255) NOT NULL COMMENT 'RunnableConfig.threadId',
    is_released BOOLEAN NOT NULL DEFAULT FALSE COMMENT '工作流是否已释放',
    PRIMARY KEY (thread_id),
    UNIQUE KEY IDX_LANGRAPH4J_THREAD_NAME_RELEASED (thread_name, is_released)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='LangGraph4j 工作流线程';

CREATE TABLE IF NOT EXISTS LANGRAPH4J_CHECKPOINT (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '数据库排序主键',
    checkpoint_id VARCHAR(36) NOT NULL COMMENT 'LangGraph4j 检查点 UUID',
    thread_id VARCHAR(36) NOT NULL COMMENT '所属工作流线程',
    node_id VARCHAR(255) DEFAULT NULL COMMENT '当前已执行节点',
    next_node_id VARCHAR(255) DEFAULT NULL COMMENT '断点恢复后的下一节点',
    state_data JSON NOT NULL COMMENT '工作流 State 快照',
    saved_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '保存时间',
    PRIMARY KEY (checkpoint_id),
    UNIQUE KEY uk_langraph4j_checkpoint_order_id (id),
    KEY idx_langraph4j_checkpoint_thread_saved (thread_id, saved_at, id),
    CONSTRAINT LANGRAPH4J_FK_THREAD
        FOREIGN KEY (thread_id)
        REFERENCES LANGRAPH4J_THREAD (thread_id)
        ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='LangGraph4j 工作流检查点';
