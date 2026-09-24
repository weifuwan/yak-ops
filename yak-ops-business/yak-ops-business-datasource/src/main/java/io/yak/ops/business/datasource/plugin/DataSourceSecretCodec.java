package io.yak.ops.business.datasource.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.business.datasource.security.SensitiveTextMasker;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor;
import jakarta.annotation.Resource;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 根据插件表单定义统一完成连接参数的敏感字段遮罩和编辑态密钥合并。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Component
@ConditionalOnDataSourceEnabled
public class DataSourceSecretCodec {

    public static final String MASKED_VALUE = SensitiveTextMasker.MASKED_VALUE;

    private static final Set<String> COMMON_SECRET_KEYS = Set.of(
            "password",
            "pwd",
            "secret",
            "secretkey",
            "accesstoken",
            "token",
            "privatekey",
            "privatekeycontent",
            "passphrase",
            "privatekeypassphrase");

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private SensitiveTextMasker textMasker;

    public String maskConnectionJson(DataSourcePluginDescriptor descriptor, String connectionJson) {
        if (connectionJson == null || connectionJson.trim().isEmpty()) return null;
        ObjectNode root = readObject(connectionJson);
        maskObject(root, secretKeys(descriptor));
        return write(root);
    }

    public String mergeStoredSecrets(DataSourcePluginDescriptor descriptor, String submittedJson, String storedJson) {
        ObjectNode submitted = readObject(submittedJson);
        ObjectNode stored = readObject(storedJson);
        mergeObject(submitted, stored, secretKeys(descriptor));
        return write(submitted);
    }

    public String maskSensitiveText(String value) {
        return textMasker.mask(value);
    }

    private void maskObject(ObjectNode object, Set<String> configuredKeys) {
        Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode value = field.getValue();
            if (isSecretKey(field.getKey(), configuredKeys)) {
                object.put(field.getKey(), MASKED_VALUE);
            } else if (value != null && value.isObject()) {
                maskObject((ObjectNode) value, configuredKeys);
            } else if (value != null && value.isArray()) {
                maskArray((ArrayNode) value, configuredKeys);
            }
        }
    }

    private void maskArray(ArrayNode array, Set<String> configuredKeys) {
        for (JsonNode value : array) {
            if (value != null && value.isObject()) {
                maskObject((ObjectNode) value, configuredKeys);
            } else if (value != null && value.isArray()) {
                maskArray((ArrayNode) value, configuredKeys);
            }
        }
    }

    private void mergeObject(ObjectNode submitted, ObjectNode stored, Set<String> configuredKeys) {
        Iterator<Map.Entry<String, JsonNode>> storedFields = stored.fields();
        while (storedFields.hasNext()) {
            Map.Entry<String, JsonNode> field = storedFields.next();
            String key = field.getKey();
            JsonNode storedValue = field.getValue();
            JsonNode submittedValue = submitted.get(key);

            if (isSecretKey(key, configuredKeys) && shouldPreserve(submittedValue)) {
                submitted.set(key, storedValue.deepCopy());
            } else if (storedValue != null
                    && storedValue.isObject()
                    && submittedValue != null
                    && submittedValue.isObject()) {
                mergeObject((ObjectNode) submittedValue, (ObjectNode) storedValue, configuredKeys);
            } else if (storedValue != null
                    && storedValue.isArray()
                    && submittedValue != null
                    && submittedValue.isArray()) {
                mergeArray((ArrayNode) submittedValue, (ArrayNode) storedValue, configuredKeys);
            }
        }
    }

    private void mergeArray(ArrayNode submitted, ArrayNode stored, Set<String> configuredKeys) {
        int length = Math.min(submitted.size(), stored.size());
        for (int index = 0; index < length; index++) {
            JsonNode submittedValue = submitted.get(index);
            JsonNode storedValue = stored.get(index);
            if (submittedValue != null && submittedValue.isObject() && storedValue != null && storedValue.isObject()) {
                mergeObject((ObjectNode) submittedValue, (ObjectNode) storedValue, configuredKeys);
            } else if (submittedValue != null
                    && submittedValue.isArray()
                    && storedValue != null
                    && storedValue.isArray()) {
                mergeArray((ArrayNode) submittedValue, (ArrayNode) storedValue, configuredKeys);
            }
        }
    }

    private Set<String> secretKeys(DataSourcePluginDescriptor descriptor) {
        Set<String> keys = new LinkedHashSet<>();
        if (descriptor == null) return keys;
        for (String key : descriptor.secretFieldKeys()) keys.add(normalizeKey(key));
        return keys;
    }

    private boolean isSecretKey(String key, Set<String> configuredKeys) {
        String normalized = normalizeKey(key);
        return COMMON_SECRET_KEYS.contains(normalized)
                || configuredKeys.contains(normalized)
                || normalized.endsWith("password")
                || normalized.endsWith("secret")
                || normalized.endsWith("token")
                || normalized.endsWith("privatekey")
                || normalized.endsWith("passphrase");
    }

    private String normalizeKey(String key) {
        return key == null ? "" : key.replace("_", "").replace("-", "").trim().toLowerCase(Locale.ROOT);
    }

    private boolean shouldPreserve(JsonNode value) {
        if (value == null || value.isNull()) return true;
        if (!value.isTextual()) return false;
        String text = value.asText();
        return text == null || text.trim().isEmpty() || MASKED_VALUE.equals(text.trim());
    }

    private ObjectNode readObject(String value) {
        try {
            JsonNode root = objectMapper.readTree(value);
            if (root == null || !root.isObject()) throw invalidJson("连接参数必须是 JSON 对象", null);
            return (ObjectNode) root;
        } catch (DataSourceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidJson("连接参数不是合法 JSON", exception);
        }
    }

    private String write(ObjectNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw invalidJson("连接参数序列化失败", exception);
        }
    }

    private DataSourceException invalidJson(String message, Throwable cause) {
        return new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS, message, cause);
    }
}
