package io.yak.ops.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

/**
 * 统一提供无领域语义的 JSON 解析、JSON Tree 创建与序列化能力。
 *
 * @author weifuwan
 * @since 2026-09-25
 */
public final class JSONUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private JSONUtils() {}

    public static String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("JSON serialization failed", exception);
        }
    }

    public static JsonNode readTree(String json) {
        requireJson(json);
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid JSON", exception);
        }
    }

    public static ObjectNode createObjectNode() {
        return OBJECT_MAPPER.createObjectNode();
    }

    public static String firstText(JsonNode node, String... keys) {
        if (ObjectUtils.isNull(node) || ObjectUtils.isNull(keys)) return null;
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (ObjectUtils.isNotNull(value) && !value.isNull()) return value.asText();
        }
        return null;
    }

    public static <T> T parseObject(String json, Class<T> target) {
        requireJson(json);
        if (ObjectUtils.isNull(target)) throw new IllegalArgumentException("target must not be null");
        try {
            return OBJECT_MAPPER.readValue(json, target);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid JSON", exception);
        }
    }

    public static <T> List<T> parseList(String json, Class<T> elementType) {
        requireJson(json);
        if (ObjectUtils.isNull(elementType)) throw new IllegalArgumentException("elementType must not be null");
        try {
            JavaType type = OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, elementType);
            return OBJECT_MAPPER.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid JSON", exception);
        }
    }

    private static void requireJson(String json) {
        if (StringUtils.isBlank(json)) throw new IllegalArgumentException("JSON must not be blank");
    }
}
