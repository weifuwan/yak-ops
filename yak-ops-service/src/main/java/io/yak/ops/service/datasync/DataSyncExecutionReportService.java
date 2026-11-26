package io.yak.ops.service.datasync;

import io.yak.ops.extract.master.transportor.DataSyncReportAck;
import io.yak.ops.extract.master.transportor.DataSyncReportRequest;
import io.yak.ops.service.datasync.model.DataSyncExecutionReportDetail;

public interface DataSyncExecutionReportService {

    DataSyncReportAck acceptReport(DataSyncReportRequest request);

    DataSyncExecutionReportDetail getLatestReport(int taskInstanceId);
}
