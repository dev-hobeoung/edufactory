package com.hobeoung.edufactory.material;

import java.util.List;
import java.util.UUID;

/**
 * 청크 편집 필드. PATCH 에서는 null 인 필드를 그대로 둔다 (labelIds·choices·tags 도 null 이면 유지).
 * 구조화 결과(AI 제안 라벨 포함)도 같은 모양으로 넘긴다.
 */
public record ChunkInput(String chunkType, String title, String position, String itemNo, String questionType,
    String content, List<Choice> choices, String answer, String explanation, Double score, List<String> tags,
    List<UUID> labelIds, Integer pageFrom, Integer pageTo) {

  public static final List<String> CHUNK_TYPES = List.of("QUESTION", "PASSAGE", "CONCEPT", "EXPLANATION", "EXAMPLE",
      "SECTION", "OTHER");
  public static final List<String> QUESTION_TYPES = List.of("MULTIPLE_CHOICE", "MULTI_SELECT", "TRUE_FALSE",
      "SHORT_ANSWER", "FILL_BLANK", "ESSAY", "MATCHING", "ORDERING", "FLASHCARD");

  public static String chunkType(String v) {
    return v != null && CHUNK_TYPES.contains(v) ? v : "OTHER";
  }

  public static String questionType(String v) {
    return v != null && QUESTION_TYPES.contains(v) ? v : null;
  }
}
