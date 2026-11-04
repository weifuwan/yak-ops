package org.apache.flink.cdc.common.event;

import org.apache.flink.cdc.common.annotation.PublicEvolving;

/**
 * Class {@code Event} is the super interface defines the events of external systems flowing into
 * Flink CDC.
 */
@PublicEvolving
public interface Event {}
