package cn.ninth.novel.domain.chapter.model.valobj;

import cn.ninth.novel.domain.chapter.model.valobj.enums.RegressionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** 一条返修项的回归检查结果。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RegressionCheckResult {

    /** 对应 RepairPlanItem.id。 */
    private String repairId;

    /** 本次检查是否已解决该返修项。 */
    private RegressionStatus status;

    /** 本次检查后仍未解决的 REQUIRED 返修项，用于 Revision #2 的窄输入。 */
    @Builder.Default
    private List<String> unresolvedRepairIds = new ArrayList<>();

    /** 是否出现新的硬回归。 */
    private boolean newHardRegression;

    /** 检查结论理由。 */
    private String reason;

    public boolean isResolved() {
        return status == RegressionStatus.RESOLVED;
    }

    public boolean isUnresolved() {
        return status == RegressionStatus.UNRESOLVED;
    }

    public List<String> getUnresolvedRepairIds() {
        return unresolvedRepairIds == null ? List.of() : List.copyOf(unresolvedRepairIds);
    }

    public void setUnresolvedRepairIds(List<String> unresolvedRepairIds) {
        this.unresolvedRepairIds = unresolvedRepairIds == null
                ? new ArrayList<>() : new ArrayList<>(unresolvedRepairIds);
    }
}
