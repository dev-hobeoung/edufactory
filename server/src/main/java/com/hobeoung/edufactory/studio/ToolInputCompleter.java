package com.hobeoung.edufactory.studio;

import com.anthropic.core.JsonValue;
import com.anthropic.core.ObjectMappers;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.models.messages.InputJsonDelta;
import com.anthropic.models.messages.RawContentBlockDeltaEvent;
import com.anthropic.models.messages.RawContentBlockStartEvent;
import com.anthropic.models.messages.RawMessageStreamEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * SDK MessageAccumulator 는 tool_use·server_tool_use 블록이 끝날 때 input_json_delta 를 하나도 받지 못했으면
 * "Missing input JSON for index N" 으로 실패한다. 웹 검색 동적 필터링의 code_execution 같은 서버 도구 호출은 입력을
 * content_block_start 에 통째로 담고 delta 없이 끝나기도 하므로, 그런 블록은 시작 이벤트의 입력을 delta 하나로 채워 넘긴다.
 */
final class ToolInputCompleter {

  /** 블록 index → content_block_start 에 담겨 온 입력 JSON */
  private final Map<Long, String> startInputs = new HashMap<>();
  private final Set<Long> gotDelta = new HashSet<>();

  void accumulate(MessageAccumulator acc, RawMessageStreamEvent event) {
    event.contentBlockStart().ifPresent(start -> {
      RawContentBlockStartEvent.ContentBlock block = start.contentBlock();
      block.toolUse().ifPresent(use -> startInputs.put(start.index(), json(use._input())));
      block.serverToolUse().ifPresent(use -> startInputs.put(start.index(), json(use._input())));
    });
    event.contentBlockDelta().ifPresent(delta -> {
      if (delta.delta().inputJson().isPresent()) {
        gotDelta.add(delta.index());
      }
    });
    event.contentBlockStop().ifPresent(stop -> {
      String input = startInputs.remove(stop.index());
      if (input != null && !gotDelta.remove(stop.index())) {
        acc.accumulate(RawMessageStreamEvent.ofContentBlockDelta(RawContentBlockDeltaEvent.builder()
            .index(stop.index())
            .delta(InputJsonDelta.builder().partialJson(input).build())
            .build()));
      }
    });
    acc.accumulate(event);
  }

  private static String json(JsonValue input) {
    try {
      String s = ObjectMappers.jsonMapper().writeValueAsString(input);
      return s == null || s.isBlank() || "null".equals(s) ? "{}" : s;
    } catch (Exception e) {
      return "{}";
    }
  }
}
