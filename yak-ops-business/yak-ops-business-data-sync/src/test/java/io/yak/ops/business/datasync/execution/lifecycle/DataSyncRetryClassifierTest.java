package io.yak.ops.business.datasync.execution.lifecycle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.common.bean.vo.datasync.DataSyncDefinitionSnapshotVO;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.enums.datasync.DataSyncWriteMode;
import java.net.ConnectException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLTransientConnectionException;
import org.junit.jupiter.api.Test;

class DataSyncRetryClassifierTest {

    private final DataSyncRetryClassifier classifier = new DataSyncRetryClassifier();

    @Test
    void shouldRetryTransientPlannerFailureBeforeOfflineRuntimeStarts() {
        DataSyncRetryAssessment assessment =
                classifier.classify(
                        snapshot(DataSyncType.OFFLINE, DataSyncWriteMode.APPEND),
                        new SQLTransientConnectionException("connection reset", "08006"),
                        false,
                        0);

        assertTrue(assessment.retryable());
    }

    @Test
    void shouldBlockAutomaticReplayAfterAppendRuntimeStarted() {
        DataSyncRetryAssessment assessment =
                classifier.classify(
                        snapshot(DataSyncType.OFFLINE, DataSyncWriteMode.APPEND),
                        new SQLTransientConnectionException("connection reset", "08006"),
                        true,
                        0);

        assertFalse(assessment.retryable());
    }

    @Test
    void shouldBlockAutomaticReplayAfterOverwriteRuntimeStarted() {
        DataSyncRetryAssessment assessment =
                classifier.classify(
                        snapshot(DataSyncType.OFFLINE, DataSyncWriteMode.OVERWRITE),
                        new SQLTransientConnectionException("connection reset", "08006"),
                        true,
                        100);

        assertFalse(assessment.retryable());
    }

    @Test
    void shouldRetryTransientUpsertFailureAfterRuntimeStarted() {
        DataSyncRetryAssessment assessment =
                classifier.classify(
                        snapshot(DataSyncType.OFFLINE, DataSyncWriteMode.UPSERT),
                        new SQLTransientConnectionException("connection reset", "08006"),
                        true,
                        100);

        assertTrue(assessment.retryable());
    }

    @Test
    void shouldRejectPermanentIntegrityFailure() {
        DataSyncRetryAssessment assessment =
                classifier.classify(
                        snapshot(DataSyncType.OFFLINE, DataSyncWriteMode.UPSERT),
                        new SQLIntegrityConstraintViolationException("duplicate", "23000"),
                        true,
                        0);

        assertFalse(assessment.retryable());
    }

    @Test
    void shouldRetryRealtimeNetworkFailure() {
        DataSyncRetryAssessment assessment =
                classifier.classify(
                        snapshot(DataSyncType.REALTIME, DataSyncWriteMode.APPEND),
                        new ConnectException("connection refused"),
                        true,
                        200);

        assertTrue(assessment.retryable());
    }

    @Test
    void shouldNotRetryUnknownFailure() {
        DataSyncRetryAssessment assessment =
                classifier.classify(
                        snapshot(DataSyncType.REALTIME, DataSyncWriteMode.APPEND),
                        new IllegalStateException("unknown"),
                        true,
                        0);

        assertFalse(assessment.retryable());
    }

    @Test
    void shouldRetryUnexpectedRealtimeSourceCompletion() {
        assertTrue(classifier
                .classifyUnexpectedContinuousEnd(snapshot(DataSyncType.REALTIME, DataSyncWriteMode.APPEND))
                .retryable());
    }

    private DataSyncDefinitionSnapshotVO snapshot(DataSyncType syncType, DataSyncWriteMode writeMode) {
        DataSyncDefinitionSnapshotVO snapshot = new DataSyncDefinitionSnapshotVO();
        snapshot.setSyncType(syncType.name());
        snapshot.setWriteMode(writeMode.name());
        return snapshot;
    }
}
