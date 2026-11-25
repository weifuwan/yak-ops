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

    public DdlPlan(String ddl, List<String> statements, boolean noChanges, List<String> keyFields) {
        this.ddl = ddl;
        this.statements = statements == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(statements));
        this.noChanges = noChanges;
        this.keyFields = keyFields == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(keyFields));
    }

    public String getDdl() { return ddl; }
    public List<String> getStatements() { return statements; }
    public boolean isNoChanges() { return noChanges; }
    public List<String> getKeyFields() { return keyFields; }
}
