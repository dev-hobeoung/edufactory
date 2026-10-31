package com.hobeoung.edufactory.material.ingest;

import com.anthropic.models.messages.Base64PdfSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.DocumentBlockParam;
import com.anthropic.models.messages.TextBlockParam;
import com.fasterxml.jackson.databind.JsonNode;
import com.hobeoung.edufactory.ai.StructuredCall;
import com.hobeoung.edufactory.common.Json;
import com.hobeoung.edufactory.config.EduProperties;
import com.hobeoung.edufactory.label.LabelRef;
import com.hobeoung.edufactory.material.Choice;
import com.hobeoung.edufactory.material.ChunkInput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Claude 로 자료를 청크(문항·지문·개념·예제·해설·단락)로 구조화한다. 긴 자료는 나눠 보내고(텍스트 3만 자 / 스캔 PDF 10쪽),
 * 다른 묶음에 있는 정답표는 문항 번호로 이어 붙인다.
 */
@Component
public class AiStructurer {

  static final int TEXT_BATCH_CHARS = 30_000;
  static final int PDF_BATCH_PAGES = 10;

  /** 고정 문자열 — 묶음마다 같은 시스템 프롬프트로 캐시된다 */
  static final String SYSTEM = """
      당신은 교육 자료(시험지·학습지·교재·강의 노트·참고 자료)를 검색과 재사용에 알맞은 단위(청크)로 구조화하는 편집자입니다.
      결과는 문제·교육자료를 새로 만들 때 검색되는 자료 라이브러리에 들어갑니다.

      # 청크 나누기
      - QUESTION: 문항 하나 = 청크 하나. 발문은 content, 선택지는 choices(label 은 원문 표기 그대로 "①"·"A" 등),
        정답·해설이 자료에 있으면 answer·explanation 에 넣습니다. itemNo 는 문항 번호("3", "2-1"), position 은 "3번" 처럼.
        배점이 보이면 score(숫자). questionType 은 문항 형식에 맞게 고릅니다.
      - PASSAGE: 여러 문항이 함께 보는 지문·자료·표는 따로 PASSAGE 청크로 두고 position 에 "[1~3] 공통 지문" 처럼 범위를 적습니다.
      - CONCEPT(개념 설명·정의·정리), EXAMPLE(예제·풀이 예시), EXPLANATION(문항에 붙일 수 없는 해설), SECTION(그 밖의 단락),
        OTHER(목차·표지 등). 문항이 아닌 청크는 의미 단위로 200~2,000자 안팎이 되게 나누고 title 에 소제목을 둡니다.
      - 원문을 요약하거나 고쳐 쓰지 말고 그대로 옮깁니다. 줄바꿈·목록은 마크다운으로, 수식은 LaTeX($…$, $$…$$)로 바꿉니다.
        그림·도표는 [그림: 무엇을 나타내는지 구체적으로] 처럼 글로 설명합니다.
      - 머리글·바닥글·쪽 번호·반복되는 안내문은 버립니다.
      - 자료가 시험이 아니어도 본문 속 연습문제는 QUESTION 으로 둡니다.
      - 이 묶음이 문항 중간에서 시작하거나 끝나면 보이는 부분만 청크로 만듭니다.
      - 정답표·빠른 정답처럼 문항과 떨어져 있는 정답은 청크로 만들지 말고 answerKey 에 문항 번호별로 넣습니다.

      # 메타데이터
      - tags: 단원·개념·기능을 나타내는 짧은 키워드 2~6개.
      - labelIds: 요청에 라벨 목록이 있으면, 그 청크에 분명히 해당하는 라벨의 id 만 넣습니다 (목록에 없는 id 금지, 애매하면 넣지 않음).
      - pageFrom·pageTo: 원본 쪽 번호를 알 수 있으면 넣고 모르면 null.
      """;

  private final StructuredCall call;
  private final EduProperties props;

  public AiStructurer(StructuredCall call, EduProperties props) {
    this.call = call;
    this.props = props;
  }

  /** @param progress "구조화 2/5" 같은 진행 문구를 받는다 */
  public List<ChunkInput> structure(StructureRequest req, Consumer<String> progress) throws IOException {
    List<List<ContentBlockParam>> batches = batches(req);
    Set<String> validLabels = req.labelCatalog().stream().map(l -> l.id().toString()).collect(Collectors.toSet());
    List<ChunkInput> chunks = new ArrayList<>();
    Map<String, String[]> answerKey = new LinkedHashMap<>();
    for (int i = 0; i < batches.size(); i++) {
      progress.accept("AI 구조화 " + (i + 1) + "/" + batches.size());
      JsonNode result = call.run(props.ai().structureModel(), "low", SYSTEM, batches.get(i), schema(), "structure");
      for (JsonNode c : result.path("chunks")) {
        ChunkInput chunk = toChunk(c, validLabels);
        if (chunk != null) {
          chunks.add(chunk);
        }
      }
      for (JsonNode a : result.path("answerKey")) {
        String no = Json.text(a, "itemNo");
        if (no != null) {
          answerKey.put(no.strip(), new String[] {Json.text(a, "answer"), Json.text(a, "explanation")});
        }
      }
    }
    return applyAnswerKey(chunks, answerKey);
  }

  static List<ChunkInput> applyAnswerKey(List<ChunkInput> chunks, Map<String, String[]> key) {
    if (key.isEmpty()) {
      return chunks;
    }
    List<ChunkInput> out = new ArrayList<>(chunks.size());
    for (ChunkInput c : chunks) {
      String[] a = c.itemNo() == null ? null : key.get(c.itemNo().strip());
      if ("QUESTION".equals(c.chunkType()) && a != null) {
        out.add(new ChunkInput(c.chunkType(), c.title(), c.position(), c.itemNo(), c.questionType(), c.content(),
            c.choices(), c.answer() == null ? a[0] : c.answer(), c.explanation() == null ? a[1] : c.explanation(),
            c.score(), c.tags(), c.labelIds(), c.pageFrom(), c.pageTo()));
      } else {
        out.add(c);
      }
    }
    return out;
  }

  private List<List<ContentBlockParam>> batches(StructureRequest req) throws IOException {
    ExtractedText ex = req.extracted();
    String header = header(req);
    List<List<ContentBlockParam>> out = new ArrayList<>();
    if (ex.pdf() != null && ex.looksScanned()) {
      int pages = ex.pages().size();
      for (int from = 1; from <= pages; from += PDF_BATCH_PAGES) {
        int to = Math.min(pages, from + PDF_BATCH_PAGES - 1);
        byte[] part = TextExtractor.pdfPages(ex.pdf(), from, to);
        out.add(List.of(
            ContentBlockParam.ofDocument(DocumentBlockParam.builder()
                .source(Base64PdfSource.builder().data(Base64.getEncoder().encodeToString(part)).build())
                .build()),
            text(header + "\n이 PDF 는 원본 " + from + "~" + to + "쪽입니다 (쪽 번호는 원본 기준으로 적으세요). 구조화하세요.")));
      }
      return out;
    }
    for (String part : split(ex.text(), TEXT_BATCH_CHARS)) {
      out.add(List.of(text(header + "\n<material_text>\n" + part + "\n</material_text>\n위 자료를 구조화하세요."
          + (ex.pdf() != null ? " \"=== p.N ===\" 줄은 원본 쪽 경계입니다." : ""))));
    }
    return out;
  }

  private static String header(StructureRequest req) {
    StringBuilder sb = new StringBuilder("<material>\n제목: ").append(req.title())
        .append("\n종류: ").append(req.kind())
        .append("\n시험 자료: ").append(req.isExam() ? "예 (문항마다 배점·정답이 중요)" : "아니오");
    if (req.description() != null) {
      sb.append("\n설명: ").append(req.description());
    }
    sb.append("\n</material>");
    if (!req.labelCatalog().isEmpty()) {
      sb.append("\n<labels>\n");
      for (LabelRef l : req.labelCatalog()) {
        sb.append(l.id()).append(" = ").append(l.group()).append(" / ").append(l.name()).append('\n');
      }
      sb.append("</labels>");
    }
    return sb.toString();
  }

  /** 빈 줄 경계에서 maxChars 안팎으로 자른다 */
  static List<String> split(String text, int maxChars) {
    List<String> out = new ArrayList<>();
    if (text.length() <= maxChars) {
      out.add(text);
      return out;
    }
    StringBuilder buf = new StringBuilder();
    for (String para : text.split("(?<=\n)\\s*\n")) {
      if (buf.length() + para.length() > maxChars && buf.length() > 0) {
        out.add(buf.toString());
        buf.setLength(0);
      }
      while (para.length() > maxChars) {
        out.add(para.substring(0, maxChars));
        para = para.substring(maxChars);
      }
      buf.append(para).append('\n');
    }
    if (!buf.toString().isBlank()) {
      out.add(buf.toString());
    }
    return out;
  }

  private static TextBlockParam textBlock(String s) {
    return TextBlockParam.builder().text(s).build();
  }

  private static ContentBlockParam text(String s) {
    return ContentBlockParam.ofText(textBlock(s));
  }

  static ChunkInput toChunk(JsonNode c, Set<String> validLabels) {
    String content = Json.text(c, "content");
    if (content == null) {
      return null;
    }
    List<Choice> choices = new ArrayList<>();
    for (JsonNode ch : c.path("choices")) {
      String text = Json.text(ch, "text");
      if (text != null) {
        String label = Json.text(ch, "label");
        choices.add(new Choice(label == null ? String.valueOf(choices.size() + 1) : label, text));
      }
    }
    List<String> tags = new ArrayList<>();
    c.path("tags").forEach(t -> {
      if (!t.asText().isBlank()) {
        tags.add(t.asText().strip());
      }
    });
    List<UUID> labelIds = new ArrayList<>();
    c.path("labelIds").forEach(l -> {
      if (validLabels.contains(l.asText())) {
        labelIds.add(UUID.fromString(l.asText()));
      }
    });
    JsonNode score = c.get("score");
    return new ChunkInput(ChunkInput.chunkType(Json.text(c, "chunkType")), Json.text(c, "title"),
        Json.text(c, "position"), Json.text(c, "itemNo"), ChunkInput.questionType(Json.text(c, "questionType")),
        content, choices, Json.text(c, "answer"), Json.text(c, "explanation"),
        score == null || !score.isNumber() ? null : score.asDouble(), tags, labelIds, intOrNull(c, "pageFrom"),
        intOrNull(c, "pageTo"));
  }

  private static Integer intOrNull(JsonNode c, String field) {
    JsonNode v = c.get(field);
    return v == null || !v.canConvertToInt() || v.isNull() ? null : v.asInt();
  }

  private static Map<String, Object> nullable(Map<String, Object> schema) {
    return Map.of("anyOf", List.of(schema, Map.of("type", "null")));
  }

  static Map<String, Object> schema() {
    Map<String, Object> str = Map.of("type", "string");
    Map<String, Object> choice = Map.of("type", "object", "additionalProperties", false,
        "required", List.of("label", "text"), "properties", Map.of("label", str, "text", str));
    Map<String, Object> props = new LinkedHashMap<>();
    props.put("chunkType", Map.of("type", "string", "enum", ChunkInput.CHUNK_TYPES));
    props.put("title", nullable(str));
    props.put("position", nullable(str));
    props.put("itemNo", nullable(str));
    props.put("questionType", nullable(Map.of("type", "string", "enum", ChunkInput.QUESTION_TYPES)));
    props.put("content", str);
    props.put("choices", Map.of("type", "array", "items", choice));
    props.put("answer", nullable(str));
    props.put("explanation", nullable(str));
    props.put("score", nullable(Map.of("type", "number")));
    props.put("tags", Map.of("type", "array", "items", str));
    props.put("labelIds", Map.of("type", "array", "items", str));
    props.put("pageFrom", nullable(Map.of("type", "integer")));
    props.put("pageTo", nullable(Map.of("type", "integer")));
    Map<String, Object> chunk = Map.of("type", "object", "additionalProperties", false,
        "required", List.copyOf(props.keySet()), "properties", props);
    Map<String, Object> key = Map.of("type", "object", "additionalProperties", false,
        "required", List.of("itemNo", "answer", "explanation"),
        "properties", Map.of("itemNo", str, "answer", str, "explanation", nullable(str)));
    Map<String, Object> root = new HashMap<>();
    root.put("type", "object");
    root.put("additionalProperties", false);
    root.put("required", List.of("chunks", "answerKey"));
    root.put("properties", Map.of("chunks", Map.of("type", "array", "items", chunk),
        "answerKey", Map.of("type", "array", "items", key)));
    return root;
  }
}
