package com.hobeoung.edufactory.ai;

import com.anthropic.core.JsonValue;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.errors.BadRequestException;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import com.fasterxml.jackson.databind.JsonNode;
import com.hobeoung.edufactory.common.Json;
import com.hobeoung.edufactory.usage.UsageService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 구조화 출력(output_config.format = json_schema) 한 번 호출. 출력이 길 수 있어 스트리밍으로 받아 모은다.
 * 스키마가 서버 문법 한도를 넘는 등 400 이면 스키마 없이 "JSON 만" 지시로 한 번 더 시도한다.
 */
@Component
public class StructuredCall {

  private static final Logger log = LoggerFactory.getLogger(StructuredCall.class);

  private final ClaudeClientProvider claude;
  private final UsageService usage;

  public StructuredCall(ClaudeClientProvider claude, UsageService usage) {
    this.claude = claude;
    this.usage = usage;
  }

  public JsonNode run(String model, String effort, String system, List<ContentBlockParam> content,
      Map<String, Object> schema, String purpose) {
    try {
      return call(model, effort, system, content, schema, purpose);
    } catch (BadRequestException e) {
      log.warn("구조화 출력 스키마 요청 실패 — 스키마 없이 재시도: {}", e.getMessage());
      List<ContentBlockParam> retry = new ArrayList<>(content);
      retry.add(ContentBlockParam.ofText(TextBlockParam.builder()
          .text("응답은 아래 JSON 스키마를 따르는 JSON 객체 하나만 출력하세요. 코드 블록·설명 없이.\n" + Json.write(schema))
          .build()));
      return call(model, effort, system, retry, null, purpose);
    }
  }

  private JsonNode call(String model, String effort, String system, List<ContentBlockParam> content,
      Map<String, Object> schema, String purpose) {
    OutputConfig.Builder output = OutputConfig.builder().effort(OutputConfig.Effort.of(effort));
    if (schema != null) {
      JsonOutputFormat.Schema.Builder s = JsonOutputFormat.Schema.builder();
      schema.forEach((k, v) -> s.putAdditionalProperty(k, JsonValue.from(v)));
      output.format(JsonOutputFormat.builder().schema(s.build()).build());
    }
    MessageCreateParams params = MessageCreateParams.builder()
        .model(model)
        .maxTokens(64000L)
        .system(system)
        .addUserMessageOfBlockParams(content)
        .outputConfig(output.build())
        .build();
    MessageAccumulator acc = MessageAccumulator.create();
    try (StreamResponse<RawMessageStreamEvent> stream = claude.get().messages().createStreaming(params)) {
      stream.stream().forEach(acc::accumulate);
    }
    Message message = acc.message();
    usage.record(message.model().asString(), purpose, new UsageService.Tokens(message.usage().inputTokens(),
        message.usage().outputTokens(), message.usage().cacheReadInputTokens().orElse(0L),
        message.usage().cacheCreationInputTokens().orElse(0L), 0));
    StopReason stop = message.stopReason().orElse(StopReason.END_TURN);
    if (StopReason.REFUSAL.equals(stop)) {
      throw new IllegalStateException("모델이 이 자료의 구조화를 거절했습니다");
    }
    if (StopReason.MAX_TOKENS.equals(stop)) {
      throw new IllegalStateException("구조화 출력이 너무 길어 잘렸습니다 (자료를 나눠 올려 주세요)");
    }
    StringBuilder text = new StringBuilder();
    for (ContentBlock block : message.content()) {
      block.text().ifPresent(t -> text.append(t.text()));
    }
    return parseJson(text.toString());
  }

  /** 코드 블록·앞뒤 설명이 섞여 와도 첫 '{' ~ 마지막 '}' 를 JSON 으로 읽는다 */
  static JsonNode parseJson(String text) {
    int start = text.indexOf('{');
    int end = text.lastIndexOf('}');
    if (start < 0 || end <= start) {
      throw new IllegalStateException("구조화 응답에서 JSON 을 찾지 못했습니다");
    }
    return Json.read(text.substring(start, end + 1));
  }
}
