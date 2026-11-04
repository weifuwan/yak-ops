package io.yak.ops.common.utils;

public class MaskUtils {

    private static final String MASK_STRING = "****";

    public static String maskString(String str) {
        return maskString(str, 0);
    }

    public static String maskString(String str, int maskStartIndex) {
        if (str == null) {
            return null;
        }
        if (maskStartIndex == 0) {
            return MASK_STRING;
        }
        if (maskStartIndex >= str.length()) {
            return str;
        }
        return str.substring(0, maskStartIndex) + MASK_STRING;
    }

}
