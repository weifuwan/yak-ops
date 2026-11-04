package org.apache.flink.cdc.connectors.mysql.source.offset;

/**
 * Predefined kind of binlog offset.
 *
 * <p>Binlog offset kind describes some special binlog offsets such as earliest accessible binlog,
 * the latest binlog offset currently, and a specific offset described by binlog file + position or
 * GTID set.
 */
public enum BinlogOffsetKind {

    /** The earliest accessible offset in the binlog. */
    EARLIEST,

    /** The latest offset in the binlog. */
    LATEST,

    /**
     * The binlog offset is described by a timestamp, for example "1667232000" means start reading
     * from events happens at 2022-11-01 00:00:00.
     */
    TIMESTAMP,

    /**
     * A specific offset described by binlog file name and position, or GTID set if GTID is enabled
     * on the cluster. For example:
     *
     * <ul>
     *   <li>Binlog file "mysql-bin.000002" and position 4
     *   <li>GTID set "24DA167-0C0C-11E8-8442-00059A3C7B00:1-19"
     * </ul>
     */
    SPECIFIC,

    /**
     * A special offset indicating there's no ending offsets for the binlog reader.
     *
     * <p>Please note that this is an INTERNAL kind and should not be used for specifying starting
     * offset.
     */
    NON_STOPPING
}
