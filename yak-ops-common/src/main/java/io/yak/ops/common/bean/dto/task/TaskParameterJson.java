package io.yak.ops.common.bean.dto.task;

import com.fasterxml.jackson.databind.JsonNode;

/** JSON-object transport boundary for plugin-owned Task parameters; database storage stays canonical JSON text. */
public final class TaskParameterJson {
    private TaskParameterJson() {}

    public static String requiredObject(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("Task parameters must be a JSON object");
        }
        return node.toString();
    }
}
