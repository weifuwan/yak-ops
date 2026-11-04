package io.yak.ops.extract.common;

import io.yak.ops.extract.base.RpcMethod;
import io.yak.ops.extract.base.RpcService;
import io.yak.ops.extract.common.transportor.TaskInstanceLogFileDownloadRequest;
import io.yak.ops.extract.common.transportor.TaskInstanceLogFileDownloadResponse;
import io.yak.ops.extract.common.transportor.TaskInstanceLogPageQueryRequest;
import io.yak.ops.extract.common.transportor.TaskInstanceLogPageQueryResponse;

@RpcService
public interface ILogService {

    @RpcMethod
    TaskInstanceLogFileDownloadResponse getTaskInstanceWholeLogFileBytes(TaskInstanceLogFileDownloadRequest taskInstanceLogFileDownloadRequest);

    @RpcMethod
    TaskInstanceLogPageQueryResponse pageQueryTaskInstanceLog(TaskInstanceLogPageQueryRequest taskInstanceLogPageQueryRequest);

    @RpcMethod
    void removeTaskInstanceLog(String taskInstanceLogAbsolutePath);

}
