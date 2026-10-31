package com.hobeoung.edufactory.studio;

import com.hobeoung.edufactory.ai.ClaudeClientProvider;
import com.hobeoung.edufactory.common.ApiException;
import com.hobeoung.edufactory.common.Json;
import com.hobeoung.edufactory.config.EduProperties;
import com.hobeoung.edufactory.context.Conditions;
import com.hobeoung.edufactory.context.ContextRule;
import com.hobeoung.edufactory.context.ContextRuleService;
import com.hobeoung.edufactory.document.DocumentRepository;
import com.hobeoung.edufactory.document.DocumentSummary;
import com.hobeoung.edufactory.document.DocumentVersion;
import com.hobeoung.edufactory.label.LabelService;
import com.hobeoung.edufactory.material.MaterialRepository;
import com.hobeoung.edufactory.studio.tools.AgentTool;
import com.hobeoung.edufactory.studio.tools.ToolRegistry;
import com.hobeoung.edufactory.system.SettingsService;
import com.hobeoung.edufactory.usage.Pricing;
import com.hobeoung.edufactory.usage.UsageService;
import com.anthropic.core.JsonValue;
import com.anthropic.core.ObjectMappers;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.RawContentBlockStartEvent;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.ServerToolUseBlock;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.anthropic.models.messages.ToolChoiceNone;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.anthropic.models.messages.WebSearchToolResultBlock;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 대화 한 턴 실행: 사용자 메시지 저장 → (조건별 컨텍스트 규칙 주입) → Claude 스트리밍 ↔ 도구 실행 반복 → SSE 로 진행 중계.
 * 대화 기록은 이어 붙이기만 한다 (과거 메시지를 고치면 thinking 블록이 무효가 된다).
 */
@Service
public class AgentService {

  /** thinking.block_binding(prefix 불일치 시 drop_block) 을 보내기 위한 베타 */
  static final String BETA_THINKING_BINDING_CONTROLS = "thinking-binding-controls-2026-08-01";
  /** 거절 시 서버측 대체 모델 재시도 베타 */
  static final String BETA_SERVER_SIDE_FALLBACK = "server-side-fallback-2026-07-01";

  private static final Logger log = LoggerFactory.getLogger(AgentService.class);
  private static final int MAX_ROUNDS = 16;

  private final ClaudeClientProvider claude;
  private final ToolRegistry tools;
  private final MessageRepository store;
  private final ConversationRepository conversations;
  private final DocumentRepository documents;
  private final ContextRuleService rules;
  private final LabelService labels;
  private final MaterialRepository materials;
  private final SettingsService settings;
  private final UsageService usage;
  private final EduProperties.Ai ai;
  private final Map<UUID, RunHandle> running = new ConcurrentHashMap<>();

  public AgentService(ClaudeClientProvider claude, ToolRegistry tools, MessageRepository store,
      ConversationRepository conversations, DocumentRepository documents, ContextRuleService rules,
      LabelService labels, MaterialRepository materials, SettingsService settings, UsageService usage,
      EduProperties props) {
    this.claude = claude;
    this.tools = tools;
    this.store = store;
    this.conversations = conversations;
    this.documents = documents;
    this.rules = rules;
    this.labels = labels;
    this.materials = materials;
    this.settings = settings;
    this.usage = usage;
    this.ai = props.ai();
  }

  /** 진행 중인 턴 — 중단 요청 시 스트림을 닫는다 */
  static final class RunHandle {
    volatile boolean cancelled;
    volatile StreamResponse<RawMessageStreamEvent> stream;

    void cancel() {
      cancelled = true;
      StreamResponse<RawMessageStreamEvent> s = stream;
      if (s != null) {
        try {
          s.close();
        } catch (RuntimeException ignored) {
          // 이미 닫힘
        }
      }
    }
  }

  public boolean isRunning(UUID conversationId) {
    return running.containsKey(conversationId);
  }

  /** 지금 답변을 만들고 있는 대화 — 화면을 떠났다 돌아와도(스트림이 끊겨도) 서버는 끝까지 만든다 */
  public List<UUID> runningIds() {
    return List.copyOf(running.keySet());
  }

  /** 실행 자리를 잡는다. 이미 진행 중이면 false. */
  public boolean reserve(UUID conversationId) {
    return running.putIfAbsent(conversationId, new RunHandle()) == null;
  }

  /** reserve() 했지만 실행하지 못했을 때 자리를 돌려준다 */
  public void release(UUID conversationId) {
    running.remove(conversationId);
  }

  public void stop(UUID conversationId) {
    RunHandle handle = running.get(conversationId);
    if (handle != null) {
      handle.cancel();
    }
  }

  /** reserve() 성공 후 호출. 호출 스레드에서 끝까지 실행한다. */
  public void run(UUID conversationId, String text, Conditions conditions, SseSink sink) {
    RunHandle handle = running.get(conversationId);
    Turn turn = new Turn();
    try {
      runTurn(conversationId, text, conditions == null ? Conditions.empty() : conditions, sink, handle, turn);
    } catch (ApiException e) {
      sink.send("error", Map.of("code", e.code(), "message", e.getMessage()));
    } catch (RuntimeException e) {
      if (handle != null && handle.cancelled) {
        finishStopped(conversationId, turn, sink);
      } else {
        log.error("대화 생성 실패 conversation={}", conversationId, e);
        sink.send("error", Map.of("code", "AI_ERROR", "message", friendly(e)));
      }
    } finally {
      running.remove(conversationId);
      if (turn.cost > 0) {
        conversations.addCost(conversationId, turn.cost);
      }
      sink.complete();
    }
  }

  /** 한 턴 누적값 */
  static final class Turn {
    UsageService.Tokens tokens = UsageService.Tokens.zero();
    double cost;
    boolean contextAttached;
    List<ContextRule.Applied> context = List.of();
    String lastStopReason = "end_turn";
    /** 서버 도구 호출 id → 이름. pause_turn 으로 호출과 결과가 다른 응답에 나뉘어 와도 칩을 갱신하려고 턴 단위로 둔다 */
    final Map<String, String> serverToolNames = new HashMap<>();
  }

  private void runTurn(UUID conversationId, String text, Conditions conditions, SseSink sink, RunHandle handle,
      Turn turn) {
    conversations.get(conversationId);
    usage.assertWithinBudget();
    claude.get();
    SettingsService.Settings s = settings.get();

    repairDanglingToolUse(conversationId);
    conversations.update(conversationId, null, null, conditions);

    // 조건별 컨텍스트 규칙
    ContextRuleService.Result context = rules.apply(conditions, text);
    turn.context = context.applied();
    String userContent = userContent(text, describe(conditions), context.block(), documentState(conversationId));

    ObjectNode userDisplay = MessageRepository.display();
    userDisplay.put("text", text);
    userDisplay.set("conditions", Json.MAPPER.valueToTree(conditions));
    UUID userMessageId = store.append(conversationId, "user", MessageRepository.KIND_CHAT, userParam(userContent),
        userDisplay, null);
    conversations.titleIfDefault(conversationId, text);
    sink.send("meta", Map.of("userTurnId", userMessageId.toString(), "conversationId", conversationId.toString()));
    if (!context.applied().isEmpty()) {
      sink.send("context", Map.of("rules", context.applied()));
    }

    AgentTool.Context ctx = new AgentTool.Context(conversationId, userMessageId, text, conditions);
    int toolCalls = 0;
    boolean toolsExhausted = false;
    for (int round = 0; round < MAX_ROUNDS; round++) {
      if (handle.cancelled) {
        finishStopped(conversationId, turn, sink);
        return;
      }
      Message message = stream(conversationId, s, toolsExhausted, sink, handle, turn);
      StopReason stop = message.stopReason().orElse(StopReason.END_TURN);
      turn.lastStopReason = stop.asString();

      if (StopReason.REFUSAL.equals(stop)) {
        saveAssistantText(conversationId, "(요청이 안전 정책에 따라 거절되었습니다. 표현을 바꿔 다시 요청해 주세요.)", turn);
        sink.send("error", Map.of("code", "REFUSED", "message", "모델이 이 요청을 거절했습니다. 표현을 바꿔 다시 요청해 주세요."));
        return;
      }
      if (StopReason.MAX_TOKENS.equals(stop)) {
        // 잘린 tool_use 는 실행할 수 없으므로 텍스트만 남긴다
        saveAssistantText(conversationId, textOf(message) + "\n\n(응답이 너무 길어 중간에 끊겼습니다. 이어서 요청해 주세요.)", turn);
        break;
      }

      List<ToolUseBlock> uses = message.content().stream().flatMap(b -> b.toolUse().stream()).toList();
      ObjectNode assistantDisplay = assistantDisplay(message, turn);
      store.append(conversationId, "assistant", MessageRepository.KIND_ANSWER, message.toParam(), assistantDisplay,
          usageOf(message));
      if (StopReason.PAUSE_TURN.equals(stop)) {
        // 서버 도구(웹 검색) 반복이 한도에 닿아 멈췄다. 멈춘 응답을 그대로 이어 보내면 서버가 이어서 진행한다
        // ("계속" 같은 사용자 메시지를 덧붙이지 않는다).
        continue;
      }
      if (uses.isEmpty() || !StopReason.TOOL_USE.equals(stop)) {
        break;
      }

      // 도구 실행 — 같은 응답의 tool_use 결과는 하나의 user 메시지로 돌려준다
      List<ContentBlockParam> results = new ArrayList<>();
      ArrayNode toolSteps = Json.MAPPER.createArrayNode();
      ArrayNode documentVersions = Json.MAPPER.createArrayNode();
      for (ToolUseBlock use : uses) {
        toolCalls++;
        AgentTool.Result result;
        if (handle.cancelled) {
          result = AgentTool.Result.error("사용자가 생성을 중단했습니다");
        } else if (toolCalls > s.maxToolCalls()) {
          toolsExhausted = true;
          result = AgentTool.Result.error("이번 요청의 도구 호출 한도(" + s.maxToolCalls()
              + "회)를 넘었습니다. 지금까지의 결과로 답변을 마무리하세요.");
        } else {
          result = execute(use, ctx);
        }
        ObjectNode step = toolStep(use.id(), use.name(), result.error() ? "error" : "done", result.summary());
        toolSteps.add(step);
        sink.send("tool", step);
        if (result.document() != null) {
          documentVersions.add(result.document().version());
          sink.send("document", result.document());
          conversations.titleIfDefault(conversationId, result.document().payload().title());
        }
        results.add(ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
            .toolUseId(use.id())
            .content(result.content())
            .isError(result.error())
            .build()));
      }
      ObjectNode resultDisplay = MessageRepository.display();
      resultDisplay.set("tools", toolSteps);
      resultDisplay.set("documentVersions", documentVersions);
      store.append(conversationId, "user", MessageRepository.KIND_TOOL_RESULT,
          MessageParam.builder().role(MessageParam.Role.USER).contentOfBlockParams(results).build(), resultDisplay,
          null);
    }
    if (handle.cancelled) {
      finishStopped(conversationId, turn, sink);
      return;
    }
    sendDone(conversationId, turn, sink, turn.lastStopReason);
  }

  /** 한 번의 Claude 호출 (스트리밍). 텍스트·추론 요약·도구 시작을 실시간으로 중계한다. */
  private Message stream(UUID conversationId, SettingsService.Settings s, boolean toolsExhausted, SseSink sink,
      RunHandle handle, Turn turn) {
    MessageCreateParams.Builder params = MessageCreateParams.builder()
        .model(s.model())
        .maxTokens(ai.maxTokens())
        // 시스템 끝에 캐시 지점 하나(대화 간 공유), 최상위 자동 캐시가 대화 끝까지(턴 간 공유)
        .systemOfTextBlockParams(List.of(TextBlockParam.builder()
            .text(Prompts.SYSTEM)
            .cacheControl(CacheControlEphemeral.builder().build())
            .build()))
        .tools(tools.definitions(s.model()))
        .messages(forRequest(store.history(conversationId)))
        .thinking(ThinkingConfigAdaptive.builder()
            .display(ThinkingConfigAdaptive.Display.SUMMARIZED)
            // 저장된 thinking 블록은 생성 당시의 시스템 프롬프트·도구 목록·앞선 메시지에 서명으로 묶여 있다.
            // 배포로 Prompts·도구 정의가 바뀌면 그 전에 시작된 대화의 블록이 모두 무효가 되어
            // 400("The block is bound to a different conversation")이 난다. 거절 대신 서버가 무효 블록을
            // 버리고 진행하게 한다(첫 턴만 추론을 다시 하고, 이후 블록은 새 프롬프트에 묶인다).
            .putAdditionalProperty("block_binding",
                JsonValue.from(Map.of("prefix_mismatch_behavior", "drop_block")))
            .build())
        .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.of(s.effort())).build())
        .cacheControl(CacheControlEphemeral.builder().build());
    if (toolsExhausted) {
      params.toolChoice(ToolChoiceNone.builder().build());
    }
    List<String> betas = new ArrayList<>();
    betas.add(BETA_THINKING_BINDING_CONTROLS); // block_binding 필드를 쓰려면 필수
    if (ai.fallbackEnabled()) {
      // 거절 시 서버가 거절 유형에 맞는 대체 모델로 자동 재시도
      betas.add(BETA_SERVER_SIDE_FALLBACK);
      params.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
    }
    params.putAdditionalHeader("anthropic-beta", String.join(",", betas));

    MessageAccumulator acc = MessageAccumulator.create();
    ToolInputCompleter inputs = new ToolInputCompleter();
    Map<String, String> serverToolNames = turn.serverToolNames;
    try (StreamResponse<RawMessageStreamEvent> response = claude.get().messages().createStreaming(params.build())) {
      handle.stream = response;
      if (handle.cancelled) {
        throw new IllegalStateException("cancelled");
      }
      response.stream().forEach(event -> {
        inputs.accumulate(acc, event);
        event.contentBlockStart().ifPresent(start -> {
          RawContentBlockStartEvent.ContentBlock block = start.contentBlock();
          block.toolUse().ifPresent(use -> sink.send("tool", toolStep(use.id(), use.name(), "started", null)));
          // 서버 도구(웹 검색)는 Anthropic 이 실행하고 결과 블록이 같은 응답에 이어 온다
          block.serverToolUse().ifPresent(use -> {
            serverToolNames.put(use.id(), use.name().asString());
            sink.send("tool", toolStep(use.id(), use.name().asString(), "started", null));
          });
          if (block.toolUse().isEmpty() && block.serverToolUse().isEmpty()) {
            block.toolUseId().ifPresent(id -> {
              String name = serverToolNames.get(id);
              if (name != null) {
                String summary = block.webSearchToolResult().map(AgentService::searchSummary).orElse(null);
                boolean error = block.webSearchToolResult().map(r -> r.content().isError()).orElse(false);
                sink.send("tool", toolStep(id, name, error ? "error" : "done", summary));
              }
            });
          }
        });
        event.contentBlockDelta().ifPresent(delta -> {
          delta.delta().text().ifPresent(t -> sink.send("text", Map.of("text", t.text())));
          delta.delta().thinking().ifPresent(t -> sink.send("thinking", Map.of("text", t.thinking())));
        });
      });
    } finally {
      handle.stream = null;
    }
    Message message = acc.message();
    logDroppedThinking(conversationId, message);
    // 검색어는 스트림이 끝나야 다 모이므로 완성된 단계로 칩을 한 번 더 갱신한다
    serverToolSteps(message, turn.serverToolNames).forEach(step -> sink.send("tool", step));
    MessageRepository.Usage u = usageOf(message);
    UsageService.Tokens tokens = new UsageService.Tokens(u.input(), u.output(), u.cacheRead(),
        u.cacheWrite(), webSearches(message));
    turn.tokens = turn.tokens.plus(tokens);
    turn.cost += usage.record(u.model(), "chat", tokens);
    return message;
  }

  /**
   * 저장된 기록 → 요청 messages. 연속된 assistant 메시지는 하나로 합친다.
   * pause_turn 이면 멈춘 응답과 이어진 응답이 따로 저장되는데, 서버 도구 호출(server_tool_use)과 결과가 서로 다른
   * 메시지에 있으면 400("tool use ... was found without a corresponding ..._tool_result block")이다. 합친 모양이
   * 모델이 실제로 이어 쓴 한 턴과 같다. 마지막이 아닌 메시지에 결과 없는 서버 도구 호출이 남았으면(멈춘 뒤 중단·라운드
   * 한도) 그 호출만 뺀다 — 마지막 메시지의 것은 서버가 이어서 실행해야 하므로 둔다.
   */
  static List<MessageParam> forRequest(List<MessageParam> history) {
    List<MessageParam> merged = new ArrayList<>();
    List<ContentBlockParam> pending = null;
    for (MessageParam m : history) {
      if (MessageParam.Role.ASSISTANT.equals(m.role())) {
        if (pending == null) {
          pending = new ArrayList<>();
        }
        pending.addAll(blocks(m));
        continue;
      }
      if (pending != null) {
        merged.add(assistant(pending));
        pending = null;
      }
      merged.add(m);
    }
    if (pending != null) {
      merged.add(assistant(pending));
    }
    for (int i = 0; i < merged.size() - 1; i++) {
      MessageParam m = merged.get(i);
      if (MessageParam.Role.ASSISTANT.equals(m.role())) {
        merged.set(i, withoutUnansweredServerTools(m));
      }
    }
    return merged;
  }

  private static List<ContentBlockParam> blocks(MessageParam m) {
    if (m.content().isString()) {
      return List.of(ContentBlockParam.ofText(TextBlockParam.builder().text(m.content().asString()).build()));
    }
    return m.content().blockParams().orElse(List.of());
  }

  private static MessageParam assistant(List<ContentBlockParam> blocks) {
    return MessageParam.builder().role(MessageParam.Role.ASSISTANT).contentOfBlockParams(blocks).build();
  }

  private static MessageParam withoutUnansweredServerTools(MessageParam m) {
    List<ContentBlockParam> blocks = blocks(m);
    Set<String> answered = new HashSet<>();
    for (ContentBlockParam b : blocks) {
      if (b.serverToolUse().isEmpty() && b.toolUse().isEmpty()) {
        b.toolUseId().ifPresent(answered::add);
      }
    }
    List<ContentBlockParam> kept = blocks.stream()
        .filter(b -> b.serverToolUse().map(u -> answered.contains(u.id())).orElse(true))
        .toList();
    if (kept.size() == blocks.size()) {
      return m;
    }
    return kept.isEmpty()
        ? assistant(List.of(ContentBlockParam.ofText(TextBlockParam.builder().text("(중단됨)").build())))
        : assistant(kept);
  }

  /**
   * 응답 하나의 서버 도구(웹 검색) 단계 — 검색어와 결과 수를 요약에 담는다.
   * names 는 이 턴의 앞선 응답에서 시작된 호출(pause_turn 뒤 결과만 온 경우)의 이름을 찾는 데 쓴다.
   */
  List<ObjectNode> serverToolSteps(Message message, Map<String, String> names) {
    Map<String, WebSearchToolResultBlock> results = new HashMap<>();
    Set<String> answered = new HashSet<>();
    for (ContentBlock block : message.content()) {
      block.webSearchToolResult().ifPresent(r -> results.put(r.toolUseId(), r));
      if (block.toolUse().isEmpty() && block.serverToolUse().isEmpty()) {
        block.toolUseId().ifPresent(answered::add);
      }
    }
    List<ObjectNode> steps = new ArrayList<>();
    for (ContentBlock block : message.content()) {
      block.serverToolUse().ifPresent(use -> {
        WebSearchToolResultBlock result = results.get(use.id());
        String query = searchQuery(use);
        String summary = result == null ? null : searchSummary(result);
        if (query != null) {
          summary = "\"" + query + "\"" + (summary == null ? "" : " · " + summary);
        }
        String status = result != null && result.content().isError() ? "error"
            : answered.contains(use.id()) ? "done" : "started";
        steps.add(toolStep(use.id(), use.name().asString(), status, summary));
      });
    }
    // 앞선(멈춘) 응답에서 호출하고 결과만 이번 응답에 온 서버 도구
    Set<String> usedHere = new HashSet<>();
    message.content().forEach(b -> b.serverToolUse().ifPresent(u -> usedHere.add(u.id())));
    for (String id : answered) {
      String name = names.get(id);
      if (name != null && !usedHere.contains(id)) {
        WebSearchToolResultBlock result = results.get(id);
        boolean error = result != null && result.content().isError();
        steps.add(toolStep(id, name, error ? "error" : "done", result == null ? null : searchSummary(result)));
      }
    }
    return steps;
  }

  private static String searchQuery(ServerToolUseBlock use) {
    try {
      JsonNode input = Json.read(ObjectMappers.jsonMapper().writeValueAsString(use._input()));
      String query = input == null ? null : input.path("query").asText(null);
      return query == null || query.isBlank() ? null : query;
    } catch (Exception e) {
      return null;
    }
  }

  private static String searchSummary(WebSearchToolResultBlock result) {
    if (result.content().isError()) {
      return "검색 실패 (" + result.content().asError().errorCode().asString() + ")";
    }
    return "결과 " + result.content().asResultBlocks().size() + "개";
  }

  private static long webSearches(Message message) {
    return message.usage().serverToolUse().map(u -> u.webSearchRequests()).orElse(0L);
  }

  /**
   * 서버가 버린 thinking 블록(input_transformations)을 남긴다. 프롬프트·도구 배포 직후 옛 대화의 첫 턴에
   * 한 번 나오는 것이 정상이고, 매 턴 반복되면 우리가 보내는 기록이 턴마다 바뀌고 있다는 신호다.
   */
  private static void logDroppedThinking(UUID conversationId, Message message) {
    JsonValue transformations = message._additionalProperties().get("input_transformations");
    if (transformations == null) {
      return;
    }
    try {
      JsonNode node = Json.read(ObjectMappers.jsonMapper().writeValueAsString(transformations));
      if (node != null && node.isArray() && !node.isEmpty()) {
        log.info("thinking 블록 버림 conversation={} input_transformations={}", conversationId, node);
      }
    } catch (Exception e) {
      log.debug("input_transformations 해석 실패", e);
    }
  }

  private AgentTool.Result execute(ToolUseBlock use, AgentTool.Context ctx) {
    AgentTool tool = tools.get(use.name());
    if (tool == null) {
      return AgentTool.Result.error("알 수 없는 도구입니다: " + use.name());
    }
    JsonNode input;
    try {
      input = Json.read(ObjectMappers.jsonMapper().writeValueAsString(use._input()));
    } catch (Exception e) {
      return AgentTool.Result.error("도구 입력을 읽을 수 없습니다: " + e.getMessage());
    }
    try {
      return tool.run(input == null || input.isNull() ? Json.MAPPER.createObjectNode() : input, ctx);
    } catch (ApiException e) {
      return AgentTool.Result.error(e.getMessage());
    } catch (RuntimeException e) {
      log.warn("도구 실행 실패 {}", use.name(), e);
      return AgentTool.Result.error("도구 실행 중 오류: " + friendly(e));
    }
  }

  private ObjectNode assistantDisplay(Message message, Turn turn) {
    ObjectNode display = MessageRepository.display();
    display.put("text", textOf(message));
    StringBuilder thinking = new StringBuilder();
    for (ContentBlock block : message.content()) {
      block.thinking().ifPresent(t -> {
        if (!t.thinking().isBlank()) {
          thinking.append(thinking.length() > 0 ? "\n\n" : "").append(t.thinking());
        }
      });
    }
    if (thinking.length() > 0) {
      display.put("thinking", thinking.toString());
    }
    ArrayNode steps = Json.MAPPER.createArrayNode();
    serverToolSteps(message, turn.serverToolNames).forEach(steps::add);
    message.content().stream().flatMap(b -> b.toolUse().stream())
        .forEach(use -> steps.add(toolStep(use.id(), use.name(), "started", null)));
    display.set("tools", steps);
    if (!turn.contextAttached && !turn.context.isEmpty()) {
      display.set("context", Json.MAPPER.valueToTree(turn.context));
      turn.contextAttached = true;
    }
    return display;
  }

  private void saveAssistantText(UUID conversationId, String text, Turn turn) {
    ObjectNode display = MessageRepository.display();
    display.put("text", text);
    if (!turn.contextAttached && !turn.context.isEmpty()) {
      display.set("context", Json.MAPPER.valueToTree(turn.context));
      turn.contextAttached = true;
    }
    store.append(conversationId, "assistant", MessageRepository.KIND_ANSWER, MessageParam.builder()
        .role(MessageParam.Role.ASSISTANT)
        .contentOfBlockParams(List.of(ContentBlockParam.ofText(TextBlockParam.builder().text(text).build())))
        .build(), display, null);
  }

  private void finishStopped(UUID conversationId, Turn turn, SseSink sink) {
    try {
      repairDanglingToolUse(conversationId);
      ObjectNode display = MessageRepository.display();
      display.put("text", "(생성을 중단했습니다)");
      display.put("stopped", true);
      store.append(conversationId, "assistant", MessageRepository.KIND_ANSWER, MessageParam.builder()
          .role(MessageParam.Role.ASSISTANT)
          .contentOfBlockParams(List.of(ContentBlockParam.ofText(TextBlockParam.builder()
              .text("(사용자가 생성을 중단했습니다)").build())))
          .build(), display, null);
    } catch (RuntimeException e) {
      log.warn("중단 처리 중 오류", e);
    }
    sendDone(conversationId, turn, sink, "stopped");
  }

  private void sendDone(UUID conversationId, Turn turn, SseSink sink, String stopReason) {
    Map<String, Object> done = new LinkedHashMap<>();
    done.put("inputTokens", turn.tokens.input());
    done.put("outputTokens", turn.tokens.output());
    done.put("cacheReadTokens", turn.tokens.cacheRead());
    done.put("cacheWriteTokens", turn.tokens.cacheWrite());
    done.put("costUsd", round4(turn.cost));
    done.put("conversationCostUsd", round4(conversations.cost(conversationId) + turn.cost));
    done.put("monthCostUsd", round4(usage.monthCost()));
    done.put("stopReason", stopReason);
    sink.send("done", done);
  }

  /**
   * 직전 실행이 도구 실행 도중 끊겨 tool_use 에 짝이 되는 tool_result 가 없으면 오류 결과를 채워 넣는다
   * (짝이 없으면 다음 요청이 400 으로 거절된다).
   */
  private void repairDanglingToolUse(UUID conversationId) {
    List<MessageParam> history = store.history(conversationId);
    if (history.isEmpty()) {
      return;
    }
    MessageParam last = history.get(history.size() - 1);
    if (!MessageParam.Role.ASSISTANT.equals(last.role())) {
      return;
    }
    List<String> ids = new ArrayList<>();
    last.content().blockParams().ifPresent(blocks -> blocks.forEach(b -> b.toolUse().ifPresent(u -> ids.add(u.id()))));
    if (ids.isEmpty()) {
      return;
    }
    List<ContentBlockParam> results = ids.stream().map(id -> ContentBlockParam.ofToolResult(
        ToolResultBlockParam.builder().toolUseId(id).content("{\"error\":\"실행이 중단되었습니다\"}").isError(true)
            .build())).toList();
    ObjectNode display = MessageRepository.display();
    ArrayNode steps = Json.MAPPER.createArrayNode();
    ids.forEach(id -> steps.add(toolStep(id, "", "error", "중단됨")));
    display.set("tools", steps);
    store.append(conversationId, "user", MessageRepository.KIND_TOOL_RESULT,
        MessageParam.builder().role(MessageParam.Role.USER).contentOfBlockParams(results).build(), display, null);
  }

  /** 사용자 메시지 본문 — 조건, 규칙 컨텍스트, (필요하면) 현재 산출물 상태, 요청 */
  static String userContent(String text, String conditions, String contextBlock, String documentState) {
    StringBuilder sb = new StringBuilder();
    sb.append("<conditions>").append(conditions).append("</conditions>\n");
    if (contextBlock != null) {
      sb.append(contextBlock).append('\n');
    }
    if (documentState != null) {
      sb.append(documentState).append('\n');
    }
    sb.append("<request>\n").append(text).append("\n</request>");
    return sb.toString();
  }

  private String describe(Conditions c) {
    Map<UUID, String> titles = new HashMap<>();
    c.materialIds().forEach(id -> materials.find(id).ifPresent(m -> titles.put(id, m.title())));
    return c.describe(labels.refs(c.labelIds()), titles);
  }

  /**
   * 화면에서 직접 고치거나 버전을 되돌렸으면 모델이 기억하는 마지막 제출본과 현재 버전이 다르다 — 현재 본문을 알려준다.
   */
  private String documentState(UUID conversationId) {
    Optional<UUID> documentId = documents.findIdByConversation(conversationId);
    if (documentId.isEmpty()) {
      return null;
    }
    DocumentSummary doc = documents.find(documentId.get()).orElse(null);
    if (doc == null || doc.currentVersion() == 0 || documents.currentFromAi(documentId.get())) {
      return null;
    }
    DocumentVersion v = documents.version(documentId.get(), doc.currentVersion()).orElse(null);
    if (v == null) {
      return null;
    }
    return "<document_state>출제자가 문서 화면에서 직접 수정했거나 버전을 되돌려 현재 산출물은 v" + v.version() + " 입니다 ("
        + (v.payload().changeNote() == null ? "" : v.payload().changeNote())
        + "). 이후 수정은 아래 본문을 기준으로 하세요.\n" + Json.write(v.payload()) + "\n</document_state>";
  }

  private static MessageParam userParam(String content) {
    return MessageParam.builder()
        .role(MessageParam.Role.USER)
        .contentOfBlockParams(List.of(ContentBlockParam.ofText(TextBlockParam.builder().text(content).build())))
        .build();
  }

  ObjectNode toolStep(String id, String name, String status, String summary) {
    ObjectNode step = Json.MAPPER.createObjectNode();
    step.put("id", id);
    step.put("name", name);
    step.put("label", name.isEmpty() ? "도구" : tools.label(name));
    step.put("status", status);
    if (summary == null) {
      step.putNull("summary");
    } else {
      step.put("summary", summary);
    }
    return step;
  }

  private static String textOf(Message message) {
    StringBuilder sb = new StringBuilder();
    for (ContentBlock block : message.content()) {
      block.text().ifPresent(t -> sb.append(t.text()));
    }
    return sb.toString();
  }

  private static MessageRepository.Usage usageOf(Message m) {
    long input = m.usage().inputTokens();
    long output = m.usage().outputTokens();
    long cacheRead = m.usage().cacheReadInputTokens().orElse(0L);
    long cacheWrite = m.usage().cacheCreationInputTokens().orElse(0L);
    String model = m.model().asString();
    return new MessageRepository.Usage(model, input, output, cacheRead, cacheWrite,
        Pricing.cost(model, input, output, cacheRead, cacheWrite, webSearches(m)));
  }

  private static double round4(double v) {
    return Math.round(v * 10000) / 10000.0;
  }

  private static String friendly(Throwable e) {
    if (e instanceof AnthropicServiceException ase) {
      int status = ase.statusCode();
      if (status == 429) {
        return "AI 요청이 많아 잠시 제한되었습니다. 잠시 후 다시 시도해 주세요.";
      }
      if (status == 529 || status >= 500) {
        return "AI 서버가 일시적으로 불안정합니다. 잠시 후 다시 시도해 주세요.";
      }
      return "AI 요청 오류 (" + status + "): " + ase.getMessage();
    }
    return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
  }
}
