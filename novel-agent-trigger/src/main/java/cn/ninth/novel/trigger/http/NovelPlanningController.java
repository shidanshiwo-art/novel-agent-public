package cn.ninth.novel.trigger.http;

import cn.ninth.novel.api.dto.*;
import cn.ninth.novel.domain.planning.model.valobj.*;
import cn.ninth.novel.domain.planning.service.IPlanningService;
import cn.ninth.novel.types.response.Response;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/novels/projects/{projectCode}")
public class NovelPlanningController {

    private final IPlanningService planningService;

    public NovelPlanningController(IPlanningService planningService) {
        this.planningService = planningService;
    }

    @GetMapping("/chapter-plans")
    public Response<List<ChapterPlanResponseDTO>> listChapterPlans(
            @PathVariable String projectCode
    ) {
        return Response.success(planningService.listChapterPlans(projectCode).stream()
                .map(this::toChapterPlanResponse)
                .toList());
    }

    @PostMapping("/chapter-plans/{chapterNumber}")
    public Response<ChapterPlanResponseDTO> updateChapterPlan(
            @PathVariable String projectCode,
            @PathVariable Integer chapterNumber,
            @RequestBody UpdateChapterPlanRequestDTO request
    ) {
        return Response.success(toChapterPlanResponse(
                planningService.updateChapterPlan(
                        projectCode,
                        chapterNumber,
                        new ChapterOutlineVO(
                                chapterNumber, null, request.title(), request.summary(), null
                        )
                )
        ));
    }

    @GetMapping("/outlines/tree")
    public Response<List<OutlineNodeResponseDTO>> listOutlines(
            @PathVariable String projectCode
    ) {
        return Response.success(planningService.listOutlineTree(projectCode).stream()
                .map(this::toOutlineResponse)
                .toList());
    }

    @PostMapping("/outlines/nodes")
    public Response<OutlineNodeResponseDTO> createOutlineNode(
            @PathVariable String projectCode,
            @RequestBody CreateOutlineNodeRequestDTO request
    ) {
        return Response.success(toOutlineResponse(
                planningService.createOutlineNode(projectCode, new OutlineNodeVO(
                        null, request.parentNodeCode(), null, null,
                        request.title(), request.summary(),
                        request.startChapter(), request.endChapter(), null
                ))
        ));
    }

    @PostMapping("/outlines/nodes/{nodeCode}")
    public Response<OutlineNodeResponseDTO> updateOutlineNode(
            @PathVariable String projectCode,
            @PathVariable String nodeCode,
            @RequestBody UpdateOutlineNodeRequestDTO request
    ) {
        return Response.success(toOutlineResponse(
                planningService.updateOutlineNode(projectCode, new OutlineNodeVO(
                        nodeCode, request.parentNodeCode(), toNodeKind(request.nodeKind()),
                        request.sequenceNo(), request.title(), request.summary(),
                        request.startChapter(), request.endChapter(), request.status()
                ))
        ));
    }

    @PostMapping("/outlines/nodes/{nodeCode}/reorder")
    public Response<OutlineNodeResponseDTO> reorderOutlineNode(
            @PathVariable String projectCode,
            @PathVariable String nodeCode,
            @RequestBody ReorderOutlineNodeRequestDTO request
    ) {
        return Response.success(toOutlineResponse(
                planningService.reorderOutlineNode(
                        projectCode, nodeCode, request.targetSequence()
                )
        ));
    }

    @PostMapping("/outlines/nodes/{nodeCode}/delete")
    public Response<Void> deleteOutlineNode(
            @PathVariable String projectCode,
            @PathVariable String nodeCode
    ) {
        planningService.deleteOutlineNode(projectCode, nodeCode);
        return Response.success(null);
    }

    @PostMapping("/outlines/root/generate")
    public Response<PlanningDraftResponseDTO> generateRootOutline(
            @PathVariable String projectCode,
            @RequestBody GenerateRootOutlineRequestDTO request
    ) {
        return Response.success(toDraftResponse(
                planningService.generateRootOutline(projectCode, request.requirement())
        ));
    }

    @PostMapping("/outlines/root/confirm")
    public Response<Void> confirmRootOutline(
            @PathVariable String projectCode,
            @RequestBody ConfirmRootOutlineRequestDTO request
    ) {
        planningService.confirmRootOutline(
                projectCode,
                request.draftId(),
                request.summary()
        );
        return Response.success(null);
    }

    @PostMapping("/outlines/{bookNodeCode}/volumes/create")
    public Response<OutlineNodeResponseDTO> createNextVolume(
            @PathVariable String projectCode,
            @PathVariable String bookNodeCode
    ) {
        return Response.success(toOutlineResponse(
                planningService.createNextVolume(projectCode, bookNodeCode)
        ));
    }

    @PostMapping("/outlines/{parentNodeCode}/children/generate")
    public Response<PlanningDraftResponseDTO> generateNextOutline(
            @PathVariable String projectCode,
            @PathVariable String parentNodeCode,
            @RequestBody GenerateNextOutlineRequestDTO request
    ) {
        return Response.success(toDraftResponse(
                planningService.generateNextOutline(
                        projectCode,
                        parentNodeCode,
                        request.requirement()
                )
        ));
    }

    @PostMapping("/outlines/{parentNodeCode}/children/confirm")
    public Response<Void> confirmNextOutline(
            @PathVariable String projectCode,
            @PathVariable String parentNodeCode,
            @RequestBody ConfirmNextOutlineRequestDTO request
    ) {
        planningService.confirmNextOutline(
                projectCode,
                parentNodeCode,
                request.draftId(),
                request.title(),
                request.summary()
        );
        return Response.success(null);
    }

    @PostMapping("/outlines/volumes/{volumeNodeCode}/generate")
    public Response<PlanningDraftResponseDTO> generateVolumeOutline(
            @PathVariable String projectCode,
            @PathVariable String volumeNodeCode,
            @RequestBody GenerateNextOutlineRequestDTO request
    ) {
        return Response.success(toDraftResponse(
                planningService.generateVolumeOutline(
                        projectCode, volumeNodeCode, request.requirement()
                )
        ));
    }

    @PostMapping("/outlines/volumes/{volumeNodeCode}/confirm")
    public Response<Void> confirmVolumeOutline(
            @PathVariable String projectCode,
            @PathVariable String volumeNodeCode,
            @RequestBody ConfirmNextOutlineRequestDTO request
    ) {
        planningService.confirmVolumeOutline(
                projectCode,
                volumeNodeCode,
                request.draftId(),
                request.title(),
                request.summary()
        );
        return Response.success(null);
    }

    @PostMapping("/outlines/nodes/{nodeCode}/regenerate")
    public Response<PlanningDraftResponseDTO> generateArcRegeneration(
            @PathVariable String projectCode,
            @PathVariable String nodeCode,
            @RequestBody GenerateArcRegenerationRequestDTO request
    ) {
        return Response.success(toDraftResponse(
                planningService.generateArcRegeneration(
                        projectCode, nodeCode, request.requirement()
                )
        ));
    }

    @PostMapping("/outlines/nodes/{nodeCode}/regenerate/confirm")
    public Response<Void> confirmArcRegeneration(
            @PathVariable String projectCode,
            @PathVariable String nodeCode,
            @RequestBody ConfirmArcRegenerationRequestDTO request
    ) {
        planningService.confirmArcRegeneration(
                projectCode,
                nodeCode,
                request.draftId(),
                request.title(),
                request.summary()
        );
        return Response.success(null);
    }

    @PostMapping("/chapter-plans/{chapterNumber}/generate")
    public Response<PlanningDraftResponseDTO> generateChapterPlan(
            @PathVariable String projectCode,
            @PathVariable Integer chapterNumber,
            @RequestBody GenerateChapterPlanRequestDTO request
    ) {
        return Response.success(toDraftResponse(
                planningService.generateChapterPlan(
                        projectCode, chapterNumber, request.requirement()
                )
        ));
    }

    @PostMapping("/chapter-plans/{chapterNumber}/confirm")
    public Response<Void> confirmChapterPlan(
            @PathVariable String projectCode,
            @PathVariable Integer chapterNumber,
            @RequestBody ConfirmChapterPlanRequestDTO request
    ) {
        planningService.confirmChapterPlan(
                projectCode,
                chapterNumber,
                request.draftId(),
                request.title(),
                request.summary()
        );
        return Response.success(null);
    }

    private ChapterPlanResponseDTO toChapterPlanResponse(ChapterOutlineVO plan) {
        return new ChapterPlanResponseDTO(
                plan.chapterNumber(), plan.outlineNodeCode(), plan.title(), plan.summary(), plan.status()
        );
    }

    private PlanningDraftResponseDTO toDraftResponse(PlanningDraftVO draft) {
        return new PlanningDraftResponseDTO(
                draft.draftId(), draft.draftType(), draft.payload(), draft.expiresAt()
        );
    }

    private OutlineNodeResponseDTO toOutlineResponse(OutlineNodeVO node) {
        return new OutlineNodeResponseDTO(
                node.nodeCode(), node.parentNodeCode(),
                node.nodeKind() == null ? null : node.nodeKind().name(),
                node.sequenceNo(), node.startChapter(), node.endChapter(),
                node.title(), node.summary(), node.status()
        );
    }

    private cn.ninth.novel.domain.planning.model.valobj.enums.OutlineNodeKindEnum toNodeKind(
            String value
    ) {
        return value == null ? null
                : cn.ninth.novel.domain.planning.model.valobj.enums.OutlineNodeKindEnum.valueOf(value);
    }
}
