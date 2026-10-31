package com.hobeoung.edufactory.studio.tools;

import com.hobeoung.edufactory.common.Json;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolUnion;
import com.anthropic.models.messages.WebSearchTool20250305;
import com.anthropic.models.messages.WebSearchTool20260209;
import com.hobeoung.edufactory.config.EduProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 도구 정의는 이름 순으로 고정한다 — 바이트가 하나라도 바뀌면 프롬프트 캐시가 깨진다.
 * 웹 검색(서버 도구)은 맨 뒤에 붙고, 창의성 단계와 관계없이 항상 같은 정의로 보낸다 — 단계는 사용자 메시지의
 * &lt;conditions&gt; 로 전달한다 (단계마다 도구 목록을 바꾸면 캐시와 저장된 thinking 블록이 매번 무효가 된다).
 */
@Component
public class ToolRegistry {

  public static final String WEB_SEARCH = "web_search";

  /** 서버 도구 단계 칩 문구 — web_search 의 동적 필터링이 내부적으로 code_execution 을 돌린다 */
  private static final Map<String, String> SERVER_TOOL_LABELS = Map.of(
      WEB_SEARCH, "웹 검색",
      "web_fetch", "웹 페이지 읽기",
      "code_execution", "검색 결과 걸러내기",
      "bash_code_execution", "검색 결과 걸러내기",
      "text_editor_code_execution", "검색 결과 걸러내기");

  private final Map<String, AgentTool> tools = new LinkedHashMap<>();
  private final List<ToolUnion> definitions;
  private final EduProperties.Ai ai;

  public ToolRegistry(List<AgentTool> beans, EduProperties props) {
    this.ai = props.ai();
    List<AgentTool> sorted = new ArrayList<>(beans);
    sorted.sort(Comparator.comparing(AgentTool::name));
    List<ToolUnion> defs = new ArrayList<>();
    for (AgentTool tool : sorted) {
      tools.put(tool.name(), tool);
      defs.add(ToolUnion.ofTool(definition(tool)));
    }
    this.definitions = List.copyOf(defs);
  }

  public List<ToolUnion> definitions() {
    return definitions;
  }

  /** 대화 생성 요청에 보낼 도구 — 자료 도구 + (켜져 있으면) 웹 검색 */
  public List<ToolUnion> definitions(String model) {
    if (!ai.webSearchEnabled()) {
      return definitions;
    }
    List<ToolUnion> all = new ArrayList<>(definitions);
    all.add(webSearch(model, ai.webSearchMaxUses()));
    return List.copyOf(all);
  }

  /** 동적 필터링(20260209)은 Opus·Sonnet 4.6 이후만 받는다 — Haiku 는 기본 변형 */
  static ToolUnion webSearch(String model, int maxUses) {
    if (model != null && model.startsWith("claude-haiku")) {
      return ToolUnion.ofWebSearchTool20250305(WebSearchTool20250305.builder().maxUses((long) maxUses).build());
    }
    return ToolUnion.ofWebSearchTool20260209(WebSearchTool20260209.builder().maxUses((long) maxUses).build());
  }

  public boolean webSearchEnabled() {
    return ai.webSearchEnabled();
  }

  public AgentTool get(String name) {
    return tools.get(name);
  }

  public String label(String name) {
    AgentTool tool = tools.get(name);
    if (tool != null) {
      return tool.label();
    }
    return SERVER_TOOL_LABELS.getOrDefault(name, name);
  }

  static Tool definition(AgentTool tool) {
    Map<String, Object> schema = Json.read(tool.schema(), new TypeReference<LinkedHashMap<String, Object>>() {});
    @SuppressWarnings("unchecked")
    Map<String, Object> properties = (Map<String, Object>) schema.getOrDefault("properties", Map.of());
    Tool.InputSchema.Properties.Builder props = Tool.InputSchema.Properties.builder();
    properties.forEach((k, v) -> props.putAdditionalProperty(k, JsonValue.from(v)));
    Tool.InputSchema.Builder input = Tool.InputSchema.builder().properties(props.build());
    @SuppressWarnings("unchecked")
    List<String> required = (List<String>) schema.get("required");
    if (required != null) {
      input.required(required);
    }
    input.putAdditionalProperty("additionalProperties", JsonValue.from(false));
    Tool.Builder builder = Tool.builder()
        .name(tool.name())
        .description(tool.description())
        .inputSchema(input.build());
    if (tool.strict()) {
      builder.strict(true);
    }
    return builder.build();
  }
}
