package io.yak.ops.connector.base.factory;

import io.yak.ops.configuration.rule.OptionRule;

/** SPI contract declaring an independent Connector and its configuration requirements. */
public interface Factory {

    /** Persistent connector identifier used to select this provider. */
    String factoryIdentifier();

    /** Declares options accepted by this Connector. */
    OptionRule optionRule();

    /**
     * Declares the complete inputs required to construct this Connector.
     *
     * <p>Includes operation options and resolved connection or system-supplied settings.
     * Callers validate operation options before resolving sensitive connection settings.
     *
     * @return complete creation rules, reusing the operation rules
     */
    default OptionRule creationOptionRule() {
        return optionRule();
    }
}
