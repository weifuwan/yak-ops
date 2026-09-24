package io.yak.ops.business.datasource.catalog;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import io.yak.ops.spi.datasource.metadata.DataSourceTable;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 兼容旧表选择器的正则和精确表名匹配，不把 UI 匹配语义下沉到插件。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Component
@ConditionalOnDataSourceEnabled
public class CatalogTableMatcher {

    private static final int MAX_MATCH_KEYWORD_LENGTH = 256;

    public List<DataSourceTable> match(List<DataSourceTable> tables, String matchMode, String keyword) {
        List<DataSourceTable> source = tables == null ? List.of() : tables;
        if (isBlank(keyword)) return source;
        if (keyword.length() > MAX_MATCH_KEYWORD_LENGTH) {
            throw new DataSourceException(
                    DataSourceErrorCode.INVALID_CONNECTION_PARAMS,
                    "表名匹配条件不能超过 " + MAX_MATCH_KEYWORD_LENGTH + " 个字符");
        }

        if ("2".equals(matchMode)) {
            try {
                Pattern pattern = Pattern.compile(keyword);
                return source.stream()
                        .filter(table -> pattern.matcher(table.getName()).matches())
                        .toList();
            } catch (PatternSyntaxException exception) {
                throw new DataSourceException(
                        DataSourceErrorCode.INVALID_CONNECTION_PARAMS,
                        "表名正则表达式不合法：" + exception.getDescription(),
                        exception);
            }
        }

        if ("3".equals(matchMode)) {
            Set<String> exactNames = Arrays.stream(keyword.split(","))
                    .map(String::trim)
                    .filter(name -> !name.isEmpty())
                    .collect(Collectors.toSet());
            return source.stream()
                    .filter(table -> exactNames.contains(table.getName()))
                    .toList();
        }

        return source;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
