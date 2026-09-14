package cn.ninth.novel.domain.memory.service;

import cn.ninth.novel.domain.memory.model.MemoryCandidate;
import cn.ninth.novel.domain.memory.model.MemoryCandidateStatus;
import cn.ninth.novel.domain.memory.model.MemoryCandidateType;
import cn.ninth.novel.domain.memory.model.MemoryEvidenceRef;
import cn.ninth.novel.domain.memory.model.MemorySourceVersion;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;

/**
 * 从已经确定的最终正文中提取进入 Gate 的最小 Candidate 集合。
 *
 * <p>这里不读取压缩摘要、章纲或旧正文；每个 Candidate 都直接绑定最终正文的
 * {@link MemorySourceVersion} 和连续 evidence range。抽取保持确定性，语义合并仍
 * 由后续 Reconcile/Gate 负责。</p>
 */
@Component
public final class FinalChapterCandidateExtractor {

    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[。！？!?\\n])");
    private static final int MAX_EVENTS_PER_CHAPTER = 4;
    private static final int MAX_FACTS_PER_CHAPTER = 8;
    private static final int MAX_OPEN_LOOPS_PER_CHAPTER = 3;

    private static final List<String> EVENT_SIGNALS = List.of(
            "进入", "前往", "发现", "找到", "收到", "获得", "决定", "约见", "约定",
            "通知", "发作", "出现", "确认", "测试", "记录", "提出", "答应", "赶到",
            "击杀", "离开", "转运", "清扫", "观摩", "标记", "达成", "取出", "藏",
            "转移", "恢复");
    private static final List<String> FACT_SIGNALS = List.of(
            "残晶", "边角料", "黑色", "同源", "封锁纹路", "压制纹路", "暗损", "旧锁",
            "位于", "持有", "拥有", "塞回", "分藏", "封存", "报废", "无知觉", "灵气",
            "频率", "波动", "陆遥", "周深", "秦少", "媒介", "结界", "货箱", "脉动",
            "异兽", "名单", "通讯", "评级", "潜力", "感知", "阻滞", "收尾位", "接应位",
            "考核", "配额", "共振", "观察留存");
    private static final List<String> OPEN_LOOP_SIGNALS = List.of(
            "尚未", "仍未", "未知", "无法确认", "不确定", "不知道", "未查明", "未确定",
            "待查", "问题是", "需要找到", "还没", "没法");

    /**
     * 从最终正文产生 Event、Fact 和 Open Loop Change 候选。
     *
     * @param projectCode 项目业务编码，仅用于生成稳定 Candidate ID
     * @param chapterNumber 章节号
     * @param finalContent 最终正文
     * @param sourceVersion 与最终正文匹配的版本绑定
     */
    public List<MemoryCandidate> extract(
            String projectCode,
            int chapterNumber,
            String finalContent,
            MemorySourceVersion sourceVersion
    ) {
        String project = required(projectCode, "projectCode");
        if (chapterNumber < 0) {
            throw new IllegalArgumentException("chapterNumber 不能小于 0");
        }
        if (finalContent == null || finalContent.isBlank()) {
            throw new IllegalArgumentException("finalContent 不能为空");
        }
        if (sourceVersion == null) {
            throw new IllegalArgumentException("sourceVersion 不能为空");
        }
        sourceVersion.requireMatches(finalContent);

        List<Segment> segments = splitSegments(finalContent);
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("最终正文没有可绑定的 evidence");
        }
        Segment primary = segments.stream()
                .filter(segment -> !segment.text().startsWith("#"))
                .findFirst()
                .orElse(segments.get(0));

        List<Segment> events = selectRanked(
                segments, primary, MAX_EVENTS_PER_CHAPTER,
                this::eventScore, value -> containsAny(value, EVENT_SIGNALS));
        List<Segment> facts = selectRanked(
                segments, null, MAX_FACTS_PER_CHAPTER,
                this::factScore, this::isFactEvidence);
        List<Segment> openLoops = selectRanked(
                segments, null, MAX_OPEN_LOOPS_PER_CHAPTER,
                this::openLoopScore, this::isOpenLoopEvidence);

        List<MemoryCandidate> candidates = new ArrayList<>();
        Set<String> eventAndFactEvidence = new HashSet<>();
        appendCandidates(
                candidates, eventAndFactEvidence, events, MemoryCandidateType.EVENT,
                project, chapterNumber, finalContent, sourceVersion);
        appendCandidates(
                candidates, eventAndFactEvidence, facts, MemoryCandidateType.FACT,
                project, chapterNumber, finalContent, sourceVersion);
        appendCandidates(
                candidates, new HashSet<>(), openLoops, MemoryCandidateType.OPEN_LOOP_CHANGE,
                project, chapterNumber, finalContent, sourceVersion);
        return List.copyOf(candidates);
    }

    private void appendCandidates(
            List<MemoryCandidate> target,
            Set<String> seenEvidence,
            List<Segment> segments,
            MemoryCandidateType type,
            String projectCode,
            int chapterNumber,
            String finalContent,
            MemorySourceVersion sourceVersion
    ) {
        int index = 0;
        for (Segment segment : segments) {
            if (!seenEvidence.add(key(segment))) {
                continue;
            }
            String identity = projectCode + ":" + chapterNumber + ":"
                    + sourceVersion.chapterVersion() + ":" + sourceVersion.contentHash()
                    + ":" + type.name() + ":" + index + ":"
                    + segment.start() + ":" + segment.end();
            String candidateId = "runtime:candidate:" + MemorySourceVersion.hash(identity);
            target.add(new MemoryCandidate(
                    candidateId,
                    sourceVersion,
                    new MemoryEvidenceRef(segment.start(), segment.end(),
                            finalContent.substring(segment.start(), segment.end())),
                    type,
                    MemoryCandidateStatus.READY_FOR_GATE));
            index++;
        }
    }

    private List<Segment> selectRanked(
            List<Segment> segments,
            Segment first,
            int limit,
            ToIntFunction<Segment> score,
            Predicate<String> eligible
    ) {
        Set<String> seen = new LinkedHashSet<>();
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
        return signalScore(segment.text(), EVENT_SIGNALS, 8)
                + signalScore(segment.text(), List.of(
                "发现", "确认", "验证", "获得", "出现", "发作", "转运",
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

    private boolean containsAny(String value, List<String> signals) {
        return signals.stream().anyMatch(value::contains);
    }

    private String key(Segment segment) {
        return segment.start() + ":" + segment.end();
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }

    private record Segment(int start, int end, String text) {
    }
}
