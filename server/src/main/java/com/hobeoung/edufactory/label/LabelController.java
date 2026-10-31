package com.hobeoung.edufactory.label;

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
public class LabelController {

  private final LabelService labels;

  public LabelController(LabelService labels) {
    this.labels = labels;
  }

  @GetMapping("/labels")
  public List<Label> list() {
    return labels.list();
  }

  @PostMapping("/labels")
  public Label create(@RequestBody LabelService.LabelInput in) {
    return labels.create(in);
  }

  @PatchMapping("/labels/{id}")
  public Label update(@PathVariable UUID id, @RequestBody LabelService.LabelInput in) {
    return labels.update(id, in);
  }

  @DeleteMapping("/labels/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    labels.delete(id);
    return ResponseEntity.noContent().build();
  }
}
