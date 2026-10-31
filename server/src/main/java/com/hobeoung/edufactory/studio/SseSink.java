package com.hobeoung.edufactory.studio;

import com.hobeoung.edufactory.common.Json;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 전송기. 브라우저가 연결을 끊어도 생성은 끝까지 진행해 결과를 저장한다 (전송만 멈춤).
 *
 * <p>생각·웹 검색·도구 실행 중에는 한동안 보낼 이벤트가 없어, 앞단 프록시(로드밸런서·CloudFront)가 유휴 연결로 보고
 * 끊는다 (브라우저에는 "network error"). 열려 있는 동안 {@link #HEARTBEAT_SECONDS}초마다 SSE 주석을 보내 연결을 살려 둔다.
 */
public class SseSink {

  static final long HEARTBEAT_SECONDS = 15;

  private static final ScheduledExecutorService HEARTBEAT = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread t = new Thread(r, "studio-sse-heartbeat");
    t.setDaemon(true);
    return t;
  });

  private final SseEmitter emitter;
  private final ScheduledFuture<?> heartbeat;
  private volatile boolean open = true;

  public SseSink(SseEmitter emitter) {
    this.emitter = emitter;
    emitter.onCompletion(this::closed);
    emitter.onTimeout(this::closed);
    emitter.onError(e -> closed());
    this.heartbeat = HEARTBEAT.scheduleAtFixedRate(this::ping, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS,
        TimeUnit.SECONDS);
  }

  public synchronized void send(String event, Object data) {
    if (!open) {
      return;
    }
    try {
      emitter.send(SseEmitter.event().name(event).data(Json.write(data)));
    } catch (IOException | IllegalStateException e) {
      closed();
    }
  }

  synchronized void ping() {
    if (!open) {
      return;
    }
    try {
      emitter.send(SseEmitter.event().comment("ping"));
    } catch (IOException | IllegalStateException e) {
      closed();
    }
  }

  public synchronized void complete() {
    if (open) {
      closed();
      try {
        emitter.complete();
      } catch (RuntimeException ignored) {
        // 이미 닫힘
      }
    }
  }

  private void closed() {
    open = false;
    if (heartbeat != null) {
      heartbeat.cancel(false);
    }
  }
}
