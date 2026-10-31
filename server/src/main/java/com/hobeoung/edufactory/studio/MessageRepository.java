package com.hobeoung.edufactory.studio;

import com.anthropic.core.ObjectMappers;
import com.anthropic.models.messages.MessageParam;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hobeoung.edufactory.common.Json;
import com.hobeoung.edufactory.common.Sql;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 대화 메시지. content 는 Claude 에 보낸 MessageParam 그대로, display 는 화면용 요약.
 * 이어 붙이기만 한다 — 과거 메시지를 고치면 thinking 블록이 무효가 된다.
 */
@Repository
public class MessageRepository {

  public static final String KIND_CHAT = "CHAT";
  public static final String KIND_ANSWER = "ANSWER";
  public static final String KIND_TOOL_RESULT = "TOOL_RESULT";

  public record Usage(String model, long input, long output, long cacheRead, long cacheWrite, double costUsd) {}

  private final NamedParameterJdbcTemplate jdbc;

  public MessageRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public static ObjectNode display() {
    return Json.object();
  }

  public UUID append(UUID conversationId, String role, String kind, MessageParam content, JsonNode display,
      Usage usage) {
    UUID id = UUID.randomUUID();
    jdbc.update("""
        INSERT INTO ef_message (id, conversation_id, role, kind, content, display, model, input_tokens, output_tokens,
                                cache_read_tokens, cache_write_tokens, cost_usd)
        VALUES (:id, :c, :role, :kind, CAST(:content AS jsonb), CAST(:display AS jsonb), :model, :in, :out, :cr, :cw,
                :cost)
        """, new MapSqlParameterSource().addValue("id", id).addValue("c", conversationId).addValue("role", role)
        .addValue("kind", kind).addValue("content", toJson(content)).addValue("display", Json.write(display))
        .addValue("model", usage == null ? null : usage.model())
        .addValue("in", usage == null ? null : usage.input())
        .addValue("out", usage == null ? null : usage.output())
        .addValue("cr", usage == null ? null : usage.cacheRead())
        .addValue("cw", usage == null ? null : usage.cacheWrite())
        .addValue("cost", usage == null ? null : usage.costUsd()));
    jdbc.update("UPDATE ef_conversation SET updated_at = now() WHERE id = :c", Map.of("c", conversationId));
    return id;
  }

  public List<MessageParam> history(UUID conversationId) {
    return jdbc.query("SELECT content::text AS content FROM ef_message WHERE conversation_id = :c ORDER BY seq",
        Map.of("c", conversationId), (rs, i) -> fromJson(rs.getString("content")));
  }

  record Row(String id, String role, String kind, JsonNode display, String createdAt) {}

  List<Row> rows(UUID conversationId) {
    return jdbc.query("SELECT id, role, kind, display::text AS display, created_at FROM ef_message"
            + " WHERE conversation_id = :c ORDER BY seq", Map.of("c", conversationId),
        (rs, i) -> new Row(rs.getString("id"), rs.getString("role"), rs.getString("kind"),
            Json.read(rs.getString("display")), Sql.instant(rs, "created_at")));
  }

  /**
   * 화면용 턴: 사용자 입력 하나가 user 턴, 다음 입력 전까지의 assistant·도구 결과를 묶어 assistant 턴 하나.
   */
  public List<Map<String, Object>> turns(UUID conversationId) {
    List<Map<String, Object>> turns = new ArrayList<>();
    Map<String, Object> current = null;
    for (Row row : rows(conversationId)) {
      JsonNode d = row.display();
      if (KIND_CHAT.equals(row.kind())) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", row.id());
        user.put("role", "user");
        user.put("text", d.path("text").asText(""));
        user.put("thinking", null);
        user.put("tools", List.of());
        user.put("context", List.of());
        user.put("documentVersions", List.of());
        user.put("conditions", d.has("conditions") ? d.get("conditions") : null);
        user.put("stopped", false);
        user.put("createdAt", row.createdAt());
        turns.add(user);
        current = null;
        continue;
      }
      if (current == null) {
        current = new LinkedHashMap<>();
        current.put("id", row.id());
        current.put("role", "assistant");
        current.put("text", "");
        current.put("thinking", null);
        current.put("tools", Json.array());
        current.put("context", Json.array());
        current.put("documentVersions", Json.array());
        current.put("conditions", null);
        current.put("stopped", false);
        current.put("createdAt", row.createdAt());
        turns.add(current);
      }
      String text = d.path("text").asText("");
      if (!text.isBlank()) {
        String prev = (String) current.get("text");
        current.put("text", prev.isBlank() ? text : prev + "\n\n" + text);
      }
      String thinking = d.path("thinking").asText("");
      if (!thinking.isBlank()) {
        String prev = (String) current.get("thinking");
        current.put("thinking", prev == null ? thinking : prev + "\n\n" + thinking);
      }
      mergeTools((ArrayNode) current.get("tools"), d.path("tools"));
      if (d.path("context").isArray()) {
        ((ArrayNode) current.get("context")).addAll((ArrayNode) d.get("context"));
      }
      if (d.path("documentVersions").isArray()) {
        ((ArrayNode) current.get("documentVersions")).addAll((ArrayNode) d.get("documentVersions"));
      }
      if (d.path("stopped").asBoolean(false)) {
        current.put("stopped", true);
      }
    }
    return turns;
  }

  /** 도구 단계는 id 기준으로 최신 상태로 덮어쓴다 (started → done) */
  private static void mergeTools(ArrayNode target, JsonNode incoming) {
    if (!incoming.isArray()) {
      return;
    }
    for (JsonNode tool : incoming) {
      boolean replaced = false;
      for (int i = 0; i < target.size(); i++) {
        if (target.get(i).path("id").asText().equals(tool.path("id").asText())) {
          if (tool.path("name").asText().isEmpty()) {
            ObjectNode merged = ((ObjectNode) target.get(i)).deepCopy();
            merged.set("status", tool.get("status"));
            merged.set("summary", tool.get("summary"));
            target.set(i, merged);
          } else {
            target.set(i, tool);
          }
          replaced = true;
          break;
        }
      }
      if (!replaced) {
        target.add(tool);
      }
    }
  }

  static String toJson(MessageParam param) {
    try {
      return ObjectMappers.jsonMapper().writeValueAsString(param);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("메시지 직렬화 실패", e);
    }
  }

  static MessageParam fromJson(String json) {
    try {
      return ObjectMappers.jsonMapper().readValue(json, MessageParam.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("메시지 역직렬화 실패", e);
    }
  }
}
