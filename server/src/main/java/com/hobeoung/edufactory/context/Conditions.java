package com.hobeoung.edufactory.context;

import com.hobeoung.edufactory.label.LabelRef;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 작업실 요청 조건 — 사용자 메시지마다 함께 온다 */
public record Conditions(String outputKind, List<UUID> labelIds, List<UUID> materialIds, List<String> questionTypes,
    Integer itemCount, Double totalScore, Double targetCorrectRate, String audience, Integer creativity,
    String extra) {

  public static final List<String> OUTPUT_KINDS = List.of("EXAM", "QUIZ", "WORKSHEET", "LESSON_NOTE", "SUMMARY",
      "FLASHCARDS", "EXPLANATION", "OTHER");

  private static final Map<String, String> KIND_NAMES = Map.of("EXAM", "시험지", "QUIZ", "퀴즈·쪽지시험",
      "WORKSHEET", "학습지", "LESSON_NOTE", "수업 자료·개념 정리", "SUMMARY", "요약 노트", "FLASHCARDS", "플래시카드",
      "EXPLANATION", "해설 자료", "OTHER", "기타 교육자료");

  private static final Map<String, String> QUESTION_NAMES = Map.of("MULTIPLE_CHOICE", "객관식(단일 정답)",
      "MULTI_SELECT", "객관식(복수 정답)", "TRUE_FALSE", "OX", "SHORT_ANSWER", "단답형", "FILL_BLANK", "빈칸 채우기",
      "ESSAY", "서술형", "MATCHING", "짝짓기", "ORDERING", "순서 배열", "FLASHCARD", "플래시카드");

  public Conditions {
    outputKind = outputKind != null && OUTPUT_KINDS.contains(outputKind) ? outputKind : null;
    labelIds = labelIds == null ? List.of() : List.copyOf(labelIds);
    materialIds = materialIds == null ? List.of() : List.copyOf(materialIds);
    questionTypes = questionTypes == null ? List.of() : List.copyOf(questionTypes);
    audience = audience == null || audience.isBlank() ? null : audience.strip();
    extra = extra == null || extra.isBlank() ? null : extra.strip();
    creativity = creativity == null ? null : Math.max(0, Math.min(4, creativity));
  }

  public static Conditions empty() {
    return new Conditions(null, null, null, null, null, null, null, null, null, null);
  }

  public int creativityLevel() {
    return creativity == null ? 0 : creativity;
  }

  /** 모델에 보낼 &lt;conditions&gt; 본문 */
  public String describe(Map<UUID, LabelRef> labels, Map<UUID, String> materialTitles) {
    StringBuilder sb = new StringBuilder();
    if (outputKind != null) {
      sb.append("\n- 만들 자료: ").append(KIND_NAMES.get(outputKind)).append(" (").append(outputKind).append(')');
    }
    if (!labelIds.isEmpty()) {
      sb.append("\n- 참고 범위 라벨: ");
      sb.append(String.join(", ", labelIds.stream().map(id -> labels.containsKey(id)
          ? labels.get(id).group() + "/" + labels.get(id).name() + " (" + id + ")" : id.toString()).toList()));
    }
    if (!materialIds.isEmpty()) {
      sb.append("\n- 꼭 참고할 자료: ");
      sb.append(String.join(", ", materialIds.stream().map(id -> materialTitles.getOrDefault(id, "?") + " (" + id + ")")
          .toList()));
    }
    if (!questionTypes.isEmpty()) {
      sb.append("\n- 문항 유형: ").append(String.join(", ", questionTypes.stream()
          .map(t -> QUESTION_NAMES.getOrDefault(t, t) + " (" + t + ")").toList()));
    }
    if (itemCount != null) {
      sb.append("\n- 문항 수: ").append(itemCount);
    }
    if (totalScore != null) {
      sb.append("\n- 총점: ").append(fmt(totalScore));
    }
    if (targetCorrectRate != null) {
      sb.append("\n- 목표 정답률: ").append(Math.round(targetCorrectRate * 100)).append('%');
    }
    if (audience != null) {
      sb.append("\n- 대상: ").append(audience);
    }
    sb.append("\n- 창의성(외부 자료 참조): ").append(creativityLevel()).append("/4단계");
    if (extra != null) {
      sb.append("\n- 기타: ").append(extra);
    }
    return sb.append('\n').toString();
  }

  private static String fmt(double v) {
    return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
  }
}
