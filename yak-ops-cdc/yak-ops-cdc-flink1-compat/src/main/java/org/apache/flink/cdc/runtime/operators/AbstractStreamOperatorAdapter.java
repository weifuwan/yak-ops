package org.apache.flink.cdc.runtime.operators;

import org.apache.flink.annotation.Internal;
import org.apache.flink.streaming.api.operators.AbstractStreamOperator;

/**
 * Compatibility adapter for Flink 1.20. This class is part of the multi-version compatibility layer
 * that allows Flink CDC to work across different Flink versions.
 */
@Internal
public class AbstractStreamOperatorAdapter<OUT> extends AbstractStreamOperator<OUT> {}
