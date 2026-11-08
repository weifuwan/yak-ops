package io.yak.ops.api.service;

import io.yak.ops.api.dto.datasync.DataSyncPublishRequest;
import io.yak.ops.api.dto.datasync.DataSyncTaskRequest;
import io.yak.ops.api.enums.DataSyncScope;
import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.api.vo.DataSyncDefinitionVO;
import io.yak.ops.api.vo.DataSyncExecutionStatsVO;
import io.yak.ops.api.vo.DataSyncInstanceVO;
import io.yak.ops.api.vo.DataSyncOverviewVO;
import io.yak.ops.api.vo.DataSyncTaskVO;
import io.yak.ops.api.vo.DataSyncTaskVersionVO;
import io.yak.ops.common.enums.ReleaseState;
import io.yak.ops.dao.entity.User;
import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;

import java.time.LocalDate;
import java.util.List;

public interface DataSyncService {

    PageInfo<DataSyncTaskVO> listTasks(User user, long projectCode,
                                       int pageNo, int pageSize, String keyword, ReleaseState status,
                                       DataSyncScope scope);

    DataSyncOverviewVO getOverview(User user, long projectCode);

    DataSyncExecutionStatsVO getExecutionStats(User user, long projectCode,
                                              LocalDate startDate, LocalDate endDate);

    DataSyncTaskVO getTask(User user, long projectCode, long taskCode);

    String preview(User user, long projectCode, DataSyncTaskRequest request);

    DataSyncTaskVO createTask(User user, long projectCode, DataSyncTaskRequest request);

    DataSyncTaskVO updateTask(User user, long projectCode, long taskCode, DataSyncTaskRequest request);

    DataSyncTaskVO publish(User user, long projectCode, long taskCode, DataSyncPublishRequest request);

    DataSyncTaskVO unpublish(User user, long projectCode, long taskCode);

    void deleteTask(User user, long projectCode, long taskCode);

    DataSyncTaskVersionVO getVersion(User user, long projectCode, long taskCode, int version);

    PageInfo<DataSyncInstanceVO> listInstances(User user, long projectCode, int pageNo,
                                               int pageSize, Long taskCode, TaskExecutionStatus status);

    List<DataSyncInstanceVO> latestInstances(User user, long projectCode,
                                             List<Long> taskCodes, boolean activeOnly);

    DataSyncInstanceVO getInstance(User user, long projectCode, int instanceId);

    DataSyncDefinitionVO getInstanceDefinition(User user, long projectCode, int instanceId);

}
