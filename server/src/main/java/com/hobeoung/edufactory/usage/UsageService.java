package com.hobeoung.edufactory.usage;

import com.hobeoung.edufactory.common.ApiException;
import com.hobeoung.edufactory.config.EduProperties;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/** 비용 집계 (일·용도·모델 단위) 와 월 예산 */
@Service
public class UsageService {

  public record Tokens(long input, long output, long cacheRead, long cacheWrite, long webSearches) {
    public static Tokens zero() {
      return new Tokens(0, 0, 0, 0, 0);
    }

    public Tokens plus(Tokens o) {
      return new Tokens(input + o.input, output + o.output, cacheRead + o.cacheRead, cacheWrite + o.cacheWrite,
          webSearches + o.webSearches);
    }
  }

  private final NamedParameterJdbcTemplate jdbc;
  private final EduProperties props;

  public UsageService(NamedParameterJdbcTemplate jdbc, EduProperties props) {
    this.jdbc = jdbc;
    this.props = props;
  }

  /** @return 이번 호출 비용(USD) */
  public double record(String model, String purpose, Tokens t) {
    double cost = Pricing.cost(model, t.input(), t.output(), t.cacheRead(), t.cacheWrite(), t.webSearches());
    add(model, purpose, t, cost);
    return cost;
  }

  public double recordEmbedding(String model, long tokens) {
    double cost = tokens * Pricing.VOYAGE_PER_MTOK / 1_000_000.0;
    add(model, "embedding", new Tokens(tokens, 0, 0, 0, 0), cost);
    return cost;
  }

  private void add(String model, String purpose, Tokens t, double cost) {
    jdbc.update("""
        INSERT INTO ef_usage (day, purpose, model, input_tokens, output_tokens, cache_read_tokens, cache_write_tokens,
                              web_searches, cost_usd)
        VALUES (:day, :p, :m, :in, :out, :cr, :cw, :ws, :cost)
        ON CONFLICT (day, purpose, model) DO UPDATE SET
          input_tokens = ef_usage.input_tokens + EXCLUDED.input_tokens,
          output_tokens = ef_usage.output_tokens + EXCLUDED.output_tokens,
          cache_read_tokens = ef_usage.cache_read_tokens + EXCLUDED.cache_read_tokens,
          cache_write_tokens = ef_usage.cache_write_tokens + EXCLUDED.cache_write_tokens,
          web_searches = ef_usage.web_searches + EXCLUDED.web_searches,
          cost_usd = ef_usage.cost_usd + EXCLUDED.cost_usd
        """, new MapSqlParameterSource().addValue("day", Date.valueOf(today())).addValue("p", purpose)
        .addValue("m", model == null ? "unknown" : model).addValue("in", t.input()).addValue("out", t.output())
        .addValue("cr", t.cacheRead()).addValue("cw", t.cacheWrite()).addValue("ws", t.webSearches())
        .addValue("cost", cost));
  }

  public double monthCost() {
    LocalDate first = today().withDayOfMonth(1);
    Double v = jdbc.queryForObject("SELECT coalesce(sum(cost_usd), 0) FROM ef_usage WHERE day >= :d",
        Map.of("d", Date.valueOf(first)), Double.class);
    return v == null ? 0 : v;
  }

  public double budget() {
    return props.ai().monthlyBudgetUsd();
  }

  public void assertWithinBudget() {
    if (monthCost() >= budget()) {
      throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "BUDGET_EXCEEDED",
          "이번 달 AI 예산(" + budget() + " USD)을 모두 썼습니다");
    }
  }

  public Map<String, Object> summary() {
    LocalDate first = today().withDayOfMonth(1);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("monthCostUsd", round(monthCost()));
    out.put("monthBudgetUsd", budget());
    List<Map<String, Object>> byPurpose = jdbc.query("""
        SELECT purpose, sum(cost_usd) AS cost FROM ef_usage WHERE day >= :d GROUP BY purpose ORDER BY purpose
        """, Map.of("d", Date.valueOf(first)), (rs, i) -> Map.of("purpose", rs.getString("purpose"), "costUsd",
        round(rs.getDouble("cost"))));
    out.put("byPurpose", byPurpose);
    List<Map<String, Object>> daily = jdbc.query("""
        SELECT day, sum(cost_usd) AS cost FROM ef_usage WHERE day >= :d GROUP BY day ORDER BY day
        """, Map.of("d", Date.valueOf(today().minusDays(29))), (rs, i) -> Map.of("day",
        rs.getDate("day").toLocalDate().toString(), "costUsd", round(rs.getDouble("cost"))));
    out.put("daily", daily);
    return out;
  }

  private static LocalDate today() {
    return LocalDate.now(ZoneId.of(EduProperties.ZONE));
  }

  public static double round(double v) {
    return Math.round(v * 10000) / 10000.0;
  }
}
