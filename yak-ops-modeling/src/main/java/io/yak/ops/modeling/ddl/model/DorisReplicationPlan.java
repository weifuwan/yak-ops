package io.yak.ops.modeling.ddl.model;

/** Resolved replica count and the backend-capacity information used for one CREATE TABLE plan. */
public class DorisReplicationPlan {

    private final int replicationNum;
    private final Integer availableBackendCount;
    private final boolean automatic;
    private final String warning;

    public DorisReplicationPlan(
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
