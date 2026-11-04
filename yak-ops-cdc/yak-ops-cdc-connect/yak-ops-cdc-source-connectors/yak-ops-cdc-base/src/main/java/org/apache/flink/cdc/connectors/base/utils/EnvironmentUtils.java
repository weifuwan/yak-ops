package org.apache.flink.cdc.connectors.base.utils;

import org.apache.flink.runtime.util.EnvironmentInformation;

/** Utilities for environment information at runtime. */
public class EnvironmentUtils {

    private EnvironmentUtils() {}

    private static final VersionComparable FLINK_1_14 = VersionComparable.fromVersionString("1.14");

    public static VersionComparable runtimeFlinkVersion() {
        return VersionComparable.fromVersionString(EnvironmentInformation.getVersion());
    }

    public static boolean supportCheckpointsAfterTasksFinished() {
        return runtimeFlinkVersion().newerThanOrEqualTo(FLINK_1_14);
    }

    public static void checkSupportCheckpointsAfterTasksFinished(boolean closeIdleReaders) {
        if (closeIdleReaders && !supportCheckpointsAfterTasksFinished()) {
            throw new UnsupportedOperationException(
                    "The flink version is required to be greater than or equal to 1.14 when 'execution.checkpointing.checkpoints-after-tasks-finish.enabled' is set to true. But the current version is "
                            + runtimeFlinkVersion());
        }
    }
}
