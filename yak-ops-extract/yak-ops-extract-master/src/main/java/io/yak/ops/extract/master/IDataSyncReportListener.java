package io.yak.ops.extract.master;

import io.yak.ops.extract.base.RpcMethod;
import io.yak.ops.extract.base.RpcService;
import io.yak.ops.extract.master.transportor.DataSyncReportAck;
import io.yak.ops.extract.master.transportor.DataSyncReportRequest;

/** Dedicated Worker-to-Master endpoint for CDC execution metrics. */
@RpcService
public interface IDataSyncReportListener {

    @RpcMethod
    DataSyncReportAck publishReport(DataSyncReportRequest request);
}
