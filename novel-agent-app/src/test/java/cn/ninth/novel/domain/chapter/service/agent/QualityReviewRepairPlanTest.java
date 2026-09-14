package cn.ninth.novel.domain.chapter.service.agent;

import cn.ninth.novel.domain.chapter.model.valobj.ContinuityFinding;
import cn.ninth.novel.domain.chapter.model.valobj.QualityFinding;
import cn.ninth.novel.domain.chapter.model.valobj.RepairPlan;
import cn.ninth.novel.domain.chapter.model.valobj.RepairPlanItem;
import cn.ninth.novel.domain.chapter.model.valobj.enums.ContinuitySeverity;
import cn.ninth.novel.domain.chapter.model.valobj.enums.QualitySeverity;
import cn.ninth.novel.domain.chapter.model.valobj.enums.RepairPriority;
import cn.ninth.novel.domain.chapter.model.valobj.enums.RepairSource;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Quality Review 六项边界、严重度映射和 RepairPlan semantic dedup 验收。 */
class QualityReviewRepairPlanTest {

    @Test
    void hardContinuityShouldBecomeRequired() {
        RepairPlan plan = plan(List.of(continuity(ContinuitySeverity.HARD)), List.of());

        assertThat(plan.getItems()).hasSize(1);
        assertThat(plan.getItems().get(0).getPriority()).isEqualTo(RepairPriority.REQUIRED);
        System.out.printf("HARD → REQUIRED：items=%d，priority=%s%n",
                plan.getItems().size(), plan.getItems().get(0).getPriority());
    }

    @Test
    void softContinuityShouldBecomeOptional() {
        RepairPlan plan = plan(List.of(continuity(ContinuitySeverity.SOFT)), List.of());

        assertThat(plan.getItems()).hasSize(1);
        assertThat(plan.getItems().get(0).getPriority()).isEqualTo(RepairPriority.OPTIONAL);
        System.out.printf("SOFT → OPTIONAL：items=%d，priority=%s%n",
                plan.getItems().size(), plan.getItems().get(0).getPriority());
    }

    @Test
    void mustFixQualityShouldBecomeRequired() {
        RepairPlan plan = plan(List.of(), List.of(quality(QualitySeverity.MUST_FIX,
                "CAUSAL_LOGIC", "因果无法成立")));

        assertThat(plan.getItems()).hasSize(1);
        assertThat(plan.getItems().get(0).getPriority()).isEqualTo(RepairPriority.REQUIRED);
        System.out.printf("MUST_FIX → REQUIRED：items=%d，priority=%s%n",
                plan.getItems().size(), plan.getItems().get(0).getPriority());
    }

    @Test
    void optionalQualityShouldBecomeOptional() {
        RepairPlan plan = plan(List.of(), List.of(quality(QualitySeverity.OPTIONAL,
                "PROSE_NATURALNESS", "措辞可以更自然")));

        assertThat(plan.getItems()).hasSize(1);
        assertThat(plan.getItems().get(0).getPriority()).isEqualTo(RepairPriority.OPTIONAL);
        System.out.printf("OPTIONAL → OPTIONAL：items=%d，priority=%s%n",
                plan.getItems().size(), plan.getItems().get(0).getPriority());
    }

    @Test
    void semanticallySameContinuityAndQualityIssuesShouldMerge() {
        ContinuityFinding continuity = ContinuityFinding.builder()
                .type("CHARACTER_STATE")
                .entity("郝乐")
                .severity(ContinuitySeverity.HARD)
                .currentEvidence("郝乐出现在宿舍")
                .historicalEvidence("上一章郝乐在行政楼")
                .reason("郝乐位置冲突")
                .build();
        QualityFinding quality = QualityFinding.builder()
                .type("PLOT_PROGRESSION")
                .evidence("宿舍场景")
                .problem("郝乐场景衔接缺少解释")
                .repairIntent("补充郝乐从行政楼到宿舍的过渡")
                .affectedRange("宿舍段落")
                .severity(QualitySeverity.MUST_FIX)
                .build();

        RepairPlan plan = plan(List.of(continuity), List.of(quality));

        assertThat(plan.getItems()).hasSize(1);
        RepairPlanItem item = plan.getItems().get(0);
        assertThat(item.getPriority()).isEqualTo(RepairPriority.REQUIRED);
        assertThat(item.getSource()).isEqualTo(RepairSource.CONTINUITY);
        assertThat(item.getProblem()).contains("郝乐位置冲突", "郝乐场景衔接缺少解释");
        assertThat(item.getRepairIntent()).contains("补充郝乐");
        System.out.printf("semantic dedup：items=%d，source=%s，priority=%s，problem=%s%n",
                plan.getItems().size(), item.getSource(), item.getPriority(), item.getProblem());
    }

    @Test
    void differentIssuesShouldRemainSeparate() {
        ContinuityFinding continuity = ContinuityFinding.builder()
                .type("CHARACTER_STATE")
                .entity("郝乐")
                .severity(ContinuitySeverity.HARD)
                .currentEvidence("郝乐出现在宿舍")
                .historicalEvidence("上一章郝乐在行政楼")
                .reason("郝乐位置冲突")
                .build();
        QualityFinding quality = quality(QualitySeverity.MUST_FIX,
                "CHARACTER_MOTIVATION", "郝乐突然决定隐瞒真相，动机不足");

        RepairPlan plan = plan(List.of(continuity), List.of(quality));

        assertThat(plan.getItems()).hasSize(2);
        assertThat(plan.getItems()).extracting(RepairPlanItem::getPriority)
                .containsExactly(RepairPriority.REQUIRED, RepairPriority.REQUIRED);
        System.out.printf("不同问题保留：items=%d，problems=%s%n",
                plan.getItems().size(),
                plan.getItems().stream().map(RepairPlanItem::getProblem).toList());
    }

    @Test
    void qualityReviewShouldKeepOnlySixTypesAndTwoSeverities() {
        QualityFinding finding = quality(QualitySeverity.MUST_FIX,
                "Outline Completion", "章纲核心事件缺失");
        Map<String, Object> update = new QualityReviewNode().apply(new ChapterGraphState(Map.of(
                ChapterGraphKeys.QUALITY_FINDINGS, List.of(finding))));

        @SuppressWarnings("unchecked")
        List<QualityFinding> normalized = (List<QualityFinding>) update.get(
                ChapterGraphKeys.QUALITY_FINDINGS);
        assertThat(normalized).singleElement().satisfies(value -> {
            assertThat(value.getType()).isEqualTo("OUTLINE_COMPLETION");
            assertThat(value.getSeverity()).isEqualTo(QualitySeverity.MUST_FIX);
        });
        assertThatThrownBy(() -> new QualityReviewNode().apply(new ChapterGraphState(Map.of(
                ChapterGraphKeys.QUALITY_FINDINGS,
                List.of(quality(QualitySeverity.OPTIONAL, "PACING", "节奏问题"))))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的质量审核类型");
        System.out.printf("Quality Review 边界：type=%s，severity=%s，supportedTypes=%s%n",
                normalized.get(0).getType(), normalized.get(0).getSeverity(),
                QualityReviewNode.SUPPORTED_TYPES);
    }

    private RepairPlan plan(
            List<ContinuityFinding> continuityFindings,
            List<QualityFinding> qualityFindings
    ) {
        Map<String, Object> update = new RepairPlanNode().apply(new ChapterGraphState(Map.of(
                ChapterGraphKeys.CONTINUITY_FINDINGS, continuityFindings,
                ChapterGraphKeys.QUALITY_FINDINGS, qualityFindings)));
        return (RepairPlan) update.get(ChapterGraphKeys.REPAIR_PLAN);
    }

    private ContinuityFinding continuity(ContinuitySeverity severity) {
        return ContinuityFinding.builder()
                .type("ITEM_STATE")
                .entity("旧锁")
                .severity(severity)
                .currentEvidence("当前正文中的旧锁")
                .historicalEvidence("历史证据中的旧锁")
                .reason("旧锁状态需要复核")
                .build();
    }

    private QualityFinding quality(QualitySeverity severity, String type, String problem) {
        return QualityFinding.builder()
                .type(type)
                .evidence(problem + "的正文证据")
                .problem(problem)
                .repairIntent("修复" + problem)
                .affectedRange("当前章节相关段落")
                .severity(severity)
                .build();
    }
}
