package io.yak.ops.core.execution.sql;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dependency-free conservative SQL lexer used for platform policy decisions.
 *
 * <p>This is intentionally not a dialect parser. It skips comments and quoted content, tracks
 * parenthesis depth, identifies the first known top-level statement semantic, and records known
 * semantics observed anywhere in the SQL. That lets read-only policy reject cases such as a
 * data-modifying CTE even when the outer statement is SELECT.
 */
public final class LexicalSqlStatementClassifier implements SqlStatementClassifier {

  private static final Map<String, SqlStatementType> KEYWORDS = Map.ofEntries(
      Map.entry("SELECT", SqlStatementType.SELECT),
      Map.entry("VALUES", SqlStatementType.VALUES),
      Map.entry("SHOW", SqlStatementType.SHOW),
      Map.entry("DESCRIBE", SqlStatementType.DESCRIBE),
      Map.entry("DESC", SqlStatementType.DESCRIBE),
      Map.entry("INSERT", SqlStatementType.INSERT),
      Map.entry("UPDATE", SqlStatementType.UPDATE),
      Map.entry("DELETE", SqlStatementType.DELETE),
      Map.entry("MERGE", SqlStatementType.MERGE),
      Map.entry("REPLACE", SqlStatementType.REPLACE),
      Map.entry("UPSERT", SqlStatementType.MERGE),
      Map.entry("CREATE", SqlStatementType.CREATE),
      Map.entry("ALTER", SqlStatementType.ALTER),
      Map.entry("DROP", SqlStatementType.DROP),
      Map.entry("TRUNCATE", SqlStatementType.TRUNCATE),
      Map.entry("GRANT", SqlStatementType.GRANT),
      Map.entry("REVOKE", SqlStatementType.REVOKE),
      Map.entry("CALL", SqlStatementType.CALL),
      Map.entry("EXEC", SqlStatementType.CALL),
      Map.entry("EXECUTE", SqlStatementType.CALL),
      Map.entry("EXPLAIN", SqlStatementType.EXPLAIN),
      Map.entry("SET", SqlStatementType.SET),
      Map.entry("BEGIN", SqlStatementType.BEGIN),
      Map.entry("START", SqlStatementType.BEGIN),
      Map.entry("COMMIT", SqlStatementType.COMMIT),
      Map.entry("ROLLBACK", SqlStatementType.ROLLBACK));

  @Override
  public SqlStatementClassification classify(String sql) {
    if (sql == null || sql.isBlank()) return SqlStatementClassification.other();

    List<Token> tokens = scan(sql);
    EnumSet<SqlStatementType> observed = EnumSet.noneOf(SqlStatementType.class);
    SqlStatementType primary = SqlStatementType.OTHER;

    for (Token token : tokens) {
      SqlStatementType type = KEYWORDS.get(token.word());
      if (type == null) continue;
      observed.add(type);
      if (primary == SqlStatementType.OTHER && token.depth() == 0) {
        primary = type;
      }
    }
    return new SqlStatementClassification(primary, observed);
  }

  private static List<Token> scan(String sql) {
    List<Token> tokens = new ArrayList<>();
    int depth = 0;
    int index = 0;
    while (index < sql.length()) {
      char current = sql.charAt(index);

      if (Character.isWhitespace(current) || current == ';' || current == ',') {
        index++;
        continue;
      }
      if (current == '-' && hasNext(sql, index, '-')) {
        index = skipLine(sql, index + 2);
        continue;
      }
      if (current == '#') {
        index = skipLine(sql, index + 1);
        continue;
      }
      if (current == '/' && hasNext(sql, index, '*')) {
        index = skipBlockComment(sql, index + 2);
        continue;
      }
      if (current == '\'' || current == '"' || current == '`') {
        index = skipQuoted(sql, index, current);
        continue;
      }
      if (current == '[') {
        index = skipBracketIdentifier(sql, index + 1);
        continue;
      }
      if (current == '$') {
        int afterDollarQuote = skipDollarQuoted(sql, index);
        if (afterDollarQuote > index) {
          index = afterDollarQuote;
          continue;
        }
      }
      if (current == '(') {
        depth++;
        index++;
        continue;
      }
      if (current == ')') {
        depth = Math.max(0, depth - 1);
        index++;
        continue;
      }
      if (isWordStart(current)) {
        int end = index + 1;
        while (end < sql.length() && isWordPart(sql.charAt(end))) end++;
        String word = sql.substring(index, end).toUpperCase(Locale.ROOT);
        tokens.add(new Token(word, depth));
        index = end;
        continue;
      }
      index++;
    }
    return tokens;
  }

  private static boolean hasNext(String sql, int index, char expected) {
    return index + 1 < sql.length() && sql.charAt(index + 1) == expected;
  }

  private static int skipLine(String sql, int index) {
    int current = index;
    while (current < sql.length()) {
      char value = sql.charAt(current++);
      if (value == '\n' || value == '\r') break;
    }
    return current;
  }

  private static int skipBlockComment(String sql, int index) {
    int current = index;
    while (current + 1 < sql.length()) {
      if (sql.charAt(current) == '*' && sql.charAt(current + 1) == '/') return current + 2;
      current++;
    }
    return sql.length();
  }

  private static int skipQuoted(String sql, int index, char quote) {
    int current = index + 1;
    while (current < sql.length()) {
      char value = sql.charAt(current);
      if (value == quote) {
        if (current + 1 < sql.length() && sql.charAt(current + 1) == quote) {
          current += 2;
          continue;
        }
        return current + 1;
      }
      if (value == '\\' && current + 1 < sql.length()) {
        current += 2;
      } else {
        current++;
      }
    }
    return sql.length();
  }

  private static int skipBracketIdentifier(String sql, int index) {
    int current = index;
    while (current < sql.length()) {
      if (sql.charAt(current) == ']') {
        if (current + 1 < sql.length() && sql.charAt(current + 1) == ']') {
          current += 2;
          continue;
        }
        return current + 1;
      }
      current++;
    }
    return sql.length();
  }

  private static int skipDollarQuoted(String sql, int index) {
    int tagEnd = index + 1;
    while (tagEnd < sql.length() && isDollarTagPart(sql.charAt(tagEnd))) tagEnd++;
    if (tagEnd >= sql.length() || sql.charAt(tagEnd) != '$') return index;
    String tag = sql.substring(index, tagEnd + 1);
    int closing = sql.indexOf(tag, tagEnd + 1);
    return closing < 0 ? sql.length() : closing + tag.length();
  }

  private static boolean isWordStart(char value) {
    return Character.isLetter(value) || value == '_';
  }

  private static boolean isWordPart(char value) {
    return Character.isLetterOrDigit(value) || value == '_' || value == '$';
  }

  private static boolean isDollarTagPart(char value) {
    return Character.isLetterOrDigit(value) || value == '_';
  }

  private record Token(String word, int depth) {}
}
