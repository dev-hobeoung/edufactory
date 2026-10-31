package com.hobeoung.edufactory.document;

import java.util.List;
import java.util.UUID;

public record DocumentVersion(UUID documentId, int version, DocPayload payload, List<String> warnings,
    List<SimilarHit> similar, List<ItemPrediction> prediction, String createdAt) {

  public record SimilarHit(String itemNumber, UUID chunkId, UUID materialId, String materialTitle, String position,
      double similarity) {}

  public record Neighbor(UUID chunkId, String materialTitle, String position, double similarity, int n,
      double avgRate) {}

  public record ItemPrediction(String itemNumber, Double expectedRate, String confidence, List<Neighbor> neighbors) {}
}
