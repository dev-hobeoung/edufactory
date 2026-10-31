package com.hobeoung.edufactory.embedding;

import com.hobeoung.edufactory.config.EduProperties;
import com.hobeoung.edufactory.usage.UsageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingConfig {

  private static final Logger log = LoggerFactory.getLogger(EmbeddingConfig.class);

  @Bean
  public Embedder embedder(EduProperties props, UsageService usage) {
    EduProperties.Embedding e = props.embedding();
    if (e.voyageApiKey() != null && !e.voyageApiKey().isBlank()) {
      return new VoyageEmbedder(e.voyageApiKey(), e.voyageBaseUrl(), e.model(), e.dimension(), usage);
    }
    log.warn("VOYAGE_API_KEY 가 없어 로컬 해시 임베딩({})으로 동작합니다 — 의미 검색 품질이 낮습니다",
        LocalHashEmbedder.MODEL);
    return new LocalHashEmbedder(e.dimension());
  }
}
