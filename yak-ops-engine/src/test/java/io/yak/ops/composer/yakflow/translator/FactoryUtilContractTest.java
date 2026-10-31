package io.yak.ops.composer.yakflow.translator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.core.factories.DataSourceFactory;
import io.yak.ops.connector.cdc.mysql.factory.MySqlDataSourceFactory;
import io.yak.ops.connector.jdbc.sink.MySqlDataSinkFactory;
import io.yak.ops.connector.jdbc.source.JdbcDataSourceFactory;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.cli.parser.YamlPipelineDefinitionParser;
import io.yak.ops.composer.definition.EndpointOptions;
import io.yak.ops.composer.validation.PipelineDefinitionValidator;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies typed Connector Factory discovery without plugin-owned parameter classes. */
class FactoryUtilContractTest {

    @Test
    void discoversSourceAndSinkFactoriesThroughJavaSpi() {
        FactoryUtil factories = new FactoryUtil();

        assertInstanceOf(JdbcDataSourceFactory.class, factories.source("jdbc-mysql"));
        assertInstanceOf(MySqlDataSourceFactory.class, factories.source("mysql"));
        assertInstanceOf(MySqlDataSinkFactory.class, factories.sink("jdbc-mysql"));
        assertThrows(IllegalArgumentException.class, () -> factories.source("UNKNOWN"));
        assertThrows(IllegalArgumentException.class, () -> factories.sink("UNKNOWN"));
    }

    @Test
    void resolvesAdditionalSourceByIdentifierAndItsOptionRule() {
        FactoryUtil factories = new FactoryUtil(
                List.of(new io.yak.ops.connector.jdbc.source.MySqlDataSourceFactory(), new ExternalSourceFactory()),
                List.of(new MySqlDataSinkFactory()));
        ReadonlyConfig source = ReadonlyConfig.fromMap(java.util.Map.of(
                "type", "external", "datasourceId", "source", "table", "orders"));
        FactoryUtil.validate(source, factories.source("external"), EndpointOptions.ENDPOINT_RULE);
        assertEquals("external", factories.source(source.get(EndpointOptions.TYPE)).identifier());
        assertEquals("external", PipelineDefinitionValidator.validateConfig(
                new YamlPipelineDefinitionParser().parse("""
                        source:
                          type: external
                          datasource-id: source
                          tables: orders
                        sink:
                          type: jdbc-mysql
                          datasource-id: target
                          write.mode: APPEND
                        route:
                          - source-table: orders
                            sink-table: copy
                        pipeline:
                          execution.runtime-mode: BATCH
                        """), factories).sourceIdentifier());
    }

    @Test
    void rejectsDuplicateIdentifiersWithoutParameterClassLookup() {
        assertThrows(IllegalStateException.class, () -> new FactoryUtil(
                List.of(new io.yak.ops.connector.jdbc.source.MySqlDataSourceFactory(), new io.yak.ops.connector.jdbc.source.MySqlDataSourceFactory()),
                List.of(new MySqlDataSinkFactory())));
        assertTrue(new FactoryUtil(List.of(new io.yak.ops.connector.jdbc.source.MySqlDataSourceFactory()), List.of(new MySqlDataSinkFactory()))
                .source("jdbc-mysql")
                .boundedness(ReadonlyConfig.fromMap(java.util.Map.of())) == Boundedness.BOUNDED);
    }

    private static final class ExternalSourceFactory implements DataSourceFactory {

        @Override
        public String identifier() {
            return "external";
        }

        @Override
        public String datasourceType() {
            return "MYSQL";
        }

        @Override
        public OptionRule optionRule() {
            return OptionRule.builder().build();
        }

        @Override
        public Boundedness boundedness(ReadonlyConfig options) {
            return Boundedness.BOUNDED;
        }

        @Override
        public boolean producesChangelog() {
            return false;
        }

        @Override
        public io.yak.ops.core.source.DataSource createDataSource(io.yak.ops.core.factories.Factory.Context context) {
            throw new UnsupportedOperationException("Source execution is not part of this test");
        }
    }
}
