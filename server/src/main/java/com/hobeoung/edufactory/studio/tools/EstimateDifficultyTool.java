package com.hobeoung.edufactory.studio.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.hobeoung.edufactory.common.Json;
import com.hobeoung.edufactory.document.DocumentVersion;
import com.hobeoung.edufactory.document.ItemAnalyzer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class EstimateDifficultyTool implements AgentTool {

  private final ItemAnalyzer analyzer;

  public EstimateDifficultyTool(ItemAnalyzer analyzer) {
    this.analyzer = analyzer;
  }

  @Override
  public String name() {
    return "estimate_difficulty";
  }

  @Override
  public String label() {
    return "예상 정답률";
  }

  @Override
  public String description() {
    return "구상 중인 문항의 예상 정답률 — 성적이 있는 비슷한 문항(kNN)의 실제 정답률을 유사도·응답 수로 가중 평균하고 "
        + "전체 평균 쪽으로 보정합니다. 신뢰도(HIGH~NONE)와 근거 이웃 문항을 함께 돌려줍니다. "
        + "이 계산은 문장 유사도만 보므로 계산량·조건 차이는 직접 보정하세요.";
  }

  @Override
  public String schema() {
    return """
        {"type":"object","properties":{
          "items":{"type":"array","maxItems":30,"items":{"type":"object","properties":{
            "label":{"type":"string","description":"문항 번호 등"},
            "text":{"type":"string","description":"발문 + 선택지"}
          },"required":["label","text"]}}
        },"required":["items"]}""";
  }

  @Override
  public Result run(JsonNode input, Context ctx) {
    List<DocumentVersion.ItemPrediction> out = new ArrayList<>();
    for (JsonNode item : input.path("items")) {
      String text = Json.text(item, "text");
      if (text != null) {
        out.add(analyzer.predictText(item.path("label").asText("?"), text));
      }
    }
    if (out.isEmpty()) {
      return Result.error("items 가 비었습니다");
    }
    long known = out.stream().filter(p -> p.expectedRate() != null).count();
    return Result.ok(Map.of("predictions", out, "note",
        "expectedRate 가 null 이면 성적 있는 비슷한 문항이 없다는 뜻입니다 — 일반적인 판단으로 정하고 근거를 밝히세요"),
        "문항 " + out.size() + "개 · 예측 " + known);
  }
}
