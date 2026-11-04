package org.apache.flink.cdc.common.pipeline;

import org.apache.flink.cdc.common.annotation.PublicEvolving;

/** Route mode for routing rules. */
@PublicEvolving
public enum RouteMode {
    /** Match all applicable routing rules. */
    ALL_MATCH,
    /** Match only the first applicable routing rule. */
    FIRST_MATCH;
}
