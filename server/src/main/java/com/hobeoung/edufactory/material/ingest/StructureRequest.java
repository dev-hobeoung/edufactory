package com.hobeoung.edufactory.material.ingest;

import com.hobeoung.edufactory.label.LabelRef;
import java.util.List;

/** 구조화 입력 — 자료 메타 + 추출 결과 + (autoLabel 이면) 라벨 목록 */
public record StructureRequest(String title, String kind, String description, boolean isExam,
    List<LabelRef> labelCatalog, ExtractedText extracted) {}
