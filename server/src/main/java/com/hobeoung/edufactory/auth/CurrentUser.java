package com.hobeoung.edufactory.auth;

import com.hobeoung.edufactory.common.ApiException;
import java.util.UUID;

/** 요청 스레드의 로그인 사용자 (AuthInterceptor 가 채운다) */
public final class CurrentUser {

  private static final ThreadLocal<User> HOLDER = new ThreadLocal<>();

  private CurrentUser() {}

  static void set(User user) {
    HOLDER.set(user);
  }

  static void clear() {
    HOLDER.remove();
  }

  public static User get() {
    User user = HOLDER.get();
    if (user == null) {
      throw ApiException.unauthorized();
    }
    return user;
  }

  /** 백그라운드 스레드처럼 로그인 정보가 없을 수 있는 곳 */
  public static UUID idOrNull() {
    User user = HOLDER.get();
    return user == null ? null : user.id();
  }

  public static void requireAdmin() {
    if (!get().admin()) {
      throw ApiException.forbidden("관리자만 할 수 있습니다");
    }
  }
}
