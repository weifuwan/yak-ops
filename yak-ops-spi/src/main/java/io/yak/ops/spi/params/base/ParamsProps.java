package io.yak.ops.spi.params.base;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * the props field in form-create`s json rule
 */
public class ParamsProps {

    private String placeholder;

    /**
     * input box size, optional value medium/small/mini
     */
    private String size = "small";

    /**
     * whether to disable, the default value is false
     */
    private Boolean disabled;

    public void setSize(String size) {
        this.size = size;
    }

    @JsonProperty("size")
    public String getSize() {
        return size;
    }

    @JsonProperty("placeholder")
    public String getPlaceholder() {
        return placeholder;
    }

    public ParamsProps setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
        return this;
    }

    public Boolean getDisabled() {
        return disabled;
    }

    public void setDisabled(Boolean disabled) {
        this.disabled = disabled;
    }
}
