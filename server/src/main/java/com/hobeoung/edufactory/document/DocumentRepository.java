package com.hobeoung.edufactory.document;

import com.fasterxml.jackson.core.type.TypeReference;
import com.hobeoung.edufactory.common.ApiException;
import com.hobeoung.edufactory.common.Json;
import com.hobeoung.edufactory.common.Sql;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentRepository {

  private static final String SUMMARY = """
      SELECT d.id, d.conversation_id, d.title, d.kind, d.status, d.current_version, d.updated_at,
             v.payload::text AS payload, v.max_similarity
      FROM ef_document d LEFT JOIN ef_document_version v ON v.document_id = d.id AND v.version = d.current_version
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public DocumentRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public UUID create(UUID conversationId, String title, String kind, UUID createdBy) {
    UUID id = UUID.randomUUID();
    jdbc.update("""
        INSERT INTO ef_document (id, conversation_id, title, kind, created_by) VALUES (:id, :c, :t, :k, :by)
        """, new MapSqlParameterSource().addValue("id", id).addValue("c", conversationId).addValue("t", title)
        .addValue("k", kind).addValue("by", createdBy));
    return id;
  }

  public Optional<UUID> findIdByConversation(UUID conversationId) {
    return jdbc.queryForList("SELECT id FROM ef_document WHERE conversation_id = :c", Map.of("c", conversationId),
        UUID.class).stream().findFirst();
  }

  public Optional<DocumentSummary> find(UUID id) {
    return jdbc.query(SUMMARY + " WHERE d.id = :id", Map.of("id", id), (rs, i) -> summary(rs)).stream().findFirst();
  }

  public DocumentSummary get(UUID id) {
    return find(id).orElseThrow(() -> ApiException.notFound("산출물"));
  }

  public List<DocumentSummary> list(String q, String kind, String status) {
    StringBuilder sql = new StringBuilder(SUMMARY).append(" WHERE true");
    MapSqlParameterSource p = new MapSqlParameterSource();
    if (q != null && !q.isBlank()) {
      sql.append(" AND d.title ILIKE :q");
      p.addValue("q", "%" + q.strip() + "%");
    }
    if (kind != null && !kind.isBlank()) {
      sql.append(" AND d.kind = :kind");
      p.addValue("kind", kind);
    }
    if (status != null && !status.isBlank()) {
      sql.append(" AND d.status = :status");
      p.addValue("status", status);
    } else {
      sql.append(" AND d.status <> 'ARCHIVED'");
    }
    sql.append(" ORDER BY d.updated_at DESC LIMIT 500");
    return jdbc.query(sql.toString(), p, (rs, i) -> summary(rs));
  }

  private static DocumentSummary summary(ResultSet rs) throws SQLException {
    String payloadJson = rs.getString("payload");
    DocPayload payload = payloadJson == null ? null : Json.read(payloadJson, DocPayload.class);
    return new DocumentSummary(Sql.uuid(rs, "id"), Sql.uuid(rs, "conversation_id"), rs.getString("title"),
        rs.getString("kind"), rs.getString("status"), rs.getInt("current_version"),
        payload == null ? 0 : payload.items().size(), payload == null ? null : payload.totalScore(),
        Sql.dbl(rs, "max_similarity"), Sql.instant(rs, "updated_at"));
  }

  public List<DocumentVersion> versions(UUID documentId) {
    return jdbc.query("""
        SELECT document_id, version, payload::text AS payload, warnings::text AS warnings, similar_hits::text AS similar_hits,
               prediction::text AS prediction, created_at
        FROM ef_document_version WHERE document_id = :d ORDER BY version
        """, Map.of("d", documentId), (rs, i) -> version(rs));
  }

  public Optional<DocumentVersion> version(UUID documentId, int version) {
    return jdbc.query("""
        SELECT document_id, version, payload::text AS payload, warnings::text AS warnings, similar_hits::text AS similar_hits,
               prediction::text AS prediction, created_at
        FROM ef_document_version WHERE document_id = :d AND version = :v
        """, Map.of("d", documentId, "v", version), (rs, i) -> version(rs)).stream().findFirst();
  }

  private static DocumentVersion version(ResultSet rs) throws SQLException {
    return new DocumentVersion(Sql.uuid(rs, "document_id"), rs.getInt("version"),
        Json.read(rs.getString("payload"), DocPayload.class),
        Json.read(rs.getString("warnings"), new TypeReference<List<String>>() {}),
        Json.read(rs.getString("similar_hits"), new TypeReference<List<DocumentVersion.SimilarHit>>() {}),
        Json.read(rs.getString("prediction"), new TypeReference<List<DocumentVersion.ItemPrediction>>() {}),
        Sql.instant(rs, "created_at"));
  }

  /** 새 버전 저장 + 현재 버전·제목 갱신. @return 버전 번호 */
  public int addVersion(UUID documentId, DocPayload payload, List<String> warnings,
      List<DocumentVersion.SimilarHit> similar, List<DocumentVersion.ItemPrediction> prediction, Double maxSimilarity,
      UUID messageId) {
    Integer next = jdbc.queryForObject("SELECT current_version + 1 FROM ef_document WHERE id = :d FOR UPDATE",
        Map.of("d", documentId), Integer.class);
    int version = next == null ? 1 : next;
    jdbc.update("""
        INSERT INTO ef_document_version (document_id, version, payload, warnings, similar_hits, prediction, max_similarity,
                                         message_id)
        VALUES (:d, :v, CAST(:p AS jsonb), CAST(:w AS jsonb), CAST(:s AS jsonb), CAST(:pr AS jsonb), :max, :msg)
        """, new MapSqlParameterSource().addValue("d", documentId).addValue("v", version)
        .addValue("p", Json.write(payload)).addValue("w", Json.write(warnings)).addValue("s", Json.write(similar))
        .addValue("pr", Json.write(prediction)).addValue("max", maxSimilarity).addValue("msg", messageId));
    jdbc.update("""
        UPDATE ef_document SET current_version = :v, title = :t, kind = :k, updated_at = now() WHERE id = :d
        """, Map.of("d", documentId, "v", version, "t", payload.title(), "k", payload.kind()));
    return version;
  }

  /** 현재 버전이 대화에서 AI 가 낸 것인지(message_id 있음) — 아니면 화면에서 고치거나 되돌린 것 */
  public boolean currentFromAi(UUID documentId) {
    List<Map<String, Object>> rows = jdbc.queryForList("""
        SELECT v.message_id FROM ef_document d JOIN ef_document_version v
          ON v.document_id = d.id AND v.version = d.current_version WHERE d.id = :d
        """, Map.of("d", documentId));
    return !rows.isEmpty() && rows.get(0).get("message_id") != null;
  }

  public void update(UUID id, String title, String status) {
    jdbc.update("""
        UPDATE ef_document SET title = coalesce(:t, title), status = coalesce(:s, status), updated_at = now()
        WHERE id = :id
        """, new MapSqlParameterSource().addValue("id", id).addValue("t", title).addValue("s", status));
  }

  public void delete(UUID id) {
    jdbc.update("DELETE FROM ef_document WHERE id = :id", Map.of("id", id));
  }
}
