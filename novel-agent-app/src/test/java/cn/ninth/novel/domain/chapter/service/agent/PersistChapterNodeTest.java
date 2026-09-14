package cn.ninth.novel.domain.chapter.service.agent;

import cn.ninth.novel.domain.chapter.adapter.repository.IChapterPersistRepository;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterMemoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.StoryStateSnapshot;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphState;
import cn.ninth.novel.domain.memory.model.MemoryCandidate;
import cn.ninth.novel.domain.memory.model.MemoryCandidateStatus;
import cn.ninth.novel.domain.memory.model.MemoryCandidateType;
import cn.ninth.novel.domain.memory.model.MemoryCommitRequest;
import cn.ninth.novel.domain.memory.model.MemorySourceVersion;
import cn.ninth.novel.types.enums.ResponseCode;
import cn.ninth.novel.types.exception.AppException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class PersistChapterNodeTest {

    @Test
    void shouldExposeOnlyChapterMemoryPersistSignature() {
        List<Method> persistMethods = Arrays.stream(
                        IChapterPersistRepository.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("persist"))
                .toList();

        System.out.printf("persist repository signatures: %s%n",
                persistMethods.stream().map(Method::toGenericString).toList());
        assertThat(persistMethods).hasSize(2);
        assertThat(persistMethods).anySatisfy(method -> assertThat(method.getParameterTypes())
                .containsExactly(
                        String.class,
                        int.class,
                        String.class,
                        ChapterMemoryVO.class
                ));
        assertThat(persistMethods).anySatisfy(method -> assertThat(method.getParameterTypes())
                .containsExactly(
                        String.class,
                        int.class,
                        String.class,
                        ChapterMemoryVO.class,
                        StoryStateSnapshot.class
                ));
    }

    @Test
    void shouldPersistFinalChapterAndMarkStageCompleted() {
        AtomicReference<PersistRequest> captured = new AtomicReference<>();
        IChapterPersistRepository repository = (projectCode, chapterNumber, content, chapterMemory) ->
                captured.set(new PersistRequest(projectCode, chapterNumber, content, chapterMemory));
        PersistChapterNode node = new PersistChapterNode(repository);
        ChapterMemoryVO chapterMemory = chapterMemory();

        Map<String, Object> update = node.apply(new ChapterGraphState(Map.of(
                ChapterGraphKeys.PROJECT_CODE, "persist-node-story",
                ChapterGraphKeys.CHAPTER_NUMBER, 2,
                ChapterGraphKeys.DRAFT, "最终章节正文",
                ChapterGraphKeys.MEMORY, chapterMemory
        )));

        assertThat(captured.get()).isEqualTo(new PersistRequest(
                "persist-node-story", 2, "最终章节正文", chapterMemory));
        assertThat(update)
                .containsOnlyKeys(ChapterGraphKeys.CURRENT_NODE, ChapterGraphKeys.COMPLETED_STAGES)
                .containsEntry(ChapterGraphKeys.CURRENT_NODE, "PERSIST")
                .containsEntry(ChapterGraphKeys.COMPLETED_STAGES, List.of("PERSIST"));
    }

    @Test
    void shouldRejectMissingRequiredState() {
        PersistChapterNode node = new PersistChapterNode((projectCode, chapterNumber, content, chapterMemory) -> {
        });
        ChapterMemoryVO chapterMemory = chapterMemory();

        assertMissingState(() -> node.apply(state(Map.of())), "PERSIST 节点缺少 projectCode");
        assertMissingState(() -> node.apply(state(Map.of(
                ChapterGraphKeys.PROJECT_CODE, "story"
        ))), "PERSIST 节点缺少 chapterNumber");
        assertMissingState(() -> node.apply(state(Map.of(
                ChapterGraphKeys.PROJECT_CODE, "story",
                ChapterGraphKeys.CHAPTER_NUMBER, 1
        ))), "PERSIST 节点缺少 draft");
        assertMissingState(() -> node.apply(state(Map.of(
                ChapterGraphKeys.PROJECT_CODE, "story",
                ChapterGraphKeys.CHAPTER_NUMBER, 1,
                ChapterGraphKeys.DRAFT, "正文"
        ))), "PERSIST 节点缺少 chapterMemory");
        assertThat(chapterMemory.getShortSummary()).isEqualTo("章节摘要");
    }

    @Test
    void shouldPassIndependentStoryStateSnapshotToPersistence() {
        AtomicReference<StoryStateSnapshot> captured = new AtomicReference<>();
        IChapterPersistRepository repository = new IChapterPersistRepository() {
            @Override
            public void persist(
                    String projectCode,
                    int chapterNumber,
                    String content,
                    ChapterMemoryVO chapterMemory
            ) {
                throw new AssertionError("应调用包含状态快照的持久化重载");
            }

            @Override
            public void persist(
                    String projectCode,
                    int chapterNumber,
                    String content,
                    ChapterMemoryVO chapterMemory,
                    StoryStateSnapshot storyStateSnapshot
            ) {
                captured.set(storyStateSnapshot);
            }
        };
        StoryStateSnapshot snapshot = new StoryStateSnapshot(
                List.of("旧钥匙"), List.of(), List.of("知道入口位置"), List.of("城门外"));

        new PersistChapterNode(repository).apply(new ChapterGraphState(Map.of(
                ChapterGraphKeys.PROJECT_CODE, "persist-node-story",
                ChapterGraphKeys.CHAPTER_NUMBER, 2,
                ChapterGraphKeys.DRAFT, "最终章节正文",
                ChapterGraphKeys.MEMORY, chapterMemory(),
                ChapterGraphKeys.STORY_STATE_SNAPSHOT, snapshot
        )));

        assertThat(captured).hasValue(snapshot);
        System.out.println("PERSIST 独立状态快照传递：" + captured.get());
    }

    @Test
    void shouldPassFinalVersionAndCandidatesThroughCanonicalGateEntry() {
        String content = "最终正文中的证据";
        MemorySourceVersion sourceVersion = MemorySourceVersion.create("chapter-v1", content);
        MemoryCandidate candidate = MemoryCandidate.provisional(
                        sourceVersion, content, 0, 4, MemoryCandidateType.EVENT)
                .withStatus(MemoryCandidateStatus.READY_FOR_GATE);
        AtomicReference<MemoryCommitRequest> captured = new AtomicReference<>();
        IChapterPersistRepository repository = new IChapterPersistRepository() {
            @Override
            public void persist(
                    String projectCode,
                    int chapterNumber,
                    String content,
                    ChapterMemoryVO chapterMemory
            ) {
                throw new AssertionError("应调用 Canonical Gate 持久化入口");
            }

            @Override
            public void persistWithMemoryCommit(
                    String projectCode,
                    int chapterNumber,
                    String content,
                    ChapterMemoryVO chapterMemory,
                    StoryStateSnapshot storyStateSnapshot,
                    MemoryCommitRequest memoryCommitRequest
            ) {
                captured.set(memoryCommitRequest);
            }
        };

        new PersistChapterNode(repository).apply(new ChapterGraphState(Map.of(
                ChapterGraphKeys.PROJECT_CODE, "persist-gate-story",
                ChapterGraphKeys.CHAPTER_NUMBER, 2,
                ChapterGraphKeys.DRAFT, content,
                ChapterGraphKeys.SOURCE_VERSION, sourceVersion,
                ChapterGraphKeys.MEMORY_CANDIDATES, List.of(candidate),
                ChapterGraphKeys.MEMORY, chapterMemory()
        )));

        System.out.printf("PERSIST Canonical Gate 输入：version=%s, candidates=%d%n",
                captured.get().finalSourceVersion().chapterVersion(),
                captured.get().candidates().size());
        assertThat(captured.get().finalContent()).isEqualTo(content);
        assertThat(captured.get().finalSourceVersion()).isEqualTo(sourceVersion);
        assertThat(captured.get().candidates()).containsExactly(candidate);
    }

    private ChapterMemoryVO chapterMemory() {
        return new ChapterMemoryVO(
                2,
                "章节摘要",
                List.of("关键事件"),
                List.of("未解决问题"),
                "结尾钩子"
        );
    }

    private ChapterGraphState state(Map<String, Object> data) {
        return new ChapterGraphState(data);
    }

    private void assertMissingState(ThrowingCallable invocation, String expectedMessage) {
        AppException exception = catchThrowableOfType(invocation, AppException.class);

        assertThat(exception.getCode()).isEqualTo(ResponseCode.ILLEGAL_PARAMETER.getCode());
        assertThat(exception.getInternalDetail()).isEqualTo(expectedMessage);
    }

    private record PersistRequest(
            String projectCode,
            int chapterNumber,
            String content,
            ChapterMemoryVO chapterMemory
    ) {
    }
}
