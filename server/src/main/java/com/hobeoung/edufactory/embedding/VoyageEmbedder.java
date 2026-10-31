package com.hobeoung.edufactory.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hobeoung.edufactory.common.Json;
import com.hobeoung.edufactory.common.Vectors;
import com.hobeoung.edufactory.usage.UsageService;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Voyage AI 임베딩 (/v1/embeddings). Anthropic 은 임베딩 모델을 제공하지 않아 Voyage 를 쓴다. */
public class VoyageEmbedder implements Embedder {

  private static final Logger log = LoggerFactory.getLogger(VoyageEmbedder.class);
  private static final int BATCH = 64;
  private static final int MAX_RETRIES = 3;

  private final String apiKey;
  private final String baseUrl;
  private final String model;
  private final int dimension;
  private final UsageService usage;
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

  public VoyageEmbedder(String apiKey, String baseUrl, String model, int dimension, UsageService usage) {
    this.apiKey = apiKey;
    this.baseUrl = baseUrl;
    this.model = model;
    this.dimension = dimension;
    this.usage = usage;
  }

  @Override
  public String provider() {
    return "voyage";
  }

  @Override
  public String model() {
    return model;
  }

  @Override
  public int dimension() {
    return dimension;
  }

  @Override
  public List<float[]> embed(List<String> texts, String inputType) {
    List<float[]> out = new ArrayList<>(texts.size());
    for (int start = 0; start < texts.size(); start += BATCH) {
      List<String> batch = texts.subList(start, Math.min(texts.size(), start + BATCH));
      ObjectNode body = Json.object();
      body.put("model", model);
      body.put("input_type", inputType);
      body.put("output_dimension", dimension);
      body.set("input", Json.MAPPER.valueToTree(batch.stream().map(t -> t == null || t.isBlank() ? " " : t).toList()));
      JsonNode response = request(body);
      float[][] ordered = new float[batch.size()][];
      for (JsonNode item : response.path("data")) {
        JsonNode e = item.path("embedding");
        float[] v = new float[e.size()];
        for (int i = 0; i < v.length; i++) {
          v[i] = (float) e.get(i).asDouble();
        }
        if (v.length != dimension) {
          throw new IllegalStateException("임베딩 차원(" + v.length + ")이 설정(" + dimension + ")과 다릅니다");
        }
        ordered[item.path("index").asInt()] = Vectors.normalize(v);
      }
      for (float[] v : ordered) {
        if (v == null) {
          throw new IllegalStateException("Voyage 응답에 빠진 임베딩이 있습니다");
        }
        out.add(v);
      }
      usage.recordEmbedding(model, response.path("usage").path("total_tokens").asLong(0));
    }
    return out;
  }

  private JsonNode request(ObjectNode body) {
    HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/embeddings"))
        .timeout(Duration.ofSeconds(120))
        .header("Authorization", "Bearer " + apiKey)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(Json.write(body)))
        .build();
    IOException last = null;
    for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
      try {
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 200) {
          return Json.read(res.body());
        }
        if (res.statusCode() == 429 || res.statusCode() >= 500) {
          log.warn("Voyage 재시도 {} status={}", attempt + 1, res.statusCode());
          sleep(attempt);
          continue;
        }
        throw new IllegalStateException("Voyage 임베딩 오류 (" + res.statusCode() + "): " + res.body());
      } catch (IOException e) {
        last = e;
        sleep(attempt);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("임베딩 요청이 중단되었습니다", e);
      }
    }
    throw new IllegalStateException("Voyage 임베딩 요청 실패", last);
  }

  private static void sleep(int attempt) {
    try {
      Thread.sleep((long) (1000 * Math.pow(2, attempt)));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
