package com.hobeoung.edufactory.embedding;

import java.util.List;

/** 텍스트 → 정규화된 벡터. 모델이 다르면 같은 인덱스에서 비교하지 않는다 (ef_chunk.embedding_model). */
public interface Embedder {

  String INPUT_DOCUMENT = "document";
  String INPUT_QUERY = "query";

  String provider();

  String model();

  int dimension();

  List<float[]> embed(List<String> texts, String inputType);

  default float[] embedQuery(String text) {
    return embed(List.of(text), INPUT_QUERY).get(0);
  }
}
