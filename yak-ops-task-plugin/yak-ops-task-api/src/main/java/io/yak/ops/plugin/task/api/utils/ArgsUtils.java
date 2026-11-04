package io.yak.ops.plugin.task.api.utils;

import static io.yak.ops.plugin.task.api.TaskConstants.SINGLE_QUOTES;

public class ArgsUtils {

    private ArgsUtils() throws IllegalStateException {
        throw new IllegalStateException("Utility class");
    }

    public static String escape(String arg) {
        return arg.replace(" ", "\\ ").replace("\"", "\\\"").replace("'", "\\'");
    }

    public static String wrapperSingleQuotes(String value) {
        return SINGLE_QUOTES + value + SINGLE_QUOTES;
    }

}
