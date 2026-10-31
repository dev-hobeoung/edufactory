package com.hobeoung.edufactory.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hobeoung.edufactory.common.Json;
import com.hobeoung.edufactory.material.Chunk;
import com.hobeoung.edufactory.material.SearchService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ItemAnalyzerTest {

  private static SearchService.Hit hit(double similarity, int n, double rate) {
    Chunk c = new Chunk(UUID.randomUUID(), UUID.randomUUID(), 1, "QUESTION", null, "1번", "1", null, "x", List.of(),
        null, null, 1.0, List.of(), null, null, List.of(), true,
        Json.read("{\"n\":" + n + ",\"avgRate\":" + rate + "}"));
    return new SearchService.Hit(c, c.materialId(), "시험", List.of(), similarity);
  }

  @Test
  void noScoredNeighborsMeansNone() {
    DocumentVersion.ItemPrediction p = ItemAnalyzer.predict("1", List.of(), 0.6);
    assertNull(p.expectedRate());
    assertEquals("NONE", p.confidence());
  }

  @Test
  void closeNeighborsDominateAndShrinkToPrior() {
    DocumentVersion.ItemPrediction p = ItemAnalyzer.predict("1",
        List.of(hit(0.95, 40, 0.2), hit(0.9, 40, 0.25), hit(0.88, 40, 0.3), hit(0.4, 40, 0.9)), 0.6);
    assertTrue(p.expectedRate() > 0.2 && p.expectedRate() < 0.4, String.valueOf(p.expectedRate()));
    assertEquals("HIGH", p.confidence());
  }

  @Test
  void weakEvidenceIsLowConfidence() {
    DocumentVersion.ItemPrediction p = ItemAnalyzer.predict("1", List.of(hit(0.5, 3, 0.1)), 0.6);
    assertEquals("LOW", p.confidence());
    assertTrue(p.expectedRate() > 0.1, "적은 표본은 전체 평균 쪽으로 당겨져야 한다");
  }
}
