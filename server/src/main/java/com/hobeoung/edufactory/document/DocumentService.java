package com.hobeoung.edufactory.document;

import com.hobeoung.edufactory.auth.CurrentUser;
import com.hobeoung.edufactory.common.ApiException;
import com.hobeoung.edufactory.common.Sql;
import com.hobeoung.edufactory.context.Conditions;
import com.hobeoung.edufactory.system.SettingsService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentService {

  public static final Set<String> STATUSES = Set.of("DRAFT", "FINAL", "ARCHIVED");

  private final DocumentRepository repo;
  private final ItemAnalyzer analyzer;
  private final SettingsService settings;

  public DocumentService(DocumentRepository repo, ItemAnalyzer analyzer, SettingsService settings) {
    this.repo = repo;
    this.analyzer = analyzer;
    this.settings = settings;
  }

  /** 검증 실패 — 도구는 모델에 오류 목록을 돌려주고, 화면 편집은 400 으로 */
  public static class InvalidPayload extends RuntimeException {
    private final List<String> errors;

    public InvalidPayload(List<String> errors) {
      super(String.join("\n", errors));
      this.errors = errors;
    }

    public List<String> errors() {
      return errors;
    }
  }

  /**
   * 새 버전 제출 (AI 제출·화면 편집 공통): 검증 → 유사도·예상 정답률 → 저장.
   *
   * @param documentId null 이면 conversationId 의 산출물을 찾거나 새로 만든다
   * @param conditions 요청 조건 (문항 수·총점이 다르면 경고)
   */
  @Transactional
  public DocumentVersion submit(UUID documentId, UUID conversationId, DocPayload payload, Conditions conditions,
      UUID messageId) {
    List<String> errors = payload.validate();
    if (!errors.isEmpty()) {
      throw new InvalidPayload(errors);
    }
    UUID docId = documentId;
    if (docId == null) {
      docId = conversationId == null ? null : repo.findIdByConversation(conversationId).orElse(null);
      if (docId == null) {
        docId = repo.create(conversationId, payload.title(), payload.kind(), CurrentUser.idOrNull());
      }
    }
    List<DocPayload.Item> items = payload.items();
    ItemAnalyzer.Analysis analysis = analyzer.analyze(items);
    List<String> warnings = warnings(payload, conditions, analysis);
    int version = repo.addVersion(docId, payload, warnings, analysis.similar(), analysis.prediction(),
        analysis.maxSimilarity(), messageId);
    return repo.version(docId, version).orElseThrow();
  }

  @Transactional
  public DocumentVersion create(DocPayload payload) {
    List<String> errors = payload.validate();
    if (!errors.isEmpty()) {
      throw new InvalidPayload(errors);
    }
    UUID id = repo.create(null, payload.title(), payload.kind(), CurrentUser.idOrNull());
    return submit(id, null, payload, null, null);
  }

  List<String> warnings(DocPayload payload, Conditions c, ItemAnalyzer.Analysis analysis) {
    List<String> warnings = new ArrayList<>();
    double warnAt = settings.get().similarityWarn();
    for (DocumentVersion.SimilarHit h : analysis.similar()) {
      if (h.similarity() >= warnAt) {
        warnings.add(h.itemNumber() + "번이 자료 '" + h.materialTitle() + (h.position() == null ? "" : " · " + h.position())
            + "'와 매우 비슷합니다 (유사도 " + String.format("%.2f", h.similarity()) + ") — 원문을 그대로 옮기지 않았는지 확인하세요");
      }
    }
    for (DocumentVersion.ItemPrediction p : analysis.prediction()) {
      DocPayload.Item item = payload.items().stream().filter(i -> i.number().equals(p.itemNumber())).findFirst()
          .orElse(null);
      if (item != null && item.difficulty() != null && p.expectedRate() != null && !"LOW".equals(p.confidence())
          && Math.abs(item.difficulty() - p.expectedRate()) > 0.15) {
        warnings.add(p.itemNumber() + "번 예상 정답률(" + pct(item.difficulty()) + ")이 성적 기반 예측(" + pct(p.expectedRate())
            + ", 신뢰도 " + p.confidence() + ")과 15%p 넘게 다릅니다");
      }
    }
    if (c != null) {
      int count = payload.items().size();
      if (c.itemCount() != null && count > 0 && count != c.itemCount()) {
        warnings.add("요청 문항 수(" + c.itemCount() + ")와 실제 문항 수(" + count + ")가 다릅니다");
      }
      Double total = payload.totalScore();
      if (c.totalScore() != null && total != null && Math.abs(total - c.totalScore()) > 0.01) {
        warnings.add("요청 총점(" + c.totalScore() + ")과 배점 합(" + total + ")이 다릅니다");
      }
    }
    return warnings;
  }

  private static String pct(double v) {
    return Math.round(v * 100) + "%";
  }

  @Transactional
  public DocumentVersion restore(UUID documentId, int version) {
    DocumentVersion v = repo.version(documentId, version).orElseThrow(() -> ApiException.notFound("버전"));
    DocPayload p = v.payload();
    DocPayload copy = new DocPayload(p.title(), p.kind(), p.audience(), p.summary(), p.instructions(), p.sections(),
        p.evidence(), "v" + version + " 으로 되돌림");
    int next = repo.addVersion(documentId, copy, v.warnings(), v.similar(), v.prediction(),
        v.similar().stream().mapToDouble(DocumentVersion.SimilarHit::similarity).boxed().max(Double::compare)
            .orElse(null), null);
    return repo.version(documentId, next).orElseThrow();
  }

  public DocumentSummary update(UUID id, String title, String status) {
    repo.get(id);
    if (status != null && !STATUSES.contains(status)) {
      throw ApiException.badRequest("상태는 DRAFT·FINAL·ARCHIVED 중 하나입니다");
    }
    repo.update(id, Sql.str(title), status);
    return repo.get(id);
  }
}
