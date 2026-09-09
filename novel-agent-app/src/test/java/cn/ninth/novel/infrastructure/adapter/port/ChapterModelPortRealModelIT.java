package cn.ninth.novel.infrastructure.adapter.port;

import cn.ninth.novel.Application;
import cn.ninth.novel.domain.chapter.adapter.port.IChapterModelPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = Application.class)
@ActiveProfiles("dev")
class ChapterModelPortRealModelIT {

    @Autowired
    private IChapterModelPort modelPort;

    @Test
    void printTextModelOutput() {
        String output = modelPort.call(
                "你是一名中文小说作家，请直接回答用户，不要解释创作过程。",
                "写一段不超过 150 字的雨夜客栈开场，营造悬疑氛围。"
        );

        System.out.println("\n========== REAL MODEL TEXT OUTPUT ==========");
        System.out.println(output);
        System.out.println("========== END TEXT OUTPUT ==========\n");
    }

}
