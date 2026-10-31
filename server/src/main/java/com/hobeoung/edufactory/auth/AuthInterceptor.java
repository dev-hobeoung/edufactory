package com.hobeoung.edufactory.auth;

import com.hobeoung.edufactory.common.Json;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Authorization: Bearer 토큰 검사 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

  private final AuthService auth;

  public AuthInterceptor(AuthService auth) {
    this.auth = auth;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {
    if (HttpMethod.OPTIONS.matches(request.getMethod())) {
      return true;
    }
    String token = token(request);
    User user = token == null ? null : auth.byToken(token).orElse(null);
    if (user == null) {
      response.setStatus(401);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.setCharacterEncoding("UTF-8");
      response.getWriter().write(Json.write(Map.of("code", "UNAUTHORIZED", "message", "로그인이 필요합니다")));
      return false;
    }
    CurrentUser.set(user);
    return true;
  }

  @Override
  public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
      Exception ex) {
    CurrentUser.clear();
  }

  static String token(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      String t = header.substring(7).trim();
      return t.isEmpty() ? null : t;
    }
    // 파일 다운로드(<a href>)용
    String q = request.getParameter("access_token");
    return q == null || q.isBlank() ? null : q;
  }
}
