package io.yak.ops.core.execution.sql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Splits a SQL script into individual statements on top-level semicolons.
 *
 * <p>Semicolons inside string literals, quoted identifiers, line/block comments, and
 * dollar-quoted bodies are respected and never treated as statement boundaries. Empty
 * statements (consecutive semicolons or trailing semicolons) are silently skipped.
 *
 * <p>This is intentionally not a dialect parser; it is a conservative lexer sufficient
 * for the platform SQL task execution path.
 */
public final class SqlScriptSplitter {

  private SqlScriptSplitter() {}

  /**
   * Split a SQL script into individual statements.
   *
   * @param script the SQL script text, possibly containing multiple semicolon-separated statements
   * @return an unmodifiable list of trimmed, non-blank statement strings
   */
  public static List<String> split(String script) {
    if (script == null || script.isBlank()) return List.of();

    List<String> statements = new ArrayList<>();
    int length = script.length();
    int statementStart = 0;
    int index = 0;

    while (index < length) {
      char current = script.charAt(index);

      // Top-level semicolon – flush the accumulated statement
      if (current == ';') {
        String statement = script.substring(statementStart, index).trim();
        if (!statement.isBlank()) {
          statements.add(statement);
        }
        index++;
        statementStart = index;
        continue;
      }

      // Skip whitespace
      if (Character.isWhitespace(current)) {
        index++;
        continue;
      }

      // Line comment
      if (current == '-' && index + 1 < length && script.charAt(index + 1) == '-') {
        index = skipLineComment(script, index + 2);
        continue;
      }

      // Hash-style line comment (MySQL)
      if (current == '#') {
        index = skipLineComment(script, index + 1);
        continue;
      }

      // Block comment
      if (current == '/' && index + 1 < length && script.charAt(index + 1) == '*') {
        index = skipBlockComment(script, index + 2);
        continue;
      }

      // Single-quoted string literal
      if (current == '\'') {
        index = skipQuoted(script, index + 1, '\'', true);
        continue;
      }

      // Double-quoted string literal or identifier
      if (current == '"') {
        index = skipQuoted(script, index + 1, '"', true);
        continue;
      }

      // Backtick-quoted identifier
      if (current == '`') {
        index = skipQuoted(script, index + 1, '`', true);
        continue;
      }

      // Bracket-quoted identifier (SQL Server)
      if (current == '[') {
        index = skipBracketIdentifier(script, index + 1);
        continue;
      }

      // Dollar-quoted string (PostgreSQL)
      if (current == '$') {
        int afterDollar = skipDollarQuoted(script, index);
        if (afterDollar > index) {
          index = afterDollar;
          continue;
        }
      }

      index++;
    }

    // Flush trailing statement (script without trailing semicolon)
    if (statementStart < length) {
      String statement = script.substring(statementStart).trim();
      if (!statement.isBlank()) {
        statements.add(statement);
      }
    }

    return Collections.unmodifiableList(statements);
  }

  private static int skipLineComment(String sql, int index) {
    while (index < sql.length()) {
      char value = sql.charAt(index);
      if (value == '\n' || value == '\r') return index + 1;
      index++;
    }
    return sql.length();
  }

  private static int skipBlockComment(String sql, int index) {
    while (index + 1 < sql.length()) {
      if (sql.charAt(index) == '*' && sql.charAt(index + 1) == '/') return index + 2;
      index++;
    }
    return sql.length();
  }

  private static int skipQuoted(String sql, int index, char quote, boolean doubledEscape) {
    while (index < sql.length()) {
      char value = sql.charAt(index);
      if (value == quote) {
        if (doubledEscape && index + 1 < sql.length() && sql.charAt(index + 1) == quote) {
          index += 2;
          continue;
        }
        return index + 1;
      }
      if (value == '\\' && index + 1 < sql.length()) {
        index += 2;
      } else {
        index++;
      }
    }
    return sql.length();
  }

  private static int skipBracketIdentifier(String sql, int index) {
    while (index < sql.length()) {
      if (sql.charAt(index) == ']') {
        if (index + 1 < sql.length() && sql.charAt(index + 1) == ']') {
          index += 2;
          continue;
        }
        return index + 1;
      }
      index++;
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

  private static boolean isDollarTagPart(char value) {
    return Character.isLetterOrDigit(value) || value == '_';
  }
}
