package com.hobeoung.edufactory.document;

import com.hobeoung.edufactory.material.Choice;

/** 산출물 → Markdown (학생용 / 정답·해설 포함) */
public final class MarkdownExporter {

  private MarkdownExporter() {}

  public static String export(DocPayload p, boolean answers) {
    StringBuilder sb = new StringBuilder("# ").append(p.title()).append("\n\n");
    if (p.audience() != null) {
      sb.append("> 대상: ").append(p.audience()).append("\n\n");
    }
    if (p.instructions() != null) {
      sb.append(p.instructions()).append("\n\n");
    }
    for (DocPayload.Section s : p.sections()) {
      if (s.title() != null) {
        sb.append("## ").append(s.title()).append("\n\n");
      }
      if (s.body() != null) {
        sb.append(s.body()).append("\n\n");
      }
      for (DocPayload.Item item : s.items()) {
        sb.append("**").append(item.number()).append(".** ").append(item.stem());
        if (item.score() != null) {
          sb.append(" [").append(fmt(item.score())).append("점]");
        }
        sb.append("\n\n");
        for (Choice c : item.choices()) {
          sb.append("- ").append(c.label()).append(' ').append(c.text()).append('\n');
        }
        if (!item.choices().isEmpty()) {
          sb.append('\n');
        }
      }
    }
    if (answers) {
      sb.append("\n---\n\n# 정답 및 해설\n\n");
      for (DocPayload.Item item : p.items()) {
        sb.append("**").append(item.number()).append(".** 정답: ").append(item.answer()).append("\n\n");
        if (item.explanation() != null) {
          sb.append(item.explanation()).append("\n\n");
        }
        if (!item.criteria().isEmpty()) {
          sb.append("채점기준\n\n");
          for (DocPayload.Criterion c : item.criteria()) {
            sb.append("- ").append(c.text()).append(" (").append(fmt(c.score())).append("점)\n");
          }
          sb.append('\n');
        }
      }
      if (p.summary() != null) {
        sb.append("\n## 출제 의도\n\n").append(p.summary()).append('\n');
      }
    }
    return sb.toString();
  }

  private static String fmt(double v) {
    return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
  }
}
