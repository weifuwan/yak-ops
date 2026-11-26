package io.yak.ops.server.master.rpc;

import io.yak.ops.extract.master.IDataSyncReportListener;
import io.yak.ops.extract.master.transportor.DataSyncReportAck;
import io.yak.ops.extract.master.transportor.DataSyncReportRequest;
import io.yak.ops.service.datasync.DataSyncExecutionReportService;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Receives Worker CDC reports and delegates persistence to the shared service layer. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSyncReportListenerImpl implements IDataSyncReportListener {

    private final DataSyncExecutionReportService reportService;

    @Override
    public DataSyncReportAck publishReport(DataSyncReportRequest request) {
        DataSyncReportAck ack = reportService.acceptReport(request);
        if (!ack.isAccepted()) {
            log.warn("Rejected CDC report, executionId={}, sequence={}, result={}, message={}",
                    request == null ? null : request.getExecutionId(),
                    request == null ? null : request.getSequence(),
                    ack.getResult(),
                    ack.getMessage());
        }
        return ack;
    }
}
