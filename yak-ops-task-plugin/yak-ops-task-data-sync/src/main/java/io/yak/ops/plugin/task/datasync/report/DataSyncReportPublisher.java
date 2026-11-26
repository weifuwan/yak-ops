package io.yak.ops.plugin.task.datasync.report;

import io.yak.ops.extract.master.transportor.DataSyncReportAck;
import io.yak.ops.extract.master.transportor.DataSyncReportRequest;

/** Sends one versioned CDC metrics report to the Master. */
public interface DataSyncReportPublisher {

    DataSyncReportAck publish(DataSyncReportRequest request) throws Exception;
}
