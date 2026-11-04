package org.apache.flink.cdc.composer.flink.coordination;

import org.apache.flink.cdc.common.annotation.Internal;
import org.apache.flink.runtime.jobgraph.OperatorID;

import org.apache.flink.shaded.guava31.com.google.common.hash.Hashing;

import static java.nio.charset.StandardCharsets.UTF_8;

/** Generating {@link OperatorID} for communication between Flink operators. */
@Internal
public class OperatorIDGenerator {
    private final String transformationUid;

    public OperatorIDGenerator(String transformationUid) {
        this.transformationUid = transformationUid;
    }

    /**
     * Generate {@link OperatorID}.
     *
     * <p>Operator ID generation is an internal implementation inside Flink, happening during the
     * stream graph generating phase, so our algorithm of generating operator ID should be exactly
     * the same as in Flink, in order to make sure that operators can reach out each other on the
     * cluster.
     *
     * @see
     *     org.apache.flink.streaming.api.graph.StreamGraphHasherV2#traverseStreamGraphAndGenerateHashes
     *     the algorithm of generating operator ID in Flink
     */
    public OperatorID generate() {
        byte[] hash =
                Hashing.murmur3_128(0)
                        .newHasher()
                        .putString(transformationUid, UTF_8)
                        .hash()
                        .asBytes();
        return new OperatorID(hash);
    }
}
