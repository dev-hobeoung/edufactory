package com.hobeoung.edufactory.studio.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hobeoung.edufactory.common.Json;
import com.hobeoung.edufactory.material.SearchService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SearchMaterialsTool implements AgentTool {

  private final SearchService search;

  public SearchMaterialsTool(SearchService search) {
    this.search = search;
  }

  @Override
  public String name() {
    return "search_materials";
  }

  @Override
  public String label() {
    return "자료 검색";
  }

  @Override
  public String description() {
    return "자료 라이브러리 의미 검색. 문항·지문·개념·예제·해설 청크를 유사도 순으로 돌려주고, 성적이 있는 문항에는 "
        + "실제 정답률·변별도·응답 수(stat)를 붙입니다. labelIds 를 생략하면 요청 조건의 참고 범위 라벨로 걸러지고, "
        + "빈 배열([])을 주면 전체에서 찾습니다. 라벨 필터는 같은 그룹 안에서는 OR, 그룹끼리는 AND 입니다.";
  }

  @Override
  public String schema() {
    return """
        {"type":"object","properties":{
          "query":{"type":"string","description":"찾을 내용 (개념·문항 상황·소재)"},
          "labelIds":{"type":"array","items":{"type":"string"}},
          "materialIds":{"type":"array","items":{"type":"string"}},
          "chunkTypes":{"type":"array","items":{"type":"string","enum":["QUESTION","PASSAGE","CONCEPT","EXPLANATION","EXAMPLE","SECTION","OTHER"]}},
          "examOnly":{"type":"boolean"},
          "scoredOnly":{"type":"boolean","description":"성적(정답률)이 있는 문항만"},
          "limit":{"type":"integer","minimum":1,"maximum":20}
        },"required":["query"]}""";
  }

  @Override
  public Result run(JsonNode input, Context ctx) {
    String query = Json.text(input, "query");
    if (query == null) {
      return Result.error("query 가 필요합니다");
    }
    boolean fromConditions = !input.has("labelIds") && !ctx.conditions().labelIds().isEmpty();
    List<UUID> labelIds = fromConditions ? ctx.conditions().labelIds() : ToolViews.uuids(input.get("labelIds"));
    List<SearchService.Hit> hits = search.search(new SearchService.Query(query, labelIds,
        ToolViews.uuids(input.get("materialIds")), ToolViews.strings(input.get("chunkTypes")),
        input.path("examOnly").asBoolean(false), input.path("limit").asInt(8),
        input.path("scoredOnly").asBoolean(false)));
    ArrayNode out = Json.array();
    for (SearchService.Hit h : hits) {
      ObjectNode n = ToolViews.chunk(h.chunk(), h.materialTitle(), 1500);
      n.put("similarity", h.similarity());
      if (!h.materialLabels().isEmpty()) {
        n.set("materialLabels", Json.MAPPER.valueToTree(ToolViews.labels(h.materialLabels())));
      }
      out.add(n);
    }
    ObjectNode result = Json.object();
    if (fromConditions) {
      result.put("labelFilter", "요청 조건의 참고 범위 라벨로 걸렀습니다 (전체 검색은 labelIds: [])");
    }
    result.set("hits", out);
    long scored = hits.stream().filter(h -> h.chunk().stat() != null).count();
    return Result.ok(result, "청크 " + hits.size() + "개" + (scored > 0 ? " · 성적 있음 " + scored : ""));
  }
}
