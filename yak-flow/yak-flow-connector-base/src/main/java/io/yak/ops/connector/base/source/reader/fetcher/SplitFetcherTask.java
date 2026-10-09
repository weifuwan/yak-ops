package io.yak.ops.connector.base.source.reader.fetcher;

/** A fetcher-thread action serialized with blocking reads and split assignment. */
interface SplitFetcherTask {

    /** @return true if completed, false if interrupted for a control action */
    boolean run() throws Exception;

    void wakeUp();
}
