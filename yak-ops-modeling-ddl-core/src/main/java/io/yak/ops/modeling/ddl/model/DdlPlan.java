package io.yak.ops.modeling.ddl.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Database-neutral executable DDL plan with an optional dialect-specific detail object. */
public class DdlPlan {

    private final String ddl;
    private final List<String> statements;
    private final boolean noChanges;
    private final List<String> keyFields;
    private final boolean targetTableExists;
    private final List<String> addedColumns;
    private final List<String> droppedColumns;
    private final boolean requiresConfirmation;
    private final boolean schemaDrift;
    private final String physicalSchema;
    private final DdlPlanDetails details;

    public DdlPlan(
            String ddl,
            List<String> statements,
            boolean noChanges,
            List<String> keyFields,
            boolean targetTableExists,
            List<String> addedColumns,
            List<String> droppedColumns,
            boolean requiresConfirmation,
            boolean schemaDrift,
            String physicalSchema,
            DdlPlanDetails details) {
        this.ddl = ddl;
        this.statements = immutableCopy(statements);
        this.noChanges = noChanges;
        this.keyFields = immutableCopy(keyFields);
        this.targetTableExists = targetTableExists;
        this.addedColumns = immutableCopy(addedColumns);
        this.droppedColumns = immutableCopy(droppedColumns);
        this.requiresConfirmation = requiresConfirmation;
        this.schemaDrift = schemaDrift;
        this.physicalSchema = physicalSchema;
        this.details = details;
    }

    public DdlPlan(String ddl, List<String> statements, boolean noChanges, List<String> keyFields) {
        this(ddl, statements, noChanges, keyFields, false,
                Collections.<String>emptyList(), Collections.<String>emptyList(),
                false, false, null, null);
    }

    private List<String> immutableCopy(List<String> values) {
        return values == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(values));
    }

    public String getDdl() { return ddl; }
    public List<String> getStatements() { return statements; }
    public boolean isNoChanges() { return noChanges; }
    public List<String> getKeyFields() { return keyFields; }
    public boolean isTargetTableExists() { return targetTableExists; }
    public List<String> getAddedColumns() { return addedColumns; }
    public List<String> getDroppedColumns() { return droppedColumns; }
    public boolean isRequiresConfirmation() { return requiresConfirmation; }
    public boolean isSchemaDrift() { return schemaDrift; }
    public String getPhysicalSchema() { return physicalSchema; }
    public DdlPlanDetails getDetails() { return details; }

    public <T extends DdlPlanDetails> T getDetails(Class<T> detailsType) {
        if (details == null || !detailsType.isInstance(details)) {
            return null;
        }
        return detailsType.cast(details);
    }
}
