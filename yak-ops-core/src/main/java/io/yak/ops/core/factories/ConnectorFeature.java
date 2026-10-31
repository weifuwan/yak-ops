package io.yak.ops.core.factories;

/** Connector-owned executable features used by planning and configuration discovery. */
public enum ConnectorFeature {
    /** A database predicate can be applied while reading bounded data. */
    READ_PREDICATE,
    /** A caller can choose a validated, recoverable bounded-read split key. */
    EXPLICIT_SPLIT_KEY,
    /** The source provides a bounded read preview with the same predicate semantics. */
    PREVIEW,
    /** Initial CDC snapshots support a per-table predicate. */
    SNAPSHOT_FILTER,
    /** Individual CDC tables can skip their initial snapshot. */
    SNAPSHOT_SKIP,
    /** Original incremental data events can be selected by operation type. */
    DML_FILTER,
    /** Matching tables can join an active source through a recoverable registration protocol. */
    DYNAMIC_TABLES,
    /** Bounded sinks execute ordered SQL before and after committed data writes. */
    SQL_HOOKS,
    /** The sink implements an explicit replace-write operation. */
    REPLACE_WRITE,
    /** Target schema planning supports validated physical type mappings. */
    TYPE_MAPPING
}
