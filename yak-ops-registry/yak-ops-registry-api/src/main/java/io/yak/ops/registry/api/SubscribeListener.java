package io.yak.ops.registry.api;

public interface SubscribeListener {

    void notify(final Event event);

    SubscribeScope getSubscribeScope();

    enum SubscribeScope {
        /**
         * Only watch the path itself
         */
        PATH_ONLY,
        /**
         * Only watch the children of the path
         */
        CHILDREN_ONLY,
        /**
         * Watch the path and all its children and the parent path
         */
        ALL

    }
}
