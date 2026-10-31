package io.yak.ops.composer.definition;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.core.types.CharType;
import io.yak.ops.core.types.DecimalType;
import io.yak.ops.core.types.LogicalType;
import io.yak.ops.core.types.LogicalTypeRoot;
import io.yak.ops.core.types.VarCharType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Validates lossless, explicit target type rules shared by planning and row projection. */
public final class TypeMappingRules {
    private static final List<Map<String, Object>> SUPPORTED = List.of(
            rule("TINYINT", List.of("SMALLINT", "INT", "BIGINT"), false),
            rule("SMALLINT", List.of("INT", "BIGINT"), false),
            rule("INT", List.of("BIGINT"), false),
            rule("BIGINT", List.of("DECIMAL(20,0)"), false),
            rule("FLOAT", List.of("DOUBLE"), false),
            rule("DECIMAL", List.of("DECIMAL(38,9)"), true),
            rule("CHAR", List.of("VARCHAR(255)", "VARCHAR(1024)", "VARCHAR(65535)"), true),
            rule("VARCHAR", List.of("VARCHAR(255)", "VARCHAR(1024)", "VARCHAR(65535)"), true));

    private final Map<String, String> mappings;

    private TypeMappingRules(Map<String, String> mappings) {
        this.mappings = Map.copyOf(mappings);
    }

    /**
     * Returns immutable capability descriptors. Conditional rules require sufficient source
     * precision or length for every matching physical column during admission and discovery.
     *
     * @return supported source families and selectable target types
     */
    public static List<Map<String, Object>> supportedRules() {
        return SUPPORTED;
    }

    public static TypeMappingRules from(PipelineDef definition) {
        String json = definition.getSink().getConfig().toMap().get("yak.type-mappings");
        if (json == null) return new TypeMappingRules(Map.of());
        try {
            JsonNode values = new ObjectMapper().readTree(json);
            if (!values.isArray()) throw invalid();
            Map<String, String> mappings = new LinkedHashMap<>();
            for (JsonNode value : values) {
                if (!value.isObject()
                        || value.size() != 2
                        || !value.path("sourceType").isTextual()
                        || !value.path("targetType").isTextual()) {
                    throw invalid();
                }
                String source = value.path("sourceType").textValue();
                String target = value.path("targetType").textValue();
                boolean supported = SUPPORTED.stream()
                        .anyMatch(rule -> rule.get("sourceType").equals(source)
                                && ((List<?>) rule.get("targetTypes")).contains(target));
                if (!supported || mappings.putIfAbsent(source, target) != null) throw invalid();
            }
            return new TypeMappingRules(mappings);
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException("Invalid target type mapping rules", failure);
        }
    }

    public boolean isEmpty() {
        return mappings.isEmpty();
    }

    /** Returns a supported CAST target, rejecting precision loss before any rows are written. */
    public String target(LogicalType source) {
        String family = source.getTypeRoot() == LogicalTypeRoot.INTEGER
                ? "INT"
                : source.getTypeRoot().name();
        String target = mappings.get(family);
        if (target == null) return null;
        if (source instanceof DecimalType decimal
                && (decimal.scale() > 9 || decimal.precision() - decimal.scale() > 29)) {
            throw new IllegalArgumentException("Target decimal mapping would reduce source precision");
        }
        if (source.getTypeRoot() == LogicalTypeRoot.DECIMAL && !(source instanceof DecimalType)) {
            throw new IllegalArgumentException("Target decimal mapping requires resolved source precision");
        }
        if (source instanceof CharType character && character.length() > length(target)
                || source instanceof VarCharType variable && variable.length() > length(target)) {
            throw new IllegalArgumentException("Target character mapping would reduce source capacity");
        }
        return target;
    }

    /** Stable sorted representation participates in the immutable transform fingerprint. */
    public String semanticRevision() {
        return mappings.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(";"));
    }

    private static int length(String target) {
        return Integer.parseInt(target.substring(target.indexOf('(') + 1, target.length() - 1));
    }

    private static Map<String, Object> rule(String source, List<String> targets, boolean conditional) {
        return Map.of("sourceType", source, "targetTypes", targets, "conditional", conditional);
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Unsupported or duplicate target type mapping");
    }
}
