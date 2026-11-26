package io.yak.ops.extract.master.transportor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Master acknowledgement for a CDC report. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSyncReportAck {

    private boolean accepted;

    /** ACCEPTED, DUPLICATE, STALE, FINAL_ALREADY_ACCEPTED, QUEUED or REJECTED. */
    private String result;

    private long latestSequence;

    private String message;

    public static DataSyncReportAck accepted(String result, long latestSequence) {
        return DataSyncReportAck.builder()
                .accepted(true)
                .result(result)
                .latestSequence(latestSequence)
                .build();
    }

    public static DataSyncReportAck rejected(String result, long latestSequence, String message) {
        return DataSyncReportAck.builder()
                .accepted(false)
                .result(result)
                .latestSequence(latestSequence)
                .message(message)
                .build();
    }
}
