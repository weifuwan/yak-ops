package org.apache.flink.cdc.runtime.operators.transform;

import org.apache.flink.cdc.common.utils.StringUtils;
import org.apache.flink.cdc.runtime.operators.transform.exceptions.TransformException;
import org.apache.flink.cdc.runtime.parser.TransformParser;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The TransformFilter applies to describe the information of the filter row.
 *
 * <p>A filter row contains:
 *
 * <ul>
 *   <li>expression: a string for filter expression split from the user-defined filter.
 *   <li>scriptExpression: a string for filter script expression compiled from the column
 *       expression.
 *   <li>columnNames: a list for recording the name of all columns used by the filter expression.
 * </ul>
 */
public class TransformFilter implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String expression;
    private final List<String> columnNames;
    private final Map<String, String> columnNameMap;

    public TransformFilter(
            String expression, List<String> columnNames, Map<String, String> columnNameMap) {
        this.expression = expression;
        this.columnNames = columnNames;
        this.columnNameMap = columnNameMap;
    }

    public String getExpression() {
        return expression;
    }

    public List<String> getColumnNames() {
        return columnNames;
    }

    public Map<String, String> getColumnNameMap() {
        return columnNameMap;
    }

    public String getColumnNameMapAsString() {
        return TransformException.prettyPrintColumnNameMap(getColumnNameMap());
    }

    public static Optional<TransformFilter> of(String filterExpression) {
        if (StringUtils.isNullOrWhitespaceOnly(filterExpression)) {
            return Optional.empty();
        }
        List<String> columnNames = TransformParser.parseFilterColumnNameList(filterExpression);
        Map<String, String> columnNameMap = TransformParser.generateColumnNameMap(columnNames);
        return Optional.of(new TransformFilter(filterExpression, columnNames, columnNameMap));
    }

    public boolean isValid() {
        return !columnNames.isEmpty();
    }

    @Override
    public String toString() {
        return "TransformFilter{"
                + "expression='"
                + expression
                + '\''
                + ", columnNames="
                + columnNames
                + ", columnNameMap="
                + columnNameMap
                + '}';
    }
}
