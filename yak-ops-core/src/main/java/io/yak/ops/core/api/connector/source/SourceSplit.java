package io.yak.ops.core.api.connector.source;

/**
* Independently assignable work unit of a Source.
*
* <p>The Connector defines the range, partition, cursor and other source-specific fields.
* Split IDs must be stable and unique within a Source. Restorable split state must also
* contain sufficient progress to continue reading after a checkpoint.
*
* @author weifuwan
*/
public interface SourceSplit {

    /** Returns a stable, unique and nonblank split ID within the owning Source. */
    String splitId();
}
