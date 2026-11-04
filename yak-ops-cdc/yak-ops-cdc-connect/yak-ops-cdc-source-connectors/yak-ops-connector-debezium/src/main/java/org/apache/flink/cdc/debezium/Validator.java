package org.apache.flink.cdc.debezium;

import java.io.Serializable;

/** Validator to validate the connected database satisfies the cdc connector's requirements. */
public interface Validator extends Serializable {

    void validate();

    static Validator getDefaultValidator() {
        return () -> {
            // do nothing;
        };
    }
}
