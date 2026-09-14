package cn.ninth.novel.infrastructure.adapter.repository;

import cn.ninth.novel.domain.memory.adapter.repository.IMemoryRetrievalMetricsRepository;
import cn.ninth.novel.domain.memory.model.MemoryProfile;
import cn.ninth.novel.domain.memory.model.MemoryRetrievalContextItem;
import cn.ninth.novel.domain.memory.model.MemoryRetrievalMetricsQuery;
import cn.ninth.novel.domain.memory.model.MemoryRetrievalObservation;
import cn.ninth.novel.domain.memory.model.MemoryRetrievalRoute;
import cn.ninth.novel.infrastructure.dao.IMemoryRetrievalMetricsDao;
import cn.ninth.novel.infrastructure.dao.po.MemoryRetrievalMetricsPO;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/** MyBatis Memory Retrieval 观测仓储。 */
@Repository
public class MemoryRetrievalMetricsRepository implements IMemoryRetrievalMetricsRepository {

    private final IMemoryRetrievalMetricsDao metricsDao;
    private final ObjectMapper objectMapper;

    public MemoryRetrievalMetricsRepository(
            IMemoryRetrievalMetricsDao metricsDao,
            ObjectMapper objectMapper
    ) {
        this.metricsDao = metricsDao;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(MemoryRetrievalObservation observation) {
        if (observation == null) {
            return;
        }
        MemoryRetrievalMetricsPO po = new MemoryRetrievalMetricsPO();
        po.setProjectCode(observation.projectCode());
        po.setChapterNumber(observation.chapterNumber());
        po.setProfile(observation.profile().name());
        po.setGenerationId(observation.generationId());
        po.setMemoryMode(observation.route().memoryMode());
        po.setCanonicalRequested(observation.route().canonicalRequested());
        po.setCanonicalHit(observation.route().canonicalHit());
        po.setLegacyFallbackRequested(observation.route().legacyFallbackRequested());
        po.setLegacyFallbackHit(observation.route().legacyFallbackHit());
        po.setCandidateCount(observation.candidateCount());
        po.setFilteredCount(observation.filteredCount());
        po.setSelectedCount(observation.selectedCount());
        po.setTrimmedCount(observation.trimmedCount());
        po.setRetrievalLatencyMs(observation.retrievalLatencyMillis());
        po.setEstimatedTokens(observation.estimatedTokens());
        po.setNotFoundCount(observation.notFoundCount());
        po.setRetrievalMissCount(observation.retrievalMissCount());
        po.setIntentionalTrimCount(observation.intentionalTrimCount());
        po.setBudgetTrimCount(observation.budgetTrimCount());
        po.setContextItemsJson(objectMapper.writeValueAsString(observation.items()));
        metricsDao.insert(po);
    }

    @Override
    public List<MemoryRetrievalObservation> find(MemoryRetrievalMetricsQuery query) {
        return metricsDao.query(
                        query.projectCode(),
                        query.chapterNumber(),
                        query.profile() == null ? null : query.profile().name(),
                        query.generationId())
                .stream()
                .map(this::toObservation)
                .toList();
    }

    private MemoryRetrievalObservation toObservation(MemoryRetrievalMetricsPO po) {
        List<MemoryRetrievalContextItem> items = readItems(po.getContextItemsJson());
        return new MemoryRetrievalObservation(
                po.getProjectCode(),
                po.getChapterNumber(),
                MemoryProfile.valueOf(po.getProfile()),
                po.getGenerationId(),
                new MemoryRetrievalRoute(
                        po.getMemoryMode(),
                        Boolean.TRUE.equals(po.getCanonicalRequested()),
                        Boolean.TRUE.equals(po.getCanonicalHit()),
                        Boolean.TRUE.equals(po.getLegacyFallbackRequested()),
                        Boolean.TRUE.equals(po.getLegacyFallbackHit())),
                value(po.getCandidateCount()),
                value(po.getFilteredCount()),
                value(po.getSelectedCount()),
                value(po.getTrimmedCount()),
                value(po.getRetrievalLatencyMs()),
                value(po.getEstimatedTokens()),
                value(po.getNotFoundCount()),
                value(po.getRetrievalMissCount()),
                value(po.getIntentionalTrimCount()),
                value(po.getBudgetTrimCount()),
                items);
    }

    private List<MemoryRetrievalContextItem> readItems(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            MemoryRetrievalContextItem[] items = objectMapper.readValue(
                    json, MemoryRetrievalContextItem[].class);
            return items == null ? List.of() : List.of(items);
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private long value(Long value) {
        return value == null ? 0L : value;
    }
}
