package com.hobeoung.edufactory.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/** DB jsonb 컬럼·도구 입출력용 공용 ObjectMapper. */
public final class Json {

  public static final ObjectMapper MAPPER = new ObjectMapper()
      .registerModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  private Json() {}

  public static String write(Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("JSON 변환 실패", e);
    }
  }

  public static JsonNode read(String json) {
    if (json == null || json.isBlank()) {
      return MAPPER.nullNode();
    }
    try {
      return MAPPER.readTree(json);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("JSON 파싱 실패", e);
    }
  }

  public static <T> T read(String json, Class<T> type) {
    try {
      return MAPPER.readValue(json, type);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("JSON 파싱 실패: " + type.getSimpleName(), e);
    }
  }

  public static <T> T read(String json, TypeReference<T> type) {
    try {
      return MAPPER.readValue(json, type);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("JSON 파싱 실패", e);
    }
  }

  public static <T> T convert(Object value, Class<T> type) {
    return MAPPER.convertValue(value, type);
  }

  public static <T> T convert(Object value, TypeReference<T> type) {
    return MAPPER.convertValue(value, type);
  }

  public static ObjectNode object() {
    return MAPPER.createObjectNode();
  }

  public static ArrayNode array() {
    return MAPPER.createArrayNode();
  }

  /** null·빈 문자열을 null 로 */
  public static String text(JsonNode node, String field) {
    JsonNode v = node == null ? null : node.get(field);
    if (v == null || v.isNull()) {
      return null;
    }
    String s = v.asText();
    return s.isBlank() ? null : s;
  }
}
