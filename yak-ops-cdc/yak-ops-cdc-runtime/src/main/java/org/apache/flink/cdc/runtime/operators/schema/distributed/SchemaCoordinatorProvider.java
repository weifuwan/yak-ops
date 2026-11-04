package org.apache.flink.cdc.runtime.operators.schema.distributed;

import org.apache.flink.cdc.common.annotation.Internal;
import org.apache.flink.cdc.common.pipeline.ExistingTableSchemaExpansionMode;
import org.apache.flink.cdc.common.pipeline.RouteMode;
import org.apache.flink.cdc.common.pipeline.SchemaChangeBehavior;
import org.apache.flink.cdc.common.route.RouteRule;
import org.apache.flink.cdc.common.sink.MetadataApplier;
import org.apache.flink.cdc.runtime.operators.schema.common.CoordinatorExecutorThreadFactory;
import org.apache.flink.runtime.jobgraph.OperatorID;
import org.apache.flink.runtime.operators.coordination.OperatorCoordinator;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Provider for {@link SchemaCoordinator}. */
@Internal
public class SchemaCoordinatorProvider implements OperatorCoordinator.Provider {
    private static final long serialVersionUID = 1L;

    private final OperatorID operatorID;
    private final String operatorName;
    private final MetadataApplier metadataApplier;
    private final List<RouteRule> routingRules;
    private final RouteMode routeMode;
    private final SchemaChangeBehavior schemaChangeBehavior;
    private final ExistingTableSchemaExpansionMode existingTableSchemaExpansionMode;
    private final Duration rpcTimeout;

    public SchemaCoordinatorProvider(
            OperatorID operatorID,
            String operatorName,
            MetadataApplier metadataApplier,
            List<RouteRule> routingRules,
            RouteMode routeMode,
            SchemaChangeBehavior schemaChangeBehavior,
            Duration rpcTimeout) {
        this(
                operatorID,
                operatorName,
                metadataApplier,
                routingRules,
                routeMode,
                schemaChangeBehavior,
                ExistingTableSchemaExpansionMode.DISABLED,
                rpcTimeout);
    }

    public SchemaCoordinatorProvider(
            OperatorID operatorID,
            String operatorName,
            MetadataApplier metadataApplier,
            List<RouteRule> routingRules,
            RouteMode routeMode,
            SchemaChangeBehavior schemaChangeBehavior,
            ExistingTableSchemaExpansionMode existingTableSchemaExpansionMode,
            Duration rpcTimeout) {
        this.operatorID = operatorID;
        this.operatorName = operatorName;
        this.metadataApplier = metadataApplier;
        this.routingRules = routingRules;
        this.routeMode = routeMode;
        this.schemaChangeBehavior = schemaChangeBehavior;
        this.existingTableSchemaExpansionMode = existingTableSchemaExpansionMode;
        this.rpcTimeout = rpcTimeout;
    }

    @Override
    public OperatorID getOperatorId() {
        return operatorID;
    }

    @Override
    public OperatorCoordinator create(OperatorCoordinator.Context context) throws Exception {
        CoordinatorExecutorThreadFactory coordinatorThreadFactory =
                new CoordinatorExecutorThreadFactory(
                        "schema-evolution-coordinator", context.getUserCodeClassloader());
        ExecutorService coordinatorExecutor =
                Executors.newSingleThreadExecutor(coordinatorThreadFactory);
        return new SchemaCoordinator(
                operatorName,
                context,
                coordinatorExecutor,
                metadataApplier,
                routingRules,
                routeMode,
                schemaChangeBehavior,
                existingTableSchemaExpansionMode,
                rpcTimeout);
    }
}
