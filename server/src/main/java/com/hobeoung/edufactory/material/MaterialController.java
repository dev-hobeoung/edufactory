package com.hobeoung.edufactory.material;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.springframework.web.multipart.MultipartFile;

@RestController
public class MaterialController {

  private final MaterialService service;
  private final MaterialRepository repo;
  private final SearchService search;

  public MaterialController(MaterialService service, MaterialRepository repo, SearchService search) {
    this.service = service;
    this.repo = repo;
    this.search = search;
  }

  @GetMapping("/materials")
  public List<MaterialSummary> list(@RequestParam(required = false) String q,
      @RequestParam(required = false) String labelIds, @RequestParam(required = false) String kind,
      @RequestParam(required = false) Boolean exam, @RequestParam(required = false) String status) {
    return repo.list(new MaterialRepository.Filter(q, uuids(labelIds), kind, exam, status));
  }

  @PostMapping(value = "/materials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public MaterialSummary upload(@RequestParam(required = false) MultipartFile file,
      @RequestParam(required = false) String text, @RequestParam(required = false) String title,
      @RequestParam(required = false) String kind, @RequestParam(required = false) String description,
      @RequestParam(required = false) String labelIds, @RequestParam(defaultValue = "false") boolean isExam,
      @RequestParam(defaultValue = "true") boolean autoLabel) throws IOException {
    return service.upload(new MaterialService.Upload(file, text, title, kind, description, uuids(labelIds), isExam,
        autoLabel));
  }

  @GetMapping("/materials/{id}")
  public Map<String, Object> detail(@PathVariable UUID id) {
    MaterialSummary m = repo.get(id);
    Map<String, Object> out = new LinkedHashMap<>(com.hobeoung.edufactory.common.Json.convert(m, Map.class));
    out.put("chunks", repo.chunks(id));
    return out;
  }

  @PatchMapping("/materials/{id}")
  public MaterialSummary update(@PathVariable UUID id, @RequestBody MaterialService.MaterialPatch patch) {
    return service.update(id, patch);
  }

  @DeleteMapping("/materials/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }

  public record ProcessRequest(boolean restructure) {}

  @PostMapping("/materials/{id}/process")
  public MaterialSummary process(@PathVariable UUID id, @RequestBody(required = false) ProcessRequest req) {
    return service.process(id, req != null && req.restructure());
  }

  @GetMapping("/materials/{id}/file")
  public ResponseEntity<byte[]> file(@PathVariable UUID id) throws IOException {
    MaterialRepository.Source src = repo.source(id);
    byte[] bytes = service.file(id);
    String name = src.fileName() == null ? "material" : src.fileName();
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''"
            + URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20"))
        .contentType(src.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM
            : MediaType.parseMediaType(src.contentType()))
        .body(bytes);
  }

  @PostMapping("/materials/{id}/chunks")
  public Chunk addChunk(@PathVariable UUID id, @RequestBody ChunkInput in) {
    return service.addChunk(id, in);
  }

  @PatchMapping("/chunks/{id}")
  public Chunk updateChunk(@PathVariable UUID id, @RequestBody ChunkInput in) {
    return service.updateChunk(id, in);
  }

  @DeleteMapping("/chunks/{id}")
  public ResponseEntity<Void> deleteChunk(@PathVariable UUID id) {
    service.deleteChunk(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/search")
  public List<SearchService.Hit> search(@RequestBody SearchService.Query q) {
    return search.search(q);
  }

  static List<UUID> uuids(String csv) {
    if (csv == null || csv.isBlank()) {
      return new ArrayList<>();
    }
    return Arrays.stream(csv.split(",")).map(String::strip).filter(s -> !s.isEmpty()).map(UUID::fromString).toList();
  }
}
