package io.yak.ops.modeling.ddl.doris.schema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Formats Doris DDL for previews and persisted publish snapshots.
 *
 * <p>The formatter only changes whitespace and line breaks. It does not rewrite identifiers,
 * literals, types, keys, comments, or distribution clauses.
 */
@Component
public class DdlFormatter {

    private static final List<String> CREATE_CLAUSES = Arrays.asList(
            "UNIQUE KEY",
            "DUPLICATE KEY",
            "AGGREGATE KEY",
            "PRIMARY KEY",
            "PARTITION BY",
            "DISTRIBUTED BY",
            "ORDER BY",
            "COMMENT",
            "PROPERTIES");

    private static final List<String> ALTER_CLAUSES = Arrays.asList(
            "ADD COLUMN",
            "DROP COLUMN",
            "MODIFY COLUMN",
            "ORDER BY",
            "PROPERTIES");

    public String format(String ddl) {
        if (ddl == null) {
            return null;
        }

        String sql = ddl.trim();
        if (sql.isEmpty()) {
            return sql;
        }
        if (startsWithKeyword(sql, "CREATE TABLE")) {
            return formatCreateTable(sql);
        }
        if (startsWithKeyword(sql, "ALTER TABLE")) {
            return formatAlterTable(sql);
        }
        return sql;
    }

    private String formatCreateTable(String sql) {
        int openParenthesis = findFirstUnquoted(sql, '(');
        if (openParenthesis < 0) {
            return sql;
        }

        int closeParenthesis = findMatchingParenthesis(sql, openParenthesis);
        if (closeParenthesis < 0) {
            return sql;
        }

        List<String> columns = splitTopLevel(sql.substring(openParenthesis + 1, closeParenthesis), ',');
        if (columns.isEmpty()) {
            return sql;
        }

        StringBuilder formatted = new StringBuilder(sql.length() + columns.size() * 8);
        formatted.append(sql.substring(0, openParenthesis).trim()).append(" (\n");
        for (int index = 0; index < columns.size(); index++) {
            formatted.append("    ").append(columns.get(index));
            if (index < columns.size() - 1) {
                formatted.append(',');
            }
            formatted.append('\n');
        }
        formatted.append(')');

        String suffix = sql.substring(closeParenthesis + 1).trim();
        if (!suffix.isEmpty()) {
            formatted.append('\n').append(formatClauses(suffix, CREATE_CLAUSES, ""));
        }
        return formatted.toString();
    }

    private String formatAlterTable(String sql) {
        return formatClauses(sql, ALTER_CLAUSES, "    ");
    }

    private String formatClauses(String sql, List<String> clauses, String indentation) {
        List<String> segments = new ArrayList<>();
        int segmentStart = 0;
        int depth = 0;

        for (int index = 0; index < sql.length(); index++) {
            char current = sql.charAt(index);
            if (current == '\'' || current == '"' || current == '`') {
                index = skipQuoted(sql, index, current);
                continue;
            }
            if (current == '-' && hasNext(sql, index, '-')) {
                index = skipLineComment(sql, index);
                continue;
            }
            if (current == '/' && hasNext(sql, index, '*')) {
                index = skipBlockComment(sql, index);
                continue;
            }
            if (current == '(') {
                depth++;
                continue;
            }
            if (current == ')') {
                depth = Math.max(0, depth - 1);
                continue;
            }
            if (depth != 0 || index <= segmentStart) {
                continue;
            }

            String clause = matchingClause(sql, index, clauses);
            if (clause != null) {
                String previous = sql.substring(segmentStart, index).trim();
                if (!previous.isEmpty()) {
                    segments.add(previous);
                }
                segmentStart = index;
                index += clause.length() - 1;
            }
        }

        String last = sql.substring(segmentStart).trim();
        if (!last.isEmpty()) {
            segments.add(last);
        }
        return String.join("\n" + indentation, segments);
    }

    private List<String> splitTopLevel(String sql, char separator) {
        List<String> parts = new ArrayList<>();
        int segmentStart = 0;
        int depth = 0;

        for (int index = 0; index < sql.length(); index++) {
            char current = sql.charAt(index);
            if (current == '\'' || current == '"' || current == '`') {
                index = skipQuoted(sql, index, current);
                continue;
            }
            if (current == '-' && hasNext(sql, index, '-')) {
                index = skipLineComment(sql, index);
                continue;
            }
            if (current == '/' && hasNext(sql, index, '*')) {
                index = skipBlockComment(sql, index);
                continue;
            }
            if (current == '(') {
                depth++;
            } else if (current == ')') {
                depth = Math.max(0, depth - 1);
            } else if (current == separator && depth == 0) {
                addPart(parts, sql.substring(segmentStart, index));
                segmentStart = index + 1;
            }
        }

        addPart(parts, sql.substring(segmentStart));
        return parts;
    }

    private int findFirstUnquoted(String sql, char target) {
        for (int index = 0; index < sql.length(); index++) {
            char current = sql.charAt(index);
            if (current == '\'' || current == '"' || current == '`') {
                index = skipQuoted(sql, index, current);
            } else if (current == '-' && hasNext(sql, index, '-')) {
                index = skipLineComment(sql, index);
            } else if (current == '/' && hasNext(sql, index, '*')) {
                index = skipBlockComment(sql, index);
            } else if (current == target) {
                return index;
            }
        }
        return -1;
    }

    private int findMatchingParenthesis(String sql, int openParenthesis) {
        int depth = 1;
        for (int index = openParenthesis + 1; index < sql.length(); index++) {
            char current = sql.charAt(index);
            if (current == '\'' || current == '"' || current == '`') {
                index = skipQuoted(sql, index, current);
            } else if (current == '-' && hasNext(sql, index, '-')) {
                index = skipLineComment(sql, index);
            } else if (current == '/' && hasNext(sql, index, '*')) {
                index = skipBlockComment(sql, index);
            } else if (current == '(') {
                depth++;
            } else if (current == ')') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    private int skipQuoted(String sql, int start, char quote) {
        for (int index = start + 1; index < sql.length(); index++) {
            char current = sql.charAt(index);
            if (quote != '`' && current == '\\' && index + 1 < sql.length()) {
                index++;
            } else if (current == quote) {
                if (index + 1 < sql.length() && sql.charAt(index + 1) == quote) {
                    index++;
                } else {
                    return index;
                }
            }
        }
        return sql.length() - 1;
    }

    private int skipLineComment(String sql, int start) {
        int end = sql.indexOf('\n', start + 2);
        return end < 0 ? sql.length() - 1 : end - 1;
    }

    private int skipBlockComment(String sql, int start) {
        int end = sql.indexOf("*/", start + 2);
        return end < 0 ? sql.length() - 1 : end + 1;
    }

    private String matchingClause(String sql, int index, List<String> clauses) {
        for (String clause : clauses) {
            int end = index + clause.length();
            if (end > sql.length() || !sql.regionMatches(true, index, clause, 0, clause.length())) {
                continue;
            }
            if (index > 0 && isWordCharacter(sql.charAt(index - 1))) {
                continue;
            }
            if (end < sql.length() && isWordCharacter(sql.charAt(end))) {
                continue;
            }
            return clause;
        }
        return null;
    }

    private boolean startsWithKeyword(String sql, String keyword) {
        return sql.regionMatches(true, 0, keyword, 0, keyword.length())
                && (sql.length() == keyword.length()
                        || Character.isWhitespace(sql.charAt(keyword.length())));
    }

    private boolean isWordCharacter(char value) {
        return Character.isLetterOrDigit(value) || value == '_';
    }

    private boolean hasNext(String value, int index, char expected) {
        return index + 1 < value.length() && value.charAt(index + 1) == expected;
    }

    private void addPart(List<String> parts, String part) {
        String value = part.trim();
        if (!value.isEmpty()) {
            parts.add(value);
        }
    }
}
