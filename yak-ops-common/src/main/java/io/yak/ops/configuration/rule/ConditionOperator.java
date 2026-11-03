package io.yak.ops.configuration.rule;

/** Supported declarative value-constraint operators. */
public enum ConditionOperator {
    EQUAL,
    NOT_EQUAL,
    GREATER_THAN,
    GREATER_OR_EQUAL,
    LESS_THAN,
    LESS_OR_EQUAL,
    NOT_BLANK,
    STARTS_WITH,
    CONTAINS,
    MATCHES,
    NOT_EMPTY,
    UNIQUE,
    MAP_NOT_EMPTY,
    MAP_CONTAINS_KEY,
    MAP_CONTAINS_KEYS,
    EXTENSION,
    AND,
    OR
}
