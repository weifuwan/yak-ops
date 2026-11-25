package io.yak.ops.modeling.ddl.model;

import io.yak.ops.dao.entity.enums.DorisDataModelEnum;
import io.yak.ops.dao.entity.enums.DorisPublishModeEnum;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Complete input required to plan one Doris publish operation. */
public class DorisDdlRequest {

    private final DdlTableDefinition table;
    private final DorisDataModelEnum tableModel;
    private final DorisPublishModeEnum publishMode;
    private final DorisDataModelEnum previousTableModel;
    private final List<String> previousKeyFields;
    private final Integer replicationNum;
    private final String previousPhysicalSchema;

    public DorisDdlRequest(
            DdlTableDefinition table,
            DorisDataModelEnum tableModel,
            DorisPublishModeEnum publishMode,
            DorisDataModelEnum previousTableModel,
            List<String> previousKeyFields) {
        this(table, tableModel, publishMode, previousTableModel, previousKeyFields, null, null, false);
    }

    public DorisDdlRequest(
            DdlTableDefinition table,
            DorisDataModelEnum tableModel,
            DorisPublishModeEnum publishMode,
            DorisDataModelEnum previousTableModel,
            List<String> previousKeyFields,
            Integer replicationNum) {
        this(table, tableModel, publishMode, previousTableModel, previousKeyFields,
                replicationNum, null);
    }

    public DorisDdlRequest(
            DdlTableDefinition table,
            DorisDataModelEnum tableModel,
            DorisPublishModeEnum publishMode,
            DorisDataModelEnum previousTableModel,
            List<String> previousKeyFields,
            Integer replicationNum,
            String previousPhysicalSchema) {
        this.table = table;
        this.tableModel = tableModel;
        this.publishMode = publishMode;
        this.previousTableModel = previousTableModel;
        this.previousKeyFields = previousKeyFields == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(previousKeyFields));
        this.replicationNum = replicationNum;
        this.previousPhysicalSchema = previousPhysicalSchema;
    }

    public DdlTableDefinition getTable() { return table; }
    public DorisDataModelEnum getTableModel() { return tableModel; }
    public DorisPublishModeEnum getPublishMode() { return publishMode; }
    public DorisDataModelEnum getPreviousTableModel() { return previousTableModel; }
    public List<String> getPreviousKeyFields() { return previousKeyFields; }
    public Integer getReplicationNum() { return replicationNum; }
    public String getPreviousPhysicalSchema() { return previousPhysicalSchema; }
}
