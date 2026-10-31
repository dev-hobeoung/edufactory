package com.hobeoung.edufactory.system;

import com.hobeoung.edufactory.ai.ClaudeClientProvider;
import com.hobeoung.edufactory.embedding.Embedder;
import com.hobeoung.edufactory.usage.UsageService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SystemController {

  private final SettingsService settings;
  private final UsageService usage;
  private final ClaudeClientProvider claude;
  private final Embedder embedder;
  private final JdbcTemplate jdbc;

  public SystemController(SettingsService settings, UsageService usage, ClaudeClientProvider claude,
      Embedder embedder, JdbcTemplate jdbc) {
    this.settings = settings;
    this.usage = usage;
    this.claude = claude;
    this.embedder = embedder;
    this.jdbc = jdbc;
  }

  @GetMapping("/health")
  public Map<String, Object> health() {
    return Map.of("ok", true);
  }

  @GetMapping("/settings")
  public SettingsService.Settings getSettings() {
    return settings.get();
  }

  @PutMapping("/settings")
  public SettingsService.Settings saveSettings(@RequestBody SettingsService.Settings in) {
    return settings.save(in);
  }

  @GetMapping("/usage")
  public Map<String, Object> usage() {
    return usage.summary();
  }

  @GetMapping("/system/status")
  public Map<String, Object> status() {
    Map<String, Object> out = new LinkedHashMap<>();
    Map<String, Object> db = new LinkedHashMap<>();
    Map<String, Object> counts = new LinkedHashMap<>();
    try {
      counts.put("materials", count("SELECT count(*) FROM ef_material"));
      counts.put("chunks", count("SELECT count(*) FROM ef_chunk"));
      counts.put("embedded", count("SELECT count(*) FROM ef_chunk WHERE embedding IS NOT NULL"));
      counts.put("labels", count("SELECT count(*) FROM ef_label"));
      counts.put("rules", count("SELECT count(*) FROM ef_context_rule"));
      counts.put("documents", count("SELECT count(*) FROM ef_document"));
      counts.put("scoreSets", count("SELECT count(*) FROM ef_score_set"));
      db.put("ok", true);
      db.put("error", null);
    } catch (RuntimeException e) {
      db.put("ok", false);
      db.put("error", e.getMessage());
    }
    out.put("db", db);
    out.put("claude", Map.of("configured", claude.configured(), "model", settings.get().model()));
    out.put("embedding", Map.of("provider", embedder.provider(), "model", embedder.model(), "dimension",
        embedder.dimension()));
    out.put("counts", counts);
    return out;
  }

  private long count(String sql) {
    Long v = jdbc.queryForObject(sql, Long.class);
    return v == null ? 0 : v;
  }
}
