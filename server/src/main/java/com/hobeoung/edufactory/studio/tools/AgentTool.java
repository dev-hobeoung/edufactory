package com.hobeoung.edufactory.studio.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.hobeoung.edufactory.common.Json;
import com.hobeoung.edufactory.context.Conditions;
import com.hobeoung.edufactory.document.DocumentVersion;
import java.util.UUID;

/** Claude 가 호출하는 도구 하나. 결과는 JSON 문자열 */
public interface AgentTool {

  String name();

  /** 화면 단계 칩 문구 */
  String label();

  String description();

  /** input_schema JSON */
  String schema();

  default boolean strict() {
    return false;
  }

  Result run(JsonNode input, Context ctx);

  record Context(UUID conversationId, UUID userMessageId, String requestText, Conditions conditions) {}

  /**
   * @param content 모델에 돌려줄 결과 (JSON 문자열)
   * @param summary 단계 칩 요약
   * @param document submit_document 가 만든 버전 (화면 갱신용)
   */
  record Result(String content, String summary, boolean error, DocumentVersion document) {

    public static Result ok(Object content, String summary) {
      return new Result(content instanceof String s ? s : Json.write(content), summary, false, null);
    }

    public static Result error(String message) {
      return new Result("{\"error\":" + Json.write(message) + "}", message, true, null);
    }
  }
}
