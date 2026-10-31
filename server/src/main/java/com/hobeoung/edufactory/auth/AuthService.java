package com.hobeoung.edufactory.auth;

import com.hobeoung.edufactory.common.ApiException;
import com.hobeoung.edufactory.common.Sql;
import com.hobeoung.edufactory.config.EduProperties;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);
  private static final Duration SESSION_TTL = Duration.ofDays(30);
  private static final RowMapper<User> USER = (rs, i) -> new User(Sql.uuid(rs, "id"), rs.getString("email"),
      rs.getString("name"), rs.getString("role"));

  private final NamedParameterJdbcTemplate jdbc;
  private final EduProperties props;
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
  private final SecureRandom random = new SecureRandom();

  public AuthService(NamedParameterJdbcTemplate jdbc, EduProperties props) {
    this.jdbc = jdbc;
    this.props = props;
  }

  /** 사용자가 하나도 없으면 설정의 관리자 계정을 만든다 */
  @EventListener(ApplicationReadyEvent.class)
  public void seedAdmin() {
    Integer count = jdbc.getJdbcTemplate().queryForObject("SELECT count(*) FROM ef_user", Integer.class);
    if (count != null && count == 0) {
      create(props.admin().email(), "관리자", props.admin().password(), "ADMIN");
      log.info("관리자 계정을 만들었습니다: {}", props.admin().email());
    }
  }

  public record Login(String token, User user) {}

  public Login login(String email, String password) {
    if (email == null || password == null) {
      throw ApiException.badRequest("이메일과 비밀번호를 입력하세요");
    }
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT id, password_hash FROM ef_user WHERE email = :e", Map.of("e", email.trim().toLowerCase(Locale.ROOT)));
    if (rows.isEmpty() || !encoder.matches(password, (String) rows.get(0).get("password_hash"))) {
      throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
          "이메일 또는 비밀번호가 올바르지 않습니다");
    }
    UUID userId = (UUID) rows.get(0).get("id");
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    jdbc.update("INSERT INTO ef_session (token, user_id, expires_at) VALUES (:t, :u, :x)",
        Map.of("t", token, "u", userId, "x", Timestamp.from(Instant.now().plus(SESSION_TTL))));
    jdbc.update("DELETE FROM ef_session WHERE expires_at < now()", Map.of());
    return new Login(token, find(userId).orElseThrow());
  }

  public void logout(String token) {
    jdbc.update("DELETE FROM ef_session WHERE token = :t", Map.of("t", token));
  }

  public Optional<User> byToken(String token) {
    return jdbc.query("SELECT u.id, u.email, u.name, u.role FROM ef_session s JOIN ef_user u ON u.id = s.user_id"
        + " WHERE s.token = :t AND s.expires_at > now()", Map.of("t", token), USER).stream().findFirst();
  }

  public Optional<User> find(UUID id) {
    return jdbc.query("SELECT id, email, name, role FROM ef_user WHERE id = :id", Map.of("id", id), USER).stream()
        .findFirst();
  }

  public List<User> list() {
    return jdbc.query("SELECT id, email, name, role FROM ef_user ORDER BY created_at", Map.of(), USER);
  }

  public User create(String email, String name, String password, String role) {
    if (email == null || email.isBlank() || password == null || password.length() < 6) {
      throw ApiException.badRequest("이메일과 6자 이상 비밀번호가 필요합니다");
    }
    String normalized = email.trim().toLowerCase(Locale.ROOT);
    String r = "ADMIN".equals(role) ? "ADMIN" : "EDITOR";
    Integer exists = jdbc.queryForObject("SELECT count(*) FROM ef_user WHERE email = :e", Map.of("e", normalized),
        Integer.class);
    if (exists != null && exists > 0) {
      throw ApiException.conflict("이미 있는 이메일입니다");
    }
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO ef_user (id, email, name, password_hash, role) VALUES (:id, :e, :n, :p, :r)",
        Map.of("id", id, "e", normalized, "n", name == null || name.isBlank() ? normalized : name.trim(), "p",
            encoder.encode(password), "r", r));
    return find(id).orElseThrow();
  }

  public void delete(UUID id) {
    if (id.equals(CurrentUser.get().id())) {
      throw ApiException.badRequest("자기 자신은 삭제할 수 없습니다");
    }
    jdbc.update("DELETE FROM ef_user WHERE id = :id", Map.of("id", id));
  }
}
