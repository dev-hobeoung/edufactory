package com.hobeoung.edufactory.context;

import java.util.List;
import java.util.UUID;

public record ContextRule(UUID id, String name, String description, boolean enabled, int priority,
    Condition condition, Injection injection, String createdAt, String updatedAt) {

  public record Clause(String field, String op, List<String> values) {
    public Clause {
      values = values == null ? List.of() : values.stream().filter(v -> v != null && !v.isBlank()).map(String::strip)
          .toList();
    }
  }

  public record Condition(String match, List<Clause> clauses) {
    public Condition {
      match = "ANY".equals(match) ? "ANY" : "ALL";
      clauses = clauses == null ? List.of() : List.copyOf(clauses);
    }
  }

  public record Retrieval(String query, List<UUID> labelIds, List<String> chunkTypes, int topK) {
    public Retrieval {
      query = query == null || query.isBlank() ? null : query.strip();
      labelIds = labelIds == null ? List.of() : List.copyOf(labelIds);
      chunkTypes = chunkTypes == null ? List.of() : List.copyOf(chunkTypes);
      topK = topK <= 0 ? 5 : Math.min(20, topK);
    }
  }

  public record Injection(String instruction, List<UUID> materialIds, Retrieval retrieval, int maxChars) {
    public Injection {
      instruction = instruction == null || instruction.isBlank() ? null : instruction.strip();
      materialIds = materialIds == null ? List.of() : List.copyOf(materialIds);
      maxChars = maxChars <= 0 ? 8000 : Math.min(60000, maxChars);
    }
  }

  /** 화면 칩·턴 기록용 */
  public record Applied(UUID id, String name, int materialCount, int chunkCount, int chars) {}
}
