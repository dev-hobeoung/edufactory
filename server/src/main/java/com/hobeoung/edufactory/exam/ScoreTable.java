package com.hobeoung.edufactory.exam;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 붙여 넣은 CSV·TSV(엑셀) → 표. 머리글 문항 번호 → 문항 */
final class ScoreTable {

  private ScoreTable() {}

  static List<String[]> parse(String text) {
    List<String[]> rows = new ArrayList<>();
    String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
    String firstLine = normalized.split("\n", 2)[0];
    char sep = firstLine.indexOf('\t') >= 0 ? '\t' : ',';
    for (String line : normalized.split("\n")) {
      if (line.isBlank()) {
        continue;
      }
      rows.add(split(line, sep));
    }
    return rows;
  }

  /** 따옴표로 감싼 칸("a,b") 지원 */
  static String[] split(String line, char sep) {
    List<String> cells = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < line.length(); i++) {
      char ch = line.charAt(i);
      if (quoted) {
        if (ch == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
          cur.append('"');
          i++;
        } else if (ch == '"') {
          quoted = false;
        } else {
          cur.append(ch);
        }
      } else if (ch == '"' && cur.length() == 0) {
        quoted = true;
      } else if (ch == sep) {
        cells.add(cur.toString().strip());
        cur.setLength(0);
      } else {
        cur.append(ch);
      }
    }
    cells.add(cur.toString().strip());
    return cells.toArray(String[]::new);
  }

  /** "3", "3번", "Q3", "문항3" → itemNo "3" 인 문항 */
  static UUID matchItem(String header, List<StatsCalculator.Item> items) {
    String h = header.strip();
    for (StatsCalculator.Item item : items) {
      if (item.itemNo() != null && item.itemNo().strip().equalsIgnoreCase(h)) {
        return item.chunkId();
      }
    }
    String digits = h.replaceAll("^(?i)(문항|문제|q|no\\.?)\\s*", "").replaceAll("번$", "").strip();
    for (StatsCalculator.Item item : items) {
      if (item.itemNo() != null && item.itemNo().strip().equalsIgnoreCase(digits)) {
        return item.chunkId();
      }
    }
    return null;
  }
}
