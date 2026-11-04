package io.yak.ops.dao.utils;

public class EnvironmentUtils {

    private static final long EMPTY_ENVIRONMENT_CODE = -1L;

    /**
     * Check if the environment code is empty (we should use null instead of -1, this is used to comply with the original code)
     *
     * @return true if the environment code is empty, false otherwise
     */
    public static boolean isEnvironmentCodeEmpty(Long environmentCode) {
        return environmentCode == null || environmentCode <= 0;
    }

    /**
     * Get the empty environment code
     */
    public static Long getDefaultEnvironmentCode() {
        return EMPTY_ENVIRONMENT_CODE;
    }

    /**
     * Get the environment code or the default environment code if the environment code is empty
     */
    public static Long getEnvironmentCodeOrDefault(Long environmentCode) {
        return getEnvironmentCodeOrDefault(environmentCode, getDefaultEnvironmentCode());
    }

    /**
     * Get the environment code or the default environment code if the environment code is empty
     */
    public static Long getEnvironmentCodeOrDefault(Long environmentCode, Long defaultEnvironmentCode) {
        return isEnvironmentCodeEmpty(environmentCode) ? defaultEnvironmentCode : environmentCode;
    }

}
