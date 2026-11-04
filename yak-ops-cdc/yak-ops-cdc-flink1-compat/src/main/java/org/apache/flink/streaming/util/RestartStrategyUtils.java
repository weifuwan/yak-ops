package org.apache.flink.streaming.util;

import org.apache.flink.annotation.Internal;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.RestartStrategyOptions;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.time.Duration;

/**
 * Compatibility adapter for Flink 1.20. This class is part of the multi-version compatibility layer
 * that allows Flink CDC to work across different Flink versions.
 */
@Internal
public class RestartStrategyUtils {

    /**
     * Disables the restart strategy for the given StreamExecutionEnvironment.
     *
     * @param env the StreamExecutionEnvironment to configure
     */
    public static void configureNoRestartStrategy(StreamExecutionEnvironment env) {
        env.configure(new Configuration().set(RestartStrategyOptions.RESTART_STRATEGY, "none"));
    }

    /**
     * Sets a fixed-delay restart strategy for the given StreamExecutionEnvironment.
     *
     * @param env the StreamExecutionEnvironment to configure
     * @param restartAttempts the number of restart attempts
     * @param delayBetweenAttempts the delay between restart attempts in milliseconds
     */
    public static void configureFixedDelayRestartStrategy(
            StreamExecutionEnvironment env, int restartAttempts, long delayBetweenAttempts) {
        configureFixedDelayRestartStrategy(
                env, restartAttempts, Duration.ofMillis(delayBetweenAttempts));
    }

    /**
     * Sets a fixed-delay restart strategy for the given StreamExecutionEnvironment.
     *
     * @param env the StreamExecutionEnvironment to configure
     * @param restartAttempts the number of restart attempts
     * @param delayBetweenAttempts the delay between restart attempts
     */
    public static void configureFixedDelayRestartStrategy(
            StreamExecutionEnvironment env, int restartAttempts, Duration delayBetweenAttempts) {
        Configuration configuration = new Configuration();
        configuration.set(RestartStrategyOptions.RESTART_STRATEGY, "fixed-delay");
        configuration.set(
                RestartStrategyOptions.RESTART_STRATEGY_FIXED_DELAY_ATTEMPTS, restartAttempts);
        configuration.set(
                RestartStrategyOptions.RESTART_STRATEGY_FIXED_DELAY_DELAY, delayBetweenAttempts);

        env.configure(configuration);
    }
}
