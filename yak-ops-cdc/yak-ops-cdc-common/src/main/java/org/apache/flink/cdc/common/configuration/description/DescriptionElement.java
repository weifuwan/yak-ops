package org.apache.flink.cdc.common.configuration.description;

import org.apache.flink.cdc.common.annotation.PublicEvolving;

/** Part of a {@link Description} that can be converted into String representation. */
@PublicEvolving
interface DescriptionElement {
    /**
     * Transforms itself into String representation using given format.
     *
     * @param formatter formatter to use.
     */
    void format(Formatter formatter);
}
