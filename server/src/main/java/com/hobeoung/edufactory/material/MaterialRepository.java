package com.hobeoung.edufactory.material;

import com.fasterxml.jackson.core.type.TypeReference;
import com.hobeoung.edufactory.common.ApiException;
import com.hobeoung.edufactory.common.Json;
import com.hobeoung.edufactory.common.Sql;
import com.hobeoung.edufactory.common.Vectors;
import com.hobeoung.edufactory.label.LabelRef;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MaterialRepository {

  private static final TypeReference<List<Choice>> CHOICES = new TypeReference<>() {};
  private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};

  private static final String SUMMARY_SELECT = """
      SELECT m.id, m.title, m.kind, m.description, m.source_type, m.file_name, m.file_size, m.is_exam, m.status,
             m.status_message, m.error, m.structured_by, m.created_at, m.updated_at,
             (SELECT count(*) FROM ef_chunk c WHERE c.material_id = m.id) AS chunk_count,
             (SELECT count(*) FROM ef_chunk c WHERE c.material_id = m.id AND c.embedding IS NOT NULL) AS embedded_count,
             (SELECT count(*) FROM ef_score_set s WHERE s.material_id = m.id) AS score_set_count
      FROM ef_material m
      """;

  static final String CHUNK_COLUMNS = """
      c.id, c.material_id, c.seq, c.chunk_type, c.title, c.position, c.item_no, c.question_type, c.content,
      c.choices::text AS choices, c.answer, c.explanation, c.score, c.tags::text AS tags, c.page_from, c.page_to,
      (c.embedding IS NOT NULL) AS embedded, st.stat::text AS stat
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public MaterialRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // ---------- 자료 ----------

  public record NewMaterial(UUID id, String title, String kind, String description, String sourceType,
      String fileName, String contentType, Long fileSize, String fileKey, String rawText, boolean isExam,
      boolean autoLabel, UUID createdBy) {}

  public void insert(NewMaterial m) {
    jdbc.update("""
        INSERT INTO ef_material (id, title, kind, description, source_type, file_name, content_type, file_size,
                                 file_key, raw_text, is_exam, auto_label, status, created_by)
        VALUES (:id, :title, :kind, :d, :st, :fn, :ct, :fs, :fk, :raw, :exam, :auto, 'QUEUED', :by)
        """, new MapSqlParameterSource().addValue("id", m.id()).addValue("title", m.title()).addValue("kind", m.kind())
        .addValue("d", m.description()).addValue("st", m.sourceType()).addValue("fn", m.fileName())
        .addValue("ct", m.contentType()).addValue("fs", m.fileSize()).addValue("fk", m.fileKey())
        .addValue("raw", m.rawText()).addValue("exam", m.isExam()).addValue("auto", m.autoLabel())
        .addValue("by", m.createdBy()));
  }

  public record Source(UUID id, String title, String kind, String description, String sourceType, String fileName,
      String contentType, String fileKey, String rawText, boolean isExam, boolean autoLabel) {}

  public Source source(UUID id) {
    return jdbc.query("""
        SELECT id, title, kind, description, source_type, file_name, content_type, file_key, raw_text, is_exam,
               auto_label FROM ef_material WHERE id = :id
        """, Map.of("id", id), (rs, i) -> new Source(Sql.uuid(rs, "id"), rs.getString("title"),
        rs.getString("kind"), rs.getString("description"), rs.getString("source_type"), rs.getString("file_name"),
        rs.getString("content_type"), rs.getString("file_key"), rs.getString("raw_text"), rs.getBoolean("is_exam"),
        rs.getBoolean("auto_label"))).stream().findFirst().orElseThrow(() -> ApiException.notFound("자료"));
  }

  public void setRawText(UUID id, String rawText) {
    jdbc.update("UPDATE ef_material SET raw_text = :r, updated_at = now() WHERE id = :id",
        new MapSqlParameterSource().addValue("id", id).addValue("r", rawText));
  }

  public void setStatus(UUID id, String status, String message, String error) {
    jdbc.update("UPDATE ef_material SET status = :s, status_message = :m, error = :e, updated_at = now() WHERE id = :id",
        new MapSqlParameterSource().addValue("id", id).addValue("s", status).addValue("m", message)
            .addValue("e", error));
  }

  public void setStructuredBy(UUID id, String by) {
    jdbc.update("UPDATE ef_material SET structured_by = :b WHERE id = :id", Map.of("id", id, "b", by));
  }

  public void update(UUID id, String title, String kind, String description, Boolean isExam) {
    jdbc.update("""
        UPDATE ef_material SET title = coalesce(:t, title), kind = coalesce(:k, kind),
               description = CASE WHEN :dset THEN :d ELSE description END,
               is_exam = coalesce(:exam, is_exam), updated_at = now() WHERE id = :id
        """, new MapSqlParameterSource().addValue("id", id).addValue("t", title).addValue("k", kind)
        .addValue("dset", description != null).addValue("d", Sql.str(description)).addValue("exam", isExam));
  }

  public void touch(UUID id) {
    jdbc.update("UPDATE ef_material SET updated_at = now() WHERE id = :id", Map.of("id", id));
  }

  public void delete(UUID id) {
    jdbc.update("DELETE FROM ef_material WHERE id = :id", Map.of("id", id));
  }

  public Optional<MaterialSummary> find(UUID id) {
    List<MaterialSummary> rows = summaries(SUMMARY_SELECT + " WHERE m.id = :id",
        new MapSqlParameterSource().addValue("id", id));
    return rows.stream().findFirst();
  }

  public MaterialSummary get(UUID id) {
    return find(id).orElseThrow(() -> ApiException.notFound("자료"));
  }

  public record Filter(String q, List<UUID> labelIds, String kind, Boolean exam, String status) {}

  public List<MaterialSummary> list(Filter f) {
    StringBuilder sql = new StringBuilder(SUMMARY_SELECT).append(" WHERE true");
    MapSqlParameterSource p = new MapSqlParameterSource();
    if (f.q() != null && !f.q().isBlank()) {
      sql.append(" AND (m.title ILIKE :q OR m.description ILIKE :q OR m.file_name ILIKE :q)");
      p.addValue("q", "%" + f.q().trim() + "%");
    }
    if (f.kind() != null && !f.kind().isBlank()) {
      sql.append(" AND m.kind = :kind");
      p.addValue("kind", f.kind());
    }
    if (f.exam() != null) {
      sql.append(" AND m.is_exam = :exam");
      p.addValue("exam", f.exam());
    }
    if (f.status() != null && !f.status().isBlank()) {
      sql.append(" AND m.status = :status");
      p.addValue("status", f.status());
    }
    if (f.labelIds() != null) {
      int i = 0;
      for (UUID labelId : f.labelIds()) {
        String key = "l" + i++;
        sql.append(" AND EXISTS (SELECT 1 FROM ef_material_label ml WHERE ml.material_id = m.id AND ml.label_id = :")
            .append(key).append(')');
        p.addValue(key, labelId);
      }
    }
    sql.append(" ORDER BY m.updated_at DESC LIMIT 500");
    return summaries(sql.toString(), p);
  }

  private List<MaterialSummary> summaries(String sql, MapSqlParameterSource p) {
    List<Object[]> raw = jdbc.query(sql, p, (rs, i) -> new Object[] {Sql.uuid(rs, "id"), rowValues(rs)});
    List<UUID> ids = raw.stream().map(r -> (UUID) r[0]).toList();
    Map<UUID, List<LabelRef>> labels = materialLabels(ids);
    List<MaterialSummary> out = new ArrayList<>();
    for (Object[] r : raw) {
      Object[] v = (Object[]) r[1];
      UUID id = (UUID) r[0];
      out.add(new MaterialSummary(id, (String) v[0], (String) v[1], (String) v[2], (String) v[3], (String) v[4],
          (Long) v[5], (Boolean) v[6], (String) v[7], (String) v[8], (String) v[9],
          labels.getOrDefault(id, List.of()), (Integer) v[10], (Integer) v[11], (Integer) v[12], (String) v[13],
          (String) v[14], (String) v[15]));
    }
    return out;
  }

  private static Object[] rowValues(ResultSet rs) throws SQLException {
    return new Object[] {rs.getString("title"), rs.getString("kind"), rs.getString("description"),
        rs.getString("source_type"), rs.getString("file_name"), Sql.lng(rs, "file_size"), rs.getBoolean("is_exam"),
        rs.getString("status"), rs.getString("status_message"), rs.getString("error"), rs.getInt("chunk_count"),
        rs.getInt("embedded_count"), rs.getInt("score_set_count"), rs.getString("structured_by"),
        Sql.instant(rs, "created_at"), Sql.instant(rs, "updated_at")};
  }

  public Map<UUID, List<LabelRef>> materialLabels(Collection<UUID> materialIds) {
    Map<UUID, List<LabelRef>> out = new HashMap<>();
    if (materialIds.isEmpty()) {
      return out;
    }
    jdbc.query("""
        SELECT ml.material_id, l.id, l.group_name, l.name, l.color FROM ef_material_label ml
        JOIN ef_label l ON l.id = ml.label_id WHERE ml.material_id IN (:ids) ORDER BY l.group_name, l.name
        """, Map.of("ids", materialIds), rs -> {
      out.computeIfAbsent(Sql.uuid(rs, "material_id"), k -> new ArrayList<>()).add(new LabelRef(Sql.uuid(rs, "id"),
          rs.getString("group_name"), rs.getString("name"), rs.getString("color")));
    });
    return out;
  }

  public void setMaterialLabels(UUID materialId, Collection<UUID> labelIds) {
    jdbc.update("DELETE FROM ef_material_label WHERE material_id = :m", Map.of("m", materialId));
    for (UUID labelId : labelIds) {
      jdbc.update("""
          INSERT INTO ef_material_label (material_id, label_id) SELECT :m, id FROM ef_label WHERE id = :l
          ON CONFLICT DO NOTHING
          """, Map.of("m", materialId, "l", labelId));
    }
  }

  // ---------- 청크 ----------

  public List<Chunk> chunks(UUID materialId) {
    return chunkQuery("WHERE c.material_id = :m ORDER BY c.seq", new MapSqlParameterSource("m", materialId));
  }

  public Optional<Chunk> chunk(UUID chunkId) {
    return chunkQuery("WHERE c.id = :id", new MapSqlParameterSource("id", chunkId)).stream().findFirst();
  }

  public List<Chunk> chunksByIds(Collection<UUID> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    return chunkQuery("WHERE c.id IN (:ids) ORDER BY c.material_id, c.seq", new MapSqlParameterSource("ids", ids));
  }

  List<Chunk> chunkQuery(String where, MapSqlParameterSource p) {
    List<Chunk> rows = jdbc.query("SELECT " + CHUNK_COLUMNS
        + " FROM ef_chunk c LEFT JOIN ef_item_stat st ON st.chunk_id = c.id " + where, p, (rs, i) -> mapChunk(rs));
    return withLabels(rows);
  }

  static Chunk mapChunk(ResultSet rs) throws SQLException {
    String stat = rs.getString("stat");
    return new Chunk(Sql.uuid(rs, "id"), Sql.uuid(rs, "material_id"), rs.getInt("seq"), rs.getString("chunk_type"),
        rs.getString("title"), rs.getString("position"), rs.getString("item_no"), rs.getString("question_type"),
        rs.getString("content"), Json.read(rs.getString("choices"), CHOICES), rs.getString("answer"),
        rs.getString("explanation"), Sql.dbl(rs, "score"), Json.read(rs.getString("tags"), STRINGS),
        Sql.integer(rs, "page_from"), Sql.integer(rs, "page_to"), List.of(), rs.getBoolean("embedded"),
        stat == null ? null : Json.read(stat));
  }

  public List<Chunk> withLabels(List<Chunk> chunks) {
    if (chunks.isEmpty()) {
      return chunks;
    }
    Map<UUID, List<ChunkLabel>> labels = new HashMap<>();
    jdbc.query("""
        SELECT cl.chunk_id, cl.source, l.id, l.group_name, l.name, l.color FROM ef_chunk_label cl
        JOIN ef_label l ON l.id = cl.label_id WHERE cl.chunk_id IN (:ids) ORDER BY l.group_name, l.name
        """, Map.of("ids", chunks.stream().map(Chunk::id).toList()), rs -> {
      labels.computeIfAbsent(Sql.uuid(rs, "chunk_id"), k -> new ArrayList<>()).add(new ChunkLabel(Sql.uuid(rs, "id"),
          rs.getString("group_name"), rs.getString("name"), rs.getString("color"), rs.getString("source")));
    });
    return chunks.stream().map(c -> new Chunk(c.id(), c.materialId(), c.seq(), c.chunkType(), c.title(), c.position(),
        c.itemNo(), c.questionType(), c.content(), c.choices(), c.answer(), c.explanation(), c.score(), c.tags(),
        c.pageFrom(), c.pageTo(), labels.getOrDefault(c.id(), List.of()), c.embedded(), c.stat())).toList();
  }

  public int nextSeq(UUID materialId) {
    Integer v = jdbc.queryForObject("SELECT coalesce(max(seq), 0) + 1 FROM ef_chunk WHERE material_id = :m",
        Map.of("m", materialId), Integer.class);
    return v == null ? 1 : v;
  }

  public UUID insertChunk(UUID materialId, int seq, ChunkInput in, String labelSource) {
    UUID id = UUID.randomUUID();
    jdbc.update("""
        INSERT INTO ef_chunk (id, material_id, seq, chunk_type, title, position, item_no, question_type, content,
                              choices, answer, explanation, score, tags, page_from, page_to)
        VALUES (:id, :m, :seq, :type, :title, :pos, :no, :qt, :content, CAST(:choices AS jsonb), :answer, :expl,
                :score, CAST(:tags AS jsonb), :pf, :pt)
        """, chunkParams(in).addValue("id", id).addValue("m", materialId).addValue("seq", seq));
    if (in.labelIds() != null) {
      setChunkLabels(id, in.labelIds(), labelSource);
    }
    return id;
  }

  private static MapSqlParameterSource chunkParams(ChunkInput in) {
    return new MapSqlParameterSource()
        .addValue("type", ChunkInput.chunkType(in.chunkType()))
        .addValue("title", Sql.str(in.title()))
        .addValue("pos", Sql.str(in.position()))
        .addValue("no", Sql.str(in.itemNo()))
        .addValue("qt", ChunkInput.questionType(in.questionType()))
        .addValue("content", in.content() == null ? "" : in.content())
        .addValue("choices", Json.write(in.choices() == null ? List.of() : in.choices()))
        .addValue("answer", Sql.str(in.answer()))
        .addValue("expl", Sql.str(in.explanation()))
        .addValue("score", in.score())
        .addValue("tags", Json.write(in.tags() == null ? List.of() : in.tags()))
        .addValue("pf", in.pageFrom())
        .addValue("pt", in.pageTo());
  }

  /** null 이 아닌 필드만 바꾼다 */
  public void updateChunk(UUID id, ChunkInput in) {
    Chunk c = chunk(id).orElseThrow(() -> ApiException.notFound("청크"));
    ChunkInput merged = new ChunkInput(
        in.chunkType() != null ? in.chunkType() : c.chunkType(),
        in.title() != null ? in.title() : c.title(),
        in.position() != null ? in.position() : c.position(),
        in.itemNo() != null ? in.itemNo() : c.itemNo(),
        in.questionType() != null ? in.questionType() : c.questionType(),
        in.content() != null ? in.content() : c.content(),
        in.choices() != null ? in.choices() : c.choices(),
        in.answer() != null ? in.answer() : c.answer(),
        in.explanation() != null ? in.explanation() : c.explanation(),
        in.score() != null ? in.score() : c.score(),
        in.tags() != null ? in.tags() : c.tags(),
        null,
        in.pageFrom() != null ? in.pageFrom() : c.pageFrom(),
        in.pageTo() != null ? in.pageTo() : c.pageTo());
    jdbc.update("""
        UPDATE ef_chunk SET chunk_type = :type, title = :title, position = :pos, item_no = :no, question_type = :qt,
               content = :content, choices = CAST(:choices AS jsonb), answer = :answer, explanation = :expl,
               score = :score, tags = CAST(:tags AS jsonb), page_from = :pf, page_to = :pt, updated_at = now()
        WHERE id = :id
        """, chunkParams(merged).addValue("id", id));
    if (in.labelIds() != null) {
      setChunkLabels(id, in.labelIds(), "MANUAL");
    }
  }

  public void setChunkLabels(UUID chunkId, Collection<UUID> labelIds, String source) {
    jdbc.update("DELETE FROM ef_chunk_label WHERE chunk_id = :c", Map.of("c", chunkId));
    for (UUID labelId : labelIds) {
      jdbc.update("""
          INSERT INTO ef_chunk_label (chunk_id, label_id, source) SELECT :c, id, :s FROM ef_label WHERE id = :l
          ON CONFLICT DO NOTHING
          """, Map.of("c", chunkId, "l", labelId, "s", source));
    }
  }

  public void deleteChunk(UUID id) {
    jdbc.update("DELETE FROM ef_chunk WHERE id = :id", Map.of("id", id));
  }

  public void deleteChunks(UUID materialId) {
    jdbc.update("DELETE FROM ef_chunk WHERE material_id = :m", Map.of("m", materialId));
  }

  public boolean chunkHasScores(UUID chunkId) {
    Integer n = jdbc.queryForObject("SELECT count(*) FROM ef_score_entry WHERE chunk_id = :c", Map.of("c", chunkId),
        Integer.class);
    return n != null && n > 0;
  }

  public void setEmbedding(UUID chunkId, float[] vector, String model) {
    jdbc.update("""
        UPDATE ef_chunk SET embedding = CAST(:v AS vector), embedding_model = :m, embedded_at = now() WHERE id = :id
        """, Map.of("id", chunkId, "v", Vectors.toPg(vector), "m", model));
  }

  public void clearEmbedding(UUID chunkId) {
    jdbc.update("UPDATE ef_chunk SET embedding = NULL, embedding_model = NULL, embedded_at = NULL WHERE id = :id",
        Map.of("id", chunkId));
  }

  /** 라벨이 (자료나 청크에) 붙은 청크 id */
  public List<UUID> chunkIdsWithLabel(UUID labelId) {
    return jdbc.queryForList("""
        SELECT c.id FROM ef_chunk c WHERE EXISTS (SELECT 1 FROM ef_chunk_label cl WHERE cl.chunk_id = c.id AND cl.label_id = :l)
           OR EXISTS (SELECT 1 FROM ef_material_label ml WHERE ml.material_id = c.material_id AND ml.label_id = :l)
        """, Map.of("l", labelId), UUID.class);
  }

  /** 현재 임베딩 모델과 다른(또는 없는) 청크 — 기동 시 재임베딩 */
  public List<UUID> chunkIdsNeedingEmbedding(String model) {
    return jdbc.queryForList("""
        SELECT c.id FROM ef_chunk c JOIN ef_material m ON m.id = c.material_id
        WHERE m.status = 'READY' AND (c.embedding IS NULL OR c.embedding_model IS DISTINCT FROM :m)
        """, Map.of("m", model), UUID.class);
  }
}
