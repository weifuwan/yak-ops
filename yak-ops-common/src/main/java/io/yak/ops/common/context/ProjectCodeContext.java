package io.yak.ops.common.context;

/**
 * Request-scoped project code used by modeling's MyBatis tenant interceptor.
 *
 * <p>Always clear this context in a request completion callback.
 */
public final class ProjectCodeContext {

    private static final ThreadLocal<Long> PROJECT_CODE = new ThreadLocal<>();

    private ProjectCodeContext() {
    }

    public static void setProjectCode(long projectCode) {
        PROJECT_CODE.set(projectCode);
    }

    public static Long getProjectCode() {
        return PROJECT_CODE.get();
    }

    public static boolean isActive() {
        return PROJECT_CODE.get() != null;
    }

    public static void clear() {
        PROJECT_CODE.remove();
    }
}
