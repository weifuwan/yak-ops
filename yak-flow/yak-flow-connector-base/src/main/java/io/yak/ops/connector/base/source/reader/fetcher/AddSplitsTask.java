package io.yak.ops.connector.base.source.reader.fetcher;

import io.yak.ops.connector.base.source.reader.splitreader.SplitReader;
import io.yak.ops.core.api.connector.source.SourceSplit;
import java.util.List;
import java.util.Objects;

/** Delivers newly assigned work to a SplitReader on its owning fetcher thread. */
final class AddSplitsTask<E, SplitT extends SourceSplit> implements SplitFetcherTask {

    private final SplitReader<E, SplitT> splitReader;
    private final List<SplitT> splits;

    AddSplitsTask(SplitReader<E, SplitT> splitReader, List<SplitT> splits) {
        this.splitReader = Objects.requireNonNull(splitReader, "splitReader");
        this.splits = List.copyOf(splits);
    }

    @Override
    public boolean run() throws Exception {
        splitReader.addSplits(splits);
        return true;
    }

    @Override
    public void wakeUp() {}
}
