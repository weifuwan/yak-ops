package org.apache.flink.cdc.runtime.operators.schema.regular;

import org.apache.flink.cdc.common.annotation.Internal;
import org.apache.flink.cdc.common.event.Event;
import org.apache.flink.cdc.common.pipeline.ExistingTableSchemaExpansionMode;
import org.apache.flink.cdc.common.pipeline.RouteMode;
import org.apache.flink.cdc.common.pipeline.SchemaChangeBehavior;
import org.apache.flink.cdc.common.route.RouteRule;
import org.apache.flink.cdc.common.sink.MetadataApplier;
import org.apache.flink.runtime.jobgraph.OperatorID;
import org.apache.flink.runtime.operators.coordination.OperatorCoordinator;
import org.apache.flink.streaming.api.operators.CoordinatedOperatorFactory;
import org.apache.flink.streaming.api.operators.OneInputStreamOperatorFactory;
import org.apache.flink.streaming.api.operators.SimpleOperatorFactory;

import java.time.Duration;
import java.util.List;

/** Factory to create {@link SchemaOperator}. */
@Internal
public class SchemaOperatorFactory extends SimpleOperatorFactory<Event>
        implements CoordinatedOperatorFactory<Event>, OneInputStreamOperatorFactory<Event, Event> {

    private static final long serialVersionUID = 1L;

    private final MetadataApplier metadataApplier;
    private final List<RouteRule> routingRules;
    private final RouteMode routeMode;
    private final SchemaChangeBehavior schemaChangeBehavior;
    private final ExistingTableSchemaExpansionMode existingTableSchemaExpansionMode;
    private final Duration rpcTimeout;

    public SchemaOperatorFactory(
            MetadataApplier metadataApplier,
            List<RouteRule> routingRules,
            RouteMode routeMode,
            Duration rpcTimeout,
            SchemaChangeBehavior schemaChangeBehavior,
            String timezone) {
        this(
                metadataApplier,
                routingRules,
                routeMode,
                rpcTimeout,
                schemaChangeBehavior,
                ExistingTableSchemaExpansionMode.DISABLED,
                timezone);
    }

    public SchemaOperatorFactory(
            MetadataApplier metadataApplier,
            List<RouteRule> routingRules,
            RouteMode routeMode,
            Duration rpcTimeout,
            SchemaChangeBehavior schemaChangeBehavior,
            ExistingTableSchemaExpansionMode existingTableSchemaExpansionMode,
            String timezone) {
        super(
                new SchemaOperator(
                        routingRules, routeMode, rpcTimeout, schemaChangeBehavior, timezone));
        this.metadataApplier = metadataApplier;
        this.routingRules = routingRules;
        this.routeMode = routeMode;
        this.schemaChangeBehavior = schemaChangeBehavior;
        this.existingTableSchemaExpansionMode = existingTableSchemaExpansionMode;
        this.rpcTimeout = rpcTimeout;
    }

    @Override
    public OperatorCoordinator.Provider getCoordinatorProvider(
            String operatorName, OperatorID operatorID) {
        return new SchemaCoordinatorProvider(
                operatorID,
                operatorName,
                metadataApplier,
                routingRules,
                routeMode,
                schemaChangeBehavior,
                existingTableSchemaExpansionMode,
                rpcTimeout);
    }
}
