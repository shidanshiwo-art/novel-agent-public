-- Novel Agent optional development seed data.
-- Run after schema.sql. Statements are safe to execute repeatedly and do not
-- overwrite records that have already been edited through the application.

USE novel_agent;

INSERT IGNORE INTO novel_project
    (project_code, title, genre, target_chapter_count, words_per_chapter,
     current_chapter_number, status)
VALUES
    ('demo-story', '钟楼残页', '玄幻悬疑', 100, 2500, 0, 'DRAFT');

INSERT IGNORE INTO story_bible
    (project_id, one_sentence_premise, core_theme, main_conflict,
     ending_direction, world_background, power_system_json, hard_rules_json,
     style_guide, status)
SELECT id,
       '失忆守钟人林渊从一张会改写现实的残页开始，追查自己被抹去的过去。',
       '记忆是否决定一个人是谁',
       '林渊必须使用残页的力量寻找真相，但每次改写都会让他失去一段真实记忆。',
       '林渊在保留自我与拯救城市之间作出最终选择。',
       '旧城由十二座钟楼维持时间秩序，午夜后出现的异常由守钟人处理。',
       JSON_OBJECT('name', '残页刻印', 'description', '一种可以改写现实的特殊体系；每次使用都会让使用者失去一段真实记忆。'),
       JSON_ARRAY('残页不能创造生命', '同一事件只能被改写一次'),
       '第三人称限知，节奏紧凑，避免连续解释设定。',
       'DRAFT'
FROM novel_project
WHERE project_code = 'demo-story';

INSERT IGNORE INTO story_character
    (project_id, character_code, name, role_type, gender, age_description,
     appearance, personality, background_story, current_state_json,
     life_status, status)
SELECT id,
       'lin-yuan',
       '林渊',
       'MALE_LEAD',
       'MALE',
       '二十四岁',
       '黑发灰眸，常穿旧守钟人制服。',
       '克制、谨慎，对无法解释的细节有近乎偏执的敏锐。',
       '三年前在钟楼事故中失去大部分记忆，此后独自看守午夜钟楼。',
       JSON_OBJECT('summary', '位于午夜钟楼，正在查明残页来历', '_updatedChapter', 0),
       'ALIVE',
       'ACTIVE'
FROM novel_project
WHERE project_code = 'demo-story';

UPDATE outline_node arc
JOIN novel_project project ON project.id = arc.project_id
SET arc.end_chapter = arc.start_chapter
WHERE project.project_code = 'demo-story'
  AND arc.node_code = 'demo-arc-1'
  AND arc.node_kind = 'ARC';

-- Demo outline tree: BOOK -> VOLUME -> ARC. Chapter plans are created separately by the application.
INSERT IGNORE INTO outline_node
    (project_id, parent_id, node_code, node_kind, sequence_no,
     start_chapter, end_chapter, title, summary, status)
SELECT id, NULL, 'demo-book', 'BOOK', 1,
       1, target_chapter_count, '钟楼残页', '林渊追查残页与失忆真相的全书大纲。', 'PLANNED'
FROM novel_project
WHERE project_code = 'demo-story';

INSERT IGNORE INTO outline_node
    (project_id, parent_id, node_code, node_kind, sequence_no,
     start_chapter, end_chapter, title, summary, status)
SELECT project.id, book.id, 'demo-volume-1', 'VOLUME', 1,
       1, project.target_chapter_count, '午夜钟楼', '围绕残页现世和钟楼调查展开的第一卷。', 'PLANNED'
FROM novel_project project
JOIN outline_node book
  ON book.project_id = project.id AND book.node_code = 'demo-book'
WHERE project.project_code = 'demo-story';

INSERT IGNORE INTO outline_node
    (project_id, parent_id, node_code, node_kind, sequence_no,
     start_chapter, end_chapter, title, summary, status)
SELECT project.id, volume.id, 'demo-arc-1', 'ARC', 1,
       1, 1, '残页初现', '林渊在午夜钟楼发现会改写现实的残页，并留下议会徽记线索。', 'PLANNED'
FROM novel_project project
JOIN outline_node volume
  ON volume.project_id = project.id AND volume.node_code = 'demo-volume-1'
WHERE project.project_code = 'demo-story';

INSERT IGNORE INTO chapter_plan
    (project_id, outline_node_id, chapter_number, title, summary, status)
SELECT project.id, arc.id, 1, '午夜钟声', '林渊在钟楼发现残页，并确认其能够改写现实。', 'PLANNED'
FROM novel_project project
JOIN outline_node arc
  ON arc.project_id = project.id AND arc.node_code = 'demo-arc-1'
WHERE project.project_code = 'demo-story';
