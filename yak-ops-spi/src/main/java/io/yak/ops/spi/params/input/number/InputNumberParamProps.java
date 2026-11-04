package io.yak.ops.spi.params.input.number;

import io.yak.ops.spi.params.base.ParamsProps;

import lombok.Data;

/**
 * front-end input number component props attributes
 */
@Data
public class InputNumberParamProps extends ParamsProps {

    /**
     * set the minimum value allowed by the counter
     */
    private Integer min;

    /**
     * set the maximum value allowed by the counter
     */
    private Integer max;

    /**
     * counter step
     */
    private Integer step;

    /**
     * numerical accuracy
     */
    private Integer precision;

    /**
     * whether to use the control button, the default value is true
     */
    private Boolean controls;

    /**
     * control button position, the default value is right
     */
    private String controlsPosition;

    /**
     * name attribute
     */
    private String name;

    /**
     * the label text associated with the input box
     */
    private String label;

}
