package io.yak.ops.spi.params.base;

public enum ResizeType {

    NONE("none"),
    BOTH("both"),
    HORIZONTAL("horizontal"),
    VERTICAL("vertical");

    private String value;

    ResizeType(String value) {
        this.value = value;
    }
}
