package io.yak.ops.plugin.task.datasync.report;

import io.yak.ops.extract.base.client.Clients;
import io.yak.ops.extract.master.IDataSyncReportListener;
import io.yak.ops.extract.master.transportor.DataSyncReportAck;
import io.yak.ops.extract.master.transportor.DataSyncReportRequest;
import io.yak.ops.plugin.task.api.TaskExecutionContext;

/** Worker-to-Master RPC publisher for CDC execution reports. */
public class RpcDataSyncReportPublisher extends AbstractDataSyncReportPublisher {

    private final TaskExecutionContext context;

    public RpcDataSyncReportPublisher(TaskExecutionContext context) {
        this.context = context;
    }

    @Override
    protected DataSyncReportAck doPublish(DataSyncReportRequest request) {
        String masterAddress = context.getWorkflowInstanceHost();
        if (masterAddress == null || masterAddress.trim().isEmpty()) {
            throw new IllegalStateException("Master address is missing from task execution context");
        }
        return Clients.withService(IDataSyncReportListener.class)
                .withHost(masterAddress)
                .publishReport(request);
    }
}
