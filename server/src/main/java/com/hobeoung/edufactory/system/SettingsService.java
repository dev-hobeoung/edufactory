package com.hobeoung.edufactory.system;

import com.hobeoung.edufactory.common.Json;
import com.hobeoung.edufactory.config.EduProperties;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/** 화면에서 바꾸는 설정 (ef_setting 'settings'). 월 예산은 서버 설정이라 읽기 전용 */
@Service
public class SettingsService {

  public static final List<String> MODELS = List.of("claude-opus-5-5", "claude-sonnet-5-5");
  public static final List<String> EFFORTS = List.of("low", "medium", "high", "xhigh");

  public record Settings(String model, String effort, double similarityWarn, int maxToolCalls,
      double monthlyBudgetUsd) {}

  private final NamedParameterJdbcTemplate jdbc;
  private final EduProperties props;

  public SettingsService(NamedParameterJdbcTemplate jdbc, EduProperties props) {
    this.jdbc = jdbc;
    this.props = props;
  }

  public Settings get() {
    List<String> rows = jdbc.queryForList("SELECT value::text FROM ef_setting WHERE key = 'settings'", Map.of(),
        String.class);
    Settings stored = rows.isEmpty() ? null : Json.read(rows.get(0), Settings.class);
    return normalize(stored);
  }

  public Settings save(Settings in) {
    Settings s = normalize(in);
    jdbc.update("""
        INSERT INTO ef_setting (key, value) VALUES ('settings', CAST(:v AS jsonb))
        ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value
        """, Map.of("v", Json.write(s)));
    return s;
  }

  private Settings normalize(Settings s) {
    EduProperties.Ai ai = props.ai();
    if (s == null) {
      return new Settings(ai.model(), ai.effort(), 0.92, ai.maxToolCalls(), ai.monthlyBudgetUsd());
    }
    return new Settings(MODELS.contains(s.model()) ? s.model() : ai.model(),
        EFFORTS.contains(s.effort()) ? s.effort() : ai.effort(),
        s.similarityWarn() <= 0 || s.similarityWarn() > 1 ? 0.92 : s.similarityWarn(),
        s.maxToolCalls() <= 0 ? ai.maxToolCalls() : Math.min(60, s.maxToolCalls()), ai.monthlyBudgetUsd());
  }
}
