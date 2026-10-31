package com.hobeoung.edufactory.label;

import com.hobeoung.edufactory.common.ApiException;
import com.hobeoung.edufactory.common.Sql;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LabelService {

  /** 라벨 이름이 바뀌어 붙은 청크를 다시 임베딩해야 함 */
  public record LabelRenamed(UUID labelId) {}

  /** 라벨이 지워짐 — 규칙 조건에서도 뺀다 */
  public record LabelDeleted(UUID labelId) {}

  private final NamedParameterJdbcTemplate jdbc;
  private final ApplicationEventPublisher events;

  public LabelService(NamedParameterJdbcTemplate jdbc, ApplicationEventPublisher events) {
    this.jdbc = jdbc;
    this.events = events;
  }

  public List<Label> list() {
    return jdbc.query("""
        SELECT l.id, l.group_name, l.name, l.color, l.description,
               (SELECT count(*) FROM ef_material_label ml WHERE ml.label_id = l.id) AS material_count,
               (SELECT count(*) FROM ef_chunk_label cl WHERE cl.label_id = l.id) AS chunk_count
        FROM ef_label l ORDER BY l.group_name, l.name
        """, Map.of(), (rs, i) -> new Label(Sql.uuid(rs, "id"), rs.getString("group_name"), rs.getString("name"),
        rs.getString("color"), rs.getString("description"), rs.getInt("material_count"), rs.getInt("chunk_count")));
  }

  public Label get(UUID id) {
    return list().stream().filter(l -> l.id().equals(id)).findFirst().orElseThrow(() -> ApiException.notFound("라벨"));
  }

  public record LabelInput(String group, String name, String color, String description) {}

  @Transactional
  public Label create(LabelInput in) {
    String group = Sql.str(in.group());
    String name = Sql.str(in.name());
    if (group == null || name == null) {
      throw ApiException.badRequest("라벨 그룹과 이름을 입력하세요");
    }
    assertUnique(group, name, null);
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO ef_label (id, group_name, name, color, description) VALUES (:id, :g, :n, :c, :d)",
        new MapSqlParameterSource().addValue("id", id).addValue("g", group).addValue("n", name)
            .addValue("c", Sql.str(in.color())).addValue("d", Sql.str(in.description())));
    return get(id);
  }

  @Transactional
  public Label update(UUID id, LabelInput in) {
    Label current = get(id);
    String group = in.group() == null ? current.group() : Sql.str(in.group());
    String name = in.name() == null ? current.name() : Sql.str(in.name());
    if (group == null || name == null) {
      throw ApiException.badRequest("라벨 그룹과 이름을 입력하세요");
    }
    assertUnique(group, name, id);
    jdbc.update("UPDATE ef_label SET group_name = :g, name = :n, color = :c, description = :d WHERE id = :id",
        new MapSqlParameterSource().addValue("id", id).addValue("g", group).addValue("n", name)
            .addValue("c", in.color() == null ? current.color() : Sql.str(in.color()))
            .addValue("d", in.description() == null ? current.description() : Sql.str(in.description())));
    if (!group.equals(current.group()) || !name.equals(current.name())) {
      events.publishEvent(new LabelRenamed(id));
    }
    return get(id);
  }

  @Transactional
  public void delete(UUID id) {
    get(id);
    jdbc.update("DELETE FROM ef_label WHERE id = :id", Map.of("id", id));
    events.publishEvent(new LabelDeleted(id));
  }

  /** id → 라벨 (없는 id 는 빠진다) */
  public Map<UUID, LabelRef> refs(Collection<UUID> ids) {
    if (ids == null || ids.isEmpty()) {
      return Map.of();
    }
    Map<UUID, LabelRef> out = new LinkedHashMap<>();
    jdbc.query("SELECT id, group_name, name, color FROM ef_label WHERE id IN (:ids) ORDER BY group_name, name",
        Map.of("ids", ids), rs -> {
          UUID id = Sql.uuid(rs, "id");
          out.put(id, new LabelRef(id, rs.getString("group_name"), rs.getString("name"), rs.getString("color")));
        });
    return out;
  }

  public List<LabelRef> all() {
    return jdbc.query("SELECT id, group_name, name, color FROM ef_label ORDER BY group_name, name", Map.of(),
        (rs, i) -> new LabelRef(Sql.uuid(rs, "id"), rs.getString("group_name"), rs.getString("name"),
            rs.getString("color")));
  }

  /** 검색 필터용: 라벨 id 를 그룹별로 묶는다 (같은 그룹은 OR, 다른 그룹은 AND) */
  public Map<String, List<UUID>> byGroup(Collection<UUID> ids) {
    Map<String, List<UUID>> groups = new HashMap<>();
    refs(ids).values().forEach(l -> groups.computeIfAbsent(l.group(), g -> new java.util.ArrayList<>()).add(l.id()));
    return groups;
  }

  private void assertUnique(String group, String name, UUID except) {
    Integer n = jdbc.queryForObject("SELECT count(*) FROM ef_label WHERE group_name = :g AND name = :n"
            + (except == null ? "" : " AND id <> :id"),
        new MapSqlParameterSource().addValue("g", group).addValue("n", name).addValue("id", except), Integer.class);
    if (n != null && n > 0) {
      throw ApiException.conflict("이미 있는 라벨입니다: " + group + " / " + name);
    }
  }
}
