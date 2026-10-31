package com.hobeoung.edufactory.context;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** 규칙 조건 판정 (순수 함수) */
public final class RuleMatcher {

  private RuleMatcher() {}

  public static boolean matches(ContextRule.Condition condition, Conditions c, String requestText) {
    if (condition.clauses().isEmpty()) {
      return true;
    }
    boolean any = "ANY".equals(condition.match());
    for (ContextRule.Clause clause : condition.clauses()) {
      boolean ok = clause(clause, c, requestText);
      if (any && ok) {
        return true;
      }
      if (!any && !ok) {
        return false;
      }
    }
    return !any;
  }

  static boolean clause(ContextRule.Clause clause, Conditions c, String requestText) {
    String op = clause.op() == null ? "any" : clause.op();
    List<String> values = clause.values();
    if (values.isEmpty()) {
      return true;
    }
    return switch (clause.field() == null ? "" : clause.field()) {
      case "labels" -> set(op, values, ids(c.labelIds()));
      case "materials" -> set(op, values, ids(c.materialIds()));
      case "questionTypes" -> set(op, values, c.questionTypes());
      case "outputKind" -> set(op, values, c.outputKind() == null ? List.of() : List.of(c.outputKind()));
      case "audience" -> text(op, values, c.audience());
      case "request" -> text(op, values, requestText);
      default -> false;
    };
  }

  private static List<String> ids(List<UUID> ids) {
    return ids.stream().map(UUID::toString).toList();
  }

  static boolean set(String op, List<String> values, Collection<String> actual) {
    return switch (op) {
      case "all" -> actual.containsAll(values);
      case "none" -> values.stream().noneMatch(actual::contains);
      default -> values.stream().anyMatch(actual::contains);
    };
  }

  static boolean text(String op, List<String> values, String actual) {
    String a = actual == null ? "" : actual.toLowerCase(Locale.ROOT);
    return switch (op) {
      case "all" -> values.stream().allMatch(v -> a.contains(v.toLowerCase(Locale.ROOT)));
      case "none" -> values.stream().noneMatch(v -> a.contains(v.toLowerCase(Locale.ROOT)));
      default -> values.stream().anyMatch(v -> a.contains(v.toLowerCase(Locale.ROOT)));
    };
  }
}
