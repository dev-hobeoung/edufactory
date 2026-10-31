package com.hobeoung.edufactory.studio.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hobeoung.edufactory.common.Json;
import com.hobeoung.edufactory.label.Label;
import com.hobeoung.edufactory.label.LabelService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ListLabelsTool implements AgentTool {

  private final LabelService labels;

  public ListLabelsTool(LabelService labels) {
    this.labels = labels;
  }

  @Override
  public String name() {
    return "list_labels";
  }

  @Override
  public String label() {
    return "라벨 목록";
  }

  @Override
  public String description() {
    return "자료 라이브러리의 라벨(그룹/이름, 예: 과목/수학, 단원/미분)과 각 라벨이 붙은 자료·청크 수. "
        + "검색 범위를 라벨로 좁힐 때 id 를 확인하려고 씁니다.";
  }

  @Override
  public String schema() {
    return "{\"type\":\"object\",\"properties\":{}}";
  }

  @Override
  public Result run(JsonNode input, Context ctx) {
    List<Label> all = labels.list();
    Map<String, ArrayNode> groups = new LinkedHashMap<>();
    for (Label l : all) {
      ObjectNode n = Json.object();
      n.put("id", l.id().toString());
      n.put("name", l.name());
      n.put("materials", l.materialCount());
      n.put("chunks", l.chunkCount());
      groups.computeIfAbsent(l.group(), g -> Json.array()).add(n);
    }
    return Result.ok(Map.of("groups", groups), "라벨 " + all.size() + "개");
  }
}
