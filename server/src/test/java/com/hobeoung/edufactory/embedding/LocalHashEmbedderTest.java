package com.hobeoung.edufactory.embedding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class LocalHashEmbedderTest {

  private static double cos(float[] a, float[] b) {
    double s = 0;
    for (int i = 0; i < a.length; i++) {
      s += a[i] * b[i];
    }
    return s;
  }

  @Test
  void similarTextsAreCloserThanUnrelated() {
    LocalHashEmbedder e = new LocalHashEmbedder(1024);
    List<float[]> v = e.embed(List.of("이차함수의 최댓값과 최솟값", "이차함수 최솟값 구하기", "광합성과 엽록체"),
        Embedder.INPUT_DOCUMENT);
    assertEquals(1024, v.get(0).length);
    assertEquals(1.0, cos(v.get(0), v.get(0)), 1e-5);
    assertTrue(cos(v.get(0), v.get(1)) > cos(v.get(0), v.get(2)));
  }
}
