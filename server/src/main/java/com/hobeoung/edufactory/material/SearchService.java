package com.hobeoung.edufactory.material;

import com.hobeoung.edufactory.embedding.Embedder;
import com.hobeoung.edufactory.label.LabelRef;
import com.hobeoung.edufactory.label.LabelService;
import com.hobeoung.edufactory.common.Sql;
import com.hobeoung.edufactory.common.Vectors;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 청크 의미 검색 (pgvector 코사인). 라벨 필터는 같은 그룹 안에서 OR, 그룹끼리 AND — 자료 라벨이나 청크 라벨 어느 쪽에 붙어도 된다.
 */
@Service
public class SearchService {

  public record Query(String query, List<UUID> labelIds, List<UUID> materialIds, List<String> chunkTypes,
      Boolean examOnly, Integer limit, Boolean scoredOnly) {}

  public record Hit(Chunk chunk, UUID materialId, String materialTitle, List<LabelRef> materialLabels,
      double similarity) {}

  private final NamedParameterJdbcTemplate jdbc;
  private final Embedder embedder;
  private final LabelService labels;
  private final MaterialRepository repo;

  public SearchService(NamedParameterJdbcTemplate jdbc, Embedder embedder, LabelService labels,
      MaterialRepository repo) {
    this.jdbc = jdbc;
    this.embedder = embedder;
    this.labels = labels;
    this.repo = repo;
  }

  public List<Hit> search(Query q) {
    if (q.query() == null || q.query().isBlank()) {
      return List.of();
    }
    return searchVector(embedder.embedQuery(q.query()), q, null);
  }

  /** 미리 계산한 벡터로 검색 (유사도 검사·난이도 예측) */
  public List<Hit> searchVector(float[] vector, Query q, UUID excludeMaterial) {
    int limit = q.limit() == null ? 10 : Math.max(1, Math.min(50, q.limit()));
    StringBuilder where = new StringBuilder(" WHERE c.embedding IS NOT NULL AND c.embedding_model = :model");
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("v", Vectors.toPg(vector)).addValue("model", embedder.model()).addValue("limit", limit);
    if (excludeMaterial != null) {
      where.append(" AND c.material_id <> :exclude");
      p.addValue("exclude", excludeMaterial);
    }
    if (q.materialIds() != null && !q.materialIds().isEmpty()) {
      where.append(" AND c.material_id IN (:materialIds)");
      p.addValue("materialIds", q.materialIds());
    }
    if (q.chunkTypes() != null && !q.chunkTypes().isEmpty()) {
      where.append(" AND c.chunk_type IN (:types)");
      p.addValue("types", q.chunkTypes());
    }
    if (Boolean.TRUE.equals(q.examOnly())) {
      where.append(" AND m.is_exam");
    }
    if (Boolean.TRUE.equals(q.scoredOnly())) {
      where.append(" AND st.chunk_id IS NOT NULL");
    }
    appendLabelFilter(where, p, q.labelIds());
    List<Object[]> rows = jdbc.query("SELECT " + MaterialRepository.CHUNK_COLUMNS
        + ", m.title AS material_title, 1 - (c.embedding <=> CAST(:v AS vector)) AS similarity"
        + " FROM ef_chunk c JOIN ef_material m ON m.id = c.material_id"
        + " LEFT JOIN ef_item_stat st ON st.chunk_id = c.id" + where
        + " ORDER BY c.embedding <=> CAST(:v AS vector) LIMIT :limit", p,
        (rs, i) -> new Object[] {MaterialRepository.mapChunk(rs), rs.getString("material_title"),
            rs.getDouble("similarity")});
    List<Chunk> chunks = repo.withLabels(rows.stream().map(r -> (Chunk) r[0]).toList());
    Map<UUID, List<LabelRef>> materialLabels = repo.materialLabels(
        chunks.stream().map(Chunk::materialId).distinct().toList());
    List<Hit> hits = new ArrayList<>();
    for (int i = 0; i < chunks.size(); i++) {
      Chunk c = chunks.get(i);
      hits.add(new Hit(c, c.materialId(), (String) rows.get(i)[1], materialLabels.getOrDefault(c.materialId(),
          List.of()), Math.round((Double) rows.get(i)[2] * 10000) / 10000.0));
    }
    return hits;
  }

  /** 라벨 필터 SQL (chunk 별칭 c) */
  public void appendLabelFilter(StringBuilder where, MapSqlParameterSource p, Collection<UUID> labelIds) {
    if (labelIds == null || labelIds.isEmpty()) {
      return;
    }
    int g = 0;
    for (List<UUID> group : labels.byGroup(labelIds).values()) {
      String key = "lg" + g++;
      where.append(" AND (EXISTS (SELECT 1 FROM ef_chunk_label cl WHERE cl.chunk_id = c.id AND cl.label_id IN (:")
          .append(key).append(")) OR EXISTS (SELECT 1 FROM ef_material_label ml WHERE ml.material_id = c.material_id"
              + " AND ml.label_id IN (:").append(key).append(")))");
      p.addValue(key, group);
    }
  }

  public static String preview(String content, int max) {
    String s = content == null ? "" : content.replaceAll("\\s+", " ").strip();
    return s.length() > max ? s.substring(0, max) + "…" : s;
  }

  static String str(String s) {
    return Sql.str(s);
  }
}
