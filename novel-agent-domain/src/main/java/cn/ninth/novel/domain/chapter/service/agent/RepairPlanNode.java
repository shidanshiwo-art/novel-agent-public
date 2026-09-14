package cn.ninth.novel.domain.chapter.service.agent;

import cn.ninth.novel.domain.chapter.model.valobj.ContinuityFinding;
import cn.ninth.novel.domain.chapter.model.valobj.QualityFinding;
import cn.ninth.novel.domain.chapter.model.valobj.RepairPlan;
import cn.ninth.novel.domain.chapter.model.valobj.RepairPlanItem;
import cn.ninth.novel.domain.chapter.model.valobj.enums.RepairPriority;
import cn.ninth.novel.domain.chapter.model.valobj.enums.RepairSource;
import cn.ninth.novel.domain.chapter.model.valobj.enums.ContinuitySeverity;
import cn.ninth.novel.domain.chapter.model.valobj.enums.QualitySeverity;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphState;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 将两类审核发现确定性地汇总为返修计划。 */
@Component
public class RepairPlanNode implements NodeAction<ChapterGraphState> {

    public static final String NODE = "REPAIR_PLAN";
    private static final Pattern HAN_RUN = Pattern.compile("[\\p{IsHan}]{2,}");
    private static final Set<String> GENERIC_TERMS = Set.of(
            "当前", "正文", "问题", "需要", "缺少", "存在", "没有", "不能", "无法",
            "明显", "相关", "内容", "信息", "场景", "状态", "说明", "解释", "导致",
            "进行", "部分", "这一", "本章", "章节", "人物", "行为", "过程");
    private static final Set<String> BRIDGE_TERMS = Set.of(
            "位置", "地点", "在场", "出现", "离场", "前往", "进入", "到达", "抵达",
            "返回", "移动", "转移", "场景", "衔接", "过渡", "解释", "承接", "桥接");

    @Override
    public Map<String, Object> apply(ChapterGraphState state) {
        List<RepairIssue> issues = new ArrayList<>();
        int index = 1;
        for (ContinuityFinding finding : state.continuityFindings()) {
            if (finding == null) {
                continue;
            }
            issues.add(new RepairIssue(
                    RepairPlanItem.builder()
                            .id("continuity-" + index++)
                            .source(RepairSource.CONTINUITY)
                            .problem(prefer(finding.getReason(), finding.getType()))
                            .evidence(finding.getCurrentEvidence())
                            .affectedRange(finding.getCurrentEvidence())
                            .repairIntent(finding.getReason())
                            .priority(finding.getSeverity() == ContinuitySeverity.HARD
                                    ? RepairPriority.REQUIRED : RepairPriority.OPTIONAL)
                            .build(),
                    finding.getEntity(),
                    finding.getType()
            ));
        }
        index = 1;
        for (QualityFinding finding : state.qualityFindings()) {
            if (finding == null) {
                continue;
            }
            issues.add(new RepairIssue(
                    RepairPlanItem.builder()
                            .id("quality-" + index++)
                            .source(RepairSource.QUALITY)
                            .problem(prefer(finding.getProblem(), finding.getType()))
                            .evidence(finding.getEvidence())
                            .affectedRange(finding.getAffectedRange())
                            .repairIntent(finding.getRepairIntent())
                            .priority(finding.getSeverity() == QualitySeverity.MUST_FIX
                                    ? RepairPriority.REQUIRED : RepairPriority.OPTIONAL)
                            .build(),
                    null,
                    finding.getType()
            ));
        }

        List<RepairIssue> deduplicated = deduplicate(issues);
        List<RepairPlanItem> items = deduplicated.stream()
                .map(RepairIssue::item)
                .toList();

        return Map.of(
                ChapterGraphKeys.REPAIR_PLAN,
                RepairPlan.builder().items(items).build(),
                ChapterGraphKeys.CURRENT_NODE,
                NODE,
                ChapterGraphKeys.COMPLETED_STAGES,
                List.of(NODE)
        );
    }

    /**
     * 一次确定性 semantic dedup：优先使用相同证据；否则要求实体和语义域同时一致。
     * 例如“郝乐位置冲突”和“郝乐场景衔接缺少解释”会合并，
     * 但同一人物的动机问题不会因为共享人物名而被吞掉。
     */
    private List<RepairIssue> deduplicate(List<RepairIssue> source) {
        List<RepairIssue> result = new ArrayList<>();
        for (RepairIssue incoming : source) {
            int duplicateIndex = -1;
            for (int i = 0; i < result.size(); i++) {
                if (sameIssue(result.get(i), incoming)) {
                    duplicateIndex = i;
                    break;
                }
            }
            if (duplicateIndex < 0) {
                result.add(incoming);
            } else {
                result.set(duplicateIndex, merge(result.get(duplicateIndex), incoming));
            }
        }
        return result;
    }

    private boolean sameIssue(RepairIssue left, RepairIssue right) {
        if (hasSharedNonBlank(left.item().getProblem(), right.item().getProblem())) {
            return true;
        }
        String leftEntity = normalize(left.entity());
        String rightEntity = normalize(right.entity());
        if (!leftEntity.isBlank() && rightEntity.isBlank()) {
            rightEntity = findSharedEntity(leftEntity, right.item());
        }
        if (!leftEntity.isBlank() && !rightEntity.isBlank()
                && !leftEntity.equals(rightEntity)) {
            return false;
        }
        Set<String> leftDomains = semanticDomains(left);
        Set<String> rightDomains = semanticDomains(right);
        boolean sharedDomain = !leftDomains.isEmpty()
                && !rightDomains.isEmpty()
                && !java.util.Collections.disjoint(leftDomains, rightDomains);
        if (!sharedDomain) {
            return false;
        }
        if (hasSharedNonBlank(left.item().getEvidence(), right.item().getEvidence())) {
            return true;
        }
        Set<String> leftTerms = distinctiveTerms(left.item());
        Set<String> rightTerms = distinctiveTerms(right.item());
        boolean sharedTerm = !java.util.Collections.disjoint(leftTerms, rightTerms);
        return (!leftEntity.isBlank() && !rightEntity.isBlank())
                || sharedTerm;
    }

    private RepairIssue merge(RepairIssue left, RepairIssue right) {
        RepairPlanItem leftItem = left.item();
        RepairPlanItem rightItem = right.item();
        RepairPlanItem merged = RepairPlanItem.builder()
                .id(leftItem.getId())
                .source(leftItem.getSource())
                .problem(join(leftItem.getProblem(), rightItem.getProblem()))
                .evidence(join(leftItem.getEvidence(), rightItem.getEvidence()))
                .affectedRange(join(leftItem.getAffectedRange(), rightItem.getAffectedRange()))
                .repairIntent(join(leftItem.getRepairIntent(), rightItem.getRepairIntent()))
                .priority(required(leftItem.getPriority()) || required(rightItem.getPriority())
                        ? RepairPriority.REQUIRED : RepairPriority.OPTIONAL)
                .build();
        return new RepairIssue(
                merged,
                prefer(left.entity(), right.entity()),
                join(left.type(), right.type())
        );
    }

    private Set<String> semanticDomains(RepairIssue issue) {
        Set<String> domains = new LinkedHashSet<>();
        String text = normalize(issue.type() + " "
                + issue.item().getProblem() + " "
                + issue.item().getEvidence() + " "
                + issue.item().getRepairIntent());
        if (containsAny(text, "位置", "地点", "在场", "场景", "衔接", "过渡", "承接", "桥接")) {
            domains.add("SCENE_BRIDGE");
        }
        if (containsAny(text, "因果", "原因", "动机", "行为", "逻辑", "导致")) {
            domains.add("CAUSAL_MOTIVATION");
        }
        if (containsAny(text, "调查", "重复", "探查", "追查", "推进", "进展")) {
            domains.add("PROGRESSION_REDUNDANCY");
        }
        if (containsAny(text, "文风", "措辞", "描写", "自然", "信息密度")) {
            domains.add("PROSE");
        }
        if (containsAny(text, "纲", "事件", "完成", "缺失")) {
            domains.add("OUTLINE");
        }
        if (issue.item().getSource() == RepairSource.CONTINUITY
                && "TIMELINE".equalsIgnoreCase(issue.type())) {
            domains.add("SCENE_BRIDGE");
        }
        return domains;
    }

    private Set<String> distinctiveTerms(RepairPlanItem item) {
        Set<String> terms = new LinkedHashSet<>();
        String text = normalize(item.getProblem() + " " + item.getEvidence() + " "
                + item.getRepairIntent());
        Matcher matcher = HAN_RUN.matcher(text);
        while (matcher.find()) {
            String run = matcher.group();
            if (!GENERIC_TERMS.contains(run)) {
                terms.add(run);
            }
        }
        return terms;
    }

    private String findSharedEntity(String entity, RepairPlanItem item) {
        String text = normalize(item.getProblem() + " " + item.getEvidence() + " "
                + item.getRepairIntent());
        return text.contains(entity) ? entity : "";
    }

    private boolean hasSharedNonBlank(String left, String right) {
        String normalizedLeft = normalize(left);
        String normalizedRight = normalize(right);
        return !normalizedLeft.isBlank() && normalizedLeft.equals(normalizedRight);
    }

    private boolean required(RepairPriority priority) {
        return priority == RepairPriority.REQUIRED;
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private String join(String left, String right) {
        String normalizedLeft = normalize(left);
        String normalizedRight = normalize(right);
        if (normalizedLeft.isBlank()) {
            return normalizedRight.isBlank() ? null : normalizedRight;
        }
        if (normalizedRight.isBlank() || normalizedLeft.equals(normalizedRight)) {
            return normalizedLeft;
        }
        return normalizedLeft + "；" + normalizedRight;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").trim();
    }

    private String prefer(String primary, String fallback) {
        return primary == null || primary.isBlank() ? fallback : primary;
    }

    private record RepairIssue(RepairPlanItem item, String entity, String type) {
    }
}
