package io.yak.ops.connector.base.source.reader;

import io.yak.ops.connector.base.source.reader.fetcher.SingleThreadFetcherManager;
import io.yak.ops.connector.base.source.reader.splitreader.SplitReader;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.api.connector.source.SourceSplit;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Reader foundation for a single blocking SplitReader per parallel Source subtask.
 *
 * <p>Unlike SourceReaderBase, this constructor chooses the single-fetcher ownership and queue
 * policy so concrete JDBC/CDC readers do not create or share worker threads themselves.
 */
public abstract class SingleThreadMultiplexSourceReaderBase<E, T, SplitT extends SourceSplit, StateT>
        extends SourceReaderBase<E, T, SplitT, StateT> {

    protected SingleThreadMultiplexSourceReaderBase(
            Supplier<? extends SplitReader<E, SplitT>> splitReaderFactory,
            RecordEmitter<E, T, StateT> emitter,
            SourceReaderContext context) {
        super(
                new SingleThreadFetcherManager<>(
                        Objects.requireNonNull(splitReaderFactory, "splitReaderFactory"),
                        Objects.requireNonNull(context, "context").getConfiguration()),
                emitter,
                context);
    }
}
