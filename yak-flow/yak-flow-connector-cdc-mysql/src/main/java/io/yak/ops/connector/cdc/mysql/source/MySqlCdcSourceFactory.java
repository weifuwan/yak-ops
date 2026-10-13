package io.yak.ops.connector.cdc.mysql.source;

import com.google.auto.service.AutoService;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.Conditions;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.connector.base.factory.TableSourceFactory;
import io.yak.ops.connector.base.factory.TableSourceFactoryContext;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.data.TableRecord;

/** Creates a hybrid MySQL Source from direct connection and replication settings. */
@AutoService(TableSourceFactory.class)
public final class MySqlCdcSourceFactory implements TableSourceFactory {
    @Override
    public String factoryIdentifier() {
        return "MYSQL_CDC";
    }

    @Override
    public OptionRule optionRule() {
        return OptionRule.builder().build();
    }

    @Override
    public OptionRule creationOptionRule() {
        return OptionRule.builder()
                .include(optionRule())
                .include(JdbcConnectionOptions.RULE)
                .include(MySqlCdcSourceOptions.PHYSICAL_RULE)
                .required(
                        MySqlCdcSourceOptions.SERVER_ID, Conditions.greaterOrEqual(MySqlCdcSourceOptions.SERVER_ID, 1))
                .required(MySqlCdcSourceOptions.TOPIC_PREFIX, Conditions.notBlank(MySqlCdcSourceOptions.TOPIC_PREFIX))
                .build();
    }

    @Override
    public Boundedness boundedness() {
        return Boundedness.CONTINUOUS_UNBOUNDED;
    }

    @Override
    public boolean producesChangelog() {
        return true;
    }

    @Override
    public void validateDatasource(String datasourceType) {
        if (!"MYSQL".equals(datasourceType)) {
            throw new IllegalArgumentException("MySQL CDC requires a MySQL datasource");
        }
    }

    @Override
    public Source<TableRecord, ?, ?> createSource(TableSourceFactoryContext context) {
        ReadonlyConfig options = context.options();
        if (options.get(JdbcConnectionOptions.SSH_ENABLED)) {
            throw new UnsupportedOperationException("MySQL CDC requires a direct MySQL connection");
        }
        FactoryUtil.validateCreation(options, this);
        JdbcConnectionOptions connection = new JdbcConnectionOptions(options);
        if (!connection.url().startsWith("jdbc:mysql://")) {
            throw new IllegalArgumentException("MySQL CDC requires a MySQL connection URL");
        }
        if (connection.host() == null) {
            throw new UnsupportedOperationException("MySQL CDC requires a direct MySQL connection");
        }
        return MySqlCdcSource.builder()
                .hostname(connection.host())
                .port(connection.port())
                .username(connection.username())
                .password(connection.password())
                .serverId(options.get(MySqlCdcSourceOptions.SERVER_ID))
                .topicPrefix(options.get(MySqlCdcSourceOptions.TOPIC_PREFIX))
                .table(context.table(), context.schema())
                .queueCapacity(options.get(MySqlCdcSourceOptions.QUEUE_CAPACITY))
                .buildHybrid(options.get(MySqlCdcSourceOptions.SNAPSHOT_CHUNK_SIZE));
    }
}
