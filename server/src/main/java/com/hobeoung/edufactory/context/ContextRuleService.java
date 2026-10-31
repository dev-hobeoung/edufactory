package com.hobeoung.edufactory.context;

import com.hobeoung.edufactory.common.ApiException;
import com.hobeoung.edufactory.common.Json;
import com.hobeoung.edufactory.common.Sql;
import com.hobeoung.edufactory.label.LabelService;
import com.hobeoung.edufactory.material.Chunk;
import com.hobeoung.edufactory.material.Choice;
import com.hobeoung.edufactory.material.MaterialRepository;
import com.hobeoung.edufactory.material.MaterialSummary;
import com.hobeoung.edufactory.material.SearchService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 컨텍스트 규칙: 조건이 맞으면 지침·고정 자료·라벨 범위 검색 결과를 &lt;context_rules&gt; 블록으로 만든다.
 * 블록은 사용자 메시지에 붙는다 (시스템 프롬프트를 바꾸지 않아 캐시가 유지된다).
 */
@Service
public class ContextRuleService {

  public record Result(List<ContextRule.Applied> applied, String block) {}

  private final NamedParameterJdbcTemplate jdbc;
  private final MaterialRepository materials;
  private final SearchService search;

  public ContextRuleService(NamedParameterJdbcTemplate jdbc, MaterialRepository materials, SearchService search) {
    this.jdbc = jdbc;
    this.materials = materials;
    this.search = search;
  }

  public List<ContextRule> list() {
    return jdbc.query("""
        SELECT id, name, description, enabled, priority, condition::text AS condition, injection::text AS injection,
               created_at, updated_at FROM ef_context_rule ORDER BY priority DESC, created_at
        """, Map.of(), (rs, i) -> new ContextRule(Sql.uuid(rs, "id"), rs.getString("name"),
        rs.getString("description"), rs.getBoolean("enabled"), rs.getInt("priority"),
        Json.read(rs.getString("condition"), ContextRule.Condition.class),
        Json.read(rs.getString("injection"), ContextRule.Injection.class), Sql.instant(rs, "created_at"),
        Sql.instant(rs, "updated_at")));
  }

  public ContextRule get(UUID id) {
    return list().stream().filter(r -> r.id().equals(id)).findFirst()
        .orElseThrow(() -> ApiException.notFound("규칙"));
  }

  public record RuleInput(String name, String description, Boolean enabled, Integer priority,
      ContextRule.Condition condition, ContextRule.Injection injection) {}

  public ContextRule create(RuleInput in) {
    String name = Sql.str(in.name());
    if (name == null) {
      throw ApiException.badRequest("규칙 이름을 입력하세요");
    }
    UUID id = UUID.randomUUID();
    jdbc.update("""
        INSERT INTO ef_context_rule (id, name, description, enabled, priority, condition, injection)
        VALUES (:id, :n, :d, :e, :p, CAST(:c AS jsonb), CAST(:i AS jsonb))
        """, new MapSqlParameterSource().addValue("id", id).addValue("n", name).addValue("d", Sql.str(in.description()))
        .addValue("e", in.enabled() == null || in.enabled()).addValue("p", in.priority() == null ? 0 : in.priority())
        .addValue("c", Json.write(in.condition() == null ? new ContextRule.Condition("ALL", List.of()) : in.condition()))
        .addValue("i", Json.write(validInjection(in.injection()))));
    return get(id);
  }

  public ContextRule update(UUID id, RuleInput in) {
    ContextRule r = get(id);
    String name = in.name() == null ? r.name() : Sql.str(in.name());
    if (name == null) {
      throw ApiException.badRequest("규칙 이름을 입력하세요");
    }
    jdbc.update("""
        UPDATE ef_context_rule SET name = :n, description = :d, enabled = :e, priority = :p,
               condition = CAST(:c AS jsonb), injection = CAST(:i AS jsonb), updated_at = now() WHERE id = :id
        """, new MapSqlParameterSource().addValue("id", id).addValue("n", name)
        .addValue("d", in.description() == null ? r.description() : Sql.str(in.description()))
        .addValue("e", in.enabled() == null ? r.enabled() : in.enabled())
        .addValue("p", in.priority() == null ? r.priority() : in.priority())
        .addValue("c", Json.write(in.condition() == null ? r.condition() : in.condition()))
        .addValue("i", Json.write(in.injection() == null ? r.injection() : validInjection(in.injection()))));
    return get(id);
  }

  private static ContextRule.Injection validInjection(ContextRule.Injection in) {
    if (in == null) {
      throw ApiException.badRequest("주입할 내용(지침·자료·검색)을 하나 이상 정하세요");
    }
    if (in.instruction() == null && in.materialIds().isEmpty() && in.retrieval() == null) {
      throw ApiException.badRequest("주입할 내용(지침·자료·검색)을 하나 이상 정하세요");
    }
    return in;
  }

  public void delete(UUID id) {
    get(id);
    jdbc.update("DELETE FROM ef_context_rule WHERE id = :id", Map.of("id", id));
  }

  /** 라벨이 지워지면 조건·검색 범위에서 뺀다 */
  @EventListener
  public void onLabelDeleted(LabelService.LabelDeleted e) {
    String id = e.labelId().toString();
    for (ContextRule r : list()) {
      ContextRule.Condition c = new ContextRule.Condition(r.condition().match(), r.condition().clauses().stream()
          .map(cl -> new ContextRule.Clause(cl.field(), cl.op(), cl.values().stream().filter(v -> !v.equals(id))
              .toList()))
          .filter(cl -> !cl.values().isEmpty())
          .toList());
      ContextRule.Retrieval rt = r.injection().retrieval();
      ContextRule.Injection inj = rt == null ? r.injection() : new ContextRule.Injection(r.injection().instruction(),
          r.injection().materialIds(), new ContextRule.Retrieval(rt.query(), rt.labelIds().stream()
          .filter(l -> !l.equals(e.labelId())).toList(), rt.chunkTypes(), rt.topK()), r.injection().maxChars());
      if (!c.equals(r.condition()) || !inj.equals(r.injection())) {
        jdbc.update("UPDATE ef_context_rule SET condition = CAST(:c AS jsonb), injection = CAST(:i AS jsonb)"
            + " WHERE id = :id", Map.of("id", r.id(), "c", Json.write(c), "i", Json.write(inj)));
      }
    }
  }

  /** 지금 요청에 맞는 규칙들의 블록 */
  public Result apply(Conditions conditions, String requestText) {
    List<ContextRule.Applied> applied = new ArrayList<>();
    StringBuilder block = new StringBuilder();
    for (ContextRule rule : list()) {
      if (!rule.enabled() || !RuleMatcher.matches(rule.condition(), conditions, requestText)) {
        continue;
      }
      Rendered r = render(rule, requestText);
      block.append(r.text());
      applied.add(new ContextRule.Applied(rule.id(), rule.name(), r.materials(), r.chunks(), r.text().length()));
    }
    if (applied.isEmpty()) {
      return new Result(List.of(), null);
    }
    return new Result(applied, "<context_rules>\n출제자가 미리 정해 둔 조건별 컨텍스트입니다. 지침은 따르고, 자료는 참고하세요.\n"
        + block + "</context_rules>");
  }

  record Rendered(String text, int materials, int chunks) {}

  private Rendered render(ContextRule rule, String requestText) {
    ContextRule.Injection inj = rule.injection();
    Budget budget = new Budget(inj.maxChars());
    StringBuilder sb = new StringBuilder("<rule id=\"rule:").append(rule.id()).append("\" name=\"")
        .append(attr(rule.name())).append("\">\n");
    if (inj.instruction() != null) {
      sb.append("<instruction>\n").append(budget.take(inj.instruction())).append("\n</instruction>\n");
    }
    int materialCount = 0;
    int chunkCount = 0;
    Set<UUID> seen = new LinkedHashSet<>();
    for (UUID materialId : inj.materialIds()) {
      if (budget.left() <= 0) {
        break;
      }
      MaterialSummary m = materials.find(materialId).orElse(null);
      if (m == null) {
        continue;
      }
      materialCount++;
      sb.append("<material id=\"").append(m.id()).append("\" title=\"").append(attr(m.title())).append("\">\n");
      for (Chunk c : materials.chunks(materialId)) {
        if (budget.left() <= 0) {
          sb.append("(이하 생략 — 글자 한도)\n");
          break;
        }
        seen.add(c.id());
        sb.append(budget.take(chunkText(c))).append('\n');
      }
      sb.append("</material>\n");
    }
    ContextRule.Retrieval rt = inj.retrieval();
    if (rt != null && budget.left() > 0) {
      String query = rt.query() != null ? rt.query() : requestText;
      List<SearchService.Hit> hits = search.search(new SearchService.Query(query, rt.labelIds(), null,
          rt.chunkTypes(), null, rt.topK(), null));
      sb.append("<retrieved query=\"").append(attr(query)).append("\">\n");
      for (SearchService.Hit h : hits) {
        if (budget.left() <= 0 || !seen.add(h.chunk().id())) {
          continue;
        }
        chunkCount++;
        sb.append("<chunk ref=\"chunk:").append(h.chunk().id()).append("\" material=\"").append(attr(h.materialTitle()))
            .append("\" similarity=\"").append(h.similarity()).append("\">\n")
            .append(budget.take(chunkText(h.chunk()))).append("\n</chunk>\n");
      }
      sb.append("</retrieved>\n");
    }
    sb.append("</rule>\n");
    return new Rendered(sb.toString(), materialCount, chunkCount);
  }

  static String chunkText(Chunk c) {
    StringBuilder sb = new StringBuilder();
    sb.append('[').append(c.chunkType());
    if (c.position() != null) {
      sb.append(" · ").append(c.position());
    }
    if (c.title() != null) {
      sb.append(" · ").append(c.title());
    }
    sb.append("]\n").append(c.content());
    for (Choice ch : c.choices()) {
      sb.append('\n').append(ch.label()).append(' ').append(ch.text());
    }
    if (c.answer() != null) {
      sb.append("\n정답: ").append(c.answer());
    }
    if (c.explanation() != null) {
      sb.append("\n해설: ").append(c.explanation());
    }
    return sb.toString();
  }

  private static String attr(String s) {
    return s == null ? "" : s.replace("\"", "'").replace("\n", " ");
  }

  /** 규칙 하나의 글자 한도 */
  static final class Budget {
    private int left;

    Budget(int max) {
      this.left = max;
    }

    int left() {
      return left;
    }

    String take(String s) {
      if (left <= 0) {
        return "";
      }
      if (s.length() <= left) {
        left -= s.length();
        return s;
      }
      String cut = s.substring(0, left) + "…(생략)";
      left = 0;
      return cut;
    }
  }
}
