package com.hobeoung.edufactory.document;

import com.hobeoung.edufactory.common.ApiException;
import com.hobeoung.edufactory.common.Json;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DocumentController {

  private final DocumentRepository repo;
  private final DocumentService service;

  public DocumentController(DocumentRepository repo, DocumentService service) {
    this.repo = repo;
    this.service = service;
  }

  @GetMapping("/documents")
  public List<DocumentSummary> list(@RequestParam(required = false) String q,
      @RequestParam(required = false) String kind, @RequestParam(required = false) String status) {
    return repo.list(q, kind, status);
  }

  @GetMapping("/documents/{id}")
  public Map<String, Object> detail(@PathVariable UUID id) {
    Map<String, Object> out = new LinkedHashMap<>(Json.convert(repo.get(id), Map.class));
    out.put("versions", repo.versions(id));
    return out;
  }

  /** 직접 작성 — 대화 없이 새 산출물(v1)을 만든다 */
  @PostMapping("/documents")
  public DocumentVersion create(@RequestBody DocPayload payload) {
    try {
      return service.create(payload);
    } catch (DocumentService.InvalidPayload e) {
      throw ApiException.badRequest("검증 오류\n" + e.getMessage());
    }
  }

  public record Patch(String title, String status) {}

  @PatchMapping("/documents/{id}")
  public DocumentSummary update(@PathVariable UUID id, @RequestBody Patch patch) {
    return service.update(id, patch.title(), patch.status());
  }

  @DeleteMapping("/documents/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    repo.get(id);
    repo.delete(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/documents/{id}/versions")
  public DocumentVersion save(@PathVariable UUID id, @RequestBody DocPayload payload) {
    repo.get(id);
    try {
      return service.submit(id, null, payload, null, null);
    } catch (DocumentService.InvalidPayload e) {
      throw ApiException.badRequest("검증 오류\n" + e.getMessage());
    }
  }

  @PostMapping("/documents/{id}/restore/{version}")
  public DocumentVersion restore(@PathVariable UUID id, @PathVariable int version) {
    return service.restore(id, version);
  }

  @GetMapping("/documents/{id}/export")
  public ResponseEntity<String> export(@PathVariable UUID id, @RequestParam(required = false) Integer version,
      @RequestParam(defaultValue = "true") boolean answers) {
    DocumentSummary d = repo.get(id);
    DocumentVersion v = repo.version(id, version == null ? d.currentVersion() : version)
        .orElseThrow(() -> ApiException.notFound("버전"));
    String name = d.title() + " v" + v.version() + (answers ? " (정답)" : "") + ".md";
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''"
            + URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20"))
        .contentType(new MediaType("text", "markdown", StandardCharsets.UTF_8))
        .body(MarkdownExporter.export(v.payload(), answers));
  }
}
