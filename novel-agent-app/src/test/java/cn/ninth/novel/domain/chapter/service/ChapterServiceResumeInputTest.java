package cn.ninth.novel.domain.chapter.service;

import cn.ninth.novel.domain.chapter.model.valobj.enums.HumanDecisionEnum;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChapterServiceResumeInputTest {

    @Test
    void shouldTrimCurrentInstructionAndClearMissingInstruction() {
        Map<String, Object> instructed = ChapterService.resumeInput(
                HumanDecisionEnum.REVISE,
                "  加强环境压迫感  "
        );
        Map<String, Object> withoutInstruction = ChapterService.resumeInput(
                HumanDecisionEnum.REVISE,
                null
        );
        Map<String, Object> passWithInstruction = ChapterService.resumeInput(
                HumanDecisionEnum.PASS,
                "不应进入改稿节点"
        );

        assertThat(instructed)
                .containsEntry(
                        ChapterGraphKeys.HUMAN_DECISION,
                        HumanDecisionEnum.REVISE.name()
                )
                .containsEntry(
                        ChapterGraphKeys.REVISION_INSTRUCTION,
                        "加强环境压迫感"
                );
        assertThat(withoutInstruction)
                .containsEntry(ChapterGraphKeys.REVISION_INSTRUCTION, "");
        assertThat(passWithInstruction)
                .containsEntry(ChapterGraphKeys.REVISION_INSTRUCTION, "");
    }
}
