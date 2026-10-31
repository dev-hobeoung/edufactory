package com.hobeoung.edufactory.studio;

import com.hobeoung.edufactory.common.ApiException;
import com.hobeoung.edufactory.common.Json;
import com.hobeoung.edufactory.common.Sql;
import com.hobeoung.edufactory.context.Conditions;
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
public class ConversationRepository {

  public static final String DEFAULT_TITLE = "새 작업";

  public record Summary(UUID id, String title, boolean archived, Conditions conditions, UUID documentId,
      Integer currentVersion, double costUsd, String createdAt, String updatedAt) {}

  private static final String SELECT = """
      SELECT c.id, c.title, c.archived, c.conditions::text AS conditions, c.cost_usd, c.created_at, c.updated_at,
             d.id AS document_id, d.current_version
      FROM ef_conversation c LEFT JOIN ef_document d ON d.conversation_id = c.id
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public ConversationRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Summary> list(String q, boolean archived) {
    MapSqlParameterSource p = new MapSqlParameterSource("a", archived);
    String where = " WHERE c.archived = :a";
    if (q != null && !q.isBlank()) {
      where += " AND c.title ILIKE :q";
      p.addValue("q", "%" + q.strip() + "%");
    }
    return jdbc.query(SELECT + where + " ORDER BY c.updated_at DESC LIMIT 300", p, (rs, i) -> map(rs));
  }

  public Optional<Summary> find(UUID id) {
    return jdbc.query(SELECT + " WHERE c.id = :id", Map.of("id", id), (rs, i) -> map(rs)).stream().findFirst();
  }

  public Summary get(UUID id) {
    return find(id).orElseThrow(() -> ApiException.notFound("대화"));
  }

  private static Summary map(ResultSet rs) throws SQLException {
    return new Summary(Sql.uuid(rs, "id"), rs.getString("title"), rs.getBoolean("archived"),
        Json.read(rs.getString("conditions"), Conditions.class), Sql.uuid(rs, "document_id"),
        Sql.integer(rs, "current_version"), rs.getDouble("cost_usd"), Sql.instant(rs, "created_at"),
        Sql.instant(rs, "updated_at"));
  }

  public UUID create(String title, Conditions conditions, UUID createdBy) {
    UUID id = UUID.randomUUID();
    jdbc.update("""
        INSERT INTO ef_conversation (id, title, conditions, created_by) VALUES (:id, :t, CAST(:c AS jsonb), :by)
        """, new MapSqlParameterSource().addValue("id", id)
        .addValue("t", title == null || title.isBlank() ? DEFAULT_TITLE : title.strip())
        .addValue("c", Json.write(conditions == null ? Conditions.empty() : conditions)).addValue("by", createdBy));
    return id;
  }

  public void update(UUID id, String title, Boolean archived, Conditions conditions) {
    jdbc.update("""
        UPDATE ef_conversation SET title = coalesce(:t, title), archived = coalesce(:a, archived),
               conditions = coalesce(CAST(:c AS jsonb), conditions), updated_at = now() WHERE id = :id
        """, new MapSqlParameterSource().addValue("id", id).addValue("t", Sql.str(title)).addValue("a", archived)
        .addValue("c", conditions == null ? null : Json.write(conditions)));
  }

  /** 기본 제목이면 첫 요청·산출물 제목으로 바꾼다 */
  public void titleIfDefault(UUID id, String title) {
    if (title == null || title.isBlank()) {
      return;
    }
    String t = title.strip().replaceAll("\\s+", " ");
    jdbc.update("UPDATE ef_conversation SET title = :t WHERE id = :id AND title = :d",
        Map.of("id", id, "t", t.length() > 60 ? t.substring(0, 60) + "…" : t, "d", DEFAULT_TITLE));
  }

  public void addCost(UUID id, double cost) {
    jdbc.update("UPDATE ef_conversation SET cost_usd = cost_usd + :c, updated_at = now() WHERE id = :id",
        Map.of("id", id, "c", cost));
  }

  public double cost(UUID id) {
    Double v = jdbc.queryForObject("SELECT cost_usd FROM ef_conversation WHERE id = :id", Map.of("id", id),
        Double.class);
    return v == null ? 0 : v;
  }

  public void delete(UUID id) {
    jdbc.update("DELETE FROM ef_conversation WHERE id = :id", Map.of("id", id));
  }
}
