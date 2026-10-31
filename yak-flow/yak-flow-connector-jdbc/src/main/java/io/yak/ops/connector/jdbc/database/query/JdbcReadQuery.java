package io.yak.ops.connector.jdbc.database.query;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.AnalyticExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.JdbcNamedParameter;
import net.sf.jsqlparser.expression.JdbcParameter;
import net.sf.jsqlparser.expression.NextValExpression;
import net.sf.jsqlparser.expression.NumericBind;
import net.sf.jsqlparser.expression.UserVariable;
import net.sf.jsqlparser.expression.VariableAssignment;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.Select;

/** Shared read-only predicate validation for catalog previews, batch reads and CDC snapshots. */
public final class JdbcReadQuery {
    private static final int MAX_PREDICATE_LENGTH = 16_384;
    private static final Set<String> FUNCTIONS = Set.of(
            "ABS",
            "CEIL",
            "CEILING",
            "FLOOR",
            "ROUND",
            "MOD",
            "POWER",
            "SQRT",
            "LOWER",
            "UPPER",
            "LENGTH",
            "CHAR_LENGTH",
            "CHARACTER_LENGTH",
            "TRIM",
            "LTRIM",
            "RTRIM",
            "SUBSTRING",
            "SUBSTR",
            "REPLACE",
            "CONCAT",
            "CONCAT_WS",
            "LEFT",
            "RIGHT",
            "COALESCE",
            "NULLIF",
            "IFNULL",
            "NVL",
            "DATE",
            "YEAR",
            "MONTH",
            "DAY",
            "DAYOFMONTH",
            "HOUR",
            "MINUTE",
            "SECOND",
            "DATE_FORMAT",
            "STR_TO_DATE",
            "TO_DATE",
            "TO_TIMESTAMP");

    private JdbcReadQuery() {}

    /**
     * Validates one complete predicate without executing SQL or accepting a separate query.
     *
     * @param where optional SQL condition without the WHERE keyword
     * @return trimmed input, or null when no condition is configured
     */
    public static String validateWhere(String where) {
        if (where == null || where.isBlank()) return null;
        String value = where.trim();
        parse(value);
        return value;
    }

    /**
     * Combines a validated user predicate with trusted parameterized range/cursor conditions.
     * AST rendering removes comments before composing parentheses; the input text is not rewritten in storage.
     *
     * @param where optional user predicate
     * @param requiredPredicates connector-owned conditions with JDBC placeholders
     * @return an empty string or a complete WHERE clause, including its leading space
     */
    public static String whereClause(String where, List<String> requiredPredicates) {
        List<String> conditions = new ArrayList<>();
        if (where != null && !where.isBlank()) conditions.add("(" + parse(where.trim()) + ")");
        for (String predicate : List.copyOf(requiredPredicates)) {
            if (predicate.isBlank()) throw new IllegalArgumentException("JDBC range predicate must not be blank");
            conditions.add("(" + predicate + ")");
        }
        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }

    /** Returns source columns whose physical types must remain stable across checkpoint recovery. */
    public static List<String> referencedColumns(String where) {
        if (where == null || where.isBlank()) return List.of();
        PredicateVisitor visitor = new PredicateVisitor();
        parse(where.trim()).accept(visitor);
        return List.copyOf(visitor.columns);
    }

    private static Expression parse(String value) {
        if (value.length() > MAX_PREDICATE_LENGTH) {
            throw new IllegalArgumentException("JDBC read predicate exceeds the supported length");
        }
        try {
            Expression expression = CCJSqlParserUtil.parseCondExpression(value, false);
            if (expression == null) throw new IllegalArgumentException("JDBC read predicate is empty");
            expression.accept(new PredicateVisitor());
            return expression;
        } catch (JSQLParserException failure) {
            throw new IllegalArgumentException("JDBC read predicate must be one complete SQL condition", failure);
        }
    }

    private static final class PredicateVisitor extends ExpressionVisitorAdapter {
        private final Set<String> columns = new LinkedHashSet<>();

        @Override
        public void visit(net.sf.jsqlparser.schema.Column column) {
            String name = column.getColumnName();
            if (name.startsWith("`") && name.endsWith("`")) {
                name = name.substring(1, name.length() - 1).replace("``", "`");
            } else if (name.startsWith("\"") && name.endsWith("\"")) {
                name = name.substring(1, name.length() - 1).replace("\"\"", "\"");
            }
            columns.add(name);
        }

        @Override
        public void visit(Select select) {
            reject();
        }

        @Override
        public void visit(ParenthesedSelect select) {
            reject();
        }

        @Override
        public void visit(JdbcParameter parameter) {
            reject();
        }

        @Override
        public void visit(JdbcNamedParameter parameter) {
            reject();
        }

        @Override
        public void visit(NumericBind parameter) {
            reject();
        }

        @Override
        public void visit(UserVariable variable) {
            reject();
        }

        @Override
        public void visit(VariableAssignment assignment) {
            reject();
        }

        @Override
        public void visit(NextValExpression expression) {
            reject();
        }

        @Override
        public void visit(AnalyticExpression expression) {
            reject();
        }

        @Override
        public void visit(Function function) {
            if (!FUNCTIONS.contains(function.getName().toUpperCase(Locale.ROOT))) reject();
            super.visit(function);
        }

        private static void reject() {
            throw new IllegalArgumentException(
                    "JDBC read predicates cannot contain subqueries, parameters, stateful functions or variables");
        }
    }
}
