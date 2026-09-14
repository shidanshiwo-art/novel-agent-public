package cn.ninth.novel.domain.memory.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Candidate 所绑定的正文版本。
 *
 * <p>{@code chapterVersion} 区分同一章节的 DRAFT、REVISE 和重试结果，
 * {@code contentHash} 用于确认候选没有脱离产生它的正文。</p>
 */
public record MemorySourceVersion(
        String chapterVersion,
        String contentHash
) {

    public MemorySourceVersion {
        requireText(chapterVersion, "chapterVersion");
        requireText(contentHash, "contentHash");
    }

    /** 根据正文创建一个新的版本绑定。 */
    public static MemorySourceVersion create(String chapterVersion, String content) {
        requireText(chapterVersion, "chapterVersion");
        requireContent(content);
        return new MemorySourceVersion(chapterVersion, hash(content));
    }

    /** 使用随机版本号创建正文版本，适合每次 DRAFT/REVISE 成功产出。 */
    public static MemorySourceVersion create(String content) {
        return create(java.util.UUID.randomUUID().toString(), content);
    }

    public boolean matchesContent(String content) {
        return content != null && contentHash.equals(hash(content));
    }

    public void requireMatches(String content) {
        requireContent(content);
        if (!matchesContent(content)) {
            throw new IllegalArgumentException("contentHash 与正文不匹配");
        }
    }

    public String getChapterVersion() {
        return chapterVersion;
    }

    public String getContentHash() {
        return contentHash;
    }

    public static String hash(String content) {
        requireContent(content);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行时不支持 SHA-256", exception);
        }
    }

    private static void requireContent(String content) {
        if (content == null || content.isEmpty()) {
            throw new IllegalArgumentException("正文不能为空");
        }
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
    }
}
