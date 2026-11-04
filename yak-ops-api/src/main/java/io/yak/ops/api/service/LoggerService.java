package io.yak.ops.api.service;

import io.yak.ops.api.utils.Result;
import io.yak.ops.dao.entity.ResponseTaskLog;
import io.yak.ops.dao.entity.User;

public interface LoggerService {

    /**
     * view log
     *
     * @param loginUser   login user
     * @param taskInstId task instance id
     * @param skipLineNum skip line number
     * @param limit limit
     * @return log string data
     */
    Result<ResponseTaskLog> queryLog(User loginUser, int taskInstId, int skipLineNum, int limit);

    /**
     * get log size
     *
     * @param loginUser   login user
     * @param taskInstId task instance id
     * @return log byte array
     */
    byte[] getLogBytes(User loginUser, int taskInstId);

    /**
     * query log
     *
     * @param loginUser   login user
     * @param projectCode project code
     * @param taskInstId  task instance id
     * @param skipLineNum skip line number
     * @param limit       limit
     * @return log string data
     */
    String queryLog(User loginUser, long projectCode, int taskInstId, int skipLineNum, int limit);

    /**
     * get log bytes
     *
     * @param loginUser   login user
     * @param projectCode project code
     * @param taskInstId  task instance id
     * @return log byte array
     */
    byte[] getLogBytes(User loginUser, long projectCode, int taskInstId);
}
