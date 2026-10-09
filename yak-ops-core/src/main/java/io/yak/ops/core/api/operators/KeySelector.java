package io.yak.ops.core.api.operators;

/**
* Extracts the stable business key used for keyed partitioning.
*
* <p>Keys must be non-null and have stable equality and hash semantics; array and
* identity-based hashes are not supported. CDC streams should use the target primary
* key. Keyed routing does not itself guarantee transactional ordering.
*
* @param <T> the upstream record type
*/
@FunctionalInterface
public interface KeySelector<T> {

    /** Returns a stable, non-null partition key for the supplied record. */
    Object getKey(T record) throws Exception;
}
