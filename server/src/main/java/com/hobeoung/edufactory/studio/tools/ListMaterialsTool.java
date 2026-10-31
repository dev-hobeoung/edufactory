package com.hobeoung.edufactory.studio.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hobeoung.edufactory.common.Json;
import com.hobeoung.edufactory.material.MaterialRepository;
import com.hobeoung.edufactory.material.MaterialSummary;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ListMaterialsTool implements AgentTool {

  private final MaterialRepository materials;

  public ListMaterialsTool(MaterialRepository materials) {
    this.materials = materials;
  }

  @Override
  public String name() {
    return "list_materials";
  }

  @Override
  public String label() {
    return "자료 목록";
  }

  @Override
  public String description() {
    return "자료 라이브러리의 자료 목록 (제목·종류·라벨·청크 수·시험 여부·성적 세트 수). labelIds 는 모두 붙은 자료만. "
        + "특정 자료 전체를 보려면 get_material 을 씁니다.";
  }

  @Override
  public String schema() {
    return """
        {"type":"object","properties":{
          "q":{"type":"string","description":"제목 검색어"},
          "labelIds":{"type":"array","items":{"type":"string"}},
          "kind":{"type":"string","enum":["EXAM","WORKSHEET","TEXTBOOK","NOTE","REFERENCE","OTHER"]},
          "examOnly":{"type":"boolean","description":"시험 자료만"}
        }}""";
  }

  @Override
  public Result run(JsonNode input, Context ctx) {
    List<MaterialSummary> list = materials.list(new MaterialRepository.Filter(Json.text(input, "q"),
        ToolViews.uuids(input.get("labelIds")), Json.text(input, "kind"),
        input.path("examOnly").asBoolean(false) ? Boolean.TRUE : null, "READY"));
    ArrayNode out = Json.array();
    for (MaterialSummary m : list.stream().limit(80).toList()) {
      ObjectNode n = Json.object();
      n.put("id", m.id().toString());
      n.put("title", m.title());
      n.put("kind", m.kind());
      ToolViews.putIf(n, "description", m.description());
      n.set("labels", Json.MAPPER.valueToTree(ToolViews.labels(m.labels())));
      n.put("chunks", m.chunkCount());
      n.put("isExam", m.isExam());
      if (m.scoreSetCount() > 0) {
        n.put("scoreSets", m.scoreSetCount());
      }
      out.add(n);
    }
    return Result.ok(Map.of("materials", out, "total", list.size()), "자료 " + list.size() + "개");
  }
}
