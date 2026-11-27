package io.yak.ops.service.datasync;

import io.yak.ops.extract.master.transportor.DataSyncReportAck;
import io.yak.ops.extract.master.transportor.DataSyncReportRequest;
import io.yak.ops.service.datasync.model.DataSyncExecutionReportDetail;

import java.util.List;
import java.util.Map;

public interface DataSyncExecutionReportService {

    DataSyncReportAck acceptReport(DataSyncReportRequest request);

    DataSyncExecutionReportDetail getLatestReport(int taskInstanceId);

    /** Returns the latest stage observation for each supplied task instance in one batch query. */
    Map<Integer, String> getLatestExecutionReports(List<Integer> taskInstanceIds);
}
