package com.hobeoung.edufactory.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hobeoung.edufactory.material.Choice;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocPayloadTest {

  private static DocPayload.Item mc(String no, String answer, Double score) {
    return new DocPayload.Item(no, "MULTIPLE_CHOICE", "문제", List.of(new Choice("①", "a"), new Choice("②", "b")),
        answer, "해설", score, List.of(), 0.5, List.of(), List.of());
  }

  private static DocPayload doc(DocPayload.Item... items) {
    return new DocPayload("제목", "EXAM", null, null, null, List.of(new DocPayload.Section(null, null, List.of(items))),
        List.of(), null);
  }

  @Test
  void validPayloadPasses() {
    DocPayload p = doc(mc("1", "①", 2.0), mc("2", "2", 3.0));
    assertEquals(List.of(), p.validate());
    assertEquals(5.0, p.totalScore());
  }

  @Test
  void catchesStructuralErrors() {
    DocPayload.Item essay = new DocPayload.Item("3", "ESSAY", "서술", List.of(), "모범답안", null, 5.0,
        List.of(new DocPayload.Criterion("a", 2), new DocPayload.Criterion("b", 2)), null, List.of(), List.of());
    List<String> errors = doc(mc("1", "③", 1.0), mc("1", "①", 1.0), essay).validate();
    assertTrue(errors.stream().anyMatch(e -> e.contains("선택지 표기에 없습니다")), errors.toString());
    assertTrue(errors.stream().anyMatch(e -> e.contains("중복")), errors.toString());
    assertTrue(errors.stream().anyMatch(e -> e.contains("채점기준 배점 합")), errors.toString());
  }

  @Test
  void unknownKindFallsBackToOther() {
    DocPayload p = new DocPayload("t", "WHATEVER", null, null, null,
        List.of(new DocPayload.Section("개념", "본문", null)), null, null);
    assertEquals("OTHER", p.kind());
    assertEquals(List.of(), p.validate());
  }

  @Test
  void markdownExportHidesAnswersForStudents() {
    DocPayload p = doc(mc("1", "①", 2.0));
    String student = MarkdownExporter.export(p, false);
    String teacher = MarkdownExporter.export(p, true);
    assertTrue(!student.contains("정답"));
    assertTrue(teacher.contains("정답: ①"));
  }
}
