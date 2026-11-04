package io.yak.ops.extract.common.service.impl;

import io.yak.ops.common.utils.FileUtils;
import io.yak.ops.common.utils.LogUtils;
import io.yak.ops.extract.common.ILogService;
import io.yak.ops.extract.common.transportor.LogResponseStatus;
import io.yak.ops.extract.common.transportor.TaskInstanceLogFileDownloadRequest;
import io.yak.ops.extract.common.transportor.TaskInstanceLogFileDownloadResponse;
import io.yak.ops.extract.common.transportor.TaskInstanceLogPageQueryRequest;
import io.yak.ops.extract.common.transportor.TaskInstanceLogPageQueryResponse;

import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.List;

public class LogServiceImpl implements ILogService {

    /**
     * Downloads the entire log file for a task instance.
     *
     * @param taskInstanceLogFileDownloadRequest Request object containing the path to the task instance log file.
     * @return Response object containing the log file content in byte array form.
     */
    @Override
    public TaskInstanceLogFileDownloadResponse getTaskInstanceWholeLogFileBytes(TaskInstanceLogFileDownloadRequest taskInstanceLogFileDownloadRequest) {
        final TaskInstanceLogFileDownloadResponse taskInstanceLogFileDownloadResponse =
                new TaskInstanceLogFileDownloadResponse();
        try {
            byte[] bytes = LogUtils
                    .getFileContentBytesFromLocal(taskInstanceLogFileDownloadRequest.getTaskInstanceLogAbsolutePath());
            taskInstanceLogFileDownloadResponse.setLogBytes(bytes);
        } catch (Exception e) {
            taskInstanceLogFileDownloadResponse.setCode(LogResponseStatus.ERROR);
            taskInstanceLogFileDownloadResponse.setMessage(ExceptionUtils.getRootCauseMessage(e));
        }
        return taskInstanceLogFileDownloadResponse;
    }

    /**
     * Performs paginated queries on task instance logs.
     *
     * @param taskInstanceLogPageQueryRequest Request object containing the path to the task instance log file, the number of lines to skip, and the maximum number of lines to read.
     * @return Response object containing the log content.
     */
    @Override
    public TaskInstanceLogPageQueryResponse pageQueryTaskInstanceLog(TaskInstanceLogPageQueryRequest taskInstanceLogPageQueryRequest) {
        final TaskInstanceLogPageQueryResponse taskInstanceLogPageQueryResponse =
                new TaskInstanceLogPageQueryResponse();
        List<String> lines;
        try {
            lines = LogUtils.readPartFileContentFromLocal(
                    taskInstanceLogPageQueryRequest.getTaskInstanceLogAbsolutePath(),
                    taskInstanceLogPageQueryRequest.getSkipLineNum(),
                    taskInstanceLogPageQueryRequest.getLimit());
            taskInstanceLogPageQueryResponse.setLogContent(LogUtils.rollViewLogLines(lines));
        } catch (Exception e) {
            taskInstanceLogPageQueryResponse.setCode(LogResponseStatus.ERROR);
            taskInstanceLogPageQueryResponse.setMessage(ExceptionUtils.getMessage(e));
        }
        return taskInstanceLogPageQueryResponse;
    }

    @Override
    public void removeTaskInstanceLog(String taskInstanceLogAbsolutePath) {
        FileUtils.deleteFile(taskInstanceLogAbsolutePath);
    }

}
