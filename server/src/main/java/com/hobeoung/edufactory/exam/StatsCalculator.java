package com.hobeoung.edufactory.exam;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** 성적 → 문항·시험 통계 (순수 계산, DB 없음) */
public final class StatsCalculator {

  /** 상·하위 집단 비율 (고전검사이론 관례 27%) */
  static final double GROUP_SHARE = 0.27;
  static final int MIN_N_DISCRIMINATION = 10;
  static final int SMALL_SAMPLE = 30;

  private StatsCalculator() {}

  public record Item(UUID chunkId, String itemNo, double maxScore, String answer) {}

  public record Entry(Double score, String response) {}

  public record ChoiceCount(String response, int count, boolean correct) {}

  public record ItemStat(UUID chunkId, String itemNo, double maxScore, int n, double avgRate, double fullRate,
      double zeroRate, Double discrimination, List<ChoiceCount> choiceDist, boolean smallSample) {}

  public record Bucket(double from, double to, int count) {}

  public record ExamStat(int n, double maxTotal, double mean, Double std, double meanRate, double p10, double p25,
      double p50, double p75, double p90, List<Bucket> histogram) {}

  public record Result(List<ItemStat> items, ExamStat exam) {}

  /** @param students 학생 키 → (청크 id → 응답) */
  public static Result compute(List<Item> items, Map<String, Map<UUID, Entry>> students) {
    double maxTotal = items.stream().mapToDouble(Item::maxScore).sum();
    // 학생별 총점·득점률 (응답한 문항 배점 기준) — 변별도 집단 나누기에 쓴다
    Map<String, Double> rate = new LinkedHashMap<>();
    List<Double> totals = new ArrayList<>();
    for (Map.Entry<String, Map<UUID, Entry>> s : students.entrySet()) {
      double got = 0;
      double possible = 0;
      boolean any = false;
      for (Item item : items) {
        Entry e = s.getValue().get(item.chunkId());
        if (e != null && e.score() != null) {
          got += e.score();
          possible += item.maxScore();
          any = true;
        }
      }
      if (any) {
        rate.put(s.getKey(), possible == 0 ? 0 : got / possible);
        totals.add(got);
      }
    }
    List<String> ranked = new ArrayList<>(rate.keySet());
    ranked.sort(Comparator.comparingDouble(rate::get));
    int groupSize = (int) Math.floor(ranked.size() * GROUP_SHARE);
    List<String> low = ranked.subList(0, groupSize);
    List<String> high = ranked.subList(ranked.size() - groupSize, ranked.size());

    List<ItemStat> itemStats = new ArrayList<>();
    for (Item item : items) {
      itemStats.add(itemStat(item, students, ranked.size() >= MIN_N_DISCRIMINATION && groupSize > 0 ? low : null,
          high));
    }
    ExamStat exam = totals.isEmpty() ? null : examStat(totals, maxTotal);
    return new Result(itemStats, exam);
  }

  private static ItemStat itemStat(Item item, Map<String, Map<UUID, Entry>> students, List<String> low,
      List<String> high) {
    int n = 0;
    double sumRate = 0;
    int full = 0;
    int zero = 0;
    Map<String, int[]> choices = new LinkedHashMap<>();
    for (Map<UUID, Entry> answers : students.values()) {
      Entry e = answers.get(item.chunkId());
      if (e == null) {
        continue;
      }
      if (e.response() != null && !e.response().isBlank()) {
        choices.computeIfAbsent(e.response().strip(), k -> new int[1])[0]++;
      }
      if (e.score() == null) {
        continue;
      }
      n++;
      double r = item.maxScore() <= 0 ? 0 : clamp(e.score() / item.maxScore());
      sumRate += r;
      if (r >= 0.9999) {
        full++;
      }
      if (r <= 0.0001) {
        zero++;
      }
    }
    Double discrimination = null;
    if (low != null) {
      double hi = groupRate(item, students, high);
      double lo = groupRate(item, students, low);
      if (!Double.isNaN(hi) && !Double.isNaN(lo)) {
        discrimination = round(hi - lo);
      }
    }
    List<ChoiceCount> dist = choices.entrySet().stream()
        .map(c -> new ChoiceCount(c.getKey(), c.getValue()[0], matches(c.getKey(), item.answer())))
        .sorted(Comparator.comparing(ChoiceCount::response))
        .toList();
    return new ItemStat(item.chunkId(), item.itemNo(), item.maxScore(), n, n == 0 ? 0 : round(sumRate / n),
        n == 0 ? 0 : round((double) full / n), n == 0 ? 0 : round((double) zero / n), discrimination, dist,
        n < SMALL_SAMPLE);
  }

  private static double groupRate(Item item, Map<String, Map<UUID, Entry>> students, List<String> group) {
    double sum = 0;
    int n = 0;
    for (String key : group) {
      Entry e = students.get(key).get(item.chunkId());
      if (e != null && e.score() != null && item.maxScore() > 0) {
        sum += clamp(e.score() / item.maxScore());
        n++;
      }
    }
    return n == 0 ? Double.NaN : sum / n;
  }

  static ExamStat examStat(List<Double> totals, double maxTotal) {
    double[] sorted = totals.stream().mapToDouble(Double::doubleValue).sorted().toArray();
    int n = sorted.length;
    double mean = Arrays.stream(sorted).average().orElse(0);
    Double std = null;
    if (n >= 2) {
      double ss = Arrays.stream(sorted).map(v -> (v - mean) * (v - mean)).sum();
      std = round(Math.sqrt(ss / (n - 1)));
    }
    int[] counts = new int[10];
    for (double t : sorted) {
      double r = maxTotal <= 0 ? 0 : clamp(t / maxTotal);
      counts[Math.min(9, (int) Math.floor(r * 10))]++;
    }
    List<Bucket> histogram = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      histogram.add(new Bucket(i / 10.0, (i + 1) / 10.0, counts[i]));
    }
    return new ExamStat(n, maxTotal, round(mean), std, maxTotal <= 0 ? 0 : round(mean / maxTotal),
        round(quantile(sorted, 0.10)), round(quantile(sorted, 0.25)), round(quantile(sorted, 0.50)),
        round(quantile(sorted, 0.75)), round(quantile(sorted, 0.90)), histogram);
  }

  static double quantile(double[] sorted, double q) {
    if (sorted.length == 0) {
      return 0;
    }
    double pos = q * (sorted.length - 1);
    int lo = (int) Math.floor(pos);
    int hi = (int) Math.ceil(pos);
    return sorted[lo] + (sorted[hi] - sorted[lo]) * (pos - lo);
  }

  /**
   * 응답이 정답과 같은지 — 공백·대소문자 무시, ①↔1 동일, 정답이 "①,③"·"1/3" 이면 그중 하나와 같으면 정답.
   * 응답이 여러 개("1,3")인 복수 선택은 정답 집합과 완전히 같아야 정답.
   */
  public static boolean matches(String response, String answer) {
    if (response == null || answer == null) {
      return false;
    }
    String r = normalize(response);
    if (r.isEmpty()) {
      return false;
    }
    List<String> answers = Arrays.stream(answer.split("[,/|]")).map(StatsCalculator::normalize)
        .filter(s -> !s.isEmpty()).sorted().toList();
    if (r.contains(",")) {
      List<String> rs = Arrays.stream(r.split(",")).filter(s -> !s.isEmpty()).sorted().toList();
      return rs.equals(answers);
    }
    return answers.contains(r);
  }

  /** 응답·정답 비교용 정규화: 공백 제거, 소문자, ①→1, '번' 제거, / | → , */
  public static String normalize(String s) {
    StringBuilder sb = new StringBuilder();
    for (char ch : s.strip().toLowerCase(Locale.ROOT).toCharArray()) {
      int circled = "①②③④⑤⑥⑦⑧⑨⑩".indexOf(ch);
      if (circled >= 0) {
        sb.append(circled + 1);
      } else if (ch == '/' || ch == '|') {
        sb.append(',');
      } else if (!Character.isWhitespace(ch) && ch != '번') {
        sb.append(ch);
      }
    }
    return sb.toString();
  }

  private static double clamp(double v) {
    return Math.max(0, Math.min(1, v));
  }

  static double round(double v) {
    return Math.round(v * 10000) / 10000.0;
  }
}
