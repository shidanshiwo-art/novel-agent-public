package cn.ninth.novel.domain.chapter.service.agent;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PromptAppenderTest {

    @Test
    void shouldAppendTrimmedListItemsAndIgnoreBlankValues() {
        StringBuilder prompt = new StringBuilder();

        PromptAppender.appendList(
                prompt,
                "伏笔动作",
                Arrays.asList("  埋下船票线索。  ", null, " ", "推进佩剑刻痕。")
        );

        assertEquals(
                "伏笔动作：\n- 埋下船票线索。\n- 推进佩剑刻痕。\n",
                prompt.toString()
        );
    }
}
