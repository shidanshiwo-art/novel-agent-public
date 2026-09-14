package cn.ninth.novel.trigger.http;

import cn.ninth.novel.api.dto.MemoryRetrievalContextItemResponseDTO;
import cn.ninth.novel.api.dto.MemoryRetrievalMetricsResponseDTO;
import cn.ninth.novel.domain.memory.adapter.repository.IMemoryRetrievalMetricsRepository;
import cn.ninth.novel.domain.memory.model.MemoryRetrievalContextItem;
import cn.ninth.novel.domain.memory.model.MemoryRetrievalMetricsQuery;
import cn.ninth.novel.domain.memory.model.MemoryRetrievalObservation;
import cn.ninth.novel.types.response.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/** 供实验脚本查询 Memory Retrieval 观测的内部只读接口。 */
@RestController
@RequestMapping("/api/v1/novels/projects")
public class NovelMemoryRetrievalMetricsController {

    private final IMemoryRetrievalMetricsRepository metricsRepository;

    public NovelMemoryRetrievalMetricsController(
            IMemoryRetrievalMetricsRepository metricsRepository) {
        this.metricsRepository = metricsRepository;
    }

    /** 按项目查询，可选按章节、profile、generation/run 过滤。 */
    @GetMapping("/{projectCode}/memory-retrieval-metrics")
    public Response<List<MemoryRetrievalMetricsResponseDTO>> find(
            @PathVariable String projectCode,
            @RequestParam(required = false) Integer chapterNumber,
            @RequestParam(required = false) String profile,
            @RequestParam(required = false) String generationId
    ) {
        return Response.success(metricsRepository.find(new MemoryRetrievalMetricsQuery(
                        projectCode,
                        chapterNumber,
                        parseProfile(profile),
                        generationId))
                .stream()
                .map(this::toResponse)
                .toList());
    }

    private cn.ninth.novel.domain.memory.model.MemoryProfile parseProfile(String profile) {
        if (profile == null || profile.isBlank()) {
            return null;
        }
        return cn.ninth.novel.domain.memory.model.MemoryProfile.valueOf(
                profile.trim().toUpperCase(Locale.ROOT));
    }

    private MemoryRetrievalMetricsResponseDTO toResponse(MemoryRetrievalObservation observation) {
        return new MemoryRetrievalMetricsResponseDTO(
                observation.projectCode(),
                observation.chapterNumber(),
                observation.profile().name(),
                observation.generationId(),
                observation.route().memoryMode(),
                observation.route().canonicalRequested(),
                observation.route().canonicalHit(),
                observation.route().legacyFallbackRequested(),
                observation.route().legacyFallbackHit(),
                observation.candidateCount(),
                observation.filteredCount(),
                observation.selectedCount(),
                observation.trimmedCount(),
                observation.retrievalLatencyMillis(),
                observation.estimatedTokens(),
                observation.notFoundCount(),
                observation.retrievalMissCount(),
                observation.intentionalTrimCount(),
                observation.budgetTrimCount(),
                observation.items().stream().map(this::toResponse).toList());
    }

    private MemoryRetrievalContextItemResponseDTO toResponse(MemoryRetrievalContextItem item) {
        return new MemoryRetrievalContextItemResponseDTO(
                item.itemId(),
                item.category().name(),
                item.sourceType(),
                item.sourceChapter(),
                item.canonicalOrLegacy(),
                item.estimatedTokens(),
                item.selected(),
                item.trimmed(),
                item.decision());
    }
}
