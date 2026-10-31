package com.hobeoung.edufactory.studio;

import com.hobeoung.edufactory.auth.CurrentUser;
import com.hobeoung.edufactory.common.ApiException;
import com.hobeoung.edufactory.context.Conditions;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/conversations")
public class ConversationController {

  /** 생각·도구 실행이 길어도 30분까지 */
  private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L;

  private final ConversationRepository conversations;
  private final MessageRepository messages;
  private final AgentService agent;
  private final Executor executor;

  public ConversationController(ConversationRepository conversations, MessageRepository messages, AgentService agent,
      @Qualifier("chatExecutor") Executor executor) {
    this.conversations = conversations;
    this.messages = messages;
    this.agent = agent;
    this.executor = executor;
  }

  @GetMapping
  public List<ConversationRepository.Summary> list(@RequestParam(required = false) String q,
      @RequestParam(defaultValue = "false") boolean archived) {
    return conversations.list(q, archived);
  }

  public record Create(String title, Conditions conditions) {}

  @PostMapping
  public ConversationRepository.Summary create(@RequestBody(required = false) Create body) {
    UUID id = conversations.create(body == null ? null : body.title(), body == null ? null : body.conditions(),
        CurrentUser.get().id());
    return conversations.get(id);
  }

  public record Patch(String title, Boolean archived, Conditions conditions) {}

  @PatchMapping("/{id}")
  public ConversationRepository.Summary patch(@PathVariable UUID id, @RequestBody Patch body) {
    conversations.get(id);
    conversations.update(id, body.title(), body.archived(), body.conditions());
    return conversations.get(id);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    conversations.get(id);
    if (agent.isRunning(id)) {
      throw new ApiException(HttpStatus.CONFLICT, "BUSY", "생성 중인 대화는 삭제할 수 없습니다");
    }
    conversations.delete(id);
    return ResponseEntity.noContent().build();
  }

  /** 답변을 만들고 있는 대화 id — 스트림이 끊긴 화면이 생성 중 표시와 자동 갱신에 쓴다 */
  @GetMapping("/running")
  public List<String> running() {
    return agent.runningIds().stream().map(UUID::toString).toList();
  }

  @GetMapping("/{id}/messages")
  public List<Map<String, Object>> messages(@PathVariable UUID id) {
    conversations.get(id);
    return messages.turns(id);
  }

  public record Send(String text, Conditions conditions) {}

  /** 메시지 보내기 — 진행 상황을 SSE(meta·context·thinking·tool·text·document·done·error)로 흘려보낸다 */
  @PostMapping(value = "/{id}/messages", produces = "text/event-stream")
  public SseEmitter send(@PathVariable UUID id, @RequestBody Send body, HttpServletResponse response) {
    response.setHeader("X-Accel-Buffering", "no");
    response.setHeader("Cache-Control", "no-cache");
    SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
    SseSink sink = new SseSink(emitter);
    if (conversations.find(id).isEmpty() || body.text() == null || body.text().isBlank()) {
      sink.send("error", Map.of("code", "BAD_REQUEST", "message", "대화가 없거나 메시지가 비었습니다"));
      sink.complete();
      return emitter;
    }
    if (!agent.reserve(id)) {
      sink.send("error", Map.of("code", "BUSY", "message", "이 대화에서 이미 답변을 만들고 있습니다"));
      sink.complete();
      return emitter;
    }
    try {
      executor.execute(() -> agent.run(id, body.text().strip(), body.conditions(), sink));
    } catch (RejectedExecutionException e) {
      agent.release(id);
      sink.send("error", Map.of("code", "BUSY", "message", "서버가 바쁩니다. 잠시 후 다시 시도해 주세요"));
      sink.complete();
    }
    return emitter;
  }

  @PostMapping("/{id}/stop")
  public ResponseEntity<Void> stop(@PathVariable UUID id) {
    agent.stop(id);
    return ResponseEntity.noContent().build();
  }
}
