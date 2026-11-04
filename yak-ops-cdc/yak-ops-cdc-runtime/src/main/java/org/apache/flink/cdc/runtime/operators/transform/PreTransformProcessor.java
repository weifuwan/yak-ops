package org.apache.flink.cdc.runtime.operators.transform;

import org.apache.flink.cdc.common.data.RecordData;
import org.apache.flink.cdc.common.data.binary.BinaryRecordData;
import org.apache.flink.cdc.common.event.CreateTableEvent;
import org.apache.flink.cdc.common.schema.Column;
import org.apache.flink.cdc.common.schema.Schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The processor of pre-transform projection in {@link PreTransformOperator}.
 *
 * <p>A pre-transform projection processor handles:
 *
 * <ul>
 *   <li>CreateTableEvent: removes unused (unreferenced) columns from given schema.
 *   <li>SchemaChangeEvent: update the columns of TransformProjection.
 *   <li>DataChangeEvent: omits unused columns in data row.
 * </ul>
 */
public class PreTransformProcessor {
    private final PreTransformChangeInfo tableChangeInfo;

    public PreTransformProcessor(PreTransformChangeInfo tableChangeInfo) {
        this.tableChangeInfo = tableChangeInfo;
    }

    /**
     * This method analyses (directly and indirectly) referenced columns, and peels unused columns
     * from schema. For example, given original schema with columns (A, B, C, D, E) with projection
     * rule (A, B + 1 as newB) and filtering rule (C > 0), a peeled schema containing (A, B, C) only
     * will be sent to downstream, and (D, E) column along with corresponding data will be trimmed.
     */
    public CreateTableEvent preTransformCreateTableEvent(CreateTableEvent createTableEvent) {
        Schema schema =
                createTableEvent
                        .getSchema()
                        .copy(tableChangeInfo.getPreTransformedSchema().getColumns());
        return new CreateTableEvent(createTableEvent.tableId(), schema);
    }

    public BinaryRecordData processFillDataField(RecordData data) {
        List<Object> valueList = new ArrayList<>();
        List<Column> columns = tableChangeInfo.getPreTransformedSchema().getColumns();
        Map<String, RecordData.FieldGetter> sourceFieldGettersMap =
                tableChangeInfo.getSourceFieldGettersMap();
        for (Column column : columns) {
            RecordData.FieldGetter fieldGetter = sourceFieldGettersMap.get(column.getName());
            valueList.add(fieldGetter.getFieldOrNull(data));
        }
        return tableChangeInfo
                .getPreTransformedRecordDataGenerator()
                .generate(valueList.toArray(new Object[0]));
    }
}
