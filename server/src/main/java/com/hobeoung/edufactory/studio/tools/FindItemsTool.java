package com.hobeoung.edufactory.studio.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.hobeoung.edufactory.common.Json;
import com.hobeoung.edufactory.material.Chunk;
import com.hobeoung.edufactory.material.MaterialRepository;
import com.hobeoung.edufactory.material.SearchService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class FindItemsTool implements AgentTool {

  private final NamedParameterJdbcTemplate jdbc;
  private final SearchService search;
  private final MaterialRepository materials;

  public FindItemsTool(NamedParameterJdbcTemplate jdbc, SearchService search, MaterialRepository materials) {
    this.jdbc = jdbc;
    this.search = search;
    this.materials = materials;
  }

  @Override
  public String name() {
    return "find_items_by_stats";
  }

  @Override
  public String label() {
    return "정답률로 문항 찾기";
  }

  @Override
  public String description() {
    return "성적이 있는 문항을 정답률 구간·변별도로 찾습니다 (예: 정답률 30~50% 이면서 변별도 높은 문항). "
        + "목표 난이도에 맞는 실제 문항을 기준으로 삼을 때 씁니다. labelIds 를 생략하면 요청 조건의 참고 범위 라벨로 거릅니다.";
  }

  @Override
  public String schema() {
    return """
        {"type":"object","properties":{
          "rateMin":{"type":"number","minimum":0,"maximum":1},
          "rateMax":{"type":"number","minimum":0,"maximum":1},
          "minDiscrimination":{"type":"number"},
          "minN":{"type":"integer","description":"최소 응답 수 (기본 5)"},
          "labelIds":{"type":"array","items":{"type":"string"}},
          "questionTypes":{"type":"array","items":{"type":"string"}},
          "sort":{"type":"string","enum":["rate_asc","rate_desc","discrimination_desc"]},
          "limit":{"type":"integer","minimum":1,"maximum":30}
        }}""";
  }

  @Override
  public Result run(JsonNode input, Context ctx) {
    StringBuilder where = new StringBuilder(" WHERE c.chunk_type = 'QUESTION' AND st.n >= :minN");
    MapSqlParameterSource p = new MapSqlParameterSource().addValue("minN", input.path("minN").asInt(5))
        .addValue("limit", Math.max(1, Math.min(30, input.path("limit").asInt(12))));
    if (input.hasNonNull("rateMin")) {
      where.append(" AND st.avg_rate >= :rmin");
      p.addValue("rmin", input.get("rateMin").asDouble());
    }
    if (input.hasNonNull("rateMax")) {
      where.append(" AND st.avg_rate <= :rmax");
      p.addValue("rmax", input.get("rateMax").asDouble());
    }
    if (input.hasNonNull("minDiscrimination")) {
      where.append(" AND (st.stat->>'discrimination')::float >= :dmin");
      p.addValue("dmin", input.get("minDiscrimination").asDouble());
    }
    List<String> types = ToolViews.strings(input.get("questionTypes"));
    if (!types.isEmpty()) {
      where.append(" AND c.question_type IN (:qt)");
      p.addValue("qt", types);
    }
    List<UUID> labelIds = input.has("labelIds") ? ToolViews.uuids(input.get("labelIds"))
        : ctx.conditions().labelIds();
    search.appendLabelFilter(where, p, labelIds);
    String order = switch (input.path("sort").asText("discrimination_desc")) {
      case "rate_asc" -> "st.avg_rate ASC";
      case "rate_desc" -> "st.avg_rate DESC";
      default -> "coalesce((st.stat->>'discrimination')::float, -1) DESC";
    };
    List<UUID> ids = jdbc.queryForList("SELECT c.id FROM ef_chunk c JOIN ef_item_stat st ON st.chunk_id = c.id"
        + where + " ORDER BY " + order + " LIMIT :limit", p, UUID.class);
    Map<UUID, Chunk> byId = new HashMap<>();
    materials.chunksByIds(ids).forEach(c -> byId.put(c.id(), c));
    Map<UUID, String> titles = new HashMap<>();
    ArrayNode out = Json.array();
    for (UUID id : ids) {
      Chunk c = byId.get(id);
      if (c != null) {
        String title = titles.computeIfAbsent(c.materialId(), m -> materials.find(m).map(x -> x.title()).orElse(null));
        out.add(ToolViews.chunk(c, title, 800));
      }
    }
    return Result.ok(Map.of("items", out), "문항 " + out.size() + "개");
  }
}
