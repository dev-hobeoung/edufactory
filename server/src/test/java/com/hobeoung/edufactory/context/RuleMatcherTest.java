package com.hobeoung.edufactory.context;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RuleMatcherTest {

  private final UUID math = UUID.randomUUID();
  private final UUID grade2 = UUID.randomUUID();
  private final Conditions c = new Conditions("EXAM", List.of(math, grade2), List.of(), List.of("MULTIPLE_CHOICE"),
      10, 100.0, 0.6, "고2 중위권", 1, null);

  private static ContextRule.Condition cond(String match, ContextRule.Clause... clauses) {
    return new ContextRule.Condition(match, List.of(clauses));
  }

  private static ContextRule.Clause clause(String field, String op, String... values) {
    return new ContextRule.Clause(field, op, List.of(values));
  }

  @Test
  void emptyConditionAlwaysApplies() {
    assertTrue(RuleMatcher.matches(cond("ALL"), c, "아무 요청"));
  }

  @Test
  void labelSetOperators() {
    assertTrue(RuleMatcher.matches(cond("ALL", clause("labels", "any", math.toString(), UUID.randomUUID().toString())),
        c, ""));
    assertTrue(RuleMatcher.matches(cond("ALL", clause("labels", "all", math.toString(), grade2.toString())), c, ""));
    assertFalse(RuleMatcher.matches(cond("ALL", clause("labels", "all", math.toString(),
        UUID.randomUUID().toString())), c, ""));
    assertFalse(RuleMatcher.matches(cond("ALL", clause("labels", "none", math.toString())), c, ""));
  }

  @Test
  void allVersusAny() {
    ContextRule.Clause kindOk = clause("outputKind", "any", "EXAM", "QUIZ");
    ContextRule.Clause kindNo = clause("outputKind", "any", "WORKSHEET");
    assertFalse(RuleMatcher.matches(cond("ALL", kindOk, kindNo), c, ""));
    assertTrue(RuleMatcher.matches(cond("ANY", kindOk, kindNo), c, ""));
  }

  @Test
  void textFieldsAreCaseInsensitiveContains() {
    assertTrue(RuleMatcher.matches(cond("ALL", clause("request", "any", "수능", "Mock")), c, "6월 MOCK 형식으로"));
    assertTrue(RuleMatcher.matches(cond("ALL", clause("audience", "any", "고2")), c, ""));
    assertFalse(RuleMatcher.matches(cond("ALL", clause("audience", "none", "중위권")), c, ""));
    assertTrue(RuleMatcher.matches(cond("ALL", clause("questionTypes", "any", "MULTIPLE_CHOICE")), c, ""));
  }

  @Test
  void unsetOutputKindDoesNotMatchAny() {
    Conditions none = Conditions.empty();
    assertFalse(RuleMatcher.matches(cond("ALL", clause("outputKind", "any", "EXAM")), none, ""));
    assertTrue(RuleMatcher.matches(cond("ALL", clause("outputKind", "none", "EXAM")), none, ""));
  }
}
