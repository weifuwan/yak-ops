package io.yak.ops.sync.runtime.connector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.connector.base.factory.TableSourceFactory;
import io.yak.ops.connector.base.factory.TableSourceFactoryContext;
import io.yak.ops.connector.cdc.mysql.table.MySqlCdcSourceFactory;
import io.yak.ops.connector.jdbc.sink.JdbcSinkFactory;
import io.yak.ops.connector.jdbc.source.JdbcSourceFactory;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.parser.YamlPipelineDefinitionParser;
import io.yak.ops.sync.runtime.config.JobOptions;
import io.yak.ops.sync.runtime.validation.PipelineDefinitionValidator;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies typed Connector Factory discovery without plugin-owned parameter classes. */
class FactoryUtilContractTest {

    @Test
    void discoversSourceAndSinkFactoriesThroughJavaSpi() {
        FactoryUtil factories = new FactoryUtil();

        assertInstanceOf(JdbcSourceFactory.class, factories.source("JDBC"));
        assertInstanceOf(MySqlCdcSourceFactory.class, factories.source("MYSQL_CDC"));
        assertInstanceOf(JdbcSinkFactory.class, factories.sink("JDBC"));
        assertThrows(IllegalArgumentException.class, () -> factories.source("UNKNOWN"));
        assertThrows(IllegalArgumentException.class, () -> factories.sink("UNKNOWN"));
    }

    @Test
    void resolvesAdditionalSourceByIdentifierAndItsOptionRule() {
        FactoryUtil factories = new FactoryUtil(
                List.of(new JdbcSourceFactory(), new ExternalSourceFactory()),
                List.of(new JdbcSinkFactory()));
        ReadonlyConfig source = ReadonlyConfig.fromMap(java.util.Map.of(
                "type", "EXTERNAL", "datasourceId", "source", "table", "orders"));
        FactoryUtil.validate(source, factories.source("EXTERNAL"), JobOptions.ENDPOINT_RULE);
        assertEquals("EXTERNAL", factories.source(source.get(JobOptions.TYPE)).factoryIdentifier());
        // A registered Connector is not an automatically enabled product solution.
        assertThrows(IllegalArgumentException.class, () -> PipelineDefinitionValidator.validateConfig(
                new YamlPipelineDefinitionParser().parse("""
                        source:
                          type: external
                          datasource-id: source
                          tables: orders
                        sink:
                          type: jdbc
                          datasource-id: target
                        route:
                          - source-table: orders
                            sink-table: copy
                        pipeline:
                          execution.runtime-mode: BATCH
                        """), factories));
    }

    @Test
    void rejectsDuplicateIdentifiersWithoutParameterClassLookup() {
        assertThrows(IllegalStateException.class, () -> new FactoryUtil(
                List.of(new JdbcSourceFactory(), new JdbcSourceFactory()),
                List.of(new JdbcSinkFactory())));
        assertTrue(new FactoryUtil(List.of(new JdbcSourceFactory()), List.of(new JdbcSinkFactory()))
                .source("JDBC")
                .boundedness() == Boundedness.BOUNDED);
    }

    private static final class ExternalSourceFactory implements TableSourceFactory {

        @Override
        public String factoryIdentifier() {
            return "EXTERNAL";
        }

        @Override
        public OptionRule optionRule() {
            return OptionRule.builder().build();
        }

        @Override
        public Boundedness boundedness() {
            return Boundedness.BOUNDED;
        }

        @Override
        public boolean producesChangelog() {
            return false;
        }

        @Override
        public Source<TableRecord, ?, ?> createSource(TableSourceFactoryContext context) {
            throw new UnsupportedOperationException("Source execution is not part of this test");
        }
    }
}
