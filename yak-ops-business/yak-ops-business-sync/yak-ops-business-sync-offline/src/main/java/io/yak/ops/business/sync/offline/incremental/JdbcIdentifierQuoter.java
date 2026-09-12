package io.yak.ops.business.sync.offline.incremental;

import io.yak.ops.common.enums.datasource.DataSourceDbType;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Quotes validated JDBC identifiers for the catalog MAX query. */
final class JdbcIdentifierQuoter {

  private static final Pattern SAFE = Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*");

  private JdbcIdentifierQuoter() {}

  static String quote(DataSourceDbType type, String identifier) {
    String value = validate(identifier);
    if (type == DataSourceDbType.SQL_SERVER) return "[" + value + "]";
    if (type == DataSourceDbType.MYSQL
        || type == DataSourceDbType.TIDB
        || type == DataSourceDbType.DORIS
        || type == DataSourceDbType.GOLDENDB
        || type == DataSourceDbType.GBASE8A) {
      return "`" + value + "`";
    }
    return "\"" + value + "\"";
  }

  static String quotePath(DataSourceDbType type, String path) {
    return Arrays.stream(path.split("\\."))
        .map(part -> quote(type, part))
        .collect(Collectors.joining("."));
  }

  private static String validate(String value) {
    String normalized = value == null ? "" : value.trim();
    if (!SAFE.matcher(normalized).matches()) {
      throw new IllegalArgumentException("数据库标识符不是安全标识符：" + value);
    }
    return normalized;
  }
}
