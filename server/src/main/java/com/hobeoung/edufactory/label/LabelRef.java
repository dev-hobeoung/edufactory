package com.hobeoung.edufactory.label;

import java.util.UUID;

public record LabelRef(UUID id, String group, String name, String color) {

  /** 임베딩·프롬프트용 표기 "그룹:이름" */
  public String display() {
    return group + ":" + name;
  }
}
