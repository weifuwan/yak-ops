package io.yak.ops.modeling.ddl.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Executable DDL plan produced by the schema planner. */
public class DdlPlan {

    private final String ddl;
    private final List<String> statements;
    private final boolean noChanges;
    private final List<String> keyFields;
    private final Integer replicationNum;
    private final Integer availableBackendCount;
    private final boolean replicationAuto;
    private final String replicationWarning;

    public DdlPlan(String ddl, List<String> statements, boolean noChanges, List<String> keyFields) {
        this(ddl, statements, noChanges, keyFields, null, null, false, null);
    }

    public DdlPlan(
            String ddl, List<String> statements, boolean noChanges, List<String> keyFields,
            Integer replicationNum, Integer availableBackendCount,
            boolean replicationAuto, String replicationWarning) {
        this.ddl = ddl;
        this.statements = statements == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(statements));
        this.noChanges = noChanges;
        this.keyFields = keyFields == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(keyFields));
        this.replicationNum = replicationNum;
        this.availableBackendCount = availableBackendCount;
        this.replicationAuto = replicationAuto;
        this.replicationWarning = replicationWarning;
    }

    public String getDdl() { return ddl; }
    public List<String> getStatements() { return statements; }
    public boolean isNoChanges() { return noChanges; }
    public List<String> getKeyFields() { return keyFields; }
    public Integer getReplicationNum() { return replicationNum; }
    public Integer getAvailableBackendCount() { return availableBackendCount; }
    public boolean isReplicationAuto() { return replicationAuto; }
    public String getReplicationWarning() { return replicationWarning; }
}
