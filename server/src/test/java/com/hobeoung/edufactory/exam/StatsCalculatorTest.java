package com.hobeoung.edufactory.exam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StatsCalculatorTest {

  @Test
  void matchesNormalizesCircledNumbersAndMultipleAnswers() {
    assertTrue(StatsCalculator.matches("3", "③"));
    assertTrue(StatsCalculator.matches(" ③ ", "3"));
    assertTrue(StatsCalculator.matches("3번", "③"));
    assertTrue(StatsCalculator.matches("b", "B"));
    assertTrue(StatsCalculator.matches("2", "①,②"));          // 정답이 여럿이면 그중 하나
    assertTrue(StatsCalculator.matches("1,3", "③,①"));        // 복수 선택은 집합이 같아야
    assertFalse(StatsCalculator.matches("1", "③"));
    assertFalse(StatsCalculator.matches("1,2", "①,③"));
    assertFalse(StatsCalculator.matches("", "1"));
    assertFalse(StatsCalculator.matches("1", null));
  }

  @Test
  void itemAndExamStats() {
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    List<StatsCalculator.Item> items = List.of(new StatsCalculator.Item(a, "1", 2, "①"),
        new StatsCalculator.Item(b, "2", 3, null));
    Map<String, Map<UUID, StatsCalculator.Entry>> students = new LinkedHashMap<>();
    // 학생 10명: i 번째 학생은 1번을 i>=4 일 때 맞히고, 2번은 i*0.3 점
    for (int i = 0; i < 10; i++) {
      Map<UUID, StatsCalculator.Entry> e = new HashMap<>();
      boolean correct = i >= 4;
      e.put(a, new StatsCalculator.Entry(correct ? 2.0 : 0.0, correct ? "①" : "②"));
      e.put(b, new StatsCalculator.Entry(i * 0.3, null));
      students.put("s" + i, e);
    }
    StatsCalculator.Result r = StatsCalculator.compute(items, students);
    StatsCalculator.ItemStat s1 = r.items().get(0);
    assertEquals(10, s1.n());
    assertEquals(0.6, s1.avgRate(), 1e-9);
    assertEquals(0.6, s1.fullRate(), 1e-9);
    assertEquals(0.4, s1.zeroRate(), 1e-9);
    assertTrue(s1.smallSample());
    // 상위 27%(2명)는 모두 정답, 하위 27%(2명)는 모두 오답 → 변별도 1
    assertEquals(1.0, s1.discrimination(), 1e-9);
    assertEquals(2, s1.choiceDist().size());
    assertTrue(s1.choiceDist().stream().anyMatch(c -> c.response().equals("①") && c.correct() && c.count() == 6));

    StatsCalculator.ExamStat exam = r.exam();
    assertNotNull(exam);
    assertEquals(10, exam.n());
    assertEquals(5.0, exam.maxTotal(), 1e-9);
    // 총점 = (i>=4 ? 2 : 0) + 0.3i → 평균 = 1.2 + 1.35 = 2.55
    assertEquals(2.55, exam.mean(), 1e-9);
    assertEquals(10, exam.histogram().stream().mapToInt(StatsCalculator.Bucket::count).sum());
  }

  @Test
  void discriminationNeedsTenStudents() {
    UUID a = UUID.randomUUID();
    Map<String, Map<UUID, StatsCalculator.Entry>> students = new LinkedHashMap<>();
    for (int i = 0; i < 5; i++) {
      students.put("s" + i, Map.of(a, new StatsCalculator.Entry(i % 2 == 0 ? 1.0 : 0.0, null)));
    }
    StatsCalculator.Result r = StatsCalculator.compute(List.of(new StatsCalculator.Item(a, "1", 1, null)), students);
    assertNull(r.items().get(0).discrimination());
  }

  @Test
  void noScoresMeansNoExamStat() {
    UUID a = UUID.randomUUID();
    StatsCalculator.Result r = StatsCalculator.compute(List.of(new StatsCalculator.Item(a, "1", 1, null)), Map.of());
    assertNull(r.exam());
    assertEquals(0, r.items().get(0).n());
  }

  @Test
  void quantileInterpolates() {
    double[] v = {0, 10, 20, 30};
    assertEquals(15, StatsCalculator.quantile(v, 0.5), 1e-9);
    assertEquals(0, StatsCalculator.quantile(v, 0), 1e-9);
    assertEquals(30, StatsCalculator.quantile(v, 1), 1e-9);
  }

  @Test
  void scoreTableParsesTsvAndQuotedCsv() {
    List<String[]> tsv = ScoreTable.parse("학생\t1\t2\nA\t1\t\nB\t0\t3\n");
    assertEquals(3, tsv.size());
    assertEquals("", tsv.get(1)[2]);
    List<String[]> csv = ScoreTable.parse("학생,1번,\"Q2\"\n\"김, 철수\",1,2");
    assertEquals("김, 철수", csv.get(1)[0]);
    UUID one = UUID.randomUUID();
    UUID two = UUID.randomUUID();
    List<StatsCalculator.Item> items = List.of(new StatsCalculator.Item(one, "1", 1, null),
        new StatsCalculator.Item(two, "2", 1, null));
    assertEquals(one, ScoreTable.matchItem("1번", items));
    assertEquals(two, ScoreTable.matchItem("Q2", items));
    assertEquals(two, ScoreTable.matchItem("문항 2", items));
    assertNull(ScoreTable.matchItem("총점", items));
  }
}
