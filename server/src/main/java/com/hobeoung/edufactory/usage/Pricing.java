package com.hobeoung.edufactory.usage;

import java.util.Map;

/** 모델별 단가 (USD / 100만 토큰). 캐시 쓰기는 5분 TTL 기준 입력 단가의 1.25배. */
public final class Pricing {

  public record Price(double input, double output, double cacheRead) {
    double cacheWrite() {
      return input * 1.25;
    }
  }

  private static final Map<String, Price> PRICES = Map.of(
      "claude-opus-5-5", new Price(4.0, 20.0, 0.20),
      "claude-sonnet-5-5", new Price(2.0, 10.0, 0.20),
      "claude-opus-5", new Price(5.0, 25.0, 0.50),
      "claude-opus-4-8", new Price(5.0, 25.0, 0.50),
      "claude-sonnet-5", new Price(2.0, 10.0, 0.20),
      "claude-haiku-4-5", new Price(1.0, 5.0, 0.10));

  private static final Price DEFAULT = new Price(5.0, 25.0, 0.50);

  /** 웹 검색 1회 ($10 / 1,000회) */
  public static final double WEB_SEARCH_USD = 0.01;

  /** Voyage 임베딩 (voyage-3.5 기준 $0.06 / 100만 토큰) */
  public static final double VOYAGE_PER_MTOK = 0.06;

  private Pricing() {}

  public static Price of(String model) {
    if (model == null) {
      return DEFAULT;
    }
    Price exact = PRICES.get(model);
    if (exact != null) {
      return exact;
    }
    return PRICES.entrySet().stream().filter(e -> model.startsWith(e.getKey())).map(Map.Entry::getValue)
        .findFirst().orElse(DEFAULT);
  }

  public static double cost(String model, long input, long output, long cacheRead, long cacheWrite,
      long webSearches) {
    Price p = of(model);
    return (input * p.input() + output * p.output() + cacheRead * p.cacheRead() + cacheWrite * p.cacheWrite())
        / 1_000_000.0 + webSearches * WEB_SEARCH_USD;
  }
}
