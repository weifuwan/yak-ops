package io.yak.ops.plugin.datasource.api.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.business.datasource.impl.H2TestDataSourcePlugin;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.spi.SpiRegistry;
import java.sql.Connection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Verifies SPI discovery, registered aliases and descriptor conflicts. */
class DataSourcePluginRegistryTest {

    @Test
    void loadsTestProviderThroughSpi() {
        assertSame(H2TestDataSourcePlugin.class, DataSourcePluginRegistry.get("TEST_H2").getClass());
        DataSourcePluginRegistry.load();
    }

    @Test
    void resolvesAliasesWithoutChangingGlobalRegistration() {
        DataSourcePlugin provider = provider("POSTGRE_SQL", Set.of("POSTGRES", "POSTGRESQL"));
        SpiRegistry<DataSourcePlugin> registered = DataSourcePluginRegistry.registerProviders(List.of(provider));

        assertSame(provider, registered.get("POSTGRE_SQL"));
        assertSame(provider, registered.get("POSTGRES"));
        assertSame(provider, registered.get("POSTGRESQL"));
        assertEquals(3, registered.types().size());
        assertThrows(UnsupportedOperationException.class, () -> registered.types().add("MYSQL"));
        assertThrows(NoSuchElementException.class, () -> DataSourcePluginRegistry.get("UNREGISTERED_TYPE"));
    }

    @Test
    void rejectsDuplicateAliases() {
        assertThrows(
                IllegalStateException.class,
                () -> DataSourcePluginRegistry.registerProviders(List.of(
                        provider("MYSQL", Set.of("DATABASE")),
                        provider("ORACLE", Set.of("DATABASE")))));
    }

    @Test
    void rejectsDuplicateTypes() {
        assertThrows(
                IllegalStateException.class,
                () -> DataSourcePluginRegistry.registerProviders(List.of(
                        provider("MYSQL", Set.of()), provider("MYSQL", Set.of()))));
    }

    @Test
    void rejectsIncompatibleProviderVersion() {
        DataSourcePlugin plugin = new DataSourcePlugin() {
            @Override
            public String type() {
                return "MYSQL";
            }

            @Override
            public DataSourcePluginDescriptor descriptor() {
                return new DataSourcePluginDescriptor("MYSQL", Set.of(), "999", Set.of(), Set.of());
            }

            @Override
            public OptionRule optionRule() {
                return OptionRule.builder().build();
            }

            @Override
            public DataSourceConnection parseConnection(ReadonlyConfig configuration) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void testConnection(DataSourceConnection connection, int timeoutSeconds) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ReadonlyConfig connectionOptions(DataSourceConnection connection, int timeoutSeconds) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Connection openConnection(DataSourceConnection connection, int timeoutSeconds) {
                throw new UnsupportedOperationException();
            }
        };
        assertThrows(IllegalStateException.class, () -> DataSourcePluginRegistry.registerProviders(List.of(plugin)));
    }

    private DataSourcePlugin provider(String type, Set<String> aliases) {
        return new DataSourcePlugin() {
            @Override
            public String type() {
                return type;
            }

            @Override
            public DataSourcePluginDescriptor descriptor() {
                return new DataSourcePluginDescriptor(
                        type, aliases, DataSourcePluginDescriptor.CURRENT_API_VERSION, Set.of(), Set.of());
            }

            @Override
            public OptionRule optionRule() {
                return OptionRule.builder().build();
            }

            @Override
            public DataSourceConnection parseConnection(ReadonlyConfig configuration) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void testConnection(DataSourceConnection connection, int timeoutSeconds) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ReadonlyConfig connectionOptions(DataSourceConnection connection, int timeoutSeconds) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Connection openConnection(DataSourceConnection connection, int timeoutSeconds) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
