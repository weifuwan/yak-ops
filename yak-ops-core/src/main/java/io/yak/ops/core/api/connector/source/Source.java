package io.yak.ops.core.api.connector.source;

import io.yak.ops.core.api.io.SimpleVersionedSerializer;

/**
* Reusable factory for a source's split enumerator, readers and state serializers.
*
* <p>A Source is a component definition, not an active connection or task instance.
* {@link Boundedness} describes its data stream independently of the execution mode.
*
* @param <T> the record type produced by this source
* @param <SplitT> the split type assigned to readers
* @param <EnumStateT> the split enumerator's checkpoint state type
* @author weifuwan
*/
public interface Source<T, SplitT extends SourceSplit, EnumStateT> {

    /** Returns whether the source produces a finite or unbounded stream. */
    Boundedness getBoundedness();

    /**
    * Creates an enumerator for a fresh job execution.
    *
    * @param context the runtime-managed coordinator context
    * @return a new enumerator that has not yet been started
    * @throws Exception if enumerator creation fails
    */
    SplitEnumerator<SplitT, EnumStateT> createEnumerator(SplitEnumeratorContext<SplitT> context) throws Exception;

    /**
    * Restores an enumerator from a completed checkpoint.
    *
    * <p>The runtime calls {@link SplitEnumerator#start()} after creation, not during this call.
    *
    * @param context the coordinator context for the new execution attempt
    * @param checkpointState the deserialized enumerator state
    * @return an enumerator restored from the supplied state
    * @throws Exception if restoration fails
    */
    SplitEnumerator<SplitT, EnumStateT> restoreEnumerator(
            SplitEnumeratorContext<SplitT> context, EnumStateT checkpointState) throws Exception;

    /**
    * Creates an independent reader for one parallel source subtask.
    *
    * <p>The runtime delivers restored splits, including their progress, through
    * {@link SourceReader#addSplits(java.util.List)}.
    *
    * @param context the subtask's runtime context
    * @return a new reader owned by this subtask
    * @throws Exception if reader creation fails
    */
    SourceReader<T, SplitT> createReader(SourceReaderContext context) throws Exception;

    /** Returns the versioned serializer used to transport and checkpoint source splits. */
    SimpleVersionedSerializer<SplitT> getSplitSerializer();

    /** Returns the versioned serializer for enumerator checkpoint state. */
    SimpleVersionedSerializer<EnumStateT> getEnumeratorCheckpointSerializer();
}
