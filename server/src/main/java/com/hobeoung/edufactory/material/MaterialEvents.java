package com.hobeoung.edufactory.material;

import java.util.UUID;

public final class MaterialEvents {

  private MaterialEvents() {}

  /** 시험 문항의 정답·배점·유형이 바뀌었거나 문항이 지워짐 — 성적 통계를 다시 낸다 */
  public record ItemChanged(UUID materialId) {}
}
