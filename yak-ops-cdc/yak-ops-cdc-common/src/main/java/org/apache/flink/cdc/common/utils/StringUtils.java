package org.apache.flink.cdc.common.utils;

/** Utility class to convert objects into strings in vice-versa. */
public class StringUtils {
    /**
     * Checks if the string is null, empty, or contains only whitespace characters. A whitespace
     * character is defined via {@link Character#isWhitespace(char)}.
     *
     * @param str The string to check
     * @return True, if the string is null or blank, false otherwise.
     */
    public static boolean isNullOrWhitespaceOnly(String str) {
        if (str == null || str.isEmpty()) {
            return true;
        }

        final int len = str.length();
        for (int i = 0; i < len; i++) {
            if (!Character.isWhitespace(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Convert underline and space case to camel case.
     *
     * @param str The string to convert
     * @return String which is camel case.
     */
    public static String convertToCamelCase(String str) {
        StringBuilder result = new StringBuilder();
        if (isNullOrWhitespaceOnly(str)) {
            return "";
        } else if (!str.contains("_") && !str.contains(" ")) {
            return str.toLowerCase();
        }
        String[] camels = str.split("[_| ]");
        for (String camel : camels) {
            if (camel.isEmpty()) {
                continue;
            }
            result.append(camel.substring(0, 1).toUpperCase());
            result.append(camel.substring(1).toLowerCase());
        }
        StringBuilder ret = new StringBuilder(result.substring(0, 1).toLowerCase());
        ret.append(result.substring(1, result.toString().length()));
        return ret.toString();
    }
}
