package org.apache.flink.cdc.common.model;

import org.apache.flink.cdc.common.annotation.Experimental;

import java.util.Map;

/** Context passed to {@link AiModelClientFactory#createClient} at pipeline assembly time. */
@Experimental
public interface ModelContext {

    /** The logical name of this model as declared in the pipeline YAML. */
    String getModelName();

    /** Raw key/value options from the pipeline YAML {@code model.options} block. */
    Map<String, String> getOptions();

    /** Class loader to use when loading implementation classes. */
    ClassLoader getClassLoader();
}
