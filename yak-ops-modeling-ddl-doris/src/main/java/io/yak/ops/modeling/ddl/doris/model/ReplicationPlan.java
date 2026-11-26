package io.yak.ops.modeling.ddl.doris.model;

import io.yak.ops.modeling.ddl.model.DdlPlanDetails;

/** Resolved Doris replica count and backend capacity for one CREATE TABLE plan. */
public class ReplicationPlan implements DdlPlanDetails {

    private final int replicationNum;
    private final Integer availableBackendCount;
    private final boolean automatic;
    private final String warning;

    public ReplicationPlan(
            int replicationNum, Integer availableBackendCount, boolean automatic, String warning) {
        this.replicationNum = replicationNum;
        this.availableBackendCount = availableBackendCount;
        this.automatic = automatic;
        this.warning = warning;
    }

    public int getReplicationNum() { return replicationNum; }
    public Integer getAvailableBackendCount() { return availableBackendCount; }
    public boolean isAutomatic() { return automatic; }
    public String getWarning() { return warning; }
}
