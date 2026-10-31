package com.hobeoung.edufactory.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

  private final AuthService auth;

  public AuthController(AuthService auth) {
    this.auth = auth;
  }

  public record LoginRequest(String email, String password) {}

  @PostMapping("/auth/login")
  public AuthService.Login login(@RequestBody LoginRequest req) {
    return auth.login(req.email(), req.password());
  }

  @PostMapping("/auth/logout")
  public ResponseEntity<Void> logout(HttpServletRequest request) {
    String token = AuthInterceptor.token(request);
    if (token != null) {
      auth.logout(token);
    }
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/auth/me")
  public User me() {
    return CurrentUser.get();
  }

  @GetMapping("/users")
  public List<User> users() {
    CurrentUser.requireAdmin();
    return auth.list();
  }

  public record CreateUser(String email, String name, String password, String role) {}

  @PostMapping("/users")
  public User create(@RequestBody CreateUser req) {
    CurrentUser.requireAdmin();
    return auth.create(req.email(), req.name(), req.password(), req.role());
  }

  @DeleteMapping("/users/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    CurrentUser.requireAdmin();
    auth.delete(id);
    return ResponseEntity.noContent().build();
  }
}
