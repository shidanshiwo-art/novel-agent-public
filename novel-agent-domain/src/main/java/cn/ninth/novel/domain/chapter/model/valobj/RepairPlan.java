package cn.ninth.novel.domain.chapter.model.valobj;

import cn.ninth.novel.domain.chapter.model.valobj.enums.RepairPriority;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Review Pipeline 汇总后的返修计划。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RepairPlan {

    /** 按审核来源和出现顺序排列的返修项。 */
    @Builder.Default
    private List<RepairPlanItem> items = new ArrayList<>();

    public List<RepairPlanItem> getItems() {
        return items == null ? List.of() : List.copyOf(items);
    }

    public void setItems(List<RepairPlanItem> items) {
        this.items = items == null ? new ArrayList<>() : new ArrayList<>(items);
    }

    /** 是否存在必须完成的返修项。 */
    public boolean hasRequiredItems() {
        return getItems().stream()
                .anyMatch(item -> item != null
                        && item.getPriority() == RepairPriority.REQUIRED);
    }

    /** 返回必须完成的返修项。 */
    public List<RepairPlanItem> requiredItems() {
        return getItems().stream()
                .filter(item -> item != null
                        && item.getPriority() == RepairPriority.REQUIRED)
                .toList();
    }
}
