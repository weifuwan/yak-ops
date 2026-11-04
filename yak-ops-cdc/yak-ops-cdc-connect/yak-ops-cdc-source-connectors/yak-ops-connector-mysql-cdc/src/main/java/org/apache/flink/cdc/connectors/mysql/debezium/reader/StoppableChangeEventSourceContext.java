package org.apache.flink.cdc.connectors.mysql.debezium.reader;

import io.debezium.pipeline.source.spi.ChangeEventSource;

/**
 * A change event source context that can stop the running source by invoking {@link
 * #stopChangeEventSource()}.
 */
public class StoppableChangeEventSourceContext
        implements ChangeEventSource.ChangeEventSourceContext {

    private volatile boolean isRunning = true;

    public void startChangeEventSource() {
        isRunning = true;
    }

    public void stopChangeEventSource() {
        isRunning = false;
    }

    @Override
    public boolean isRunning() {
        return isRunning;
    }
}
