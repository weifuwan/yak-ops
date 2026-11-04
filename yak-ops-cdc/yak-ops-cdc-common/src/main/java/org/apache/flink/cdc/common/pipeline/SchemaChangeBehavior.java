package org.apache.flink.cdc.common.pipeline;

import org.apache.flink.cdc.common.annotation.PublicEvolving;

/**
 * Behavior for handling schema changes. Enums are sorted from the most tolerant strategy (IGNORE)
 * to the most aggressive one (EXCEPTION).
 */
@PublicEvolving
public enum SchemaChangeBehavior {
    IGNORE,
    LENIENT,
    TRY_EVOLVE,
    EVOLVE,
    EXCEPTION
}
