package cn.ninth.novel.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AddChildOutlineUiContractTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void shouldProvideResponsiveAddChildFormWithServerDerivedKind() throws IOException {
        String view = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/OutlineView.vue"));

        assertThat(view)
                .contains("command=\"add-child\"")
                .contains("command === 'add-child'")
                .contains("function openAddChild(node")
                .contains("<el-dialog")
                .contains("<el-drawer")
                .contains("title=\"新增下级大纲\"")
                .contains("<el-form")
                .contains("label=\"系统层级\"")
                .contains("class=\"system-kind-hint\"")
                .contains("由系统按层级和序号自动生成，不可手动命名")
                .contains("章节号将由系统从当前卷自动分配。")
                .contains("v-if=\"addChildKind === 'VOLUME'\"")
                .contains("const addChildRules = computed(() => ({")
                .contains("addChildKind.value === 'VOLUME'")
                .contains("v-model=\"addChildForm.startChapter\"")
                .contains("v-model=\"addChildForm.endChapter\"")
                .contains("label=\"章节范围\"")
                .contains("父节点范围：第${parent.startChapter}-${parent.endChapter}章；不能与已有同级大纲重叠")
                .contains("isValidChildRange(parent, addChildForm.startChapter, addChildForm.endChapter)")
                .contains("const request: CreateOutlineNodeRequest")
                .contains("if (childKind === 'VOLUME')")
                .doesNotContain("startChapter: addChildForm.startChapter,")
                .doesNotContain("endChapter: addChildForm.endChapter,")
                .contains("label=\"标题\"")
                .contains("label=\"大纲内容\"")
                .contains("if (nodeKind === 'BOOK') return 'VOLUME'")
                .contains("if (nodeKind === 'VOLUME') return 'ARC'")
                .contains("const childKind = parent ? nextChildKind(parent.nodeKind) : null")
                .contains("const addChildSystemLabel = computed")
                .contains("parentNodeCode: parent.nodeCode")
                .contains("data.nodeKind !== 'ARC'")
                .contains("v-if=\"!isMobile\"")
                .contains("v-else")
                .doesNotContain("v-model=\"addChildForm.nodeKind\"")
                .doesNotContain("label=\"节点类型\"")
                .doesNotContain("nodeCode: nextNodeCode(childKind)")
                .doesNotContain("nodeKind: childKind")
                .doesNotContain("sequenceNo: nextChildSequenceNo(parent)")
                .doesNotContain("status: 'PLANNED'")
                .doesNotContain("childKindOrders")
                .doesNotContain("startChapter: null,\n      endChapter: null");

        System.out.println("AddChildOutlineUiContractTest verified Dialog/Drawer and server-derived child kind display");
    }
}
