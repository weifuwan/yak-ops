package io.yak.ops.api.service;

import io.yak.ops.api.dto.datasync.DataSyncTaskRequest;
import io.yak.ops.api.vo.DataSyncDefinitionVO;
import io.yak.ops.api.vo.DataSyncInstanceVO;
import io.yak.ops.api.vo.DataSyncLogVO;
import io.yak.ops.api.vo.DataSyncPageVO;
import io.yak.ops.api.vo.DataSyncRunVO;
import io.yak.ops.api.vo.DataSyncTaskVO;
import io.yak.ops.api.vo.DataSyncTaskVersionVO;
import io.yak.ops.dao.entity.User;

import java.util.List;

public interface DataSyncService {

    DataSyncPageVO<DataSyncTaskVO> listTasks(User user, long projectCode,
                                             int pageNo, int pageSize, String keyword, String status);

    DataSyncTaskVO getTask(User user, long projectCode, long taskCode);

    DataSyncTaskVO createTask(User user, long projectCode, DataSyncTaskRequest request);

    DataSyncTaskVO updateTask(User user, long projectCode, long taskCode, DataSyncTaskRequest request);

    DataSyncTaskVO publish(User user, long projectCode, long taskCode);

    DataSyncTaskVO unpublish(User user, long projectCode, long taskCode);

    void deleteTask(User user, long projectCode, long taskCode);

    DataSyncPageVO<DataSyncTaskVersionVO> listVersions(User user, long projectCode, long taskCode,
                                                       int pageNo, int pageSize);

    DataSyncTaskVersionVO getVersion(User user, long projectCode, long taskCode, int version);

    DataSyncRunVO start(User user, long projectCode, long taskCode);

    DataSyncPageVO<DataSyncInstanceVO> listInstances(User user, long projectCode, int pageNo,
                                                     int pageSize, Long taskCode, String status);

    List<DataSyncInstanceVO> latestInstances(User user, long projectCode,
                                             List<Long> taskCodes, boolean activeOnly);

    DataSyncInstanceVO getInstance(User user, long projectCode, int instanceId);

    DataSyncDefinitionVO getInstanceDefinition(User user, long projectCode, int instanceId);

    void stop(User user, long projectCode, int instanceId);

    DataSyncLogVO logs(User user, long projectCode, int instanceId, int offset, int limit);
}
