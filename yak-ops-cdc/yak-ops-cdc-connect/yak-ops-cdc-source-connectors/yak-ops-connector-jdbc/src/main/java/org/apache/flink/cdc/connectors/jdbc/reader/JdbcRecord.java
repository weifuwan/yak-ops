package org.apache.flink.cdc.connectors.jdbc.reader;

import org.apache.flink.cdc.common.event.Event;

import java.math.BigDecimal;

/** A fetched event together with the numeric key needed to checkpoint its progress. */
final class JdbcRecord {

    private final Event event;
    private final BigDecimal lastReadValue;

    JdbcRecord(Event event, BigDecimal lastReadValue) {
        this.event = event;
        this.lastReadValue = lastReadValue;
    }

    Event getEvent() {
        return event;
    }

    BigDecimal getLastReadValue() {
        return lastReadValue;
    }
}
