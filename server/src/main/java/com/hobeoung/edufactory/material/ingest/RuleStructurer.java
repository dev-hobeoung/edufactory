package com.hobeoung.edufactory.material.ingest;

import com.hobeoung.edufactory.material.Choice;
import com.hobeoung.edufactory.material.ChunkInput;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Claude 키가 없을 때의 규칙 기반 분할. 시험 자료면 "1." "2)" "[3]" "문제 4" 같은 번호로 문항을 나누고
 * ①~⑤ 선택지를 떼어 낸다. 아니면 제목(#)·빈 줄 기준으로 1,500자 안팎 단락으로 묶는다.
 */
@Component
public class RuleStructurer {

  private static final Pattern ITEM_START = Pattern.compile(
      "^\\s*(?:문제\\s*)?(?:\\[(\\d{1,3})\\]|(\\d{1,3})\\s*[.)번])\\s+(.*)$");
  private static final Pattern CHOICE = Pattern.compile("([①②③④⑤⑥⑦⑧⑨⑩])\\s*([^①②③④⑤⑥⑦⑧⑨⑩]+)");
  private static final Pattern PAGE_MARK = Pattern.compile("^=== p\\.(\\d+) ===$");
  private static final int TARGET = 1500;

  public List<ChunkInput> structure(StructureRequest req) {
    String text = req.extracted().text();
    if (req.isExam()) {
      List<ChunkInput> items = items(text);
      if (items.stream().filter(c -> "QUESTION".equals(c.chunkType())).count() >= 2) {
        return items;
      }
    }
    return sections(text);
  }

  List<ChunkInput> items(String text) {
    List<ChunkInput> out = new ArrayList<>();
    StringBuilder preface = new StringBuilder();
    String currentNo = null;
    StringBuilder current = null;
    Integer page = null;
    Integer itemPage = null;
    int expected = 1;
    for (String line : text.split("\n")) {
      Matcher pm = PAGE_MARK.matcher(line.strip());
      if (pm.matches()) {
        page = Integer.parseInt(pm.group(1));
        continue;
      }
      Matcher m = ITEM_START.matcher(line);
      if (m.matches()) {
        String no = m.group(1) != null ? m.group(1) : m.group(2);
        int n = Integer.parseInt(no);
        // 본문 속 "2) 조건" 같은 줄을 문항으로 오인하지 않도록 번호가 순서대로 늘 때만 새 문항
        if (n == expected || (current == null && n == 1)) {
          if (current != null) {
            out.add(question(currentNo, current.toString(), itemPage, page));
          }
          currentNo = no;
          current = new StringBuilder(m.group(3)).append('\n');
          itemPage = page;
          expected = n + 1;
          continue;
        }
      }
      if (current != null) {
        current.append(line).append('\n');
      } else {
        preface.append(line).append('\n');
      }
    }
    if (current != null) {
      out.add(question(currentNo, current.toString(), itemPage, page));
    }
    if (!preface.toString().isBlank()) {
      out.add(0, new ChunkInput("PASSAGE", null, "앞부분", null, null, preface.toString().strip(), List.of(), null, null,
          null, List.of(), null, null, null));
    }
    return out;
  }

  private static ChunkInput question(String no, String body, Integer pageFrom, Integer pageTo) {
    List<Choice> choices = new ArrayList<>();
    String stem = body.strip();
    int first = indexOfChoice(stem);
    if (first >= 0) {
      Matcher cm = CHOICE.matcher(stem.substring(first));
      while (cm.find()) {
        choices.add(new Choice(cm.group(1), cm.group(2).strip()));
      }
      stem = stem.substring(0, first).strip();
    }
    String type = choices.size() >= 2 ? "MULTIPLE_CHOICE" : "SHORT_ANSWER";
    return new ChunkInput("QUESTION", null, no + "번", no, type, stem, choices, null, null, null, List.of(), null,
        pageFrom, pageTo);
  }

  private static int indexOfChoice(String s) {
    for (int i = 0; i < s.length(); i++) {
      if ("①②③④⑤⑥⑦⑧⑨⑩".indexOf(s.charAt(i)) >= 0) {
        return i;
      }
    }
    return -1;
  }

  List<ChunkInput> sections(String text) {
    List<ChunkInput> out = new ArrayList<>();
    StringBuilder buf = new StringBuilder();
    String title = null;
    for (String para : text.replaceAll("(?m)^=== p\\.\\d+ ===$", "").split("\n\\s*\n")) {
      String p = para.strip();
      if (p.isEmpty()) {
        continue;
      }
      boolean heading = p.startsWith("#");
      if ((heading || buf.length() + p.length() > TARGET) && buf.length() > 0) {
        out.add(section(title, buf.toString()));
        buf.setLength(0);
        title = null;
      }
      if (heading) {
        String firstLine = p.split("\n", 2)[0];
        title = firstLine.replaceFirst("^#+\\s*", "").strip();
      }
      buf.append(p).append("\n\n");
    }
    if (buf.length() > 0) {
      out.add(section(title, buf.toString()));
    }
    return out;
  }

  private static ChunkInput section(String title, String body) {
    return new ChunkInput("SECTION", title, null, null, null, body.strip(), List.of(), null, null, null, List.of(),
        null, null, null);
  }
}
