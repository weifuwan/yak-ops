package org.apache.flink.cdc.composer.definition;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/** Definition of an AI model declared in a pipeline. */
public class ModelDef {

    private final String name;

    private final String type;

    private final String className;

    private final Map<String, String> options;

    private final boolean legacy;

    /**
     * Creates a legacy model definition backed by a model UDF class.
     *
     * @deprecated Use {@link #of(String, String, Map)} for factory-based AI model clients.
     */
    @Deprecated
    public ModelDef(String modelName, String className, Map<String, String> parameters) {
        this(modelName, null, className, parameters, true);
    }

    private ModelDef(
            String name,
            String type,
            String className,
            Map<String, String> options,
            boolean legacy) {
        this.name = name;
        this.type = type;
        this.className = className;
        this.options = options == null ? Collections.emptyMap() : options;
        this.legacy = legacy;
    }

    /** Creates a factory-based AI model client definition. */
    public static ModelDef of(String name, String type, Map<String, String> options) {
        return new ModelDef(name, type, null, options, false);
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public Map<String, String> getOptions() {
        return options;
    }

    public boolean isLegacy() {
        return legacy;
    }

    /**
     * @deprecated Use {@link #getName()}.
     */
    @Deprecated
    public String getModelName() {
        return name;
    }

    /**
     * @deprecated Factory-based models use {@link #getType()}.
     */
    @Deprecated
    public String getClassName() {
        return className;
    }

    /**
     * @deprecated Use {@link #getOptions()}.
     */
    @Deprecated
    public Map<String, String> getParameters() {
        return options;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ModelDef modelDef = (ModelDef) o;
        return Objects.equals(name, modelDef.name)
                && Objects.equals(type, modelDef.type)
                && Objects.equals(className, modelDef.className)
                && Objects.equals(options, modelDef.options)
                && legacy == modelDef.legacy;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type, className, options, legacy);
    }

    @Override
    public String toString() {
        return "ModelDef{"
                + "name='"
                + name
                + '\''
                + (legacy ? ", className='" + className + '\'' : ", type='" + type + '\'')
                + ", options="
                + options
                + ", legacy="
                + legacy
                + '}';
    }
}
