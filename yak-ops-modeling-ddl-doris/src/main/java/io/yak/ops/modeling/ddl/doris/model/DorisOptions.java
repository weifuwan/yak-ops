package io.yak.ops.modeling.ddl.doris.model;

import io.yak.ops.modeling.ddl.model.DdlOptions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Doris-only table model, replica and historical physical schema options. */
public class DorisOptions implements DdlOptions {

    private final DorisTableModel tableModel;
    private final DorisTableModel previousTableModel;
    private final List<String> previousKeyFields;
    private final Integer replicationNum;
    private final String previousPhysicalSchema;

    public DorisOptions(
            DorisTableModel tableModel,
            DorisTableModel previousTableModel,
            List<String> previousKeyFields,
            Integer replicationNum,
            String previousPhysicalSchema) {
        this.tableModel = tableModel;
        this.previousTableModel = previousTableModel;
        this.previousKeyFields = previousKeyFields == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(previousKeyFields));
        this.replicationNum = replicationNum;
        this.previousPhysicalSchema = previousPhysicalSchema;
    }

    public DorisTableModel getTableModel() { return tableModel; }
    public DorisTableModel getPreviousTableModel() { return previousTableModel; }
    public List<String> getPreviousKeyFields() { return previousKeyFields; }
    public Integer getReplicationNum() { return replicationNum; }
    public String getPreviousPhysicalSchema() { return previousPhysicalSchema; }
}
