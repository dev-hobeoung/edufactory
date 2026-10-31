package com.hobeoung.edufactory.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {

  /** 자료 처리(추출·구조화·임베딩) — 동시에 2건 */
  @Bean(name = "materialExecutor")
  public Executor materialExecutor() {
    ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
    ex.setCorePoolSize(2);
    ex.setMaxPoolSize(2);
    ex.setQueueCapacity(500);
    ex.setThreadNamePrefix("material-");
    ex.initialize();
    return ex;
  }

  /** 대화 생성 — 요청 스레드와 분리해 브라우저가 끊어도 끝까지 만든다 */
  @Bean(name = "chatExecutor")
  public Executor chatExecutor() {
    ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
    ex.setCorePoolSize(4);
    ex.setMaxPoolSize(16);
    ex.setQueueCapacity(0);
    ex.setThreadNamePrefix("chat-");
    ex.initialize();
    return ex;
  }
}
