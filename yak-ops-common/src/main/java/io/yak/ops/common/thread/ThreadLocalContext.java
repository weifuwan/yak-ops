package io.yak.ops.common.thread;

/**
 * A utility class to manage timezone context using ThreadLocal.
 * This allows each thread to have its own timezone value, which is useful in
 * multi-threaded environments such as web applications where each request
 * may need to operate in a different timezone.
 *
 * Note: Always call {@link #removeTimezone()} at the end of a request or task
 * to prevent memory leaks and context pollution in thread pool environments.
 */
public class ThreadLocalContext {

    /**
     * ThreadLocal variable to hold the timezone string for the current thread.
     * Each thread will have its own copy of the timezone value.
     */
    private static final ThreadLocal<String> TIMEZONE_THREAD_LOCAL = new ThreadLocal<>();

    /**
     * Sets the timezone for the current thread.
     *
     * @param timezone the timezone ID (e.g., "UTC", "Asia/Shanghai", "America/New_York")
     */
    public static void setTimezone(String timezone) {
        TIMEZONE_THREAD_LOCAL.set(timezone);
    }

    /**
     * Retrieves the timezone for the current thread.
     *
     * @return the timezone string set for the current thread, or null if not set
     */
    public static String getTimezone() {
        return TIMEZONE_THREAD_LOCAL.get();
    }

    /**
     * Removes the timezone value for the current thread.
     * This method should be called to clean up the thread-local value, especially
     * when using thread pools (e.g., in web servers), to prevent memory leaks
     * and unintended data leakage between requests.
     */
    public static void removeTimezone() {
        TIMEZONE_THREAD_LOCAL.remove();
    }
}
