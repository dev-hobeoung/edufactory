package com.hobeoung.edufactory.exam;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExamController {

  private final ScoreService scores;

  public ExamController(ScoreService scores) {
    this.scores = scores;
  }

  @GetMapping("/exams")
  public List<Map<String, Object>> exams() {
    return scores.exams();
  }

  @GetMapping("/exams/{materialId}")
  public Map<String, Object> exam(@PathVariable UUID materialId) {
    return scores.exam(materialId);
  }

  @PostMapping("/exams/{materialId}/score-sets")
  public ScoreService.ScoreSet createSet(@PathVariable UUID materialId, @RequestBody ScoreService.SetInput in) {
    return scores.createSet(materialId, in);
  }

  @PatchMapping("/score-sets/{id}")
  public ScoreService.ScoreSet updateSet(@PathVariable UUID id, @RequestBody ScoreService.SetInput in) {
    return scores.updateSet(id, in);
  }

  @DeleteMapping("/score-sets/{id}")
  public ResponseEntity<Void> deleteSet(@PathVariable UUID id) {
    scores.deleteSet(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/score-sets/{id}/entries")
  public Map<String, Object> entries(@PathVariable UUID id) {
    return scores.entries(id);
  }

  @PutMapping("/score-sets/{id}/entries")
  public Map<String, Object> putEntries(@PathVariable UUID id, @RequestBody ScoreService.EntriesInput in) {
    return scores.putEntries(id, in);
  }

  @PostMapping("/score-sets/{id}/import")
  public Map<String, Object> importText(@PathVariable UUID id, @RequestBody ScoreService.ImportInput in) {
    return scores.importText(id, in);
  }

  @GetMapping("/score-sets/{id}/stats")
  public Map<String, Object> stats(@PathVariable UUID id) {
    return scores.setStats(id);
  }
}
