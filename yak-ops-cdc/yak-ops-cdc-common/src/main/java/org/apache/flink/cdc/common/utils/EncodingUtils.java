package org.apache.flink.cdc.common.utils;

/** General utilities for string-encoding. */
public abstract class EncodingUtils {

    private EncodingUtils() {
        // do not instantiate
    }

    public static String escapeBackticks(String s) {
        return s.replace("`", "``");
    }

    public static String escapeSingleQuotes(String s) {
        return s.replace("'", "''");
    }

    public static String escapeIdentifier(String s) {
        return "`" + escapeBackticks(s) + "`";
    }
}
