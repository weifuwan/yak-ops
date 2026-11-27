package io.yak.ops.plugin.task.datasync;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A configured one-to-one source and sink table route. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSyncTableMapping {

    private String sourceTable;

    private String sinkTable;

    @Builder.Default
    private boolean databaseScope = false;

    public static List<DataSyncTableMapping> fromDefinition(JsonNode definition) {
        JsonNode routes = definition == null ? null : definition.path("route");
        if (routes == null || !routes.isArray()) {
            return Collections.emptyList();
        }

        List<DataSyncTableMapping> mappings = new ArrayList<>();
        for (JsonNode route : routes) {
            String source = route.path("source-table").asText();
            String sink = route.path("sink-table").asText();
            if (source.trim().isEmpty() || sink.trim().isEmpty()) {
                continue;
            }
            boolean databaseScope = source.endsWith(".\\.*") && sink.endsWith(".<>");
            mappings.add(DataSyncTableMapping.builder()
                    .sourceTable(source)
                    .sinkTable(sink)
                    .databaseScope(databaseScope)
                    .build());
        }
        return mappings;
    }

    public boolean matchesSourceTable(String actualTable) {
        return databaseScope
                ? matchesDatabasePattern(actualTable, sourceTable, ".\\.*")
                : sameTable(actualTable, sourceTable);
    }

    public boolean matchesSinkTable(String actualTable) {
        return databaseScope
                ? matchesDatabasePattern(actualTable, sinkTable, ".<>")
                : sameTable(actualTable, sinkTable);
    }

    public String resolveSinkTable(String actualSourceTable) {
        if (!databaseScope) {
            return sinkTable;
        }
        return databaseName(sinkTable, ".<>") + "." + tableName(actualSourceTable);
    }

    public String resolveSourceTable(String actualSinkTable) {
        if (!databaseScope) {
            return sourceTable;
        }
        return databaseName(sourceTable, ".\\.*") + "." + tableName(actualSinkTable);
    }

    private static boolean matchesDatabasePattern(String actualTable, String pattern, String suffix) {
        if (actualTable == null || pattern == null || !pattern.endsWith(suffix)) {
            return false;
        }
        String database = normalize(databaseName(pattern, suffix));
        String actual = normalize(actualTable);
        return actual.startsWith(database + ".") || actual.contains("." + database + ".");
    }

    private static String databaseName(String pattern, String suffix) {
        return pattern.substring(0, pattern.length() - suffix.length());
    }

    private static String tableName(String tablePath) {
        if (tablePath == null) {
            return "";
        }
        int separator = tablePath.lastIndexOf('.');
        return separator < 0 ? tablePath : tablePath.substring(separator + 1);
    }

    private static boolean sameTable(String actual, String configured) {
        if (actual == null || configured == null) {
            return false;
        }
        String normalizedActual = normalize(actual);
        String normalizedConfigured = normalize(configured);
        return normalizedActual.equals(normalizedConfigured)
                || normalizedActual.endsWith("." + normalizedConfigured);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
