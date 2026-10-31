package com.hobeoung.edufactory.common;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;

/** ResultSet 읽기 도우미 */
public final class Sql {

  private Sql() {}

  public static String instant(ResultSet rs, String column) throws SQLException {
    Timestamp ts = rs.getTimestamp(column);
    return ts == null ? null : ts.toInstant().toString();
  }

  public static UUID uuid(ResultSet rs, String column) throws SQLException {
    Object v = rs.getObject(column);
    return v == null ? null : (UUID) v;
  }

  public static Double dbl(ResultSet rs, String column) throws SQLException {
    double v = rs.getDouble(column);
    return rs.wasNull() ? null : v;
  }

  public static Integer integer(ResultSet rs, String column) throws SQLException {
    int v = rs.getInt(column);
    return rs.wasNull() ? null : v;
  }

  public static Long lng(ResultSet rs, String column) throws SQLException {
    long v = rs.getLong(column);
    return rs.wasNull() ? null : v;
  }

  public static String str(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
