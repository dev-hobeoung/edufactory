package com.hobeoung.edufactory.embedding;

import com.hobeoung.edufactory.common.Vectors;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Voyage 키가 없을 때 쓰는 개발용 임베딩 — 단어 + 글자 2·3-gram 을 해시해 벡터로 만든다.
 * 의미 검색 품질은 낮지만 키 없이도 업로드·검색·유사도 흐름 전체를 돌려 볼 수 있다.
 */
public class LocalHashEmbedder implements Embedder {

  public static final String MODEL = "local-hash-v1";

  private final int dimension;

  public LocalHashEmbedder(int dimension) {
    this.dimension = dimension;
  }

  @Override
  public String provider() {
    return "local";
  }

  @Override
  public String model() {
    return MODEL;
  }

  @Override
  public int dimension() {
    return dimension;
  }

  @Override
  public List<float[]> embed(List<String> texts, String inputType) {
    List<float[]> out = new ArrayList<>(texts.size());
    for (String text : texts) {
      out.add(vector(text));
    }
    return out;
  }

  float[] vector(String text) {
    float[] v = new float[dimension];
    String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
    for (String word : normalized.split("[^\\p{L}\\p{N}]+")) {
      if (word.isEmpty()) {
        continue;
      }
      add(v, "w:" + word, 1.0f);
      String padded = " " + word + " ";
      for (int n = 2; n <= 3; n++) {
        for (int i = 0; i + n <= padded.length(); i++) {
          add(v, n + ":" + padded.substring(i, i + n), 0.5f);
        }
      }
    }
    return Vectors.normalize(v);
  }

  private void add(float[] v, String feature, float weight) {
    int h = murmur(feature);
    int index = Math.floorMod(h, dimension);
    v[index] += (h & 0x40000000) == 0 ? weight : -weight;
  }

  private static int murmur(String s) {
    int h = 0x9747b28c;
    for (int i = 0; i < s.length(); i++) {
      int k = s.charAt(i) * 0xcc9e2d51;
      k = Integer.rotateLeft(k, 15) * 0x1b873593;
      h ^= k;
      h = Integer.rotateLeft(h, 13) * 5 + 0xe6546b64;
    }
    h ^= s.length();
    h ^= h >>> 16;
    h *= 0x85ebca6b;
    h ^= h >>> 13;
    h *= 0xc2b2ae35;
    return h ^ (h >>> 16);
  }
}
