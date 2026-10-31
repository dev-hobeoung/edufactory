package com.hobeoung.edufactory.material;

import com.hobeoung.edufactory.ai.ClaudeClientProvider;
import com.hobeoung.edufactory.embedding.Embedder;
import com.hobeoung.edufactory.label.LabelRef;
import com.hobeoung.edufactory.label.LabelService;
import com.hobeoung.edufactory.material.ingest.AiStructurer;
import com.hobeoung.edufactory.material.ingest.ExtractedText;
import com.hobeoung.edufactory.material.ingest.RuleStructurer;
import com.hobeoung.edufactory.material.ingest.StructureRequest;
import com.hobeoung.edufactory.material.ingest.TextExtractor;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 자료 처리 파이프라인 (백그라운드): 추출 → 구조화 → 임베딩. 상태는 ef_material.status 로 화면에 보인다.
 */
@Component
public class MaterialProcessor {

  private static final Logger log = LoggerFactory.getLogger(MaterialProcessor.class);

  private final MaterialRepository repo;
  private final FileStorage storage;
  private final TextExtractor extractor;
  private final AiStructurer ai;
  private final RuleStructurer rules;
  private final ChunkEmbedder chunkEmbedder;
  private final Embedder embedder;
  private final LabelService labels;
  private final ClaudeClientProvider claude;
  private final Executor executor;
  private final java.util.Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

  public MaterialProcessor(MaterialRepository repo, FileStorage storage, TextExtractor extractor, AiStructurer ai,
      RuleStructurer rules, ChunkEmbedder chunkEmbedder, Embedder embedder, LabelService labels,
      ClaudeClientProvider claude, @Qualifier("materialExecutor") Executor executor) {
    this.repo = repo;
    this.storage = storage;
    this.extractor = extractor;
    this.ai = ai;
    this.rules = rules;
    this.chunkEmbedder = chunkEmbedder;
    this.embedder = embedder;
    this.labels = labels;
    this.claude = claude;
    this.executor = executor;
  }

  public boolean busy(UUID materialId) {
    return inFlight.contains(materialId);
  }

  public void submit(UUID materialId, boolean restructure) {
    if (!inFlight.add(materialId)) {
      return;
    }
    repo.setStatus(materialId, "QUEUED", "처리 대기 중", null);
    executor.execute(() -> {
      try {
        process(materialId, restructure);
      } finally {
        inFlight.remove(materialId);
      }
    });
  }

  void process(UUID id, boolean restructure) {
    try {
      if (restructure) {
        MaterialRepository.Source src = repo.source(id);
        repo.setStatus(id, "EXTRACTING", "텍스트 추출 중", null);
        byte[] bytes = src.fileKey() == null ? null : storage.read(src.fileKey());
        ExtractedText extracted = extractor.extract(src.sourceType(), bytes, src.rawText());
        repo.setRawText(id, extracted.text());
        if (extracted.text().isBlank() && !extracted.looksScanned()) {
          throw new IllegalStateException("자료에서 텍스트를 찾지 못했습니다");
        }
        if (extracted.looksScanned() && !claude.configured()) {
          throw new IllegalStateException("스캔본 PDF 는 AI 구조화가 필요합니다 (ANTHROPIC_API_KEY 설정)");
        }
        repo.setStatus(id, "STRUCTURING", "구조화 중", null);
        List<LabelRef> catalog = src.autoLabel() ? labels.all() : List.of();
        StructureRequest req = new StructureRequest(src.title(), src.kind(), src.description(), src.isExam(), catalog,
            extracted);
        List<ChunkInput> chunks;
        String by;
        if (claude.configured()) {
          chunks = ai.structure(req, msg -> repo.setStatus(id, "STRUCTURING", msg, null));
          by = "AI";
        } else {
          chunks = rules.structure(req);
          by = "RULE";
        }
        if (chunks.isEmpty()) {
          throw new IllegalStateException("구조화 결과 청크가 없습니다");
        }
        repo.deleteChunks(id);
        int seq = 1;
        for (ChunkInput c : chunks) {
          repo.insertChunk(id, seq++, c, "AI");
        }
        repo.setStructuredBy(id, by);
      }
      repo.setStatus(id, "EMBEDDING", "임베딩 중", null);
      List<UUID> chunkIds = repo.chunks(id).stream().map(Chunk::id).toList();
      chunkEmbedder.embed(chunkIds);
      repo.setStatus(id, "READY", null, null);
    } catch (Exception e) {
      log.warn("자료 처리 실패 material={}", id, e);
      repo.setStatus(id, "FAILED", null, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
    }
  }

  /** 청크 몇 개만 다시 임베딩 (편집·라벨 변경) — 백그라운드 */
  public void reembed(Collection<UUID> chunkIds) {
    if (chunkIds.isEmpty()) {
      return;
    }
    List<UUID> ids = List.copyOf(chunkIds);
    executor.execute(() -> {
      try {
        chunkEmbedder.embed(ids);
      } catch (Exception e) {
        log.warn("재임베딩 실패 ({}개)", ids.size(), e);
      }
    });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onLabelRenamed(LabelService.LabelRenamed e) {
    reembed(repo.chunkIdsWithLabel(e.labelId()));
  }

  /** 임베딩 모델을 바꿨거나 처리 중 서버가 내려갔으면 기동 시 이어서 처리 */
  @EventListener(ApplicationReadyEvent.class)
  public void resume() {
    List<UUID> stale = repo.chunkIdsNeedingEmbedding(embedder.model());
    if (!stale.isEmpty()) {
      log.info("임베딩 모델({}) 기준으로 청크 {}개를 다시 임베딩합니다", embedder.model(), stale.size());
      reembed(stale);
    }
    repo.list(new MaterialRepository.Filter(null, null, null, null, null)).stream()
        .filter(m -> List.of("QUEUED", "EXTRACTING", "STRUCTURING", "EMBEDDING").contains(m.status()))
        .forEach(m -> submit(m.id(), !"EMBEDDING".equals(m.status())));
  }
}
