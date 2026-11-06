package io.yak.ops.plugin.task.datasync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.plugin.task.api.enums.ResourceType;

import org.junit.jupiter.api.Test;

class DataSyncParametersTest {

    @Test
    void acceptsSingleTableInitialStreamingDefinition() {
        DataSyncParameters parameters = parse("INITIAL", "STREAMING", "UPSERT");
        assertTrue(parameters.checkParameters());
        assertTrue(parameters.getResources().getResourceMap(ResourceType.DATASOURCE).containsKey(1));
        assertTrue(parameters.getResources().getResourceMap(ResourceType.DATASOURCE).containsKey(2));
    }

    @Test
    void rejectsUnsupportedCDCStrategyAndWriteMode() {
        assertFalse(parse("SNAPSHOT", "STREAMING", "UPSERT").checkParameters());
        assertFalse(parse("INITIAL", "BATCH", "UPSERT").checkParameters());
        assertFalse(parse("INITIAL", "STREAMING", "APPEND").checkParameters());
    }

    @Test
    void rejectsRouteMismatchAndAdditionalTransforms() {
        DataSyncParameters parameters = parse("INITIAL", "STREAMING", "UPSERT");
        parameters.getRoute().get(0).setSinkTable("target_db.other");
        assertFalse(parameters.checkParameters());
        parameters = parse("INITIAL", "STREAMING", "UPSERT");
        parameters.setTransform(java.util.Collections.singletonList(JSONUtils.parseObject("{\"projection\":\"*\"}")));
        assertFalse(parameters.checkParameters());
    }

    @Test
    void rejectsDropColumnsForInitialPipeline() {
        DataSyncParameters parameters = parse("INITIAL", "STREAMING", "UPSERT");
        parameters.getSink().getSchemaPolicy().getEvolution().setAllowDropColumns(true);
        assertFalse(parameters.checkParameters());
    }

    static DataSyncParameters parse(String startup, String mode, String writeMode) {
        String json = "{"
                + "\"source\":{\"type\":\"mysql-cdc\",\"datasourceId\":\"1\","
                + "\"startup\":{\"mode\":\"" + startup + "\"},"
                + "\"tables\":[{\"database\":\"source_db\",\"table\":\"orders\"}]},"
                + "\"sink\":{\"type\":\"jdbc-mysql\",\"datasourceId\":\"2\","
                + "\"schemaPolicy\":{\"initial\":\"CREATE_OR_ALTER\","
                + "\"evolution\":{\"enabled\":true,\"allowDropColumns\":false}},"
                + "\"tables\":[{\"database\":\"target_db\",\"table\":\"orders\","
                + "\"write\":{\"mode\":\"" + writeMode + "\"}}]},"
                + "\"route\":[{\"source-table\":\"source_db.orders\","
                + "\"sink-table\":\"target_db.orders\"}],"
                + "\"transform\":[],"
                + "\"pipeline\":{\"execution.runtime-mode\":\"" + mode + "\","
                + "\"pipeline.table-scope\":\"SINGLE\"}"
                + "}";
        return JSONUtils.parseObject(json, DataSyncParameters.class);
    }
}
