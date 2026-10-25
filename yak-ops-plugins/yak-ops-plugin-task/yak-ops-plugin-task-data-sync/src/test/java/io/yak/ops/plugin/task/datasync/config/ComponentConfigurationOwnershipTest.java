package io.yak.ops.plugin.task.datasync.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.configuration.ConfigParser;
import io.yak.ops.connector.cdc.mysql.source.MySqlCdcSourceOptions;
import io.yak.ops.connector.jdbc.sink.JdbcSinkOptions;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Source and Sink own distinct read, identity and commit protocols. */
class ComponentConfigurationOwnershipTest {
    @Test
    void sendsSnapshotSettingsToSourceAndXaOnlyToSink() {
        var saved = ConfigParser.parseJson(keyless("RETRACT", "\"transaction\":{\"mode\":\"CHECKPOINT_XA\"}"));
        var job = JobConfig.from(saved);
        assertEquals(saved, job.parameters());
        assertTrue(job.source().get(MySqlCdcSourceOptions.KEYLESS_ENABLED));
        assertEquals("bucket", job.source().get(MySqlCdcSourceOptions.CHUNK_KEY_COLUMN));
        assertFalse(job.source().getSourceMap().containsKey("xa"));
        assertFalse(job.source().getSourceMap().containsKey("snapshot"));
        assertTrue(job.sink().get(JdbcSinkOptions.XA_ENABLED));
        assertFalse(saved.getSourceMap().containsKey(JdbcSinkOptions.XA_ENABLED.key()));
    }

    @Test
    void keepsVerifiedTargetKeysOutOfConnectorOptionMaps() {
        var job = JobConfig.from(ConfigParser.parseJson(keyless("UPSERT", "\"primaryKeys\":[\"bucket\"]")));
        assertEquals(List.of("bucket"), job.tables().getFirst().targetPrimaryKeys());
        assertFalse(job.sink().get(JdbcSinkOptions.XA_ENABLED));
        assertFalse(job.sink().getSourceMap().containsKey("primaryKeys"));
        assertFalse(job.source().getSourceMap().containsKey("keyless"));
        assertEquals("MYSQL_CDC", job.source().get(JobOptions.TYPE));
        assertEquals("JDBC", job.sink().get(JobOptions.TYPE));
    }

    @Test
    void incrementalSelectionIsSourceOwnedAndRequiresUpsetWrite() {
        String json = """
                {"solutionType":"SINGLE_TABLE_OFFLINE","steps":["INCREMENTAL"],
                 "source":{"datasourceId":"source","tables":[{"id":"orders","table":"orders",
                           "incremental":{"mode":"TIMESTAMP","column":"updated_at",
                                          "initialValue":"2026-10-01T00:00:00"}}]},
                 "transform":[],
                 "sink":{"datasourceId":"sink",
                         "schemaPolicy":{"initial":"VERIFY","evolution":{"enabled":false,"allowDropColumns":false}},
                         "tables":[{"input":"orders","table":"copy","write":{"mode":"UPSERT"}}]},
                 "env":{}}
                """;
        var job = JobConfig.from(ConfigParser.parseJson(json));
        assertEquals("updated_at", job.tables().getFirst().incremental().column());
        assertFalse(job.source().getSourceMap().containsKey("incremental"));
        assertEquals("UPSERT", job.sink().getSourceMap().get("writeMode"));
        assertThrows(IllegalArgumentException.class, () -> JobConfig.from(ConfigParser.parseJson(
                json.replace("\"mode\":\"UPSERT\"", "\"mode\":\"APPEND\""))));
    }

    @Test
    void forbidsEnvironmentAndPhysicalOptionInjectionInProductDefinitions() {
        String json = """
                {"solutionType":"SINGLE_TABLE_OFFLINE","steps":["FULL"],
                 "source":{"datasourceId":"source","tables":[{"id":"orders","table":"orders"}]},
                 "transform":[],
                 "sink":{"datasourceId":"sink",
                         "schemaPolicy":{"initial":"VERIFY","evolution":{"enabled":false,"allowDropColumns":false}},
                         "tables":[{"input":"orders","table":"copy","write":{"mode":"APPEND"}}]},
                 "env":{}}
                """;
        assertThrows(RuntimeException.class, () -> JobConfig.from(ConfigParser.parseJson(
                json.replace("\"env\":{}", "\"env\":{\"execution.debug\":true}"))));
        assertThrows(RuntimeException.class, () -> JobConfig.from(ConfigParser.parseJson(
                json.replace("\"table\":\"orders\"", "\"table\":\"orders\",\"connector.mysql-cdc.server-id\":12"))));
        assertThrows(RuntimeException.class, () -> JobConfig.from(ConfigParser.parseJson(
                json.replace("\"table\":\"copy\"", "\"table\":\"copy\",\"connector.jdbc.sink.xa-enabled\":true"))));
    }

    private static String keyless(String writeMode, String writeOptions) {
        return """
                {"solutionType":"SINGLE_TABLE_REALTIME","steps":["FULL","INCREMENTAL"],
                 "source":{"datasourceId":"source","tables":[{"id":"orders","table":"orders",
                           "keyless":true,"snapshot":{"chunkKeyColumn":"bucket"}}]},
                 "transform":[],
                 "sink":{"datasourceId":"sink",
                         "schemaPolicy":{"initial":"VERIFY","evolution":{"enabled":false,"allowDropColumns":false}},
                         "tables":[{"input":"orders","table":"copy",
                                    "write":{"mode":"%s",%s}}]},
                 "env":{}}
                """.formatted(writeMode, writeOptions);
    }
}
