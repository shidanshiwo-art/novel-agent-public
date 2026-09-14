package cn.ninth.novel.infrastructure.dao;

import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MvpPersistenceContractTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();
    private static final Pattern CREATE_TABLE_PATTERN = Pattern.compile(
            "(?im)^CREATE TABLE IF NOT EXISTS\\s+([a-z0-9_]+)\\s*\\(");

    private static final Set<String> EXPECTED_TABLES = Set.of(
            "novel_project",
            "story_bible",
            "story_character",
            "outline_node",
            "chapter_plan",
            "story_chapter",
            "story_summary",
            "generation_metrics",
            "chapter_model_trace",
            "memory_commit",
            "memory_accepted_chapter_version",
            "memory_canonical_event",
            "memory_canonical_fact",
            "memory_canonical_projection",
            "memory_outbox",
            "LANGRAPH4J_THREAD",
            "LANGRAPH4J_CHECKPOINT");

    private static final List<MapperContract> MAPPER_CONTRACTS = List.of(
            new MapperContract(
                    "cn.ninth.novel.infrastructure.dao.INovelProjectDao",
                    "novel_project_mapper.xml",
                    Set.of("insert", "update", "advanceProgress", "updateCurrentChapterNumber",
                            "queryByProjectCode")),
            new MapperContract(
                    "cn.ninth.novel.infrastructure.dao.IStoryBibleDao",
                    "story_bible_mapper.xml",
                    Set.of("insert", "updateByProjectId", "queryByProjectId")),
            new MapperContract(
                    "cn.ninth.novel.infrastructure.dao.IStoryCharacterDao",
                    "story_character_mapper.xml",
                    Set.of("insert", "update", "queryByCharacterCode", "queryByProjectId", "queryMaleLead")),
            new MapperContract(
                    "cn.ninth.novel.infrastructure.dao.IOutlineNodeDao",
                    "outline_node_mapper.xml",
                    Set.of("insert", "update", "deleteOne", "queryByCode", "queryById", "queryRoot",
                            "queryChildren", "queryByProject", "queryMaxSequenceNo")),
            new MapperContract(
                    "cn.ninth.novel.infrastructure.dao.IChapterPlanDao",
                    "chapter_plan_mapper.xml",
                    Set.of("insert", "queryByChapterNumber", "queryByOutlineNode", "queryByProject",
                            "update", "updateStatus", "updateStatusIfCurrent", "delete")),
            new MapperContract(
                    "cn.ninth.novel.infrastructure.dao.IStoryChapterDao",
                    "story_chapter_mapper.xml",
                    Set.of("insertOrUpdate", "queryByProjectIdAndChapterNumber",
                            "countByProjectIdAndChapterNumberGreaterThan", "queryMaxChapterNumber")),
            new MapperContract(
                "cn.ninth.novel.infrastructure.dao.IStorySummaryDao",
                "story_summary_mapper.xml",
                Set.of("insertOrUpdate", "queryByProjectIdAndChapterNumber", "queryRecent",
                        "queryAllValidByProjectId")),
            new MapperContract(
                    "cn.ninth.novel.infrastructure.dao.IGenerationMetricsDao",
                    "generation_metrics_mapper.xml",
                    Set.of("insertOrUpdate", "queryByIdentity", "queryByChapter", "queryProjectSummary")));

    @Test
    void shouldProvideCompleteDatabaseSchemaAndSeedEntry() throws Exception {
        Path schemaFile = PROJECT_ROOT.resolve("docs/sql/schema.sql");
        assertTrue(Files.isRegularFile(schemaFile), schemaFile.toString());
        String schema = Files.readString(schemaFile);
        String outlineSchema = schema.substring(
                schema.indexOf("CREATE TABLE IF NOT EXISTS outline_node"),
                schema.indexOf("CREATE TABLE IF NOT EXISTS chapter_plan"));
        String chapterPlanSchema = schema.substring(
                schema.indexOf("CREATE TABLE IF NOT EXISTS chapter_plan"),
                schema.indexOf("CREATE TABLE IF NOT EXISTS story_chapter"));
        Matcher matcher = CREATE_TABLE_PATTERN.matcher(schema);
        Set<String> actual = new java.util.HashSet<>();
        while (matcher.find()) {
            actual.add(matcher.group(1));
        }

        assertEquals(EXPECTED_TABLES, actual);
        assertTrue(schema.contains("CREATE DATABASE IF NOT EXISTS novel_agent"));
        assertTrue(schema.contains("USE novel_agent"));
        assertTrue(schema.contains("uk_single_male_lead"));
        assertTrue(schema.contains("note TEXT DEFAULT NULL"));
        assertTrue(schema.contains("DEFAULT 'OTHER'"));
        assertTrue(!schema.contains("aliases_json"));
        assertTrue(!schema.contains("initial_ability_json"));
        assertTrue(outlineSchema.contains("node_kind VARCHAR(32) NOT NULL"));
        assertTrue(outlineSchema.contains("start_chapter INT UNSIGNED DEFAULT NULL"));
        assertTrue(outlineSchema.contains("end_chapter INT UNSIGNED DEFAULT NULL"));
        assertTrue(outlineSchema.contains("('BOOK', 'VOLUME', 'ARC')"));
        assertTrue(!outlineSchema.contains("STAGE"));
        assertTrue(outlineSchema.contains("uk_outline_single_book"));
        assertTrue(outlineSchema.contains("FOREIGN KEY (project_id, parent_id)"));
        assertTrue(outlineSchema.contains("node_kind = 'BOOK' AND parent_id IS NULL"));
        assertTrue(outlineSchema.contains("node_kind <> 'BOOK' AND parent_id IS NOT NULL"));
        assertTrue(outlineSchema.contains("start_chapter IS NULL AND end_chapter IS NULL"));
        assertTrue(outlineSchema.contains("start_chapter <= end_chapter"));
        assertTrue(!outlineSchema.contains("node_type"));
        assertTrue(!outlineSchema.contains("planned_start_chapter"));
        assertTrue(!outlineSchema.contains("planned_end_chapter"));
        assertTrue(!outlineSchema.contains("chapter_number"));
        assertTrue(!outlineSchema.contains("protagonist_goal"));
        assertTrue(!outlineSchema.contains("conflict_description"));
        assertTrue(!outlineSchema.contains("expected_payoff"));
        assertTrue(!outlineSchema.contains("ending_hook"));
        assertTrue(!outlineSchema.contains("uk_outline_chapter_number"));
        assertTrue(!outlineSchema.contains("ck_outline_chapter_card"));
        assertTrue(chapterPlanSchema.contains("id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT"));
        assertTrue(chapterPlanSchema.contains("project_id BIGINT UNSIGNED NOT NULL"));
        assertTrue(chapterPlanSchema.contains("outline_node_id BIGINT UNSIGNED NOT NULL"));
        assertTrue(chapterPlanSchema.contains("chapter_number INT UNSIGNED NOT NULL"));
        assertTrue(chapterPlanSchema.contains("title VARCHAR(300) NOT NULL"));
        assertTrue(chapterPlanSchema.contains("summary LONGTEXT NOT NULL"));
        assertTrue(chapterPlanSchema.contains("status VARCHAR(32) NOT NULL"));
        assertTrue(chapterPlanSchema.contains("created_at DATETIME(3) NOT NULL"));
        assertTrue(chapterPlanSchema.contains("updated_at DATETIME(3) NOT NULL"));
        assertTrue(chapterPlanSchema.contains("UNIQUE KEY uk_chapter_plan_project_number (project_id, chapter_number)"));
        assertTrue(chapterPlanSchema.contains("UNIQUE KEY uk_chapter_plan_identity (project_id, id, chapter_number)"));
        assertTrue(chapterPlanSchema.contains("FOREIGN KEY (project_id, outline_node_id)"));
        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS story_chapter"));
        String storyChapterSchema = schema.substring(
                schema.indexOf("CREATE TABLE IF NOT EXISTS story_chapter"),
                schema.indexOf("CREATE TABLE IF NOT EXISTS story_summary"));
        String storySummarySchema = schema.substring(
                schema.indexOf("CREATE TABLE IF NOT EXISTS story_summary"),
                schema.indexOf("CREATE TABLE IF NOT EXISTS LANGRAPH4J_THREAD"));
        assertTrue(storyChapterSchema.contains("chapter_plan_id BIGINT UNSIGNED NOT NULL"));
        assertTrue(storyChapterSchema.contains("FOREIGN KEY (project_id, chapter_plan_id, chapter_number)"));
        assertTrue(storyChapterSchema.contains(
                "REFERENCES chapter_plan (project_id, id, chapter_number)"));
        assertTrue(!storyChapterSchema.contains("outline_node_id"));
        assertTrue(schema.contains("FOREIGN KEY (project_id, chapter_id, chapter_number)"));
        assertTrue(storySummarySchema.contains("key_events_json JSON DEFAULT NULL"));
        assertTrue(!storySummarySchema.contains("character_states_json"));
        assertTrue(!storySummarySchema.contains("protagonist_goal"));
        assertTrue(!storySummarySchema.contains("actual_result"));
        assertTrue(!storySummarySchema.contains("price_paid"));
        assertTrue(!storySummarySchema.contains("protagonist_changes_json"));
        assertTrue(!schema.contains("fact_type"));
        assertTrue(schema.contains("LANGRAPH4J_FK_THREAD"));

        String generationMetricsSchema = schema.substring(
                schema.indexOf("CREATE TABLE IF NOT EXISTS generation_metrics"),
                schema.indexOf("CREATE TABLE IF NOT EXISTS chapter_model_trace"));
        assertTrue(generationMetricsSchema.contains("project_id BIGINT UNSIGNED NOT NULL"));
        assertTrue(generationMetricsSchema.contains("chapter_number INT UNSIGNED NOT NULL"));
        assertTrue(generationMetricsSchema.contains("generation_session_id VARCHAR(64) NOT NULL"));
        assertTrue(generationMetricsSchema.contains("draft_calls INT UNSIGNED NOT NULL DEFAULT 0"));
        assertTrue(generationMetricsSchema.contains("review_calls INT UNSIGNED NOT NULL DEFAULT 0"));
        assertTrue(generationMetricsSchema.contains("revise_calls INT UNSIGNED NOT NULL DEFAULT 0"));
        assertTrue(generationMetricsSchema.contains("compression_calls INT UNSIGNED NOT NULL DEFAULT 0"));
        assertTrue(generationMetricsSchema.contains("revise_rounds INT UNSIGNED NOT NULL DEFAULT 0"));
        assertTrue(generationMetricsSchema.contains("retry_count INT UNSIGNED NOT NULL DEFAULT 0"));
        assertTrue(generationMetricsSchema.contains("human_intervened BOOLEAN NOT NULL DEFAULT FALSE"));
        assertTrue(generationMetricsSchema.contains("generation_duration_ms BIGINT UNSIGNED DEFAULT NULL"));
        assertTrue(generationMetricsSchema.contains("input_tokens BIGINT UNSIGNED DEFAULT NULL"));
        assertTrue(generationMetricsSchema.contains("output_tokens BIGINT UNSIGNED DEFAULT NULL"));
        assertTrue(generationMetricsSchema.contains("total_tokens BIGINT UNSIGNED DEFAULT NULL"));
        assertTrue(generationMetricsSchema.contains("final_word_count INT UNSIGNED NOT NULL DEFAULT 0"));
        assertTrue(generationMetricsSchema.contains("generation_started_at DATETIME(3) DEFAULT NULL"));
        assertTrue(generationMetricsSchema.contains("generation_ended_at DATETIME(3) DEFAULT NULL"));
        assertTrue(generationMetricsSchema.contains(
                "PRIMARY KEY (project_id, chapter_number, generation_session_id)"));
        assertTrue(generationMetricsSchema.contains(
                "FOREIGN KEY (project_id) REFERENCES novel_project (id)"));

        Path seedFile = PROJECT_ROOT.resolve("docs/sql/seed.sql");
        assertTrue(Files.isRegularFile(seedFile), seedFile.toString());
        String seed = Files.readString(seedFile);
        assertTrue(seed.contains("INSERT IGNORE INTO novel_project"));
        assertTrue(seed.contains("'demo-story'"));
        assertTrue(seed.contains("INSERT IGNORE INTO outline_node"));
        assertTrue(seed.contains("'BOOK'"));
        assertTrue(seed.contains("'VOLUME'"));
        assertTrue(seed.contains("'ARC'"));
        assertTrue(!seed.contains("'STAGE'"));
        assertTrue(seed.contains("'_updatedChapter'"));
        assertTrue(seed.contains("INSERT IGNORE INTO chapter_plan"));
        assertTrue(!seed.contains("'CHAPTER_CARD'"));

        try (var sqlFiles = Files.list(PROJECT_ROOT.resolve("docs/sql"))) {
            assertEquals(
                    Set.of("schema.sql", "seed.sql"),
                    sqlFiles.filter(path -> path.getFileName().toString().endsWith(".sql"))
                            .map(path -> path.getFileName().toString())
                            .collect(java.util.stream.Collectors.toSet()));
        }
    }

    @Test
    void shouldProvideDaoInterfacesAndXmlMappers() throws Exception {
        Path mapperRoot = PROJECT_ROOT.resolve(
                "novel-agent-app/src/main/resources/mybatis/mapper");

        for (MapperContract contract : MAPPER_CONTRACTS) {
            assertNotNull(assertDoesNotThrow(() -> Class.forName(contract.interfaceName())));
            Path mapperFile = mapperRoot.resolve(contract.xmlFile());
            assertTrue(Files.isRegularFile(mapperFile), contract.xmlFile());

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(
                    "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                    false
            );
            var document = factory.newDocumentBuilder().parse(mapperFile.toFile());
            assertEquals(
                    contract.interfaceName(),
                    document.getDocumentElement().getAttribute("namespace"));

            Set<String> statementIds = new java.util.HashSet<>();
            for (String tag : List.of("insert", "update", "select", "delete")) {
                var nodes = document.getElementsByTagName(tag);
                for (int index = 0; index < nodes.getLength(); index++) {
                    statementIds.add(nodes.item(index).getAttributes().getNamedItem("id").getNodeValue());
                }
            }
            if (contract.interfaceName().equals("cn.ninth.novel.infrastructure.dao.IOutlineNodeDao")) {
                assertTrue(!statementIds.contains("queryChapterCard"));
                assertTrue(!statementIds.contains("queryChapterCards"));
            }
            if (contract.interfaceName().equals("cn.ninth.novel.infrastructure.dao.IStoryChapterDao")) {
                String mapper = Files.readString(mapperFile);
                assertTrue(mapper.contains("chapter_plan_id"));
                assertTrue(!mapper.contains("outline_node_id"));
                assertTrue(mapper.contains("chapterPlanId"));
                assertTrue(!mapper.contains("outlineNodeId"));
            }
            assertTrue(statementIds.containsAll(contract.statementIds()), contract.xmlFile());
        }
    }

    private record MapperContract(
            String interfaceName,
            String xmlFile,
            Set<String> statementIds) {
    }
}
