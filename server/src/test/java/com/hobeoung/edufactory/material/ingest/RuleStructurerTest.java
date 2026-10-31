package com.hobeoung.edufactory.material.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hobeoung.edufactory.material.ChunkInput;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuleStructurerTest {

  private final RuleStructurer structurer = new RuleStructurer();

  private static StructureRequest req(String text, boolean exam) {
    return new StructureRequest("t", exam ? "EXAM" : "NOTE", null, exam, List.of(),
        new ExtractedText(text, List.of(text), null));
  }

  @Test
  void splitsNumberedItemsAndChoices() {
    String text = """
        [1~2] 다음 글을 읽고 물음에 답하시오.
        광합성은 빛 에너지를 이용한다.
        1. 광합성에 필요한 것은?
        ① 빛 ② 소리 ③ 자기장
        2) 광합성이 일어나는 곳을 쓰시오.
        (단, 2) 조건은 무시)
        3. 마지막 문제
        """;
    List<ChunkInput> chunks = structurer.structure(req(text, true));
    assertEquals("PASSAGE", chunks.get(0).chunkType());
    List<ChunkInput> qs = chunks.stream().filter(c -> "QUESTION".equals(c.chunkType())).toList();
    assertEquals(3, qs.size());
    assertEquals("1", qs.get(0).itemNo());
    assertEquals("MULTIPLE_CHOICE", qs.get(0).questionType());
    assertEquals(3, qs.get(0).choices().size());
    assertEquals("①", qs.get(0).choices().get(0).label());
    assertEquals("빛", qs.get(0).choices().get(0).text());
    assertEquals("SHORT_ANSWER", qs.get(1).questionType());
    assertTrue(qs.get(1).content().contains("조건은 무시"));   // 본문 속 "2)" 는 새 문항이 아님
  }

  @Test
  void nonExamBecomesSections() {
    String text = "# 미분\n\n도함수의 정의.\n\n# 적분\n\n넓이.";
    List<ChunkInput> chunks = structurer.structure(req(text, false));
    assertEquals(2, chunks.size());
    assertEquals("미분", chunks.get(0).title());
    assertEquals("SECTION", chunks.get(1).chunkType());
  }
}
