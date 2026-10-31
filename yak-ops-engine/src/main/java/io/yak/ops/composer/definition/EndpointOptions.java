package io.yak.ops.composer.definition;

import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.Options;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.Conditions;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.core.data.TableId;
import java.util.List;
import java.util.Map;

/** Persisted DATA_SYNC job keys and shared Connector endpoint identity. */
public final class EndpointOptions {

    public static final Option<String> TYPE = Options.key("type").stringType().noDefaultValue();
    public static final Option<String> DATASOURCE_ID =
            Options.key("datasourceId").stringType().noDefaultValue();
    public static final Option<String> DATABASE =
            Options.key("database").stringType().noDefaultValue();
    public static final Option<String> SCHEMA =
            Options.key("schema").stringType().noDefaultValue();
    public static final Option<String> TABLE = Options.key("table").stringType().noDefaultValue();

    public static final OptionRule ENDPOINT_RULE = OptionRule.builder()
            .required(TYPE, Conditions.notBlank(TYPE))
            .required(
                    DATASOURCE_ID,
                    Conditions.notBlank(DATASOURCE_ID).and(Conditions.matches(DATASOURCE_ID, "(?s)^.{1,64}$")))
            .required(TABLE, Conditions.notBlank(TABLE).and(Conditions.matches(TABLE, "(?s)^.{1,128}$")))
            .optional(DATABASE, Conditions.matches(DATABASE, "(?s)^.{0,128}$"))
            .optional(SCHEMA, Conditions.matches(SCHEMA, "(?s)^.{0,128}$"))
            .build();

    /** Returns Connector operation settings without platform routing and datasource references. */
    public static ReadonlyConfig connectorOptions(ReadonlyConfig endpoint) {
        Map<String, Object> values = new java.util.LinkedHashMap<>(endpoint.getSourceMap());
        for (Option<?> option : List.of(TYPE, DATASOURCE_ID, DATABASE, SCHEMA, TABLE)) {
            values.remove(option.key());
        }
        return ReadonlyConfig.fromMap(values);
    }

    /** Table identity as selected by the definition, before datasource defaults complete its namespace. */
    public static TableId selectedTable(ReadonlyConfig endpoint) {
        return new TableId(endpoint.get(DATABASE), endpoint.get(SCHEMA), endpoint.get(TABLE));
    }

    private EndpointOptions() {}
}
