package cn.ninth.novel.trigger.http;

import cn.ninth.novel.api.dto.AddStoryCharacterRequestDTO;
import cn.ninth.novel.api.dto.ChapterSearchResultResponseDTO;
import cn.ninth.novel.api.dto.CreateNovelProjectRequestDTO;
import cn.ninth.novel.api.dto.ConfirmStoryBibleRequestDTO;
import cn.ninth.novel.api.dto.ConfirmCharacterDraftDTO;
import cn.ninth.novel.api.dto.ConfirmCharactersRequestDTO;
import cn.ninth.novel.api.dto.DiscardCharacterDraftRequestDTO;
import cn.ninth.novel.api.dto.GeneratedChapterResponseDTO;
import cn.ninth.novel.api.dto.GenerateStoryBibleRequestDTO;
import cn.ninth.novel.api.dto.GenerateCharacterRequestDTO;
import cn.ninth.novel.api.dto.NovelProjectResponseDTO;
import cn.ninth.novel.api.dto.OverwriteChapterContentRequestDTO;
import cn.ninth.novel.api.dto.PlanningDraftResponseDTO;
import cn.ninth.novel.api.dto.SaveStoryBibleRequestDTO;
import cn.ninth.novel.api.dto.StoryBibleResponseDTO;
import cn.ninth.novel.api.dto.StoryCharacterResponseDTO;
import cn.ninth.novel.api.dto.UpdateStoryCharacterRequestDTO;
import cn.ninth.novel.api.dto.UpdateTargetChapterCountRequestDTO;
import cn.ninth.novel.api.dto.VolumeChapterGroupResponseDTO;
import cn.ninth.novel.domain.project.model.valobj.GeneratedChapterVO;
import cn.ninth.novel.domain.project.model.valobj.CharacterDraftVO;
import cn.ninth.novel.domain.project.model.valobj.CharacterUpdateResultVO;
import cn.ninth.novel.domain.planning.model.valobj.PlanningDraftVO;
import cn.ninth.novel.domain.project.model.valobj.NovelProjectVO;
import cn.ninth.novel.domain.project.model.valobj.StoryBibleVO;
import cn.ninth.novel.domain.project.model.valobj.StoryCharacterVO;
import cn.ninth.novel.domain.project.model.valobj.VolumeChapterGroupVO;
import cn.ninth.novel.domain.project.service.INovelProjectService;
import cn.ninth.novel.types.response.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/novels/projects")
public class NovelProjectController {

    private final INovelProjectService projectService;

    public NovelProjectController(INovelProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public Response<NovelProjectResponseDTO> createProject(
            @RequestBody CreateNovelProjectRequestDTO request
    ) {
        NovelProjectVO project = projectService.createProject(
                new NovelProjectVO(
                        request.projectCode(),
                        request.title(),
                        request.genre(),
                        request.targetChapterCount(),
                        request.wordsPerChapter(),
                        null,
                        null
                )
        );
        return Response.success(toProjectResponse(project));
    }

    @PostMapping("/{projectCode}/target-chapter-count")
    public Response<NovelProjectResponseDTO> updateTargetChapterCount(
            @PathVariable String projectCode,
            @RequestBody UpdateTargetChapterCountRequestDTO request
    ) {
        NovelProjectVO project = projectService.updateTargetChapterCount(
                projectCode,
                request == null ? null : request.targetChapterCount()
        );
        return Response.success(toProjectResponse(project));
    }

    @PostMapping("/list")
    public Response<List<NovelProjectResponseDTO>> listProjects() {
        return Response.success(projectService.listProjects().stream()
                .map(this::toProjectResponse)
                .toList());
    }

    @PostMapping("/{projectCode}/bible")
    public Response<StoryBibleResponseDTO> saveBible(
            @PathVariable String projectCode,
            @RequestBody SaveStoryBibleRequestDTO request
    ) {
        StoryBibleVO bible = projectService.saveBible(
                projectCode,
                new StoryBibleVO(
                        request.oneSentencePremise(),
                        request.coreTheme(),
                        request.mainConflict(),
                        request.endingDirection(),
                        request.worldBackground(),
                        request.powerSystemJson(),
                        request.hardRulesJson(),
                        request.styleGuide(),
                        request.status()
                )
        );
        return Response.success(toBibleResponse(bible));
    }

    @GetMapping("/{projectCode}/bible")
    public Response<StoryBibleResponseDTO> getBible(@PathVariable String projectCode) {
        StoryBibleVO bible = projectService.getBible(projectCode);
        return Response.success(bible == null ? null : toBibleResponse(bible));
    }

    @PostMapping("/{projectCode}/bible/generate")
    public Response<PlanningDraftResponseDTO> generateStoryBible(
            @PathVariable String projectCode,
            @RequestBody GenerateStoryBibleRequestDTO request
    ) {
        PlanningDraftVO draft = projectService.generateStoryBible(
                projectCode,
                request == null ? null : request.requirement()
        );
        return Response.success(toDraftResponse(draft));
    }

    @PostMapping("/{projectCode}/characters/generate")
    public Response<PlanningDraftResponseDTO> generateCharacters(
            @PathVariable String projectCode,
            @RequestBody GenerateCharacterRequestDTO request
    ) {
        PlanningDraftVO draft = projectService.generateCharacters(
                projectCode,
                request == null ? null : request.preferredCount(),
                request == null ? null : request.requirement()
        );
        return Response.success(toDraftResponse(draft));
    }

    @PostMapping("/{projectCode}/characters/confirm")
    public Response<List<StoryCharacterResponseDTO>> confirmCharacters(
            @PathVariable String projectCode,
            @RequestBody ConfirmCharactersRequestDTO request
    ) {
        List<StoryCharacterVO> characters = projectService.confirmCharacters(
                projectCode,
                request.draftId(),
                toCharacterDrafts(request.characters())
        );
        return Response.success(characters.stream()
                .map(this::toCharacterResponse)
                .toList());
    }

    @PostMapping("/{projectCode}/characters/discard")
    public Response<Void> discardCharacterDraft(
            @PathVariable String projectCode,
            @RequestBody DiscardCharacterDraftRequestDTO request
    ) {
        projectService.discardCharacterDraft(projectCode, request.draftId());
        return Response.success(null);
    }

    @PostMapping("/{projectCode}/bible/confirm")
    public Response<StoryBibleResponseDTO> confirmStoryBible(
            @PathVariable String projectCode,
            @RequestBody ConfirmStoryBibleRequestDTO request
    ) {
        StoryBibleVO bible = projectService.confirmStoryBible(
                projectCode,
                request.draftId(),
                new StoryBibleVO(
                        request.oneSentencePremise(),
                        request.coreTheme(),
                        request.mainConflict(),
                        request.endingDirection(),
                        request.worldBackground(),
                        request.powerSystemJson(),
                        request.hardRulesJson(),
                        request.styleGuide(),
                        null
                )
        );
        return Response.success(toBibleResponse(bible));
    }

    @GetMapping("/{projectCode}/characters")
    public Response<List<StoryCharacterResponseDTO>> listCharacters(
            @PathVariable String projectCode
    ) {
        return Response.success(projectService.listCharacters(projectCode).stream()
                .map(this::toCharacterResponse)
                .toList());
    }

    @PostMapping("/{projectCode}/characters")
    public Response<StoryCharacterResponseDTO> addCharacter(
            @PathVariable String projectCode,
            @RequestBody AddStoryCharacterRequestDTO request
    ) {
        StoryCharacterVO character = projectService.addCharacter(
                projectCode,
                toCharacter(request)
        );
        return Response.success(toCharacterResponse(character));
    }

    @PostMapping("/{projectCode}/characters/{characterCode}")
    public Response<StoryCharacterResponseDTO> updateCharacter(
            @PathVariable String projectCode,
            @PathVariable String characterCode,
            @RequestBody UpdateStoryCharacterRequestDTO request
    ) {
        CharacterUpdateResultVO result = projectService.updateCharacterWithWarning(
                projectCode,
                characterCode,
                toCharacter(characterCode, request)
        );
        return Response.success(toCharacterResponse(result.character(), result.warning()));
    }

    @DeleteMapping("/{projectCode}/characters/{characterCode}")
    public Response<Void> deleteCharacter(
            @PathVariable String projectCode,
            @PathVariable String characterCode
    ) {
        projectService.deleteCharacter(projectCode, characterCode);
        return Response.success(null);
    }

    @PostMapping("/{projectCode}/chapters/{chapterNumber}")
    public Response<GeneratedChapterResponseDTO> overwriteChapterContent(
            @PathVariable String projectCode,
            @PathVariable int chapterNumber,
            @RequestBody OverwriteChapterContentRequestDTO request
    ) {
        return Response.success(toGeneratedChapterResponse(
                projectService.overwriteChapterContent(
                        projectCode,
                        chapterNumber,
                        request.title(),
                        request.content()
                )
        ));
    }

    @PostMapping("/{projectCode}/chapters/{chapterNumber}/resync")
    public Response<GeneratedChapterResponseDTO> resyncChapterDerivedData(
            @PathVariable String projectCode,
            @PathVariable int chapterNumber
    ) {
        return Response.success(toGeneratedChapterResponse(
                projectService.resyncChapterDerivedData(projectCode, chapterNumber)
        ));
    }

    @GetMapping("/{projectCode}/chapters/{chapterNumber}")
    public Response<GeneratedChapterResponseDTO> getChapter(
            @PathVariable String projectCode,
            @PathVariable int chapterNumber
    ) {
        GeneratedChapterVO chapter = projectService.getChapter(
                projectCode,
                chapterNumber
        );
        return Response.success(toGeneratedChapterResponse(chapter));
    }

    @DeleteMapping("/{projectCode}/chapters/{chapterNumber}")
    public Response<Void> deleteChapter(@PathVariable String projectCode, @PathVariable int chapterNumber) {
        projectService.deleteChapter(projectCode, chapterNumber);
        return Response.success(null);
    }

    @GetMapping("/{projectCode}/chapters")
    public Response<List<GeneratedChapterResponseDTO>> listChapters(
            @PathVariable String projectCode
    ) {
        return Response.success(projectService.listChapters(projectCode).stream()
                .map(this::toGeneratedChapterResponse)
                .toList());
    }

    @PostMapping("/{projectCode}/chapters/search")
    public Response<List<ChapterSearchResultResponseDTO>> searchChapters(
            @PathVariable String projectCode,
            @RequestBody java.util.Map<String, String> request
    ) {
        String keyword = request == null ? null : request.get("keyword");
        return Response.success(projectService.searchChapters(projectCode, keyword).stream()
                .map(chapter -> new ChapterSearchResultResponseDTO(
                        chapter.chapterNumber(), chapter.title(), snippet(chapter.content(), keyword),
                        countMatches(chapter.content(), keyword)))
                .toList());
    }

    private String snippet(String content, String keyword) {
        if (content == null || keyword == null) return "";
        int index = content.toLowerCase().indexOf(keyword.trim().toLowerCase());
        if (index < 0) return content.substring(0, Math.min(80, content.length()));
        int start = Math.max(0, index - 35);
        int end = Math.min(content.length(), index + keyword.trim().length() + 45);
        return (start > 0 ? "…" : "") + content.substring(start, end) + (end < content.length() ? "…" : "");
    }

    private int countMatches(String content, String keyword) {
        if (content == null || keyword == null || keyword.isBlank()) return 0;
        int count = 0, from = 0;
        String source = content.toLowerCase(), target = keyword.trim().toLowerCase();
        while ((from = source.indexOf(target, from)) >= 0) { count++; from += target.length(); }
        return count;
    }

    @PostMapping("/{projectCode}/chapters/list")
    public Response<List<VolumeChapterGroupResponseDTO>> listChaptersByVolume(
            @PathVariable String projectCode
    ) {
        return Response.success(projectService
                .listChaptersByVolume(projectCode).stream()
                .map(this::toVolumeChapterGroupResponse)
                .toList());
    }

    @GetMapping("/{projectCode}")
    public Response<NovelProjectResponseDTO> getProject(
            @PathVariable String projectCode
    ) {
        return Response.success(toProjectResponse(
                projectService.getProject(projectCode)
        ));
    }

    private NovelProjectResponseDTO toProjectResponse(NovelProjectVO project) {
        return new NovelProjectResponseDTO(
                project.projectCode(),
                project.title(),
                project.genre(),
                project.targetChapterCount(),
                project.wordsPerChapter(),
                project.currentChapterNumber(),
                project.status()
        );
    }

    private VolumeChapterGroupResponseDTO toVolumeChapterGroupResponse(
            VolumeChapterGroupVO group
    ) {
        return new VolumeChapterGroupResponseDTO(
                group.volumeCode(),
                group.sequenceNo(),
                group.title(),
                group.startChapter(),
                group.endChapter(),
                group.status(),
                group.chapters().stream()
                        .map(this::toGeneratedChapterResponse)
                        .toList()
        );
    }

    private StoryBibleResponseDTO toBibleResponse(StoryBibleVO bible) {
        return new StoryBibleResponseDTO(
                bible.oneSentencePremise(),
                bible.coreTheme(),
                bible.mainConflict(),
                bible.endingDirection(),
                bible.worldBackground(),
                bible.powerSystemJson(),
                bible.hardRulesJson(),
                bible.styleGuide(),
                bible.status()
        );
    }

    private PlanningDraftResponseDTO toDraftResponse(PlanningDraftVO draft) {
        return new PlanningDraftResponseDTO(
                draft.draftId(), draft.draftType(), draft.payload(), draft.expiresAt()
        );
    }

    private StoryCharacterVO toCharacter(
            AddStoryCharacterRequestDTO character
    ) {
        return new StoryCharacterVO(
                null,
                character.name(),
                character.roleType(),
                character.gender(),
                character.ageDescription(),
                character.appearance(),
                character.personality(),
                character.backgroundStory(),
                character.note(),
                null,
                null,
                null
        );
    }

    private StoryCharacterVO toCharacter(
            String characterCode,
            UpdateStoryCharacterRequestDTO character
    ) {
        return new StoryCharacterVO(
                characterCode,
                character.name(),
                character.roleType(),
                character.gender(),
                character.ageDescription(),
                character.appearance(),
                character.personality(),
                character.backgroundStory(),
                character.note(),
                null,
                null,
                null
        );
    }

    private List<CharacterDraftVO> toCharacterDrafts(
            List<ConfirmCharacterDraftDTO> characters
    ) {
        if (characters == null) {
            return null;
        }
        return characters.stream()
                .map(character -> new CharacterDraftVO(
                        character.name(),
                        character.role(),
                        character.gender(),
                        character.ageDescription(),
                        character.appearance(),
                        character.personality(),
                        character.backgroundStory(),
                        character.note()
                ))
                .toList();
    }

    private GeneratedChapterResponseDTO toGeneratedChapterResponse(
            GeneratedChapterVO chapter
    ) {
        return new GeneratedChapterResponseDTO(
                chapter.chapterNumber(),
                chapter.title(),
                chapter.content(),
                chapter.wordCount(),
                chapter.status()
        );
    }

    private StoryCharacterResponseDTO toCharacterResponse(
            StoryCharacterVO character
    ) {
        return toCharacterResponse(character, null);
    }

    private StoryCharacterResponseDTO toCharacterResponse(
            StoryCharacterVO character,
            String warning
    ) {
        return new StoryCharacterResponseDTO(
                character.characterCode(),
                character.name(),
                character.roleType(),
                character.gender(),
                character.ageDescription(),
                character.personality(),
                character.appearance(),
                character.backgroundStory(),
                character.note(),
                character.currentStateJson(),
                character.lifeStatus(),
                character.status(),
                warning
        );
    }
}
