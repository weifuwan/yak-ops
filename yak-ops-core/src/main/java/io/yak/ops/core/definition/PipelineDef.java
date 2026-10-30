package io.yak.ops.core.definition;

import io.yak.ops.configuration.ReadonlyConfig;
import java.util.List;
import java.util.Objects;

/**
 * Declarative pipeline definition, based on Flink CDC 3.7's PipelineDef boundaries.
 * V5 is the only supported pipeline definition.
 */
public final class PipelineDef {
    private final int version;
    private final ReadonlyConfig original;
    private final SourceDef source;
    private final SinkDef sink;
    private final List<RouteDef> routes;
    private final List<TransformDef> transforms;
    private final ReadonlyConfig config;

    public PipelineDef(
            ReadonlyConfig original,
            SourceDef source,
            SinkDef sink,
            List<RouteDef> routes,
            List<TransformDef> transforms,
            ReadonlyConfig config) {
        this.version = 5;
        this.original = Objects.requireNonNull(original, "original");
        this.source = Objects.requireNonNull(source, "source");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.routes = List.copyOf(routes);
        this.transforms = List.copyOf(transforms);
        this.config = Objects.requireNonNull(config, "config");
    }

    public int getVersion() {
        return version;
    }

    public ReadonlyConfig getOriginal() {
        return original;
    }

    public SourceDef getSource() {
        return source;
    }

    public SinkDef getSink() {
        return sink;
    }

    public List<RouteDef> getRoute() {
        return routes;
    }

    public List<TransformDef> getTransforms() {
        return transforms;
    }

    public ReadonlyConfig getConfig() {
        return config;
    }
}
