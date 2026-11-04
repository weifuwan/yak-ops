package io.yak.ops.plugin.alert.email.template;

import io.yak.ops.alert.api.ShowType;

public interface AlertTemplate {

    /**
     * get a message from a specified alert template
     *
     * @param content alert message content
     * @param showType show type
     * @param showAll whether to show all
     * @return a message from a specified alert template
     */
    String getMessageFromTemplate(String content, ShowType showType, boolean showAll);

    /**
     * default showAll is true
     *
     * @param content alert message content
     * @param showType show type
     * @return a message from a specified alert template
     */
    default String getMessageFromTemplate(String content, ShowType showType) {
        return getMessageFromTemplate(content, showType, true);
    }
}
