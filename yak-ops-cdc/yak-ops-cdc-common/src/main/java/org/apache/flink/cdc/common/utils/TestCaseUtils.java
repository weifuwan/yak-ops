package org.apache.flink.cdc.common.utils;

import org.apache.flink.util.function.SupplierWithException;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Some utility methods for creating repeated-checking test cases. */
public class TestCaseUtils {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    public static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(1);

    /** Fetch with a ({@code timeout}, {@code interval}) duration. */
    public static void repeatedCheck(Supplier<Boolean> fetcher) {
        repeatedCheck(fetcher, DEFAULT_TIMEOUT);
    }

    /** Fetch with a ({@code timeout}, {@code interval}) duration. */
    public static void repeatedCheck(Supplier<Boolean> fetcher, Duration timeout) {
        repeatedCheck(fetcher, timeout, DEFAULT_INTERVAL);
    }

    /** Fetch with a ({@code timeout}, {@code interval}) duration. */
    public static void repeatedCheck(
            Supplier<Boolean> fetcher, Duration timeout, Duration interval) {
        repeatedCheck(fetcher::get, timeout, interval, Collections.emptyList());
    }

    /** Fetch and wait with a ({@code timeout}, {@code interval}) duration. */
    public static <T> void repeatedCheck(
            Supplier<T> fetcher, Predicate<T> validator, Duration timeout, Duration interval) {
        repeatedCheckAndValidate(
                fetcher::get, validator, timeout, interval, Collections.emptyList());
    }

    /** Waiting for fetching values with a ({@code timeout}, {@code interval}) duration. */
    public static void repeatedCheck(
            SupplierWithException<Boolean, Throwable> fetcher,
            Duration timeout,
            Duration interval,
            List<Class<? extends Throwable>> allowedThrowsList) {
        repeatedCheckAndValidate(fetcher, b -> b, timeout, interval, allowedThrowsList);
    }

    /** Fetch and validate, with a ({@code timeout}, {@code interval}) duration. */
    public static <T> void repeatedCheckAndValidate(
            SupplierWithException<T, Throwable> fetcher,
            Predicate<T> validator,
            Duration timeout,
            Duration interval,
            List<Class<? extends Throwable>> allowedThrowsList) {

        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeout.toMillis()) {
            try {
                if (validator.test(fetcher.get())) {
                    return;
                }
            } catch (Throwable t) {
                if (allowedThrowsList.stream()
                        .noneMatch(clazz -> clazz.isAssignableFrom(t.getClass()))) {
                    throw new RuntimeException("Fetcher has thrown an unexpected exception: ", t);
                }
            }
            try {
                Thread.sleep(interval.toMillis());
            } catch (InterruptedException ignored) {
                // ignored
            }
        }
        throw new RuntimeException("Timeout when waiting for state to be ready.");
    }
}
