package io.yak.ops.connector.base.source.reader;

import io.yak.ops.core.configuration.ConfigOption;
import io.yak.ops.core.configuration.ConfigOptions;

/** Bounded handover and shutdown settings shared by asynchronous source readers. */
public final class SourceReaderOptions {

    public static final ConfigOption<Integer> ELEMENT_QUEUE_CAPACITY =
            ConfigOptions.key("source.reader.element.queue.capacity").intType().defaultValue(2);

    public static final ConfigOption<Long> CLOSE_TIMEOUT_MILLIS =
            ConfigOptions.key("source.reader.close.timeout").longType().defaultValue(30_000L);

    private SourceReaderOptions() {}
}
