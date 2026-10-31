package com.hobeoung.edufactory.document;

import com.hobeoung.edufactory.embedding.Embedder;
import com.hobeoung.edufactory.material.SearchService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 문항 분석 — 라이브러리와의 유사도(원문 베끼기 경고)와 성적 있는 비슷한 문항으로 낸 예상 정답률(kNN).
 *
 * <p>예상 정답률 = (Σ wᵢ·정답률ᵢ + k₀·전체 평균) / (Σ wᵢ + k₀), wᵢ = exp((유사도ᵢ − 1)/τ) · min(1, nᵢ/30).
 * 유사도가 낮거나 이웃이 적으면 전체 평균 쪽으로 당겨지고 신뢰도가 낮아진다.
 */
@Component
public class ItemAnalyzer {

  static final double TAU = 0.1;
  static final double PRIOR_STRENGTH = 0.3;
  static final double MIN_SIMILARITY = 0.3;
  static final int K = 8;

  private final Embedder embedder;
  private final SearchService search;
  private final NamedParameterJdbcTemplate jdbc;

  public ItemAnalyzer(Embedder embedder, SearchService search, NamedParameterJdbcTemplate jdbc) {
    this.embedder = embedder;
    this.search = search;
    this.jdbc = jdbc;
  }

  public record Analysis(List<DocumentVersion.SimilarHit> similar, List<DocumentVersion.ItemPrediction> prediction,
      Double maxSimilarity) {}

  public Analysis analyze(List<DocPayload.Item> items) {
    if (items.isEmpty()) {
      return new Analysis(List.of(), List.of(), null);
    }
    List<float[]> vectors = embedder.embed(items.stream().map(DocPayload.Item::searchText).toList(),
        Embedder.INPUT_QUERY);
    double prior = prior();
    List<DocumentVersion.SimilarHit> similar = new ArrayList<>();
    List<DocumentVersion.ItemPrediction> predictions = new ArrayList<>();
    Double max = null;
    for (int i = 0; i < items.size(); i++) {
      DocPayload.Item item = items.get(i);
      List<SearchService.Hit> nearest = search.searchVector(vectors.get(i),
          new SearchService.Query(null, null, null, null, null, 1, null), null);
      if (!nearest.isEmpty()) {
        SearchService.Hit h = nearest.get(0);
        similar.add(new DocumentVersion.SimilarHit(item.number(), h.chunk().id(), h.materialId(), h.materialTitle(),
            h.chunk().position(), h.similarity()));
        max = max == null ? h.similarity() : Math.max(max, h.similarity());
      }
      List<SearchService.Hit> scored = search.searchVector(vectors.get(i),
          new SearchService.Query(null, null, null, List.of("QUESTION"), null, K, true), null);
      predictions.add(predict(item.number(), scored, prior));
    }
    return new Analysis(similar, predictions, max);
  }

  /** 문항 하나 (도구 estimate_difficulty 에서도 씀) */
  public DocumentVersion.ItemPrediction predictText(String label, String text) {
    float[] v = embedder.embedQuery(text);
    List<SearchService.Hit> scored = search.searchVector(v,
        new SearchService.Query(null, null, null, List.of("QUESTION"), null, K, true), null);
    return predict(label, scored, prior());
  }

  static DocumentVersion.ItemPrediction predict(String label, List<SearchService.Hit> hits, double prior) {
    List<DocumentVersion.Neighbor> neighbors = new ArrayList<>();
    double sumW = 0;
    double sumW2 = 0;
    double sumWR = 0;
    double maxSim = 0;
    for (SearchService.Hit h : hits) {
      if (h.similarity() < MIN_SIMILARITY || h.chunk().stat() == null) {
        continue;
      }
      int n = h.chunk().stat().path("n").asInt(0);
      double rate = h.chunk().stat().path("avgRate").asDouble(0);
      if (n <= 0) {
        continue;
      }
      double w = Math.exp((h.similarity() - 1) / TAU) * Math.min(1.0, n / 30.0);
      sumW += w;
      sumW2 += w * w;
      sumWR += w * rate;
      maxSim = Math.max(maxSim, h.similarity());
      neighbors.add(new DocumentVersion.Neighbor(h.chunk().id(), h.materialTitle(), h.chunk().position(),
          h.similarity(), n, rate));
    }
    if (neighbors.isEmpty()) {
      return new DocumentVersion.ItemPrediction(label, null, "NONE", List.of());
    }
    double estimate = (sumWR + PRIOR_STRENGTH * prior) / (sumW + PRIOR_STRENGTH);
    double effective = sumW2 == 0 ? 0 : sumW * sumW / sumW2;
    // 유효 이웃 수 = (Σw)²/Σw². τ=0.1 이라 유사도 0.9 안팎 이웃 셋이면 2.5~3 정도가 된다
    String confidence = maxSim >= 0.8 && effective >= 2.5 ? "HIGH"
        : maxSim >= 0.65 && effective >= 1.5 ? "MEDIUM" : "LOW";
    return new DocumentVersion.ItemPrediction(label, Math.round(estimate * 1000) / 1000.0, confidence, neighbors);
  }

  /** 성적 있는 모든 문항의 응답 수 가중 평균 정답률 (없으면 0.6) */
  double prior() {
    Map<String, Object> row = jdbc.queryForMap(
        "SELECT sum(avg_rate * n) / nullif(sum(n), 0) AS r FROM ef_item_stat", Map.of());
    Object r = row.get("r");
    return r == null ? 0.6 : ((Number) r).doubleValue();
  }
}
