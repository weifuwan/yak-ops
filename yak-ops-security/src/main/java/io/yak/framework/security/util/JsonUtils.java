package io.yak.framework.security.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * 安全模块统一的 JSON 转换工具。
 *
 * <p>用于统一处理对象序列化、对象反序列化及集合反序列化，
 * 避免业务层绑定到特定的旧序列化实现。
 *
 * @author weifuwan
 */
public final class JsonUtils {

    /**
     * Jackson JSON 转换器。
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 禁止实例化工具类。
     */
    private JsonUtils() {
    }

    /**
     * 将对象序列化为 JSON 字符串。
     *
     * @param value 待序列化的对象
     * @return JSON 字符串
     * @throws IllegalArgumentException 当 JSON 序列化失败时抛出
     */
    public static String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("JSON 序列化失败", exception);
        }
    }

    /**
     * 将 JSON 字符串反序列化为指定类型的对象。
     *
     * @param value JSON 字符串
     * @param type  目标对象类型
     * @param <T>   目标对象类型
     * @return 反序列化后的对象
     * @throws IllegalArgumentException 当 JSON 反序列化失败时抛出
     */
    public static <T> T fromJson(String value, Class<T> type) {
        try {
            return OBJECT_MAPPER.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("JSON 反序列化失败", exception);
        }
    }

    /**
     * 将 JSON 数组字符串反序列化为指定元素类型的集合。
     *
     * @param value       JSON 数组字符串
     * @param elementType 集合元素类型
     * @param <T>         集合元素类型
     * @return 反序列化后的集合
     * @throws IllegalArgumentException 当 JSON 数组反序列化失败时抛出
     */
    public static <T> List<T> toList(
            String value,
            Class<T> elementType) {

        JavaType type =
                OBJECT_MAPPER
                        .getTypeFactory()
                        .constructCollectionType(List.class, elementType);

        try {
            return OBJECT_MAPPER.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "JSON 数组反序列化失败",
                    exception);
        }
    }
}
