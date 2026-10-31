package com.hobeoung.edufactory.material.ingest;

import java.util.List;

/**
 * 추출 결과. pages 는 PDF 일 때 페이지별 텍스트 (그 외는 한 덩어리), pdf 는 원본 바이트(스캔본이면 AI 에 PDF 로 보낸다).
 */
public record ExtractedText(String text, List<String> pages, byte[] pdf) {

  /** 페이지당 평균 글자가 적으면 스캔본으로 본다 */
  public boolean looksScanned() {
    if (pdf == null || pages.isEmpty()) {
      return false;
    }
    int chars = pages.stream().mapToInt(p -> p.strip().length()).sum();
    return chars / pages.size() < 80;
  }
}
