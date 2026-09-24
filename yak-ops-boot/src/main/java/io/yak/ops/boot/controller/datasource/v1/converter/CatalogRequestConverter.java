package io.yak.ops.boot.controller.datasource.v1.converter;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.domain.catalog.CatalogReadRequest;
import io.yak.ops.business.datasource.domain.catalog.CatalogReadRequest.ReadMode;
import io.yak.ops.business.datasource.domain.catalog.CatalogReadRequest.Variable;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Converts the legacy HTTP Map compatibility contract into typed catalog requests. */
@Component
@ConditionalOnDataSourceEnabled
public class CatalogRequestConverter {

  public CatalogReadRequest readRequest(Map<String, Object> requestBody) {
    Map<String, Object> request = requireRequest(requestBody);
    String readMode = optionalText(request, "read_mode", "readMode");
    String tablePath = optionalText(request, "table_path", "tablePath", "table");
    String query = optionalText(request, "query", "sql");
    ReadMode mode = "sql".equalsIgnoreCase(readMode) || (!isBlank(query) && isBlank(tablePath))
        ? ReadMode.SQL : ReadMode.TABLE;
    try {
      return new CatalogReadRequest(mode, tablePath, query, variables(request));
    } catch (IllegalArgumentException exception) {
      throw new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS, exception.getMessage(), exception);
    }
  }

  public String tablePath(Map<String, Object> requestBody) {
    return requiredText(requireRequest(requestBody), "table_path", "tablePath", "table");
  }

  private List<Variable> variables(Map<String, Object> request) {
    Object value = request.get("paramsList");
    if (!(value instanceof Iterable<?> items)) return List.of();
    List<Variable> variables = new ArrayList<>();
    for (Object item : items) {
      if (!(item instanceof Map<?, ?> map)) continue;
      String name = mapText(map, "paramName", "name");
      Object rawValue = mapValue(map, "paramValue", "value");
      if (!isBlank(name)) variables.add(new Variable(name, rawValue == null ? null : String.valueOf(rawValue)));
    }
    return List.copyOf(variables);
  }

  private Map<String, Object> requireRequest(Map<String, Object> requestBody) {
    if (requestBody == null || requestBody.isEmpty()) {
      throw new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS, "requestBody 不能为空");
    }
    return requestBody;
  }

  private String requiredText(Map<String, Object> request, String... keys) {
    String value = optionalText(request, keys);
    if (!isBlank(value)) return value;
    throw new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS, keys[0] + " 不能为空");
  }

  private String optionalText(Map<String, Object> request, String... keys) {
    Object value = mapValue(request, keys);
    return value == null || String.valueOf(value).trim().isEmpty() ? null : String.valueOf(value).trim();
  }

  private String mapText(Map<?, ?> request, String... keys) {
    Object value = mapValue(request, keys);
    return value == null ? null : String.valueOf(value).trim();
  }

  private Object mapValue(Map<?, ?> request, String... keys) {
    for (String key : keys) if (request.containsKey(key)) return request.get(key);
    return null;
  }

  private boolean isBlank(String value) { return value == null || value.trim().isEmpty(); }
}
