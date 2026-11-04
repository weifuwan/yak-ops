package io.yak.ops.spi.params.radio;

import io.yak.ops.spi.params.base.ParamsProps;

import lombok.Data;

/**
 * front-end radio component props attributes
 */
@Data
public class RadioParamProps extends ParamsProps {

    /**
     * the color of the text when Radio is activated in the form of a button
     */
    private String textColor;

    /**
     * the fill color and border color of the button form of Radio when activated
     */
    private String fill;

}
