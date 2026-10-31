package com.hobeoung.edufactory.material;

import com.hobeoung.edufactory.auth.CurrentUser;
import com.hobeoung.edufactory.common.ApiException;
import com.hobeoung.edufactory.common.Sql;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MaterialService {

  private final MaterialRepository repo;
  private final FileStorage storage;
  private final MaterialProcessor processor;
  private final org.springframework.context.ApplicationEventPublisher events;

  public MaterialService(MaterialRepository repo, FileStorage storage, MaterialProcessor processor,
      org.springframework.context.ApplicationEventPublisher events) {
    this.repo = repo;
    this.storage = storage;
    this.processor = processor;
    this.events = events;
  }

  public record Upload(MultipartFile file, String text, String title, String kind, String description,
      List<UUID> labelIds, boolean isExam, boolean autoLabel) {}

  public MaterialSummary upload(Upload u) throws IOException {
    boolean hasFile = u.file() != null && !u.file().isEmpty();
    if (!hasFile && (u.text() == null || u.text().isBlank())) {
      throw ApiException.badRequest("파일을 올리거나 텍스트를 붙여 넣으세요");
    }
    String sourceType = "PASTE";
    String fileName = null;
    String contentType = null;
    Long size = null;
    String key = null;
    if (hasFile) {
      fileName = u.file().getOriginalFilename();
      contentType = u.file().getContentType();
      size = u.file().getSize();
      sourceType = sourceType(fileName, contentType);
      key = storage.save(u.file().getBytes());
    }
    String title = Sql.str(u.title());
    if (title == null) {
      title = fileName != null ? fileName.replaceFirst("\\.[^.]+$", "") : "붙여 넣은 자료";
    }
    UUID id = UUID.randomUUID();
    repo.insert(new MaterialRepository.NewMaterial(id, title, MaterialSummary.kind(u.kind()), Sql.str(u.description()),
        sourceType, fileName, contentType, size, key, hasFile ? null : u.text(), u.isExam(), u.autoLabel(),
        CurrentUser.idOrNull()));
    if (u.labelIds() != null) {
      repo.setMaterialLabels(id, u.labelIds());
    }
    processor.submit(id, true);
    return repo.get(id);
  }

  static String sourceType(String fileName, String contentType) {
    String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
    if (name.endsWith(".pdf") || "application/pdf".equals(contentType)) {
      return "PDF";
    }
    if (name.endsWith(".docx")) {
      return "DOCX";
    }
    if (name.endsWith(".md") || name.endsWith(".markdown")) {
      return "MARKDOWN";
    }
    if (name.endsWith(".txt") || name.endsWith(".csv") || (contentType != null && contentType.startsWith("text/"))) {
      return "TEXT";
    }
    throw ApiException.badRequest("지원하지 않는 파일 형식입니다 (PDF·DOCX·TXT·MD)");
  }

  public record MaterialPatch(String title, String kind, String description, List<UUID> labelIds, Boolean isExam) {}

  @Transactional
  public MaterialSummary update(UUID id, MaterialPatch patch) {
    MaterialSummary before = repo.get(id);
    repo.update(id, Sql.str(patch.title()), patch.kind() == null ? null : MaterialSummary.kind(patch.kind()),
        patch.description(), patch.isExam());
    if (patch.labelIds() != null) {
      List<UUID> old = before.labels().stream().map(l -> l.id()).sorted().toList();
      if (!old.equals(patch.labelIds().stream().sorted().toList())) {
        repo.setMaterialLabels(id, patch.labelIds());
        processor.reembed(repo.chunks(id).stream().map(Chunk::id).toList());
      }
    }
    return repo.get(id);
  }

  public void delete(UUID id) {
    MaterialRepository.Source src = repo.source(id);
    repo.delete(id);
    if (src.fileKey() != null) {
      storage.delete(src.fileKey());
    }
  }

  public MaterialSummary process(UUID id, boolean restructure) {
    MaterialSummary m = repo.get(id);
    if (processor.busy(id)) {
      throw ApiException.conflict("이미 처리 중입니다");
    }
    if (restructure && m.scoreSetCount() > 0) {
      throw ApiException.conflict("성적이 입력된 시험 자료는 다시 구조화할 수 없습니다 (문항이 바뀌면 성적이 끊깁니다)");
    }
    processor.submit(id, restructure);
    return repo.get(id);
  }

  public byte[] file(UUID id) throws IOException {
    MaterialRepository.Source src = repo.source(id);
    if (src.fileKey() == null) {
      throw ApiException.notFound("원본 파일");
    }
    return storage.read(src.fileKey());
  }

  @Transactional
  public Chunk addChunk(UUID materialId, ChunkInput in) {
    repo.get(materialId);
    if (in.content() == null || in.content().isBlank()) {
      throw ApiException.badRequest("본문을 입력하세요");
    }
    UUID id = repo.insertChunk(materialId, repo.nextSeq(materialId), in, "MANUAL");
    repo.touch(materialId);
    processor.reembed(List.of(id));
    return repo.chunk(id).orElseThrow();
  }

  @Transactional
  public Chunk updateChunk(UUID chunkId, ChunkInput in) {
    Chunk before = repo.chunk(chunkId).orElseThrow(() -> ApiException.notFound("청크"));
    repo.updateChunk(chunkId, in);
    repo.touch(before.materialId());
    Chunk after = repo.chunk(chunkId).orElseThrow();
    if (!java.util.Objects.equals(before.answer(), after.answer())
        || !java.util.Objects.equals(before.score(), after.score())
        || !java.util.Objects.equals(before.chunkType(), after.chunkType())) {
      events.publishEvent(new MaterialEvents.ItemChanged(before.materialId()));
    }
    if (!before.content().equals(after.content()) || !before.choices().equals(after.choices())
        || !before.tags().equals(after.tags()) || in.labelIds() != null || in.title() != null
        || in.position() != null || in.explanation() != null) {
      repo.clearEmbedding(chunkId);
      processor.reembed(List.of(chunkId));
      after = repo.chunk(chunkId).orElseThrow();
    }
    return after;
  }

  public void deleteChunk(UUID chunkId) {
    Chunk c = repo.chunk(chunkId).orElseThrow(() -> ApiException.notFound("청크"));
    if (repo.chunkHasScores(chunkId)) {
      throw ApiException.conflict("성적이 입력된 문항은 삭제할 수 없습니다");
    }
    repo.deleteChunk(chunkId);
    repo.touch(c.materialId());
    events.publishEvent(new MaterialEvents.ItemChanged(c.materialId()));
  }
}
