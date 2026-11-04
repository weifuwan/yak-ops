package org.apache.flink.cdc.common.source.discover;

import org.apache.flink.cdc.common.annotation.PublicEvolving;
import org.apache.flink.cdc.common.configuration.Configuration;

import java.io.Serializable;
import java.util.Set;

/** Pluggable abstraction for discovering a set of object identifiers. */
@PublicEvolving
public interface ObjectIdDiscoverer<T> extends Serializable, AutoCloseable {

    /** Opens this discoverer and initializes any resources needed for discovery. */
    void open(Context context) throws Exception;

    /**
     * Discovers and returns the complete current set of object identifiers selected by the caller
     * configuration.
     *
     * <p>A successful result is authoritative: identifiers absent from it are no longer selected,
     * and an empty result selects no identifiers. A failed discovery does not describe a new set.
     */
    Set<T> discover() throws Exception;

    /** Closes this discoverer and releases any resources. */
    @Override
    void close() throws Exception;

    /** Context providing runtime information for the discoverer. */
    interface Context {

        /** Returns the full connector configuration. */
        Configuration getConfiguration();

        /** Returns the user code class loader of the current session. */
        ClassLoader getUserCodeClassLoader();
    }
}
