package cn.ninth.novel.infrastructure.memory.backfill;

import cn.ninth.novel.domain.memory.model.CanonicalProjection;
import cn.ninth.novel.domain.memory.model.MemoryCandidate;
import cn.ninth.novel.domain.memory.model.MemoryCandidateStatus;
import cn.ninth.novel.domain.memory.model.MemoryCandidateType;
import cn.ninth.novel.domain.memory.model.MemoryCommitRequest;
import cn.ninth.novel.domain.memory.model.MemoryEvent;
import cn.ninth.novel.domain.memory.model.MemoryFact;
import cn.ninth.novel.domain.memory.model.MemoryFactStatus;
import cn.ninth.novel.domain.memory.model.MemoryEvidenceRef;
import cn.ninth.novel.domain.memory.model.MemoryOperationDecision;
import cn.ninth.novel.domain.memory.model.MemoryProjectionStatus;
import cn.ninth.novel.domain.memory.model.MemorySourceVersion;
import cn.ninth.novel.domain.memory.service.MemoryCommitGate;
import cn.ninth.novel.domain.memory.service.MemoryReconciler;
import cn.ninth.novel.domain.memoryservice.MemoryCommitRejectedException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 只读正文抽取、Evidence 绑定、Reconcile 和 Canonical Gate 的一次性 backfill 服务。
 *
 * <p>该服务只接受 FINALIZED 的 {@code story_chapter.content} 作为事实证据。ChapterMemory、
 * Snapshot、Story Bible 和人物设定不参与 Canonical candidate 的正文升级；它们仍由正常
 * 上下文链路独立读取。Projection 也只引用本次通过 Gate 的 Event/Fact candidate。</p>
 */
@Service
public class CanonicalMemoryBackfillService {

    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[。！？!?\\n])");
    private static final int MAX_EVENTS_PER_CHAPTER = 4;
    private static final int MAX_FACTS_PER_CHAPTER = 8;
    private static final int MAX_OPEN_LOOPS_PER_CHAPTER = 3;

    private static final List<String> EVENT_SIGNALS = List.of(
            "进入", "前往", "发现", "找到", "收到", "获得", "决定", "约见", "约定",
            "通知", "发作", "出现", "确认", "测试", "记录", "提出", "答应", "赶到",
            "击杀", "离开", "转运", "清扫", "观摩", "标记", "达成", "取出", "藏"
    );
    private static final List<String> FACT_SIGNALS = List.of(
            "残晶", "边角料", "黑色", "同源", "封锁纹路", "压制纹路", "暗损", "旧锁",
            "位于", "持有", "拥有", "塞回", "分藏", "封存", "报废", "无知觉", "灵气",
            "频率", "波动", "陆遥", "周深", "秦少", "媒介", "结界", "货箱", "脉动",
            "异兽", "名单", "通讯", "评级", "潜力", "感知", "阻滞", "收尾位", "接应位",
            "考核", "配额", "共振", "观察留存"
    );
    private static final List<String> OPEN_LOOP_SIGNALS = List.of(
            "尚未", "仍未", "未知", "无法确认", "不确定", "不知道", "未查明", "未确定",
            "待查", "问题是", "需要找到", "还没", "没法"
    );

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final MemoryCommitGate memoryCommitGate;
    private final MemoryReconciler reconciler = new MemoryReconciler();

    public CanonicalMemoryBackfillService(
            DataSource dataSource,
            ObjectMapper objectMapper,
            MemoryCommitGate memoryCommitGate
    ) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.objectMapper = objectMapper;
        this.memoryCommitGate = memoryCommitGate;
    }

    /**
     * 对一个连续章节范围执行 backfill。调用方必须显式给出项目和章节边界。
     */
    @Transactional(rollbackFor = Exception.class)
    public CanonicalMemoryBackfillReport backfill(
            String projectCode,
            int fromChapter,
            int toChapter
    ) {
        requireRange(projectCode, fromChapter, toChapter);
        List<ChapterRow> chapters = loadFinalizedChapters(projectCode, fromChapter, toChapter);
        if (chapters.size() != toChapter - fromChapter + 1
                || chapters.stream().anyMatch(chapter -> !"FINALIZED".equalsIgnoreCase(chapter.status()))) {
            throw new IllegalStateException(
                    "Canonical backfill 要求目标范围内每一章都存在且为 FINALIZED: "
                            + projectCode + " " + fromChapter + "~" + toChapter);
        }

        List<CanonicalMemoryBackfillReport.ChapterReport> chapterReports = new ArrayList<>();
        int rejectedCandidates = 0;
        int invalidCandidates = 0;
        for (ChapterRow chapter : chapters) {
            MemorySourceVersion sourceVersion = sourceVersion(projectCode, chapter);
            String commitKey = commitKey(projectCode, chapter.chapterNumber(), sourceVersion);
            if (isAlreadyCommitted(commitKey)) {
                chapterReports.add(existingChapterReport(projectCode, chapter, sourceVersion));
                continue;
            }

            Extraction extraction = extract(projectCode, chapter, sourceVersion);
            List<MemoryEvent> relatedEvents = loadEvents(projectCode, chapter.chapterNumber());
            List<MemoryFact> relatedFacts = loadFacts(projectCode);
            List<MemoryOperationDecision> decisions = extraction.candidates().stream()
                    .map(candidate -> reconciler.reconcile(candidate, relatedEvents, relatedFacts))
                    .toList();
            List<CanonicalProjection> projections = createProjections(
                    projectCode, chapter.chapterNumber(), extraction, decisions);

            MemoryCommitRequest request = new MemoryCommitRequest(
                    projectCode,
                    chapter.chapterNumber(),
                    chapter.content(),
                    sourceVersion,
                    extraction.candidates(),
                    decisions,
                    projections,
                    List.of(),
                    true,
                    commitKey);
            try {
                memoryCommitGate.commit(request);
            } catch (MemoryCommitRejectedException exception) {
                int rejected = exception.conflicts().size();
                rejectedCandidates += rejected;
                chapterReports.add(new CanonicalMemoryBackfillReport.ChapterReport(
                        chapter.chapterNumber(), sourceVersion.chapterVersion(),
                        sourceVersion.contentHash(), false, extraction.candidates().size(),
                        0, 0, 0, 0, rejected, extraction.invalidCandidateCount()));
                throw new IllegalStateException(
                        "Canonical backfill 被 Gate 拒绝，章节=" + chapter.chapterNumber()
                                + ", conflicts=" + exception.conflicts().stream()
                                .map(conflict -> conflict.code()).toList(), exception);
            }

            rejectedCandidates += extraction.rejectedCandidateCount();
            invalidCandidates += extraction.invalidCandidateCount();
            chapterReports.add(new CanonicalMemoryBackfillReport.ChapterReport(
                    chapter.chapterNumber(), sourceVersion.chapterVersion(),
                    sourceVersion.contentHash(), true, extraction.candidates().size(),
                    countMaterializedEvents(decisions),
                    countMaterializedFacts(decisions), extraction.openLoopSegments().size(),
                    projections.size(), extraction.rejectedCandidateCount(),
                    extraction.invalidCandidateCount()));
        }

        return report(projectCode, fromChapter, toChapter, chapterReports,
                rejectedCandidates, invalidCandidates);
    }

    private int countMaterializedEvents(List<MemoryOperationDecision> decisions) {
        return (int) decisions.stream()
                .filter(decision -> decision.operation().name().equals("ADD"))
                .filter(decision -> decision.candidate().candidateType() == MemoryCandidateType.EVENT)
                .count();
    }

    private int countMaterializedFacts(List<MemoryOperationDecision> decisions) {
        return (int) decisions.stream()
                .filter(decision -> decision.operation().name().equals("ADD")
                        || decision.operation().name().equals("REINFORCE")
                        || decision.operation().name().equals("SUPERSEDE"))
                .filter(decision -> decision.candidate().candidateType() == MemoryCandidateType.FACT)
                .count();
    }

    private CanonicalMemoryBackfillReport report(
            String projectCode,
            int fromChapter,
            int toChapter,
            List<CanonicalMemoryBackfillReport.ChapterReport> chapterReports,
            int rejectedCandidates,
            int invalidCandidates
    ) {
        String range = " AND chapter_number BETWEEN ? AND ?";
        int accepted = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM memory_accepted_chapter_version WHERE project_code = ?" + range,
                Integer.class, projectCode, fromChapter, toChapter);
        int events = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT o.aggregate_id) FROM memory_outbox o "
                        + "JOIN memory_commit c ON c.commit_key = o.commit_key "
                        + "WHERE o.aggregate_type = 'EVENT' AND c.project_code = ?" + range,
                Integer.class, projectCode, fromChapter, toChapter);
        int facts = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT o.aggregate_id) FROM memory_outbox o "
                        + "JOIN memory_commit c ON c.commit_key = o.commit_key "
                        + "WHERE o.aggregate_type = 'FACT' AND c.project_code = ?" + range,
                Integer.class, projectCode, fromChapter, toChapter);
        int projections = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT o.aggregate_id) FROM memory_outbox o "
                        + "JOIN memory_commit c ON c.commit_key = o.commit_key "
                        + "WHERE o.aggregate_type = 'PROJECTION' AND c.project_code = ?" + range,
                Integer.class, projectCode, fromChapter, toChapter);
        List<Integer> missing = chapterReports.stream()
                .filter(chapter -> !chapter.accepted()
                        || (chapter.eventCount() == 0
                        && chapter.factCount() == 0
                        && chapter.projectionCount() == 0))
                .map(CanonicalMemoryBackfillReport.ChapterReport::chapterNumber)
                .toList();
        int openLoops = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT o.aggregate_id) FROM memory_outbox o "
                        + "JOIN memory_commit c ON c.commit_key = o.commit_key "
                        + "WHERE o.aggregate_type = 'PROJECTION' "
                        + "AND o.aggregate_id LIKE CONCAT('backfill:open-loop:', ?, ':%') "
                        + "AND c.project_code = ?" + range,
                Integer.class, projectCode, projectCode, fromChapter, toChapter);
        return new CanonicalMemoryBackfillReport(
                projectCode, fromChapter, toChapter, toChapter - fromChapter + 1,
                accepted, events, facts, openLoops, projections,
                rejectedCandidates, invalidCandidates, missing, chapterReports);
    }

    private CanonicalMemoryBackfillReport.ChapterReport existingChapterReport(
            String projectCode,
            ChapterRow chapter,
            MemorySourceVersion sourceVersion
    ) {
        Integer events = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM memory_canonical_event WHERE project_code = ? AND chapter_number = ? "
                        + "AND chapter_version = ? AND content_hash = ?",
                Integer.class, projectCode, chapter.chapterNumber(),
                sourceVersion.chapterVersion(), sourceVersion.contentHash());
        Integer facts = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM memory_canonical_fact f WHERE f.project_code = ? "
                        + "AND EXISTS (SELECT 1 FROM memory_outbox o JOIN memory_commit c "
                        + "ON c.commit_key = o.commit_key WHERE o.aggregate_type = 'FACT' "
                        + "AND o.aggregate_id = f.fact_id AND c.project_code = ? "
                        + "AND c.chapter_number = ?)",
                Integer.class, projectCode, projectCode, chapter.chapterNumber());
        Integer projections = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM memory_canonical_projection p WHERE p.project_code = ? "
                        + "AND EXISTS (SELECT 1 FROM memory_outbox o JOIN memory_commit c "
                        + "ON c.commit_key = o.commit_key WHERE o.aggregate_type = 'PROJECTION' "
                        + "AND o.aggregate_id = p.projection_id AND c.project_code = ? "
                        + "AND c.chapter_number = ?)",
                Integer.class, projectCode, projectCode, chapter.chapterNumber());
        Integer openLoops = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM memory_canonical_projection p WHERE p.project_code = ? "
                        + "AND p.projection_id LIKE ? "
                        + "AND EXISTS (SELECT 1 FROM memory_outbox o JOIN memory_commit c "
                        + "ON c.commit_key = o.commit_key WHERE o.aggregate_type = 'PROJECTION' "
                        + "AND o.aggregate_id = p.projection_id AND c.project_code = ? "
                        + "AND c.chapter_number = ?)",
                Integer.class, projectCode, "backfill:open-loop:" + projectCode + ":%",
                projectCode, chapter.chapterNumber());
        return new CanonicalMemoryBackfillReport.ChapterReport(
                chapter.chapterNumber(), sourceVersion.chapterVersion(), sourceVersion.contentHash(),
                true, 0, events, facts, openLoops, projections, 0, 0);
    }

    private List<CanonicalProjection> createProjections(
            String projectCode,
            int chapterNumber,
            Extraction extraction,
            List<MemoryOperationDecision> decisions
    ) {
        List<MemoryCandidate> events = extraction.candidates().stream()
                .filter(candidate -> candidate.candidateType() == MemoryCandidateType.EVENT)
                .toList();
        List<MemoryCandidate> facts = extraction.candidates().stream()
                .filter(candidate -> candidate.candidateType() == MemoryCandidateType.FACT)
                .toList();
        List<CanonicalProjection> projections = new ArrayList<>();
        if (!events.isEmpty() && !facts.isEmpty()) {
            MemoryCandidate eventCandidate = events.stream()
                    .max(Comparator.comparingInt(candidate -> eventScore(
                            candidate.evidenceRange().excerpt())))
                    .orElse(events.get(0));
            MemoryEvidenceRef eventEvidence = eventCandidate.evidenceRange();
            MemoryEvidenceRef factEvidence = facts.get(0).evidenceRange();
            projections.add(new CanonicalProjection(
                    "backfill:projection:" + projectCode + ":" + chapterNumber,
                    projectCode,
                    "章节已接受证据：" + eventEvidence.excerpt() + "；" + factEvidence.excerpt(),
                    List.of(eventCandidate.candidateId(), facts.get(0).candidateId(),
                            evidenceBinding(eventCandidate), evidenceBinding(facts.get(0))),
                    MemoryProjectionStatus.PROJECTION_ACTIVE));
        }
        for (int index = 0; index < extraction.openLoopSegments().size(); index++) {
            Segment loop = extraction.openLoopSegments().get(index);
            MemoryCandidate event = events.stream()
                    .max(Comparator.comparingInt(candidate -> eventScore(
                            candidate.evidenceRange().excerpt())))
                    .orElse(events.get(0));
            MemoryCandidate second = facts.isEmpty()
                    ? (events.size() > 1 ? events.get(1) : event)
                    : facts.get(0);
            if (event.candidateId().equals(second.candidateId())) {
                continue;
            }
            projections.add(new CanonicalProjection(
                    "backfill:open-loop:" + projectCode + ":" + chapterNumber + ":" + index,
                    projectCode,
                    "开放线索（正文尚未解决）：" + loop.text(),
                    List.of(event.candidateId(), second.candidateId(),
                            evidenceBinding(event), evidenceBinding(second),
                            evidenceBinding(event.sourceVersion(), loop)),
                    MemoryProjectionStatus.PROJECTION_ACTIVE));
        }
        return List.copyOf(projections);
    }

    private Extraction extract(
            String projectCode,
            ChapterRow chapter,
            MemorySourceVersion sourceVersion
    ) {
        List<Segment> segments = splitSegments(chapter.content());
        if (segments.isEmpty()) {
            throw new IllegalStateException("FINALIZED 正文没有可绑定的 evidence, chapter="
                    + chapter.chapterNumber());
        }
        Segment primary = segments.stream()
                .filter(segment -> !segment.text().startsWith("#"))
                .findFirst()
                .orElse(segments.get(0));
        List<Segment> eventSegments = selectEvents(segments, primary);
        List<Segment> factSegments = selectFacts(segments);
        List<Segment> openLoopSegments = selectOpenLoops(segments);
        if (eventSegments.isEmpty()) {
            eventSegments = List.of(primary);
        }
        List<MemoryCandidate> candidates = new ArrayList<>();
        Set<String> candidateEvidence = new HashSet<>();
        for (Segment segment : eventSegments) {
            if (candidateEvidence.add(key(segment))) {
                candidates.add(candidate(projectCode, chapter, sourceVersion, segment,
                        MemoryCandidateType.EVENT, candidates.size()));
            }
        }
        for (Segment segment : factSegments) {
            if (candidateEvidence.add(key(segment))) {
                candidates.add(candidate(projectCode, chapter, sourceVersion, segment,
                        MemoryCandidateType.FACT, candidates.size()));
            }
        }
        return new Extraction(
                List.copyOf(candidates),
                openLoopSegments,
                0,
                0);
    }

    private MemoryCandidate candidate(
            String projectCode,
            ChapterRow chapter,
            MemorySourceVersion sourceVersion,
            Segment segment,
            MemoryCandidateType type,
            int index
    ) {
        String candidateId = String.format(
                Locale.ROOT, "backfill:candidate:%s:%d:%s:%02d",
                projectCode, chapter.chapterNumber(), type.name().toLowerCase(Locale.ROOT), index);
        return new MemoryCandidate(
                candidateId,
                sourceVersion,
                new MemoryEvidenceRef(segment.start(), segment.end(), segment.text()),
                type,
                MemoryCandidateStatus.READY_FOR_GATE);
    }

    private List<Segment> selectEvents(List<Segment> segments, Segment primary) {
        return selectRanked(segments, primary, MAX_EVENTS_PER_CHAPTER,
                this::eventScore, value -> containsAny(value, EVENT_SIGNALS));
    }

    private List<Segment> selectFacts(List<Segment> segments) {
        return selectRanked(segments, null, MAX_FACTS_PER_CHAPTER,
                this::factScore, this::isFactEvidence);
    }

    private List<Segment> selectOpenLoops(List<Segment> segments) {
        return selectRanked(segments, null, MAX_OPEN_LOOPS_PER_CHAPTER,
                this::openLoopScore, this::isOpenLoopEvidence);
    }

    private List<Segment> selectRanked(
            List<Segment> segments,
            Segment first,
            int limit,
            java.util.function.ToIntFunction<Segment> score,
            java.util.function.Predicate<String> eligible
    ) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<Segment> selected = new ArrayList<>();
        List<Segment> ranked = segments.stream()
                .filter(segment -> first == null || !key(segment).equals(key(first)))
                .filter(segment -> eligible.test(segment.text()))
                .sorted(Comparator.comparingInt(score).reversed()
                        .thenComparingInt(Segment::start))
                .toList();
        if (first != null && (eligible.test(first.text()) || ranked.isEmpty())) {
            seen.add(key(first));
            selected.add(first);
        }
        for (Segment segment : ranked) {
            if (selected.size() >= limit) {
                break;
            }
            if (seen.add(key(segment))) {
                selected.add(segment);
            }
        }
        return List.copyOf(selected);
    }

    private int eventScore(Segment segment) {
        return eventScore(segment.text());
    }

    private int eventScore(String value) {
        return signalScore(value, EVENT_SIGNALS, 8)
                + signalScore(value, List.of(
                        "发现", "确认", "验证", "获得", "出现", "发作", "转运", "清扫",
                        "测试", "决定", "通知", "提出", "答应", "记录", "击杀", "达成"), 12);
    }

    private int factScore(Segment segment) {
        String value = segment.text();
        int score = signalScore(value, FACT_SIGNALS, 10)
                + signalScore(value, List.of(
                        "确认", "确定", "验证", "存在", "同一种", "一致", "几乎一致",
                        "需要", "必须", "只有", "明确", "收进", "藏于", "留在"), 14);
        if (containsAny(value, OPEN_LOOP_SIGNALS)) {
            score -= 45;
        }
        return score;
    }

    private int openLoopScore(Segment segment) {
        String value = segment.text();
        return signalScore(value, OPEN_LOOP_SIGNALS, 10)
                + signalScore(value, List.of(
                        "来源", "成因", "身份", "媒介", "同源", "同频", "频谱", "结界",
                        "裂缝", "线头", "陆遥", "改名单", "追查", "窗口", "位置", "谁",
                        "为什么", "是否"), 16);
    }

    private int signalScore(String value, List<String> signals, int weight) {
        int count = 0;
        for (String signal : signals) {
            if (value.contains(signal)) {
                count++;
            }
        }
        return count * weight;
    }

    private boolean isFactEvidence(String value) {
        if (!containsAny(value, FACT_SIGNALS)) {
            return false;
        }
        return !containsAny(value, OPEN_LOOP_SIGNALS)
                || containsAny(value, List.of("确认", "确定", "验证", "存在", "明确"));
    }

    private boolean isOpenLoopEvidence(String value) {
        if (!containsAny(value, OPEN_LOOP_SIGNALS)
                || value.contains("不知道的是")) {
            return false;
        }
        if (!value.contains("还没")) {
            return true;
        }
        return containsAny(value, List.of(
                "锁定", "确认", "查明", "实证", "解决", "找到", "追查", "来源",
                "位置", "身份", "成因", "媒介", "同源", "同频", "频率", "谁",
                "为什么", "是否", "待查", "线头"));
    }

    private List<Segment> splitSegments(String content) {
        List<Segment> segments = new ArrayList<>();
        int searchFrom = 0;
        for (String raw : SENTENCE_SPLIT.split(content)) {
            String text = raw.trim();
            if (text.isBlank()) {
                searchFrom += raw.length();
                continue;
            }
            int rawStart = content.indexOf(text, searchFrom);
            if (rawStart < 0) {
                searchFrom += raw.length();
                continue;
            }
            int rawEnd = rawStart + text.length();
            segments.add(new Segment(rawStart, rawEnd, text));
            searchFrom = rawEnd;
        }
        return List.copyOf(segments);
    }

    private List<MemoryEvent> loadEvents(String projectCode, int beforeChapter) {
        return jdbcTemplate.query(
                "SELECT event_id, description, chapter_version, "
                        + "CONCAT(evidence_start_offset, ':', evidence_end_offset), story_time "
                        + "FROM memory_canonical_event WHERE project_code = ? AND chapter_number < ?",
                (rs, row) -> new MemoryEvent(
                        rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5)),
                projectCode, beforeChapter);
    }

    private List<MemoryFact> loadFacts(String projectCode) {
        return jdbcTemplate.query(
                "SELECT fact_id, proposition, source_ids_json, status "
                        + "FROM memory_canonical_fact WHERE project_code = ?",
                (rs, row) -> new MemoryFact(
                        rs.getString(1), rs.getString(2), parseSourceIds(rs.getString(3)),
                        MemoryFactStatus.valueOf(rs.getString(4))),
                projectCode);
    }

    private List<String> parseSourceIds(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            List<String> ids = new ArrayList<>();
            if (root != null && root.isArray()) {
                root.forEach(node -> {
                    if (node != null && node.isTextual() && !node.asText().isBlank()) {
                        ids.add(node.asText());
                    }
                });
            }
            return ids.isEmpty() ? List.of("backfill:unknown-source") : List.copyOf(ids);
        } catch (RuntimeException exception) {
            return List.of("backfill:unreadable-source");
        }
    }

    private List<ChapterRow> loadFinalizedChapters(
            String projectCode,
            int fromChapter,
            int toChapter
    ) {
        return jdbcTemplate.query(
                "SELECT c.id, c.chapter_number, c.status, c.content "
                        + "FROM story_chapter c JOIN novel_project p ON p.id = c.project_id "
                        + "WHERE p.project_code = ? AND c.chapter_number BETWEEN ? AND ? "
                        + "ORDER BY c.chapter_number",
                (rs, row) -> new ChapterRow(
                        rs.getLong(1), rs.getInt(2), rs.getString(3), rs.getString(4)),
                projectCode, fromChapter, toChapter);
    }

    private MemorySourceVersion sourceVersion(String projectCode, ChapterRow chapter) {
        return MemorySourceVersion.create(
                "finalized:story-chapter:" + projectCode + ":" + chapter.chapterNumber()
                        + ":" + chapter.id(),
                chapter.content());
    }

    private String commitKey(
            String projectCode,
            int chapterNumber,
            MemorySourceVersion sourceVersion
    ) {
        return "backfill:" + projectCode + ":chapter:" + chapterNumber + ":"
                + sourceVersion.contentHash();
    }

    private boolean isAlreadyCommitted(String commitKey) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM memory_commit WHERE commit_key = ?", Integer.class, commitKey);
        return count != null && count > 0;
    }

    private String evidenceBinding(MemoryCandidate candidate) {
        return candidate.sourceVersion().chapterVersion()
                + "#" + candidate.sourceVersion().contentHash()
                + "#" + candidate.evidenceRange().startOffset()
                + ":" + candidate.evidenceRange().endOffset();
    }

    private String evidenceBinding(MemorySourceVersion sourceVersion, Segment segment) {
        return sourceVersion.chapterVersion()
                + "#" + sourceVersion.contentHash()
                + "#" + segment.start() + ":" + segment.end();
    }

    private String key(Segment segment) {
        return segment.start() + ":" + segment.end();
    }

    private boolean containsAny(String value, List<String> signals) {
        return signals.stream().anyMatch(value::contains);
    }

    private void requireRange(String projectCode, int fromChapter, int toChapter) {
        if (projectCode == null || projectCode.isBlank()) {
            throw new IllegalArgumentException("projectCode 不能为空");
        }
        if (fromChapter < 1 || toChapter < fromChapter) {
            throw new IllegalArgumentException("backfill 章节范围无效");
        }
    }

    private record ChapterRow(long id, int chapterNumber, String status, String content) {
    }

    private record Segment(int start, int end, String text) {
    }

    private record Extraction(
            List<MemoryCandidate> candidates,
            List<Segment> openLoopSegments,
            int rejectedCandidateCount,
            int invalidCandidateCount
    ) {
    }
}
