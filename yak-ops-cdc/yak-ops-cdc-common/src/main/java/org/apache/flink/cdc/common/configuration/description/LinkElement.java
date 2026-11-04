package org.apache.flink.cdc.common.configuration.description;

import org.apache.flink.cdc.common.annotation.PublicEvolving;

/** Element that represents a link in the {@link Description}. */
@PublicEvolving
public class LinkElement implements InlineElement {
    private final String link;
    private final String text;

    /**
     * Creates a link with a given url and description.
     *
     * @param link address that this link should point to
     * @param text a description for that link, that should be used in text
     * @return link representation
     */
    public static LinkElement link(String link, String text) {
        return new LinkElement(link, text);
    }

    /**
     * Creates a link with a given url. This url will be used as a description for that link.
     *
     * @param link address that this link should point to
     * @return link representation
     */
    public static LinkElement link(String link) {
        return new LinkElement(link, link);
    }

    public String getLink() {
        return link;
    }

    public String getText() {
        return text;
    }

    private LinkElement(String link, String text) {
        this.link = link;
        this.text = text;
    }

    @Override
    public void format(Formatter formatter) {
        formatter.format(this);
    }
}
