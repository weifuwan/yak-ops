package io.yak.ops.connector.jdbc.source;

import com.google.auto.service.AutoService;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.connector.base.factory.TableSourceFactory;
import io.yak.ops.connector.base.factory.TableSourceFactoryContext;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.JdbcSourceOptions;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.data.TableRecord;
import java.util.List;

/** Creates bounded JDBC Sources from effective connection and reader settings. */
@AutoService(TableSourceFactory.class)
public final class JdbcSourceFactory implements TableSourceFactory {
    @Override
    public String factoryIdentifier() {
        return "JDBC";
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
                .include(JdbcSourceOptions.RULE)
                .build();
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
        return createSource(List.of(context));
    }

    @Override
    public Source<TableRecord, ?, ?> createSource(List<TableSourceFactoryContext> contexts) {
        if (contexts.isEmpty()) throw new IllegalArgumentException("Source needs tables");
        var first = contexts.getFirst();
        for (var context : contexts) {
            FactoryUtil.validateCreation(context.options(), this);
            if (!context.options().equals(first.options())) {
                throw new IllegalArgumentException("JDBC tables must share effective Source settings");
            }
        }
        var connection = new JdbcConnectionOptions(first.options());
        return new JdbcSource(
                new io.yak.ops.connector.jdbc.database.connection.DefaultJdbcConnectionProvider(connection),
                connection.url(),
                contexts.stream().map(TableSourceFactoryContext::table).toList(),
                JdbcSourceOptions.configuration(first.options()),
                java.util.Map.of(),
                first.metrics());
    }
}
