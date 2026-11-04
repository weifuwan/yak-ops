package io.yak.ops.spi.params.base;

import com.fasterxml.jackson.annotation.JsonValue;

public enum FormType {

    INPUT("input"),
    INPUTNUMBER("input-number"),
    RADIO("radio"),
    SELECT("select");

    private String formType;

    FormType(String formType) {
        this.formType = formType;
    }

    @JsonValue
    public String getFormType() {
        return this.formType;
    }
}
