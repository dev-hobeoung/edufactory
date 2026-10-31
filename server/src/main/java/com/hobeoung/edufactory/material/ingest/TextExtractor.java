package com.hobeoung.edufactory.material.ingest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

/** 업로드 파일 → 텍스트 */
@Component
public class TextExtractor {

  public ExtractedText extract(String sourceType, byte[] bytes, String pastedText) throws IOException {
    return switch (sourceType) {
      case "PDF" -> pdf(bytes);
      case "DOCX" -> single(docx(bytes));
      case "TEXT", "MARKDOWN" -> single(new String(bytes, StandardCharsets.UTF_8));
      default -> single(pastedText == null ? "" : pastedText);
    };
  }

  private static ExtractedText single(String text) {
    String t = text.replace("\r\n", "\n");
    return new ExtractedText(t, List.of(t), null);
  }

  private static ExtractedText pdf(byte[] bytes) throws IOException {
    try (PDDocument doc = Loader.loadPDF(bytes)) {
      PDFTextStripper stripper = new PDFTextStripper();
      stripper.setSortByPosition(true);
      List<String> pages = new ArrayList<>();
      StringBuilder all = new StringBuilder();
      for (int p = 1; p <= doc.getNumberOfPages(); p++) {
        stripper.setStartPage(p);
        stripper.setEndPage(p);
        String text = stripper.getText(doc).replace("\r\n", "\n");
        pages.add(text);
        all.append("\n\n=== p.").append(p).append(" ===\n").append(text);
      }
      return new ExtractedText(all.toString().strip(), pages, bytes);
    }
  }

  private static String docx(byte[] bytes) throws IOException {
    try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes));
        XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
      return extractor.getText();
    }
  }

  /** PDF 의 [from, to] (1부터) 페이지만 새 PDF 로 */
  public static byte[] pdfPages(byte[] pdf, int from, int to) throws IOException {
    try (PDDocument src = Loader.loadPDF(pdf); PDDocument out = new PDDocument()) {
      for (int p = from; p <= to && p <= src.getNumberOfPages(); p++) {
        out.importPage(src.getPage(p - 1));
      }
      java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
      out.save(bos);
      return bos.toByteArray();
    }
  }
}
