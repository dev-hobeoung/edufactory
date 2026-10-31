package com.hobeoung.edufactory.material;

import com.hobeoung.edufactory.embedding.Embedder;
import com.hobeoung.edufactory.label.LabelRef;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** 청크 임베딩. 입력 = 라벨 표기 + 자료 제목·위치 + 본문 + 선택지 (+ 짧은 해설) */
@Component
public class ChunkEmbedder {

  static final int MAX_CHARS = 8000;
  private static final int BATCH = 64;

  private final MaterialRepository repo;
  private final Embedder embedder;

  public ChunkEmbedder(MaterialRepository repo, Embedder embedder) {
    this.repo = repo;
    this.embedder = embedder;
  }

  public int embed(Collection<UUID> chunkIds) {
    List<Chunk> chunks = repo.chunksByIds(chunkIds);
    if (chunks.isEmpty()) {
      return 0;
    }
    Set<UUID> materialIds = chunks.stream().map(Chunk::materialId).collect(Collectors.toSet());
    Map<UUID, List<LabelRef>> materialLabels = repo.materialLabels(materialIds);
    Map<UUID, String> titles = new java.util.HashMap<>();
    materialIds.forEach(id -> repo.find(id).ifPresent(m -> titles.put(id, m.title())));
    int done = 0;
    for (int start = 0; start < chunks.size(); start += BATCH) {
      List<Chunk> batch = chunks.subList(start, Math.min(chunks.size(), start + BATCH));
      List<String> texts = new ArrayList<>();
      for (Chunk c : batch) {
        texts.add(text(c, titles.get(c.materialId()), materialLabels.getOrDefault(c.materialId(), List.of())));
      }
      List<float[]> vectors = embedder.embed(texts, Embedder.INPUT_DOCUMENT);
      for (int i = 0; i < batch.size(); i++) {
        repo.setEmbedding(batch.get(i).id(), vectors.get(i), embedder.model());
      }
      done += batch.size();
    }
    return done;
  }

  static String text(Chunk c, String materialTitle, List<LabelRef> materialLabels) {
    Set<String> labels = new LinkedHashSet<>();
    materialLabels.forEach(l -> labels.add(l.display()));
    c.labels().forEach(l -> labels.add(l.group() + ":" + l.name()));
    StringBuilder sb = new StringBuilder();
    labels.forEach(l -> sb.append('[').append(l).append("] "));
    if (!c.tags().isEmpty()) {
      sb.append("#").append(String.join(" #", c.tags())).append(' ');
    }
    sb.append('\n');
    if (materialTitle != null) {
      sb.append(materialTitle);
    }
    if (c.position() != null) {
      sb.append(" · ").append(c.position());
    }
    if (c.title() != null) {
      sb.append(" · ").append(c.title());
    }
    sb.append('\n').append(c.content());
    for (Choice ch : c.choices()) {
      sb.append('\n').append(ch.label()).append(' ').append(ch.text());
    }
    if (c.explanation() != null) {
      sb.append("\n해설: ").append(c.explanation(), 0, Math.min(c.explanation().length(), 600));
    }
    String s = sb.toString();
    return s.length() > MAX_CHARS ? s.substring(0, MAX_CHARS) : s;
  }
}
