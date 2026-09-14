package cn.ninth.novel.domain.chapter.service.agent;

import cn.ninth.novel.Application;
import cn.ninth.novel.domain.chapter.model.valobj.ConflictCandidate;
import cn.ninth.novel.domain.chapter.model.valobj.ContinuitySemanticResult;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

/** 独立的连续性语义验证真实模型入口，默认关闭。 */
@SpringBootTest(classes = Application.class)
@ActiveProfiles("dev")
class ContinuitySemanticVerifierRealModelIT {

    @Autowired
    private ModelContinuitySemanticVerifier verifier;

    @Test
    void runContinuitySemanticVerifier() {
        if (!"true".equalsIgnoreCase(System.getProperty("runContinuityVerifier"))) {
            System.out.println(
                    "Continuity semantic verifier real-model test is disabled; "
                            + "use -DrunContinuityVerifier=true explicitly.");
            return;
        }

        ConflictCandidate candidate = new ConflictCandidate(
                "CHARACTER_STATE",
                "郝乐",
                "宿舍门推开时，郝乐从宿舍床上弹起来。",
                "第13章记录：郝乐今夜已在行政楼报到。",
                List.of("郝乐上一章中午被带到行政楼"),
                "第14日夜",
                13,
                true,
                "人物位置需要语义复核");
        ReviewContext context = ReviewContext.builder()
                .currentDraft(candidate.currentEvidence())
                .build();
        ContinuitySemanticResult result = verifier.verify(candidate, context);

        System.out.println("========== CONTINUITY SEMANTIC VERIFIER ==========");
        System.out.println("SYSTEM PROMPT:\n" + ModelContinuitySemanticVerifier.SYSTEM_PROMPT);
        System.out.println("USER PROMPT:\n" + verifier.buildUserPrompt(candidate));
        System.out.println("RESULT:\n" + result);
        System.out.println("========== END CONTINUITY SEMANTIC VERIFIER ==========");
    }
}
