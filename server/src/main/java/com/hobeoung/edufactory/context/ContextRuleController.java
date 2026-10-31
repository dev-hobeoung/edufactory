package com.hobeoung.edufactory.context;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ContextRuleController {

  private final ContextRuleService rules;

  public ContextRuleController(ContextRuleService rules) {
    this.rules = rules;
  }

  @GetMapping("/context-rules")
  public List<ContextRule> list() {
    return rules.list();
  }

  @PostMapping("/context-rules")
  public ContextRule create(@RequestBody ContextRuleService.RuleInput in) {
    return rules.create(in);
  }

  @PatchMapping("/context-rules/{id}")
  public ContextRule update(@PathVariable UUID id, @RequestBody ContextRuleService.RuleInput in) {
    return rules.update(id, in);
  }

  @DeleteMapping("/context-rules/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    rules.delete(id);
    return ResponseEntity.noContent().build();
  }

  public record PreviewRequest(Conditions conditions, String text) {}

  @PostMapping("/context-rules/preview")
  public ContextRuleService.Result preview(@RequestBody PreviewRequest req) {
    return rules.apply(req.conditions() == null ? Conditions.empty() : req.conditions(),
        req.text() == null ? "" : req.text());
  }
}
