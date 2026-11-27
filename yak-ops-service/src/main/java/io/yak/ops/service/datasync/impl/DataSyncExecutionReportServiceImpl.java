package io.yak.ops.service.datasync.impl;

import io.yak.ops.common.service.BaseServiceImpl;
import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.dao.entity.DataSyncExecutionReport;
import io.yak.ops.dao.entity.DataSyncTableReport;
import io.yak.ops.dao.entity.DataSyncRecoveryPoint;
import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.dao.repository.DataSyncExecutionReportDao;
import io.yak.ops.dao.repository.DataSyncTableReportDao;
import io.yak.ops.dao.repository.DataSyncRecoveryPointDao;
import io.yak.ops.dao.repository.TaskInstanceDao;
import io.yak.ops.extract.master.transportor.DataSyncExecutionMetrics;
import io.yak.ops.extract.master.transportor.DataSyncReportAck;
import io.yak.ops.extract.master.transportor.DataSyncReportRequest;
import io.yak.ops.extract.master.transportor.DataSyncRecoveryPointReport;
import io.yak.ops.extract.master.transportor.DataSyncTableMetrics;
import io.yak.ops.service.datasync.DataSyncExecutionReportService;
import io.yak.ops.service.datasync.model.DataSyncExecutionReportDetail;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataSyncExecutionReportServiceImpl extends BaseServiceImpl
        implements DataSyncExecutionReportService {

    private static final int SUPPORTED_PROTOCOL_VERSION = 1;
    private static final String TASK_TYPE = "DATA_SYNC";

    private final DataSyncExecutionReportDao executionReportDao;
    private final DataSyncTableReportDao tableReportDao;
    private final TaskInstanceDao taskInstanceDao;
    private final DataSyncRecoveryPointDao recoveryPointDao;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataSyncReportAck acceptReport(DataSyncReportRequest request) {
        String validationError = validate(request);
        if (validationError != null) {
            return DataSyncReportAck.rejected("REJECTED", 0L, validationError);
        }

        TaskInstance taskInstance = taskInstanceDao.queryByIdForUpdate(request.getTaskInstanceId());
        if (taskInstance == null || !TASK_TYPE.equals(taskInstance.getTaskType())) {
            return DataSyncReportAck.rejected("REJECTED", 0L, "Task instance is not a DATA_SYNC task");
        }

        DataSyncExecutionReport report = executionReportDao.queryByExecutionId(request.getExecutionId());
        if (report != null
                && (!Objects.equals(report.getTaskInstanceId(), request.getTaskInstanceId())
                        || !Objects.equals(report.getJobId(), request.getJobId()))) {
            return DataSyncReportAck.rejected(
                    "REJECTED", valueOrZero(report.getLatestSequence()), "Execution identity cannot be changed");
        }
        if (report != null && Boolean.TRUE.equals(report.getFinalized())) {
            return DataSyncReportAck.accepted("FINAL_ALREADY_ACCEPTED", valueOrZero(report.getLatestSequence()));
        }

        long latestSequence = report == null ? 0L : valueOrZero(report.getLatestSequence());
        long incomingSequence = request.getSequence();
        boolean finalReport = "FINAL".equals(request.getReportType());
        if (incomingSequence < latestSequence) {
            return DataSyncReportAck.rejected("STALE", latestSequence, "Report sequence is older than latest");
        }
        if (incomingSequence == latestSequence && !(finalReport && report != null)) {
            return DataSyncReportAck.accepted("DUPLICATE", latestSequence);
        }

        Date now = new Date();
        long collectedAt = request.getCollectedAt() == null ? now.getTime() : request.getCollectedAt();
        String summaryJson = JSONUtils.toJsonString(request.getSummary());
        if (report == null) {
            report = DataSyncExecutionReport.builder()
                    .executionId(request.getExecutionId())
                    .taskInstanceId(request.getTaskInstanceId())
                    .submittedAt(request.getSubmittedAt() == null ? now.getTime() : request.getSubmittedAt())
                    .jobId(request.getJobId())
                    .deploymentMode(request.getDeploymentMode())
                    .clusterId(request.getClusterId())
                    .reportVersion(request.getProtocolVersion())
                    .createTime(now)
                    .build();
        }

        report.setExecutionState(request.getExecutionState());
        report.setMetricsState(request.getMetricsState());
        report.setLatestSequence(incomingSequence);
        report.setLatestCollectedAt(collectedAt);
        report.setLatestSnapshotJson(summaryJson);
        report.setLastMetricsError(request.getMetricsError());
        report.setUpdateTime(now);

        if (finalReport) {
            report.setFinalSequence(incomingSequence);
            report.setFinalCollectedAt(collectedAt);
            report.setFinalSnapshotJson(summaryJson);
            report.setFinalized(true);
        } else if (report.getFinalized() == null) {
            report.setFinalized(false);
        }

        if (report.getId() == null) {
            executionReportDao.insert(report);
        } else {
            executionReportDao.updateById(report);
        }

        upsertTableReports(request, collectedAt, now, finalReport);
        persistRecoveryPoint(request, taskInstance, now);
        linkRecoveredExecution(request, taskInstance);
        return DataSyncReportAck.accepted("ACCEPTED", incomingSequence);
    }

    @Override
    public DataSyncExecutionReportDetail getLatestReport(int taskInstanceId) {
        DataSyncExecutionReport report = executionReportDao.queryLatestByTaskInstanceId(taskInstanceId);
        if (report == null) {
            return null;
        }

        List<DataSyncTableMetrics> tables = new ArrayList<>();
        boolean finalized = Boolean.TRUE.equals(report.getFinalized());
        for (DataSyncTableReport tableReport : tableReportDao.queryByExecutionId(report.getExecutionId())) {
            String metricsJson = finalized
                    ? tableReport.getFinalMetricsJson()
                    : tableReport.getLatestMetricsJson();
            if (StringUtils.isBlank(metricsJson)) {
                continue;
            }
            DataSyncTableMetrics metrics = JSONUtils.parseObject(metricsJson, DataSyncTableMetrics.class);
            if (metrics != null) {
                tables.add(metrics);
            }
        }

        return DataSyncExecutionReportDetail.builder()
                .executionId(report.getExecutionId())
                .taskInstanceId(report.getTaskInstanceId())
                .jobId(report.getJobId())
                .deploymentMode(report.getDeploymentMode())
                .clusterId(report.getClusterId())
                .executionState(report.getExecutionState())
                .metricsState(report.getMetricsState())
                .latestSequence(report.getLatestSequence())
                .finalSequence(report.getFinalSequence())
                .latestCollectedAt(report.getLatestCollectedAt())
                .finalCollectedAt(report.getFinalCollectedAt())
                .finalized(Boolean.TRUE.equals(report.getFinalized()))
                .metricsError(report.getLastMetricsError())
                .latestSnapshot(JSONUtils.parseObject(
                        report.getLatestSnapshotJson(), DataSyncExecutionMetrics.class))
                .finalSnapshot(JSONUtils.parseObject(
                        report.getFinalSnapshotJson(), DataSyncExecutionMetrics.class))
                .tables(tables)
                .build();
    }

    @Override
    public Map<Integer, String> getLatestExecutionReports(List<Integer> taskInstanceIds) {
        if (taskInstanceIds == null || taskInstanceIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Integer> requestedIds = taskInstanceIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
        if (requestedIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Integer, String> reports = new LinkedHashMap<>();
        Set<Integer> seenTaskInstances = new HashSet<>();
        for (DataSyncExecutionReport report : executionReportDao.queryLatestByTaskInstanceIds(requestedIds)) {
            if (report == null || report.getTaskInstanceId() == null
                    || !seenTaskInstances.add(report.getTaskInstanceId())) {
                continue;
            }
            String snapshotJson = StringUtils.isNotBlank(report.getLatestSnapshotJson())
                    ? report.getLatestSnapshotJson()
                    : report.getFinalSnapshotJson();
            if (StringUtils.isBlank(snapshotJson)) {
                continue;
            }
            DataSyncExecutionMetrics snapshot =
                    JSONUtils.parseObject(snapshotJson, DataSyncExecutionMetrics.class);
            if (snapshot != null && snapshot.getExecutionSummary() != null) {
                reports.put(
                        report.getTaskInstanceId(),
                        JSONUtils.toJsonString(snapshot.getExecutionSummary()));
            }
        }
        return reports;
    }

    private void persistRecoveryPoint(DataSyncReportRequest request, TaskInstance taskInstance, Date now) {
        DataSyncRecoveryPointReport report = request.getRecoveryPoint();
        if (report == null) {
            return;
        }
        if (!"CHECKPOINT".equals(report.getRecoveryType())
                || StringUtils.isBlank(report.getRecoveryPointId())
                || StringUtils.isBlank(report.getRestorePath())
                || report.getCompletedAt() == null
                || report.getCompletedAt() <= 0L) {
            log.warn("Ignoring invalid Flink recovery point report, executionId={}", request.getExecutionId());
            return;
        }

        Date completedAt = new Date(report.getCompletedAt());
        DataSyncRecoveryPoint point = DataSyncRecoveryPoint.builder()
                .taskInstanceId(taskInstance.getId())
                .projectCode(taskInstance.getProjectCode())
                .taskCode(taskInstance.getTaskCode())
                .taskDefinitionVersion(taskInstance.getTaskDefinitionVersion())
                .executionId(request.getExecutionId())
                .jobId(request.getJobId())
                .recoveryPointId(report.getRecoveryPointId())
                .checkpointId(report.getCheckpointId())
                .runtimeMode(request.getDeploymentMode())
                .recoveryType(report.getRecoveryType())
                .restorePath(report.getRestorePath())
                .state("AVAILABLE")
                .createdAt(completedAt == null ? now : completedAt)
                .completedAt(completedAt)
                .build();
        recoveryPointDao.saveCheckpointIfAbsent(point);
    }

    private void linkRecoveredExecution(DataSyncReportRequest request, TaskInstance taskInstance) {
        if (StringUtils.isBlank(request.getRecoveredFromRecoveryPointId())
                || taskInstance.getWorkflowInstanceId() <= 0) {
            return;
        }
        boolean linked = recoveryPointDao.markUsedByInstance(
                request.getRecoveredFromRecoveryPointId(),
                taskInstance.getId(),
                taskInstance.getWorkflowInstanceId());
        if (!linked) {
            log.warn("Could not link recovered CDC execution to recovery point, recoveryPointId={}, taskInstanceId={}",
                    request.getRecoveredFromRecoveryPointId(), taskInstance.getId());
        }
    }

    private void upsertTableReports(
            DataSyncReportRequest request, long collectedAt, Date now, boolean finalReport) {
        List<DataSyncTableReport> existingReports =
                tableReportDao.queryByExecutionId(request.getExecutionId());
        int clearedCount = 0;
        for (DataSyncTableReport existing : existingReports) {
            clearedCount += tableReportDao.clearSnapshotFields(
                    existing.getId(), request.getSequence(), collectedAt, now, finalReport);
        }

        int upsertedCount = 0;
        List<DataSyncTableMetrics> incomingTables = request.getTables();
        if (incomingTables != null) {
            for (DataSyncTableMetrics metrics : incomingTables) {
                if (metrics == null || (StringUtils.isBlank(metrics.getSourceTable())
                        && StringUtils.isBlank(metrics.getSinkTable()))) {
                    continue;
                }
                String tableKey = tableKey(metrics.getSourceTable(), metrics.getSinkTable());
                DataSyncTableReport report = tableReportDao.queryByExecutionIdAndTableKey(
                        request.getExecutionId(), tableKey);
                if (report == null) {
                    report = tableReportDao.queryByExecutionIdAndPartialTableMapping(
                            request.getExecutionId(), metrics.getSourceTable(), metrics.getSinkTable());
                }
                if (report == null) {
                    report = DataSyncTableReport.builder()
                            .executionId(request.getExecutionId())
                            .tableKey(tableKey)
                            .sourceTable(metrics.getSourceTable())
                            .sinkTable(metrics.getSinkTable())
                            .createTime(now)
                            .build();
                } else {
                    // Route metadata is authoritative; it can fill names that were previously
                    // absent when older collectors only reported the Sink metric group.
                    report.setSourceTable(metrics.getSourceTable());
                    report.setSinkTable(metrics.getSinkTable());
                    report.setTableKey(tableKey);
                }
                report.setLatestMetricsJson(JSONUtils.toJsonString(metrics));
                report.setLatestSequence(request.getSequence());
                report.setLatestCollectedAt(collectedAt);
                report.setUpdateTime(now);
                if (finalReport) {
                    report.setFinalMetricsJson(JSONUtils.toJsonString(metrics));
                    report.setFinalCollectedAt(collectedAt);
                }
                if (report.getId() == null) {
                    tableReportDao.insert(report);
                } else {
                    tableReportDao.updateById(report);
                }
                upsertedCount++;
            }
        }

        log.info(
                "Persisted CDC table report snapshot, executionId={}, sequence={}, reportType={}, "
                        + "incomingTables={}, existingTables={}, clearedTables={}, upsertedTables={}",
                request.getExecutionId(),
                request.getSequence(),
                request.getReportType(),
                incomingTables == null ? 0 : incomingTables.size(),
                existingReports.size(),
                clearedCount,
                upsertedCount);
        if (clearedCount < existingReports.size()) {
            log.warn(
                    "CDC table report snapshot sequence was not applied to every existing row, "
                            + "executionId={}, sequence={}, existingTables={}, clearedTables={}",
                    request.getExecutionId(),
                    request.getSequence(),
                    existingReports.size(),
                    clearedCount);
        }
    }

    private static String validate(DataSyncReportRequest request) {
        if (request == null) {
            return "Report is required";
        }
        if (!Integer.valueOf(SUPPORTED_PROTOCOL_VERSION).equals(request.getProtocolVersion())) {
            return "Unsupported report protocol version";
        }
        if (StringUtils.isBlank(request.getExecutionId()) || request.getTaskInstanceId() == null
                || request.getTaskInstanceId() <= 0 || StringUtils.isBlank(request.getJobId())) {
            return "Execution identity is incomplete";
        }
        if (request.getSequence() == null || request.getSequence() <= 0) {
            return "Report sequence must be positive";
        }
        if (!"SNAPSHOT".equals(request.getReportType()) && !"FINAL".equals(request.getReportType())) {
            return "Unsupported report type";
        }
        if (StringUtils.isBlank(request.getDeploymentMode())
                || StringUtils.isBlank(request.getExecutionState())
                || StringUtils.isBlank(request.getMetricsState())
                || request.getSummary() == null) {
            return "Deployment mode, execution state, metrics state and summary are required";
        }
        return null;
    }

    private static long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    private static String tableKey(String sourceTable, String sinkTable) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(((sourceTable == null ? "" : sourceTable) + "\u001f"
                            + (sinkTable == null ? "" : sinkTable)).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(String.format("%02x", value & 0xff));
            }
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to build CDC table report key", exception);
        }
    }
}
