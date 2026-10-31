package io.yak.ops.composer.yakflow.translator;

import io.yak.ops.composer.definition.TableBinding;
import io.yak.ops.core.api.dag.Transformation;
import io.yak.ops.core.api.operators.Collector;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.ChangeEvent;
import io.yak.ops.core.event.DataChangeEvent;
import io.yak.ops.core.event.Event;
import io.yak.ops.core.event.SchemaChangeEvent;
import io.yak.ops.flow.runtime.operators.OneInputStreamOperator;
import io.yak.ops.flow.runtime.transformations.OneInputTransformation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Routes the coordinated schema and data event stream through the admitted table bindings. */
public final class SchemaTranslator {
    public Transformation<Event> translate(
            Transformation<Event> input,
            List<TableBinding> tables,
            int parallelism,
            String definitionId,
            String operatorUid) {
        Map<TableId, TableId> routes = new LinkedHashMap<>();
        for (TableBinding table : tables) {
            if (routes.putIfAbsent(table.source().table(), table.targetTable()) != null) {
                throw new IllegalArgumentException("Duplicate source route");
            }
        }
        Map<TableId, TableId> admitted = Map.copyOf(routes);
        OneInputTransformation<Event, Event> schema = new OneInputTransformation<>(
                input, "Schema", () -> new SchemaOperator(admitted), Event.class, parallelism);
        schema.setUid(
                operatorUid == null
                        ? new OperatorUidGenerator("data-sync-schema").generateUid(definitionId)
                        : operatorUid);
        return schema;
    }

    private static final class SchemaOperator implements OneInputStreamOperator<Event, Event> {
        private final Map<TableId, TableId> routes;

        private SchemaOperator(Map<TableId, TableId> routes) {
            this.routes = routes;
        }

        @Override
        public void processElement(Event event, Collector<Event> output) throws Exception {
            if (!(event instanceof ChangeEvent change))
                throw new UnsupportedOperationException("Unsupported schema input event");
            TableId target = routes.get(change.tableId());
            if (target == null) throw new IllegalStateException("Event references an unadmitted source table");
            if (event instanceof SchemaChangeEvent schema) output.collect(schema.copy(target));
            else if (event instanceof DataChangeEvent data) output.collect(DataChangeEvent.route(data, target));
            else throw new UnsupportedOperationException("Unsupported routed event type");
        }
    }
}
