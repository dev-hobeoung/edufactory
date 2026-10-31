package com.hobeoung.edufactory.material;

import com.hobeoung.edufactory.label.LabelRef;
import java.util.List;
import java.util.UUID;

public record MaterialSummary(UUID id, String title, String kind, String description, String sourceType,
    String fileName, Long fileSize, boolean isExam, String status, String statusMessage, String error,
    List<LabelRef> labels, int chunkCount, int embeddedCount, int scoreSetCount, String structuredBy,
    String createdAt, String updatedAt) {

  public static final List<String> KINDS = List.of("EXAM", "WORKSHEET", "TEXTBOOK", "NOTE", "REFERENCE", "OTHER");

  public static String kind(String v) {
    return v != null && KINDS.contains(v) ? v : "OTHER";
  }
}
