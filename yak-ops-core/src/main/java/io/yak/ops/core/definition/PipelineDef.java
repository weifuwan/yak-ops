package io.yak.ops.core.definition;

import io.yak.ops.configuration.ReadonlyConfig;
import java.util.List;
import java.util.Objects;

/**
 * Declarative pipeline definition, based on Flink CDC 3.7's PipelineDef boundaries.
 * V4 remains a pinned legacy input and is interpreted by the existing V4 job planner.
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

    private PipelineDef(ReadonlyConfig legacy) {
        this.version = 4;
        this.original = Objects.requireNonNull(legacy, "legacy");
        this.source = null;
        this.sink = null;
        this.routes = List.of();
        this.transforms = List.of();
        this.config = ReadonlyConfig.fromMap(java.util.Map.of());
    }

    public static PipelineDef legacyV4(ReadonlyConfig legacy) {
        return new PipelineDef(legacy);
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
