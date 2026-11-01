package io.yak.ops.plugin.task.datasync;

import io.yak.ops.plugin.task.api.TaskPluginException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Enforces the published schema 7 boundary while using one current execution model. */
final class PipelineParameterVersions {
    private PipelineParameterVersions() {}

    static void validateSeven(Map<String, Object> root) {
        Map<?, ?> source = map(root.get("source"));
        Map<?, ?> sink = map(root.get("sink"));
        reject(source, Set.of("selection"));
        reject(sink, Set.of("mapping", "typeMappings"));
        for (Object value : list(source.get("tables"))) {
            Map<?, ?> table = map(value);
            reject(table, Set.of("read", "options", "dml"));
            if (table.containsKey("snapshot")) {
                Map<?, ?> snapshot = map(table.get("snapshot"));
                if (!Set.of("chunkKeyColumn").containsAll(snapshot.keySet())) {
                    throw unsupported();
                }
            }
        }
        for (Object value : list(sink.get("tables"))) {
            Map<?, ?> write = map(map(value).get("write"));
            reject(write, Set.of("preSql", "postSql", "batchSize"));
            if ("REPLACE".equals(write.get("mode"))) throw unsupported();
        }
    }

    private static void reject(Map<?, ?> values, Set<String> keys) {
        if (keys.stream().anyMatch(values::containsKey)) throw unsupported();
    }

    private static Map<?, ?> map(Object value) {
        if (!(value instanceof Map<?, ?> result)) throw unsupported();
        return result;
    }

    private static List<?> list(Object value) {
        if (!(value instanceof List<?> result)) throw unsupported();
        return result;
    }

    private static TaskPluginException unsupported() {
        return new TaskPluginException("Parameters do not conform to published DATA_SYNC schema 7");
    }
}
