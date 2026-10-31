package com.hobeoung.edufactory.studio.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.hobeoung.edufactory.common.Json;
import com.hobeoung.edufactory.material.Chunk;
import com.hobeoung.edufactory.material.MaterialRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class GetChunksTool implements AgentTool {

  private final MaterialRepository materials;

  public GetChunksTool(MaterialRepository materials) {
    this.materials = materials;
  }

  @Override
  public String name() {
    return "get_chunks";
  }

  @Override
  public String label() {
    return "청크 원문";
  }

  @Override
  public String description() {
    return "청크 id(또는 ref 'chunk:<id>') 목록의 전체 본문·선택지·정답·해설·통계. 검색 결과에서 잘린 본문을 볼 때 씁니다.";
  }

  @Override
  public String schema() {
    return """
        {"type":"object","properties":{
          "chunkIds":{"type":"array","items":{"type":"string"},"maxItems":20}
        },"required":["chunkIds"]}""";
  }

  @Override
  public Result run(JsonNode input, Context ctx) {
    List<UUID> ids = ToolViews.uuids(input.get("chunkIds"));
    if (ids.isEmpty()) {
      return Result.error("chunkIds 가 비었습니다");
    }
    List<Chunk> chunks = materials.chunksByIds(ids.stream().limit(20).toList());
    Map<UUID, String> titles = new HashMap<>();
    ArrayNode out = Json.array();
    for (Chunk c : chunks) {
      String title = titles.computeIfAbsent(c.materialId(),
          id -> materials.find(id).map(m -> m.title()).orElse(null));
      out.add(ToolViews.chunk(c, title, 12000));
    }
    return Result.ok(Map.of("chunks", out), "청크 " + chunks.size() + "개");
  }
}
