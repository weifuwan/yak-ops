package io.yak.ops.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 统一 JSON 解析与序列化入口，避免业务模块各自创建 ObjectMapper。
 *
 * @author weifuwan
 * @since 2026-09-25
 */
public final class JsonUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private JsonUtils() {}

    /** 解析 JSON 树。 */
    public static JsonNode readTree(String value) {
        try {
            return OBJECT_MAPPER.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("JSON 解析失败", exception);
        }
    }

    /** 解析 JSON 对象。 */
    public static ObjectNode readObject(String value) {
        JsonNode root = readTree(value);
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("JSON 必须是对象");
        }
        return (ObjectNode) root;
    }

    /** 创建空 JSON 对象。 */
    public static ObjectNode createObjectNode() {
        return OBJECT_MAPPER.createObjectNode();
    }

    /** 序列化为 JSON 字符串。 */
    public static String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("JSON 序列化失败", exception);
        }
    }

    /** 按候选 key 顺序获取首个非 null 文本值。 */
    public static String firstText(JsonNode node, String... keys) {
        if (node == null || keys == null) return null;
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && !value.isNull()) return value.asText();
        }
        return null;
    }
}
