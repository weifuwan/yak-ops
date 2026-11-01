package io.yak.ops.cli;

import io.yak.ops.composer.definition.EndpointOptions;
import io.yak.ops.composer.definition.PipelineDef;
import io.yak.ops.composer.definition.PipelineOptions;
import io.yak.ops.composer.definition.SinkDef;
import io.yak.ops.composer.definition.SourceDef;
import io.yak.ops.composer.definition.TableConfig;
import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.ConfigValidator;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.configuration.rule.RequiredOption;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.database.catalog.JdbcCatalog;
import io.yak.ops.connector.jdbc.database.catalog.JdbcColumnInfo;
import io.yak.ops.connector.jdbc.database.catalog.JdbcTableInfo;
import io.yak.ops.connector.jdbc.database.catalog.factory.JdbcCatalogFactory;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.ColumnProperties;
import io.yak.ops.core.factories.DataSourceFactory;
import io.yak.ops.core.factories.Factory;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceLookupService;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceTable;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.apache.flink.cdc.common.configuration.Configuration;
import org.apache.flink.cdc.common.schema.Selectors;

/** Resolves only the two connections and the table snapshot explicitly supplied to one CLI run. */
final class CliDataSourceLookupService implements DataSourceLookupService {
    static final String SOURCE_ID = "cli-source";
    static final String SINK_ID = "cli-sink";

    private final String workspaceId;
    private final String executionId;
    private final Endpoint source;
    private final Endpoint sink;
    private final Function<JdbcConnectionOptions, JdbcCatalog> catalogs;
    private Set<TableId> sourceTables;
    private Set<TableId> sinkTables;
    private io.yak.ops.composer.definition.DynamicTableSelection dynamicSelection;

    CliDataSourceLookupService(PipelineDef definition, FactoryUtil factories, String workspaceId, String executionId) {
        this(definition, factories, workspaceId, executionId, JdbcCatalogFactory::create);
    }

    CliDataSourceLookupService(
            PipelineDef definition,
            FactoryUtil factories,
            String workspaceId,
            String executionId,
            Function<JdbcConnectionOptions, JdbcCatalog> catalogs) {
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId");
        this.executionId = Objects.requireNonNull(executionId, "executionId");
        this.catalogs = Objects.requireNonNull(catalogs, "catalogs");
        source = new Endpoint(
                factories.source(definition.getSource().getType()),
                definition.getSource().getConfig());
        sink = new Endpoint(
                factories.sink(definition.getSink().getType()),
                definition.getSink().getConfig());
    }

    /** Freezes the initial selection; an explicit dynamic rule retains its published capture scope. */
    PipelineDef resolve(PipelineDef definition) {
        if (sourceTables != null) throw new IllegalStateException("CLI tables have already been admitted");
        String inclusion = required(source.operation, "tables");
        String exclusion = source.operation.get("tables.exclude");
        Selectors includes =
                new Selectors.SelectorsBuilder().includeTables(inclusion).build();
        Selectors excludes = exclusion == null
                ? null
                : new Selectors.SelectorsBuilder().includeTables(exclusion).build();
        List<TableId> selected = discover(includes, excludes);
        if (selected.isEmpty() || selected.size() > PipelineOptions.MAX_TABLES) {
            throw new IllegalArgumentException("CLI table selection must contain between 1 and 100 visible tables");
        }
        Map<String, String> from = new LinkedHashMap<>(source.operation);
        from.remove("tables.exclude");
        from.put("datasource-id", SOURCE_ID);
        from.put(
                "tables",
                String.join(
                        ",",
                        selected.stream()
                                .map(CliDataSourceLookupService::identifier)
                                .toList()));
        from.put("yak.namespace-kind", source.namespaceKind());
        Map<String, String> to = new LinkedHashMap<>(sink.operation);
        to.put("datasource-id", SINK_ID);
        to.put("yak.namespace-kind", sink.namespaceKind());
        PipelineDef resolved = new PipelineDef(
                new SourceDef(
                        definition.getSource().getType(),
                        definition.getSource().getName().orElse(null),
                        Configuration.fromMap(from)),
                new SinkDef(
                        definition.getSink().getType(),
                        definition.getSink().getName().orElse(null),
                        Configuration.fromMap(to),
                        definition.getSink().getIncludedSchemaEvolutionTypes(),
                        definition.getSink().getExistingTableSchemaExpansionMode()),
                definition.getRoute(),
                definition.getTransforms(),
                definition.getUdfs(),
                definition.getModels(),
                Configuration.fromMap(definition.getConfig().toMap()));
        PipelineOptions options = PipelineOptions.from(resolved);
        dynamicSelection = options.dynamicSelection();
        if (options.bounded() && options.incremental()) {
            throw new IllegalArgumentException(
                    "CLI supports full JDBC batches and MySQL CDC; incremental JDBC batches require a durable Task cursor store");
        }
        Set<TableId> targets = new LinkedHashSet<>();
        for (TableConfig table : options.tables()) {
            source.validateCreation(executionId, EndpointOptions.connectorOptions(table.source()));
            sink.validateCreation(executionId, EndpointOptions.connectorOptions(table.sink()));
            TableId target = sink.info.resolveTable(
                    table.sink().get(EndpointOptions.DATABASE),
                    table.sink().get(EndpointOptions.SCHEMA),
                    table.sink().get(EndpointOptions.TABLE));
            requireQualified(target, sink.factory.datasourceType());
            targets.add(target);
        }
        if (sameServer(source.connection, sink.connection) && selected.stream().anyMatch(targets::contains)) {
            throw new IllegalArgumentException(
                    "CLI destinations overlap their source tables on the same database server");
        }
        sourceTables = Set.copyOf(selected);
        sinkTables = Set.copyOf(targets);
        return resolved;
    }

    Map<String, ReadonlyConfig> sourceConnectorOptions() {
        return Map.of(source.factory.identifier(), source.creation);
    }

    Map<String, ReadonlyConfig> sinkConnectorOptions() {
        return Map.of(sink.factory.identifier(), sink.creation);
    }

    @Override
    public DataSourceInfo dataSource(String workspaceId, String datasourceId) {
        return endpoint(workspaceId, datasourceId).info;
    }

    @Override
    public DataSourceTable table(
            String workspaceId, String datasourceId, String database, String schema, String table) {
        return findTable(workspaceId, datasourceId, database, schema, table)
                .orElseThrow(() -> new IllegalArgumentException("CLI table is absent or not visible"));
    }

    @Override
    public Optional<DataSourceTable> findTable(
            String workspaceId, String datasourceId, String database, String schema, String table) {
        Endpoint endpoint = endpoint(workspaceId, datasourceId);
        TableId selected = endpoint.info.resolveTable(database, schema, table);
        Set<TableId> admitted = endpoint == source ? sourceTables : sinkTables;
        boolean dynamic = dynamicSelection != null && (endpoint == sink || dynamicSelection.matches(selected));
        if (admitted == null || !admitted.contains(selected) && !dynamic) {
            throw new IllegalArgumentException("Table is outside the CLI selection");
        }
        try (JdbcCatalog catalog = catalogs.apply(endpoint.connection)) {
            Optional<JdbcTableInfo> physical = catalog.findTable(selected);
            if (physical.isEmpty()) return Optional.empty();
            JdbcTableInfo description = physical.orElseThrow();
            Map<String, String> comments = new HashMap<>();
            Set<String> features = new HashSet<>();
            Map<String, ColumnProperties> properties = new HashMap<>();
            if (!"TABLE".equalsIgnoreCase(description.type()) && !"BASE TABLE".equalsIgnoreCase(description.type())) {
                features.add("NON_TABLE");
            }
            for (JdbcColumnInfo column : catalog.getColumns(selected)) {
                if (column.remarks() != null) comments.put(column.name(), column.remarks());
                if (column.hasDefaultValue()) features.add("DEFAULT");
                if (Boolean.TRUE.equals(column.autoIncrement())) features.add("IDENTITY");
                if (Boolean.TRUE.equals(column.generated())) features.add("GENERATED");
                if (column.autoIncrement() == null || column.generated() == null) features.add("UNKNOWN_GENERATION");
                properties.put(
                        column.name(),
                        new ColumnProperties(
                                column.defaultExpression(),
                                column.autoIncrement(),
                                column.generated(),
                                column.onUpdateExpression(),
                                column.defaultGenerated(),
                                column.nativeType()));
            }
            return Optional.of(new DataSourceTable(
                    endpoint.info.type(),
                    selected,
                    catalog.getTable(selected),
                    1,
                    description.remarks(),
                    comments,
                    features,
                    properties,
                    catalog.getUniqueKeys(selected)));
        } catch (SQLException failure) {
            throw new IllegalStateException("Unable to resolve CLI table metadata", failure);
        }
    }

    @Override
    public ReadonlyConfig connectionOptions(String workspaceId, String datasourceId) {
        return connectionOptions(workspaceId, datasourceId, 1);
    }

    @Override
    public ReadonlyConfig connectionOptions(String workspaceId, String datasourceId, long expectedRevision) {
        Endpoint endpoint = endpoint(workspaceId, datasourceId);
        if (expectedRevision != 1) throw new IllegalStateException("CLI connection revision changed");
        return endpoint.connection.configuration();
    }

    @Override
    public boolean samePhysicalServer(String workspaceId, String firstDatasourceId, String secondDatasourceId) {
        return io.yak.ops.connector.jdbc.database.connection.MySqlServerIdentity.sameServer(
                endpoint(workspaceId, firstDatasourceId).connection.configuration(),
                endpoint(workspaceId, secondDatasourceId).connection.configuration());
    }

    private Endpoint endpoint(String requestedWorkspace, String datasourceId) {
        if (!workspaceId.equals(requestedWorkspace)) throw new IllegalArgumentException("Unknown CLI execution scope");
        if (SOURCE_ID.equals(datasourceId)) return source;
        if (SINK_ID.equals(datasourceId)) return sink;
        throw new IllegalArgumentException("CLI cannot resolve saved platform datasources");
    }

    private List<TableId> discover(Selectors includes, Selectors excludes) {
        Set<TableId> selected = new LinkedHashSet<>();
        try (JdbcCatalog catalog = catalogs.apply(source.connection)) {
            for (String database : catalog.listDatabases()) {
                if ("MYSQL".equals(source.info.type())) {
                    if (!Set.of("information_schema", "mysql", "performance_schema", "sys")
                            .contains(database)) {
                        select(physicalTables(catalog, database, null), includes, excludes, selected);
                    }
                } else if ("ORACLE".equals(source.info.type())) {
                    select(physicalTables(catalog, null, database), includes, excludes, selected);
                } else {
                    for (String schema : catalog.listSchemas(database)) {
                        select(physicalTables(catalog, database, schema), includes, excludes, selected);
                    }
                }
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Unable to discover CLI source tables", failure);
        }
        return selected.stream()
                .sorted(Comparator.comparing(CliDataSourceLookupService::identifier))
                .toList();
    }

    private static List<TableId> physicalTables(JdbcCatalog catalog, String database, String schema)
            throws SQLException {
        return catalog.listTableInfos(database, schema, null, null).stream()
                .filter(table -> "TABLE".equalsIgnoreCase(table.type()) || "BASE TABLE".equalsIgnoreCase(table.type()))
                .map(JdbcTableInfo::tableId)
                .toList();
    }

    private static void select(
            List<TableId> candidates, Selectors includes, Selectors excludes, Set<TableId> selected) {
        for (TableId table : candidates) {
            org.apache.flink.cdc.common.event.TableId id = upstreamId(table);
            if (includes.isMatch(id) && (excludes == null || !excludes.isMatch(id))) {
                // The Composer's admitted table syntax cannot represent literal dots in identifiers.
                for (String part : List.of(nullToEmpty(table.catalog()), nullToEmpty(table.schema()), table.table())) {
                    if (!part.isEmpty() && !part.matches("[A-Za-z0-9_$]+")) {
                        throw new IllegalArgumentException(
                                "CLI selected an identifier unsupported by the Pipeline table contract");
                    }
                }
                selected.add(table);
                if (selected.size() > PipelineOptions.MAX_TABLES) {
                    throw new IllegalArgumentException("CLI table selection exceeds 100 tables");
                }
            }
        }
    }

    private static String identifier(TableId table) {
        return upstreamId(table).identifier();
    }

    private static org.apache.flink.cdc.common.event.TableId upstreamId(TableId table) {
        if (table.catalog() != null && table.schema() != null) {
            return org.apache.flink.cdc.common.event.TableId.tableId(table.catalog(), table.schema(), table.table());
        }
        String namespace = table.catalog() == null ? table.schema() : table.catalog();
        return namespace == null
                ? org.apache.flink.cdc.common.event.TableId.tableId(table.table())
                : org.apache.flink.cdc.common.event.TableId.tableId(namespace, table.table());
    }

    private static void requireQualified(TableId table, String type) {
        if ("MYSQL".equals(type) && (table.catalog() == null || table.schema() != null)
                || "POSTGRE_SQL".equals(type) && (table.catalog() == null || table.schema() == null)
                || "ORACLE".equals(type) && table.schema() == null) {
            throw new IllegalArgumentException("CLI routes must specify the full target database/schema namespace");
        }
    }

    private static boolean sameServer(JdbcConnectionOptions from, JdbcConnectionOptions to) {
        if (from.url().startsWith("jdbc:mysql://") && to.url().startsWith("jdbc:mysql://")) {
            return Objects.equals(from.host(), to.host()) && from.port() == to.port();
        }
        return from.url().split("[?;]", 2)[0].equals(to.url().split("[?;]", 2)[0]);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Required CLI option missing: " + key);
        return value;
    }

    /** Connection credentials never enter the retained, non-secret Pipeline definition. */
    private static final class Endpoint {
        private final Factory factory;
        private final DataSourceInfo info;
        private final JdbcConnectionOptions connection;
        private final Map<String, String> operation;
        private final ReadonlyConfig creation;

        private Endpoint(Factory factory, Configuration config) {
            this.factory = factory;
            Map<String, String> values = new LinkedHashMap<>(config.toMap());
            if (values.containsKey("datasource-id")) {
                throw new IllegalArgumentException(
                        "Standalone CLI uses explicit connections, not saved datasource-id references");
            }
            if (values.containsKey("server-id") || values.containsKey("topic-prefix")) {
                throw new IllegalArgumentException("CLI replication identity is allocated from its execution identity");
            }
            Map<String, Object> connections = new LinkedHashMap<>();
            for (String key : new ArrayList<>(values.keySet())) {
                if (key.startsWith("connection.")) connections.put(key, values.remove(key));
            }
            move(values, connections, "username", JdbcConnectionOptions.USERNAME.key());
            move(values, connections, "password", JdbcConnectionOptions.PASSWORD.key());
            String hostname = values.remove("hostname");
            String portText = values.remove("port");
            if (hostname != null) {
                if (!"MYSQL".equals(factory.datasourceType())
                        || connections.containsKey(JdbcConnectionOptions.URL.key())
                        || connections.containsKey(JdbcConnectionOptions.HOST.key())
                        || connections.containsKey(JdbcConnectionOptions.PORT.key())) {
                    throw new IllegalArgumentException(
                            "hostname/port requires a MySQL connector without connection.url/host/port");
                }
                int port = portText == null ? 3306 : Integer.parseInt(portText);
                if (hostname.isBlank() || port < 1 || port > 65535) {
                    throw new IllegalArgumentException("Invalid MySQL hostname or port");
                }
                try {
                    URI address = new URI("mysql", null, hostname, port, "/", null, null);
                    if (address.getHost() == null) throw new IllegalArgumentException("Invalid MySQL hostname");
                    connections.put(JdbcConnectionOptions.URL.key(), "jdbc:" + address);
                } catch (URISyntaxException failure) {
                    throw new IllegalArgumentException("Invalid MySQL hostname", failure);
                }
                connections.put(JdbcConnectionOptions.HOST.key(), hostname);
                connections.put(JdbcConnectionOptions.PORT.key(), port);
                if (!connections.containsKey(JdbcConnectionOptions.USERNAME.key())
                        || !connections.containsKey(JdbcConnectionOptions.PASSWORD.key())) {
                    throw new IllegalArgumentException("MySQL hostname connections require username and password");
                }
            } else if (portText != null) {
                throw new IllegalArgumentException("port requires hostname");
            }
            ReadonlyConfig connectionConfig = ReadonlyConfig.fromMap(connections);
            ConfigValidator.validateUnknownKeys(connectionConfig, JdbcConnectionOptions.RULE, "CLI connection");
            connection = new JdbcConnectionOptions(connectionConfig);
            String prefix =
                    switch (factory.datasourceType()) {
                        case "MYSQL" -> "jdbc:mysql:";
                        case "POSTGRE_SQL" -> "jdbc:postgresql:";
                        case "ORACLE" -> "jdbc:oracle:";
                        default ->
                            throw new IllegalArgumentException("CLI requires a connector with supported JDBC metadata");
                    };
            if (!connection.url().startsWith(prefix)) {
                throw new IllegalArgumentException("Connection URL does not match the CLI connector datasource type");
            }
            info = new DataSourceInfo(factory.datasourceType(), null, null, 1);
            Set<String> operationKeys = optionKeys(factory.optionRule());
            Set<String> creationKeys = optionKeys(factory.creationOptionRule());
            Map<String, Object> physical = new LinkedHashMap<>();
            for (String key : new ArrayList<>(values.keySet())) {
                if (creationKeys.contains(key) && !operationKeys.contains(key)) {
                    physical.put(key, values.remove(key));
                }
            }
            operation = Map.copyOf(values);
            creation = ReadonlyConfig.fromMap(physical);
        }

        private void validateCreation(String executionId, ReadonlyConfig operationOptions) {
            Map<String, Object> effective = new LinkedHashMap<>(creation.getSourceMap());
            effective.putAll(operationOptions.getSourceMap());
            effective.putAll(connection.configuration().getSourceMap());
            ReadonlyConfig options = ReadonlyConfig.fromMap(effective);
            if (factory instanceof DataSourceFactory sourceFactory) {
                options = sourceFactory.prepareExecutionOptions(options, executionId);
            }
            FactoryUtil.validateCreation(options, factory);
        }

        private String namespaceKind() {
            return "MYSQL".equals(info.type()) ? "DATABASE" : "SCHEMA";
        }

        private static void move(Map<String, String> from, Map<String, Object> to, String source, String target) {
            if (from.containsKey(source)) {
                if (to.containsKey(target))
                    throw new IllegalArgumentException("Conflicting CLI connection option: " + source);
                to.put(target, from.remove(source));
            }
        }

        private static Set<String> optionKeys(OptionRule rule) {
            Set<String> keys = new HashSet<>();
            for (Option<?> option : rule.getOptionalOptions()) keys.add(option.key());
            for (RequiredOption required : rule.getRequiredOptions()) {
                for (Option<?> option : required.options()) keys.add(option.key());
            }
            rule.getConditionRules().forEach(condition -> keys.addAll(optionKeys(condition.optionRule())));
            return keys;
        }
    }
}
