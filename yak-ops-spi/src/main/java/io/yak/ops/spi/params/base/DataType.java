package io.yak.ops.spi.params.base;

import lombok.Getter;

/**
 * param datetype
 */
@Getter
public enum DataType {

    STRING("string"),

    NUMBER("number");

    private String dataType;

    DataType(String dataType) {
        this.dataType = dataType;
    }

}
