package org.apache.flink.cdc.source;

import org.apache.flink.api.common.functions.RuntimeContext;

/**
 * Compatibility adapter for Flink 1.18. This class is part of the multi-version compatibility layer
 * that allows Flink CDC to work across different Flink versions.
 */
public class RuntimeContextAdapter {

    public static int getIndexOfThisSubtask(RuntimeContext runtimeContext) {
        return runtimeContext.getIndexOfThisSubtask();
    }
}
