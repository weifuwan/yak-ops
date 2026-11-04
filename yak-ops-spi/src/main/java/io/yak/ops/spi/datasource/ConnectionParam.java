package io.yak.ops.spi.datasource;

import java.io.Serializable;

/**
 * The model of Datasource Connection param
 */
public interface ConnectionParam extends Serializable {

    default String getPassword() {
        return "";
    }

    default void setPassword(String s) {
    }

}
