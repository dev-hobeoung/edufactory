package com.hobeoung.edufactory.exam;

import com.hobeoung.edufactory.common.ApiException;
import com.hobeoung.edufactory.common.Json;
import com.hobeoung.edufactory.common.Sql;
import com.hobeoung.edufactory.material.MaterialRepository;
import com.hobeoung.edufactory.material.MaterialSummary;
import com.hobeoung.edufactory.material.SearchService;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 시험 자료의 성적 세트·입력·통계 */
@Service
public class ScoreService {

  public record ExamItem(UUID chunkId, String itemNo, String questionType, double maxScore, String answer,
      String preview, StatsCalculator.ItemStat stat) {}

  public record ScoreSet(UUID id, UUID materialId, String name, String takenOn, String note, int studentCount,
      String createdAt) {}

  private final NamedParameterJdbcTemplate jdbc;
  private final MaterialRepository materials;

  public ScoreService(NamedParameterJdbcTemplate jdbc, MaterialRepository materials) {
    this.jdbc = jdbc;
    this.materials = materials;
  }

  // ---------- 조회 ----------

  public List<Map<String, Object>> exams() {
    List<Map<String, Object>> out = new ArrayList<>();
    for (MaterialSummary m : materials.list(new MaterialRepository.Filter(null, null, null, true, null))) {
      List<StatsCalculator.Item> items = items(m.id());
      StatsCalculator.ExamStat stat = examStat(m.id());
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("material", m);
      row.put("itemCount", items.size());
      row.put("maxTotal", items.stream().mapToDouble(StatsCalculator.Item::maxScore).sum());
      row.put("scoreSetCount", m.scoreSetCount());
      row.put("n", stat == null ? 0 : stat.n());
      row.put("meanRate", stat == null ? null : stat.meanRate());
      out.add(row);
    }
    return out;
  }

  public Map<String, Object> exam(UUID materialId) {
    MaterialSummary m = materials.get(materialId);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("material", m);
    out.put("items", examItems(materialId));
    out.put("scoreSets", sets(materialId));
    out.put("stat", examStat(materialId));
    return out;
  }

  /** 시험 문항 = QUESTION 청크 (seq 순). 배점이 없으면 1점 */
  public List<StatsCalculator.Item> items(UUID materialId) {
    return jdbc.query("""
        SELECT id, item_no, score, answer FROM ef_chunk WHERE material_id = :m AND chunk_type = 'QUESTION'
        ORDER BY seq
        """, Map.of("m", materialId), (rs, i) -> {
      Double score = Sql.dbl(rs, "score");
      return new StatsCalculator.Item(Sql.uuid(rs, "id"), rs.getString("item_no"),
          score == null || score <= 0 ? 1.0 : score, rs.getString("answer"));
    });
  }

  public List<ExamItem> examItems(UUID materialId) {
    Map<UUID, StatsCalculator.ItemStat> stats = new HashMap<>();
    jdbc.query("""
        SELECT st.chunk_id, st.stat::text AS stat FROM ef_item_stat st JOIN ef_chunk c ON c.id = st.chunk_id
        WHERE c.material_id = :m
        """, Map.of("m", materialId), rs -> {
      stats.put(Sql.uuid(rs, "chunk_id"), Json.read(rs.getString("stat"), StatsCalculator.ItemStat.class));
    });
    return jdbc.query("""
        SELECT id, item_no, question_type, score, answer, content FROM ef_chunk
        WHERE material_id = :m AND chunk_type = 'QUESTION' ORDER BY seq
        """, Map.of("m", materialId), (rs, i) -> {
      Double score = Sql.dbl(rs, "score");
      UUID id = Sql.uuid(rs, "id");
      return new ExamItem(id, rs.getString("item_no"), rs.getString("question_type"),
          score == null || score <= 0 ? 1.0 : score, rs.getString("answer"),
          SearchService.preview(rs.getString("content"), 120), stats.get(id));
    });
  }

  public StatsCalculator.ExamStat examStat(UUID materialId) {
    List<String> rows = jdbc.queryForList("SELECT stat::text FROM ef_exam_stat WHERE material_id = :m",
        Map.of("m", materialId), String.class);
    return rows.isEmpty() ? null : Json.read(rows.get(0), StatsCalculator.ExamStat.class);
  }

  public List<ScoreSet> sets(UUID materialId) {
    return jdbc.query("""
        SELECT s.id, s.material_id, s.name, s.taken_on, s.note, s.created_at,
               (SELECT count(DISTINCT e.student_key) FROM ef_score_entry e WHERE e.score_set_id = s.id) AS students
        FROM ef_score_set s WHERE s.material_id = :m ORDER BY s.created_at
        """, Map.of("m", materialId), (rs, i) -> new ScoreSet(Sql.uuid(rs, "id"), Sql.uuid(rs, "material_id"),
        rs.getString("name"), rs.getDate("taken_on") == null ? null : rs.getDate("taken_on").toLocalDate().toString(),
        rs.getString("note"), rs.getInt("students"), Sql.instant(rs, "created_at")));
  }

  public ScoreSet set(UUID setId) {
    List<UUID> m = jdbc.queryForList("SELECT material_id FROM ef_score_set WHERE id = :id", Map.of("id", setId),
        UUID.class);
    if (m.isEmpty()) {
      throw ApiException.notFound("성적 세트");
    }
    return sets(m.get(0)).stream().filter(s -> s.id().equals(setId)).findFirst().orElseThrow();
  }

  // ---------- 세트 ----------

  public record SetInput(String name, String takenOn, String note) {}

  public ScoreSet createSet(UUID materialId, SetInput in) {
    MaterialSummary m = materials.get(materialId);
    if (!m.isExam()) {
      throw ApiException.badRequest("시험 자료에만 성적을 넣을 수 있습니다 (자료 설정에서 '시험 자료'를 켜세요)");
    }
    UUID id = UUID.randomUUID();
    String name = Sql.str(in.name());
    jdbc.update("INSERT INTO ef_score_set (id, material_id, name, taken_on, note) VALUES (:id, :m, :n, :d, :note)",
        new MapSqlParameterSource().addValue("id", id).addValue("m", materialId)
            .addValue("n", name == null ? "성적 " + (sets(materialId).size() + 1) : name)
            .addValue("d", date(in.takenOn())).addValue("note", Sql.str(in.note())));
    return set(id);
  }

  public ScoreSet updateSet(UUID setId, SetInput in) {
    ScoreSet s = set(setId);
    jdbc.update("UPDATE ef_score_set SET name = :n, taken_on = :d, note = :note WHERE id = :id",
        new MapSqlParameterSource().addValue("id", setId)
            .addValue("n", in.name() == null ? s.name() : Sql.str(in.name()))
            .addValue("d", in.takenOn() == null ? date(s.takenOn()) : date(in.takenOn()))
            .addValue("note", in.note() == null ? s.note() : Sql.str(in.note())));
    return set(setId);
  }

  @Transactional
  public void deleteSet(UUID setId) {
    ScoreSet s = set(setId);
    jdbc.update("DELETE FROM ef_score_set WHERE id = :id", Map.of("id", setId));
    recompute(s.materialId());
  }

  // ---------- 입력 ----------

  public Map<String, Object> entries(UUID setId) {
    ScoreSet s = set(setId);
    Map<String, Map<String, Map<String, Object>>> rows = new LinkedHashMap<>();
    jdbc.query("""
        SELECT student_key, chunk_id, score, response FROM ef_score_entry WHERE score_set_id = :s
        ORDER BY student_key
        """, Map.of("s", setId), rs -> {
      Map<String, Object> v = new HashMap<>();
      v.put("score", Sql.dbl(rs, "score"));
      v.put("response", rs.getString("response"));
      rows.computeIfAbsent(rs.getString("student_key"), k -> new LinkedHashMap<>())
          .put(rs.getString("chunk_id"), v);
    });
    List<Map<String, Object>> list = new ArrayList<>();
    rows.entrySet().stream().sorted(Map.Entry.comparingByKey(ScoreService::naturalCompare)).forEach(e -> {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("studentKey", e.getKey());
      row.put("values", e.getValue());
      list.add(row);
    });
    return Map.of("items", examItems(s.materialId()), "rows", list);
  }

  public record Row(String studentKey, Map<UUID, String> values) {}

  public record EntriesInput(String mode, List<Row> rows) {}

  @Transactional
  public Map<String, Object> putEntries(UUID setId, EntriesInput in) {
    ScoreSet s = set(setId);
    jdbc.update("DELETE FROM ef_score_entry WHERE score_set_id = :s", Map.of("s", setId));
    int written = write(s, in.mode(), in.rows() == null ? List.of() : in.rows());
    recompute(s.materialId());
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("rows", written);
    out.put("stat", setStats(setId).get("stat"));
    return out;
  }

  /** @return 저장한 학생 수 */
  private int write(ScoreSet s, String mode, List<Row> rows) {
    boolean responseMode = "RESPONSE".equals(mode);
    Map<UUID, StatsCalculator.Item> items = new HashMap<>();
    items(s.materialId()).forEach(i -> items.put(i.chunkId(), i));
    Set<String> students = new LinkedHashSet<>();
    List<MapSqlParameterSource> batch = new ArrayList<>();
    for (Row row : rows) {
      String key = Sql.str(row.studentKey());
      if (key == null || row.values() == null) {
        continue;
      }
      for (Map.Entry<UUID, String> v : row.values().entrySet()) {
        StatsCalculator.Item item = items.get(v.getKey());
        String raw = Sql.str(v.getValue());
        if (item == null || raw == null) {
          continue;
        }
        Double score;
        String response = null;
        if (responseMode) {
          response = raw;
          score = item.answer() == null ? null : (StatsCalculator.matches(raw, item.answer()) ? item.maxScore() : 0.0);
        } else {
          try {
            score = Double.parseDouble(raw.replace(",", "."));
          } catch (NumberFormatException e) {
            throw ApiException.badRequest(key + " 학생 " + item.itemNo() + "번 점수가 숫자가 아닙니다: " + raw);
          }
          if (score < 0 || score > item.maxScore() + 1e-9) {
            throw ApiException.badRequest(key + " 학생 " + item.itemNo() + "번 점수(" + raw + ")가 배점("
                + item.maxScore() + ") 범위를 벗어납니다");
          }
        }
        students.add(key);
        batch.add(new MapSqlParameterSource().addValue("s", s.id()).addValue("k", key).addValue("c", item.chunkId())
            .addValue("score", score).addValue("r", response));
      }
    }
    if (!batch.isEmpty()) {
      jdbc.batchUpdate("""
          INSERT INTO ef_score_entry (score_set_id, student_key, chunk_id, score, response)
          VALUES (:s, :k, :c, :score, :r)
          ON CONFLICT (score_set_id, student_key, chunk_id) DO UPDATE SET score = EXCLUDED.score, response = EXCLUDED.response
          """, batch.toArray(MapSqlParameterSource[]::new));
    }
    return students.size();
  }

  public record ImportInput(String mode, String text, boolean replace) {}

  @Transactional
  public Map<String, Object> importText(UUID setId, ImportInput in) {
    ScoreSet s = set(setId);
    if (in.text() == null || in.text().isBlank()) {
      throw ApiException.badRequest("붙여 넣을 내용이 없습니다");
    }
    List<String[]> table = ScoreTable.parse(in.text());
    if (table.size() < 2) {
      throw ApiException.badRequest("첫 행은 머리글(학생, 문항 번호…), 두 번째 행부터 학생별 값이어야 합니다");
    }
    List<StatsCalculator.Item> items = items(s.materialId());
    String[] header = table.get(0);
    List<String> unknown = new ArrayList<>();
    UUID[] columns = new UUID[header.length];
    for (int c = 1; c < header.length; c++) {
      UUID match = ScoreTable.matchItem(header[c], items);
      if (match == null) {
        if (!header[c].isBlank()) {
          unknown.add(header[c]);
        }
      } else {
        columns[c] = match;
      }
    }
    List<Row> rows = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    for (int r = 1; r < table.size(); r++) {
      String[] cells = table.get(r);
      if (cells.length == 0 || cells[0].isBlank()) {
        if (cells.length > 1) {
          warnings.add((r + 1) + "행: 학생 키가 비어 건너뜀");
        }
        continue;
      }
      Map<UUID, String> values = new LinkedHashMap<>();
      for (int c = 1; c < cells.length && c < columns.length; c++) {
        if (columns[c] != null) {
          values.put(columns[c], cells[c]);
        }
      }
      rows.add(new Row(cells[0], values));
    }
    if (in.replace()) {
      jdbc.update("DELETE FROM ef_score_entry WHERE score_set_id = :s", Map.of("s", setId));
    }
    int imported = write(s, in.mode(), rows);
    recompute(s.materialId());
    if (items.stream().anyMatch(i -> i.answer() == null) && "RESPONSE".equals(in.mode())) {
      warnings.add("정답이 없는 문항은 응답만 저장되고 채점되지 않았습니다");
    }
    return Map.of("imported", imported, "unknownColumns", unknown, "warnings", warnings);
  }

  // ---------- 통계 ----------

  public Map<String, Object> setStats(UUID setId) {
    ScoreSet s = set(setId);
    StatsCalculator.Result r = StatsCalculator.compute(items(s.materialId()), load(s.materialId(), setId));
    Map<String, Object> out = new HashMap<>();
    out.put("stat", r.exam());
    out.put("items", r.items());
    return out;
  }

  /** 정답·배점이 바뀌었을 수 있으니 응답 채점을 다시 하고 모든 세트 합산 통계를 저장한다 */
  @Transactional
  public void recompute(UUID materialId) {
    List<StatsCalculator.Item> items = items(materialId);
    Map<UUID, StatsCalculator.Item> byId = new HashMap<>();
    items.forEach(i -> byId.put(i.chunkId(), i));
    List<MapSqlParameterSource> regrade = new ArrayList<>();
    jdbc.query("""
        SELECT e.score_set_id, e.student_key, e.chunk_id, e.score, e.response FROM ef_score_entry e
        JOIN ef_score_set s ON s.id = e.score_set_id WHERE s.material_id = :m AND e.response IS NOT NULL
        """, Map.of("m", materialId), rs -> {
      StatsCalculator.Item item = byId.get(Sql.uuid(rs, "chunk_id"));
      if (item == null || item.answer() == null) {
        return;
      }
      double score = StatsCalculator.matches(rs.getString("response"), item.answer()) ? item.maxScore() : 0;
      Double current = Sql.dbl(rs, "score");
      if (current == null || Math.abs(current - score) > 1e-9) {
        regrade.add(new MapSqlParameterSource().addValue("score", score).addValue("s", Sql.uuid(rs, "score_set_id"))
            .addValue("k", rs.getString("student_key")).addValue("c", item.chunkId()));
      }
    });
    if (!regrade.isEmpty()) {
      jdbc.batchUpdate("""
          UPDATE ef_score_entry SET score = :score WHERE score_set_id = :s AND student_key = :k AND chunk_id = :c
          """, regrade.toArray(MapSqlParameterSource[]::new));
    }
    StatsCalculator.Result r = StatsCalculator.compute(items, load(materialId, null));
    jdbc.update("DELETE FROM ef_item_stat WHERE chunk_id IN (SELECT id FROM ef_chunk WHERE material_id = :m)",
        Map.of("m", materialId));
    for (StatsCalculator.ItemStat st : r.items()) {
      if (st.n() > 0) {
        jdbc.update("""
            INSERT INTO ef_item_stat (chunk_id, n, avg_rate, stat) VALUES (:c, :n, :r, CAST(:s AS jsonb))
            """, new MapSqlParameterSource().addValue("c", st.chunkId()).addValue("n", st.n())
            .addValue("r", st.avgRate()).addValue("s", Json.write(st)));
      }
    }
    jdbc.update("DELETE FROM ef_exam_stat WHERE material_id = :m", Map.of("m", materialId));
    if (r.exam() != null) {
      jdbc.update("INSERT INTO ef_exam_stat (material_id, stat) VALUES (:m, CAST(:s AS jsonb))",
          Map.of("m", materialId, "s", Json.write(r.exam())));
    }
  }

  /** setId 가 null 이면 모든 세트 (학생 키는 세트별로 구분) */
  private Map<String, Map<UUID, StatsCalculator.Entry>> load(UUID materialId, UUID setId) {
    Map<String, Map<UUID, StatsCalculator.Entry>> students = new LinkedHashMap<>();
    MapSqlParameterSource p = new MapSqlParameterSource().addValue("m", materialId).addValue("s", setId);
    jdbc.query("""
        SELECT e.score_set_id, e.student_key, e.chunk_id, e.score, e.response FROM ef_score_entry e
        JOIN ef_score_set s ON s.id = e.score_set_id
        WHERE s.material_id = :m AND (CAST(:s AS uuid) IS NULL OR s.id = :s)
        """, p, rs -> {
      String key = rs.getString("score_set_id") + ":" + rs.getString("student_key");
      students.computeIfAbsent(key, k -> new HashMap<>()).put(Sql.uuid(rs, "chunk_id"),
          new StatsCalculator.Entry(Sql.dbl(rs, "score"), rs.getString("response")));
    });
    return students;
  }

  private static Date date(String s) {
    String v = Sql.str(s);
    return v == null ? null : Date.valueOf(LocalDate.parse(v));
  }

  /** "2" < "10" 처럼 숫자를 숫자로 비교 */
  static int naturalCompare(String a, String b) {
    try {
      return Long.compare(Long.parseLong(a), Long.parseLong(b));
    } catch (NumberFormatException e) {
      return a.compareTo(b);
    }
  }
}
