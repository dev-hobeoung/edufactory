package com.hobeoung.edufactory.studio.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hobeoung.edufactory.common.Json;
import com.hobeoung.edufactory.label.LabelRef;
import com.hobeoung.edufactory.material.Choice;
import com.hobeoung.edufactory.material.Chunk;
import com.hobeoung.edufactory.material.ChunkLabel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 도구 결과에 넣는 청크·통계 모양 (모델용으로 짧게) */
final class ToolViews {

  private ToolViews() {}

  static ObjectNode chunk(Chunk c, String materialTitle, int maxContent) {
    ObjectNode n = Json.object();
    n.put("ref", "chunk:" + c.id());
    n.put("materialId", c.materialId().toString());
    if (materialTitle != null) {
      n.put("material", materialTitle);
    }
    n.put("chunkType", c.chunkType());
    putIf(n, "position", c.position());
    putIf(n, "title", c.title());
    putIf(n, "itemNo", c.itemNo());
    putIf(n, "questionType", c.questionType());
    n.put("content", cut(c.content(), maxContent));
    if (!c.choices().isEmpty()) {
      ArrayNode choices = n.putArray("choices");
      for (Choice ch : c.choices()) {
        choices.add(ch.label() + " " + ch.text());
      }
    }
    putIf(n, "answer", c.answer());
    if (c.explanation() != null) {
      n.put("explanation", cut(c.explanation(), Math.max(300, maxContent / 2)));
    }
    if (c.score() != null) {
      n.put("score", c.score());
    }
    if (!c.tags().isEmpty()) {
      n.set("tags", Json.MAPPER.valueToTree(c.tags()));
    }
    if (!c.labels().isEmpty()) {
      n.set("labels", Json.MAPPER.valueToTree(c.labels().stream().map(ToolViews::label).toList()));
    }
    if (c.stat() != null) {
      n.set("stat", stat(c.stat()));
    }
    return n;
  }

  static String label(ChunkLabel l) {
    return l.group() + "/" + l.name();
  }

  static List<String> labels(List<LabelRef> labels) {
    List<String> out = new ArrayList<>();
    labels.forEach(l -> out.add(l.group() + "/" + l.name()));
    return out;
  }

  /** 문항 통계 요약 — 정답률·변별도·응답 수 */
  static ObjectNode stat(JsonNode s) {
    ObjectNode n = Json.object();
    n.put("n", s.path("n").asInt());
    n.put("correctRate", s.path("avgRate").asDouble());
    n.put("fullRate", s.path("fullRate").asDouble());
    n.put("zeroRate", s.path("zeroRate").asDouble());
    if (s.hasNonNull("discrimination")) {
      n.put("discrimination", s.path("discrimination").asDouble());
    }
    if (s.path("smallSample").asBoolean(false)) {
      n.put("smallSample", true);
    }
    JsonNode dist = s.path("choiceDist");
    if (dist.isArray() && !dist.isEmpty()) {
      ArrayNode d = n.putArray("responses");
      dist.forEach(c -> d.add(c.path("response").asText() + (c.path("correct").asBoolean() ? "(정답)" : "") + ":"
          + c.path("count").asInt()));
    }
    return n;
  }

  static void putIf(ObjectNode n, String key, String value) {
    if (value != null && !value.isBlank()) {
      n.put(key, value);
    }
  }

  static String cut(String s, int max) {
    if (s == null) {
      return "";
    }
    return s.length() > max ? s.substring(0, max) + "…(생략, get_chunks 로 전체 보기)" : s;
  }

  static List<UUID> uuids(JsonNode arr) {
    List<UUID> out = new ArrayList<>();
    if (arr != null && arr.isArray()) {
      for (JsonNode v : arr) {
        String s = v.asText().replaceFirst("^(chunk|material|label):", "");
        try {
          out.add(UUID.fromString(s));
        } catch (IllegalArgumentException ignored) {
          // 잘못된 id 는 건너뛴다
        }
      }
    }
    return out;
  }

  static List<String> strings(JsonNode arr) {
    List<String> out = new ArrayList<>();
    if (arr != null && arr.isArray()) {
      arr.forEach(v -> {
        if (!v.asText().isBlank()) {
          out.add(v.asText());
        }
      });
    }
    return out;
  }
}
