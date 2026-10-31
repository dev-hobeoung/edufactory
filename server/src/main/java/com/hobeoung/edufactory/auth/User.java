package com.hobeoung.edufactory.auth;

import java.util.UUID;

public record User(UUID id, String email, String name, String role) {

  public boolean admin() {
    return "ADMIN".equals(role);
  }
}
