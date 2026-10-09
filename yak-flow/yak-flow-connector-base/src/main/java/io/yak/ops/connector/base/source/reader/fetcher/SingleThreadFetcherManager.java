package io.yak.ops.connector.base.source.reader.fetcher;

import io.yak.ops.connector.base.source.reader.splitreader.SplitReader;
import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.core.configuration.Configuration;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Multiplexes all splits assigned to one SourceReader over one dedicated blocking fetcher.
 *
 * <p>Parallel Source subtasks each create their own instance and independently own the
 * connector's I/O resources.
 */
public final class SingleThreadFetcherManager<E, SplitT extends SourceSplit>
        extends SplitFetcherManager<E, SplitT> {

    public SingleThreadFetcherManager(
            Supplier<? extends SplitReader<E, SplitT>> splitReaderFactory, Configuration configuration) {
        super(splitReaderFactory, configuration);
    }

    @Override
    public synchronized void addSplits(List<SplitT> splits) {
        List<SplitT> added = List.copyOf(Objects.requireNonNull(splits, "splits"));
        if (added.isEmpty()) {
            return;
        }
        SplitFetcher<E, SplitT> fetcher = getAnyFetcher();
        if (fetcher == null) {
            fetcher = createFetcher();
            fetcher.addSplits(added);
            startFetcher(fetcher);
        } else {
            fetcher.addSplits(added);
        }
    }
}
