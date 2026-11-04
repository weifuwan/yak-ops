package org.apache.flink.cdc.common.configuration.description;

import org.apache.flink.cdc.common.annotation.PublicEvolving;

/** Represents a line break in the {@link Description}. */
@PublicEvolving
public class LineBreakElement implements InlineElement, BlockElement {

    /** Creates a line break in the description. */
    public static LineBreakElement linebreak() {
        return new LineBreakElement();
    }

    private LineBreakElement() {}

    @Override
    public void format(Formatter formatter) {
        formatter.format(this);
    }
}
