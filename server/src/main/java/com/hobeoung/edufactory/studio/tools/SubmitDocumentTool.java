package com.hobeoung.edufactory.studio.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hobeoung.edufactory.common.Json;
import com.hobeoung.edufactory.document.DocPayload;
import com.hobeoung.edufactory.document.DocumentService;
import com.hobeoung.edufactory.document.DocumentVersion;
import org.springframework.stereotype.Component;

@Component
public class SubmitDocumentTool implements AgentTool {

  private final DocumentService documents;

  public SubmitDocumentTool(DocumentService documents) {
    this.documents = documents;
  }

  @Override
  public String name() {
    return "submit_document";
  }

  @Override
  public String label() {
    return "산출물 제출";
  }

  @Override
  public String description() {
    return "만든(또는 고친) 문제·교육자료를 제출합니다. 서버가 구조를 검증하고, 문항마다 라이브러리 유사도(원문 베끼기 경고)와 "
        + "성적 기반 예상 정답률을 계산해 새 버전으로 저장합니다. 검증 오류·경고가 오면 고쳐서 다시 제출하세요.";
  }

  /** 깊게 중첩된 스키마라 strict 를 쓰지 않고 DocPayload.validate() 로 검증한다 */
  @Override
  public String schema() {
    return """
        {"type":"object","properties":{"document":{"type":"object","properties":{
          "title":{"type":"string"},
          "kind":{"type":"string","enum":["EXAM","QUIZ","WORKSHEET","LESSON_NOTE","SUMMARY","FLASHCARDS","EXPLANATION","OTHER"]},
          "audience":{"type":["string","null"]},
          "summary":{"type":["string","null"],"description":"자료 개요·출제 의도 (교사용)"},
          "instructions":{"type":["string","null"],"description":"학생 안내문·학습 목표"},
          "sections":{"type":"array","items":{"type":"object","properties":{
            "title":{"type":["string","null"]},
            "body":{"type":["string","null"],"description":"설명·지문·개념 정리 (마크다운 + LaTeX)"},
            "items":{"type":"array","items":{"type":"object","properties":{
              "number":{"type":"string"},
              "type":{"type":"string","enum":["MULTIPLE_CHOICE","MULTI_SELECT","TRUE_FALSE","SHORT_ANSWER","FILL_BLANK","ESSAY","MATCHING","ORDERING","FLASHCARD"]},
              "stem":{"type":"string"},
              "choices":{"type":"array","items":{"type":"object","properties":{"label":{"type":"string"},"text":{"type":"string"}},"required":["label","text"]}},
              "answer":{"type":"string"},
              "explanation":{"type":["string","null"]},
              "score":{"type":["number","null"]},
              "criteria":{"type":"array","items":{"type":"object","properties":{"text":{"type":"string"},"score":{"type":"number"}},"required":["text","score"]}},
              "difficulty":{"type":["number","null"],"description":"예상 정답률 0~1"},
              "tags":{"type":"array","items":{"type":"string"}},
              "sourceRefs":{"type":"array","items":{"type":"string"},"description":"참고한 청크 ref (chunk:<id>)"}
            },"required":["number","type","stem","choices","answer"]}}
          },"required":["items"]}},
          "evidence":{"type":"array","items":{"type":"object","properties":{
            "kind":{"type":"string","enum":["chunk","item_stat","exam_stat","rule","material","web"]},
            "ref":{"type":"string"},"summary":{"type":"string"}},"required":["kind","ref","summary"]}},
          "changeNote":{"type":["string","null"]}
        },"required":["title","kind","sections","evidence"]}},"required":["document"]}""";
  }

  @Override
  public Result run(JsonNode input, Context ctx) {
    JsonNode doc = input.path("document");
    if (!doc.isObject()) {
      return Result.error("document 객체가 필요합니다");
    }
    DocPayload payload;
    try {
      payload = Json.convert(doc, DocPayload.class);
    } catch (IllegalArgumentException e) {
      return Result.error("document 형식을 읽을 수 없습니다: " + e.getMessage());
    }
    DocumentVersion v;
    try {
      v = documents.submit(null, ctx.conversationId(), payload, ctx.conditions(), ctx.userMessageId());
    } catch (DocumentService.InvalidPayload e) {
      ObjectNode err = Json.object();
      err.put("error", "검증 실패 — 고쳐서 다시 제출하세요");
      err.set("errors", Json.MAPPER.valueToTree(e.errors()));
      return new Result(Json.write(err), "검증 오류 " + e.errors().size() + "건", true, null);
    }
    ObjectNode out = Json.object();
    out.put("version", v.version());
    out.put("items", payload.items().size());
    if (payload.totalScore() != null) {
      out.put("totalScore", payload.totalScore());
    }
    out.set("warnings", Json.MAPPER.valueToTree(v.warnings()));
    ArrayNode pred = out.putArray("prediction");
    for (DocumentVersion.ItemPrediction p : v.prediction()) {
      ObjectNode n = Json.object();
      n.put("item", p.itemNumber());
      if (p.expectedRate() != null) {
        n.put("expectedRate", p.expectedRate());
      }
      n.put("confidence", p.confidence());
      pred.add(n);
    }
    ArrayNode sim = out.putArray("maxSimilarity");
    for (DocumentVersion.SimilarHit h : v.similar()) {
      sim.add(h.itemNumber() + ": " + h.similarity() + " (" + h.materialTitle() + ")");
    }
    return new Result(Json.write(out), "v" + v.version() + (v.warnings().isEmpty() ? "" : " · 경고 " + v.warnings().size()),
        false, v);
  }
}
