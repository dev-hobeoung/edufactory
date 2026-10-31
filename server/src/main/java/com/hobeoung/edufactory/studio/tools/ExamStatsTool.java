package com.hobeoung.edufactory.studio.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hobeoung.edufactory.common.Json;
import com.hobeoung.edufactory.exam.ScoreService;
import com.hobeoung.edufactory.exam.StatsCalculator;
import com.hobeoung.edufactory.material.MaterialRepository;
import com.hobeoung.edufactory.material.MaterialSummary;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ExamStatsTool implements AgentTool {

  private final ScoreService scores;
  private final MaterialRepository materials;

  public ExamStatsTool(ScoreService scores, MaterialRepository materials) {
    this.scores = scores;
    this.materials = materials;
  }

  @Override
  public String name() {
    return "get_exam_stats";
  }

  @Override
  public String label() {
    return "시험 성적 통계";
  }

  @Override
  public String description() {
    return "시험 자료 하나의 성적 통계: 응시자 수, 총점 평균·표준편차·분위수(p10~p90), 10구간 분포, 문항별 정답률·만점률·"
        + "변별도(상위27%−하위27% 정답률)·응답(선택지) 분포. 시험 전체 난이도·문항 구성의 기준으로 씁니다.";
  }

  @Override
  public String schema() {
    return "{\"type\":\"object\",\"properties\":{\"materialId\":{\"type\":\"string\"}},\"required\":[\"materialId\"]}";
  }

  @Override
  public Result run(JsonNode input, Context ctx) {
    List<UUID> ids = ToolViews.uuids(Json.array().add(input.path("materialId").asText()));
    MaterialSummary m = ids.isEmpty() ? null : materials.find(ids.get(0)).orElse(null);
    if (m == null || !m.isExam()) {
      return Result.error("시험 자료를 찾을 수 없습니다 — list_materials(examOnly=true) 로 id 를 확인하세요");
    }
    StatsCalculator.ExamStat stat = scores.examStat(m.id());
    ObjectNode out = Json.object();
    out.put("materialId", m.id().toString());
    out.put("title", m.title());
    out.set("exam", stat == null ? Json.MAPPER.nullNode() : Json.MAPPER.valueToTree(stat));
    if (stat == null) {
      out.put("note", "아직 입력된 성적이 없습니다");
    }
    ArrayNode items = out.putArray("items");
    for (ScoreService.ExamItem item : scores.examItems(m.id())) {
      ObjectNode n = Json.object();
      n.put("ref", "chunk:" + item.chunkId());
      ToolViews.putIf(n, "itemNo", item.itemNo());
      ToolViews.putIf(n, "questionType", item.questionType());
      n.put("maxScore", item.maxScore());
      ToolViews.putIf(n, "answer", item.answer());
      n.put("preview", item.preview());
      if (item.stat() != null) {
        n.set("stat", ToolViews.stat(Json.MAPPER.valueToTree(item.stat())));
      }
      items.add(n);
    }
    return Result.ok(out, m.title() + (stat == null ? " · 성적 없음" : " · 응시 " + stat.n() + "명"));
  }
}
