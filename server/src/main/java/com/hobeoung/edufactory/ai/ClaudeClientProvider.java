package com.hobeoung.edufactory.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.hobeoung.edufactory.common.ApiException;
import com.hobeoung.edufactory.config.EduProperties;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** Anthropic 클라이언트 (공식 Java SDK). 키가 없으면 서버는 뜨고 AI 기능만 오류를 낸다 (구조화는 규칙 기반으로 대체). */
@Component
public class ClaudeClientProvider {

  private final AnthropicClient client;

  public ClaudeClientProvider(EduProperties props) {
    String apiKey = props.ai().apiKey();
    if (apiKey != null && !apiKey.isBlank()) {
      this.client = AnthropicOkHttpClient.builder()
          .apiKey(apiKey)
          .timeout(Duration.ofMinutes(10))
          .maxRetries(2)
          .build();
    } else {
      this.client = null;
    }
  }

  public AnthropicClient get() {
    if (client == null) {
      throw ApiException.ai("ANTHROPIC_API_KEY 가 설정되지 않았습니다");
    }
    return client;
  }

  public boolean configured() {
    return client != null;
  }

  @PreDestroy
  public void close() {
    if (client != null) {
      client.close();
    }
  }
}
