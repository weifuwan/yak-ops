package io.yak.ops.business.datasource.catalog;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.domain.catalog.CatalogTable;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Applies legacy table matching modes without leaking them into the catalog gateway. */
@Component
@ConditionalOnDataSourceEnabled
public class CatalogTableMatcher {

    private static final int MAX_MATCH_KEYWORD_LENGTH = 256;

    public List<CatalogTable> match(List<CatalogTable> tables, String matchMode, String keyword) {
        List<CatalogTable> source = tables == null ? List.of() : tables;
        if (isBlank(keyword)) return source;
        if (keyword.length() > MAX_MATCH_KEYWORD_LENGTH) {
            throw new DataSourceException(
                    DataSourceErrorCode.INVALID_CONNECTION_PARAMS, "表名匹配条件不能超过 " + MAX_MATCH_KEYWORD_LENGTH + " 个字符");
        }

        if ("2".equals(matchMode)) {
            try {
                Pattern pattern = Pattern.compile(keyword);
                return source.stream()
                        .filter(table -> pattern.matcher(table.name()).matches())
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
                    .filter(table -> exactNames.contains(table.name()))
                    .toList();
        }

        return source;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
