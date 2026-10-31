package com.hobeoung.edufactory.studio.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hobeoung.edufactory.common.Json;
import com.hobeoung.edufactory.material.Chunk;
import com.hobeoung.edufactory.material.MaterialRepository;
import com.hobeoung.edufactory.material.MaterialSummary;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class GetMaterialTool implements AgentTool {

  static final int MAX_CHARS = 40_000;

  private final MaterialRepository materials;

  public GetMaterialTool(MaterialRepository materials) {
    this.materials = materials;
  }

  @Override
  public String name() {
    return "get_material";
  }

  @Override
  public String label() {
    return "자료 전체 보기";
  }

  @Override
  public String description() {
    return "자료 하나의 구조화된 청크 전체 (순서대로). 시험지 형식·학습지 구성을 그대로 참고할 때 씁니다. "
        + "chunkTypes 로 일부 유형만 볼 수 있습니다. 4만 자를 넘으면 뒤가 잘립니다.";
  }

  @Override
  public String schema() {
    return """
        {"type":"object","properties":{
          "materialId":{"type":"string"},
          "chunkTypes":{"type":"array","items":{"type":"string","enum":["QUESTION","PASSAGE","CONCEPT","EXPLANATION","EXAMPLE","SECTION","OTHER"]}}
        },"required":["materialId"]}""";
  }

  @Override
  public Result run(JsonNode input, Context ctx) {
    List<UUID> ids = ToolViews.uuids(Json.array().add(input.path("materialId").asText()));
    if (ids.isEmpty()) {
      return Result.error("materialId 가 올바르지 않습니다");
    }
    MaterialSummary m = materials.find(ids.get(0)).orElse(null);
    if (m == null) {
      return Result.error("자료를 찾을 수 없습니다 — list_materials 로 id 를 확인하세요");
    }
    List<String> types = ToolViews.strings(input.get("chunkTypes"));
    ObjectNode out = Json.object();
    out.put("id", m.id().toString());
    out.put("title", m.title());
    out.put("kind", m.kind());
    out.put("isExam", m.isExam());
    out.set("labels", Json.MAPPER.valueToTree(ToolViews.labels(m.labels())));
    ArrayNode chunks = out.putArray("chunks");
    int used = 0;
    int total = 0;
    for (Chunk c : materials.chunks(m.id())) {
      if (!types.isEmpty() && !types.contains(c.chunkType())) {
        continue;
      }
      total++;
      if (used > MAX_CHARS) {
        continue;
      }
      ObjectNode n = ToolViews.chunk(c, null, 6000);
      n.remove("materialId");
      used += n.toString().length();
      chunks.add(n);
    }
    if (chunks.size() < total) {
      out.put("truncated", "청크 " + total + "개 중 " + chunks.size() + "개만 담았습니다");
    }
    return Result.ok(out, m.title() + " · 청크 " + chunks.size() + "개");
  }
}
