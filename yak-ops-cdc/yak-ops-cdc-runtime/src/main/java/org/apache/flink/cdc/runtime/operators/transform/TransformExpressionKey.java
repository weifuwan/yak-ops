package org.apache.flink.cdc.runtime.operators.transform;

import org.apache.flink.cdc.runtime.parser.JaninoCompiler;

import javax.annotation.Nullable;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The key applies to describe the information of the transformation expression.
 *
 * <p>A transform expression key contains:
 *
 * <ul>
 *   <li>originalExpression: a string for the original transformation expression input by users.
 *   <li>expression: a string for the compiled transformation expression.
 *   <li>argumentNames: a list for the argument names in expression.
 *   <li>argumentClasses: a list for the argument classes in expression.
 *   <li>returnClass: a class for the return class in expression
 *   <li>columnNameMap: a map whose key is the original column name and value is the mapped column
 *       name
 * </ul>
 */
public class TransformExpressionKey implements Serializable {
    private static final long serialVersionUID = 1L;
    @Nullable private final String originalExpression;
    private final String compiledExpression;
    private final List<String> argumentNames;
    private final List<Class<?>> argumentClasses;
    private final Class<?> returnClass;
    private final Map<String, String> columnNameMap;

    private TransformExpressionKey(
            @Nullable String originalExpression,
            String compiledExpression,
            List<String> argumentNames,
            List<Class<?>> argumentClasses,
            Class<?> returnClass,
            Map<String, String> columnNameMap) {
        this.originalExpression = originalExpression;
        this.compiledExpression = compiledExpression;
        this.argumentNames = argumentNames;
        this.argumentClasses = argumentClasses;
        this.returnClass = returnClass;
        this.columnNameMap = columnNameMap;
    }

    @Nullable
    public String getOriginalExpression() {
        return originalExpression;
    }

    public String getCompiledExpression() {
        return compiledExpression;
    }

    public String getFullExpression() {
        return JaninoCompiler.loadSystemFunction(compiledExpression);
    }

    public List<String> getArgumentNames() {
        return Collections.unmodifiableList(argumentNames);
    }

    public List<Class<?>> getArgumentClasses() {
        return Collections.unmodifiableList(argumentClasses);
    }

    public Class<?> getReturnClass() {
        return returnClass;
    }

    public Map<String, String> getColumnNameMap() {
        return Collections.unmodifiableMap(columnNameMap);
    }

    public static TransformExpressionKey of(
            @Nullable String originalExpression,
            String compiledExpression,
            List<String> argumentNames,
            List<Class<?>> argumentClasses,
            Class<?> returnClass,
            Map<String, String> columnNameMap) {
        return new TransformExpressionKey(
                originalExpression,
                compiledExpression,
                argumentNames,
                argumentClasses,
                returnClass,
                columnNameMap);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TransformExpressionKey that = (TransformExpressionKey) o;
        return Objects.equals(originalExpression, that.originalExpression)
                && compiledExpression.equals(that.compiledExpression)
                && argumentNames.equals(that.argumentNames)
                && argumentClasses.equals(that.argumentClasses)
                && returnClass.equals(that.returnClass)
                && columnNameMap.equals(that.columnNameMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                originalExpression,
                compiledExpression,
                argumentNames,
                argumentClasses,
                returnClass,
                columnNameMap);
    }

    @Override
    public String toString() {
        return "TransformExpressionKey{"
                + "originalExpression='"
                + originalExpression
                + '\''
                + ", compiledExpression='"
                + compiledExpression
                + '\''
                + ", argumentNames="
                + argumentNames
                + ", argumentClasses="
                + argumentClasses
                + ", returnClass="
                + returnClass
                + ", columnNameMap="
                + columnNameMap
                + '}';
    }
}
