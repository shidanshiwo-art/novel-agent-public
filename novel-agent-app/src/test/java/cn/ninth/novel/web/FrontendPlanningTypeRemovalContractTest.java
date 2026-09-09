package cn.ninth.novel.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class FrontendPlanningTypeRemovalContractTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void shouldExposeOnlyCurrentOutlineNodeShapeAndPlanningApis() throws IOException {
        String types = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/types/index.ts"));
        String projectApi = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/api/project.ts"));

        assertThat(types)
                .contains("export type OutlineNodeKind = 'BOOK' | 'VOLUME' | 'ARC'")
                .doesNotContain(
                        "legacyOutlineKind",
                        "interface ChapterCardRequest",
                        "interface UpdateChapterCardRequest",
                        "interface ChapterCardResponse",
                        "interface SaveChapterCardsRequest"
                );

        Matcher outlineMatcher = Pattern.compile(
                "export interface OutlineNode \\{([\\s\\S]*?)\\n\\}"
        ).matcher(types);
        assertThat(outlineMatcher.find()).as("OutlineNode interface exists").isTrue();
        assertThat(outlineMatcher.group(1)).doesNotContain(
                "nodeType",
                "chapterNumber",
                "protagonistGoal",
                "conflictDescription",
                "expectedPayoff",
                "endingHook"
        );

        assertThat(projectApi).doesNotContain(
                "SaveChapterCardsRequest",
                "ChapterCardResponse",
                "UpdateChapterCardRequest",
                "saveOutlines",
                "getOutlines",
                "updateChapterCard"
        );

        System.out.println("FrontendPlanningTypeRemovalContractTest verified the current OutlineNode shape and removed unused legacy ChapterCard frontend types");
    }
}
