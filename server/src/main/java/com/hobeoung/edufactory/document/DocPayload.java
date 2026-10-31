package com.hobeoung.edufactory.document;

import com.hobeoung.edufactory.context.Conditions;
import com.hobeoung.edufactory.exam.StatsCalculator;
import com.hobeoung.edufactory.material.Choice;
import com.hobeoung.edufactory.material.ChunkInput;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 산출물 구조 — 원본. 문제지·정답해설지는 이 구조의 뷰 */
public record DocPayload(String title, String kind, String audience, String summary, String instructions,
    List<Section> sections, List<Evidence> evidence, String changeNote) {

  public record Criterion(String text, double score) {}

  public record Item(String number, String type, String stem, List<Choice> choices, String answer, String explanation,
      Double score, List<Criterion> criteria, Double difficulty, List<String> tags, List<String> sourceRefs) {
    public Item {
      choices = choices == null ? List.of() : choices;
      criteria = criteria == null ? List.of() : criteria;
      tags = tags == null ? List.of() : tags;
      sourceRefs = sourceRefs == null ? List.of() : sourceRefs;
    }

    /** 유사도·난이도 검색 입력 */
    public String searchText() {
      StringBuilder sb = new StringBuilder(stem == null ? "" : stem);
      for (Choice c : choices) {
        sb.append('\n').append(c.label()).append(' ').append(c.text());
      }
      return sb.toString();
    }
  }

  public record Section(String title, String body, List<Item> items) {
    public Section {
      items = items == null ? List.of() : items;
    }
  }

  public record Evidence(String kind, String ref, String summary) {}

  public DocPayload {
    kind = kind != null && Conditions.OUTPUT_KINDS.contains(kind) ? kind : "OTHER";
    sections = sections == null ? List.of() : sections;
    evidence = evidence == null ? List.of() : evidence;
  }

  public List<Item> items() {
    List<Item> out = new ArrayList<>();
    sections.forEach(s -> out.addAll(s.items()));
    return out;
  }

  public Double totalScore() {
    List<Item> items = items();
    if (items.isEmpty() || items.stream().allMatch(i -> i.score() == null)) {
      return null;
    }
    return items.stream().mapToDouble(i -> i.score() == null ? 0 : i.score()).sum();
  }

  private static final Set<String> CHOICE_TYPES = Set.of("MULTIPLE_CHOICE", "MULTI_SELECT", "ORDERING", "MATCHING");

  /** @return 오류 목록 (비면 통과) */
  public List<String> validate() {
    List<String> errors = new ArrayList<>();
    if (title == null || title.isBlank()) {
      errors.add("title 이 비었습니다");
    }
    if (sections.isEmpty()) {
      errors.add("sections 가 비었습니다");
    }
    Set<String> numbers = new HashSet<>();
    for (int s = 0; s < sections.size(); s++) {
      Section sec = sections.get(s);
      if ((sec.body() == null || sec.body().isBlank()) && sec.items().isEmpty()) {
        errors.add("섹션 " + (s + 1) + ": body 와 items 가 모두 비었습니다");
      }
      for (Item item : sec.items()) {
        String no = item.number() == null || item.number().isBlank() ? "?" : item.number().strip();
        String at = no + "번: ";
        if ("?".equals(no)) {
          errors.add("섹션 " + (s + 1) + ": 번호 없는 문항이 있습니다");
        } else if (!numbers.add(no)) {
          errors.add(at + "번호가 중복됩니다");
        }
        if (item.type() == null || !ChunkInput.QUESTION_TYPES.contains(item.type())) {
          errors.add(at + "type 이 올바르지 않습니다 (" + item.type() + ")");
        }
        if (item.stem() == null || item.stem().isBlank()) {
          errors.add(at + "stem(발문)이 비었습니다");
        }
        if (item.answer() == null || item.answer().isBlank()) {
          errors.add(at + "answer(정답)가 비었습니다");
        }
        if (item.type() != null && CHOICE_TYPES.contains(item.type()) && item.choices().size() < 2) {
          errors.add(at + "선택지가 2개 이상 필요합니다");
        }
        if (("MULTIPLE_CHOICE".equals(item.type()) || "MULTI_SELECT".equals(item.type()))
            && item.answer() != null && !item.choices().isEmpty()) {
          Set<String> labels = new HashSet<>();
          item.choices().forEach(c -> labels.add(StatsCalculator.normalize(c.label())));
          for (String a : item.answer().split("[,/|]")) {
            if (!a.isBlank() && !labels.contains(StatsCalculator.normalize(a))) {
              errors.add(at + "정답 '" + a.strip() + "'이(가) 선택지 표기에 없습니다");
            }
          }
        }
        if (item.score() != null && item.score() < 0) {
          errors.add(at + "배점이 음수입니다");
        }
        if (!item.criteria().isEmpty() && item.score() != null) {
          double sum = item.criteria().stream().mapToDouble(Criterion::score).sum();
          if (Math.abs(sum - item.score()) > 0.01) {
            errors.add(at + "채점기준 배점 합(" + sum + ")이 문항 배점(" + item.score() + ")과 다릅니다");
          }
        }
        if (item.difficulty() != null && (item.difficulty() < 0 || item.difficulty() > 1)) {
          errors.add(at + "difficulty 는 0~1 예상 정답률입니다");
        }
      }
    }
    return errors;
  }
}
