package io.yak.ops.business.datasource.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import io.yak.ops.common.util.JsonUtils;
import io.yak.ops.common.util.SensitiveUtils;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePluginDescriptor;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 处理 Datasource 连接参数中的敏感字段遮罩和编辑态密钥保留。
 *
 * <p>Plugin descriptor 提供领域敏感字段，本类再叠加 password / token / private key 等通用规则；任何对外连接 JSON 都必须经过遮罩后返回。</p>
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Component
public class DataSourceSecretCodec {

    /** Datasource 连接参数中跨 Provider 通用的敏感字段名。 */
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

    /**
     * 递归遮罩连接 JSON 中的敏感字段，保持原 JSON 结构不变。
     *
     * @param descriptor Plugin 元数据
     * @param connectionJson 原始连接参数
     * @return 可安全返回给前端的连接 JSON
     */
    public String maskConnectionJson(DataSourcePluginDescriptor descriptor, String connectionJson) {
        if (connectionJson == null || connectionJson.trim().isEmpty()) return null;
        ObjectNode root = readObject(connectionJson);
        maskObject(root, secretKeys(descriptor));
        return write(root);
    }

    /**
     * 编辑数据源时，将空值或遮罩占位符恢复为已保存的密钥值。
     *
     * <p>只有识别为敏感字段的值会被保留，普通字段始终以本次提交内容为准。</p>
     *
     * @param descriptor Plugin 元数据
     * @param submittedJson 本次提交的连接参数
     * @param storedJson 已保存的连接参数
     * @return 合并后的完整连接 JSON
     */
    public String mergeStoredSecrets(DataSourcePluginDescriptor descriptor, String submittedJson, String storedJson) {
        ObjectNode submitted = readObject(submittedJson);
        ObjectNode stored = readObject(storedJson);
        mergeObject(submitted, stored, secretKeys(descriptor));
        return write(submitted);
    }

    /** 对 JDBC URL、异常消息等普通文本执行凭证遮罩。 */
    public String maskSensitiveText(String value) {
        return SensitiveUtils.mask(value);
    }

    private void maskObject(ObjectNode object, Set<String> configuredKeys) {
        Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode value = field.getValue();
            if (isSecretKey(field.getKey(), configuredKeys)) {
                object.put(field.getKey(), SensitiveUtils.MASKED_VALUE);
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
        return text == null || text.trim().isEmpty() || SensitiveUtils.MASKED_VALUE.equals(text.trim());
    }

    private ObjectNode readObject(String value) {
        try {
            JsonNode root = JsonUtils.readTree(value);
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
            return JsonUtils.toJson(value);
        } catch (Exception exception) {
            throw invalidJson("连接参数序列化失败", exception);
        }
    }

    private DataSourceException invalidJson(String message, Throwable cause) {
        return new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS, message, cause);
    }
}
