package io.yak.ops.plugin.task.api.parameters;

/** Shared parameter validation contract for DolphinScheduler-style Task plugins. */
public abstract class AbstractParameters {
    public abstract boolean checkParameters();
}
