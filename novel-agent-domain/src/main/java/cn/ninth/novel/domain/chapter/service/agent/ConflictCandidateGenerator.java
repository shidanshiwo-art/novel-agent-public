package cn.ninth.novel.domain.chapter.service.agent;

import cn.ninth.novel.domain.chapter.model.valobj.ConflictCandidate;
import cn.ninth.novel.domain.chapter.model.valobj.QualityContext;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewContext;
import cn.ninth.novel.domain.memory.model.MemoryContextCategory;
import cn.ninth.novel.domain.memory.model.MemoryContextItem;
import cn.ninth.novel.domain.memory.model.MemoryContextPack;
import cn.ninth.novel.domain.memory.model.MemoryProfile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phase A 连续性候选生成器。
 *
 * <p>它只把固定 ReviewContext 中的历史状态与当前正文的同实体证据配对，
 * 不判断是否真的冲突。冲突结论由 Phase B {@link ContinuitySemanticVerifier} 负责。</p>
 */
@Component
public class ConflictCandidateGenerator {

    private static final Pattern HAN_RUN = Pattern.compile("[\\p{IsHan}]{2,}");
    private static final Pattern STORY_TIME = Pattern.compile(
            "第\\s*\\d+\\s*(?:日|天)|次日|翌日|当日|昨夜|今夜|清晨|午后|夜间");
    private static final Set<String> COMMON_TERMS = Set.of(
            "然后", "于是", "只是", "已经", "正在", "开始", "发现", "看见", "知道", "说道",
            "问道", "回答", "走到", "来到", "那里", "这里", "什么", "一个", "一种", "这个",
            "那个", "他们", "她们", "自己", "可以", "能够", "因为", "所以", "如果", "但是",
            "不过", "没有", "不是", "仍然", "似乎", "东西", "事情", "时候", "之后", "之前",
            "身上", "手中", "面前", "旁边", "里面", "外面", "声音", "目光", "身体", "位于",
            "位置", "地点", "当前", "历史", "状态", "相关", "证据");
    private static final Set<String> KNOWLEDGE_TERMS = Set.of(
            "知道", "得知", "确认", "认出", "意识到", "推断", "推测", "依据", "根据", "来源",
            "古籍", "观察", "听说");
    private static final Set<String> ITEM_STATE_TERMS = Set.of(
            "位于", "位置", "持有", "携带", "保管", "转移", "交给", "带到", "放在", "损坏",
            "破碎", "丢失", "恢复", "伤势", "伤口");
    private static final Set<String> ABILITY_TERMS = Set.of(
            "能力", "规则", "激活", "消耗", "代价", "条件", "触发", "灵息", "法则", "境界");
    private static final Set<String> ENTITY_MARKERS = Set.of(
            "在", "从", "向", "到", "于", "和", "与", "将", "把", "的", "了", "位于",
            "持有", "携带", "保管", "放在", "交给", "前往", "进入", "返回", "回到", "出现",
            "发生", "发现", "看见", "来到", "打开", "使用", "根据", "推断", "不能", "不得",
            "已经", "曾经");
    private static final Set<String> LOCATION_VALUES = Set.of(
            "行政楼", "宿舍", "训练场", "地下室", "旧钟楼", "北港", "北侧", "储物间", "门后", "床上");

    /**
     * 从 ReviewContext 的连续性投影生成候选，不触碰原始 Context Aggregate 或 Memory Store。
     */
    public List<ConflictCandidate> generate(ReviewContext reviewContext) {
        if (reviewContext == null
                || !(reviewContext.getContinuityContext() instanceof MemoryContextPack pack)
                || pack.profile() != MemoryProfile.REVIEW) {
            return List.of();
        }

        String draft = normalize(reviewContext.getCurrentDraft());
        if (draft.isBlank()) {
            return List.of();
        }
        Set<String> characterNames = characterNames(reviewContext.getQualityContext());
        Set<String> draftTerms = extractTerms(draft);
        List<ConflictCandidate> candidates = new ArrayList<>();
        for (MemoryContextItem historical : pack.items()) {
            if (historical == null) {
                continue;
            }
            List<String> entities = entitiesFor(
                    historical.content(), draft, draftTerms, characterNames);
            for (String entity : entities) {
                String currentEvidence = evidenceFor(draft, entity);
                if (currentEvidence.isBlank()) {
                    continue;
                }
                String type = classify(historical, entity, characterNames);
                List<String> relevantEvents = relevantEvents(pack, entity, draft);
                candidates.add(new ConflictCandidate(
                        type,
                        entity,
                        currentEvidence,
                        historical.content(),
                        relevantEvents,
                        storyTime(draft),
                        historical.sourceChapter(),
                        !"LEGACY".equalsIgnoreCase(historical.canonicalOrLegacy()),
                        "历史状态与当前正文同实体证据需要语义复核"
                ));
            }
        }
        return distinct(candidates);
    }

    private List<ConflictCandidate> distinct(List<ConflictCandidate> candidates) {
        // 同一实体和同一连续性类型只保留一条最有代表性的历史状态；
        // 否则一条正文会因为多个历史切片被重复送入 Phase B，形成无界模型调用。
        java.util.LinkedHashMap<String, ConflictCandidate> bestByEntity =
                new java.util.LinkedHashMap<>();
        for (ConflictCandidate candidate : candidates) {
            String key = candidate.type() + "|" + candidate.entity();
            ConflictCandidate current = bestByEntity.get(key);
            if (current == null || isMoreRelevant(candidate, current)) {
                bestByEntity.put(key, candidate);
            }
        }
        return List.copyOf(bestByEntity.values());
    }

    private boolean isMoreRelevant(ConflictCandidate candidate, ConflictCandidate current) {
        int candidateChapter = candidate.sourceChapter() == null
                ? -1 : candidate.sourceChapter();
        int currentChapter = current.sourceChapter() == null
                ? -1 : current.sourceChapter();
        if (candidateChapter != currentChapter) {
            return candidateChapter > currentChapter;
        }
        if (candidate.canonicalHistoricalState() != current.canonicalHistoricalState()) {
            return candidate.canonicalHistoricalState();
        }
        return candidate.historicalEvidence().length() > current.historicalEvidence().length();
    }

    private String classify(
            MemoryContextItem historical,
            String entity,
            Set<String> characterNames
    ) {
        String content = normalize(historical.content()).toLowerCase(Locale.ROOT);
        if (historical.category() == MemoryContextCategory.RULES
                || containsAny(content, ABILITY_TERMS)) {
            return "ABILITY_RULE";
        }
        if (containsAny(content, KNOWLEDGE_TERMS)
                || historical.itemId().toLowerCase(Locale.ROOT).contains("knowledge")) {
            return "KNOWLEDGE";
        }
        if (characterNames.contains(entity)) {
            return "CHARACTER_STATE";
        }
        if (containsAny(content, ITEM_STATE_TERMS)) {
            return "ITEM_STATE";
        }
        if (historical.category() == MemoryContextCategory.EPISODES
                || STORY_TIME.matcher(content).find()) {
            return "TIMELINE";
        }
        return "ITEM_STATE";
    }

    private List<String> entitiesFor(
            String historical,
            String draft,
            Set<String> draftTerms,
            Set<String> characterNames
    ) {
        List<String> knownCharacters = characterNames.stream()
                .filter(name -> historical.contains(name) && draft.contains(name))
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        Set<String> historicalTerms = extractTerms(historical);
        historicalTerms.retainAll(draftTerms);
        Set<String> entityTerms = historicalTerms.stream()
                .filter(term -> term.length() >= 2)
                .filter(term -> ENTITY_MARKERS.stream().noneMatch(term::contains))
                .filter(term -> !LOCATION_VALUES.contains(term))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<String> extractedEntities = entityTerms.stream()
                .filter(term -> !isContainedByLongerTerm(term, entityTerms))
                .sorted(Comparator.comparingInt(String::length).reversed())
                // 一个历史条目只产生一个代表性非角色实体；同一条目中的所有
                // 共享二字词并不是独立的连续性候选，不能逐个进入 Phase B。
                .limit(1)
                .toList();
        LinkedHashSet<String> entities = new LinkedHashSet<>(knownCharacters);
        entities.addAll(extractedEntities);
        return List.copyOf(entities);
    }

    private boolean isContainedByLongerTerm(String term, Set<String> terms) {
        return terms.stream().anyMatch(other -> other.length() > term.length()
                && other.contains(term));
    }

    private String evidenceFor(String draft, String entity) {
        for (String sentence : draft.split("(?<=[。！？!?\\n])")) {
            if (sentence.contains(entity)) {
                return sentence.trim();
            }
        }
        int index = draft.indexOf(entity);
        return index < 0 ? "" : draft.substring(index, Math.min(draft.length(), index + 80));
    }

    private List<String> relevantEvents(
            MemoryContextPack pack,
            String entity,
            String draft
    ) {
        List<String> events = pack.episodes().stream()
                .filter(Objects::nonNull)
                .filter(item -> item.content().contains(entity)
                        || (storyTime(draft) != null
                        && item.content().contains(storyTime(draft))))
                .map(MemoryContextItem::content)
                .distinct()
                .toList();
        return events;
    }

    private Set<String> characterNames(Object qualityContext) {
        if (!(qualityContext instanceof QualityContext quality)
                || quality.getRelevantCharacters() == null) {
            return Set.of();
        }
        return quality.getRelevantCharacters().stream()
                .filter(Objects::nonNull)
                .map(character -> character.getName())
                .filter(name -> name != null && !name.isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private Set<String> extractTerms(String text) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        Matcher matcher = HAN_RUN.matcher(normalize(text));
        while (matcher.find()) {
            String run = matcher.group();
            int maxLength = Math.min(8, run.length());
            for (int length = maxLength; length >= 2; length--) {
                for (int start = 0; start + length <= run.length(); start++) {
                    String term = run.substring(start, start + length);
                    if (!COMMON_TERMS.contains(term)) {
                        terms.add(term);
                    }
                }
            }
        }
        return terms;
    }

    private String storyTime(String text) {
        Matcher matcher = STORY_TIME.matcher(text == null ? "" : text);
        return matcher.find() ? matcher.group() : null;
    }

    private boolean containsAny(String text, Set<String> terms) {
        return terms.stream().anyMatch(text::contains);
    }

    private String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "").trim();
    }
}
