package cn.ninth.novel.api.dto;

public record ResumeChapterRequestDTO(
        String humanDecision,
        String revisionInstruction
) {
    public ResumeChapterRequestDTO(String humanDecision) {
        this(humanDecision, null);
    }
}
